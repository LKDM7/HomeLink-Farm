package fr.lkdm.homelink.farm.blockentity;

import fr.lkdm.homelink.farm.farm.controller.FarmAggregator;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.controller.FarmSummary;
import fr.lkdm.homelink.farm.farm.controller.LastKnownFigures;
import fr.lkdm.homelink.farm.farm.controller.LinkedComponent;
import fr.lkdm.homelink.farm.farm.controller.LinkedComponents;
import fr.lkdm.homelink.farm.homelink.FarmControllerDevice;
import fr.lkdm.homelink.farm.homelink.FarmControllerView;
import fr.lkdm.homelink.farm.homelink.HomeCoreIntegration;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import fr.lkdm.homelink.farm.network.DeviceCommand;
import fr.lkdm.homelink.farm.network.DeviceCommandTarget;
import fr.lkdm.homelink.farm.registry.ModBlockEntities;
import fr.lkdm.homelink.farm.registry.ModMenus;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Brain of a farm: groups components, aggregates their figures and is exposed to HomeCore. */
public class FarmControllerBlockEntity extends AbstractFarmDeviceBlockEntity
        implements FarmControllerView, ServerTickingDevice, DeviceCommandTarget {
    /** Aggregation period in ticks: reads component caches only, so it is cheap. */
    public static final int AGGREGATE_INTERVAL = 40;

    private final LinkedComponents components = new LinkedComponents();
    @Nullable
    private FarmControllerDevice homeCoreDevice;
    /** Server: last aggregation; client: synchronized mirror. Never saved. */
    private FarmSummary summary = FarmSummary.EMPTY;
    private final LastKnownFigures lastKnown = new LastKnownFigures();

    public FarmControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FARM_CONTROLLER.get(), pos, state);
    }

    @Override
    public FarmSummary summary() {
        return summary;
    }

    @Override
    public boolean exposedToHomeCore() {
        return true;
    }

    @Override
    public void serverTick(ServerLevel level) {
        if (Math.floorMod(level.getGameTime() + getBlockPos().hashCode(), AGGREGATE_INTERVAL) != 0) return;
        refreshSummary(level);
    }

    @Override
    protected void onMenuOpened(ServerPlayer player) {
        FarmLinkService.pruneStaleComponents(player.serverLevel(), this);
        refreshSummary(player.serverLevel());
    }

    /** Recomputes the aggregated figures; pushes them to clients and HomeCore when they changed. */
    public void refreshSummary(ServerLevel level) {
        FarmSummary updated = FarmAggregator.aggregate(level, this);
        if (updated.equals(summary)) return;
        summary = updated;
        if (homeCoreDevice != null) homeCoreDevice.refresh(summary);
        syncToClients();
    }

    @Override
    public void requestRescan() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (LinkedComponent entry : components.all()) {
            if (serverLevel.isLoaded(entry.pos()) && serverLevel.getBlockEntity(entry.pos()) instanceof CropMonitorBlockEntity monitor
                    && monitor.componentId().equals(entry.id())) {
                monitor.scanner().requestPass();
            }
        }
    }

    @Override
    public void handleCommand(ServerPlayer player, DeviceCommand command, int argument) {
        if (command == DeviceCommand.RESCAN) requestRescan();
    }

    /** Last known figures of components whose chunk is currently unloaded (memory only). */
    public LastKnownFigures lastKnown() {
        return lastKnown;
    }

    public LinkedComponents linkedComponents() {
        return components;
    }

    public void onComponentsChanged() {
        setChangedAndSync();
    }

    @Override
    public boolean isLinked() {
        return components.size() > 0 || super.isLinked();
    }

    public Optional<FarmControllerDevice> homeCoreDevice() {
        return Optional.ofNullable(homeCoreDevice);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            homeCoreDevice = HomeCoreIntegration.register(serverLevel, this, FarmControllerDevice.class).orElse(null);
        }
    }

    /** A copied block entity collided with a live controller: become a distinct, empty controller. */
    @Override
    public void resetIdentityAfterCollision() {
        components.clear();
        super.resetIdentityAfterCollision();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        releaseHomeCoreDevice();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        releaseHomeCoreDevice();
    }

    private void releaseHomeCoreDevice() {
        if (homeCoreDevice != null && level instanceof ServerLevel serverLevel) {
            HomeCoreIntegration.unregister(serverLevel, homeCoreDevice);
        }
        homeCoreDevice = null;
    }

    @Override
    protected Component defaultName() {
        return Component.translatableWithFallback("block.homelink_farm.farm_controller", "Farm Controller");
    }

    @Override
    protected MenuType<FarmDeviceMenu> menuType() {
        return ModMenus.FARM_CONTROLLER.get();
    }

    @Override
    public boolean isOperational() {
        return level != null && !isRemoved();
    }

    @Override
    public Optional<BlockPos> devicePosition() {
        return Optional.of(getBlockPos().immutable());
    }

    @Override
    public Optional<ResourceKey<Level>> deviceDimension() {
        return level == null ? Optional.empty() : Optional.of(level.dimension());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Components", components.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        components.load(tag.getList("Components", Tag.TAG_COMPOUND));
        if (tag.contains("Summary")) summary = FarmSummary.load(tag.getCompound("Summary"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Summary", summary.save());
        return tag;
    }
}
