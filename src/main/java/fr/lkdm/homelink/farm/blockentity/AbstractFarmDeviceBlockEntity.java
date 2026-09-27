package fr.lkdm.homelink.farm.blockentity;

import fr.lkdm.homecore.api.energy.EnergyBuffer;
import fr.lkdm.homelink.farm.block.AbstractFarmDeviceBlock;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.farm.DeviceNames;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Shared state of HomeLink Farm devices: stable UUID, owner and custom name.
 * All of it is persisted and synchronized to clients for display only.
 */
public abstract class AbstractFarmDeviceBlockEntity extends BlockEntity implements MenuProvider {
    private UUID deviceId = UUID.randomUUID();
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private String customName = "";
    @Nullable
    private UUID homeNetwork;
    private String homeNetworkName = "";
    /** HE received from HomeLink Energy networks; filled through the HomeCore energy capability. */
    private final EnergyBuffer energy = new EnergyBuffer(this::energyCapacity, this::onEnergyChanged);
    /** Server truth, mirrored on clients: whether the last running tick found enough HE. */
    private boolean energized;
    /** Charge shown on clients, 0..100; resynchronized only when it changes. */
    private int energyPercent;

    protected AbstractFarmDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public UUID deviceId() {
        return deviceId;
    }

    /** Assigns a fresh identity (used when a copied block entity collides with a live one). */
    protected void regenerateDeviceId() {
        deviceId = UUID.randomUUID();
        setChanged();
    }

    /** A copied block entity collided with a live device: become a distinct device, not in any network. */
    public void resetIdentityAfterCollision() {
        regenerateDeviceId();
        homeNetwork = null;
        homeNetworkName = "";
        setChangedAndSync();
    }

    /**
     * HE this device uses per minute (1200 ticks) while it runs, from the server config.
     * Zero means the device needs no energy.
     */
    protected long energyPerMinute() {
        return 0;
    }

    private long energyCapacity() {
        return energyPerMinute() * ENERGY_BUFFER_MINUTES;
    }

    /** Minutes of running cost the internal buffer holds, so short gaps in supply go unnoticed. */
    public static final int ENERGY_BUFFER_MINUTES = 2;

    /** Current value of a server config entry, or zero before the config is loaded. */
    protected static long configured(net.neoforged.neoforge.common.ModConfigSpec.IntValue value) {
        return FarmServerConfig.SPEC.isLoaded() ? value.get() : 0;
    }

    /** Energy port exposed on every face; null when this device needs no energy. */
    @Nullable
    public EnergyBuffer energyPort() {
        return energyPerMinute() > 0 ? energy : null;
    }

    /** Whether the device had enough HE on its last running tick (client: mirrored). */
    public boolean energized() {
        return energized || energyPerMinute() <= 0;
    }

    /** Charge of the internal buffer, 0..100 (client: mirrored). */
    public int energyPercent() {
        return energyPercent;
    }

    /**
     * Server: pays this tick's share of the running cost. Call it on every tick the device
     * wants to work, and do nothing that tick when it returns false.
     *
     * @return whether the device is powered and may work this tick
     */
    protected boolean drawEnergy(ServerLevel level) {
        boolean powered = energy.draw(energyPerMinute(), 1200, level.getGameTime());
        if (powered != energized) {
            energized = powered;
            setChangedAndSync();
        }
        return powered;
    }

    private void onEnergyChanged() {
        setChanged();
        int percent = energy.percent();
        if (percent != energyPercent) {
            energyPercent = percent;
            syncToClients();
        }
    }

    /** Whether this device is published to HomeCore (and can join a HomeNetwork). */
    public boolean exposedToHomeCore() {
        return false;
    }

    public Optional<UUID> homeNetwork() {
        return Optional.ofNullable(homeNetwork);
    }

    /** Name of the network when it was attached (display only; HomeCore holds the truth). */
    public String homeNetworkName() {
        return homeNetworkName;
    }

    public void setHomeNetwork(UUID network, String name) {
        homeNetwork = network;
        homeNetworkName = name;
        setChangedAndSync();
    }

    public void clearHomeNetwork() {
        homeNetwork = null;
        homeNetworkName = "";
        setChangedAndSync();
    }

    /** Whether the device is connected to something; drives its blinking lights. */
    public boolean isLinked() {
        return homeNetwork != null;
    }

    /** Server: aligns the block's LINKED state (blinking lights) with {@link #isLinked()}. */
    public void refreshLinkedState() {
        if (level == null || level.isClientSide || isRemoved()) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(AbstractFarmDeviceBlock.LINKED)) return;
        boolean linked = isLinked();
        if (state.getValue(AbstractFarmDeviceBlock.LINKED) != linked) {
            level.setBlock(worldPosition, state.setValue(AbstractFarmDeviceBlock.LINKED, linked), Block.UPDATE_CLIENTS);
        }
    }

    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
        setChangedAndSync();
    }

    public String customName() {
        return customName;
    }

    public void setCustomName(String name) {
        String sanitized = DeviceNames.sanitize(name);
        if (sanitized.equals(customName)) return;
        customName = sanitized;
        setChangedAndSync();
    }

    public Component displayName() {
        return customName.isEmpty() ? defaultName() : Component.literal(customName);
    }

    protected abstract Component defaultName();

    protected abstract MenuType<FarmDeviceMenu> menuType();

    @Override
    public Component getDisplayName() {
        return displayName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (exposedToHomeCore()) fr.lkdm.homelink.farm.network.HomeNetworkPayloads.sendChoices(serverPlayer, this);
            onMenuOpened(serverPlayer);
        }
        return createDeviceMenu(containerId, inventory, player);
    }

    /** The menu backing this device screen (slot-less by default). */
    protected AbstractContainerMenu createDeviceMenu(int containerId, Inventory inventory, Player player) {
        return FarmDeviceMenu.server(menuType(), containerId, player, getBlockPos(), getBlockState().getBlock());
    }

    /** Server hook run whenever a player opens this device's screen, whatever opened it. */
    protected void onMenuOpened(ServerPlayer player) {
    }

    /** Marks the block entity dirty and pushes the display state to tracking clients. */
    protected void setChangedAndSync() {
        setChanged();
        syncToClients();
    }

    /** Pushes display state to tracking clients without marking persistent data dirty. */
    protected void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("DeviceId", deviceId);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putString("CustomName", customName);
        energy.save(tag, "Energy");
        tag.putBoolean("Energized", energized);
        tag.putInt("EnergyPercent", energy.percent());
        if (homeNetwork != null) {
            tag.putUUID("HomeNetwork", homeNetwork);
            tag.putString("HomeNetworkName", homeNetworkName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("DeviceId")) deviceId = tag.getUUID("DeviceId");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        customName = DeviceNames.sanitize(tag.getString("CustomName"));
        energy.load(tag, "Energy");
        energized = tag.getBoolean("Energized");
        energyPercent = tag.getInt("EnergyPercent");
        homeNetwork = tag.hasUUID("HomeNetwork") ? tag.getUUID("HomeNetwork") : null;
        homeNetworkName = homeNetwork == null ? "" : tag.getString("HomeNetworkName");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
