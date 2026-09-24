package fr.lkdm.homelink.farm.farm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DeviceNamesTest {
    @Test
    void stripsControlAndFormattingCharacters() {
        assertEquals("Main Farm", DeviceNames.sanitize("  Main§c Farm\n "));
        assertEquals("AB", DeviceNames.sanitize("A\u0000\u007fB"));
    }

    @Test
    void nullAndBlankBecomeEmpty() {
        assertEquals("", DeviceNames.sanitize(null));
        assertEquals("", DeviceNames.sanitize("   \t "));
    }

    @Test
    void truncatesToMaxLength() {
        String result = DeviceNames.sanitize("x".repeat(100));
        assertEquals(DeviceNames.MAX_LENGTH, result.length());
    }
}
