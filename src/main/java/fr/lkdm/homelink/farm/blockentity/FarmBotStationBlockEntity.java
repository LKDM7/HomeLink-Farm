package fr.lkdm.homelink.farm.blockentity;

import fr.lkdm.homelink.farm.block.AbstractFarmDeviceBlock;
import fr.lkdm.homelink.farm.entity.FarmBotEntity;
import fr.lkdm.homelink.farm.farm.FarmAccess;
import fr.lkdm.homelink.farm.farm.bot.FarmBotDock;
import fr.lkdm.homelink.farm.farm.bot.FarmBotHome;
import fr.lkdm.homelink.farm.farm.bot.FarmBotSnapshot;
import fr.lkdm.homelink.farm.farm.bot.StationOutputTransfer;
import fr.lkdm.homelink.farm.farm.controller.ControllerLink;
import fr.lkdm.homelink.farm.farm.controller.FarmComponent;
import fr.lkdm.homelink.farm.farm.controller.FarmComponentKind;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.farm.crop.LoadedMonitors;
import fr.lkdm.homelink.farm.homelink.FarmBotStationDevice;
import fr.lkdm.homelink.farm.homelink.FarmBotStationView;
import fr.lkdm.homelink.farm.homelink.HomeCoreIntegration;
import fr.lkdm.homelink.farm.item.FarmBotData;
import fr.lkdm.homelink.farm.item.FarmBotItem;
import fr.lkdm.homelink.farm.menu.FarmBotStationMenu;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import fr.lkdm.homelink.farm.network.DeviceCommand;
import fr.lkdm.homelink.farm.network.DeviceCommandTarget;
import fr.lkdm.homelink.farm.registry.ModBlockEntities;
import fr.lkdm.homelink.farm.registry.ModEntities;
import fr.lkdm.homelink.farm.registry.ModMenus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * FarmBot Station: dock, charger, departure and return point, output inventory and local
 * controller of one FarmBot. Linked to a Farm Controller with the Farm Connector like any
 * other component (optional): the robot then works on every Crop Monitor of that farm, or on the one
 * pinned with the MONITOR button. Without a controller it works from the nearest Crop Monitor of the
 * same owner within link range.
 * The dock is the block in front of the station (its FACING side). A storage input placed against
 * the station (HomeLink Storage Deposit) receives its output every second.
 */
