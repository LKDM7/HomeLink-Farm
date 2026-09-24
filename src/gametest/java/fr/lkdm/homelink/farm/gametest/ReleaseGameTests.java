package fr.lkdm.homelink.farm.gametest;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 8: shipped data really loads (loot tables drop the block, every recipe is registered). */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReleaseGameTests {
    private static final List<String> RECIPES = List.of("farm_controller", "crop_monitor", "farm_connector", "irrigation_pump",
            "copper_sprinkler", "copper_pipe", "waxed_copper_pipe_from_honeycomb", "waxed_exposed_copper_pipe_from_honeycomb",
            "waxed_weathered_copper_pipe_from_honeycomb", "waxed_oxidized_copper_pipe_from_honeycomb");

    private ReleaseGameTests() {
    }

    @GameTest(template = "empty")
    public static void everyBlockDropsItself(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        int checked = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(HomeLinkFarm.MOD_ID)) continue;
            LootTable table = server.reloadableRegistries().getLootTable(block.getLootTable());
            helper.assertTrue(table != LootTable.EMPTY, "No loot table for " + id);
            LootParams params = new LootParams.Builder(helper.getLevel())
                    .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.BLOCK_STATE, block.defaultBlockState())
                    .create(LootContextParamSets.BLOCK);
            var drops = table.getRandomItems(params);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()), id + " does not drop itself: " + drops);
            checked++;
        }
        helper.assertTrue(checked == 12, "Expected 12 HomeLink Farm blocks, found " + checked);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyRecipeIsLoaded(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (String recipe : RECIPES) {
            helper.assertTrue(recipes.byKey(HomeLinkFarm.id(recipe)).isPresent(), "Recipe not loaded: " + recipe);
        }
        helper.succeed();
    }
}
