package fr.lkdm.homelink.farm.network;

/**
 * Commands a device screen may send. Each declares whether it changes configuration
 * (requiring manage rights) or only reads/visualizes (requiring an open screen).
 */
public enum DeviceCommand {
    ZONE_AUTO(true),
    ZONE_CLEAR(true),
    RESCAN(true),
    TOGGLE_ENABLED(true),
    CYCLE_REDSTONE(true),
    CYCLE_COMPARATOR(true);

    private final boolean modifiesConfiguration;

    DeviceCommand(boolean modifiesConfiguration) {
        this.modifiesConfiguration = modifiesConfiguration;
    }

    public boolean modifiesConfiguration() {
        return modifiesConfiguration;
    }

    public static DeviceCommand byId(int id) {
        DeviceCommand[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
