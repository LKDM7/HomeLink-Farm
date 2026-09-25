package fr.lkdm.homelink.farm.menu;

import fr.lkdm.homelink.farm.blockentity.FarmBotStationBlockEntity;
import fr.lkdm.homelink.farm.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Station menu: the device menu plus the nine output slots (take only) and the player's
 * inventory. The screen shows the slots only in its "Output" view; on the server they are
 * always usable, so vanilla slot synchronization and validation apply unchanged.
 */
public class FarmBotStationMenu extends FarmDeviceMenu {
    /** Top-left of the output row and of the player inventory, in screen coordinates. */
    public static final int SLOTS_X = 55;
    public static final int OUTPUT_Y = 70;
    public static final int INVENTORY_Y = 104;
    public static final int HOTBAR_Y = 162;
    private static final int PLAYER_SLOTS_START = FarmBotStationBlockEntity.OUTPUT_SIZE;
    private static final int PLAYER_SLOTS_END = PLAYER_SLOTS_START + 36;

    private final boolean clientSide;
    private boolean slotsVisible;

    private FarmBotStationMenu(int containerId, Inventory inventory, BlockPos pos, Block block, ContainerLevelAccess access,
                               IItemHandler output, boolean clientSide) {
        super(ModMenus.FARMBOT_STATION.get(), containerId, pos, block, access);
        this.clientSide = clientSide;
        for (int slot = 0; slot < output.getSlots(); slot++) {
            addSlot(new OutputSlot(output, slot, SLOTS_X + slot * 18, OUTPUT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new PlayerSlot(inventory, column + row * 9 + 9, SLOTS_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new PlayerSlot(inventory, column, SLOTS_X + column * 18, HOTBAR_Y));
        }
    }

    public static FarmBotStationMenu server(int containerId, Inventory inventory, BlockPos pos, Block block, IItemHandler output) {
        return new FarmBotStationMenu(containerId, inventory, pos, block, ContainerLevelAccess.create(inventory.player.level(), pos),
                output, false);
    }

    public static FarmBotStationMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Block block = inventory.player.level().getBlockState(pos).getBlock();
        return new FarmBotStationMenu(containerId, inventory, pos, block, ContainerLevelAccess.NULL,
                new ItemStackHandler(FarmBotStationBlockEntity.OUTPUT_SIZE), true);
    }

    /** Client: shows or hides the slots (the screen's "Output" view). */
    public void setSlotsVisible(boolean visible) {
        slotsVisible = visible;
    }

    public boolean slotsVisible() {
        return slotsVisible;
    }

    private boolean slotActive() {
        return !clientSide || slotsVisible;
    }

    /** Shift-click moves harvest into the player's inventory; nothing can be put back into the output. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= PLAYER_SLOTS_START) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, PLAYER_SLOTS_START, PLAYER_SLOTS_END, true)) return ItemStack.EMPTY;
        // Write back through the slot so the station inventory records the change.
        slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        slot.onTake(player, stack);
        return original;
    }

    private final class OutputSlot extends SlotItemHandler {
        OutputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isActive() {
            return slotActive();
        }
    }

    private final class PlayerSlot extends Slot {
        PlayerSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotActive();
        }
    }
}
