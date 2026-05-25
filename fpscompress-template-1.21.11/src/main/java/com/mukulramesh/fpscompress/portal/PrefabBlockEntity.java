package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.gui.PreFabConfigMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
 *
 * Note: Fields are package-private to allow direct access by service classes
 * (DisplayPreferenceManager, RateCalculationEngine, PrefabNBTSerializer, etc.)
 * This is an intentional design choice for the delegation pattern.
 */
public class PrefabBlockEntity extends BlockEntity implements MenuProvider {

    // CHECKSTYLE:OFF VisibilityModifier
    // Fields are intentionally package-private for service class access
    // (DisplayPreferenceManager, RateCalculationEngine, PrefabNBTSerializer, etc.)

    // Room linkage
    @Nullable
    String roomCode;

    @Nullable
    BlockPos roomCenter;

    // Room dimensions (internal size - can be non-cubic, e.g., 5x3x7)
    @Nullable
    Integer roomSizeX;
    @Nullable
    Integer roomSizeY;
    @Nullable
    Integer roomSizeZ;

    // Machine state
    MachineState currentState = MachineState.BUILDING;

    // Face configurations (Phase 1)
    final Map<Direction, FaceConfig> faceConfigs = new EnumMap<>(Direction.class);

    // Cached production rates: resource ID → rate per tick (positive = output, negative = input)
    final Map<String, Double> cachedRates = new HashMap<>();

    // Phase 6: Per-UUID rate storage for multi-output routing
    // Maps equipment UUID → (resource ID → rate per tick)
    // Example: {ExporterUUID-A: {iron: +5.0}, ExporterUUID-B: {copper: +3.0}}
    final Map<UUID, Map<String, Double>> importerExporterRates = new HashMap<>();

    // Phase 4: Rate measurement during SIMULATING state
    ResourceDeltaTracker deltaTracker = new ResourceDeltaTracker();
    long simulationStartTick = 0;
    long simulationEndTick = 0;
    long cachedStateStartTick = 0; // When CACHED state started
    final Map<String, Long> cachedProduction = new HashMap<>(); // Accumulated during CACHED
    String lastSimulationResult = ""; // Result of last simulation (for GUI display)

    // Minimum simulation time enforcement (Phase 2)
    long simulationElapsedTicks = 0; // Ticks elapsed in SIMULATING state
    long simulationRequiredTicks = 0; // Snapshot of config at simulation start

    // Phase 5: Fractional accumulators for cached production
    final Map<String, Double> itemAccumulators = new HashMap<>(); // Resource ID → fractional accumulator

    // HALTED state exponential backoff (performance optimization)
    int haltedRetryInterval = 1; // Current retry interval (ticks)
    int ticksSinceLastRetry = 0; // Ticks since last retry attempt

    // UUID lookup caching (O(1) fast path for repeated lookups)
    final Map<UUID, BlockPos> importerCache = new HashMap<>();
    final Map<UUID, BlockPos> exporterCache = new HashMap<>();

    // Display preferences (persist in NBT, synced to clients)
    // Package-private for service access
    com.mukulramesh.fpscompress.gui.RateDisplayMode currentDisplayMode =
        com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK;
    @Nullable
    String focusedResourceId = null; // null = no focus
    int autoNormalizedTicks = 1; // LCM result (1 = no normalization)
    boolean useAutoNormalize = true; // true = use auto-normalized display (default)
    com.mukulramesh.fpscompress.gui.RateDisplayMode autoNormalizedDisplayMode =
        com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK; // Original mode from auto-normalize

    // Custom name assigned by player (null = no name, show default)
    @Nullable
    String prefabName = null;

    // CHECKSTYLE:ON VisibilityModifier

    // Delegated services (all 7 services)
    private final DisplayPreferenceManager displayManager;
    private final InventoryScanningService scanningService;
    private final CachedProductionHandler cachedHandler;
    private final RateCalculationEngine rateEngine;
    private final TransportTickHandler transportHandler;
    private final StateTransitionManager stateManager;
    private final PrefabNBTSerializer nbtSerializer;

