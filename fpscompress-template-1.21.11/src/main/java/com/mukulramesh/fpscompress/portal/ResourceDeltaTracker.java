package com.mukulramesh.fpscompress.portal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks import/export deltas for rate measurement during SIMULATING state.
 *
 * <p>MVP Implementation: Only tracks totalImported and totalExported.
 * Initial/Final state scanning is POST-MVP (v1.0+ anti-cheat validation).
 *
 * <p>Formula: Net Production = Exported - Imported
 * - Positive = Factory produced this resource (output)
 * - Negative = Factory consumed this resource (input)
 * - Zero = Passthrough (no production/consumption)
 *
 * @see <a href="../../../../../VALIDATION_DELTA_ACCOUNTING.md">VALIDATION_DELTA_ACCOUNTING.md</a>
 */
public class ResourceDeltaTracker {

    /**
     * Tracks deltas for a single resource type.
     * Includes totalImported, totalExported, initialState, and finalState
     * for full delta accounting formula.
     */
    private static final class ResourceDeltas {
        private long totalImported = 0;
        private long totalExported = 0;
        private long initialState = 0;
        private long finalState = 0;

        void addImported(long amount) {
            totalImported += amount;
        }

        void addExported(long amount) {
            totalExported += amount;
        }

        long getTotalImported() {
            return totalImported;
        }

        long getTotalExported() {
            return totalExported;
        }

        long getInitialState() {
            return initialState;
        }

        long getFinalState() {
            return finalState;
        }

        void setInitialState(long amount) {
            initialState = amount;
        }

        void setFinalState(long amount) {
            finalState = amount;
        }

        CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("imported", totalImported);
            tag.putLong("exported", totalExported);
            tag.putLong("initial", initialState);
            tag.putLong("final", finalState);
            return tag;
        }

