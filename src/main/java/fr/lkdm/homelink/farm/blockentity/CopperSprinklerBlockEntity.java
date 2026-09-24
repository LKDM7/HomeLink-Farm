package fr.lkdm.homelink.farm.blockentity;

import fr.lkdm.homelink.farm.block.CopperSprinklerBlock;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationCoverage;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import net.neoforged.neoforge.common.FarmlandWaterManager;
import net.neoforged.neoforge.common.ticket.AABBTicket;
import org.jetbrains.annotations.Nullable;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationNetwork;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import fr.lkdm.homelink.farm.farm.irrigation.NetworkState;
import fr.lkdm.homelink.farm.registry.ModBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tracks whether its network irrigates and mirrors it in the block state (texture, overlay,
 * particles). Registers itself with the level's {@link IrrigationManager} while loaded.
 */
public class CopperSprinklerBlockEntity extends BlockEntity {
    public static final int UPDATE_INTERVAL = 20;

    @Nullable
    private AABBTicket hydration;

    public CopperSprinklerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COPPER_SPRINKLER.get(), pos, state);
    }

    /** State of the network feeding this sprinkler; empty when not connected to any pump. */
    public Optional<NetworkState> networkState(ServerLevel level) {
        return IrrigationManager.get(level).networkAt(getBlockPos()).map(network -> network.evaluate(level));
    }

    public Optional<IrrigationNetwork> network(ServerLevel level) {
        return IrrigationManager.get(level).networkAt(getBlockPos());
    }

    /** Whether this sprinkler irrigates right now (server truth). */
    public boolean irrigating(ServerLevel level) {
        return networkState(level).map(NetworkState::irrigates).orElse(false);
    }

    public void serverTick(ServerLevel level) {
        if (Math.floorMod(level.getGameTime() + getBlockPos().hashCode(), UPDATE_INTERVAL) != 0) return;
        IrrigationVisual visual = networkState(level).map(IrrigationVisual::of).orElse(IrrigationVisual.OFF);
        BlockState state = getBlockState();
        if (state.getValue(IrrigationVisual.PROPERTY) != visual) {
            level.setBlock(getBlockPos(), state.setValue(IrrigationVisual.PROPERTY, visual), Block.UPDATE_CLIENTS);
        }
        updateHydration(level, visual == IrrigationVisual.ACTIVE);
    }

    /**
     * An active sprinkler keeps the farmland of its area hydrated through NeoForge's
     * {@link FarmlandWaterManager} (vanilla farmland then turns moist on its own random ticks).
     */
    private void updateHydration(ServerLevel level, boolean active) {
        if (active && hydration == null) {
            hydration = FarmlandWaterManager.addAABBTicket(level, IrrigationCoverage.area(getBlockPos(), FarmServerConfig.SPRINKLER_RANGE.get(),
                    CopperSprinklerBlock.isHanging(getBlockState())));
        } else if (!active) {
            releaseHydration();
        }
    }

    private void releaseHydration() {
        if (hydration != null) {
            hydration.invalidate();
            hydration = null;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) IrrigationManager.get(serverLevel).sprinklerLoaded(getBlockPos());
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        releaseHydration();
        if (level instanceof ServerLevel serverLevel) IrrigationManager.get(serverLevel).sprinklerUnloaded(getBlockPos());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        releaseHydration();
        if (level instanceof ServerLevel serverLevel) IrrigationManager.get(serverLevel).sprinklerUnloaded(getBlockPos());
    }
}
