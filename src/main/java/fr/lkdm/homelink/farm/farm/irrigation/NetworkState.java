package fr.lkdm.homelink.farm.farm.irrigation;

/** Hydraulic state of a whole irrigation network (all its sprinklers share it). */
public enum NetworkState {
    /** No pump of the network can supply water (disabled, dry or missing). */
    INACTIVE,
    /** Every sprinkler is fed: 1..(5 x active pumps) sprinklers. */
    ACTIVE,
    /** More sprinklers than the active pumps can feed: the whole network stops (deterministic rule). */
    OVER_CAPACITY,
    /** The pipe network exceeds the configured node limit: treated as a fault. */
    TOO_LARGE;

    public boolean irrigates() {
        return this == ACTIVE;
    }

    public boolean isFault() {
        return this == OVER_CAPACITY || this == TOO_LARGE;
    }
}
