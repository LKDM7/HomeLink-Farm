package fr.lkdm.homelink.farm.farm.bot;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * What a station knows about its FarmBot, reported by the robot and mirrored to clients and
 * HomeCore. Figures are rounded (battery in whole percent) so that an unchanged robot never
 * causes a new block update.
 *
 * @param name robot display name
 * @param battery battery in percent, 0..100
 * @param storage used robot inventory slots
 * @param target crop the robot is driving to or harvesting, if any
 * @param targetBlock registry id of that crop's block ("" when none)
 */
public record FarmBotSnapshot(String name, FarmBotState state, FarmBotFault fault, int battery, int storage, int harvested,
                              @Nullable BlockPos target, String targetBlock) {
    public Optional<BlockPos> targetPos() {
        return Optional.ofNullable(target);
    }

    public Component targetLabel() {
        if (target == null) return Component.translatable("gui.homelink_farm.farmbot.no_target");
        ResourceLocation id = ResourceLocation.tryParse(targetBlock);
        Component crop = id == null ? Component.literal(targetBlock) : BuiltInRegistries.BLOCK.get(id).getName();
        return Component.translatable("gui.homelink_farm.farmbot.target_value", crop, target.getX(), target.getY(), target.getZ());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("State", state.serializedName());
        tag.putString("Fault", fault.serializedName());
        tag.putInt("Battery", battery);
        tag.putInt("Storage", storage);
        tag.putInt("Harvested", harvested);
        if (target != null) {
            tag.put("Target", NbtUtils.writeBlockPos(target));
            tag.putString("TargetBlock", targetBlock);
        }
        return tag;
    }

    public static FarmBotSnapshot load(CompoundTag tag) {
        BlockPos target = NbtUtils.readBlockPos(tag, "Target").orElse(null);
        return new FarmBotSnapshot(tag.getString("Name"), FarmBotState.byName(tag.getString("State")),
                FarmBotFault.byName(tag.getString("Fault")), tag.getInt("Battery"), tag.getInt("Storage"),
                tag.getInt("Harvested"), target, target == null ? "" : tag.getString("TargetBlock"));
    }
}
