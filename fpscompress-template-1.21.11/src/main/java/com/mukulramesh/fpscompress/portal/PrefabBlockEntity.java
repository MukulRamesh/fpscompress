package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.Config;
import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.gui.PreFabConfigMenu;
import com.mukulramesh.fpscompress.scanner.InventoryScanner;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import com.mukulramesh.fpscompress.spatial.CMInterceptorImpl;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

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

    // Room dimensions (internal size - can be non-cubic, e.g., 5x3x7)
    @Nullable
    private Integer roomSizeX;
    @Nullable
    private Integer roomSizeY;
    @Nullable
    private Integer roomSizeZ;

    // Machine state
    private MachineState currentState = MachineState.BUILDING;

    // Face configurations (Phase 1)
    private final Map<Direction, FaceConfig> faceConfigs = new EnumMap<>(Direction.class);

    // Cached production rates: resource ID → rate per tick (positive = output, negative = input)
    private final Map<String, Double> cachedRates = new HashMap<>();

    // Phase 6: Per-UUID rate storage for multi-output routing
    // Maps equipment UUID → (resource ID → rate per tick)
    // Example: {ExporterUUID-A: {iron: +5.0}, ExporterUUID-B: {copper: +3.0}}
    private final Map<UUID, Map<String, Double>> importerExporterRates = new HashMap<>();

    // Phase 4: Rate measurement during SIMULATING state
    private ResourceDeltaTracker deltaTracker = new ResourceDeltaTracker();
    private long simulationStartTick = 0;
    private long simulationEndTick = 0;
    private long cachedStateStartTick = 0; // When CACHED state started
    private final Map<String, Long> cachedProduction = new HashMap<>(); // Accumulated during CACHED
    private String lastSimulationResult = ""; // Result of last simulation (for GUI display)

    // Minimum simulation time enforcement (Phase 2)
    private long simulationElapsedTicks = 0; // Ticks elapsed in SIMULATING state
    private long simulationRequiredTicks = 0; // Snapshot of config at simulation start

    // Phase 5: Fractional accumulators for cached production
    private final Map<String, Double> itemAccumulators = new HashMap<>(); // Resource ID → fractional accumulator

    // HALTED state exponential backoff (performance optimization)
    private int haltedRetryInterval = 1; // Current retry interval (ticks)
    private int ticksSinceLastRetry = 0; // Ticks since last retry attempt

    // UUID lookup caching (O(1) fast path for repeated lookups)
    private final Map<UUID, BlockPos> importerCache = new HashMap<>();
    private final Map<UUID, BlockPos> exporterCache = new HashMap<>();

    // Display preferences (persist in NBT, synced to clients)
    private com.mukulramesh.fpscompress.gui.RateDisplayMode currentDisplayMode =
        com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK;
    @Nullable
    private String focusedResourceId = null; // null = no focus
    private int autoNormalizedTicks = 1; // LCM result (1 = no normalization)
    private boolean useAutoNormalize = true; // true = use auto-normalized display (default)
    private com.mukulramesh.fpscompress.gui.RateDisplayMode autoNormalizedDisplayMode =
        com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK; // Original mode from auto-normalize

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
    private boolean isCreativeMode(Player player) {
        return player != null && player.getAbilities().instabuild;
    }

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

    // ===== Display Preference Accessors =====

    /**
     * Get current display mode for rate visualization.
     *
     * @return Display mode (PER_TICK, PER_SECOND, etc.)
     */
    public com.mukulramesh.fpscompress.gui.RateDisplayMode getCurrentDisplayMode() {
        return currentDisplayMode;
    }

    /**
     * Set display mode for rate visualization.
     *
     * @param mode New display mode
     */
    public void setCurrentDisplayMode(com.mukulramesh.fpscompress.gui.RateDisplayMode mode) {
        this.currentDisplayMode = mode;
        setChanged();
    }

    /**
     * Get focused resource ID (null if no focus).
     *
     * @return Resource ID or null
     */
    @Nullable
    public String getFocusedResourceId() {
        return focusedResourceId;
    }

    /**
     * Set focused resource ID for normalization.
     *
     * @param id Resource ID to focus on (null to clear focus)
     */
    public void setFocusedResourceId(@Nullable String id) {
        this.focusedResourceId = id;
        setChanged();
    }

    /**
     * Get auto-normalized ticks (LCM result).
     *
     * @return Normalized ticks (1 = no normalization)
     */
    public int getAutoNormalizedTicks() {
        return autoNormalizedTicks;
    }

    /**
     * Set auto-normalized ticks (LCM result).
     *
     * @param ticks Normalized ticks (minimum 1)
     */
    public void setAutoNormalizedTicks(int ticks) {
        this.autoNormalizedTicks = Math.max(1, ticks);
        setChanged();
    }

    /**
     * Get whether to use auto-normalized display.
     *
     * @return true if using auto-normalize, false for manual time scale
     */
    public boolean getUseAutoNormalize() {
        return useAutoNormalize;
    }

    /**
     * Set whether to use auto-normalized display.
     *
     * @param use true to use auto-normalize, false for manual time scale
     */
    public void setUseAutoNormalize(boolean use) {
        this.useAutoNormalize = use;
        setChanged();
    }

    /**
     * Get the original auto-normalized display mode (from LCM calculation).
     *
     * @return Display mode suggested by auto-normalize
     */
    public com.mukulramesh.fpscompress.gui.RateDisplayMode getAutoNormalizedDisplayMode() {
        return autoNormalizedDisplayMode;
    }

    /**
     * Set the original auto-normalized display mode.
     *
     * @param mode Display mode from auto-normalize
     */
    public void setAutoNormalizedDisplayMode(com.mukulramesh.fpscompress.gui.RateDisplayMode mode) {
        this.autoNormalizedDisplayMode = mode;
        setChanged();
    }

    /**
     * Get localized item name from resource ID.
     * Used for user-friendly error messages.
     *
     * @param resourceId The resource ID (e.g., "minecraft:iron_ingot")
     * @return Localized name (e.g., "Iron Ingot") or fallback to resource ID
     */
    private String getLocalizedItemName(String resourceId) {
        try {
            net.minecraft.resources.ResourceLocation resLoc =
                net.minecraft.resources.ResourceLocation.parse(resourceId);
            net.minecraft.world.item.Item item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(resLoc);
            return item.getName(new ItemStack(item)).getString();
        } catch (Exception e) {
            // Fallback: Return just the item name part (after colon)
            return resourceId.contains(":") ? resourceId.substring(resourceId.indexOf(':') + 1) : resourceId;
        }
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

    /**
     * Apply migration rules when loading PreFab from item NBT.
     * Handles state transitions for PreFab-as-Item portability:
     * - SIMULATING → BUILDING (partial measurement invalid after chunks unload)
     * - HALTED → CACHED (location-specific condition no longer applies, but rates preserved)
     * - CACHED → preserved (portable production data)
     *
     * @param tag The NBT tag from item or world save
     */
    private void applyItemNBTMigration(CompoundTag tag) {
        // Check schema version for future migrations
        int schemaVersion = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 0;

        if (schemaVersion == 0) {
            // Legacy NBT (pre-versioning) - no migrations needed yet
            FPSCompress.LOGGER.debug("Loading PreFab with legacy NBT (no schema version)");
        }

        // Load machine state with migration rules
        if (tag.contains("state")) {
            String stateStr = tag.getString("state");

            // Migration Rule 1: SIMULATING → BUILDING
            // Rationale: deltaTracker contains partial measurement tied to specific chunk state.
            // When PreFab breaks, chunks unload and transport stops. Partial deltaTracker
            // data becomes invalid. Player must restart simulation from BUILDING.
            if ("SIMULATING".equals(stateStr)) {
                currentState = MachineState.BUILDING;
                deltaTracker = new ResourceDeltaTracker(); // Clear partial measurement
                FPSCompress.LOGGER.info("PreFab item had SIMULATING state - reset to BUILDING "
                    + "(partial measurement invalid after chunk unload)");
            } else if ("HALTED".equals(stateStr)) {
                // Migration Rule 2: HALTED → CACHED
                // Rationale: HALTED is a temporary pause during CACHED operation. When PreFab
                // moves to new location, old HALTED condition no longer applies (different
                // adjacent blocks). Restore to CACHED state and let it try running again.
                // Cached rates are preserved - they're still valid!
                currentState = MachineState.CACHED;
                haltedRetryInterval = 1; // Reset backoff optimization
                ticksSinceLastRetry = 0;
                FPSCompress.LOGGER.info("PreFab item had HALTED state - restored to CACHED "
                    + "(cached rates preserved, new location may work)");
            } else {
                // Preserve other states (BUILDING, CACHED)
                try {
                    currentState = MachineState.valueOf(stateStr);
                } catch (IllegalArgumentException e) {
                    FPSCompress.LOGGER.warn("Unknown machine state '{}' - defaulting to BUILDING", stateStr);
                    currentState = MachineState.BUILDING;
                }
            }
        }

        // Recalculate cachedStateStartTick from current time (if CACHED or migrated from HALTED)
        // Rationale: cachedStateStartTick is used for GUI display ("Running for X seconds").
        // When PreFab moves, reset the timer to current game time.
        if (currentState == MachineState.CACHED && level != null) {
            cachedStateStartTick = level.getGameTime();
            FPSCompress.LOGGER.debug("PreFab item restored CACHED state - reset start tick to {}",
                cachedStateStartTick);
        }
    }

    /**
     * Validate loaded NBT data for corruption or inconsistencies.
     * Catches edge cases where NBT schema is valid but data is logically inconsistent.
     * Called at end of loadAdditional().
     */
    private void validateLoadedData() {
        // Edge Case 1: CACHED/HALTED state but no rates
        // This should never happen (both states require rates), but handle gracefully.
        // Check both cachedRates (old schema) and importerExporterRates (new schema v2+)
        if ((currentState == MachineState.CACHED || currentState == MachineState.HALTED)
                && cachedRates.isEmpty() && importerExporterRates.isEmpty()) {
            FPSCompress.LOGGER.warn("PreFab loaded with {} state but no rates - resetting to BUILDING",
                currentState);
            currentState = MachineState.BUILDING;
            itemAccumulators.clear();
            cachedProduction.clear();
        }

        // Edge Case 2: Not CACHED/HALTED but has rates
        // Rates should only exist in CACHED/HALTED states. Clear invalid data.
        if (currentState != MachineState.CACHED && currentState != MachineState.HALTED
                && (!cachedRates.isEmpty() || !importerExporterRates.isEmpty())) {
            FPSCompress.LOGGER.warn("PreFab loaded with rates but not CACHED/HALTED - clearing rates "
                + "(state: {})", currentState);
            cachedRates.clear();
            importerExporterRates.clear();
            itemAccumulators.clear();
            cachedProduction.clear();
        }

        // Edge Case 3: Room linkage incomplete
        // roomCode without roomCenter is invalid for REAL rooms (can't locate room).
        // EXCEPTION: Fake rooms (prefix "fake_") don't need roomCenter - they're for testing.
        if (roomCode != null && roomCenter == null && !roomCode.startsWith("fake_")) {
            FPSCompress.LOGGER.warn("PreFab has roomCode '{}' but no roomCenter - clearing roomCode",
                roomCode);
            roomCode = null;
        }

        // Edge Case 4: Face configs with invalid UUID links (only validate for BUILDING/SIMULATING)
        // CACHED and HALTED states don't need UUID links (use cached rates directly, CM chunks unloaded).
        // BUILDING/SIMULATING states need UUIDs to function.
        if (currentState != MachineState.CACHED && currentState != MachineState.HALTED) {
            for (Map.Entry<Direction, FaceConfig> entry : faceConfigs.entrySet()) {
                FaceConfig config = entry.getValue();
                if (config.getMode() != FaceMode.DISABLED && config.getTargetUUID() == null) {
                    FPSCompress.LOGGER.warn("Face {} has mode {} but no UUID (state: {}) - resetting to DISABLED",
                        entry.getKey(), config.getMode(), currentState);
                    config.setMode(FaceMode.DISABLED);
                }
            }
        }

        // Edge Case 5: Focused resource ID validation
        // Validate focused resource ID exists in cached rates
        if (focusedResourceId != null && !cachedRates.containsKey(focusedResourceId)) {
            FPSCompress.LOGGER.warn("Focused resource '{}' not in cached rates, clearing focus",
                focusedResourceId);
            focusedResourceId = null;
        }
    }

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

        // Schema version for future migrations (PreFab-as-Item feature)
        // Phase 6: Version 2 = Per-UUID rates
        tag.putInt("schemaVersion", 2);

        // Save room linkage (always persist)
        if (roomCode != null) {
            tag.putString("roomCode", roomCode);
        }
        if (roomCenter != null) {
            tag.putLong("roomCenter", roomCenter.asLong());
        }

        // Save room dimensions (always persist)
        if (roomSizeX != null) {
            tag.putInt("roomSizeX", roomSizeX);
        }
        if (roomSizeY != null) {
            tag.putInt("roomSizeY", roomSizeY);
        }
        if (roomSizeZ != null) {
            tag.putInt("roomSizeZ", roomSizeZ);
        }

        // Save machine state (always persist - migration handled on load)
        tag.putString("state", currentState.name());

        // Save face configurations (always persist)
        CompoundTag facesTag = new CompoundTag();
        for (Map.Entry<Direction, FaceConfig> entry : faceConfigs.entrySet()) {
            facesTag.put(entry.getKey().getName(), entry.getValue().toNBT());
        }
        tag.put("faceConfigs", facesTag);

        // Phase 6: Save per-UUID rates (only if CACHED or HALTED state - portable data)
        if ((currentState == MachineState.CACHED || currentState == MachineState.HALTED)
                && !importerExporterRates.isEmpty()) {
            ListTag uuidRatesList = new ListTag();

            for (Map.Entry<UUID, Map<String, Double>> uuidEntry : importerExporterRates.entrySet()) {
                CompoundTag uuidTag = new CompoundTag();
                uuidTag.putUUID("uuid", uuidEntry.getKey());

                ListTag resourceRatesList = new ListTag();
                for (Map.Entry<String, Double> rateEntry : uuidEntry.getValue().entrySet()) {
                    CompoundTag rateTag = new CompoundTag();
                    rateTag.putString("id", rateEntry.getKey());
                    rateTag.putDouble("rate", rateEntry.getValue());
                    resourceRatesList.add(rateTag);
                }
                uuidTag.put("rates", resourceRatesList);
                uuidRatesList.add(uuidTag);
            }

            tag.put("importerExporterRates", uuidRatesList);
        }

        // Save aggregate cached rates for backward compatibility (GUI display)
        // HALTED is a temporary pause during CACHED operation, so rates are still valid
        if ((currentState == MachineState.CACHED || currentState == MachineState.HALTED)
                && !cachedRates.isEmpty()) {
            ListTag ratesList = new ListTag();
            for (Map.Entry<String, Double> entry : cachedRates.entrySet()) {
                CompoundTag rateEntry = new CompoundTag();
                rateEntry.putString("id", entry.getKey());
                rateEntry.putDouble("rate", entry.getValue());
                ratesList.add(rateEntry);
            }
            tag.put("rates", ratesList);
        }

        // Save fractional accumulators (only if CACHED or HALTED state - portable data)
        if ((currentState == MachineState.CACHED || currentState == MachineState.HALTED)
                && !itemAccumulators.isEmpty()) {
            ListTag accumList = new ListTag();
            for (Map.Entry<String, Double> entry : itemAccumulators.entrySet()) {
                CompoundTag accumEntry = new CompoundTag();
                accumEntry.putString("id", entry.getKey());
                accumEntry.putDouble("accum", entry.getValue());
                accumList.add(accumEntry);
            }
            tag.put("itemAccumulators", accumList);
        }

        // Save accumulated production (only if CACHED or HALTED state - portable data)
        if ((currentState == MachineState.CACHED || currentState == MachineState.HALTED)
                && !cachedProduction.isEmpty()) {
            ListTag prodList = new ListTag();
            for (Map.Entry<String, Long> entry : cachedProduction.entrySet()) {
                CompoundTag prodEntry = new CompoundTag();
                prodEntry.putString("id", entry.getKey());
                prodEntry.putLong("amount", entry.getValue());
                prodList.add(prodEntry);
            }
            tag.put("cachedProduction", prodList);
        }

        // Save simulation end tick (only if CACHED or HALTED state - for GUI display)
        if (currentState == MachineState.CACHED || currentState == MachineState.HALTED) {
            tag.putLong("simulationEndTick", simulationEndTick);
        }

        // Save simulation timer fields (only if SIMULATING state - for minimum time enforcement)
        if (currentState == MachineState.SIMULATING) {
            tag.putLong("simulationElapsedTicks", simulationElapsedTicks);
            tag.putLong("simulationRequiredTicks", simulationRequiredTicks);
        }

        // Save display preferences (always persist)
        tag.putString("displayMode", currentDisplayMode.name());
        if (focusedResourceId != null) {
            tag.putString("focusedResourceId", focusedResourceId);
        }
        tag.putInt("autoNormalizedTicks", autoNormalizedTicks);
        tag.putBoolean("useAutoNormalize", useAutoNormalize);
        tag.putString("autoNormalizedDisplayMode", autoNormalizedDisplayMode.name());

        // TRANSIENT FIELDS (not saved for PreFab-as-Item portability):
        // - deltaTracker (only valid during active simulation)
        // - simulationStartTick (recalculate on simulation start)
        // - cachedStateStartTick (recalculate from current game time)
        // - haltedRetryInterval/ticksSinceLastRetry (optimization state)
        // - importerCache/exporterCache (rebuild from registry)
        // - lastSimulationResult (UI-only message)
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Apply migration rules for PreFab-as-Item (must happen first)
        applyItemNBTMigration(tag);

        // Load room linkage
        if (tag.contains("roomCode")) {
            roomCode = tag.getString("roomCode");
        }
        if (tag.contains("roomCenter")) {
            roomCenter = BlockPos.of(tag.getLong("roomCenter"));
        }

        // Load room dimensions
        if (tag.contains("roomSizeX")) {
            roomSizeX = tag.getInt("roomSizeX");
        }
        if (tag.contains("roomSizeY")) {
            roomSizeY = tag.getInt("roomSizeY");
        }
        if (tag.contains("roomSizeZ")) {
            roomSizeZ = tag.getInt("roomSizeZ");
        }

        // Machine state already loaded by applyItemNBTMigration() with migration rules applied

        // Load face configurations
        if (tag.contains("faceConfigs")) {
            CompoundTag facesTag = tag.getCompound("faceConfigs");
            for (Direction dir : Direction.values()) {
                if (facesTag.contains(dir.getName())) {
                    faceConfigs.put(dir, FaceConfig.fromNBT(facesTag.getCompound(dir.getName())));
                }
            }
        }

        // Phase 6: Load per-UUID rates (schema version 2+)
        int schemaVersion = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 1;
        loadRatesFromNBT(tag, schemaVersion);

        // Load fractional accumulators (only if CACHED state)
        if (tag.contains("itemAccumulators")) {
            itemAccumulators.clear();
            ListTag accumList = tag.getList("itemAccumulators", Tag.TAG_COMPOUND);
            for (int i = 0; i < accumList.size(); i++) {
                CompoundTag accumEntry = accumList.getCompound(i);
                String id = accumEntry.getString("id");
                double accum = accumEntry.getDouble("accum");
                itemAccumulators.put(id, accum);
            }
        }

        // Load fake Importer/Exporter registries (for test PreFabs from debug commands)
        if (tag.contains("importerRegistry")) {
            loadFakeImporterRegistry(tag.getCompound("importerRegistry"));
        }
        if (tag.contains("exporterRegistry")) {
            loadFakeExporterRegistry(tag.getCompound("exporterRegistry"));
        }

        // Load accumulated production (only if CACHED state)
        if (tag.contains("cachedProduction")) {
            cachedProduction.clear();
            ListTag prodList = tag.getList("cachedProduction", Tag.TAG_COMPOUND);
            for (int i = 0; i < prodList.size(); i++) {
                CompoundTag prodEntry = prodList.getCompound(i);
                String id = prodEntry.getString("id");
                long amount = prodEntry.getLong("amount");
                cachedProduction.put(id, amount);
            }
        }

        // Load simulation end tick (only if CACHED state - for GUI display)
        if (tag.contains("simulationEndTick")) {
            simulationEndTick = tag.getLong("simulationEndTick");
        }

        // Load simulation timer fields (only if SIMULATING state - for minimum time enforcement)
        if (tag.contains("simulationElapsedTicks")) {
            simulationElapsedTicks = tag.getLong("simulationElapsedTicks");
        }
        if (tag.contains("simulationRequiredTicks")) {
            simulationRequiredTicks = tag.getLong("simulationRequiredTicks");
        }

        // Load display preferences with validation
        if (tag.contains("displayMode")) {
            try {
                currentDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.valueOf(
                    tag.getString("displayMode"));
            } catch (IllegalArgumentException e) {
                FPSCompress.LOGGER.warn("Invalid displayMode '{}', resetting to PER_TICK",
                    tag.getString("displayMode"));
                currentDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK;
            }
        }

        if (tag.contains("focusedResourceId")) {
            String focusedId = tag.getString("focusedResourceId");
            // Validation: Ensure focused item actually exists in cached rates
            // (will be validated later in validateLoadedData() after rates are loaded)
            focusedResourceId = focusedId;
        }

        if (tag.contains("autoNormalizedTicks")) {
            int ticks = tag.getInt("autoNormalizedTicks");
            autoNormalizedTicks = Math.max(1, ticks); // Must be >= 1
        }

        if (tag.contains("useAutoNormalize")) {
            useAutoNormalize = tag.getBoolean("useAutoNormalize");
        }

        if (tag.contains("autoNormalizedDisplayMode")) {
            try {
                autoNormalizedDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.valueOf(
                    tag.getString("autoNormalizedDisplayMode"));
            } catch (IllegalArgumentException e) {
                FPSCompress.LOGGER.warn("Invalid autoNormalizedDisplayMode '{}', resetting to PER_TICK",
                    tag.getString("autoNormalizedDisplayMode"));
                autoNormalizedDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK;
            }
        }

        // TRANSIENT FIELDS (not loaded - will be initialized/rebuilt):
        // - deltaTracker (already initialized in constructor)
        // - simulationStartTick (will be set when simulation starts)
        // - cachedStateStartTick (recalculated by applyItemNBTMigration)
        // - haltedRetryInterval/ticksSinceLastRetry (reset to defaults)
        // - importerCache/exporterCache (empty - rebuilt on first lookup)
        // - lastSimulationResult (empty - no error message on load)

        // Validate loaded data for edge cases
        validateLoadedData();
    }

    /**
     * Load cached rates from NBT (both per-UUID and aggregate formats).
     * Extracted to keep loadAdditional() under 150 lines (checkstyle limit).
     */
    private void loadRatesFromNBT(CompoundTag tag, int schemaVersion) {
        FPSCompress.LOGGER.debug("Loading PreFab NBT - schemaVersion: {}, has importerExporterRates: {}, has rates: {}",
            schemaVersion, tag.contains("importerExporterRates"), tag.contains("rates"));

        if (tag.contains("importerExporterRates")) {
            importerExporterRates.clear();
            ListTag uuidRatesList = tag.getList("importerExporterRates", Tag.TAG_COMPOUND);

            FPSCompress.LOGGER.debug("Loading {} UUID rate entries", uuidRatesList.size());

            for (int i = 0; i < uuidRatesList.size(); i++) {
                CompoundTag uuidTag = uuidRatesList.getCompound(i);
                UUID uuid = uuidTag.getUUID("uuid");

                Map<String, Double> resourceRates = new HashMap<>();
                ListTag resourceRatesList = uuidTag.getList("rates", Tag.TAG_COMPOUND);
                for (int j = 0; j < resourceRatesList.size(); j++) {
                    CompoundTag rateTag = resourceRatesList.getCompound(j);
                    String id = rateTag.getString("id");
                    double rate = rateTag.getDouble("rate");
                    resourceRates.put(id, rate);
                }

                importerExporterRates.put(uuid, resourceRates);
                FPSCompress.LOGGER.debug("Loaded rates for UUID {}: {}",
                    uuid.toString().substring(0, 8), resourceRates);
            }
        } else if (schemaVersion == 1 && tag.contains("rates")) {
            // Migration: Convert old aggregate rates to per-UUID (schema version 1)
            FPSCompress.LOGGER.info("Migrating PreFab from schema v1 to v2 (per-UUID rates)");
            importerExporterRates.clear();

            // Old format: List of {id, rate} without UUID
            // Strategy: Clear and require re-simulation to learn proper UUID associations
            FPSCompress.LOGGER.warn(
                "Cannot migrate aggregate rates to per-UUID format - clearing rates (re-simulation required)"
            );
            // Keep cachedRates loaded (for GUI display) but don't populate importerExporterRates
            // State will transition to BUILDING due to validation failure
        }

        // Load aggregate cached rates for backward compatibility (GUI display, always load)
        if (tag.contains("rates")) {
            cachedRates.clear();
            ListTag ratesList = tag.getList("rates", Tag.TAG_COMPOUND);
            for (int i = 0; i < ratesList.size(); i++) {
                CompoundTag rateEntry = ratesList.getCompound(i);
                String id = rateEntry.getString("id");
                double rate = rateEntry.getDouble("rate");
                cachedRates.put(id, rate);
            }
        } else if (!importerExporterRates.isEmpty()) {
            // Derive cachedRates from importerExporterRates if no aggregate rates saved
            // (happens for test PreFabs that only have UUID-based rates)
            cachedRates.clear();
            for (Map<String, Double> uuidRates : importerExporterRates.values()) {
                for (Map.Entry<String, Double> entry : uuidRates.entrySet()) {
                    cachedRates.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }
            FPSCompress.LOGGER.debug("Derived {} aggregate rates from per-UUID rates for GUI display",
                cachedRates.size());
        }
    }

    /**
     * Load fake Importer registry from NBT (for test PreFabs).
     * Registers fake Importers in the global registry so they appear in GUI and tooltips.
     */
    private void loadFakeImporterRegistry(CompoundTag registryTag) {
        for (String uuidString : registryTag.getAllKeys()) {
            try {
                CompoundTag importerData = registryTag.getCompound(uuidString);
                UUID uuid = UUID.fromString(uuidString);
                String roomCodeValue = importerData.getString("roomCode");

                // Get frequency item to build display name
                String displayName = "Unnamed Importer";
                if (importerData.contains("frequencyItem")) {
                    CompoundTag freqItem = importerData.getCompound("frequencyItem");
                    String itemId = freqItem.getString("id");
                    if (!itemId.isEmpty()) {
                        // Extract item name from ID (e.g., "minecraft:diamond" -> "Diamond")
                        String itemName = itemId.contains(":") ? itemId.split(":")[1] : itemId;
                        itemName = itemName.substring(0, 1).toUpperCase(Locale.ROOT)
                                 + itemName.substring(1).replace("_", " ");
                        displayName = itemName + " Importer";
                    }
                }

                // Register in global registry (use PreFab position as fake position)
                ImporterExporterRegistry.registerImporter(uuid, this.getBlockPos(), displayName, roomCodeValue);

                FPSCompress.LOGGER.debug("Registered fake Importer {} with display name: {}",
                    uuid.toString().substring(0, 8), displayName);

            } catch (Exception e) {
                FPSCompress.LOGGER.error("Failed to load fake Importer from registry: {}", e.getMessage());
            }
        }
    }

    /**
     * Load fake Exporter registry from NBT (for test PreFabs).
     * Registers fake Exporters in the global registry so they appear in GUI and tooltips.
     */
    private void loadFakeExporterRegistry(CompoundTag registryTag) {
        for (String uuidString : registryTag.getAllKeys()) {
            try {
                CompoundTag exporterData = registryTag.getCompound(uuidString);
                UUID uuid = UUID.fromString(uuidString);
                String roomCodeValue = exporterData.getString("roomCode");

                // Get frequency item to build display name
                String displayName = "Unnamed Exporter";
                if (exporterData.contains("frequencyItem")) {
                    CompoundTag freqItem = exporterData.getCompound("frequencyItem");
                    String itemId = freqItem.getString("id");
                    if (!itemId.isEmpty()) {
                        // Extract item name from ID (e.g., "minecraft:diamond" -> "Diamond")
                        String itemName = itemId.contains(":") ? itemId.split(":")[1] : itemId;
                        itemName = itemName.substring(0, 1).toUpperCase(Locale.ROOT)
                                 + itemName.substring(1).replace("_", " ");
                        displayName = itemName + " Exporter";
                    }
                }

                // Register in global registry (use PreFab position as fake position)
                ImporterExporterRegistry.registerExporter(uuid, this.getBlockPos(), displayName, roomCodeValue);

                FPSCompress.LOGGER.debug("Registered fake Exporter {} with display name: {}",
                    uuid.toString().substring(0, 8), displayName);

            } catch (Exception e) {
                FPSCompress.LOGGER.error("Failed to load fake Exporter from registry: {}", e.getMessage());
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

    // ===== Ticking Logic (Phase 3) =====

    /**
     * Server-side tick method for resource transport.
     * Called every tick by BlockEntityTicker registered in PrefabBlock.getTicker().
     *
     * @param level The level (server-side)
     * @param pos The PreFab position
     * @param state The block state
     * @param prefab The PreFab block entity
     */
    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                           PrefabBlockEntity prefab) {
        if (level.isClientSide()) {
            return;
        }

        // SIMULATING state: Increment elapsed time counter
        if (prefab.getCurrentState() == MachineState.SIMULATING) {
            prefab.incrementSimulationElapsed();
        }

        // Phase 5: Handle CACHED and HALTED modes (fractional production without CM chunks)
        if (prefab.getCurrentState() == MachineState.CACHED
                || prefab.getCurrentState() == MachineState.HALTED) {
            prefab.tickCachedProduction();
            return; // Don't process faces during CACHED/HALTED modes
        }

        // Process each configured face (SIMULATING mode only, due to Phase 4 restriction)
        for (Direction face : Direction.values()) {
            FaceConfig config = prefab.getFaceConfig(face);
            if (config.getMode() == FaceMode.DISABLED) {
                continue; // Skip disabled faces
            }

            // Only handle ITEMS for MVP (fluids/energy in Phase 7)
            if (config.getResourceType() != ResourceFilter.ITEMS
                    && config.getResourceType() != ResourceFilter.ALL) {
                continue;
            }

            if (config.getMode() == FaceMode.PULL) {
                prefab.handlePullFace(face, config);
            } else if (config.getMode() == FaceMode.PUSH) {
                prefab.handlePushFace(face, config);
            }
        }
    }

    /**
     * Handle PULL mode: Extract from Overworld → Insert to Importer.
     *
     * @param face The face direction
     * @param config The face configuration
     */
    private void handlePullFace(Direction face, FaceConfig config) {
        // Only transport during SIMULATING state
        if (currentState != MachineState.SIMULATING) {
            return;
        }

        // 1. Get CM dimension
        ServerLevel cmLevel = getCMLevel();
        if (cmLevel == null) {
            return; // CM dimension not loaded
        }

        // 2. Query adjacent Overworld block capability
        BlockPos overworldPos = getBlockPos().relative(face);
        IItemHandler overworldHandler = level.getCapability(
            Capabilities.ItemHandler.BLOCK,
            overworldPos,
            face.getOpposite()
        );
        if (overworldHandler == null) {
            return; // No inventory adjacent
        }

        // 3. Find target Importer by UUID
        UUID targetUUID = config.getTargetUUID();
        if (targetUUID == null) {
            return; // Face not linked to Importer
        }

        ImporterBlockEntity importer = findImporterByUUID(cmLevel, targetUUID);
        if (importer == null) {
            // Cache miss - try building cache from registry
            ImporterExporterRegistry.Entry entry = ImporterExporterRegistry.getImporter(targetUUID);
            if (entry != null) {
                cacheImporterPosition(targetUUID, entry.pos());
                importer = findImporterByUUID(cmLevel, targetUUID);
            }
            if (importer == null) {
                return; // Importer broken/missing
            }
        }

        // 4. Try extracting from Overworld (up to 64 items per tick)
        net.minecraft.world.item.ItemStack extracted = net.minecraft.world.item.ItemStack.EMPTY;
        for (int slot = 0; slot < overworldHandler.getSlots(); slot++) {
            extracted = overworldHandler.extractItem(slot, 64, false);
            if (!extracted.isEmpty()) {
                break;
            }
        }
        if (extracted.isEmpty()) {
            return; // Nothing to transport
        }

        // 5. Insert to Importer buffer
        net.minecraft.world.item.ItemStack remainder = importer.insertItem(extracted);

        // Phase 4: Track imports during SIMULATING (Phase 6: with UUID)
        int transferred = extracted.getCount() - remainder.getCount();
        if (transferred > 0 && currentState == MachineState.SIMULATING) {
            String resourceId = BuiltInRegistries.ITEM.getKey(extracted.getItem()).toString();
            UUID importerUUID = config.getTargetUUID();  // Already validated non-null above
            deltaTracker.recordImport(importerUUID, resourceId, transferred, level.getGameTime());
        }

        // 6. Put remainder back if Importer buffer full
        if (!remainder.isEmpty()) {
            for (int slot = 0; slot < overworldHandler.getSlots(); slot++) {
                remainder = overworldHandler.insertItem(slot, remainder, false);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
    }

    /**
     * Handle PUSH mode: Extract from Exporter → Insert to Overworld.
     *
     * @param face The face direction
     * @param config The face configuration
     */
    private void handlePushFace(Direction face, FaceConfig config) {
        // Only transport during SIMULATING state
        if (currentState != MachineState.SIMULATING) {
            return;
        }

        // 1. Get CM dimension
        ServerLevel cmLevel = getCMLevel();
        if (cmLevel == null) {
            return; // CM dimension not loaded
        }

        // 2. Find target Exporter by UUID
        UUID targetUUID = config.getTargetUUID();
        if (targetUUID == null) {
            return; // Face not linked to Exporter
        }

        ExporterBlockEntity exporter = findExporterByUUID(cmLevel, targetUUID);
        if (exporter == null) {
            // Cache miss - try building cache from registry
            ImporterExporterRegistry.Entry entry = ImporterExporterRegistry.getExporter(targetUUID);
            if (entry != null) {
                cacheExporterPosition(targetUUID, entry.pos());
                exporter = findExporterByUUID(cmLevel, targetUUID);
            }
            if (exporter == null) {
                return; // Exporter broken/missing
            }
        }

        // 3. Try extracting from Exporter buffer (up to 64 items per tick)
        net.minecraft.world.item.ItemStack extracted = exporter.extractFromBuffer(64);
        if (extracted.isEmpty()) {
            return; // Nothing to transport
        }

        // 4. Query adjacent Overworld block capability
        BlockPos overworldPos = getBlockPos().relative(face);
        IItemHandler overworldHandler = level.getCapability(
            Capabilities.ItemHandler.BLOCK,
            overworldPos,
            face.getOpposite()
        );
        if (overworldHandler == null) {
            // Can't insert to Overworld - put back in Exporter
            exporter.insertItem(extracted);
            return;
        }

        // 5. Insert to Overworld
        net.minecraft.world.item.ItemStack remainder = extracted;
        for (int slot = 0; slot < overworldHandler.getSlots(); slot++) {
            remainder = overworldHandler.insertItem(slot, remainder, false);
            if (remainder.isEmpty()) {
                break;
            }
        }

        // Phase 4: Track exports during SIMULATING (Phase 6: with UUID)
        int transferred = extracted.getCount() - remainder.getCount();
        if (transferred > 0 && currentState == MachineState.SIMULATING) {
            String resourceId = BuiltInRegistries.ITEM.getKey(extracted.getItem()).toString();
            UUID exporterUUID = config.getTargetUUID();  // Already validated non-null above
            deltaTracker.recordExport(exporterUUID, resourceId, transferred, level.getGameTime());
        }

        // 6. Put remainder back in Exporter if Overworld full
        if (!remainder.isEmpty()) {
            exporter.insertItem(remainder);
        }
    }

    /**
     * Get CM dimension level.
     *
     * @return The CM dimension ServerLevel, or null if not available
     */
    @Nullable
    private ServerLevel getCMLevel() {
        if (level == null || level.isClientSide()) {
            return null;
        }
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        return server.getLevel(
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse("compactmachines:compact_world")
            )
        );
    }

    /**
     * Get room bounds AABB for inventory scanning.
     * Uses Machine Wall scanning to find room boundaries.
     *
     * @param roomCode The room code
     * @return AABB bounds of the room
     * @throws IllegalStateException if room bounds cannot be determined
     */
    private AABB getRoomBoundsFromCM(String roomCode) {
        try {
            ServerLevel cmLevel = getCMLevel();
            if (cmLevel == null || level == null || level.isClientSide()) {
                throw new IllegalStateException("CM level not available");
            }

            net.minecraft.server.MinecraftServer server = level.getServer();
            if (server == null) {
                throw new IllegalStateException("Server not available");
            }

            RoomCoordinateCache cache = RoomCoordinateCache.get(server);
            BlockPos center = cache.getRoomCenterByRoomCode(roomCode);

            if (center == null) {
                throw new IllegalStateException("Room center not cached for room: " + roomCode);
            }

            // Try multiple known wall block IDs (CM versions may differ)
            Block wallBlock = null;
            String[] wallBlockIds = {
                "compactmachines:solid_wall",  // Primary (most common)
                "compactmachines:wall",         // Fallback
                "compactmachines:machine_wall"  // Alternative name
            };

            for (String blockId : wallBlockIds) {
                Block candidate = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
                // Registry.get() never returns null for DefaultedRegistry, but may return AIR
                if (!candidate.equals(net.minecraft.world.level.block.Blocks.AIR)) {
                    wallBlock = candidate;
                    FPSCompress.LOGGER.debug("Found wall block: {}", blockId);
                    break;
                }
            }

            if (wallBlock == null) {
                throw new IllegalStateException("No valid CM wall block found in registry");
            }

            // Scan outward from center in all 6 directions to find walls
            int minX = scanForWall(cmLevel, center, Direction.WEST, wallBlock);
            int maxX = scanForWall(cmLevel, center, Direction.EAST, wallBlock);
            int minY = scanForWall(cmLevel, center, Direction.DOWN, wallBlock);
            int maxY = scanForWall(cmLevel, center, Direction.UP, wallBlock);
            int minZ = scanForWall(cmLevel, center, Direction.NORTH, wallBlock);
            int maxZ = scanForWall(cmLevel, center, Direction.SOUTH, wallBlock);

            // Add +1 to max coordinates to make AABB inclusive of interior (exclude walls)
            AABB bounds = new AABB(minX + 1, minY + 1, minZ + 1, maxX, maxY, maxZ);
            FPSCompress.LOGGER.info("Room bounds via Machine Wall scan: {}", bounds);
            return bounds;

        } catch (IllegalStateException e) {
            // Re-throw ISE as-is (expected failure cases)
            throw e;
        } catch (RuntimeException e) {
            // Wrap unexpected runtime exceptions
            FPSCompress.LOGGER.error("Failed to determine room bounds: {}", e.getMessage());
            throw new RuntimeException("Cannot determine room bounds", e);
        }
    }

    /**
     * Send inventory scan results to player chat (debug output).
     * Shows each resource and its count.
     *
     * @param player The player to send messages to
     * @param inventory The scanned inventory (resource ID -> count)
     * @param label Label for the scan ("Initial" or "Final")
     */
    private void sendInventoryScanToChat(@Nullable Player player, Map<String, Long> inventory, String label) {
        if (player == null) {
            return; // No player to send to (shouldn't happen, but safety check)
        }

        player.displayClientMessage(
            Component.literal("§6=== " + label + " Scan Results ==="), false);

        if (inventory.isEmpty()) {
            player.displayClientMessage(
                Component.literal("§7No resources found in room"), false);
        } else {
            player.displayClientMessage(
                Component.literal("§7Found " + inventory.size() + " resource types:"), false);

            // Sort by resource ID for consistent display
            inventory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String resourceId = entry.getKey();
                    long count = entry.getValue();

                    // Get localized name (or fallback to ID)
                    String displayName = getLocalizedResourceName(resourceId);

                    // Format: "  Iron Ingot: 64"
                    player.displayClientMessage(
                        Component.literal("  §3" + displayName + "§7: §e" + count), false);
                });
        }
    }

    /**
     * Get localized display name for a resource ID.
     * Handles items, fluids, and energy.
     *
     * @param resourceId The resource ID (e.g., "minecraft:iron_ingot" or "forge:energy")
     * @return Localized display name
     */
    private String getLocalizedResourceName(String resourceId) {
        // Special case: forge:energy
        if ("forge:energy".equals(resourceId)) {
            return "Energy (FE)";
        }

        try {
            // Try as item (DefaultedRegistry.get() never returns null, but may return AIR)
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM
                .get(ResourceLocation.parse(resourceId));
            if (!item.equals(net.minecraft.world.item.Items.AIR)) {
                return item.getName(new ItemStack(item)).getString();
            }

            // Try as fluid (DefaultedRegistry.get() never returns null, but may return EMPTY)
            net.minecraft.world.level.material.Fluid fluid = BuiltInRegistries.FLUID
                .get(ResourceLocation.parse(resourceId));
            if (!fluid.equals(net.minecraft.world.level.material.Fluids.EMPTY)) {
                return fluid.getFluidType().getDescription().getString();
            }
        } catch (RuntimeException e) {
            // Fallback to ID if lookup fails (e.g., invalid resource location)
        }

        // Fallback: return short ID (remove namespace)
        return resourceId.contains(":") ? resourceId.substring(resourceId.indexOf(':') + 1) : resourceId;
    }

    /**
     * Scan outward from center in given direction until Machine Wall found.
     * Returns coordinate of wall (inclusive).
     *
     * @param level The level
     * @param center Center position
     * @param dir Direction to scan
     * @param wallBlock The wall block to look for
     * @return Coordinate of wall in the direction's axis
     * @throws IllegalStateException if wall not found within max distance
     */
    private int scanForWall(ServerLevel level, BlockPos center, Direction dir, Block wallBlock) {
        BlockPos.MutableBlockPos pos = center.mutable();
        int maxDistance = 20; // Safety limit (largest CM room is ~13x13x13)

        for (int i = 0; i < maxDistance; i++) {
            pos.move(dir);
            BlockState state = level.getBlockState(pos);

            // Debug: Log first few blocks encountered
            if (i < 3) {
                FPSCompress.LOGGER.debug("Scanning {} at distance {}: {} at {}",
                    dir, i, BuiltInRegistries.BLOCK.getKey(state.getBlock()), pos.toShortString());
            }

            if (state.is(wallBlock)) {
                FPSCompress.LOGGER.debug("Found wall block {} in direction {} at distance {}",
                    BuiltInRegistries.BLOCK.getKey(wallBlock), dir, i);
                // Return coordinate based on direction
                return switch (dir.getAxis()) {
                    case X -> pos.getX();
                    case Y -> pos.getY();
                    case Z -> pos.getZ();
                };
            }
        }

        // Enhanced error message with what we actually found
        BlockPos.MutableBlockPos debugPos = center.mutable();
        StringBuilder foundBlocks = new StringBuilder();
        for (int i = 0; i < Math.min(5, maxDistance); i++) {
            debugPos.move(dir);
            BlockState state = level.getBlockState(debugPos);
            foundBlocks.append("\n  Distance ").append(i + 1).append(": ")
                .append(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }

        throw new IllegalStateException(
            "Machine Wall (" + BuiltInRegistries.BLOCK.getKey(wallBlock)
            + ") not found in direction " + dir + " after " + maxDistance + " blocks."
            + "\nScanned from: " + center.toShortString()
            + "\nBlocks found:" + foundBlocks.toString());
    }

    // ===== Importer/Exporter Buffer Scanning =====

    /**
     * Scan Importer/Exporter buffers and add to resource totals.
     * This prevents buffer contents from inflating rate calculations.
     *
     * @param cmLevel The CM dimension level
     * @param totals The resource totals map (modified in-place)
     */
    private void scanImporterExporterBuffers(ServerLevel cmLevel, Map<String, Long> totals) {
        // Scan all configured faces for Importer/Exporter links
        for (Direction face : Direction.values()) {
            FaceConfig config = getFaceConfig(face);
            UUID targetUUID = config.getTargetUUID();
            if (targetUUID == null) {
                continue; // Face not linked
            }

            if (config.getMode() == FaceMode.PULL) {
                // PULL face links to Importer - scan its buffer
                ImporterBlockEntity importer = findImporterByUUID(cmLevel, targetUUID);
                if (importer == null) {
                    // Cache miss - try building cache from registry
                    ImporterExporterRegistry.Entry entry = ImporterExporterRegistry.getImporter(targetUUID);
                    if (entry != null) {
                        cacheImporterPosition(targetUUID, entry.pos());
                        importer = findImporterByUUID(cmLevel, targetUUID);
                    }
                }
                if (importer != null) {
                    scanImporterBuffer(importer, totals);
                } else {
                    FPSCompress.LOGGER.warn("Failed to find Importer {} for buffer scan", targetUUID);
                }
            } else if (config.getMode() == FaceMode.PUSH) {
                // PUSH face links to Exporter - scan its buffer
                ExporterBlockEntity exporter = findExporterByUUID(cmLevel, targetUUID);
                if (exporter == null) {
                    // Cache miss - try building cache from registry
                    ImporterExporterRegistry.Entry entry = ImporterExporterRegistry.getExporter(targetUUID);
                    if (entry != null) {
                        cacheExporterPosition(targetUUID, entry.pos());
                        exporter = findExporterByUUID(cmLevel, targetUUID);
                    }
                }
                if (exporter != null) {
                    scanExporterBuffer(exporter, totals);
                } else {
                    FPSCompress.LOGGER.warn("Failed to find Exporter {} for buffer scan", targetUUID);
                }
            }
        }
    }

    /**
     * Scan Importer buffer and add items to totals.
     *
     * @param importer The Importer block entity
     * @param totals The resource totals map (modified in-place)
     */
    private void scanImporterBuffer(ImporterBlockEntity importer, Map<String, Long> totals) {
        ItemStackHandler inventory = importer.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                String resourceId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                totals.merge(resourceId, (long) stack.getCount(), Long::sum);
                FPSCompress.LOGGER.debug("Importer {} buffer slot {}: {} x{}",
                    importer.getImporterUUID(), slot, resourceId, stack.getCount());
            }
        }
    }

    /**
     * Scan Exporter buffer and add items to totals.
     *
     * @param exporter The Exporter block entity
     * @param totals The resource totals map (modified in-place)
     */
    private void scanExporterBuffer(ExporterBlockEntity exporter, Map<String, Long> totals) {
        ItemStackHandler inventory = exporter.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                String resourceId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                totals.merge(resourceId, (long) stack.getCount(), Long::sum);
                FPSCompress.LOGGER.debug("Exporter {} buffer slot {}: {} x{}",
                    exporter.getExporterUUID(), slot, resourceId, stack.getCount());
            }
        }
    }

    // ===== State Transition Methods (Phase 4) =====

    /**
     * Transition BUILDING → SIMULATING.
     * Performs initial inventory scan, then loads CM chunks and starts rate measurement.
     *
     * @param player The player starting the simulation (for debug chat output)
     */
    public void startSimulation(@Nullable Player player) {
        if (currentState != MachineState.BUILDING) {
            FPSCompress.LOGGER.warn("Cannot start simulation from state {}", currentState);
            return;
        }

        ServerLevel cmLevel = getCMLevel();
        if (cmLevel == null || roomCode == null) {
            FPSCompress.LOGGER.error("Cannot start simulation: CM dimension or roomCode not available");
            return;
        }

        // Ensure room center is cached (needed for chunk loading)
        if (roomCenter != null && level != null && !level.isClientSide()) {
            net.minecraft.server.MinecraftServer server = level.getServer();
            if (server != null) {
                RoomCoordinateCache cache = RoomCoordinateCache.get(server);
                cache.setRoomCenter(getBlockPos(), roomCode, roomCenter);
                FPSCompress.LOGGER.debug("Cached room center {} for room {}",
                    roomCenter, roomCode);
            }
        }

        // Phase 1: Load chunks temporarily for initial scan
        CMInterceptorImpl interceptor = CMInterceptorImpl.getInstance();
        interceptor.setRoomChunkState(cmLevel, roomCode, true);
        FPSCompress.LOGGER.info("PreFab at {} starting initial scan (chunks loaded)", worldPosition);

        try {
            AABB roomBounds = getRoomBoundsFromCM(roomCode);

            // Get room center from cache for scanner
            net.minecraft.server.MinecraftServer server = level.getServer();
            RoomCoordinateCache cache = RoomCoordinateCache.get(server);
            BlockPos roomCtr = cache.getRoomCenterByRoomCode(roomCode);

            // Phase 2: Async scan (off main thread, game stays playable)
            InventoryScanner.scanRoomAsync(cmLevel, roomCtr, roomBounds)
                .thenAccept(inventory -> {
                    // Callback to main thread
                    level.getServer().execute(() -> {
                        try {
                            // Phase 3a: Add Importer/Exporter buffer contents to inventory
                            scanImporterExporterBuffers(cmLevel, inventory);
                            FPSCompress.LOGGER.info("Added Importer/Exporter buffer contents to initial scan");

                            // Phase 3b: Store initial state (including buffers)
                            deltaTracker = new ResourceDeltaTracker();
                            deltaTracker.captureInitialState(inventory);
                            FPSCompress.LOGGER.info("Initial scan complete: {} resource types (includes buffers)",
                                inventory.size());

                            // Debug: Send scan results to player chat
                            sendInventoryScanToChat(player, inventory, "Initial");

                            // Phase 4: Unload chunks (return to deterministic state)
                            interceptor.setRoomChunkState(cmLevel, roomCode, false);

                            // Phase 5: Load chunks and start simulation
                            interceptor.setRoomChunkState(cmLevel, roomCode, true);

                            // Phase 6: Clear previous result and enter SIMULATING
                            lastSimulationResult = "";
                            simulationStartTick = level.getGameTime();

                            // Capture config value at simulation start (snapshot behavior)
                            simulationRequiredTicks = Config.SERVER.getMinimumSimulationTicks();
                            simulationElapsedTicks = 0; // Reset counter
                            FPSCompress.LOGGER.debug("Simulation started with minimum time requirement: {} ticks",
                                simulationRequiredTicks);

                            setCurrentState(MachineState.SIMULATING);

                            FPSCompress.LOGGER.info("PreFab at {} entered SIMULATING state", worldPosition);
                        } catch (Exception e) {
                            FPSCompress.LOGGER.error("Failed to complete initial scan", e);
                        }
                    });
                })
                .exceptionally(ex -> {
                    level.getServer().execute(() -> {
                        FPSCompress.LOGGER.error("Initial scan failed for PreFab at {}", worldPosition, ex);
                        interceptor.setRoomChunkState(cmLevel, roomCode, false); // Cleanup
                    });
                    return null;
                });
        } catch (Exception e) {
            FPSCompress.LOGGER.error("Failed to start initial scan", e);
            interceptor.setRoomChunkState(cmLevel, roomCode, false); // Cleanup
        }
    }

    /**
     * Transition SIMULATING → CACHED.
     * Performs final inventory scan, calculates rates using full formula, unloads CM chunks.
     *
     * @param player The player finishing the simulation (for debug chat output)
     */
    public void finishSimulation(@Nullable Player player) {
        if (currentState != MachineState.SIMULATING) {
            FPSCompress.LOGGER.warn("Cannot finish simulation from state {}", currentState);
            return;
        }

        ServerLevel cmLevel = getCMLevel();
        if (cmLevel == null || roomCode == null) {
            FPSCompress.LOGGER.error("Cannot finish simulation: CM dimension or roomCode not available");
            return;
        }

        // Creative mode bypass: Allow instant finish
        if (player != null && isCreativeMode(player)) {
            FPSCompress.LOGGER.debug("Creative mode player finishing simulation (bypassed minimum time)");
            // Continue to normal finish logic
        } else {
            // Survival mode: Enforce minimum time
            if (simulationElapsedTicks < simulationRequiredTicks) {
                long remainingTicks = simulationRequiredTicks - simulationElapsedTicks;
                long remainingSeconds = remainingTicks / 20;
                long totalMinutes = simulationRequiredTicks / 1200;

                String timeMessage = String.format(
                    "Simulation incomplete. Minimum time: %d minutes (%d seconds remaining)",
                    totalMinutes,
                    remainingSeconds
                );

                if (player != null) {
                    player.displayClientMessage(
                        Component.literal("§c" + timeMessage),
                        false
                    );
                }

                FPSCompress.LOGGER.info("Finish simulation blocked: {} ticks remaining", remainingTicks);
                return; // Abort finish
            }
        }

        // Check if any activity occurred
        if (!deltaTracker.hasActivity()) {
            FPSCompress.LOGGER.warn("No activity detected - entering HALTED state");
            lastSimulationResult = "No activity";
            setCurrentState(MachineState.HALTED);
            return;
        }

        // Use activity-based time window (first input to last output)
        simulationStartTick = deltaTracker.getFirstActivityTick();
        simulationEndTick = deltaTracker.getLastActivityTick();

        // Phase 1: Unload chunks (stop factory)
        CMInterceptorImpl interceptor = CMInterceptorImpl.getInstance();
        interceptor.setRoomChunkState(cmLevel, roomCode, false);
        FPSCompress.LOGGER.info("PreFab at {} unloaded chunks for final scan", worldPosition);

        // Phase 2: Load chunks temporarily for final scan
        interceptor.setRoomChunkState(cmLevel, roomCode, true);

        try {
            AABB roomBounds = getRoomBoundsFromCM(roomCode);

            // Get room center from cache for scanner
            net.minecraft.server.MinecraftServer server = level.getServer();
            RoomCoordinateCache cache = RoomCoordinateCache.get(server);
            BlockPos roomCtr = cache.getRoomCenterByRoomCode(roomCode);

            // Phase 3: Async final scan (game stays playable)
            InventoryScanner.scanRoomAsync(cmLevel, roomCtr, roomBounds)
                .thenAccept(finalInventory -> {
                    level.getServer().execute(() -> {
                        try {
                            // Phase 4a: Add Importer/Exporter buffer contents to final inventory
                            scanImporterExporterBuffers(cmLevel, finalInventory);
                            FPSCompress.LOGGER.info("Added Importer/Exporter buffer contents to final scan");

                            // Phase 4b: Store final state (including buffers)
                            deltaTracker.captureFinalState(finalInventory);
                            FPSCompress.LOGGER.info("Final scan complete: {} resource types (includes buffers)",
                                finalInventory.size());

                            // Debug: Send scan results to player chat
                            sendInventoryScanToChat(player, finalInventory, "Final");

                            // Phase 5: Unload chunks (return to unloaded state)
                            interceptor.setRoomChunkState(cmLevel, roomCode, false);

                            // Phase 6: Calculate rates using FULL formula
                            calculateRatesAndTransition();

                        } catch (Exception e) {
                            FPSCompress.LOGGER.error("Failed to process final scan", e);
                            lastSimulationResult = "Scan processing failed";
                            setCurrentState(MachineState.HALTED);
                            setChanged();
                        }
                    });
                })
                .exceptionally(ex -> {
                    level.getServer().execute(() -> {
                        FPSCompress.LOGGER.error("Final scan failed", ex);
                        lastSimulationResult = "Scan failed";
                        setCurrentState(MachineState.HALTED);
                        interceptor.setRoomChunkState(cmLevel, roomCode, false); // Cleanup
                        setChanged();
                    });
                    return null;
                });
        } catch (Exception e) {
            FPSCompress.LOGGER.error("Failed to start final scan", e);
            lastSimulationResult = "Failed to start final scan";
            setCurrentState(MachineState.HALTED);
            interceptor.setRoomChunkState(cmLevel, roomCode, false);
            setChanged();
        }
    }

    /**
     * Calculate rates using full formula and transition to CACHED/BUILDING/HALTED.
     * Separated from finishSimulation() for clarity.
     */
    private void calculateRatesAndTransition() {
        long totalTicks = simulationEndTick - simulationStartTick;

        if (totalTicks == 0) {
            // All activity happened in same tick - use 1 tick to avoid division by zero
            totalTicks = 1;
            FPSCompress.LOGGER.warn("All activity in single tick - using 1 tick for rate calculation");
        }

        // Phase 6: Calculate rates per UUID using FULL formula
        // Strategy: Calculate aggregate net production with storage delta, then distribute to UUIDs
        clearImporterExporterRates();

        // Debug: Check what's in deltaTracker
        java.util.Set<UUID> trackedUUIDs = deltaTracker.getTrackedUUIDs();
        FPSCompress.LOGGER.info("▶ finishSimulation: deltaTracker has {} tracked UUIDs",
            trackedUUIDs.size());

        // Step 1: Calculate aggregate net production for each resource using FULL formula
        Map<String, Long> aggregateNetProduction = new HashMap<>();
        for (String resourceId : deltaTracker.getAllTrackedResources()) {
            long initialState = deltaTracker.getInitialState(resourceId);
            long finalState = deltaTracker.getFinalState(resourceId);
            long totalImported = deltaTracker.getTotalImported(resourceId);
            long totalExported = deltaTracker.getTotalExported(resourceId);

            // FULL FORMULA: Net = (Final - Initial) + (Exported - Imported)
            long storageDelta = finalState - initialState;
            long flowDelta = totalExported - totalImported;
            long netProduction = storageDelta + flowDelta;

            FPSCompress.LOGGER.info("  Aggregate {}: Init={}, Final={}, Imported={}, Exported={}",
                resourceId, initialState, finalState, totalImported, totalExported);
            FPSCompress.LOGGER.info("    Storage delta={}, Flow delta={}, FULL NET={}",
                storageDelta, flowDelta, netProduction);

            if (Math.abs(netProduction) >= 1) {
                aggregateNetProduction.put(resourceId, netProduction);
            }
        }

        // Step 2: Distribute aggregate net production to UUIDs based on their flow contribution
        for (UUID uuid : trackedUUIDs) {
            for (String resourceId : deltaTracker.getTrackedResourcesForUUID(uuid)) {
                long uuidImported = deltaTracker.getTotalImportedForUUID(uuid, resourceId);
                long uuidExported = deltaTracker.getTotalExportedForUUID(uuid, resourceId);
                long uuidFlowDelta = uuidExported - uuidImported;

                // Get aggregate values
                long aggregateImported = deltaTracker.getTotalImported(resourceId);
                long aggregateExported = deltaTracker.getTotalExported(resourceId);
                long aggregateFlowDelta = aggregateExported - aggregateImported;

                // Calculate this UUID's share of the aggregate net production
                Long aggregateNet = aggregateNetProduction.get(resourceId);
                if (aggregateNet == null) {
                    continue; // Resource filtered out (negligible production)
                }

                // Proportional distribution based on flow contribution
                long uuidNetProduction;
                if (aggregateFlowDelta == 0) {
                    // No flow delta - all UUIDs get equal share of storage delta
                    int uuidCount = (int) trackedUUIDs.stream()
                        .filter(u -> deltaTracker.getTrackedResourcesForUUID(u).contains(resourceId))
                        .count();
                    uuidNetProduction = aggregateNet / Math.max(1, uuidCount);
                } else {
                    // Proportional to flow contribution
                    double proportion = (double) uuidFlowDelta / aggregateFlowDelta;
                    uuidNetProduction = Math.round(aggregateNet * proportion);
                }

                FPSCompress.LOGGER.info("  UUID {}: {}: Flow={}, Proportion share of net={}",
                    uuid.toString().substring(0, 8), resourceId, uuidFlowDelta, uuidNetProduction);

                // Skip if negligible after distribution
                if (Math.abs(uuidNetProduction) < 1) {
                    FPSCompress.LOGGER.info("    Negligible after distribution, excluding from rates");
                    continue;
                }

                // Calculate rate per tick
                double ratePerTick = (double) uuidNetProduction / totalTicks;

                // Only store non-zero rates (threshold: 0.0001 items/tick)
                if (Math.abs(ratePerTick) >= 0.0001) {
                    setRateForUUID(uuid, resourceId, ratePerTick);
                    FPSCompress.LOGGER.info(
                        "Calculated per-UUID rate for {} ({}): {} items/tick (net: {} over {} ticks)",
                        uuid.toString().substring(0, 8), resourceId, ratePerTick,
                        uuidNetProduction, totalTicks);
                }
            }
        }

        // Keep aggregate cachedRates for backward compatibility (GUI display)
        // But derive from per-UUID rates
        clearCachedRates();
        for (Map<String, Double> uuidRates : importerExporterRates.values()) {
            for (Map.Entry<String, Double> entry : uuidRates.entrySet()) {
                cachedRates.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        FPSCompress.LOGGER.info("▶ Aggregate rates (for GUI): {} resources", cachedRates.size());

        // Auto-normalize rates for better display
        if (!cachedRates.isEmpty()) {
            com.mukulramesh.fpscompress.gui.RateNormalizer.NormalizationResult autoResult =
                com.mukulramesh.fpscompress.gui.RateNormalizer.autoNormalize(cachedRates);
            this.autoNormalizedTicks = autoResult.normalizedTicks();
            this.currentDisplayMode = autoResult.suggestedMode();
            this.autoNormalizedDisplayMode = autoResult.suggestedMode(); // Store original mode
            this.focusedResourceId = null; // Clear focus on new simulation
            this.useAutoNormalize = true; // Enable auto-normalize by default
            FPSCompress.LOGGER.info("Auto-normalized: {} ticks, mode {}",
                autoNormalizedTicks, currentDisplayMode.name());
        }

        // Detect passthrough (activity occurred but net production is zero)
        if (cachedRates.isEmpty()) {
            // Passthrough detected - items moved but net production is zero
            FPSCompress.LOGGER.info("Passthrough detected (net production = 0) - resetting to BUILDING");
            lastSimulationResult = "Passthrough (no net production)";
            setCurrentState(MachineState.BUILDING);
            return;
        }

        // Phase 6: Validate that all UUIDs with rates have faces mapped
        CachedConfigurationValidator.ValidationResult validation = validateCachedConfiguration();
        if (!validation.success()) {
            // Configuration error - reset to BUILDING (requires face reconfiguration)
            FPSCompress.LOGGER.error("PreFab validation failed, resetting to BUILDING:");
            for (String error : validation.errors()) {
                FPSCompress.LOGGER.error("  {}", error);
            }
            lastSimulationResult = "Configuration error: " + String.join("; ", validation.errors());
            setCurrentState(MachineState.BUILDING);
            setChanged();
            return;  // Don't transition to CACHED
        }

        // Log warnings (non-fatal)
        for (String warning : validation.warnings()) {
            FPSCompress.LOGGER.warn("PreFab validation warning: {}", warning);
        }

        // Record when CACHED state starts and reset production counters
        cachedStateStartTick = level.getGameTime();
        cachedProduction.clear();

        // Clear result message on success (only keep failure messages visible)
        lastSimulationResult = "";

        // Transition state
        setCurrentState(MachineState.CACHED);

        FPSCompress.LOGGER.info("PreFab at {} finished simulation (tick {}), cached {} rates",
            getBlockPos(), simulationEndTick, cachedRates.size());
    }

    /**
     * Transition CACHED/HALTED → BUILDING.
     * Clears rates, keeps CM chunks UNLOADED (player must manually enter to reconfigure).
     */
    public void resetToBuilding() {
        if (currentState != MachineState.CACHED && currentState != MachineState.HALTED) {
            FPSCompress.LOGGER.warn("Cannot reset from state {}", currentState);
            return;
        }

        // Clear cached rates and accumulators
        clearCachedRates();
        clearImporterExporterRates(); // Phase 6: Clear per-UUID rates
        deltaTracker = new ResourceDeltaTracker();
        itemAccumulators.clear(); // Phase 5: Clear fractional accumulators
        cachedProduction.clear();

        // Clear display preferences
        currentDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK;
        focusedResourceId = null;
        autoNormalizedTicks = 1;
        useAutoNormalize = true;
        autoNormalizedDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK;

        // Transition state
        setCurrentState(MachineState.BUILDING);

        FPSCompress.LOGGER.info("PreFab at {} reset to BUILDING mode (chunks remain unloaded)", getBlockPos());
    }

    /**
     * Transition HALTED → SIMULATING.
     * Resumes measurement after player fixes inputs/outputs.
     */
    public void resumeSimulation() {
        if (currentState != MachineState.HALTED) {
            FPSCompress.LOGGER.warn("Cannot resume from state {}", currentState);
            return;
        }

        // Treat as fresh simulation start
        setCurrentState(MachineState.BUILDING);
        startSimulation(null); // No player context (unused method)

        FPSCompress.LOGGER.info("PreFab at {} resumed simulation", getBlockPos());
    }

    // ===== Phase 5: Cached Production (Fractional Math) =====

    /**
     * Tick cached production using fractional math.
     * Called every tick when in CACHED or HALTED state.
     * CM chunks stay UNLOADED - this is the whole point of caching!
     *
     * Performance optimization: In HALTED state, uses exponential backoff
     * to reduce inventory checking frequency (1 → 2 → 4 → 8... up to 100 ticks).
     */
    private void tickCachedProduction() {
        // Performance optimization: Exponential backoff in HALTED state
        if (currentState == MachineState.HALTED) {
            ticksSinceLastRetry++;
            if (ticksSinceLastRetry < haltedRetryInterval) {
                return; // Skip this tick - waiting for retry interval
            }
            // Retry interval reached - reset counter and attempt recovery
            ticksSinceLastRetry = 0;
            FPSCompress.LOGGER.debug("HALTED retry attempt (interval: {} ticks)", haltedRetryInterval);
        }

        boolean hadFailure = false;
        String failureMessage = "";

        // Phase 6: Process per-UUID rates instead of aggregate
        for (Map.Entry<UUID, Map<String, Double>> uuidEntry : importerExporterRates.entrySet()) {
            UUID equipmentUUID = uuidEntry.getKey();

            for (Map.Entry<String, Double> rateEntry : uuidEntry.getValue().entrySet()) {
                String resourceId = rateEntry.getKey();
                double ratePerTick = rateEntry.getValue();

                // Build accumulator key: "UUID:resourceId" to track fractional state per UUID
                String accumKey = equipmentUUID.toString() + ":" + resourceId;

                // Initialize accumulator if needed
                itemAccumulators.putIfAbsent(accumKey, 0.0);

                // Accumulate fractional production (ONLY if in CACHED state, not HALTED)
                double currentAccum = itemAccumulators.get(accumKey);
                if (currentState == MachineState.CACHED) {
                    currentAccum += ratePerTick;
                }
                // In HALTED state: Don't accumulate, just try to transfer what's already there

                // Check if we have at least 1 whole item to transfer
                if (Math.abs(currentAccum) >= 1.0) {
                    int wholeItems = (int) currentAccum; // Truncate to integer (positive or negative)
                    currentAccum -= wholeItems; // Remove transferred amount

                    // Attempt to transfer whole items
                    boolean success;
                    if (wholeItems > 0) {
                        // Positive rate = Output (factory produces this resource)
                        success = transferCachedOutput(equipmentUUID, resourceId, wholeItems);
                    } else {
                        // Negative rate = Input (factory consumes this resource)
                        success = transferCachedInput(equipmentUUID, resourceId, -wholeItems); // Make positive
                    }

                    if (!success) {
                        // Transfer failed - put items back into accumulator
                        currentAccum += wholeItems;
                        itemAccumulators.put(accumKey, currentAccum);

                        // Record failure details for this tick
                        hadFailure = true;
                        String itemName = getLocalizedItemName(resourceId);
                        String uuidShort = equipmentUUID.toString().substring(0, 8);
                        if (wholeItems > 0) {
                            failureMessage = String.format("Output blocked: %s (%d needed, UUID: %s)",
                                itemName, wholeItems, uuidShort);
                        } else {
                            failureMessage = String.format("Input starved: %s (%d needed, UUID: %s)",
                                itemName, -wholeItems, uuidShort);
                        }

                        FPSCompress.LOGGER.debug("Cache transfer failed: {}", failureMessage);
                        // Continue trying other resources instead of returning
                        continue;
                    }
                    // Success - continue with updated currentAccum (will be stored below)
                }

                // Store updated accumulator (whether we transferred or not)
                itemAccumulators.put(accumKey, currentAccum);
            }
        }  // End of per-UUID rates loop

        // Update state based on whether we had any failures this tick
        if (hadFailure) {
            // At least one transfer failed - enter/stay in HALTED
            if (currentState != MachineState.HALTED) {
                // First failure - enter HALTED with 1-tick retry interval
                FPSCompress.LOGGER.warn("Cache broke at {} - entering HALTED", getBlockPos());
                haltedRetryInterval = 1;
                ticksSinceLastRetry = 0;
                setCurrentState(MachineState.HALTED);
            } else {
                // Still failing - exponential backoff (double interval, cap at 100 ticks = 5 seconds)
                haltedRetryInterval = Math.min(haltedRetryInterval * 2, 100);
            }
            lastSimulationResult = failureMessage;
        } else {
            // All transfers succeeded - recover from HALTED if needed
            if (currentState == MachineState.HALTED) {
                FPSCompress.LOGGER.info("Cache recovered at {} (after {} ticks backoff) - returning to CACHED",
                    getBlockPos(), haltedRetryInterval);
                haltedRetryInterval = 1; // Reset for next failure
                ticksSinceLastRetry = 0;
                setCurrentState(MachineState.CACHED);
                lastSimulationResult = ""; // Clear error message
            }
        }
    }

    /**
     * Phase 6: Transfer cached output from a specific Exporter UUID to Overworld via mapped faces.
     *
     * @param exporterUUID The UUID of the Exporter that produced this resource
     * @param resourceId The resource identifier (e.g., "minecraft:iron_ingot")
     * @param amount The number of items to transfer
     * @return true if fully transferred, false if blocked
     */
    private boolean transferCachedOutput(UUID exporterUUID, String resourceId, int amount) {
        return CachedTransferHandler.transferCachedOutput(
            exporterUUID, resourceId, amount, level, getBlockPos(), faceConfigs, cachedProduction);
    }

    /**
     * Phase 6: Transfer cached input from Overworld to a specific Importer UUID via mapped faces.
     *
     * @param importerUUID The UUID of the Importer that needs this resource
     * @param resourceId The resource identifier (e.g., "minecraft:coal")
     * @param amount The number of items to transfer
     * @return true if all items obtained, false if starved
     */
    private boolean transferCachedInput(UUID importerUUID, String resourceId, int amount) {
        return CachedTransferHandler.transferCachedInput(
            importerUUID, resourceId, amount, level, getBlockPos(), faceConfigs);
    }
}
