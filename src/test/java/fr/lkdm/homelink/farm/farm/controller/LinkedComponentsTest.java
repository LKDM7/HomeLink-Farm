package fr.lkdm.homelink.farm.farm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class LinkedComponentsTest {
    private static LinkedComponent monitor(int x) {
        return new LinkedComponent(UUID.randomUUID(), new BlockPos(x, 64, 0), FarmComponentKind.CROP_MONITOR);
    }

    @Test
    void enforcesCapacityForNewComponentsOnly() {
        LinkedComponents set = new LinkedComponents();
        LinkedComponent a = monitor(1);
        assertEquals(LinkedComponents.AddOutcome.ADDED, set.add(a, 1));
        assertEquals(LinkedComponents.AddOutcome.FULL, set.add(monitor(2), 1));
        assertEquals(LinkedComponents.AddOutcome.UPDATED, set.add(a, 1));
        assertEquals(1, set.size());
    }

    @Test
    void replacedBlockAtSamePositionDropsStaleEntry() {
        LinkedComponents set = new LinkedComponents();
        LinkedComponent old = monitor(5);
        LinkedComponent replacement = new LinkedComponent(UUID.randomUUID(), old.pos(), FarmComponentKind.CROP_MONITOR);
        set.add(old, 8);
        set.add(replacement, 8);
        assertEquals(1, set.size());
        assertFalse(set.contains(old.id()));
        assertTrue(set.contains(replacement.id()));
    }

    @Test
    void countsByKindAndRemoves() {
        LinkedComponents set = new LinkedComponents();
        LinkedComponent a = monitor(1);
        set.add(a, 8);
        set.add(new LinkedComponent(UUID.randomUUID(), new BlockPos(9, 64, 9), FarmComponentKind.IRRIGATION_PUMP), 8);
        assertEquals(1, set.count(FarmComponentKind.CROP_MONITOR));
        assertEquals(1, set.count(FarmComponentKind.IRRIGATION_PUMP));
        assertTrue(set.remove(a.id()));
        assertFalse(set.remove(a.id()));
        assertEquals(0, set.count(FarmComponentKind.CROP_MONITOR));
    }

    @Test
    void nbtRoundTripPreservesOrderAndData() {
        LinkedComponents set = new LinkedComponents();
        LinkedComponent a = monitor(1);
        LinkedComponent b = monitor(2);
        set.add(a, 8);
        set.add(b, 8);
        LinkedComponents copy = new LinkedComponents();
        copy.load(set.save());
        assertEquals(set.all().stream().toList(), copy.all().stream().toList());
    }

    @Test
    void controllerLinkRoundTrip() {
        ControllerLink link = new ControllerLink(UUID.randomUUID(), new BlockPos(-4, 70, 12));
        assertEquals(link, ControllerLink.load(link.save()).orElseThrow());
    }
}
