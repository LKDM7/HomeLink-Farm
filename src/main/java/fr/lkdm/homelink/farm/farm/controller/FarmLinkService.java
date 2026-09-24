package fr.lkdm.homelink.farm.farm.controller;

import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.farm.FarmAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Authoritative server logic linking components to Farm Controllers. Never forces chunk
 * loading: an unloaded controller or component is reported, or cleaned up lazily later.
 */
public final class FarmLinkService {
    private FarmLinkService() {
    }

    /** Validates and performs a link requested by a player. Call on the server thread. */
    public static LinkResult link(ServerPlayer player, ServerLevel level, BlockPos controllerPos, BlockPos componentPos) {
        if (!level.isLoaded(controllerPos)) return LinkResult.CONTROLLER_UNLOADED;
        if (!(level.getBlockEntity(controllerPos) instanceof FarmControllerBlockEntity controller)) return LinkResult.CONTROLLER_MISSING;
        BlockEntity target = level.getBlockEntity(componentPos);
        if (!(target instanceof FarmComponent component) || !(target instanceof AbstractFarmDeviceBlockEntity device)) {
            return LinkResult.NOT_A_COMPONENT;
        }
        if (!FarmAccess.canManage(player, controller) || !FarmAccess.canManage(player, device)) return LinkResult.NO_PERMISSION;
        int maxDistance = FarmServerConfig.MAX_LINK_DISTANCE.get();
        if (controllerPos.distSqr(componentPos) > (double) maxDistance * maxDistance) return LinkResult.TOO_FAR;
        return link(level, controller, component, FarmServerConfig.MAX_COMPONENTS_PER_CONTROLLER.get());
    }

    /** Performs a link after authorization and distance checks (trusted server code). */
    public static LinkResult link(ServerLevel level, FarmControllerBlockEntity controller, FarmComponent component, int capacity) {
        var previous = component.controllerLink();
        boolean alreadyLinked = previous.map(link -> link.controllerId().equals(controller.deviceId())).orElse(false);
        LinkedComponent entry = new LinkedComponent(component.componentId(), component.componentPos(), component.componentKind());
        if (controller.linkedComponents().add(entry, capacity) == LinkedComponents.AddOutcome.FULL) return LinkResult.CONTROLLER_FULL;
        controller.onComponentsChanged();
        if (alreadyLinked) return LinkResult.ALREADY_LINKED;
        previous.ifPresent(link -> detachFromController(level, link, component.componentId()));
        component.setControllerLink(new ControllerLink(controller.deviceId(), controller.getBlockPos()));
        return LinkResult.LINKED;
    }

    /** Called when a component block is broken or replaced (not on chunk unload). */
    public static void onComponentRemoved(ServerLevel level, FarmComponent component) {
        component.controllerLink().ifPresent(link -> detachFromController(level, link, component.componentId()));
    }

    /** Called when a controller block is broken: loaded components forget it immediately. */
    public static void onControllerRemoved(ServerLevel level, FarmControllerBlockEntity controller) {
        for (LinkedComponent entry : controller.linkedComponents().all()) {
            if (!level.isLoaded(entry.pos())) continue;
            if (level.getBlockEntity(entry.pos()) instanceof FarmComponent component
                    && component.controllerLink().map(link -> link.controllerId().equals(controller.deviceId())).orElse(false)) {
                component.clearControllerLink();
            }
        }
        controller.linkedComponents().clear();
    }

    /**
     * Drops entries whose loaded component no longer exists or points to another controller.
     * Entries in unloaded chunks are kept untouched.
     * @return number of entries removed
     */
    public static int pruneStaleComponents(ServerLevel level, FarmControllerBlockEntity controller) {
        var stale = controller.linkedComponents().all().stream()
                .filter(entry -> level.isLoaded(entry.pos()))
                .filter(entry -> !(level.getBlockEntity(entry.pos()) instanceof FarmComponent component)
                        || !component.componentId().equals(entry.id())
                        || !component.controllerLink().map(link -> link.controllerId().equals(controller.deviceId())).orElse(false))
                .map(LinkedComponent::id)
                .toList();
        stale.forEach(controller.linkedComponents()::remove);
        if (!stale.isEmpty()) controller.onComponentsChanged();
        return stale.size();
    }

    private static void detachFromController(ServerLevel level, ControllerLink link, java.util.UUID componentId) {
        if (!level.isLoaded(link.controllerPos())) return;
        if (level.getBlockEntity(link.controllerPos()) instanceof FarmControllerBlockEntity controller
                && controller.deviceId().equals(link.controllerId())
                && controller.linkedComponents().remove(componentId)) {
            controller.onComponentsChanged();
        }
    }
}
