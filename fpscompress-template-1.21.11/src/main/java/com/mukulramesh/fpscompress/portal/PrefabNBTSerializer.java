package com.mukulramesh.fpscompress.portal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Handles NBT serialization and deserialization for PreFab blocks.
 * Manages schema migration, validation, and fake registry loading.
 */
public class PrefabNBTSerializer {
    private final PrefabBlockEntity entity;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
        justification = "Service class intentionally holds reference to entity for delegation pattern")
    public PrefabNBTSerializer(PrefabBlockEntity entity) {
        this.entity = entity;
    }

    /**
     * Save PreFab state to NBT.
     */
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // Schema version for future migrations (PreFab-as-Item feature)
        // Phase 6: Version 2 = Per-UUID rates
        tag.putInt("schemaVersion", 2);

        // Save room linkage (always persist)
        if (entity.roomCode != null) {
            tag.putString("roomCode", entity.roomCode);
        }
        if (entity.roomCenter != null) {
            tag.putLong("roomCenter", entity.roomCenter.asLong());
        }

        // Save room dimensions (always persist)
        if (entity.roomSizeX != null) {
            tag.putInt("roomSizeX", entity.roomSizeX);
        }
        if (entity.roomSizeY != null) {
            tag.putInt("roomSizeY", entity.roomSizeY);
        }
        if (entity.roomSizeZ != null) {
            tag.putInt("roomSizeZ", entity.roomSizeZ);
        }

        // Save machine state (always persist - migration handled on load)
        tag.putString("state", entity.currentState.name());

        // Save face configurations (always persist)
        CompoundTag facesTag = new CompoundTag();
        for (Map.Entry<Direction, FaceConfig> entry : entity.faceConfigs.entrySet()) {
            facesTag.put(entry.getKey().getName(), entry.getValue().toNBT());
        }
        tag.put("faceConfigs", facesTag);

        // Phase 6: Save per-UUID rates (only if CACHED or HALTED state - portable data)
        if ((entity.currentState == MachineState.CACHED || entity.currentState == MachineState.HALTED)
                && !entity.importerExporterRates.isEmpty()) {
            ListTag uuidRatesList = new ListTag();

            for (Map.Entry<UUID, Map<String, Double>> uuidEntry : entity.importerExporterRates.entrySet()) {
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
        if ((entity.currentState == MachineState.CACHED || entity.currentState == MachineState.HALTED)
                && !entity.cachedRates.isEmpty()) {
            ListTag ratesList = new ListTag();
            for (Map.Entry<String, Double> entry : entity.cachedRates.entrySet()) {
                CompoundTag rateEntry = new CompoundTag();
                rateEntry.putString("id", entry.getKey());
                rateEntry.putDouble("rate", entry.getValue());
                ratesList.add(rateEntry);
            }
            tag.put("rates", ratesList);
        }

        // Save fractional accumulators (only if CACHED or HALTED state - portable data)
        if ((entity.currentState == MachineState.CACHED || entity.currentState == MachineState.HALTED)
                && !entity.itemAccumulators.isEmpty()) {
            ListTag accumList = new ListTag();
            for (Map.Entry<String, Double> entry : entity.itemAccumulators.entrySet()) {
                CompoundTag accumEntry = new CompoundTag();
                accumEntry.putString("id", entry.getKey());
                accumEntry.putDouble("accum", entry.getValue());
                accumList.add(accumEntry);
            }
            tag.put("itemAccumulators", accumList);
        }

        // Save accumulated production (only if CACHED or HALTED state - portable data)
        if ((entity.currentState == MachineState.CACHED || entity.currentState == MachineState.HALTED)
                && !entity.cachedProduction.isEmpty()) {
            ListTag prodList = new ListTag();
            for (Map.Entry<String, Long> entry : entity.cachedProduction.entrySet()) {
                CompoundTag prodEntry = new CompoundTag();
                prodEntry.putString("id", entry.getKey());
                prodEntry.putLong("amount", entry.getValue());
                prodList.add(prodEntry);
            }
            tag.put("cachedProduction", prodList);
        }

        // Save simulation end tick (only if CACHED or HALTED state - for GUI display)
        if (entity.currentState == MachineState.CACHED || entity.currentState == MachineState.HALTED) {
            tag.putLong("simulationEndTick", entity.simulationEndTick);
        }

        // Save simulation timer fields (only if SIMULATING state - for minimum time enforcement)
        if (entity.currentState == MachineState.SIMULATING) {
            tag.putLong("simulationElapsedTicks", entity.simulationElapsedTicks);
            tag.putLong("simulationRequiredTicks", entity.simulationRequiredTicks);
        }

        // Save display preferences (always persist)
        tag.putString("displayMode", entity.currentDisplayMode.name());
        if (entity.focusedResourceId != null) {
            tag.putString("focusedResourceId", entity.focusedResourceId);
        }
        tag.putInt("autoNormalizedTicks", entity.autoNormalizedTicks);
        tag.putBoolean("useAutoNormalize", entity.useAutoNormalize);
        tag.putString("autoNormalizedDisplayMode", entity.autoNormalizedDisplayMode.name());

        // TRANSIENT FIELDS (not saved for PreFab-as-Item portability):
        // - deltaTracker (only valid during active simulation)
        // - simulationStartTick (recalculate on simulation start)
        // - cachedStateStartTick (recalculate from current game time)
        // - haltedRetryInterval/ticksSinceLastRetry (optimization state)
        // - importerCache/exporterCache (rebuild from registry)
        // - lastSimulationResult (UI-only message)
    }

    /**
     * Load PreFab state from NBT.
     */
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // Apply migration rules for PreFab-as-Item (must happen first)
        applyItemNBTMigration(tag);

        // Load room linkage
        if (tag.contains("roomCode")) {
            entity.roomCode = tag.getString("roomCode");
        }
        if (tag.contains("roomCenter")) {
            entity.roomCenter = BlockPos.of(tag.getLong("roomCenter"));
        }

        // Load room dimensions
        if (tag.contains("roomSizeX")) {
            entity.roomSizeX = tag.getInt("roomSizeX");
        }
        if (tag.contains("roomSizeY")) {
            entity.roomSizeY = tag.getInt("roomSizeY");
        }
        if (tag.contains("roomSizeZ")) {
            entity.roomSizeZ = tag.getInt("roomSizeZ");
        }

        // Machine state already loaded by applyItemNBTMigration() with migration rules applied

        // Load face configurations
        if (tag.contains("faceConfigs")) {
            CompoundTag facesTag = tag.getCompound("faceConfigs");
            for (Direction dir : Direction.values()) {
                if (facesTag.contains(dir.getName())) {
                    entity.faceConfigs.put(dir, FaceConfig.fromNBT(facesTag.getCompound(dir.getName())));
                }
            }
        }

        // Phase 6: Load per-UUID rates (schema version 2+)
        int schemaVersion = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 1;
        loadRatesFromNBT(tag, schemaVersion);

        // Load fractional accumulators (only if CACHED state)
        if (tag.contains("itemAccumulators")) {
            entity.itemAccumulators.clear();
            ListTag accumList = tag.getList("itemAccumulators", Tag.TAG_COMPOUND);
            for (int i = 0; i < accumList.size(); i++) {
                CompoundTag accumEntry = accumList.getCompound(i);
                String id = accumEntry.getString("id");
                double accum = accumEntry.getDouble("accum");
                entity.itemAccumulators.put(id, accum);
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
            entity.cachedProduction.clear();
            ListTag prodList = tag.getList("cachedProduction", Tag.TAG_COMPOUND);
            for (int i = 0; i < prodList.size(); i++) {
                CompoundTag prodEntry = prodList.getCompound(i);
                String id = prodEntry.getString("id");
                long amount = prodEntry.getLong("amount");
                entity.cachedProduction.put(id, amount);
            }
        }

        // Load simulation end tick (only if CACHED state - for GUI display)
        if (tag.contains("simulationEndTick")) {
            entity.simulationEndTick = tag.getLong("simulationEndTick");
        }

        // Load simulation timer fields (only if SIMULATING state - for minimum time enforcement)
        if (tag.contains("simulationElapsedTicks")) {
            entity.simulationElapsedTicks = tag.getLong("simulationElapsedTicks");
        }
        if (tag.contains("simulationRequiredTicks")) {
            entity.simulationRequiredTicks = tag.getLong("simulationRequiredTicks");
        }

        // Load display preferences with validation
        if (tag.contains("displayMode")) {
            try {
                entity.currentDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.valueOf(
                    tag.getString("displayMode"));
            } catch (IllegalArgumentException e) {
                FPSCompress.LOGGER.warn("Invalid displayMode '{}', resetting to PER_TICK",
                    tag.getString("displayMode"));
                entity.currentDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK;
            }
        }

        if (tag.contains("focusedResourceId")) {
            String focusedId = tag.getString("focusedResourceId");
            // Validation: Ensure focused item actually exists in cached rates
            // (will be validated later in validateLoadedData() after rates are loaded)
            entity.focusedResourceId = focusedId;
        }

        if (tag.contains("autoNormalizedTicks")) {
            int ticks = tag.getInt("autoNormalizedTicks");
            entity.autoNormalizedTicks = Math.max(1, ticks); // Must be >= 1
        }

        if (tag.contains("useAutoNormalize")) {
            entity.useAutoNormalize = tag.getBoolean("useAutoNormalize");
        }

        if (tag.contains("autoNormalizedDisplayMode")) {
            try {
                entity.autoNormalizedDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.valueOf(
                    tag.getString("autoNormalizedDisplayMode"));
            } catch (IllegalArgumentException e) {
                FPSCompress.LOGGER.warn("Invalid autoNormalizedDisplayMode '{}', resetting to PER_TICK",
                    tag.getString("autoNormalizedDisplayMode"));
                entity.autoNormalizedDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK;
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
     * Apply NBT migration rules when loading from item form.
     */
    void applyItemNBTMigration(CompoundTag tag) {
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
                entity.currentState = MachineState.BUILDING;
                entity.deltaTracker = new ResourceDeltaTracker(); // Clear partial measurement
                FPSCompress.LOGGER.info("PreFab item had SIMULATING state - reset to BUILDING "
                    + "(partial measurement invalid after chunk unload)");
            } else if ("HALTED".equals(stateStr)) {
                // Migration Rule 2: HALTED → CACHED
                // Rationale: HALTED is a temporary pause during CACHED operation. When PreFab
                // moves to new location, old HALTED condition no longer applies (different
                // adjacent blocks). Restore to CACHED state and let it try running again.
                // Cached rates are preserved - they're still valid!
                entity.currentState = MachineState.CACHED;
                entity.haltedRetryInterval = 1; // Reset backoff optimization
                entity.ticksSinceLastRetry = 0;
                FPSCompress.LOGGER.info("PreFab item had HALTED state - restored to CACHED "
                    + "(cached rates preserved, new location may work)");
            } else {
                // Preserve other states (BUILDING, CACHED)
                try {
                    entity.currentState = MachineState.valueOf(stateStr);
                } catch (IllegalArgumentException e) {
                    FPSCompress.LOGGER.warn("Unknown machine state '{}' - defaulting to BUILDING", stateStr);
                    entity.currentState = MachineState.BUILDING;
                }
            }
        }

        // Recalculate cachedStateStartTick from current time (if CACHED or migrated from HALTED)
        // Rationale: cachedStateStartTick is used for GUI display ("Running for X seconds").
        // When PreFab moves, reset the timer to current game time.
        if (entity.currentState == MachineState.CACHED && entity.getLevel() != null) {
            entity.cachedStateStartTick = entity.getLevel().getGameTime();
            FPSCompress.LOGGER.debug("PreFab item restored CACHED state - reset start tick to {}",
                entity.cachedStateStartTick);
        }
    }

    /**
     * Validate loaded NBT data for corruption or inconsistencies.
     * Catches edge cases where NBT schema is valid but data is logically inconsistent.
     */
    void validateLoadedData() {
        // Edge Case 1: CACHED/HALTED state but no rates
        // This should never happen (both states require rates), but handle gracefully.
        // Check both cachedRates (old schema) and importerExporterRates (new schema v2+)
        if ((entity.currentState == MachineState.CACHED || entity.currentState == MachineState.HALTED)
                && entity.cachedRates.isEmpty() && entity.importerExporterRates.isEmpty()) {
            FPSCompress.LOGGER.warn("PreFab loaded with {} state but no rates - resetting to BUILDING",
                entity.currentState);
            entity.currentState = MachineState.BUILDING;
            entity.itemAccumulators.clear();
            entity.cachedProduction.clear();
        }

        // Edge Case 2: Not CACHED/HALTED but has rates
        // Rates should only exist in CACHED/HALTED states. Clear invalid data.
        if (entity.currentState != MachineState.CACHED && entity.currentState != MachineState.HALTED
                && (!entity.cachedRates.isEmpty() || !entity.importerExporterRates.isEmpty())) {
            FPSCompress.LOGGER.warn("PreFab loaded with rates but not CACHED/HALTED - clearing rates "
                + "(state: {})", entity.currentState);
            entity.cachedRates.clear();
            entity.importerExporterRates.clear();
            entity.itemAccumulators.clear();
            entity.cachedProduction.clear();
        }

        // Edge Case 3: Room linkage incomplete
        // roomCode without roomCenter is invalid for REAL rooms (can't locate room).
        // EXCEPTION: Fake rooms (prefix "fake_") don't need roomCenter - they're for testing.
        if (entity.roomCode != null && entity.roomCenter == null && !entity.roomCode.startsWith("fake_")) {
            FPSCompress.LOGGER.warn("PreFab has roomCode '{}' but no roomCenter - clearing roomCode",
                entity.roomCode);
            entity.roomCode = null;
        }

        // Edge Case 4: Face configs with invalid UUID links (only validate for BUILDING/SIMULATING)
        // CACHED and HALTED states don't need UUID links (use cached rates directly, CM chunks unloaded).
        // BUILDING/SIMULATING states need UUIDs to function.
        if (entity.currentState != MachineState.CACHED && entity.currentState != MachineState.HALTED) {
            for (Map.Entry<Direction, FaceConfig> entry : entity.faceConfigs.entrySet()) {
                FaceConfig config = entry.getValue();
                if (config.getMode() != FaceMode.DISABLED && config.getTargetUUID() == null) {
                    FPSCompress.LOGGER.warn("Face {} has mode {} but no UUID (state: {}) - resetting to DISABLED",
                        entry.getKey(), config.getMode(), entity.currentState);
                    config.setMode(FaceMode.DISABLED);
                }
            }
        }

        // Edge Case 5: Focused resource ID validation
        // Validate focused resource ID exists in cached rates
        if (entity.focusedResourceId != null && !entity.cachedRates.containsKey(entity.focusedResourceId)) {
            FPSCompress.LOGGER.warn("Focused resource '{}' not in cached rates, clearing focus",
                entity.focusedResourceId);
            entity.focusedResourceId = null;
        }
    }

    /**
     * Load cached rates from NBT (both per-UUID and aggregate formats).
     */
    private void loadRatesFromNBT(CompoundTag tag, int schemaVersion) {
        FPSCompress.LOGGER.debug("Loading PreFab NBT - schemaVersion: {}, has importerExporterRates: {}, has rates: {}",
            schemaVersion, tag.contains("importerExporterRates"), tag.contains("rates"));

        if (tag.contains("importerExporterRates")) {
            entity.importerExporterRates.clear();
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

                entity.importerExporterRates.put(uuid, resourceRates);
                FPSCompress.LOGGER.debug("Loaded rates for UUID {}: {}",
                    uuid.toString().substring(0, 8), resourceRates);
            }
        } else if (schemaVersion == 1 && tag.contains("rates")) {
            // Migration: Convert old aggregate rates to per-UUID (schema version 1)
            FPSCompress.LOGGER.info("Migrating PreFab from schema v1 to v2 (per-UUID rates)");
            entity.importerExporterRates.clear();

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
            entity.cachedRates.clear();
            ListTag ratesList = tag.getList("rates", Tag.TAG_COMPOUND);
            for (int i = 0; i < ratesList.size(); i++) {
                CompoundTag rateEntry = ratesList.getCompound(i);
                String id = rateEntry.getString("id");
                double rate = rateEntry.getDouble("rate");
                entity.cachedRates.put(id, rate);
            }
        } else if (!entity.importerExporterRates.isEmpty()) {
            // Derive cachedRates from importerExporterRates if no aggregate rates saved
            // (happens for test PreFabs that only have UUID-based rates)
            entity.cachedRates.clear();
            for (Map<String, Double> uuidRates : entity.importerExporterRates.values()) {
                for (Map.Entry<String, Double> entry : uuidRates.entrySet()) {
                    entity.cachedRates.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }
            FPSCompress.LOGGER.debug("Derived {} aggregate rates from per-UUID rates for GUI display",
                entity.cachedRates.size());
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
                ImporterExporterRegistry.registerImporter(uuid, entity.getBlockPos(), displayName, roomCodeValue);

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
                ImporterExporterRegistry.registerExporter(uuid, entity.getBlockPos(), displayName, roomCodeValue);

                FPSCompress.LOGGER.debug("Registered fake Exporter {} with display name: {}",
                    uuid.toString().substring(0, 8), displayName);

            } catch (Exception e) {
                FPSCompress.LOGGER.error("Failed to load fake Exporter from registry: {}", e.getMessage());
            }
        }
    }
}
