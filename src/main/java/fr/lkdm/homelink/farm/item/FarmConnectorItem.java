package fr.lkdm.homelink.farm.item;

import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.farm.FarmAccess;
import fr.lkdm.homelink.farm.farm.controller.FarmComponent;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.controller.LinkResult;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.crop.ZoneValidation;
import fr.lkdm.homelink.farm.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Reusable configuration tool.
 * <ul>
 *   <li>Right-click a Farm Controller: select it.</li>
 *   <li>Right-click a component: link it to the selected controller.</li>
 *   <li>Sneak + right-click any other block: set zone Position A, then Position B.</li>
 *   <li>Sneak + right-click a Crop Monitor: apply the A/B zone to it.</li>
 *   <li>Sneak + use in the air: clear the selection and both positions.</li>
 * </ul>
 * All decisions are validated on the server ({@link FarmLinkService}, {@link ZoneValidation}).
 */
public class FarmConnectorItem extends Item {
    public FarmConnectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player user = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        BlockEntity target = level.getBlockEntity(pos);
        boolean sneaking = user != null && user.isSecondaryUseActive();
        boolean device = target instanceof FarmControllerBlockEntity || target instanceof FarmComponent;
        if (!device && !sneaking) return InteractionResult.PASS;
        if (level.isClientSide || !(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (sneaking && target instanceof CropMonitorBlockEntity monitor) {
            applyZone(stack, player, serverLevel, monitor);
        } else if (sneaking && !device) {
            setCorner(stack, player, serverLevel, pos);
        } else if (target instanceof FarmControllerBlockEntity controller) {
            selectController(stack, player, serverLevel, controller);
        } else {
            linkComponent(stack, player, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }

    private static void selectController(ItemStack stack, ServerPlayer player, ServerLevel level, FarmControllerBlockEntity controller) {
        if (!FarmAccess.canManage(player, controller)) {
            player.displayClientMessage(LinkResult.NO_PERMISSION.message().withStyle(ChatFormatting.RED), true);
            return;
        }
        stack.set(ModDataComponents.SELECTED_CONTROLLER.get(), GlobalPos.of(level.dimension(), controller.getBlockPos().immutable()));
        player.displayClientMessage(Component.translatable("message.homelink_farm.connector.controller_selected",
                controller.displayName()).withStyle(ChatFormatting.GREEN), true);
    }

    private static void linkComponent(ItemStack stack, ServerPlayer player, ServerLevel level, BlockPos componentPos) {
        GlobalPos selected = stack.get(ModDataComponents.SELECTED_CONTROLLER.get());
        if (selected == null) {
            player.displayClientMessage(Component.translatable("message.homelink_farm.connector.no_selection")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!selected.dimension().equals(level.dimension())) {
            player.displayClientMessage(Component.translatable("message.homelink_farm.connector.other_dimension")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        LinkResult result = FarmLinkService.link(player, level, selected.pos(), componentPos);
        if (result == LinkResult.CONTROLLER_MISSING) stack.remove(ModDataComponents.SELECTED_CONTROLLER.get());
        player.displayClientMessage(result.message().withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    private static void setCorner(ItemStack stack, ServerPlayer player, ServerLevel level, BlockPos pos) {
        GlobalPos corner = GlobalPos.of(level.dimension(), pos.immutable());
        GlobalPos a = stack.get(ModDataComponents.ZONE_CORNER_A.get());
        boolean setB = a != null && !stack.has(ModDataComponents.ZONE_CORNER_B.get()) && a.dimension().equals(level.dimension());
        if (setB) {
            stack.set(ModDataComponents.ZONE_CORNER_B.get(), corner);
        } else {
            stack.set(ModDataComponents.ZONE_CORNER_A.get(), corner);
            stack.remove(ModDataComponents.ZONE_CORNER_B.get());
        }
        player.displayClientMessage(Component.translatable(setB ? "message.homelink_farm.zone.corner_b" : "message.homelink_farm.zone.corner_a",
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA), true);
    }

    private static void applyZone(ItemStack stack, ServerPlayer player, ServerLevel level, CropMonitorBlockEntity monitor) {
        GlobalPos a = stack.get(ModDataComponents.ZONE_CORNER_A.get());
        GlobalPos b = stack.get(ModDataComponents.ZONE_CORNER_B.get());
        if (a == null || b == null) {
            player.displayClientMessage(Component.translatable("message.homelink_farm.zone.incomplete").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!a.dimension().equals(level.dimension()) || !b.dimension().equals(level.dimension())) {
            player.displayClientMessage(Component.translatable("message.homelink_farm.connector.other_dimension").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!FarmAccess.canManage(player, monitor)) {
            player.displayClientMessage(LinkResult.NO_PERMISSION.message().withStyle(ChatFormatting.RED), true);
            return;
        }
        CropZone zone = new CropZone(a.pos(), b.pos());
        ZoneValidation.Result result = monitor.setZone(zone);
        if (result == ZoneValidation.Result.OK) {
            stack.remove(ModDataComponents.ZONE_CORNER_A.get());
            stack.remove(ModDataComponents.ZONE_CORNER_B.get());
        }
        player.displayClientMessage(result.message(zone).withStyle(result == ZoneValidation.Result.OK ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean hasData = stack.has(ModDataComponents.SELECTED_CONTROLLER.get()) || stack.has(ModDataComponents.ZONE_CORNER_A.get());
        if (player.isSecondaryUseActive() && hasData) {
            if (!level.isClientSide) {
                stack.remove(ModDataComponents.SELECTED_CONTROLLER.get());
                stack.remove(ModDataComponents.ZONE_CORNER_A.get());
                stack.remove(ModDataComponents.ZONE_CORNER_B.get());
                player.displayClientMessage(Component.translatable("message.homelink_farm.connector.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.SELECTED_CONTROLLER.get()) || stack.has(ModDataComponents.ZONE_CORNER_A.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos selected = stack.get(ModDataComponents.SELECTED_CONTROLLER.get());
        if (selected == null) {
            tooltip.add(Component.translatable("tooltip.homelink_farm.connector.none").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(position("tooltip.homelink_farm.connector.selected", selected.pos()).withStyle(ChatFormatting.AQUA));
        }
        GlobalPos a = stack.get(ModDataComponents.ZONE_CORNER_A.get());
        GlobalPos b = stack.get(ModDataComponents.ZONE_CORNER_B.get());
        if (a != null) tooltip.add(position("tooltip.homelink_farm.connector.corner_a", a.pos()).withStyle(ChatFormatting.GOLD));
        if (b != null) tooltip.add(position("tooltip.homelink_farm.connector.corner_b", b.pos()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.homelink_farm.connector.usage").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.homelink_farm.connector.usage_zone").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static net.minecraft.network.chat.MutableComponent position(String key, BlockPos pos) {
        return Component.translatable(key, pos.getX(), pos.getY(), pos.getZ());
    }
}
