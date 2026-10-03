package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.*;

import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.client.screen.FarmDeviceScreen;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/** Exercises help on real menus, including drafts, scrolling, resize and Escape. */
final class HelpSmoke {
    private static final BlockPos POS = new BlockPos(0, SmokeScenario.GROUND + 1, 0);
    private HelpSmoke() { }

    static void define() {
        step("hide first-play tutorial", () -> true, () -> Minecraft.getInstance().getTutorial()
                .setStep(net.minecraft.client.tutorial.TutorialSteps.NONE));
        var blocks = java.util.List.of(ModBlocks.FARM_CONTROLLER, ModBlocks.CROP_MONITOR, ModBlocks.IRRIGATION_PUMP, ModBlocks.FARMBOT_STATION);
        var names = java.util.List.of("controller", "monitor", "pump", "station");
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.get(i);
            String name = names.get(i);
            step("place " + name, () -> Minecraft.getInstance().screen == null, () -> onServer(player -> {
                player.teleportTo(2, POS.getY(), 2);
                player.serverLevel().setBlockAndUpdate(POS, block.get().defaultBlockState());
            }));
            pause(10);
            step("open " + name, () -> true, () -> onServer(player ->
                    player.openMenu((AbstractFarmDeviceBlockEntity) player.level().getBlockEntity(POS), POS)));
            step("draft " + name, () -> Minecraft.getInstance().screen instanceof FarmDeviceScreen<?>, () -> nameBox().setValue("Draft help"));
            step("main controls and focus", () -> true, HelpSmoke::verifyControls);
            pause(5);
            step("screen capture", () -> true, () -> screenshot("help_" + name + "_button"));
            step("open help", () -> true, () -> pressKey("gui.homelink_farm.help.button"));
            step("help controls and focus", () -> true, HelpSmoke::verifyControls);
            pause(5);
            step("help top", () -> true, () -> {
                var screen = screen();
                check(screen.isHelpOpen(), "Help did not open");
                check(screen.children().stream().noneMatch(EditBox.class::isInstance), "Machine controls visible in help");
                screenshot("help_" + name + "_top");
                screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
                screen.resize(Minecraft.getInstance(), screen.width, screen.height);
            });
            pause(5);
            step("help bottom", () -> true, () -> {
                check(screen().isHelpOpen(), "Resize closed help");
                screenshot("help_" + name + "_bottom");
                pressKey("gui.homelink_farm.help.back");
                check(!screen().isHelpOpen() && nameBox().getValue().equals("Draft help"), "Back lost rename draft");
                pressKey("gui.homelink_farm.help.button");
                screen().keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
                pressKey("gui.homelink_farm.help.down");
                pressKey("gui.homelink_farm.help.up");
                screen().mouseScrolled(screen().width / 2.0, screen().height / 2.0, 0, -3);
                screen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                check(!screen().isHelpOpen() && nameBox().getValue().equals("Draft help"), "Escape closed menu or lost draft");
                Minecraft.getInstance().player.closeContainer();
            });
            pause(5);
        }
    }

    private static FarmDeviceScreen<?> screen() { return (FarmDeviceScreen<?>) Minecraft.getInstance().screen; }
    private static void verifyControls() {
        var screen = screen();
        for (var child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            check(widget.getX() >= 0 && widget.getY() >= 0
                    && widget.getX() + widget.getWidth() <= screen.width
                    && widget.getY() + widget.getHeight() <= screen.height,
                    "Farm control outside viewport: " + widget.getMessage().getString());
        }
        screen.setFocused(null);
        var expected = screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).filter(widget -> widget.active && widget.visible).toList();
        check(!expected.isEmpty(), "Farm screen has no active controls");
        var visited = new java.util.HashSet<AbstractWidget>();
        for (int i = 0; i < expected.size(); i++) {
            screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
            check(screen.getFocused() instanceof AbstractWidget widget
                    && widget.active && widget.visible && widget.isFocused(),
                    "Farm keyboard focus did not reach an active control");
            check(visited.add((AbstractWidget) screen.getFocused()), "Farm Tab cycle repeated a control before visiting all controls");
        }
        check(visited.containsAll(expected), "Farm Tab cycle skipped an active control");
    }
    private static EditBox nameBox() {
        return screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }
}
