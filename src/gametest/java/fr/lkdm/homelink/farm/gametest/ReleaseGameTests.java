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

    /** Electronic devices are built from HomeCore's shared components (HomeCore 1.7.0+ is required): communication modules for the devices that report, control modules for the machines. */
    @GameTest(template = "empty")
    public static void deviceRecipesUseHomeCoreComponents(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        var board = BuiltInRegistries.ITEM.get(ResourceLocation.parse("homecore:homelink_circuit_board"));
        var chip = BuiltInRegistries.ITEM.get(ResourceLocation.parse("homecore:homelink_microprocessor"));
        var communication = BuiltInRegistries.ITEM.get(ResourceLocation.parse("homecore:homelink_communication_module"));
        var control = BuiltInRegistries.ITEM.get(ResourceLocation.parse("homecore:homelink_control_module"));
        var components = java.util.List.of(board, chip, communication, control);
        helper.assertTrue(components.stream().noneMatch(item -> item == net.minecraft.world.item.Items.AIR), "HomeCore components missing");
        java.util.Map<String, java.util.List<net.minecraft.world.item.Item>> expected = java.util.Map.of(
                "farm_controller", java.util.List.of(chip, communication),
                "crop_monitor", java.util.List.of(communication),
                "irrigation_pump", java.util.List.of(control),
                "farmbot", java.util.List.of(chip, control),
                "farmbot_station", java.util.List.of(communication));
        expected.forEach((name, used) -> {
            var recipe = recipes.byKey(HomeLinkFarm.id(name)).orElse(null);
            helper.assertTrue(recipe != null, "Recipe " + name + " not loaded");
            var ingredients = recipe.value().getIngredients();
            // Exactly the expected HomeCore components: no leftover board or wrong module.
            for (var component : components) {
                boolean present = ingredients.stream().anyMatch(ingredient -> ingredient.test(new ItemStack(component)));
                helper.assertTrue(present == used.contains(component), name + (present ? " uses " : " does not use ")
                        + BuiltInRegistries.ITEM.getKey(component));
            }
            helper.assertTrue(recipe.value().getResultItem(helper.getLevel().registryAccess()).is(BuiltInRegistries.ITEM.get(HomeLinkFarm.id(name))),
                    name + " makes something else");
        });
        // The sprinkler lights like a torch: its recipe takes glowstone dust.
        var sprinkler = recipes.byKey(HomeLinkFarm.id("copper_sprinkler")).orElse(null);
        helper.assertTrue(sprinkler != null && sprinkler.value().getIngredients().stream()
                .anyMatch(ingredient -> ingredient.test(new ItemStack(net.minecraft.world.item.Items.GLOWSTONE_DUST))),
                "Sprinkler recipe without glowstone dust");
        helper.succeed();
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
        helper.assertTrue(checked == 13, "Expected 13 HomeLink Farm blocks, found " + checked);
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
