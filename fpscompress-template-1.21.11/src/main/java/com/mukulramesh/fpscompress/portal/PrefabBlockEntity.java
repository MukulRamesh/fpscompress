package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.gui.PreFabConfigMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

/**
 * BlockEntity for PreFab blocks - upgraded Compact Machines that store factory state.
 *
 * Stores:
 * - Room linkage (roomCode, roomCenter coordinates)
 * - Machine state (BUILDING/SIMULATING/CACHED/HALTED)
 * - Face configurations (6 independent face settings)
 * - Cached production rates (for CACHED mode fractional math)
 */
public class PrefabBlockEntity extends BlockEntity implements MenuProvider {

    // Room linkage
    @Nullable
    private String roomCode;

    @Nullable
    private BlockPos roomCenter;

    // Machine state
    private MachineState currentState = MachineState.BUILDING;

    // Face configurations (Phase 1)
    private final Map<Direction, FaceConfig> faceConfigs = new EnumMap<>(Direction.class);

    // Cached production rates: resource ID → rate per tick (positive = output, negative = input)
    private final Map<String, Double> cachedRates = new HashMap<>();

    // UUID lookup caching (O(1) fast path for repeated lookups)
    private final Map<UUID, BlockPos> importerCache = new HashMap<>();
    private final Map<UUID, BlockPos> exporterCache = new HashMap<>();

    public PrefabBlockEntity(BlockPos pos, BlockState state) {
        super(FPSCompress.PREFAB_BE.get(), pos, state);

        // Initialize all 6 faces to DISABLED
        for (Direction dir : Direction.values()) {
            faceConfigs.put(dir, new FaceConfig());
        }
    }

    // ===== Room Linkage Accessors =====

