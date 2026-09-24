package fr.lkdm.homelink.farm.farm;

/** Validation of player-supplied device names (never trusted as sent by the client). */
public final class DeviceNames {
    public static final int MAX_LENGTH = 32;

    private DeviceNames() {
    }

    /**
     * Removes control and formatting characters, trims and truncates.
     * @return sanitized name; empty means "use the default name"
     */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        StringBuilder builder = new StringBuilder(Math.min(raw.length(), MAX_LENGTH));
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++; // drop the formatting code and its argument character
                continue;
            }
            if (c < 0x20 || c == 0x7F) continue;
            builder.append(c);
        }
        String trimmed = builder.toString().strip();
        if (trimmed.length() > MAX_LENGTH) trimmed = trimmed.substring(0, MAX_LENGTH).stripTrailing();
        return trimmed;
    }
}