    @SuppressWarnings("this-escape")
    public PrefabBlockEntity(BlockPos pos, BlockState state) {
        super(FPSCompress.PREFAB_BE.get(), pos, state);

        // Initialize all 6 faces to DISABLED
        for (Direction dir : Direction.values()) {
            faceConfigs.put(dir, new FaceConfig());
        }

        // Initialize service delegates (safe to pass 'this' as services only store reference)
        this.displayManager = new DisplayPreferenceManager(this);
        this.scanningService = new InventoryScanningService(this);
        this.cachedHandler = new CachedProductionHandler(this);
        this.rateEngine = new RateCalculationEngine(this);
        this.transportHandler = new TransportTickHandler(this);
        this.stateManager = new StateTransitionManager(this);
        this.nbtSerializer = new PrefabNBTSerializer(this);
    }

    // ===== Service Getters (Package-private for inter-service communication) =====

    RateCalculationEngine getRateEngine() {
        return rateEngine;
    }

    CachedProductionHandler getCachedHandler() {
        return cachedHandler;
    }

    InventoryScanningService getScanningService() {
        return scanningService;
    }

    StateTransitionManager getStateManager() {
        return stateManager;
    }

    TransportTickHandler getTransportHandler() {
        return transportHandler;
    }

    DisplayPreferenceManager getDisplayManager() {
        return displayManager;
    }

    PrefabNBTSerializer getNBTSerializer() {
        return nbtSerializer;
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

    @Nullable
    public Integer getRoomSizeX() {
        return roomSizeX;
    }

    @Nullable
    public Integer getRoomSizeY() {
        return roomSizeY;
    }

    @Nullable
    public Integer getRoomSizeZ() {
        return roomSizeZ;
    }

    public void setRoomSize(int sizeX, int sizeY, int sizeZ) {
        this.roomSizeX = sizeX;
        this.roomSizeY = sizeY;
        this.roomSizeZ = sizeZ;
        setChanged();
    }

    /**
     * Check if room dimensions are available.
     *
     * @return true if all three dimensions are set
     */
    public boolean hasRoomDimensions() {
        return roomSizeX != null && roomSizeY != null && roomSizeZ != null;
    }

    // ===== Machine State =====

    public MachineState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(MachineState state) {
        this.currentState = state;
        setChanged();
    }

    // ===== PreFab Name =====

    /**
     * Get custom name assigned by player.
     *
     * @return Custom name (null if not set)
     */
    @Nullable
    public String getPrefabName() {
        return prefabName;
    }

    /**
     * Set custom name for this PreFab.
     * Sanitizes input: trims whitespace, enforces max length, null if empty.
     *
     * @param name Custom name (null or empty to clear)
     */
    public void setPrefabName(@Nullable String name) {
        // Sanitize: trim whitespace, null if empty
        String sanitized = (name == null || name.isBlank()) ? null : name.trim();
        if (sanitized != null && sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32); // Enforce max length
        }
        this.prefabName = sanitized;
        setChanged(); // Trigger NBT save
    }

    /**
     * Increment simulation elapsed time (called each tick during SIMULATING state).
     */
    public void incrementSimulationElapsed() {
        simulationElapsedTicks++;
    }

    /**
     * Get elapsed ticks in current simulation.
     */
    public long getSimulationElapsedTicks() {
        return simulationElapsedTicks;
    }

    /**
     * Get required ticks for simulation (snapshot from config at simulation start).
     */
    public long getSimulationRequiredTicks() {
        return simulationRequiredTicks;
    }

    /**
     * Check if player is in creative mode.
     */

