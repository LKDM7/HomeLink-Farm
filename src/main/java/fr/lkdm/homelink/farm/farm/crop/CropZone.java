package fr.lkdm.homelink.farm.farm.crop;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.AABB;

/** Inclusive box of blocks watched by a Crop Monitor. */
public record CropZone(BlockPos min, BlockPos max) {
    public CropZone {
        BlockPos a = min;
        BlockPos b = max;
        min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /** Horizontal square of {@code 2*radius+1} blocks, {@code below}/{@code above} layers around the center. */
    public static CropZone around(BlockPos center, int radius, int below, int above) {
        return new CropZone(center.offset(-radius, -below, -radius), center.offset(radius, above, radius));
    }

    /** The monitor's 16 x 16 chunk footprint, keeping only nearby crop layers. */
    public static CropZone chunkAround(BlockPos center, int below, int above) {
        int x = Math.floorDiv(center.getX(), 16) * 16;
        int z = Math.floorDiv(center.getZ(), 16) * 16;
        return new CropZone(new BlockPos(x, center.getY() - below, z),
                new BlockPos(x + 15, center.getY() + above, z + 15));
    }

    public int sizeX() { return max.getX() - min.getX() + 1; }
    public int sizeY() { return max.getY() - min.getY() + 1; }
    public int sizeZ() { return max.getZ() - min.getZ() + 1; }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** Position of the index-th block, iterating X, then Z, then Y (one horizontal layer at a time). */
    public BlockPos.MutableBlockPos positionAt(long index, BlockPos.MutableBlockPos out) {
        int sx = sizeX();
        int sz = sizeZ();
        long layer = (long) sx * sz;
        int y = (int) (index / layer);
        long rest = index % layer;
        return out.set(min.getX() + (int) (rest % sx), min.getY() + y, min.getZ() + (int) (rest / sx));
    }

    /** Largest horizontal (Chebyshev) distance from {@code origin} to any block of the zone. */
    public int maxHorizontalDistanceFrom(BlockPos origin) {
        int dx = Math.max(Math.abs(min.getX() - origin.getX()), Math.abs(max.getX() - origin.getX()));
        int dz = Math.max(Math.abs(min.getZ() - origin.getZ()), Math.abs(max.getZ() - origin.getZ()));
        return Math.max(dx, dz);
    }

    public int maxVerticalDistanceFrom(BlockPos origin) {
        return Math.max(Math.abs(min.getY() - origin.getY()), Math.abs(max.getY() - origin.getY()));
    }

    public AABB toAabb() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Min", NbtUtils.writeBlockPos(min));
        tag.put("Max", NbtUtils.writeBlockPos(max));
        return tag;
    }

    public static Optional<CropZone> load(CompoundTag tag) {
        var min = NbtUtils.readBlockPos(tag, "Min");
        var max = NbtUtils.readBlockPos(tag, "Max");
        if (min.isEmpty() || max.isEmpty()) return Optional.empty();
        return Optional.of(new CropZone(min.get(), max.get()));
    }
}
