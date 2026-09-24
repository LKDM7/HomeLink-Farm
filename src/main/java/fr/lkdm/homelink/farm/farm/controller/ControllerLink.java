package fr.lkdm.homelink.farm.farm.controller;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/**
 * Link stored by a component towards its Farm Controller. The UUID is authoritative:
 * a controller found at {@code controllerPos} with another UUID is not the linked controller.
 */
public record ControllerLink(UUID controllerId, BlockPos controllerPos) {
    public ControllerLink {
        Objects.requireNonNull(controllerId, "controllerId");
        controllerPos = controllerPos.immutable();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ControllerId", controllerId);
        tag.put("ControllerPos", NbtUtils.writeBlockPos(controllerPos));
        return tag;
    }

    public static Optional<ControllerLink> load(CompoundTag tag) {
        if (!tag.hasUUID("ControllerId")) return Optional.empty();
        return NbtUtils.readBlockPos(tag, "ControllerPos")
                .map(pos -> new ControllerLink(tag.getUUID("ControllerId"), pos));
    }
}
