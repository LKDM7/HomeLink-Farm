package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.check;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pause;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.screenshot;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.step;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * Phase 8 client checks: every block state and item has a real model and a translated name in
 * the selected language, and the configuration screen opens with translated entries.
 */
final class AssetSmoke {
    private AssetSmoke() {
    }

    /** All buttons of a screen, including those nested in lists, in display order. */
    static void collectButtons(ContainerEventHandler parent, List<Button> out) {
        for (GuiEventListener child : parent.children()) {
            if (child instanceof Button button) out.add(button);
            else if (child instanceof ContainerEventHandler container) collectButtons(container, out);
        }
    }

    static void define() {
        step("assets", () -> true, () -> {
            Minecraft client = Minecraft.getInstance();
            var missing = client.getModelManager().getMissingModel();
            int states = 0;
            int items = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(HomeLinkFarm.MOD_ID)) continue;
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    check(client.getBlockRenderer().getBlockModelShaper().getBlockModel(state) != missing, "Missing model for " + state);
                    states++;
                }
            }
            for (var item : BuiltInRegistries.ITEM) {
                if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(HomeLinkFarm.MOD_ID)) continue;
                ItemStack stack = new ItemStack(item);
                check(client.getItemRenderer().getModel(stack, client.level, null, 0) != missing, "Missing item model for " + item);
                check(I18n.exists(item.getDescriptionId()), "Missing translation " + item.getDescriptionId());
                items++;
            }
            HomeLinkFarm.LOGGER.info("HOMELINK_FARM_ASSETS_OK language={} block_states={} items={}",
                    client.getLanguageManager().getSelected(), states, items);
        });
        // Wait until the world is on screen (the loading screen would replace ours when it closes).
        step("world displayed", () -> Minecraft.getInstance().screen == null, () -> { });
        pause(20);
        step("open config screen", () -> true, () -> {
            var mod = ModList.get().getModContainerById(HomeLinkFarm.MOD_ID).orElseThrow();
            check(mod.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class).isPresent(), "config screen not registered");
            Minecraft.getInstance().setScreen(new ConfigurationScreen(mod, null));
        });
        // With a single config file NeoForge opens its section directly.
        step("config section", () -> Minecraft.getInstance().screen instanceof ConfigurationScreen.ConfigurationSectionScreen, () -> { });
        pause(10);
        step("config section screenshot", () -> true, () -> screenshot("config_section"));
        // Category buttons live inside the options list; the third one opens "Irrigation".
        step("open irrigation category", () -> true, () -> {
            List<Button> buttons = new ArrayList<>();
            collectButtons(Minecraft.getInstance().screen, buttons);
            check(buttons.size() >= 4, "expected 3 category buttons + Done, found " + buttons.size());
            buttons.get(2).onPress();
        });
        pause(15);
        step("config irrigation screenshot", () -> true, () -> {
            check(I18n.exists("homelink_farm.configuration.maxSprinklersPerPump"), "config entry not translated");
            screenshot("config_irrigation");
        });
        step("close config", () -> true, () -> Minecraft.getInstance().setScreen(null));
    }
}
