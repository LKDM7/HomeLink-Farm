package fr.lkdm.homelink.farm.entity;

import fr.lkdm.homelink.farm.blockentity.FarmBotStationBlockEntity;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.farm.FarmAccess;
import fr.lkdm.homelink.farm.farm.bot.FarmBotBrain;
import fr.lkdm.homelink.farm.farm.bot.FarmBotFault;
import fr.lkdm.homelink.farm.farm.bot.FarmBotHome;
import fr.lkdm.homelink.farm.farm.bot.FarmBotSnapshot;
import fr.lkdm.homelink.farm.farm.bot.FarmBotState;
import fr.lkdm.homelink.farm.item.FarmBotItem;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * FarmBot: a small autonomous harvesting robot. A real path-finding mob that drives to the
 * mature crops reported by its station's Crop Monitor, harvests and replants them through
 * {@link fr.lkdm.homelink.farm.farm.crop.CropAdapter}, and returns to its station to unload
 * and recharge. All decisions are taken on the server by its {@link FarmBotBrain}; clients
 * only receive its displayed state, fault and battery level.
 */
public class FarmBotEntity extends PathfinderMob {
    public static final int INVENTORY_SIZE = 9;
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(FarmBotEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FAULT = SynchedEntityData.defineId(FarmBotEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_BATTERY = SynchedEntityData.defineId(FarmBotEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_DOCKED = SynchedEntityData.defineId(FarmBotEntity.class, EntityDataSerializers.BOOLEAN);

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE);
    private final FarmBotBrain brain = new FarmBotBrain(this);
    @Nullable
    private BlockPos stationPos;
    @Nullable
    private UUID stationId;
    @Nullable
    private UUID owner;
    /** Stored energy, 0 .. {@link FarmServerConfig#FARMBOT_BATTERY_CAPACITY}. */
    private double energy;
    private int harvested;
    private boolean homeBound;

    public FarmBotEntity(EntityType<? extends FarmBotEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        energy = capacity();
        // Drives around water, drops, fences and other hazards; never through them.
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
        setPathfindingMalus(PathType.LEAVES, -1.0F);
        setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
        setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) FarmBotState.DOCKED.ordinal());
        builder.define(DATA_FAULT, (byte) FarmBotFault.NONE.ordinal());
        builder.define(DATA_BATTERY, (byte) 100);
        builder.define(DATA_DOCKED, true);
    }

