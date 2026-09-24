package fr.lkdm.homelink.farm.farm.irrigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IrrigationRulesTest {
    @Test
    void onePumpFeedsUpToFiveSprinklers() {
        for (int sprinklers = 1; sprinklers <= 5; sprinklers++) {
            assertEquals(NetworkState.ACTIVE, IrrigationRules.evaluate(1, sprinklers, 5, false), sprinklers + " sprinklers");
        }
    }

    @Test
    void sixthSprinklerOverloadsTheNetwork() {
        assertEquals(NetworkState.OVER_CAPACITY, IrrigationRules.evaluate(1, 6, 5, false));
        assertEquals(5, IrrigationRules.capacity(1, 5));
    }

    @Test
    void capacityScalesWithSupplyingPumps() {
        assertEquals(10, IrrigationRules.capacity(2, 5));
        assertEquals(NetworkState.ACTIVE, IrrigationRules.evaluate(2, 10, 5, false));
        assertEquals(NetworkState.OVER_CAPACITY, IrrigationRules.evaluate(2, 11, 5, false));
    }

    @Test
    void noSupplyingPumpMeansInactiveAndTooLargeWins() {
        assertEquals(NetworkState.INACTIVE, IrrigationRules.evaluate(0, 3, 5, false));
        assertEquals(NetworkState.TOO_LARGE, IrrigationRules.evaluate(1, 1, 5, true));
    }

    @Test
    void visualsFollowNetworkState() {
        assertEquals(IrrigationVisual.ACTIVE, IrrigationVisual.of(NetworkState.ACTIVE));
        assertEquals(IrrigationVisual.ERROR, IrrigationVisual.of(NetworkState.OVER_CAPACITY));
        assertEquals(IrrigationVisual.OFF, IrrigationVisual.of(NetworkState.INACTIVE));
    }
}
