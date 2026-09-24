package fr.lkdm.homelink.farm.gametest;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homelink.farm.block.AbstractFarmDeviceBlock;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmComponentKind;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.controller.LinkResult;
import fr.lkdm.homelink.farm.homelink.FarmControllerDevice;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 1 in-game checks: placement, HomeCore registration, linking, removal and NBT persistence. */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundationGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 1);
    private static final BlockPos MONITOR = new BlockPos(3, 1, 1);
    private static final BlockPos MONITOR_2 = new BlockPos(3, 1, 3);

    private FoundationGameTests() {
    }

    @GameTest(template = "empty")
    public static void controllerRegistersAsHomeCoreDevice(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.FARM_CONTROLLER.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER);
        UUID id = controller.deviceId();
        helper.succeedWhen(() -> {
            Optional<DashboardDevice> device = DashboardAPI.devices(helper.getLevel().getServer()).get(id);
            helper.assertTrue(device.isPresent(), "Farm Controller not registered in HomeCore");
            helper.assertTrue(device.get() instanceof FarmControllerDevice, "Unexpected device class");
            helper.assertTrue(device.get().deviceType().equals(FarmControllerDevice.TYPE), "Wrong HomeCore device type");
            helper.assertTrue(device.get().status().state() == DeviceStatus.State.ONLINE, "Controller should be ONLINE");
            helper.assertTrue(device.get().position().equals(Optional.of(helper.absolutePos(CONTROLLER))), "Wrong device position");
        });
    }

    @GameTest(template = "empty")
    public static void controllerUnregistersWhenBroken(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.FARM_CONTROLLER.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER);
        UUID id = controller.deviceId();
        var registry = DashboardAPI.devices(helper.getLevel().getServer());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registry.get(id).isPresent(), "Controller never registered"))
                .thenExecute(() -> helper.destroyBlock(CONTROLLER))
                .thenExecute(() -> helper.assertTrue(registry.get(id).isEmpty(), "Controller still registered after removal"))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void linkAndUnlinkComponents(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.FARM_CONTROLLER.get());
        helper.setBlock(MONITOR, ModBlocks.CROP_MONITOR.get());
        helper.setBlock(MONITOR_2, ModBlocks.CROP_MONITOR.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER);
        CropMonitorBlockEntity monitor = helper.getBlockEntity(MONITOR);
        CropMonitorBlockEntity monitor2 = helper.getBlockEntity(MONITOR_2);

        helper.assertTrue(FarmLinkService.link(helper.getLevel(), controller, monitor, 32) == LinkResult.LINKED, "First link failed");
        helper.assertTrue(FarmLinkService.link(helper.getLevel(), controller, monitor, 32) == LinkResult.ALREADY_LINKED, "Relink should be idempotent");
        helper.assertTrue(FarmLinkService.link(helper.getLevel(), controller, monitor2, 1) == LinkResult.CONTROLLER_FULL, "Capacity not enforced");
        helper.assertTrue(FarmLinkService.link(helper.getLevel(), controller, monitor2, 32) == LinkResult.LINKED, "Second link failed");
        helper.assertTrue(controller.linkedComponents().size() == 2, "Controller should hold 2 components");
        helper.assertTrue(controller.linkedComponents().count(FarmComponentKind.CROP_MONITOR) == 2, "Kind count wrong");
        helper.assertTrue(monitor.controllerLink().map(link -> link.controllerId().equals(controller.deviceId())).orElse(false),
                "Monitor does not point to its controller");

        helper.destroyBlock(MONITOR);
        helper.assertTrue(controller.linkedComponents().size() == 1, "Broken monitor was not unlinked");
        helper.assertFalse(controller.linkedComponents().contains(monitor.componentId()), "Broken monitor still listed");

        helper.destroyBlock(CONTROLLER);
        helper.assertTrue(monitor2.controllerLink().isEmpty(), "Monitor kept a link to a broken controller");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void linkedDevicesBlinkTheirLights(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.FARM_CONTROLLER.get());
        helper.setBlock(MONITOR, ModBlocks.CROP_MONITOR.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER);
        CropMonitorBlockEntity monitor = helper.getBlockEntity(MONITOR);
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertFalse(linked(helper, CONTROLLER), "Lonely controller shows linked lights");
                    helper.assertFalse(linked(helper, MONITOR), "Unlinked monitor shows linked lights");
                    FarmLinkService.link(helper.getLevel(), controller, monitor, 32);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(linked(helper, CONTROLLER), "Controller lights off although a component is linked");
                    helper.assertTrue(linked(helper, MONITOR), "Monitor lights off although linked");
                })
                .thenExecute(() -> helper.destroyBlock(CONTROLLER))
                .thenWaitUntil(() -> helper.assertFalse(linked(helper, MONITOR), "Monitor lights still blinking after its controller broke"))
                .thenSucceed();
    }

    private static boolean linked(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(AbstractFarmDeviceBlock.LINKED);
    }

    @GameTest(template = "empty")
    public static void playerWithoutOwnershipCannotLink(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.FARM_CONTROLLER.get());
        helper.setBlock(MONITOR, ModBlocks.CROP_MONITOR.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER);
        controller.setOwner(UUID.randomUUID(), "someone_else");
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "farm_intruder"));
        LinkResult result = FarmLinkService.link(player, helper.getLevel(), helper.absolutePos(CONTROLLER), helper.absolutePos(MONITOR));
        helper.assertTrue(result == LinkResult.NO_PERMISSION, "Expected NO_PERMISSION, got " + result);
        helper.assertTrue(controller.linkedComponents().size() == 0, "Unauthorized link was applied");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockEntityDataSurvivesSaveLoad(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.FARM_CONTROLLER.get());
        helper.setBlock(MONITOR, ModBlocks.CROP_MONITOR.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER);
        CropMonitorBlockEntity monitor = helper.getBlockEntity(MONITOR);
        controller.setCustomName("  Main§c Farm\n ");
        FarmLinkService.link(helper.getLevel(), controller, monitor, 32);

        var registries = helper.getLevel().registryAccess();
        BlockEntity reloadedController = BlockEntity.loadStatic(controller.getBlockPos(), controller.getBlockState(),
                controller.saveWithFullMetadata(registries), registries);
        BlockEntity reloadedMonitor = BlockEntity.loadStatic(monitor.getBlockPos(), monitor.getBlockState(),
                monitor.saveWithFullMetadata(registries), registries);
        helper.assertTrue(reloadedController instanceof FarmControllerBlockEntity, "Controller did not reload");
        helper.assertTrue(reloadedMonitor instanceof CropMonitorBlockEntity, "Monitor did not reload");
        FarmControllerBlockEntity c = (FarmControllerBlockEntity) reloadedController;
        CropMonitorBlockEntity m = (CropMonitorBlockEntity) reloadedMonitor;
        helper.assertTrue(c.deviceId().equals(controller.deviceId()), "Controller UUID not persisted");
        helper.assertTrue(c.customName().equals("Main Farm"), "Name not sanitized/persisted: '" + c.customName() + "'");
        helper.assertTrue(c.linkedComponents().contains(monitor.componentId()), "Components not persisted");
        helper.assertTrue(m.deviceId().equals(monitor.deviceId()), "Monitor UUID not persisted");
        helper.assertTrue(m.controllerLink().equals(monitor.controllerLink()), "Monitor link not persisted");
        helper.succeed();
    }
}
