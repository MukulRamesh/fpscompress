package com.mukulramesh.fpscompress.portal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Handles cached production using fractional accumulator math.
 * Executes virtual factory production without loading CM chunks.
 */
public class CachedProductionHandler {
    private final PrefabBlockEntity entity;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
        justification = "Service class intentionally holds reference to entity for delegation pattern")
    public CachedProductionHandler(PrefabBlockEntity entity) {
        this.entity = entity;
    }

    /**
     * Tick cached production using fractional math.
     * Called every tick when in CACHED or HALTED state.
     * CM chunks stay UNLOADED - this is the whole point of caching!
     *
     * Performance optimization: In HALTED state, uses exponential backoff
     * to reduce inventory checking frequency (1 → 2 → 4 → 8... up to 100 ticks).
     */
    public void tickCachedProduction() {
        // Performance optimization: Exponential backoff in HALTED state
        if (entity.currentState == MachineState.HALTED) {
            entity.ticksSinceLastRetry++;
            if (entity.ticksSinceLastRetry < entity.haltedRetryInterval) {
                return; // Skip this tick - waiting for retry interval
            }
            // Retry interval reached - reset counter and attempt recovery
            entity.ticksSinceLastRetry = 0;
            FPSCompress.LOGGER.debug("HALTED retry attempt (interval: {} ticks)", entity.haltedRetryInterval);
        }

        boolean hadFailure = false;
        String failureMessage = "";

        // Phase 6: Process per-UUID rates instead of aggregate
        for (Map.Entry<UUID, Map<String, Double>> uuidEntry : entity.importerExporterRates.entrySet()) {
            UUID equipmentUUID = uuidEntry.getKey();

            for (Map.Entry<String, Double> rateEntry : uuidEntry.getValue().entrySet()) {
                String resourceId = rateEntry.getKey();
                double ratePerTick = rateEntry.getValue();

                // Build accumulator key: "UUID:resourceId" to track fractional state per UUID
                String accumKey = equipmentUUID.toString() + ":" + resourceId;

                // Initialize accumulator if needed
                entity.itemAccumulators.putIfAbsent(accumKey, 0.0);

                // Accumulate fractional production (ONLY if in CACHED state, not HALTED)
                double currentAccum = entity.itemAccumulators.get(accumKey);
                if (entity.currentState == MachineState.CACHED) {
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
                        entity.itemAccumulators.put(accumKey, currentAccum);

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
                entity.itemAccumulators.put(accumKey, currentAccum);
            }
        }  // End of per-UUID rates loop

        // Update state based on whether we had any failures this tick
        if (hadFailure) {
            // At least one transfer failed - enter/stay in HALTED
            if (entity.currentState != MachineState.HALTED) {
                // First failure - enter HALTED with 1-tick retry interval
                FPSCompress.LOGGER.warn("Cache broke at {} - entering HALTED", entity.getBlockPos());
                entity.haltedRetryInterval = 1;
                entity.ticksSinceLastRetry = 0;
                entity.setCurrentState(MachineState.HALTED);
            } else {
                // Still failing - exponential backoff (double interval, cap at 100 ticks = 5 seconds)
                entity.haltedRetryInterval = Math.min(entity.haltedRetryInterval * 2, 100);
            }
            entity.lastSimulationResult = failureMessage;
        } else {
            // All transfers succeeded - recover from HALTED if needed
            if (entity.currentState == MachineState.HALTED) {
                FPSCompress.LOGGER.info("Cache recovered at {} (after {} ticks backoff) - returning to CACHED",
                    entity.getBlockPos(), entity.haltedRetryInterval);
                entity.haltedRetryInterval = 1; // Reset for next failure
                entity.ticksSinceLastRetry = 0;
                entity.setCurrentState(MachineState.CACHED);
                entity.lastSimulationResult = ""; // Clear error message
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
            exporterUUID, resourceId, amount, entity.getLevel(), entity.getBlockPos(),
            entity.faceConfigs, entity.cachedProduction);
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
            importerUUID, resourceId, amount, entity.getLevel(), entity.getBlockPos(), entity.faceConfigs);
    }

    /**
     * Get localized item name from resource ID.
     * Used for user-friendly error messages.
     *
     * @param resourceId The resource ID (e.g., "minecraft:iron_ingot")
     * @return Localized name (e.g., "Iron Ingot") or fallback to resource ID
     */
    @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION",
        justification = "Catching all exceptions for fallback behavior is intentional")
    private String getLocalizedItemName(String resourceId) {
        try {
            ResourceLocation resLoc = ResourceLocation.parse(resourceId);
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(resLoc);
            return item.getName(new ItemStack(item)).getString();
        } catch (Exception e) {
            // Fallback: Return just the item name part (after colon)
            return resourceId.contains(":") ? resourceId.substring(resourceId.indexOf(':') + 1) : resourceId;
        }
    }
}
