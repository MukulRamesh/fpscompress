package com.mukulramesh.fpscompress.portal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
     * MVP: Only totalImported and totalExported.
     * Post-MVP: Will add initialState and finalState for anti-cheat.
     */
    private static final class ResourceDeltas {
        private long totalImported = 0;
        private long totalExported = 0;

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

        CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("imported", totalImported);
            tag.putLong("exported", totalExported);
            return tag;
        }

        static ResourceDeltas fromNBT(CompoundTag tag) {
            ResourceDeltas deltas = new ResourceDeltas();
            deltas.totalImported = tag.getLong("imported");
            deltas.totalExported = tag.getLong("exported");
            return deltas;
        }
    }

    // Map: Resource ID (e.g., "minecraft:iron_ingot") -> Deltas
    private final Map<String, ResourceDeltas> deltas = new HashMap<>();

    // Track first and last activity ticks (for accurate simulation time measurement)
    private long firstActivityTick = -1; // -1 = no activity yet
    private long lastActivityTick = -1;

    /**
     * Record items imported from Overworld into CM dimension.
     * Called after successful PULL transport.
     *
     * @param resourceId The resource ID (e.g., "minecraft:coal")
     * @param amount Number of items imported
     * @param currentTick Current game time (for tracking first/last activity)
     */
    public void recordImport(String resourceId, long amount, long currentTick) {
        deltas.computeIfAbsent(resourceId, k -> new ResourceDeltas())
              .addImported(amount);
        updateActivityTicks(currentTick);
    }

    /**
     * Record items exported from CM dimension to Overworld.
     * Called after successful PUSH transport.
     *
     * @param resourceId The resource ID (e.g., "minecraft:iron_ingot")
     * @param amount Number of items exported
     * @param currentTick Current game time (for tracking first/last activity)
     */
    public void recordExport(String resourceId, long amount, long currentTick) {
        deltas.computeIfAbsent(resourceId, k -> new ResourceDeltas())
              .addExported(amount);
        updateActivityTicks(currentTick);
    }

    /**
     * Calculate net production for a resource.
     * MVP Formula: Net = Exported - Imported
     *
     * @param resourceId The resource ID
     * @return Net production (positive = produced, negative = consumed, zero = passthrough)
     */
    public long calculateNet(String resourceId) {
        ResourceDeltas d = deltas.get(resourceId);
        if (d == null) {
            return 0;
        }
        return d.getTotalExported() - d.getTotalImported();
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
     * Get all resource IDs tracked during simulation.
     *
     * @return Set of resource IDs
     */
    public Set<String> getAllTrackedResources() {
        return deltas.keySet();
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

        tracker.firstActivityTick = tag.getLong("firstActivityTick");
        tracker.lastActivityTick = tag.getLong("lastActivityTick");
        return tracker;
    }

    /**
     * Reset all tracked data (used when starting new simulation).
     */
    public void reset() {
        deltas.clear();
    }
}
