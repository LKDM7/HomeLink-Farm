package fr.lkdm.homelink.farm.blockentity;

import fr.lkdm.homelink.farm.farm.controller.ControllerLink;
import fr.lkdm.homelink.farm.farm.controller.FarmComponent;
import fr.lkdm.homelink.farm.farm.controller.FarmComponentKind;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationNetwork;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import fr.lkdm.homelink.farm.farm.irrigation.PumpSnapshot;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import fr.lkdm.homelink.farm.farm.irrigation.RedstoneMode;
import fr.lkdm.homelink.farm.homelink.HomeCoreIntegration;
import fr.lkdm.homelink.farm.homelink.IrrigationPumpDevice;
import fr.lkdm.homelink.farm.homelink.PumpView;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import net.minecraft.resources.ResourceKey;
import fr.lkdm.homelink.farm.network.DeviceCommand;
import fr.lkdm.homelink.farm.network.DeviceCommandTarget;
import fr.lkdm.homelink.farm.registry.ModBlockEntities;
import fr.lkdm.homelink.farm.registry.ModMenus;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Irrigation Pump. Requires a real water source block next to it (it never creates water)
 * and feeds the copper network it is connected to. Re-evaluated once per second.
 */
public class IrrigationPumpBlockEntity extends AbstractFarmDeviceBlockEntity
        implements FarmComponent, ServerTickingDevice, DeviceCommandTarget, PumpView {
    public static final int UPDATE_INTERVAL = 20;

    private boolean enabled = true;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    @Nullable
    private ControllerLink controllerLink;
    // Recomputed from the world, never saved.
    private boolean water;
    private boolean powered;
    private boolean waterDirty = true;
    private boolean updateNow = true;
    private PumpSnapshot snapshot = PumpSnapshot.INITIAL;
    @Nullable
    private IrrigationPumpDevice homeCoreDevice;
    /** Irrigated crops are recounted every CROP_COUNT_EVERY scheduled updates (5 s). */
    private static final int CROP_COUNT_EVERY = 5;
    private int cropCountCycle = CROP_COUNT_EVERY - 1;
    private int irrigatedCrops;

    public IrrigationPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRRIGATION_PUMP.get(), pos, state);
    }

    /** Whether this pump currently supplies water to its network. */
    public boolean canSupply() {
        return enabled && water && redstoneMode.allows(powered);
    }

    public RedstoneMode redstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(RedstoneMode mode) {
        if (redstoneMode == mode) return;
        redstoneMode = mode;
        updateNow = true;
        setChangedAndSync();
    }

    /** Comparator output derived from the current status (see {@link PumpStatus#comparatorSignal}). */
    public int comparatorSignal() {
        return snapshot.status().comparatorSignal(snapshot.sprinklers());
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public PumpSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        updateNow = true;
        if (homeCoreDevice != null) homeCoreDevice.refresh(snapshot, enabled);
        setChangedAndSync();
    }

    @Override
    public boolean exposedToHomeCore() {
        return true;
    }

    @Override
    public boolean isOperational() {
        return level != null && !isRemoved();
    }

    @Override
    public Optional<BlockPos> devicePosition() {
        return Optional.of(getBlockPos().immutable());
    }

    @Override
    public Optional<ResourceKey<Level>> deviceDimension() {
        return level == null ? Optional.empty() : Optional.of(level.dimension());
    }

    public Optional<IrrigationPumpDevice> homeCoreDevice() {
        return Optional.ofNullable(homeCoreDevice);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            homeCoreDevice = HomeCoreIntegration.register(serverLevel, this, IrrigationPumpDevice.class).orElse(null);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        releaseHomeCoreDevice();
    }

    private void releaseHomeCoreDevice() {
        if (homeCoreDevice != null && level instanceof ServerLevel serverLevel) HomeCoreIntegration.unregister(serverLevel, homeCoreDevice);
        homeCoreDevice = null;
    }

    public void onNeighborChanged() {
        waterDirty = true;
        updateNow = true;
    }

    /** A real water source (including a waterlogged block) on any face, or the pump itself submerged. */
    public static boolean hasAdjacentWaterSource(Level level, BlockPos pos) {
        FluidState own = level.getFluidState(pos);
        if (own.is(FluidTags.WATER) && own.isSource()) return true;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.isLoaded(neighbor)) continue;
            FluidState fluid = level.getFluidState(neighbor);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()) return true;
        }
        return false;
    }

    @Override
    public void serverTick(ServerLevel level) {
        boolean scheduled = Math.floorMod(level.getGameTime() + getBlockPos().hashCode(), UPDATE_INTERVAL) == 0;
        if (!scheduled && !updateNow) return;
        updateNow = false;
        if (waterDirty || scheduled) {
            water = hasAdjacentWaterSource(level, getBlockPos());
            powered = level.hasNeighborSignal(getBlockPos());
            waterDirty = false;
        }
        IrrigationManager manager = IrrigationManager.get(level);
        IrrigationNetwork network = manager.networkForPump(getBlockPos());
        network.evaluate(level);
        if (scheduled && ++cropCountCycle % CROP_COUNT_EVERY == 0 || !network.state().irrigates()) {
            irrigatedCrops = manager.countIrrigatedCrops(network);
        }
        PumpSnapshot updated = new PumpSnapshot(PumpStatus.of(enabled, redstoneMode.allows(powered), water, network), water, network.sprinklers().size(),
                network.capacity(), network.pipes(), network.pumps().size(), network.incomplete(), irrigatedCrops);
        if (updated.equals(snapshot)) return;
        snapshot = updated;
        if (homeCoreDevice != null) homeCoreDevice.refresh(snapshot, enabled);
        IrrigationVisual visual = updated.status() == PumpStatus.ACTIVE ? IrrigationVisual.ACTIVE
                : updated.status().isFailure() ? IrrigationVisual.ERROR : IrrigationVisual.OFF;
        BlockState state = getBlockState();
        if (state.getValue(IrrigationVisual.PROPERTY) != visual) {
            level.setBlock(getBlockPos(), state.setValue(IrrigationVisual.PROPERTY, visual), Block.UPDATE_CLIENTS);
        }
        level.updateNeighbourForOutputSignal(getBlockPos(), getBlockState().getBlock());
        syncToClients();
    }

    @Override
    public void handleCommand(ServerPlayer player, DeviceCommand command, int argument) {
        if (command == DeviceCommand.TOGGLE_ENABLED) setEnabled(!enabled);
        if (command == DeviceCommand.CYCLE_REDSTONE) setRedstoneMode(redstoneMode.next());
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        releaseHomeCoreDevice();
        if (level instanceof ServerLevel serverLevel) IrrigationManager.get(serverLevel).invalidateAround(getBlockPos());
    }

    @Override public UUID componentId() { return deviceId(); }
    @Override public FarmComponentKind componentKind() { return FarmComponentKind.IRRIGATION_PUMP; }
    @Override public BlockPos componentPos() { return getBlockPos(); }
    @Override public Optional<ControllerLink> controllerLink() { return Optional.ofNullable(controllerLink); }

    @Override
    public void setControllerLink(ControllerLink link) {
        controllerLink = link;
        setChangedAndSync();
    }

    @Override
    public boolean isLinked() {
        return controllerLink != null || super.isLinked();
    }

    @Override
    public void clearControllerLink() {
        if (controllerLink == null) return;
        controllerLink = null;
        setChangedAndSync();
    }

    @Override
    protected Component defaultName() {
        return Component.translatableWithFallback("block.homelink_farm.irrigation_pump", "Irrigation Pump");
    }

    @Override
    protected MenuType<FarmDeviceMenu> menuType() {
        return ModMenus.IRRIGATION_PUMP.get();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Enabled", enabled);
        tag.putString("RedstoneMode", redstoneMode.name());
        if (controllerLink != null) tag.put("Controller", controllerLink.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        redstoneMode = RedstoneMode.byName(tag.getString("RedstoneMode"));
        controllerLink = ControllerLink.load(tag.getCompound("Controller")).orElse(null);
        if (tag.contains("Snapshot")) snapshot = PumpSnapshot.load(tag.getCompound("Snapshot"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Snapshot", snapshot.save());
        return tag;
    }
}
