package fr.lkdm.homelink.farm.gametest;

import fr.lkdm.homelink.farm.block.AbstractFarmDeviceBlock;
import fr.lkdm.homelink.farm.blockentity.FarmBotStationBlockEntity;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The station empties its output into an adjacent storage input (block tag
 * {@code homelink_farm:farmbot_station_outputs}). HomeLink Storage is not loaded here: the
 * GameTest mod adds the vanilla dropper to that tag to stand in for the Deposit.
 */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarmBotOutputGameTests {
    private static final BlockPos STATION = new BlockPos(2, 1, 2);

    private FarmBotOutputGameTests() {
    }

    private static FarmBotStationBlockEntity station(GameTestHelper helper) {
        helper.setBlock(STATION, ModBlocks.FARMBOT_STATION.get().defaultBlockState().setValue(AbstractFarmDeviceBlock.FACING, Direction.NORTH));
        FarmBotStationBlockEntity station = helper.getBlockEntity(STATION);
        station.output().setStackInSlot(0, new ItemStack(Items.WHEAT, 20));
        station.output().setStackInSlot(1, new ItemStack(Items.WHEAT_SEEDS, 64));
        return station;
    }

    private static int count(Container container) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) total += container.getItem(slot).getCount();
        return total;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stationEmptiesIntoAdjacentStorageInput(GameTestHelper helper) {
        FarmBotStationBlockEntity station = station(helper);
        BlockPos target = STATION.east();
        BlockPos chest = STATION.west();
        helper.setBlock(target, Blocks.DROPPER);
        helper.setBlock(chest, Blocks.CHEST);
        helper.succeedWhen(() -> {
            helper.assertTrue(station.outputUsed() == 0, "Station output not emptied: " + station.outputUsed() + " slots left");
            Container input = helper.getBlockEntity(target);
            helper.assertTrue(input.countItem(Items.WHEAT) == 20 && input.countItem(Items.WHEAT_SEEDS) == 64,
                    "Storage input did not receive exactly the output");
            helper.assertTrue(count(helper.getBlockEntity(chest)) == 0, "A plain chest next to the station received items");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stationKeepsOutputWithoutStorageInput(GameTestHelper helper) {
        FarmBotStationBlockEntity station = station(helper);
        BlockPos chest = STATION.east();
        helper.setBlock(chest, Blocks.CHEST);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(station.output().getStackInSlot(0).getCount() == 20
                    && station.output().getStackInSlot(1).getCount() == 64, "Station output changed without a storage input");
            helper.assertTrue(count(helper.getBlockEntity(chest)) == 0, "A plain chest next to the station received items");
            helper.succeed();
        });
    }
}