    /**
     * Get delta tracker for GUI display (creative mode only).
     */
    public ResourceDeltaTracker getDeltaTracker() {
        return deltaTracker;
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

        // Phase 6: If in CACHED state, validate that reconfiguration doesn't break routing
        if (currentState == MachineState.CACHED) {
            CachedConfigurationValidator.ValidationResult validation = validateCachedConfiguration();
            if (!validation.success()) {
                // Configuration error - reset to BUILDING (requires re-simulation)
                FPSCompress.LOGGER.error("Face reconfiguration broke routing, resetting to BUILDING:");
                for (String error : validation.errors()) {
                    FPSCompress.LOGGER.error("  {}", error);
                }
                lastSimulationResult = "Configuration error: " + String.join("; ", validation.errors());
                setCurrentState(MachineState.BUILDING);
            }
        }

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

    // Phase 6: Per-UUID rate accessor methods

    public Map<UUID, Map<String, Double>> getImporterExporterRates() {
        // Defensive copy
        Map<UUID, Map<String, Double>> copy = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Double>> entry : importerExporterRates.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    public void setRateForUUID(UUID uuid, String resourceId, double ratePerTick) {
        importerExporterRates.computeIfAbsent(uuid, k -> new HashMap<>())
                             .put(resourceId, ratePerTick);
        setChanged();
    }

    public void clearImporterExporterRates() {
        importerExporterRates.clear();
        setChanged();
    }

    public long getSimulationStartTick() {
        return simulationStartTick;
    }

    public long getSimulationEndTick() {
        return simulationEndTick;
    }

    public long getCachedStateStartTick() {
        return cachedStateStartTick;
    }

    /**
     * Get accumulated production during CACHED state (for creative mode display).
     *
     * @return Map of resource ID → total produced/consumed
     */
    public java.util.Map<String, Long> getCachedProduction() {
        return new java.util.HashMap<>(cachedProduction);
    }

    /**
     * Get live import/export stats for GUI display.
     *
     * @return Map of resource ID → [imported, exported]
     */
    public java.util.Map<String, long[]> getLiveStats() {
        java.util.Map<String, long[]> stats = new java.util.HashMap<>();
        for (String resourceId : deltaTracker.getAllTrackedResources()) {
            long imported = deltaTracker.getTotalImported(resourceId);
            long exported = deltaTracker.getTotalExported(resourceId);
            stats.put(resourceId, new long[]{imported, exported});
        }
        return stats;
    }

    /**
     * Get last simulation result message (for GUI display).
     *
     * @return Result message (empty if no simulation run yet)
     */
    public String getLastSimulationResult() {
        return lastSimulationResult;
    }

    // ===== Display Preference Accessors (Delegated to DisplayPreferenceManager) =====

    /**
     * Get current display mode for rate visualization.
     *
     * @return Display mode (PER_TICK, PER_SECOND, etc.)
     */
    public com.mukulramesh.fpscompress.gui.RateDisplayMode getCurrentDisplayMode() {
        return displayManager.getCurrentDisplayMode();
    }

    /**
     * Set display mode for rate visualization.
     *
     * @param mode New display mode
     */
    public void setCurrentDisplayMode(com.mukulramesh.fpscompress.gui.RateDisplayMode mode) {
        displayManager.setCurrentDisplayMode(mode);
    }

    /**
     * Get focused resource ID (null if no focus).
     *
     * @return Resource ID or null
     */
    @Nullable
    public String getFocusedResourceId() {
        return displayManager.getFocusedResourceId();
    }

    /**
     * Set focused resource ID for normalization.
     *
     * @param id Resource ID to focus on (null to clear focus)
     */
    public void setFocusedResourceId(@Nullable String id) {
        displayManager.setFocusedResourceId(id);
    }

    /**
     * Get auto-normalized ticks (LCM result).
     *
     * @return Normalized ticks (1 = no normalization)
     */
    public int getAutoNormalizedTicks() {
        return displayManager.getAutoNormalizedTicks();
    }

    /**
     * Set auto-normalized ticks (LCM result).
     *
     * @param ticks Normalized ticks (minimum 1)
     */
    public void setAutoNormalizedTicks(int ticks) {
        displayManager.setAutoNormalizedTicks(ticks);
    }

    /**
     * Get whether to use auto-normalized display.
     *
     * @return true if using auto-normalize, false for manual time scale
     */
    public boolean getUseAutoNormalize() {
        return displayManager.getUseAutoNormalize();
    }

    /**
     * Set whether to use auto-normalized display.
     *
     * @param use true to use auto-normalize, false for manual time scale
     */
    public void setUseAutoNormalize(boolean use) {
        displayManager.setUseAutoNormalize(use);
    }

    /**
     * Get the original auto-normalized display mode (from LCM calculation).
     *
     * @return Display mode suggested by auto-normalize
     */
    public com.mukulramesh.fpscompress.gui.RateDisplayMode getAutoNormalizedDisplayMode() {
        return displayManager.getAutoNormalizedDisplayMode();
    }

    /**
     * Set the original auto-normalized display mode.
     *
     * @param mode Display mode from auto-normalize
     */
    public void setAutoNormalizedDisplayMode(com.mukulramesh.fpscompress.gui.RateDisplayMode mode) {
        displayManager.setAutoNormalizedDisplayMode(mode);
    }

    /**
     * Get localized item name from resource ID.
     * Used for user-friendly error messages.
     *
     * @param resourceId The resource ID (e.g., "minecraft:iron_ingot")
     * @return Localized name (e.g., "Iron Ingot") or fallback to resource ID
     */

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

    // ===== NBT Serialization (Delegated to PrefabNBTSerializer) =====

    /**
     * Phase 6: Validates that all UUIDs with cached rates have at least one face mapped.
     * Called during state transitions to CACHED and during face reconfiguration.
     *
     * @return ValidationResult with success flag and error/warning messages
     */
    private CachedConfigurationValidator.ValidationResult validateCachedConfiguration() {
        return CachedConfigurationValidator.validate(importerExporterRates, faceConfigs);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        nbtSerializer.saveAdditional(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nbtSerializer.loadAdditional(tag, registries);
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

    // ===== Ticking Logic (Delegated to TransportTickHandler) =====

    /**
     * Server-side tick method for resource transport.
     * Delegates to TransportTickHandler.
     *
     * @param level The level (server-side)
     * @param pos The PreFab position
     * @param state The block state
     * @param prefab The PreFab block entity
     */
    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                           PrefabBlockEntity prefab) {
        prefab.transportHandler.tick(level, pos, state);
    }


    /**
     * Handle PULL mode: Extract from Overworld → Insert to Importer.
     *
     * @param face The face direction
     * @param config The face configuration
     */

    /**
     * Handle PUSH mode: Extract from Exporter → Insert to Overworld.
     *
     * @param face The face direction
     * @param config The face configuration
     */

    // ===== State Transition Methods (Delegated to StateTransitionManager) =====

    /**
     * OLD METHODS MOVED TO InventoryScanningService AND StateTransitionManager
     * - getRoomBoundsFromCM()
     * - scanForWall()
     * - sendInventoryScanToChat()
     * - getLocalizedResourceName()
     * - scanImporterExporterBuffers()
     * - scanImporterBuffer()
     * - scanExporterBuffer()
     * All now handled by InventoryScanningService.
     */


    // ===== State Transition Methods (Phase 4) =====

    /**
     * Transition BUILDING → SIMULATING.
     * Delegates to StateTransitionManager.
     *
     * @param player The player starting the simulation (for debug chat output)
     */
    public void startSimulation(@Nullable Player player) {
        stateManager.startSimulation(player);
    }

    /**
     * Transition SIMULATING → CACHED.
     * Delegates to StateTransitionManager.
     *
     * @param player The player finishing the simulation (for debug chat output)
     */
    public void finishSimulation(@Nullable Player player) {
        stateManager.finishSimulation(player);
    }



    /**
     * Transition CACHED/HALTED → BUILDING.
     * Delegates to StateTransitionManager.
     */
    public void resetToBuilding() {
        stateManager.resetToBuilding();
    }



    // ===== Phase 5: Cached Production (Delegated to CachedProductionHandler) =====

    /**
     * Tick cached production using fractional math.
     * Delegates to CachedProductionHandler.
     */


    /**
     * Phase 6: Transfer cached output from a specific Exporter UUID to Overworld via mapped faces.
     *
     * @param exporterUUID The UUID of the Exporter that produced this resource
     * @param resourceId The resource identifier (e.g., "minecraft:iron_ingot")
     * @param amount The number of items to transfer
     * @return true if fully transferred, false if blocked
     */

    /**
     * Phase 6: Transfer cached input from Overworld to a specific Importer UUID via mapped faces.
     *
     * @param importerUUID The UUID of the Importer that needs this resource
     * @param resourceId The resource identifier (e.g., "minecraft:coal")
     * @param amount The number of items to transfer
     * @return true if all items obtained, false if starved
     */
}
