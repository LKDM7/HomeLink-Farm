package fr.lkdm.homelink.farm.farm.bot;

import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.entity.FarmBotEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side state machine of a FarmBot. Every decision and transition lives here; the entity
 * only provides a body (position, navigation, battery, inventory). Expensive work is rate
 * limited: targets come from the Crop Monitor cache with a search cooldown, paths are rebuilt
 * at most once per {@link #REPATH_INTERVAL}, and a target that keeps failing is ignored for
 * {@link #UNREACHABLE_TICKS} so the robot can never loop forever.
 */
public final class FarmBotBrain {
    /** Ticks spent on one crop (the harvesting animation). */
    public static final int HARVEST_TICKS = 12;
    /** A docked robot leaves once its battery reaches this percentage (and there is work). */
    public static final int DEPART_BATTERY = 80;
    static final double SPEED = 1.0;
    static final int UNLOAD_INTERVAL = 4;
    static final int REPORT_INTERVAL = 10;
    static final int REPATH_INTERVAL = 20;
    static final int PROGRESS_WINDOW = 60;
    static final double MIN_PROGRESS = 0.5;
    static final int UNREACHABLE_TICKS = 1200;
    static final int HOME_RETRY_TICKS = 100;
    static final int CLAIM_TICKS = 600;
    static final int TARGET_CHECK_INTERVAL = 20;
    /** Horizontal / vertical distance at which a crop is within the harvesting tool's reach. */
    static final double REACH = 1.2;
    static final double REACH_VERTICAL = 1.25;
    /** Final docking: closer than this, the robot is aligned on the dock (a correction of a few pixels). */
    static final double DOCK_SNAP = 0.45;
    static final double DOCK_APPROACH = 1.6;
    /** Degrees per tick of the on-the-spot turn before docking (a half turn takes half a second). */
    static final float DOCK_TURN_SPEED = 18.0F;

    private final FarmBotEntity bot;
    private FarmBotState state = FarmBotState.DOCKED;
    private FarmBotFault fault = FarmBotFault.NONE;
    @Nullable
    private BlockPos target;
    private String targetBlock = "";
    private int attempts;
    private long nextSearch;
    private long nextRepath;
    private long nextHomeTry;
    private long nextTargetCheck;
    @Nullable
    private Vec3 progressFrom;
    private long progressAt;
    private int actionTicks;
    private boolean unloadBlocked;
    /** The last search found no usable Crop Monitor (shown while waiting at the station). */
    private boolean monitorMissing;
    @Nullable
    private Vec3 lastPosition;
    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    @Nullable
    private FarmBotSnapshot lastReport;
    private long nextReport;
    private long nextLocate;

    public FarmBotBrain(FarmBotEntity bot) {
        this.bot = bot;
    }

    public FarmBotState state() {
        return state;
    }

    public FarmBotFault fault() {
        return fault;
    }

    public Optional<BlockPos> target() {
        return Optional.ofNullable(target);
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        drainMovement();
        Optional<FarmBotHome> home = bot.home(level);
        if (home.isEmpty()) {
            loseHome(level);
            return;
        }
        FarmBotHome station = home.get();
        if (station.consumeReturnRequest() && !bot.docked()) goHome(level, FarmBotState.RETURNING);
        if (!bot.docked() && bot.energy() <= 0) {
            outOfPower(level, station);
        } else if (bot.docked()) {
            tickDocked(level, station, now);
        } else {
            tickAway(level, station, now);
        }
        report(station, now);
    }

    // ----- At the station -------------------------------------------------------------------

    private void tickDocked(ServerLevel level, FarmBotHome station, long now) {
        stopMoving();
        if (!atDock(station)) {
            // Pushed off the dock: drive back.
            bot.setDocked(false);
            goHome(level, FarmBotState.RETURNING);
            return;
        }
        bot.charge();
        if (!bot.inventoryEmpty()) {
            if (now % UNLOAD_INTERVAL == 0) unloadBlocked = !unloadOne(station);
            set(unloadBlocked ? FarmBotState.OUTPUT_BLOCKED : FarmBotState.UNLOADING, FarmBotFault.NONE);
            return;
        }
        unloadBlocked = false;
        if (!station.working()) {
            set(FarmBotState.PAUSED, FarmBotFault.NONE);
            return;
        }
        if (bot.batteryPercent() >= DEPART_BATTERY && now >= nextSearch) {
            nextSearch = now + FarmServerConfig.FARMBOT_SEARCH_COOLDOWN.get();
            List<CropMonitorBlockEntity> sources = station.cropSources(level);
            monitorMissing = sources.isEmpty();
            BlockPos next = pickTarget(level, sources, now);
            if (next != null) {
                bot.setDocked(false);
                startTarget(level, next, now);
                return;
            }
        }
        set(bot.batteryPercent() < 100 ? FarmBotState.CHARGING : FarmBotState.IDLE,
                monitorMissing ? FarmBotFault.NO_MONITOR : FarmBotFault.NONE);
    }

    /** Moves (part of) one inventory slot into the station. False when nothing could be moved. */
    private boolean unloadOne(FarmBotHome station) {
        var inventory = bot.inventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            ItemStack remainder = station.acceptHarvest(stack.copy());
            if (remainder.getCount() < stack.getCount()) {
                inventory.setStackInSlot(slot, remainder);
                return true;
            }
        }
        return false;
    }

    // ----- Away from the station ------------------------------------------------------------

    private void tickAway(ServerLevel level, FarmBotHome station, long now) {
        if (bot.homeBound()) {
            driveHome(level, station, now);
            return;
        }
        if (state == FarmBotState.HARVESTING) {
            // A started harvest always finishes, even if a pause or a low battery arrives meanwhile.
            if (--actionTicks <= 0) finishHarvest(level, now);
            return;
        }
        if (!station.working()) {
            dropTarget(level);
            stopMoving();
            bot.drainIdle();
            set(FarmBotState.PAUSED, FarmBotFault.NONE);
            return;
        }
        if (bot.batteryPercent() <= FarmServerConfig.FARMBOT_LOW_BATTERY_THRESHOLD.get()) {
            goHome(level, FarmBotState.LOW_BATTERY);
            driveHome(level, station, now);
            return;
        }
        if (bot.inventoryFull()) {
            goHome(level, FarmBotState.STORAGE_FULL);
            driveHome(level, station, now);
            return;
        }
        if (target != null) {
            moveToTarget(level, now);
            return;
        }
        bot.drainIdle();
        if (now < nextSearch) {
            set(FarmBotState.SEARCHING, fault == FarmBotFault.TARGET_UNREACHABLE ? fault : FarmBotFault.NONE);
            return;
        }
        nextSearch = now + FarmServerConfig.FARMBOT_SEARCH_COOLDOWN.get();
        List<CropMonitorBlockEntity> sources = station.cropSources(level);
        BlockPos next = pickTarget(level, sources, now);
        if (next != null) {
            startTarget(level, next, now);
        } else {
            // Nothing (left) to harvest, or no monitor to work from: go home.
            monitorMissing = sources.isEmpty();
            goHome(level, FarmBotState.RETURNING);
            if (monitorMissing) set(FarmBotState.RETURNING, FarmBotFault.NO_MONITOR);
        }
    }

    private void moveToTarget(ServerLevel level, long now) {
        if (now >= nextTargetCheck) {
            nextTargetCheck = now + TARGET_CHECK_INTERVAL;
            if (!CropHarvester.harvestable(level, target)) {
                // Harvested by someone else, trampled or unloaded: pick another one right away.
                dropTarget(level);
                nextSearch = now;
                set(FarmBotState.SEARCHING, FarmBotFault.NONE);
                return;
            }
        }
        if (withinReach(target)) {
            stopMoving();
            bot.lookAtCrop(target);
            actionTicks = HARVEST_TICKS;
            set(FarmBotState.HARVESTING, FarmBotFault.NONE);
            return;
        }
        set(FarmBotState.MOVING, fault == FarmBotFault.TARGET_UNREACHABLE ? fault : FarmBotFault.NONE);
        if (!navigate(target, now)) failTarget(level, now);
    }

    private void finishHarvest(ServerLevel level, long now) {
        BlockPos pos = target;
        dropTarget(level);
        if (pos != null) {
            CropHarvester.harvest(level, pos, bot, bot.inventory()).ifPresent(items -> store(level, pos, items));
        }
        fault = FarmBotFault.NONE;
        nextSearch = now;
        set(FarmBotState.SEARCHING, FarmBotFault.NONE);
    }

    /** Stores the harvest; anything that does not fit is dropped on the crop, never deleted. */
    private void store(ServerLevel level, BlockPos pos, List<ItemStack> items) {
        bot.consume(FarmServerConfig.FARMBOT_HARVEST_CONSUMPTION.get());
        bot.addHarvested(1);
        for (ItemStack stack : items) {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(bot.inventory(), stack, false);
            if (!remainder.isEmpty()) Block.popResource(level, pos, remainder);
        }
    }

    private void failTarget(ServerLevel level, long now) {
        if (++attempts < FarmServerConfig.FARMBOT_TARGET_RETRY_LIMIT.get()) {
            nextRepath = now + REPATH_INTERVAL;
            return;
        }
        unreachable.put(target, now + UNREACHABLE_TICKS);
        dropTarget(level);
        nextSearch = now;
        set(FarmBotState.SEARCHING, FarmBotFault.TARGET_UNREACHABLE);
    }

    // ----- Going home -----------------------------------------------------------------------

    private void goHome(ServerLevel level, FarmBotState reason) {
        dropTarget(level);
        bot.setHomeBound(true);
        attempts = 0;
        nextRepath = 0;
        set(reason, FarmBotFault.NONE);
    }

    private void driveHome(ServerLevel level, FarmBotHome station, long now) {
        BlockPos dock = station.dockPos();
        if (!FarmBotDock.free(level, dock)) {
            stopMoving();
            bot.drainIdle();
            set(FarmBotState.STUCK, FarmBotFault.DOCK_BLOCKED);
            return;
        }
        if (state == FarmBotState.STUCK && now < nextHomeTry) {
            bot.drainIdle();
            return;
        }
        if (state == FarmBotState.STUCK) set(FarmBotState.RETURNING, FarmBotFault.NONE);
        Vec3 center = Vec3.atBottomCenterOf(dock);
        double horizontal = horizontalDistance(center);
        double vertical = Math.abs(bot.getY() - center.y);
        if (horizontal < DOCK_SNAP && vertical < 0.6) {
            // On the pad: turn on the spot to back onto the station, then dock.
            stopMoving();
            if (bot.turnTowards(station.dockFacing().toYRot(), DOCK_TURN_SPEED)) dock(station, center);
            return;
        }
        if (horizontal < DOCK_APPROACH && vertical < 0.6) {
            // Last half-block: drive straight onto the pad instead of path-finding around it.
            bot.getNavigation().stop();
            bot.getMoveControl().setWantedPosition(center.x, bot.getY(), center.z, SPEED * 0.6);
            if (checkProgress(now)) return;
        } else if (navigate(dock, now)) {
            return;
        }
        if (++attempts < FarmServerConfig.FARMBOT_TARGET_RETRY_LIMIT.get()) {
            nextRepath = now + REPATH_INTERVAL;
            return;
        }
        attempts = 0;
        stopMoving();
        nextHomeTry = now + HOME_RETRY_TICKS;
        set(FarmBotState.STUCK, FarmBotFault.HOME_UNREACHABLE);
    }

    private void dock(FarmBotHome station, Vec3 center) {
        stopMoving();
        bot.alignOnDock(center, station.dockFacing());
        bot.setDocked(true);
        bot.setHomeBound(false);
        attempts = 0;
        unloadBlocked = false;
        set(FarmBotState.DOCKED, FarmBotFault.NONE);
    }

    private boolean atDock(FarmBotHome station) {
        Vec3 center = Vec3.atBottomCenterOf(station.dockPos());
        return horizontalDistance(center) < 0.6 && Math.abs(bot.getY() - center.y) < 0.6;
    }

    private void outOfPower(ServerLevel level, FarmBotHome station) {
        dropTarget(level);
        stopMoving();
        // A player may push an empty robot back onto its dock: it then charges normally.
        Vec3 center = Vec3.atBottomCenterOf(station.dockPos());
        if (horizontalDistance(center) < DOCK_SNAP && Math.abs(bot.getY() - center.y) < 0.6) {
            dock(station, center);
            return;
        }
        set(FarmBotState.OUT_OF_POWER, FarmBotFault.NONE);
    }

    private void loseHome(ServerLevel level) {
        dropTarget(level);
        stopMoving();
        bot.setDocked(false);
        BlockPos station = bot.stationPos();
        if (station != null && !level.isLoaded(station)) {
            bot.drainIdle();
            set(FarmBotState.STUCK, FarmBotFault.STATION_UNLOADED);
        } else {
            set(FarmBotState.ERROR, FarmBotFault.NO_STATION);
        }
        bot.syncDisplay(state, fault);
    }

    // ----- Targets and movement -------------------------------------------------------------

    @Nullable
    private BlockPos pickTarget(ServerLevel level, List<CropMonitorBlockEntity> sources, long now) {
        unreachable.values().removeIf(until -> until <= now);
        return FarmBotTargets.nearest(level, sources, bot.position(),
                pos -> unreachable.containsKey(pos) || FarmBotClaims.claimedByOther(level, pos, bot.getUUID()));
    }

    private void startTarget(ServerLevel level, BlockPos pos, long now) {
        target = pos.immutable();
        targetBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        attempts = 0;
        nextRepath = 0;
        nextTargetCheck = now + TARGET_CHECK_INTERVAL;
        progressFrom = null;
        FarmBotClaims.claim(level, pos, bot.getUUID(), CLAIM_TICKS);
        set(FarmBotState.MOVING, fault == FarmBotFault.TARGET_UNREACHABLE ? fault : FarmBotFault.NONE);
    }

    private void dropTarget(ServerLevel level) {
        if (target != null) FarmBotClaims.release(level, target, bot.getUUID());
        target = null;
        targetBlock = "";
    }

    /**
     * Keeps the robot moving towards {@code goal}, rebuilding its path at most once per
     * {@link #REPATH_INTERVAL}. False when this attempt failed (no path, or no progress).
     */
    private boolean navigate(BlockPos goal, long now) {
        var navigation = bot.getNavigation();
        if (now >= nextRepath && navigation.isDone()) {
            nextRepath = now + REPATH_INTERVAL;
            Path path = navigation.createPath(goal, 0);
            if (path == null || !(path.canReach() || endsWithinReach(path, goal))) return false;
            navigation.moveTo(path, SPEED);
            progressFrom = bot.position();
            progressAt = now;
            return true;
        }
        return checkProgress(now);
    }

    /** False when the robot barely moved during the last {@link #PROGRESS_WINDOW} ticks. */
    private boolean checkProgress(long now) {
        if (progressFrom == null) {
            progressFrom = bot.position();
            progressAt = now;
            return true;
        }
        if (now - progressAt < PROGRESS_WINDOW) return true;
        boolean moved = bot.position().distanceTo(progressFrom) >= MIN_PROGRESS;
        progressFrom = bot.position();
        progressAt = now;
        if (!moved) bot.getNavigation().stop();
        return moved;
    }

    private static boolean endsWithinReach(Path path, BlockPos goal) {
        Node end = path.getEndNode();
        if (end == null) return false;
        double dx = end.x + 0.5 - (goal.getX() + 0.5), dz = end.z + 0.5 - (goal.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= REACH && Math.abs(end.y - goal.getY()) <= REACH_VERTICAL;
    }

    private boolean withinReach(BlockPos pos) {
        return horizontalDistance(Vec3.atBottomCenterOf(pos)) <= REACH && Math.abs(bot.getY() - pos.getY()) <= REACH_VERTICAL;
    }

    private double horizontalDistance(Vec3 point) {
        double dx = bot.getX() - point.x, dz = bot.getZ() - point.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void stopMoving() {
        bot.getNavigation().stop();
        progressFrom = null;
    }

    private void drainMovement() {
        Vec3 position = bot.position();
        if (lastPosition != null && !bot.docked()) {
            double dx = position.x - lastPosition.x, dz = position.z - lastPosition.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            // Teleports and pushes over a few blocks are not driving.
            if (distance < 1.0) bot.consume(distance * FarmServerConfig.FARMBOT_MOVEMENT_CONSUMPTION.get());
        }
        lastPosition = position;
    }

    // ----- State and reporting --------------------------------------------------------------

    private void set(FarmBotState newState, FarmBotFault newFault) {
        state = newState;
        fault = newFault;
        bot.syncDisplay(state, fault);
    }

    private void report(FarmBotHome station, long now) {
        if (now >= nextLocate) {
            nextLocate = now + REPORT_INTERVAL;
            station.locate(bot.blockPosition());
        }
        FarmBotSnapshot snapshot = bot.snapshot(state, fault, target, targetBlock);
        if (snapshot.equals(lastReport)) return;
        // State changes are reported at once; battery and counters at most every REPORT_INTERVAL.
        boolean urgent = lastReport == null || lastReport.state() != snapshot.state() || lastReport.fault() != snapshot.fault();
        if (!urgent && now < nextReport) return;
        lastReport = snapshot;
        nextReport = now + REPORT_INTERVAL;
        station.report(snapshot);
    }
}
