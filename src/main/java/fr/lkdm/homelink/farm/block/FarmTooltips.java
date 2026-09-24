package fr.lkdm.homelink.farm.block;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Item tooltips explaining each HomeLink Farm block ({@code tooltip.homelink_farm.<name>.1/.2}). */
public final class FarmTooltips {
    private FarmTooltips() {
    }

    public static void append(List<Component> tooltip, String name) {
        tooltip.add(Component.translatable("tooltip.homelink_farm." + name + ".1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.homelink_farm." + name + ".2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