        static ResourceDeltas fromNBT(CompoundTag tag) {
            ResourceDeltas deltas = new ResourceDeltas();
            deltas.totalImported = tag.getLong("imported");
            deltas.totalExported = tag.getLong("exported");
            deltas.initialState = tag.getLong("initial");
            deltas.finalState = tag.getLong("final");
            return deltas;
        }
    }

    // Map: Resource ID (e.g., "minecraft:iron_ingot") -> Deltas
    private final Map<String, ResourceDeltas> deltas = new HashMap<>();

    // Phase 6: Per-UUID delta tracking for multi-output routing
    // Maps equipment UUID -> (resource ID -> deltas)
    // Example: {ExporterUUID-A: {iron: ResourceDeltas}, ExporterUUID-B: {copper: ResourceDeltas}}
    private final Map<UUID, Map<String, ResourceDeltas>> deltasByUUID = new HashMap<>();

    // Track first and last activity ticks (for accurate simulation time measurement)
    private long firstActivityTick = -1; // -1 = no activity yet
    private long lastActivityTick = -1;

    /**
     * Record items imported from Overworld into CM dimension.
     * Called after successful PULL transport.
     *
     * @param equipmentUUID The UUID of the Importer handling this resource
     * @param resourceId The resource ID (e.g., "minecraft:coal")
     * @param amount Number of items imported
     * @param currentTick Current game time (for tracking first/last activity)
     */
    public void recordImport(UUID equipmentUUID, String resourceId, long amount, long currentTick) {
        // Keep aggregate tracking (for initial/final state validation)
        deltas.computeIfAbsent(resourceId, k -> new ResourceDeltas())
              .addImported(amount);

        // NEW: Track per UUID
        deltasByUUID.computeIfAbsent(equipmentUUID, k -> new HashMap<>())
                    .computeIfAbsent(resourceId, k -> new ResourceDeltas())
                    .addImported(amount);

        updateActivityTicks(currentTick);
    }

    /**
     * Record items exported from CM dimension to Overworld.
     * Called after successful PUSH transport.
     *
     * @param equipmentUUID The UUID of the Exporter handling this resource
     * @param resourceId The resource ID (e.g., "minecraft:iron_ingot")
     * @param amount Number of items exported
     * @param currentTick Current game time (for tracking first/last activity)
     */
    public void recordExport(UUID equipmentUUID, String resourceId, long amount, long currentTick) {
        // Keep aggregate tracking (for initial/final state validation)
        deltas.computeIfAbsent(resourceId, k -> new ResourceDeltas())
              .addExported(amount);

        // NEW: Track per UUID
        deltasByUUID.computeIfAbsent(equipmentUUID, k -> new HashMap<>())
                    .computeIfAbsent(resourceId, k -> new ResourceDeltas())
                    .addExported(amount);

        updateActivityTicks(currentTick);
    }

    /**
     * Calculate net production for a resource.
     * MVP Formula: Net = Exported - Imported
     *
     * @param resourceId The resource ID
     * @return Net production (positive = produced, negative = consumed, zero = passthrough)
     * @deprecated Use {@link #calculateNetFull(String)} for post-MVP validation with initial/final state
     */
    @Deprecated
    public long calculateNet(String resourceId) {
        ResourceDeltas d = deltas.get(resourceId);
        if (d == null) {
            return 0;
        }
        return d.getTotalExported() - d.getTotalImported();
    }

    /**
     * Calculate net production using full delta accounting formula.
     * Formula: Net = (Final - Initial) + (Exported - Imported)
     *
     * This accounts for both:
     * - Resource flow through PreFab faces (Exported - Imported)
     * - Internal storage changes in CM dimension (Final - Initial)
     *
     * @param resourceId The resource ID
     * @return Net production (positive = produced, negative = consumed, zero = passthrough)
     */
    public long calculateNetFull(String resourceId) {
        ResourceDeltas d = deltas.get(resourceId);
        if (d == null) {
            return 0;
        }
        return (d.getFinalState() - d.getInitialState()) + (d.getTotalExported() - d.getTotalImported());
    }

    /**
     * Capture initial state snapshot from inventory scanner.
     * Called before simulation starts (chunks unloaded → scan → start).
     *
     * @param inventory Map of resource ID to quantity
     */
    public void captureInitialState(Map<String, Long> inventory) {
        for (Map.Entry<String, Long> entry : inventory.entrySet()) {
            deltas.computeIfAbsent(entry.getKey(), k -> new ResourceDeltas())
                  .setInitialState(entry.getValue());
        }
    }

    /**
     * Capture final state snapshot from inventory scanner.
     * Called after simulation finishes (chunks unloaded → scan → calculate rates).
     *
     * @param inventory Map of resource ID to quantity
     */
    public void captureFinalState(Map<String, Long> inventory) {
        for (Map.Entry<String, Long> entry : inventory.entrySet()) {
            deltas.computeIfAbsent(entry.getKey(), k -> new ResourceDeltas())
                  .setFinalState(entry.getValue());
        }
    }

    /**
     * Get total imported for a resource (for GUI display).
     *
     * @param resourceId The resource ID
     * @return Total imported
     */
    public long getTotalImported(String resourceId) {
        ResourceDeltas d = deltas.get(resourceId);
        return d != null ? d.getTotalImported() : 0;
    }

    /**
     * Get total exported for a resource (for GUI display).
     *
     * @param resourceId The resource ID
     * @return Total exported
     */
    public long getTotalExported(String resourceId) {
        ResourceDeltas d = deltas.get(resourceId);
        return d != null ? d.getTotalExported() : 0;
    }

    /**
     * Get initial state for a resource (for GUI display).
     *
     * @param resourceId The resource ID
     * @return Initial state quantity
     */
    public long getInitialState(String resourceId) {
        ResourceDeltas d = deltas.get(resourceId);
        return d != null ? d.getInitialState() : 0;
    }

    /**
     * Get final state for a resource (for GUI display).
     *
     * @param resourceId The resource ID
     * @return Final state quantity
     */
    public long getFinalState(String resourceId) {
        ResourceDeltas d = deltas.get(resourceId);
        return d != null ? d.getFinalState() : 0;
    }

    /**
     * Get all resource IDs tracked during simulation.
     *
     * @return Set of resource IDs
     */
    public Set<String> getAllTrackedResources() {
        return deltas.keySet();
    }

    /**
     * Get all UUIDs that have tracked deltas.
     * Phase 6: Used for per-UUID rate calculation.
     *
     * @return Unmodifiable set of equipment UUIDs
     */
    public Set<UUID> getTrackedUUIDs() {
        return Collections.unmodifiableSet(deltasByUUID.keySet());
    }

    /**
     * Get all resource IDs tracked for a specific UUID.
     * Phase 6: Used to iterate resources per Importer/Exporter.
     *
     * @param uuid The equipment UUID
     * @return Unmodifiable set of resource IDs, or empty set if UUID not tracked
     */
    public Set<String> getTrackedResourcesForUUID(UUID uuid) {
        Map<String, ResourceDeltas> uuidDeltas = deltasByUUID.get(uuid);
        return uuidDeltas != null
            ? Collections.unmodifiableSet(uuidDeltas.keySet())
            : Collections.emptySet();
    }

    /**
     * Get total imported for a specific UUID and resource.
     * Phase 6: Used for per-UUID rate calculation.
     *
     * @param uuid The equipment UUID
     * @param resourceId The resource ID
     * @return Total imported by this UUID, or 0 if not tracked
     */
    public long getTotalImportedForUUID(UUID uuid, String resourceId) {
        Map<String, ResourceDeltas> uuidDeltas = deltasByUUID.get(uuid);
        if (uuidDeltas == null) {
            return 0L;
        }
        ResourceDeltas deltas = uuidDeltas.get(resourceId);
        return deltas != null ? deltas.getTotalImported() : 0L;
    }

    /**
     * Get total exported for a specific UUID and resource.
     * Phase 6: Used for per-UUID rate calculation.
     *
     * @param uuid The equipment UUID
     * @param resourceId The resource ID
     * @return Total exported by this UUID, or 0 if not tracked
     */
    public long getTotalExportedForUUID(UUID uuid, String resourceId) {
        Map<String, ResourceDeltas> uuidDeltas = deltasByUUID.get(uuid);
        if (uuidDeltas == null) {
            return 0L;
        }
        ResourceDeltas deltas = uuidDeltas.get(resourceId);
        return deltas != null ? deltas.getTotalExported() : 0L;
    }

    /**
     * Update first/last activity ticks when resources are transferred.
     *
     * @param currentTick The current game time
     */
    private void updateActivityTicks(long currentTick) {
        if (firstActivityTick == -1) {
            firstActivityTick = currentTick; // First resource transfer
        }
        lastActivityTick = currentTick; // Update last activity
    }

    /**
     * Get the tick when first resource transfer occurred.
     *
     * @return Tick number, or -1 if no activity yet
     */
    public long getFirstActivityTick() {
        return firstActivityTick;
    }

    /**
     * Get the tick when last resource transfer occurred.
     *
     * @return Tick number, or -1 if no activity yet
     */
    public long getLastActivityTick() {
        return lastActivityTick;
    }

    /**
     * Check if any activity has been recorded.
     *
     * @return True if at least one import/export happened
     */
    public boolean hasActivity() {
        return firstActivityTick != -1;
    }

    /**
     * Serialize to NBT for crash recovery.
     *
     * @return NBT compound tag
     */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag resourceList = new ListTag();

        for (Map.Entry<String, ResourceDeltas> entry : deltas.entrySet()) {
            CompoundTag resourceTag = new CompoundTag();
            resourceTag.putString("id", entry.getKey());
            resourceTag.put("deltas", entry.getValue().toNBT());
            resourceList.add(resourceTag);
        }

        tag.put("resources", resourceList);

        // Phase 6: Serialize per-UUID deltas
        ListTag uuidDeltasList = new ListTag();
        for (Map.Entry<UUID, Map<String, ResourceDeltas>> uuidEntry : deltasByUUID.entrySet()) {
            CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID("uuid", uuidEntry.getKey());

            ListTag uuidResourceList = new ListTag();
            for (Map.Entry<String, ResourceDeltas> resourceEntry : uuidEntry.getValue().entrySet()) {
                CompoundTag resourceTag = new CompoundTag();
                resourceTag.putString("id", resourceEntry.getKey());
                resourceTag.put("deltas", resourceEntry.getValue().toNBT());
                uuidResourceList.add(resourceTag);
            }
            uuidTag.put("resources", uuidResourceList);
            uuidDeltasList.add(uuidTag);
        }
        tag.put("deltasByUUID", uuidDeltasList);

        tag.putLong("firstActivityTick", firstActivityTick);
        tag.putLong("lastActivityTick", lastActivityTick);
        return tag;
    }

    /**
     * Deserialize from NBT.
     *
     * @param tag The NBT compound tag
     * @return New ResourceDeltaTracker instance
     */
    public static ResourceDeltaTracker fromNBT(CompoundTag tag) {
        ResourceDeltaTracker tracker = new ResourceDeltaTracker();

        if (tag.contains("resources")) {
            ListTag resourceList = tag.getList("resources", Tag.TAG_COMPOUND);
            for (int i = 0; i < resourceList.size(); i++) {
                CompoundTag resourceTag = resourceList.getCompound(i);
                String id = resourceTag.getString("id");
                ResourceDeltas deltas = ResourceDeltas.fromNBT(resourceTag.getCompound("deltas"));
                tracker.deltas.put(id, deltas);
            }
        }

        // Phase 6: Deserialize per-UUID deltas
        if (tag.contains("deltasByUUID")) {
            ListTag uuidDeltasList = tag.getList("deltasByUUID", Tag.TAG_COMPOUND);
            for (int i = 0; i < uuidDeltasList.size(); i++) {
                CompoundTag uuidTag = uuidDeltasList.getCompound(i);
                UUID uuid = uuidTag.getUUID("uuid");

                Map<String, ResourceDeltas> resourceDeltas = new HashMap<>();
                ListTag uuidResourceList = uuidTag.getList("resources", Tag.TAG_COMPOUND);
                for (int j = 0; j < uuidResourceList.size(); j++) {
                    CompoundTag resourceTag = uuidResourceList.getCompound(j);
                    String id = resourceTag.getString("id");
                    ResourceDeltas deltas = ResourceDeltas.fromNBT(resourceTag.getCompound("deltas"));
                    resourceDeltas.put(id, deltas);
                }

                tracker.deltasByUUID.put(uuid, resourceDeltas);
            }
        }

        tracker.firstActivityTick = tag.getLong("firstActivityTick");
        tracker.lastActivityTick = tag.getLong("lastActivityTick");
        return tracker;
    }

    /**
     * Reset all tracked data (used when starting new simulation).
     */
    public void reset() {
        deltas.clear();
        deltasByUUID.clear();
    }
}
