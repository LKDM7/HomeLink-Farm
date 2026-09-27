package fr.lkdm.homelink.farm.blockentity;

import fr.lkdm.homelink.farm.farm.controller.ControllerLink;
import fr.lkdm.homelink.farm.farm.controller.FarmComponent;
import fr.lkdm.homelink.farm.farm.controller.FarmComponentKind;
import fr.lkdm.homelink.farm.farm.crop.ComparatorMode;
import fr.lkdm.homelink.farm.farm.crop.CropInspector;
import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.crop.CropScanner;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.crop.LoadedMonitors;
import fr.lkdm.homelink.farm.farm.crop.ZoneValidation;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import fr.lkdm.homelink.farm.network.DeviceCommand;
import fr.lkdm.homelink.farm.network.DeviceCommandTarget;
import fr.lkdm.homelink.farm.registry.ModBlockEntities;
import fr.lkdm.homelink.farm.registry.ModMenus;
import fr.lkdm.homelink.farm.farm.diagnostic.CropProblem;
import fr.lkdm.homelink.farm.network.MonitorProblemsPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Watches a zone of crops with an incremental, budgeted scan. */
public class CropMonitorBlockEntity extends AbstractFarmDeviceBlockEntity
        implements FarmComponent, ServerTickingDevice, DeviceCommandTarget {
    @Nullable
    private ControllerLink controllerLink;
    @Nullable
    private CropZone zone;
    private final CropScanner scanner = new CropScanner();
    private ComparatorMode comparatorMode = ComparatorMode.MATURITY;
    /** Last complete result: live on the server, mirrored on the client (never saved to disk). */
    @Nullable
    private CropScanResult result;

    public CropMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CROP_MONITOR.get(), pos, state);
    }

    public Optional<CropZone> zone() {
        return Optional.ofNullable(zone);
    }

    public Optional<CropScanResult> result() {
        return Optional.ofNullable(result);
    }

    public CropScanner scanner() {
        return scanner;
    }

    public ComparatorMode comparatorMode() {
        return comparatorMode;
    }

    public void setComparatorMode(ComparatorMode mode) {
        comparatorMode = mode;
        setChangedAndSync();
        updateComparators();
    }

    public int comparatorSignal() {
        return zone == null ? 0 : comparatorMode.signal(result);
    }

    private void updateComparators() {
        if (level != null && !level.isClientSide) level.updateNeighbourForOutputSignal(getBlockPos(), getBlockState().getBlock());
    }

    /** Applies a zone after validation. Server only. */
    public ZoneValidation.Result setZone(CropZone newZone) {
        ZoneValidation.Result validation = ZoneValidation.validate(getBlockPos(), newZone);
        if (validation != ZoneValidation.Result.OK) return validation;
        zone = newZone;
        resetScan();
        return validation;
    }

    public void clearZone() {
        zone = null;
        resetScan();
    }

    /** The monitor's whole chunk: 16 x 16 blocks, from the bottom of the world to its build limit. */
    public CropZone chunkZone() {
        int minY = level == null ? getBlockPos().getY() : level.getMinBuildHeight();
        int maxY = level == null ? getBlockPos().getY() : level.getMaxBuildHeight() - 1;
        return CropZone.chunkColumn(getBlockPos(), minY, maxY);
    }

    /**
     * Watches the monitor's whole chunk. Built by the server around the monitor itself, so it is
     * exempt from the size and distance limits of player-drawn zones; the scan budget per tick
     * still spreads each pass (about 10 s for a 384-block-high world).
     */
    public CropZone applyChunkZone() {
        zone = chunkZone();
        resetScan();
        return zone;
    }

    private void resetScan() {
        scanner.setZone(zone);
        result = null;
        setChangedAndSync();
        updateComparators();
        sendProblemsToViewers();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            LoadedMonitors.add(serverLevel, getBlockPos());
            scanner.setZone(zone);
            // Spread monitors loaded together (world start, chunk load) over two seconds.
            scanner.delayFirstPass(serverLevel.getGameTime(), serverLevel.random.nextInt(40));
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel serverLevel) LoadedMonitors.remove(serverLevel, getBlockPos());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) LoadedMonitors.remove(serverLevel, getBlockPos());
    }

    @Override
    public void serverTick(ServerLevel level) {
        if (!drawEnergy(level)) return;
        CropScanResult completed = scanner.tick(level, CropInspector.INSTANCE);
        if (completed == null) return;
        boolean changed = result == null || !result.sameFigures(completed);
        boolean problemsChanged = result == null || !result.samples().equals(completed.samples());
        int previousSignal = comparatorSignal();
        result = completed;
        if (changed) syncToClients();
        if (comparatorSignal() != previousSignal) updateComparators();
        if (problemsChanged) sendProblemsToViewers();
    }

    private void sendProblemsToViewers() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (ServerPlayer viewer : serverLevel.players()) {
            if (viewer.containerMenu instanceof FarmDeviceMenu menu && menu.pos().equals(getBlockPos())) sendProblemsTo(viewer);
        }
    }

    /** The current problem at this exact position, if the last scan reported one. */
    public Optional<CropProblem> findProblem(BlockPos pos) {
        if (result == null) return Optional.empty();
        return result.samples().stream().filter(problem -> problem.pos().equals(pos)).findFirst();
    }

    @Override
    protected void onMenuOpened(ServerPlayer player) {
        sendProblemsTo(player);
    }

    /** Sends the located problems to one player (only players viewing this monitor get them). */
    public void sendProblemsTo(ServerPlayer player) {
        List<CropProblem> problems = result == null ? List.of() : result.samples();
        PacketDistributor.sendToPlayer(player, new MonitorProblemsPayload(getBlockPos(), problems));
    }

    @Override
    public void handleCommand(ServerPlayer player, DeviceCommand command, int argument) {
        switch (command) {
            case ZONE_AUTO -> {
                CropZone auto = applyChunkZone();
                player.displayClientMessage(ZoneValidation.Result.OK.message(auto).withStyle(ChatFormatting.GREEN), true);
            }
            case ZONE_CLEAR -> clearZone();
            case RESCAN -> scanner.requestPass();
            case CYCLE_COMPARATOR -> setComparatorMode(comparatorMode.next());
            default -> { }
        }
    }

    @Override
    protected long energyPerMinute() {
        return configured(fr.lkdm.homelink.farm.config.FarmServerConfig.CROP_MONITOR_ENERGY);
    }

    @Override public UUID componentId() { return deviceId(); }
    @Override public FarmComponentKind componentKind() { return FarmComponentKind.CROP_MONITOR; }
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
        return Component.translatableWithFallback("block.homelink_farm.crop_monitor", "Crop Monitor");
    }

    @Override
    protected MenuType<FarmDeviceMenu> menuType() {
        return ModMenus.CROP_MONITOR.get();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerLink != null) tag.put("Controller", controllerLink.save());
        if (zone != null) tag.put("Zone", zone.save());
        tag.putString("ComparatorMode", comparatorMode.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerLink = ControllerLink.load(tag.getCompound("Controller")).orElse(null);
        zone = CropZone.load(tag.getCompound("Zone")).orElse(null);
        comparatorMode = ComparatorMode.byName(tag.getString("ComparatorMode"));
        // Only present in client sync packets.
        result = tag.contains("Result") ? CropScanResult.load(tag.getCompound("Result")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (result != null) tag.put("Result", result.save());
        return tag;
    }
}
