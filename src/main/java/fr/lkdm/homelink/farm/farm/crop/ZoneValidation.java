package fr.lkdm.homelink.farm.farm.crop;

import fr.lkdm.homelink.farm.config.FarmServerConfig;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Server-side limits applied to every Crop Monitor zone, whatever tool proposed it. */
public final class ZoneValidation {
    public enum Result {
        OK, TOO_LARGE, TOO_FAR;

        public MutableComponent message(CropZone zone) {
            return switch (this) {
                case OK -> Component.translatable("message.homelink_farm.zone.set", zone.sizeX(), zone.sizeY(), zone.sizeZ(), zone.volume());
                case TOO_LARGE -> Component.translatable("message.homelink_farm.zone.too_large", zone.volume(),
                        FarmServerConfig.MAX_CROP_MONITOR_VOLUME.get());
                case TOO_FAR -> Component.translatable("message.homelink_farm.zone.too_far", FarmServerConfig.MAX_ZONE_DISTANCE.get());
            };
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private ZoneValidation() {
    }

    public static Result validate(BlockPos monitorPos, CropZone zone) {
        return validate(monitorPos, zone, FarmServerConfig.MAX_CROP_MONITOR_VOLUME.get(), FarmServerConfig.MAX_ZONE_DISTANCE.get());
    }

    public static Result validate(BlockPos monitorPos, CropZone zone, long maxVolume, int maxDistance) {
        if (zone.volume() > maxVolume) return Result.TOO_LARGE;
        if (zone.maxHorizontalDistanceFrom(monitorPos) > maxDistance || zone.maxVerticalDistanceFrom(monitorPos) > maxDistance) {
            return Result.TOO_FAR;
        }
        return Result.OK;
    }
}