public class FarmBotStationBlockEntity extends AbstractFarmDeviceBlockEntity
        implements FarmComponent, ServerTickingDevice, DeviceCommandTarget, FarmBotHome, FarmBotStationView {
    public static final int OUTPUT_SIZE = 9;
    /** How often the station checks its robot and monitor (cheap lookups, no scanning). */
    static final int CHECK_INTERVAL = 40;
    /** How often the output is emptied into an adjacent storage input ({@link StationOutputTransfer}). */
    static final int OUTPUT_INTERVAL = 20;

    public enum InstallResult {
        INSTALLED, OCCUPIED, DOCK_BLOCKED, NO_PERMISSION, FAILED;

        public boolean success() {
            return this == INSTALLED;
        }

        public net.minecraft.network.chat.MutableComponent message() {
            return Component.translatable("message.homelink_farm.farmbot.install." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    @Nullable
    private ControllerLink controllerLink;
    @Nullable
    private UUID robot;
    /** Last known robot position (not synchronized): tells a missing robot from an unloaded one. */
    @Nullable
    private BlockPos robotPos;
    private boolean working = true;
    private boolean returnRequested;
    @Nullable
    private UUID monitorId;
    @Nullable
    private BlockPos monitorPos;
    private String monitorName = "";
    /** With a controller: work on every Crop Monitor of the farm instead of a single one. */
    private boolean wholeFarm;
    private int farmMonitors;
    @Nullable
    private FarmBotSnapshot snapshot;
    private int outputUsed;
    @Nullable
    private FarmBotStationDevice homeCoreDevice;
    private final ItemStackHandler output = new ItemStackHandler(OUTPUT_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            int used = countUsed(this);
            if (used != outputUsed) {
                outputUsed = used;
                syncToClients();
                refreshDevice();
            }
        }
    };

    public FarmBotStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FARMBOT_STATION.get(), pos, state);
    }

    // ----- Robot installation ---------------------------------------------------------------

    /** Installs the robot carried by {@code stack}; all checks happen here, on the server. */
    public InstallResult install(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!FarmAccess.canManage(player, this)) return InstallResult.NO_PERMISSION;
        if (robot != null && !robotGone(level)) return InstallResult.OCCUPIED;
        BlockPos dock = dockPos();
        if (!FarmBotDock.free(level, dock)) return InstallResult.DOCK_BLOCKED;
        FarmBotEntity bot = ModEntities.FARMBOT.get().create(level);
        if (bot == null) return InstallResult.FAILED;
        Vec3 center = Vec3.atBottomCenterOf(dock);
        bot.moveTo(center.x, center.y, center.z, dockFacing().toYRot(), 0.0F);
        bot.alignOnDock(center, dockFacing());
        if (!level.noCollision(bot)) return InstallResult.DOCK_BLOCKED;
        FarmBotData data = FarmBotItem.data(stack);
        bot.assignStation(getBlockPos(), deviceId(), player.getUUID());
        bot.setEnergy(FarmBotEntity.capacity() * data.battery() / 100.0);
        bot.addHarvested(data.harvested());
        if (stack.has(DataComponents.CUSTOM_NAME)) bot.setCustomName(stack.getHoverName());
        bot.setDocked(true);
        if (!level.addFreshEntity(bot)) return InstallResult.FAILED;
        robot = bot.getUUID();
        robotPos = dock.immutable();
        snapshot = bot.snapshot(bot.brain().state(), bot.brain().fault(), null, "");
        setChangedAndSync();
        refreshDevice();
        return InstallResult.INSTALLED;
    }

    public boolean hasRobot() {
        return robot != null;
    }

    public Optional<UUID> robotId() {
        return Optional.ofNullable(robot);
    }

    /** Client + server: the last state reported by the robot. */
    public Optional<FarmBotSnapshot> snapshot() {
        return robot == null ? Optional.empty() : Optional.ofNullable(snapshot);
    }

    /**
     * The robot is known to be gone: its entity is not loaded although the chunk it was last
     * seen in has its entities loaded. An unloaded robot is never considered gone.
     */
    private boolean robotGone(ServerLevel level) {
        if (robot == null) return true;
        if (level.getEntity(robot) != null) return false;
        BlockPos last = robotPos != null ? robotPos : dockPos();
        return level.areEntitiesLoaded(ChunkPos.asLong(last));
    }

    @Nullable
    private FarmBotEntity loadedRobot(ServerLevel level) {
        return robot != null && level.getEntity(robot) instanceof FarmBotEntity bot ? bot : null;
    }

    // ----- FarmBotHome ----------------------------------------------------------------------

    @Override
    public UUID homeId() {
        return deviceId();
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(AbstractFarmDeviceBlock.FACING) ? state.getValue(AbstractFarmDeviceBlock.FACING) : Direction.NORTH;
    }

    @Override
    public BlockPos dockPos() {
        return getBlockPos().relative(facing());
    }

    @Override
    public Direction dockFacing() {
        // The robot backs onto the station: rear charging plate against it, ready to drive off.
        return facing();
    }

    @Override
    public boolean owns(UUID candidate) {
        return candidate.equals(robot);
    }

    @Override
    public boolean working() {
        return working;
    }

    @Override
    public boolean consumeReturnRequest() {
        boolean requested = returnRequested;
        returnRequested = false;
        return requested;
    }

    @Override
    public List<CropMonitorBlockEntity> cropSources(ServerLevel level) {
        if (wholeFarm()) {
            List<CropMonitorBlockEntity> sources = new ArrayList<>();
            for (MonitorEntry entry : farmChoices(level).orElse(List.of())) {
                resolve(level, entry.id(), entry.pos()).filter(this::inFarm).ifPresent(sources::add);
            }
            return sources;
        }
        return resolve(level, monitorId, monitorPos)
                .filter(monitor -> controllerLink != null ? inFarm(monitor) : usableNearby(monitor))
                .map(List::of).orElse(List.of());
    }

    @Override
    public ItemStack acceptHarvest(ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(output, stack, false);
    }

    @Override
    public void report(FarmBotSnapshot report) {
        if (report.equals(snapshot)) return;
        snapshot = report;
        setChanged();
        syncToClients();
        refreshDevice();
    }

    @Override
    public void locate(BlockPos position) {
        robotPos = position.immutable();
    }

    @Override
    public void releaseRobot(UUID candidate) {
        if (!candidate.equals(robot)) return;
        robot = null;
        robotPos = null;
        snapshot = null;
        returnRequested = false;
        setChangedAndSync();
        refreshDevice();
    }

    // ----- Output ---------------------------------------------------------------------------

    public ItemStackHandler output() {
        return output;
    }

    /** Client + server: occupied output slots. */
    public int outputUsed() {
        return outputUsed;
    }

    private static int countUsed(ItemStackHandler handler) {
        int used = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) used++;
        }
        return used;
    }

    // ----- Crop Monitor choice --------------------------------------------------------------

    /** A Crop Monitor the robot may work from (possibly unloaded right now). */
    private record MonitorEntry(UUID id, BlockPos pos) {
    }

    public Optional<UUID> monitorId() {
        return Optional.ofNullable(monitorId);
    }

    /** Client + server: display name of the chosen Crop Monitor ("" when none or in whole-farm mode). */
    public String monitorName() {
        return monitorName;
    }

    /** Client + server: linked to a Farm Controller and working on every Crop Monitor of that farm. */
    public boolean wholeFarm() {
        return controllerLink != null && wholeFarm;
    }

    /** Client + server: number of Crop Monitors of the farm (whole-farm mode). */
    public int farmMonitors() {
        return farmMonitors;
    }

    /** Crop Monitors linked to this station's Farm Controller, nearest first; empty when the controller is not loaded. */
    private Optional<List<MonitorEntry>> farmChoices(ServerLevel level) {
        if (controllerLink == null || !level.isLoaded(controllerLink.controllerPos())) return Optional.empty();
        if (!(level.getBlockEntity(controllerLink.controllerPos()) instanceof FarmControllerBlockEntity controller)
                || !controller.deviceId().equals(controllerLink.controllerId())) {
            return Optional.empty();
        }
        return Optional.of(controller.linkedComponents().all().stream()
                .filter(entry -> entry.kind() == FarmComponentKind.CROP_MONITOR)
                .sorted(Comparator.comparingDouble(entry -> entry.pos().distSqr(getBlockPos())))
                .map(entry -> new MonitorEntry(entry.id(), entry.pos()))
                .toList());
    }

    /**
     * Without a Farm Controller: loaded Crop Monitors within link range that belong to the same
     * owner, nearest first. Reads the index of loaded monitors, never the world.
     */
    private List<MonitorEntry> nearbyChoices(ServerLevel level) {
        List<MonitorEntry> choices = new ArrayList<>();
        for (BlockPos pos : LoadedMonitors.in(level)) {
            if (level.getBlockEntity(pos) instanceof CropMonitorBlockEntity monitor && usableNearby(monitor)) {
                choices.add(new MonitorEntry(monitor.componentId(), pos));
            }
        }
        choices.sort(Comparator.comparingDouble(entry -> entry.pos().distSqr(getBlockPos())));
        return choices;
    }

    private boolean usableNearby(CropMonitorBlockEntity monitor) {
        int range = FarmServerConfig.MAX_LINK_DISTANCE.get();
        boolean sameOwner = owner().isEmpty() || monitor.owner().isEmpty() || owner().equals(monitor.owner());
        return sameOwner && monitor.getBlockPos().distSqr(getBlockPos()) <= (double) range * range;
    }

    private boolean inFarm(CropMonitorBlockEntity monitor) {
        return controllerLink != null
                && monitor.controllerLink().map(link -> link.controllerId().equals(controllerLink.controllerId())).orElse(false);
    }

    /** The loaded, scanned Crop Monitor with this identity, if any. */
    private Optional<CropMonitorBlockEntity> resolve(ServerLevel level, @Nullable UUID id, @Nullable BlockPos pos) {
        if (id == null || pos == null || !level.isLoaded(pos)) return Optional.empty();
        if (!(level.getBlockEntity(pos) instanceof CropMonitorBlockEntity monitor) || !monitor.componentId().equals(id)) return Optional.empty();
        if (monitor.zone().isEmpty() || monitor.result().isEmpty()) return Optional.empty();
        return Optional.of(monitor);
    }

    /** The chosen monitor's chunk is loaded but it is gone, or no longer allowed. */
    private boolean choiceInvalid(ServerLevel level) {
        if (monitorId == null || monitorPos == null) return true;
        if (!level.isLoaded(monitorPos)) return false;
        if (!(level.getBlockEntity(monitorPos) instanceof CropMonitorBlockEntity monitor) || !monitor.componentId().equals(monitorId)) return true;
        return controllerLink != null ? !inFarm(monitor) : !usableNearby(monitor);
    }

    /**
     * MONITOR button. With a Farm Controller: whole farm, then each of its monitors, then the whole
     * farm again. Without: each nearby monitor in turn.
     */
    public void cycleMonitor(ServerLevel level, @Nullable ServerPlayer player) {
        if (controllerLink != null) {
            List<MonitorEntry> farm = farmChoices(level).orElse(List.of());
            int index = wholeFarm ? -1 : indexOf(farm, monitorId);
            if (farm.isEmpty() || (!wholeFarm && (index < 0 || index + 1 >= farm.size()))) selectWholeFarm(level);
            else selectMonitor(level, farm.get(index + 1));
            return;
        }
        List<MonitorEntry> nearby = nearbyChoices(level);
        if (nearby.isEmpty()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.homelink_farm.farmbot.no_monitor",
                        FarmServerConfig.MAX_LINK_DISTANCE.get()).withStyle(ChatFormatting.YELLOW), true);
            }
            return;
        }
        selectMonitor(level, nearby.get((indexOf(nearby, monitorId) + 1) % nearby.size()));
    }

    private static int indexOf(List<MonitorEntry> entries, @Nullable UUID id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    private void selectMonitor(ServerLevel level, @Nullable MonitorEntry entry) {
        wholeFarm = false;
        monitorId = entry == null ? null : entry.id();
        monitorPos = entry == null ? null : entry.pos();
        refreshMonitorName(level);
        setChangedAndSync();
    }

    private void selectWholeFarm(ServerLevel level) {
        wholeFarm = true;
        monitorId = null;
        monitorPos = null;
        monitorName = "";
        farmMonitors = farmChoices(level).map(List::size).orElse(farmMonitors);
        setChangedAndSync();
    }

    private void refreshMonitorName(ServerLevel level) {
        String name = monitorName;
        if (monitorPos == null) {
            name = "";
        } else if (level.isLoaded(monitorPos) && level.getBlockEntity(monitorPos) instanceof CropMonitorBlockEntity monitor
                && monitor.componentId().equals(monitorId)) {
            name = monitor.displayName().getString();
        }
        if (!name.equals(monitorName)) {
            monitorName = name;
            setChangedAndSync();
        }
    }

    // ----- Ticking and commands -------------------------------------------------------------

    @Override
    public void serverTick(ServerLevel level) {
        long phase = level.getGameTime() + getBlockPos().hashCode();
        if (outputUsed > 0 && Math.floorMod(phase, OUTPUT_INTERVAL) == 0) StationOutputTransfer.push(level, getBlockPos(), output);
        if (Math.floorMod(phase, CHECK_INTERVAL) != 0) return;
        if (robot != null && robotGone(level)) releaseRobot(robot);
        // Zero configuration: the whole farm with a controller, otherwise the nearest usable monitor.
        if (controllerLink != null) {
            if (wholeFarm) {
                int count = farmChoices(level).map(List::size).orElse(farmMonitors);
                if (count != farmMonitors) {
                    farmMonitors = count;
                    setChangedAndSync();
                }
            } else if (choiceInvalid(level)) {
                selectWholeFarm(level);
            }
        } else if (choiceInvalid(level)) {
            List<MonitorEntry> nearby = nearbyChoices(level);
            if (!nearby.isEmpty()) selectMonitor(level, nearby.getFirst());
            else if (monitorId != null) selectMonitor(level, null);
        }
        refreshMonitorName(level);
    }

    @Override
    public void handleCommand(ServerPlayer player, DeviceCommand command, int argument) {
        switch (command) {
            case FARMBOT_START -> setWorking(true);
            case FARMBOT_PAUSE -> setWorking(false);
            case FARMBOT_RETURN -> requestReturn();
            case CYCLE_MONITOR -> cycleMonitor(player.serverLevel(), player);
            default -> { }
        }
    }

    public void setWorking(boolean working) {
        if (this.working == working) return;
        this.working = working;
        setChangedAndSync();
        refreshDevice();
    }

    /** RETURN HOME: the robot drops its task and comes back (ignored while already docked). */
    public void requestReturn() {
        if (robot != null) returnRequested = true;
    }

    /** Block broken: the output drops, the robot loses its station (it stays in the world). */
    public void onBroken(ServerLevel level) {
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack stack = output.getStackInSlot(slot);
            if (!stack.isEmpty()) Containers.dropItemStack(level, getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), stack.copy());
            output.setStackInSlot(slot, ItemStack.EMPTY);
        }
        FarmBotEntity bot = loadedRobot(level);
        if (bot != null) bot.forgetStation();
        robot = null;
    }

    @Override
    public boolean exposedToHomeCore() {
        return true;
    }

    // ----- HomeCore -------------------------------------------------------------------------

    public Optional<FarmBotStationDevice> homeCoreDevice() {
        return Optional.ofNullable(homeCoreDevice);
    }

    private void refreshDevice() {
        if (homeCoreDevice != null) homeCoreDevice.refresh();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            homeCoreDevice = HomeCoreIntegration.register(serverLevel, this, FarmBotStationDevice.class).orElse(null);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        releaseHomeCoreDevice();
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

    // ----- FarmComponent --------------------------------------------------------------------

    @Override public UUID componentId() { return deviceId(); }
    @Override public FarmComponentKind componentKind() { return FarmComponentKind.FARMBOT_STATION; }
    @Override public BlockPos componentPos() { return getBlockPos(); }
    @Override public Optional<ControllerLink> controllerLink() { return Optional.ofNullable(controllerLink); }

    @Override
    public void setControllerLink(ControllerLink link) {
        boolean otherFarm = controllerLink == null || !controllerLink.controllerId().equals(link.controllerId());
        controllerLink = link;
        // A new farm: work on all of its Crop Monitors until the player pins one.
        if (otherFarm && level instanceof ServerLevel serverLevel) selectWholeFarm(serverLevel);
        setChangedAndSync();
    }

    @Override
    public void clearControllerLink() {
        if (controllerLink == null) return;
        controllerLink = null;
        wholeFarm = false;
        farmMonitors = 0;
        monitorId = null;
        monitorPos = null;
        monitorName = "";
        setChangedAndSync();
    }

    @Override
    public boolean isLinked() {
        return controllerLink != null || super.isLinked();
    }

    // ----- Menu -----------------------------------------------------------------------------

    @Override
    protected Component defaultName() {
        return Component.translatableWithFallback("block.homelink_farm.farmbot_station", "FarmBot Station");
    }

    @Override
    protected MenuType<FarmDeviceMenu> menuType() {
        return ModMenus.FARMBOT_STATION.get();
    }

    @Override
    protected AbstractContainerMenu createDeviceMenu(int containerId, Inventory inventory, Player player) {
        return FarmBotStationMenu.server(containerId, inventory, getBlockPos(), getBlockState().getBlock(), output);
    }

    // ----- Persistence ----------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerLink != null) tag.put("Controller", controllerLink.save());
        if (robot != null) tag.putUUID("Robot", robot);
        if (robotPos != null) tag.put("RobotPos", NbtUtils.writeBlockPos(robotPos));
        tag.putBoolean("Working", working);
        if (monitorId != null && monitorPos != null) {
            tag.putUUID("MonitorId", monitorId);
            tag.put("MonitorPos", NbtUtils.writeBlockPos(monitorPos));
        }
        tag.putString("MonitorName", monitorName);
        tag.putBoolean("WholeFarm", wholeFarm);
        tag.putInt("FarmMonitors", farmMonitors);
        if (snapshot != null && robot != null) tag.put("Snapshot", snapshot.save());
        tag.put("Output", output.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerLink = ControllerLink.load(tag.getCompound("Controller")).orElse(null);
        robot = tag.hasUUID("Robot") ? tag.getUUID("Robot") : null;
        robotPos = NbtUtils.readBlockPos(tag, "RobotPos").orElse(null);
        working = !tag.contains("Working") || tag.getBoolean("Working");
        monitorId = tag.hasUUID("MonitorId") ? tag.getUUID("MonitorId") : null;
        monitorPos = monitorId == null ? null : NbtUtils.readBlockPos(tag, "MonitorPos").orElse(null);
        monitorName = tag.getString("MonitorName");
        wholeFarm = tag.getBoolean("WholeFarm");
        farmMonitors = tag.getInt("FarmMonitors");
        snapshot = robot != null && tag.contains("Snapshot") ? FarmBotSnapshot.load(tag.getCompound("Snapshot")) : null;
        if (tag.contains("Output")) {
            CompoundTag items = tag.getCompound("Output");
            items.putInt("Size", OUTPUT_SIZE);
            output.deserializeNBT(registries, items);
            outputUsed = countUsed(output);
        } else if (tag.contains("OutputUsed")) {
            outputUsed = tag.getInt("OutputUsed");
        }
    }

    /** Clients get the output count, not its items (the open menu synchronizes those). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.remove("Output");
        tag.remove("RobotPos");
        tag.putInt("OutputUsed", outputUsed);
        return tag;
    }
}
