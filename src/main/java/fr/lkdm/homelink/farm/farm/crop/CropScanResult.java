package fr.lkdm.homelink.farm.farm.crop;

import fr.lkdm.homelink.farm.farm.diagnostic.CropProblem;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemCounts;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Immutable outcome of one complete pass over a Crop Monitor zone. Recalculable: figures are
 * synchronized to clients for display but never written to disk. Located problem samples
 * stay on the server and are only sent to players viewing the monitor.
 *
 * @param crops counted crops
 * @param ready mature crops
 * @param maturity average normalized maturity (0..1) over all crops
 * @param irrigable crops that accept HomeLink irrigation
 * @param irrigated irrigable crops covered by an ACTIVE sprinkler
 * @param irrigationOffline irrigable crops covered only by non-working sprinklers
 * @param problems number of problems per type
 * @param samples first located problems (capped at {@link #MAX_SAMPLES}); empty on clients
 * @param unloaded zone positions skipped because their chunk was not loaded
 * @param finishedAt game time at which the pass completed
 */
public record CropScanResult(int crops, int ready, float maturity, int irrigable, int irrigated, int irrigationOffline,
                             ProblemCounts problems, List<CropProblem> samples, int unloaded, long finishedAt) {
    /** Maximum located problems kept per monitor (bounded memory and packet size). */
    public static final int MAX_SAMPLES = 64;

    public CropScanResult {
        samples = List.copyOf(samples);
    }

    public CropScanResult(int crops, int ready, float maturity, int unloaded, long finishedAt) {
        this(crops, ready, maturity, 0, 0, 0, ProblemCounts.NONE, List.of(), unloaded, finishedAt);
    }

    public int growing() {
        return crops - ready;
    }

    public int notIrrigated() {
        return irrigable - irrigated - irrigationOffline;
    }

    /** Irrigated share of irrigable crops (0..1). */
    public float coverage() {
        return irrigable == 0 ? 0 : irrigated / (float) irrigable;
    }

    /** False when part of the zone was unloaded: figures only cover loaded chunks. */
    public boolean complete() {
        return unloaded == 0;
    }

    public float readyFraction() {
        return crops == 0 ? 0 : ready / (float) crops;
    }

    /** Same displayed figures, ignoring when the pass finished (avoids resending identical data). */
    public boolean sameFigures(CropScanResult other) {
        return crops == other.crops && ready == other.ready && unloaded == other.unloaded
                && irrigable == other.irrigable && irrigated == other.irrigated && irrigationOffline == other.irrigationOffline
                && Math.abs(maturity - other.maturity) < 0.0005F && problems.equals(other.problems);
    }

    /** Client sync form: figures only, no located samples. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Crops", crops);
        tag.putInt("Ready", ready);
        tag.putFloat("Maturity", maturity);
        tag.putInt("Irrigable", irrigable);
        tag.putInt("Irrigated", irrigated);
        tag.putInt("Offline", irrigationOffline);
        tag.put("Problems", problems.save());
        tag.putInt("Unloaded", unloaded);
        tag.putLong("FinishedAt", finishedAt);
        return tag;
    }

    public static CropScanResult load(CompoundTag tag) {
        return new CropScanResult(tag.getInt("Crops"), tag.getInt("Ready"), tag.getFloat("Maturity"), tag.getInt("Irrigable"),
                tag.getInt("Irrigated"), tag.getInt("Offline"), ProblemCounts.load(tag.getCompound("Problems")), List.of(),
                tag.getInt("Unloaded"), tag.getLong("FinishedAt"));
    }

    /** Mutable accumulator filled during a pass. */
    public static final class Builder {
        private int crops;
        private int ready;
        private double maturitySum;
        private int irrigable;
        private int irrigated;
        private int offline;
        private int unloaded;
        private final int[] problemCounts = new int[ProblemType.values().length];
        private final List<CropProblem> samples = new ArrayList<>();
        private int notIrrigatedGrowing;
        private final List<CropProblem> notIrrigatedSamples = new ArrayList<>();

        public void addCrop(float maturity, boolean mature) {
            crops++;
            if (mature) ready++;
            maturitySum += maturity;
        }

        public void addIrrigated() {
            irrigable++;
            irrigated++;
        }

        public void addIrrigationOffline(BlockPos pos, boolean mature) {
            irrigable++;
            offline++;
            if (!mature) addProblem(pos, ProblemType.IRRIGATION_OFFLINE);
        }

        public void addNotIrrigated(BlockPos pos, boolean mature) {
            irrigable++;
            if (mature) return;
            notIrrigatedGrowing++;
            if (notIrrigatedSamples.size() < MAX_SAMPLES) notIrrigatedSamples.add(new CropProblem(pos, ProblemType.NOT_IRRIGATED));
        }

        public void addProblem(BlockPos pos, ProblemType type) {
            problemCounts[type.ordinal()]++;
            if (samples.size() < MAX_SAMPLES) samples.add(new CropProblem(pos, type));
        }

        public void addUnloaded(int count) {
            unloaded += count;
        }

        public CropScanResult build(long gameTime) {
            // Uncovered crops only count as a problem in a zone the player actually irrigates.
            if (irrigated + offline > 0 && notIrrigatedGrowing > 0) {
                problemCounts[ProblemType.NOT_IRRIGATED.ordinal()] += notIrrigatedGrowing;
                for (CropProblem problem : notIrrigatedSamples) {
                    if (samples.size() >= MAX_SAMPLES) break;
                    samples.add(problem);
                }
            }
            float average = crops == 0 ? 0 : (float) (maturitySum / crops);
            return new CropScanResult(crops, ready, average, irrigable, irrigated, offline, ProblemCounts.of(problemCounts),
                    samples, unloaded, gameTime);
        }
    }
}
