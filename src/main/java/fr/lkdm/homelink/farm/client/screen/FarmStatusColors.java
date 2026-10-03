package fr.lkdm.homelink.farm.client.screen;

import fr.lkdm.homecore.api.client.ui.HomeLinkStatusTone;
import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homelink.farm.farm.bot.FarmBotState;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;

/** Farm domain state mapping; the palette and rendering belong to HomeCore. */
public final class FarmStatusColors {
    private FarmStatusColors() { }

    public static int pumpStatus(PumpStatus status) {
        HomeLinkStatusTone tone = status == PumpStatus.ACTIVE ? HomeLinkStatusTone.ONLINE
                : status.isFailure() ? HomeLinkStatusTone.OFFLINE
                : status == PumpStatus.DISABLED ? HomeLinkStatusTone.NEUTRAL : HomeLinkStatusTone.WARNING;
        return HomeLinkTheme.statusColor(tone);
    }

    public static int farmBotStatus(FarmBotState state) {
        HomeLinkStatusTone tone = state.isFault() ? HomeLinkStatusTone.OFFLINE
                : state == FarmBotState.LOW_BATTERY || state == FarmBotState.STORAGE_FULL || state == FarmBotState.PAUSED
                    ? HomeLinkStatusTone.WARNING
                : state == FarmBotState.DOCKED || state == FarmBotState.IDLE ? HomeLinkStatusTone.NEUTRAL
                : HomeLinkStatusTone.ONLINE;
        return HomeLinkTheme.statusColor(tone);
    }
}
