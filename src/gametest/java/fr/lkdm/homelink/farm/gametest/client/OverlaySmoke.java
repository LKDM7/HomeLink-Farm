package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.*;
import static fr.lkdm.homelink.farm.gametest.client.SmokeScenario.GROUND;

import fr.lkdm.homelink.farm.block.CopperSprinklerBlock;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.client.overlay.IrrigationOverlay;
import fr.lkdm.homelink.farm.client.rendering.LocateMarkers;
import fr.lkdm.homelink.farm.client.rendering.ZonePreview;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import fr.lkdm.homelink.farm.registry.ModItems;
import fr.lkdm.homelink.farm.registry.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;

/** Visual regression farm: terraces, overlaps, offline networks, zone selection and locate. */
final class OverlaySmoke {
    private static final BlockPos PUMP = new BlockPos(1,GROUND+1,6);
    private static final BlockPos MONITOR = new BlockPos(1,GROUND+1,1);
    private OverlaySmoke() { }

    static void define() {
        step("overlay fixtures", () -> true, () -> onServer(player -> {
            var level = player.serverLevel();
            level.setDayTime(1000);
            for (int x=2;x<=12;x++) for (int z=2;z<=10;z++) {
                int y=GROUND+(x>=8?2:0);
                for (int fill=GROUND;fill<y;fill++) level.setBlockAndUpdate(new BlockPos(x,fill,z),Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
                level.setBlockAndUpdate(new BlockPos(x,y+1,z),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,(x+z)%5+2));
            }
            level.setBlockAndUpdate(PUMP.below(),Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(PUMP,ModBlocks.IRRIGATION_PUMP.get().defaultBlockState());
            for (int y=GROUND+2;y<=GROUND+7;y++) level.setBlockAndUpdate(new BlockPos(1,y,6),ModBlocks.COPPER_PIPE.get().defaultBlockState());
            for (int x=2;x<=8;x++) level.setBlockAndUpdate(new BlockPos(x,GROUND+7,6),ModBlocks.COPPER_PIPE.get().defaultBlockState());
            for (int x : new int[] {5,8}) level.setBlockAndUpdate(new BlockPos(x,GROUND+6,6),
                    ModBlocks.COPPER_SPRINKLER.get().defaultBlockState().setValue(CopperSprinklerBlock.HANGING,true));
            level.setBlockAndUpdate(MONITOR,ModBlocks.CROP_MONITOR.get().defaultBlockState());
            ((CropMonitorBlockEntity)level.getBlockEntity(MONITOR)).setZone(new CropZone(new BlockPos(2,GROUND+1,2),new BlockPos(12,GROUND+3,10)));
            player.getAbilities().flying=true;
            player.setNoGravity(true);
            player.onUpdateAbilities();
            player.teleportTo(level,16,GROUND+11,18,143,32);
        }));
        step("overlay camera", () -> Minecraft.getInstance().screen==null, () -> {
            var client=Minecraft.getInstance();
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            client.options.hideGui=true;
            client.options.fov().set(42);
            client.player.getAbilities().flying=true;
            client.player.setNoGravity(true);
            IrrigationOverlay.setEnabled(true);
        });
        step("active overlay", () -> IrrigationOverlay.data().sprinklers().size()==2 &&
                IrrigationOverlay.data().sprinklers().stream().allMatch(s -> s.state()==IrrigationVisual.ACTIVE), () -> { });
        capture("irrigation_terraces");
        step("pump off", () -> true, () -> onServer(player -> ((IrrigationPumpBlockEntity)player.level().getBlockEntity(PUMP)).setEnabled(false)));
        step("offline overlay", () -> !IrrigationOverlay.data().sprinklers().isEmpty() &&
                IrrigationOverlay.data().sprinklers().stream().allMatch(s -> s.state()==IrrigationVisual.OFF), () -> { });
        capture("irrigation_off");
        step("pump fault", () -> true, () -> onServer(player -> {
            for (int x : new int[] {2,3,4,6}) player.serverLevel().setBlockAndUpdate(new BlockPos(x,GROUND+6,6),
                    ModBlocks.COPPER_SPRINKLER.get().defaultBlockState().setValue(CopperSprinklerBlock.HANGING,true));
            ((IrrigationPumpBlockEntity)player.level().getBlockEntity(PUMP)).setEnabled(true);
        }));
        step("fault overlay", () -> IrrigationOverlay.data().sprinklers().stream().anyMatch(s -> s.state()==IrrigationVisual.ERROR), () -> { });
        capture("irrigation_fault");
        step("monitor zone", () -> true, () -> {
            IrrigationOverlay.setEnabled(false);
            ZonePreview.showMonitorZone(MONITOR);
        });
        capture("monitor_zone");
        step("connector corners", () -> true, () -> {
            ZonePreview.clear();
            onServer(player -> {
                ItemStack connector=new ItemStack(ModItems.FARM_CONNECTOR.get());
                connector.set(ModDataComponents.ZONE_CORNER_A.get(),GlobalPos.of(player.level().dimension(),new BlockPos(2,GROUND+1,2)));
                connector.set(ModDataComponents.ZONE_CORNER_B.get(),GlobalPos.of(player.level().dimension(),new BlockPos(12,GROUND+3,10)));
                player.setItemInHand(InteractionHand.MAIN_HAND,connector);
            });
        });
        capture("connector_zone");
        step("diagnostic marker", () -> true, () -> {
            onServer(player -> {
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                player.teleportTo(player.serverLevel(),7.5,GROUND+4,0,0,40);
            });
            LocateMarkers.add(new BlockPos(7,GROUND+1,4),0xFFE6B46D,100);
        });
        capture("locate");
        step("marker expiration", () -> LocateMarkers.active().isEmpty(), () -> check(!IrrigationOverlay.enabled(),"Overlay remained enabled"));
    }

    private static void capture(String name) {
        pause(25);
        step("capture "+name, () -> true, () -> screenshot("view_"+name));
    }
}