    @Nullable
    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
        setChanged();
    }

    @Nullable
    public BlockPos getRoomCenter() {
        return roomCenter;
    }

    public void setRoomCenter(BlockPos roomCenter) {
        this.roomCenter = roomCenter;
        setChanged();
    }

    // ===== Machine State =====

    public MachineState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(MachineState state) {
        this.currentState = state;
        setChanged();
    }

    // ===== Face Configuration =====

    /**
     * Get the configuration for a specific face.
     *
     * @param direction The face direction
     * @return Face configuration (never null)
     */
    public FaceConfig getFaceConfig(Direction direction) {
        return faceConfigs.get(direction);
    }

    /**
     * Set the configuration for a specific face.
     *
     * @param direction The face direction
     * @param config New configuration
     */
    public void setFaceConfig(Direction direction, FaceConfig config) {
        faceConfigs.put(direction, config);
        setChanged();
    }

    /**
     * Get all face configurations.
     *
     * @return Map of all face configs
     */
    public Map<Direction, FaceConfig> getAllFaceConfigs() {
        return new EnumMap<>(faceConfigs);
    }

    // ===== Debug Methods (Phase 1 - Adjacent Block Detection) =====

    /**
     * Debug method to display adjacent blocks and their capabilities.
     * Used to validate Phase 1 - proves PreFab can detect adjacent blocks correctly.
     *
     * @param player The player to send chat messages to
     */
    public void debugAdjacentBlocks(Player player) {
        if (level == null) {
            player.displayClientMessage(Component.literal("§cError: Level is null"), false);
            return;
        }

        player.displayClientMessage(Component.literal("§6=== PreFab Adjacent Blocks ==="), false);

        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = this.getBlockPos().relative(dir);
            BlockEntity be = this.level.getBlockEntity(adjacentPos);

            if (be != null) {
                // Try to get capabilities from the adjacent block
                IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                    adjacentPos, dir.getOpposite());
                IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    adjacentPos, dir.getOpposite());
                IEnergyStorage energyStorage = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, adjacentPos, dir.getOpposite());

                String blockName = be.getBlockState().getBlock().getName().getString();

                player.displayClientMessage(Component.literal(
                    String.format("§6%s: §7%s §a[Items:%s Fluids:%s Energy:%s]",
                        dir.name(),
                        blockName,
                        itemHandler != null ? "✓" : "✗",
                        fluidHandler != null ? "✓" : "✗",
                        energyStorage != null ? "✓" : "✗"
                    )
                ), false);
            } else {
                player.displayClientMessage(Component.literal(
                    String.format("§6%s: §8No block entity", dir.name())
                ), false);
            }
        }
    }

    // ===== Cached Rates =====

    public Map<String, Double> getCachedRates() {
        return new HashMap<>(cachedRates);
    }

    public void setCachedRate(String resourceId, double ratePerTick) {
        cachedRates.put(resourceId, ratePerTick);
        setChanged();
    }

    public void clearCachedRates() {
        cachedRates.clear();
        setChanged();
    }

    // ===== UUID Lookup System (Phase 2) =====

    /**
     * Cache an Importer's position for fast lookup.
     * Called by GUI or when Importer/Exporter is first accessed.
     *
     * @param uuid The Importer UUID
     * @param pos The Importer position
     */
    public void cacheImporterPosition(UUID uuid, BlockPos pos) {
        importerCache.put(uuid, pos);
    }

    /**
     * Cache an Exporter's position for fast lookup.
     * Called by GUI or when Importer/Exporter is first accessed.
     *
     * @param uuid The Exporter UUID
     * @param pos The Exporter position
     */
    public void cacheExporterPosition(UUID uuid, BlockPos pos) {
        exporterCache.put(uuid, pos);
    }

    /**
     * Find Importer block by UUID in CM dimension.
     * Uses cached position (O(1)). Returns null if not cached or block broken.
     *
     * @param cmLevel The CM dimension level
     * @param targetUUID UUID of the target Importer
     * @return The ImporterBlockEntity, or null if not found
     */
    @Nullable
    public ImporterBlockEntity findImporterByUUID(ServerLevel cmLevel, UUID targetUUID) {
        if (targetUUID == null) {
            return null;
        }

        // Look up cached position
        BlockPos cachedPos = importerCache.get(targetUUID);
        if (cachedPos != null) {
            BlockEntity be = cmLevel.getBlockEntity(cachedPos);
            if (be instanceof ImporterBlockEntity importer
                    && importer.getImporterUUID().equals(targetUUID)) {
                return importer;
            }
            // Cache miss - position changed or block broken
            importerCache.remove(targetUUID);
        }

        return null; // Not found - face links to broken/missing Importer
    }

    /**
     * Find Exporter block by UUID in CM dimension.
     * Uses cached position (O(1)). Returns null if not cached or block broken.
     *
     * @param cmLevel The CM dimension level
     * @param targetUUID UUID of the target Exporter
     * @return The ExporterBlockEntity, or null if not found
     */
    @Nullable
    public ExporterBlockEntity findExporterByUUID(ServerLevel cmLevel, UUID targetUUID) {
        if (targetUUID == null) {
            return null;
        }

        // Look up cached position
        BlockPos cachedPos = exporterCache.get(targetUUID);
        if (cachedPos != null) {
            BlockEntity be = cmLevel.getBlockEntity(cachedPos);
            if (be instanceof ExporterBlockEntity exporter
                    && exporter.getExporterUUID().equals(targetUUID)) {
                return exporter;
            }
            // Cache miss - position changed or block broken
            exporterCache.remove(targetUUID);
        }

        return null; // Not found - face links to broken/missing Exporter
    }

    // ===== NBT Serialization =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // Save room linkage
        if (roomCode != null) {
            tag.putString("roomCode", roomCode);
        }
        if (roomCenter != null) {
            tag.putLong("roomCenter", roomCenter.asLong());
        }

        // Save machine state
        tag.putString("state", currentState.name());

        // Save face configurations
        CompoundTag facesTag = new CompoundTag();
        for (Map.Entry<Direction, FaceConfig> entry : faceConfigs.entrySet()) {
            facesTag.put(entry.getKey().getName(), entry.getValue().toNBT());
        }
        tag.put("faceConfigs", facesTag);

        // Save cached rates
        if (!cachedRates.isEmpty()) {
            ListTag ratesList = new ListTag();
            for (Map.Entry<String, Double> entry : cachedRates.entrySet()) {
                CompoundTag rateEntry = new CompoundTag();
                rateEntry.putString("id", entry.getKey());
                rateEntry.putDouble("rate", entry.getValue());
                ratesList.add(rateEntry);
            }
            tag.put("rates", ratesList);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Load room linkage
        if (tag.contains("roomCode")) {
            roomCode = tag.getString("roomCode");
        }
        if (tag.contains("roomCenter")) {
            roomCenter = BlockPos.of(tag.getLong("roomCenter"));
        }

        // Load machine state
        if (tag.contains("state")) {
            try {
                currentState = MachineState.valueOf(tag.getString("state"));
            } catch (IllegalArgumentException e) {
                currentState = MachineState.BUILDING;
            }
        }

        // Load face configurations
        if (tag.contains("faceConfigs")) {
            CompoundTag facesTag = tag.getCompound("faceConfigs");
            for (Direction dir : Direction.values()) {
                if (facesTag.contains(dir.getName())) {
                    faceConfigs.put(dir, FaceConfig.fromNBT(facesTag.getCompound(dir.getName())));
                }
            }
        }

        // Load cached rates
        if (tag.contains("rates")) {
            cachedRates.clear();
            ListTag ratesList = tag.getList("rates", Tag.TAG_COMPOUND);
            for (int i = 0; i < ratesList.size(); i++) {
                CompoundTag rateEntry = ratesList.getCompound(i);
                String id = rateEntry.getString("id");
                double rate = rateEntry.getDouble("rate");
                cachedRates.put(id, rate);
            }
        }
    }

    // ===== MenuProvider Implementation =====

    // Store the clicked face direction for GUI default selection
    private Direction clickedFace = Direction.NORTH;

    public void setClickedFace(Direction face) {
        this.clickedFace = face;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("PreFab Configuration");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PreFabConfigMenu(containerId, playerInventory, this.getBlockPos(), this.clickedFace, null);
    }

    // ===== Client Sync =====

    /**
     * Get packet to sync BlockEntity to client.
     * Called by sendBlockUpdated().
     */
    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Get NBT data to sync to client.
     * Called when chunk loads on client or when getUpdatePacket() is sent.
     */
    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    /**
     * Handle NBT data from server on client side.
     */
    @Override
    public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }
}
