package fr.lkdm.homelink.farm.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Slot-less menu backing a device screen. Its only server role is to prove that a player
 * really has the device open (and is in range) before accepting a configuration request.
 */
public class FarmDeviceMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Block block;
    private final ContainerLevelAccess access;

    protected FarmDeviceMenu(MenuType<?> type, int containerId, BlockPos pos, Block block, ContainerLevelAccess access) {
        super(type, containerId);
        this.pos = pos.immutable();
        this.block = block;
        this.access = access;
    }

    public static FarmDeviceMenu server(MenuType<?> type, int containerId, Player player, BlockPos pos, Block block) {
        return new FarmDeviceMenu(type, containerId, pos, block, ContainerLevelAccess.create(player.level(), pos));
    }

    public static FarmDeviceMenu client(MenuType<?> type, int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Block block = inventory.player.level().getBlockState(pos).getBlock();
        return new FarmDeviceMenu(type, containerId, pos, block, ContainerLevelAccess.NULL);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, block);
    }
}
