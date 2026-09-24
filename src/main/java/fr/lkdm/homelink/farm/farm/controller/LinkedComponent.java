package fr.lkdm.homelink.farm.farm.controller;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/** Component entry stored by a Farm Controller. */
public record LinkedComponent(UUID id, BlockPos pos, FarmComponentKind kind) {
    public LinkedComponent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        pos = pos.immutable();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        tag.putString("Kind", kind.serializedName());
        return tag;
    }

    public static Optional<LinkedComponent> load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) return Optional.empty();
        return NbtUtils.readBlockPos(tag, "Pos").map(pos -> new LinkedComponent(tag.getUUID("Id"), pos,
                FarmComponentKind.bySerializedName(tag.getString("Kind"))));
    }
}
