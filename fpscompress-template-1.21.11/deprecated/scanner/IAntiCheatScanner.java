package com.mukulramesh.fpscompress.scanner;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Interface for Dev 5: Spatial Capability Scanner
 *
 * This interface provides anti-cheat validation by scanning BlockEntity capabilities
 * before and after simulation to detect resource duplication exploits.
 *
 * RESPONSIBILITIES:
 * - Take snapshots of all BlockEntity capabilities in a room
 * - Compare pre/post snapshots to validate that the factory is not cheating
 * - Detect hidden batteries, tanks, or storage that drain during "closed-loop" simulation
 *
 * ANTI-CHEAT LOGIC:
 * - A valid closed-loop factory should not net-drain stored resources
 * - Example cheat: Hidden battery that provides power during simulation, then depletes
 * - Scanner must detect: (PostSnapshot.TotalEnergy < PreSnapshot.TotalEnergy) → INVALID
 *
 * PERFORMANCE:
 * - Only scan BlockEntities, not all 3,375 blocks in a 15×15×15 room
 * - Query capabilities efficiently (don't iterate unnecessary data)
 * - Use NeoForge capability system (IItemHandler, IFluidHandler, IEnergyStorage)
 *
 * CONFIGURATION:
 * - Server config for block whitelist/blacklist
 * - Allow server admins to exclude certain blocks from scanning
 *
 * @author Dev 5 - Anti-Cheat Scanner Team
 */
public interface IAntiCheatScanner {

    /**
     * Take a snapshot of all BlockEntity capabilities in the specified room.
     *
     * This captures the state of all items, fluids, and energy in BlockEntities
     * within the bounding box.
     *
     * @param dimension The ServerLevel (CM dimension)
     * @param bounds The bounding box of the room to scan
     * @return A snapshot representing the current state
     */
    Snapshot takeSnapshot(ServerLevel dimension, BoundingBox bounds);

    /**
     * Validate that the factory operated as a closed loop during simulation.
     *
     * Compares pre and post snapshots to ensure no hidden resources were consumed.
     * A valid closed-loop factory should have:
     * - PostSnapshot.TotalResources >= PreSnapshot.TotalResources (or within tolerance)
     *
     * @param preSnapshot Snapshot taken before simulation started
     * @param postSnapshot Snapshot taken after simulation ended
     * @return true if valid, false if cheating detected
     */
    boolean validateLoop(Snapshot preSnapshot, Snapshot postSnapshot);

    /**
     * Get the validation result with detailed information.
     *
     * This provides a detailed breakdown of what failed validation (if anything).
     * Useful for debugging and reporting to players.
     *
     * @param preSnapshot Snapshot taken before simulation
     * @param postSnapshot Snapshot taken after simulation
     * @return A validation result with details
     */
    ValidationResult getValidationDetails(Snapshot preSnapshot, Snapshot postSnapshot);

    /**
     * Snapshot class representing the state of all BlockEntity capabilities in a room.
     *
     * This should store aggregated totals for each resource type:
     * - Total items (by ID)
     * - Total fluids (by ID)
     * - Total energy (in FE)
     */
    interface Snapshot {
        /**
         * Get the total count of a specific item in this snapshot.
         *
         * @param itemId The item identifier (e.g., "minecraft:iron_ingot")
         * @return The total count across all BlockEntities
         */
        int getItemCount(String itemId);

        /**
         * Get the total amount of a specific fluid in this snapshot.
         *
         * @param fluidId The fluid identifier (e.g., "minecraft:lava")
         * @return The total amount in mB across all BlockEntities
         */
        int getFluidAmount(String fluidId);

        /**
         * Get the total energy in this snapshot.
         *
         * @return The total energy in FE across all BlockEntities
         */
        long getTotalEnergy();

        /**
         * Get the number of BlockEntities scanned in this snapshot.
         *
         * @return The count of scanned BlockEntities
         */
        int getScannedBlockEntityCount();
    }

    /**
     * Validation result class with detailed information about validation failure.
     */
    interface ValidationResult {
        /**
         * Check if validation passed.
         *
         * @return true if valid, false if invalid
         */
        boolean isValid();

        /**
         * Get a human-readable message describing the validation result.
         *
         * @return A message (e.g., "Validation passed" or "Hidden battery detected")
         */
        String getMessage();

        /**
         * Get the resource type that failed validation (if any).
         *
         * @return The resource type (ITEM, FLUID, ENERGY) or null if valid
         */
        String getFailedResourceType();

        /**
         * Get the specific resource ID that failed validation (if any).
         *
         * @return The resource ID (e.g., "minecraft:iron_ingot") or null
         */
        String getFailedResourceId();

        /**
         * Get the net change in resources (post - pre).
         *
         * Negative values indicate resources were consumed from hidden storage.
         *
         * @return The net change (can be negative if cheating)
         */
        long getNetResourceChange();
    }
}