    // ----- Server behavior ------------------------------------------------------------------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (level() instanceof ServerLevel serverLevel) brain.tick(serverLevel);
    }

    /** The station this robot belongs to, when loaded and still claiming it. */
    public Optional<FarmBotHome> home(ServerLevel level) {
        if (stationPos == null || stationId == null || !level.isLoaded(stationPos)) return Optional.empty();
        if (level.getBlockEntity(stationPos) instanceof FarmBotHome home && home.homeId().equals(stationId) && home.owns(getUUID())) {
            return Optional.of(home);
        }
        return Optional.empty();
    }

    /** Binds this robot to a station (installation). */
    public void assignStation(BlockPos pos, UUID id, @Nullable UUID owner) {
        stationPos = pos.immutable();
        stationId = id;
        this.owner = owner;
    }

    /** Player who installed this robot, used to judge protections on their behalf. */
    @Nullable
    public UUID ownerId() {
        return owner;
    }

    @Nullable
    public BlockPos stationPos() {
        return stationPos;
    }

    public FarmBotBrain brain() {
        return brain;
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public int usedSlots() {
        int used = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) used++;
        }
        return used;
    }

    public boolean inventoryEmpty() {
        return usedSlots() == 0;
    }

    /** Full as soon as no slot is free: the next harvest could bring a new kind of item. */
    public boolean inventoryFull() {
        return usedSlots() == INVENTORY_SIZE;
    }

    public boolean docked() {
        return entityData.get(DATA_DOCKED);
    }

    public void setDocked(boolean docked) {
        entityData.set(DATA_DOCKED, docked);
    }

    public boolean homeBound() {
        return homeBound;
    }

    public void setHomeBound(boolean homeBound) {
        this.homeBound = homeBound;
    }

    public int harvested() {
        return harvested;
    }

    public void addHarvested(int count) {
        harvested += count;
    }

    // ----- Battery --------------------------------------------------------------------------

    public static double capacity() {
        return FarmServerConfig.FARMBOT_BATTERY_CAPACITY.get();
    }

    public double energy() {
        return energy;
    }

    public void setEnergy(double value) {
        energy = Math.max(0, Math.min(capacity(), value));
        entityData.set(DATA_BATTERY, (byte) batteryPercent());
    }

    /** Whole percent, rounded down (0 only when truly empty). */
    public int batteryPercent() {
        double fraction = energy / capacity();
        return energy > 0 ? Math.max(1, (int) Math.floor(fraction * 100)) : 0;
    }

    /** Battery shown on clients (synchronized). */
    public int displayedBattery() {
        return entityData.get(DATA_BATTERY);
    }

    public void consume(double amount) {
        if (amount > 0) setEnergy(energy - amount);
    }

    /** One tick of waiting away from the station. */
    public void drainIdle() {
        consume(FarmServerConfig.FARMBOT_IDLE_CONSUMPTION.get() / 1200.0);
    }

    /** One tick on the dock: a full charge takes {@code farmbotRechargeTime} seconds. */
    public void charge() {
        if (energy < capacity()) setEnergy(energy + capacity() / (FarmServerConfig.FARMBOT_RECHARGE_TIME.get() * 20.0));
    }

    // ----- Display --------------------------------------------------------------------------

    public void syncDisplay(FarmBotState state, FarmBotFault fault) {
        entityData.set(DATA_STATE, (byte) state.ordinal());
        entityData.set(DATA_FAULT, (byte) fault.ordinal());
    }

    public FarmBotState displayedState() {
        return FarmBotState.byId(entityData.get(DATA_STATE));
    }

    public FarmBotFault displayedFault() {
        return FarmBotFault.byId(entityData.get(DATA_FAULT));
    }

    public FarmBotSnapshot snapshot(FarmBotState state, FarmBotFault fault, @Nullable BlockPos target, String targetBlock) {
        return new FarmBotSnapshot(getName().getString(), state, fault, batteryPercent(), usedSlots(), harvested, target, targetBlock);
    }

    public void lookAtCrop(BlockPos pos) {
        getLookControl().setLookAt(Vec3.atCenterOf(pos));
    }

    /**
     * Turns the robot on the spot by at most {@code step} degrees towards {@code yaw}.
     * @return true once it faces {@code yaw}
     */
    public boolean turnTowards(float yaw, float step) {
        float delta = net.minecraft.util.Mth.wrapDegrees(yaw - getYRot());
        float next = Math.abs(delta) <= step ? yaw : getYRot() + Math.signum(delta) * step;
        setYRot(next);
        setYBodyRot(next);
        setYHeadRot(next);
        return Math.abs(delta) <= step;
    }

    /** Final alignment on the dock pad, its back to the station. */
    public void alignOnDock(Vec3 center, Direction facing) {
        float yaw = facing.toYRot();
        moveTo(center.x, getY(), center.z, yaw, 0.0F);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        setDeltaMovement(Vec3.ZERO);
    }

    // ----- Players: status, pick up, break ---------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (!player.isSecondaryUseActive()) {
            serverPlayer.displayClientMessage(Component.translatable("message.homelink_farm.farmbot.status",
                    getName(), displayedState().label(), batteryPercent()).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }
        if (!canManage(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable("message.homelink_farm.link.no_permission").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        pickUp(serverPlayer);
        return InteractionResult.SUCCESS;
    }

    /** Sneak + use: the robot and its cargo go into the player's inventory (or at their feet). */
    public void pickUp(ServerPlayer player) {
        if (isRemoved()) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
        }
        player.getInventory().placeItemBackInInventory(toItem());
        player.displayClientMessage(Component.translatable("message.homelink_farm.farmbot.picked_up", getName())
                .withStyle(ChatFormatting.GREEN), true);
        leaveStation();
    }

    /**
     * Hit by a player allowed to manage it, killed by a command or fallen out of the world: it
     * breaks like a minecart, dropping itself and its cargo. Every other damage is ignored.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        boolean player = source.getDirectEntity() instanceof ServerPlayer attacker && source.getEntity() == attacker && canManage(attacker);
        if (!player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        breakApart();
        return true;
    }

    private void breakApart() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) spawnAtLocation(stack);
        }
        spawnAtLocation(toItem());
        leaveStation();
    }

    /** Frees its station and disappears; its items were handed out just before. */
    private void leaveStation() {
        if (level() instanceof ServerLevel serverLevel) home(serverLevel).ifPresent(home -> home.releaseRobot(getUUID()));
        discard();
    }

    /** Station lost (broken): the robot stays where it is, in ERROR, until a player picks it up. */
    public void forgetStation() {
        stationPos = null;
        stationId = null;
        homeBound = false;
        setDocked(false);
    }

    public ItemStack toItem() {
        return FarmBotItem.create(batteryPercent(), harvested, hasCustomName() ? getCustomName() : null);
    }

    public boolean canManage(ServerPlayer player) {
        if (player.hasPermissions(FarmAccess.OPERATOR_LEVEL)) return true;
        if (owner == null || owner.equals(player.getUUID())) return true;
        return stationPos != null && player.serverLevel().isLoaded(stationPos)
                && player.serverLevel().getBlockEntity(stationPos) instanceof FarmBotStationBlockEntity station
                && FarmAccess.canManage(player, station);
    }

    // ----- Mob behavior that does not fit a robot --------------------------------------------

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !docked() && super.isPushable();
    }

    @Override
    protected void pushEntities() {
        // Drives through the farm without shoving animals or players around.
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    // ----- Persistence ----------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Cargo", inventory.serializeNBT(registryAccess()));
        if (stationPos != null && stationId != null) {
            tag.put("StationPos", NbtUtils.writeBlockPos(stationPos));
            tag.putUUID("StationId", stationId);
        }
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putDouble("Energy", energy);
        tag.putInt("Harvested", harvested);
        tag.putBoolean("Docked", docked());
        tag.putBoolean("HomeBound", homeBound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag cargo = tag.getCompound("Cargo");
        // Never trust a saved size: keep exactly nine slots.
        cargo.putInt("Size", INVENTORY_SIZE);
        inventory.deserializeNBT(registryAccess(), cargo);
        stationPos = NbtUtils.readBlockPos(tag, "StationPos").orElse(null);
        stationId = tag.hasUUID("StationId") ? tag.getUUID("StationId") : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        setEnergy(tag.contains("Energy") ? tag.getDouble("Energy") : capacity());
        harvested = tag.getInt("Harvested");
        setDocked(tag.getBoolean("Docked"));
        homeBound = tag.getBoolean("HomeBound");
    }
}
