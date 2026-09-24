package fr.lkdm.homelink.farm.farm.irrigation;

import net.minecraft.nbt.CompoundTag;

/**
 * Display state of a pump and its network, synchronized to clients (never saved: it is
 * recomputed from the world).
 */
public record PumpSnapshot(PumpStatus status, boolean water, int sprinklers, int capacity, int pipes, int pumps,
                           boolean incomplete, int irrigatedCrops) {
    public static final PumpSnapshot INITIAL = new PumpSnapshot(PumpStatus.DISABLED, false, 0, 0, 0, 1, false, 0);

    public boolean overCapacity() {
        return status == PumpStatus.OVER_CAPACITY;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Status", status.ordinal());
        tag.putBoolean("Water", water);
        tag.putInt("Sprinklers", sprinklers);
        tag.putInt("Capacity", capacity);
        tag.putInt("Pipes", pipes);
        tag.putInt("Pumps", pumps);
        tag.putBoolean("Incomplete", incomplete);
        tag.putInt("Irrigated", irrigatedCrops);
        return tag;
    }

    public static PumpSnapshot load(CompoundTag tag) {
        return new PumpSnapshot(PumpStatus.byId(tag.getInt("Status")), tag.getBoolean("Water"), tag.getInt("Sprinklers"),
                tag.getInt("Capacity"), tag.getInt("Pipes"), tag.getInt("Pumps"), tag.getBoolean("Incomplete"), tag.getInt("Irrigated"));
    }
}
