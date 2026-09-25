package fr.lkdm.homelink.farm.item;

import fr.lkdm.homelink.farm.blockentity.FarmBotStationBlockEntity;
import fr.lkdm.homelink.farm.registry.ModDataComponents;
import fr.lkdm.homelink.farm.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.Nullable;

/**
 * A FarmBot carried as an item. Right-click a FarmBot Station to install it: the server checks
 * the station is free and its dock clear, then the robot appears docked and bound to that
 * station only.
 */
public class FarmBotItem extends Item {
    public FarmBotItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(int battery, int harvested, @Nullable Component name) {
        ItemStack stack = new ItemStack(ModItems.FARMBOT.get());
        stack.set(ModDataComponents.FARMBOT_DATA.get(), new FarmBotData(battery, harvested));
        if (name != null) stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    public static FarmBotData data(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FARMBOT_DATA.get(), FarmBotData.NEW);
    }

    /** Runs before the station's own interaction (which would open its screen). */
    @Override
    public InteractionResult onItemUseFirst(ItemStack held, UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FarmBotStationBlockEntity station)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        FarmBotStationBlockEntity.InstallResult result = station.install(level, player, stack);
        player.displayClientMessage(result.message().withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        if (result.success() && !player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FarmBotData data = data(stack);
        tooltip.add(Component.translatable("tooltip.homelink_farm.farmbot.battery", data.battery()).withStyle(ChatFormatting.GRAY));
        if (data.harvested() > 0) {
            tooltip.add(Component.translatable("tooltip.homelink_farm.farmbot.harvested", data.harvested()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.homelink_farm.farmbot.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
