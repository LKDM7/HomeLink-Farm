package fr.lkdm.homelink.farm.farm.controller;

import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemCounts;
import net.minecraft.nbt.CompoundTag;

/**
 * Figures aggregated by a Farm Controller over its loaded components. Recalculable, so it is
 * only synchronized to clients, never saved.
 *
 * @param cropAreas Crop Monitors that reported a result
 * @param crops total crops
 * @param ready mature crops
 * @param maturity crop-weighted average maturity (0..1)
 * @param irrigable crops accepting HomeLink irrigation
 * @param irrigated irrigable crops covered by an ACTIVE sprinkler
 * @param problems crop problems per type over all monitors
 * @param pumps loaded linked Irrigation Pumps
 * @param pumpFaults linked pumps in a failure state (no water, over capacity, network too large)
 * @param sprinklers sprinklers of the distinct networks fed by the linked pumps
 * @param capacity sprinkler capacity of those networks (5 per supplying pump)
 * @param stale linked components currently unloaded whose last known figures are used
 * @param unavailable linked components currently unloaded or without data (and no known figures)
 * @param partial whether some monitored zone was only partially loaded
 */
public record FarmSummary(int cropAreas, int crops, int ready, float maturity, int irrigable, int irrigated,
                          ProblemCounts problems, int pumps, int pumpFaults, int sprinklers, int capacity,
                          int stale, int unavailable, boolean partial) {
    public static final FarmSummary EMPTY = new FarmSummary(0, 0, 0, 0, 0, 0, ProblemCounts.NONE, 0, 0, 0, 0, 0, 0, false);

    public float readyFraction() {
        return crops == 0 ? 0 : ready / (float) crops;
    }

    public float coverage() {
        return irrigable == 0 ? 0 : irrigated / (float) irrigable;
    }

    /** Crop problems plus pump faults. */
    public int problemTotal() {
        return problems.total() + pumpFaults;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Areas", cropAreas);
        tag.putInt("Crops", crops);
        tag.putInt("Ready", ready);
        tag.putFloat("Maturity", maturity);
        tag.putInt("Irrigable", irrigable);
        tag.putInt("Irrigated", irrigated);
        tag.put("Problems", problems.save());
        tag.putInt("Pumps", pumps);
        tag.putInt("PumpFaults", pumpFaults);
        tag.putInt("Sprinklers", sprinklers);
        tag.putInt("Capacity", capacity);
        tag.putInt("Stale", stale);
        tag.putInt("Unavailable", unavailable);
        tag.putBoolean("Partial", partial);
        return tag;
    }

    public static FarmSummary load(CompoundTag tag) {
        return new FarmSummary(tag.getInt("Areas"), tag.getInt("Crops"), tag.getInt("Ready"), tag.getFloat("Maturity"),
                tag.getInt("Irrigable"), tag.getInt("Irrigated"), ProblemCounts.load(tag.getCompound("Problems")),
                tag.getInt("Pumps"), tag.getInt("PumpFaults"), tag.getInt("Sprinklers"), tag.getInt("Capacity"),
                tag.getInt("Stale"), tag.getInt("Unavailable"), tag.getBoolean("Partial"));
    }

    /** Mutable aggregation helper. */
    public static final class Builder {
        private int areas;
        private int crops;
        private int ready;
        private double maturityWeighted;
        private int irrigable;
        private int irrigated;
        private ProblemCounts problems = ProblemCounts.NONE;
        private int pumps;
        private int pumpFaults;
        private int sprinklers;
        private int capacity;
        private int stale;
        private int unavailable;
        private boolean partial;

        public void addMonitor(CropScanResult result) {
            areas++;
            crops += result.crops();
            ready += result.ready();
            maturityWeighted += (double) result.maturity() * result.crops();
            irrigable += result.irrigable();
            irrigated += result.irrigated();
            problems = problems.plus(result.problems());
            partial |= !result.complete();
        }

        public void addPump(boolean fault) {
            pumps++;
            if (fault) pumpFaults++;
        }

        /** Adds one distinct network (call once per network, not per pump). */
        public void addNetwork(int networkSprinklers, int networkCapacity) {
            sprinklers += networkSprinklers;
            capacity += networkCapacity;
        }

        public void addUnavailable() {
            unavailable++;
        }

        /** Counts a component whose figures come from the controller's last-known cache. */
        public void addStale() {
            stale++;
        }

        public FarmSummary build() {
            return new FarmSummary(areas, crops, ready, crops == 0 ? 0 : (float) (maturityWeighted / crops), irrigable, irrigated,
                    problems, pumps, pumpFaults, sprinklers, capacity, stale, unavailable, partial);
        }
    }
}
