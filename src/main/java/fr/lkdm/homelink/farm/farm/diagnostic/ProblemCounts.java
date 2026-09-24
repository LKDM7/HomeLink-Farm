package fr.lkdm.homelink.farm.farm.diagnostic;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;

/** Immutable number of problems per {@link ProblemType}. */
public final class ProblemCounts {
    public static final ProblemCounts NONE = new ProblemCounts(new int[ProblemType.values().length]);

    private final int[] counts;
    private final int total;

    private ProblemCounts(int[] counts) {
        this.counts = counts;
        this.total = Arrays.stream(counts).sum();
    }

    public static ProblemCounts of(int[] counts) {
        return new ProblemCounts(Arrays.copyOf(counts, ProblemType.values().length));
    }

    public int get(ProblemType type) {
        return counts[type.ordinal()];
    }

    public int total() {
        return total;
    }

    public ProblemCounts plus(ProblemCounts other) {
        int[] sum = new int[counts.length];
        for (int i = 0; i < sum.length; i++) sum[i] = counts[i] + other.counts[i];
        return new ProblemCounts(sum);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (ProblemType type : ProblemType.values()) {
            if (get(type) > 0) tag.putInt(type.name(), get(type));
        }
        return tag;
    }

    public static ProblemCounts load(CompoundTag tag) {
        int[] counts = new int[ProblemType.values().length];
        for (ProblemType type : ProblemType.values()) counts[type.ordinal()] = tag.getInt(type.name());
        return new ProblemCounts(counts);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProblemCounts that && Arrays.equals(counts, that.counts);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(counts);
    }

    @Override
    public String toString() {
        return "ProblemCounts" + Arrays.toString(counts);
    }
}
