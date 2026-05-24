package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.gui.RateNormalizer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Calculates production rates from delta tracking during simulation.
 * Implements delta accounting formula: Net = (Final - Initial) + (Exported - Imported).
 */
public class RateCalculationEngine {
    private final PrefabBlockEntity entity;

    public RateCalculationEngine(PrefabBlockEntity entity) {
        this.entity = entity;
    }

    /**
     * Calculate rates from simulation data and transition to CACHED state.
     * Split into smaller helper methods to stay under checkstyle limit.
     */
    public void calculateRatesAndTransition() {
        long totalTicks = entity.simulationEndTick - entity.simulationStartTick;

        if (totalTicks == 0) {
            // All activity happened in same tick - use 1 tick to avoid division by zero
            totalTicks = 1;
            FPSCompress.LOGGER.warn("All activity in single tick - using 1 tick for rate calculation");
        }

        // Step 1: Calculate aggregate net production (storage + flow deltas)
        Map<String, Long> aggregateNetProduction = calculateAggregateNetProduction();

        // Step 2: Distribute to UUIDs based on flow contribution
        distributeToUUIDs(aggregateNetProduction, totalTicks);

        // Step 3: Derive aggregate rates and auto-normalize for display
        deriveAggregateRates();

        // Step 4: Validate and transition to CACHED
        validateAndTransition();
    }

    /**
     * Calculate aggregate net production using full formula.
     * Formula: Net = (Final - Initial) + (Exported - Imported)
     */
    private Map<String, Long> calculateAggregateNetProduction() {
        entity.clearImporterExporterRates();

        Set<UUID> trackedUUIDs = entity.deltaTracker.getTrackedUUIDs();
        FPSCompress.LOGGER.info("▶ finishSimulation: deltaTracker has {} tracked UUIDs",
            trackedUUIDs.size());

        Map<String, Long> aggregateNetProduction = new HashMap<>();
        for (String resourceId : entity.deltaTracker.getAllTrackedResources()) {
            long initialState = entity.deltaTracker.getInitialState(resourceId);
            long finalState = entity.deltaTracker.getFinalState(resourceId);
            long totalImported = entity.deltaTracker.getTotalImported(resourceId);
            long totalExported = entity.deltaTracker.getTotalExported(resourceId);

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

        return aggregateNetProduction;
    }

    /**
     * Distribute aggregate net production to UUIDs proportionally based on flow.
     */
    private void distributeToUUIDs(Map<String, Long> aggregateNetProduction, long totalTicks) {
        Set<UUID> trackedUUIDs = entity.deltaTracker.getTrackedUUIDs();

        for (UUID uuid : trackedUUIDs) {
            for (String resourceId : entity.deltaTracker.getTrackedResourcesForUUID(uuid)) {
                long uuidImported = entity.deltaTracker.getTotalImportedForUUID(uuid, resourceId);
                long uuidExported = entity.deltaTracker.getTotalExportedForUUID(uuid, resourceId);
                long uuidFlowDelta = uuidExported - uuidImported;

                // Get aggregate values
                long aggregateImported = entity.deltaTracker.getTotalImported(resourceId);
                long aggregateExported = entity.deltaTracker.getTotalExported(resourceId);
                long aggregateFlowDelta = aggregateExported - aggregateImported;

                // Calculate this UUID's share of the aggregate net production
                Long aggregateNet = aggregateNetProduction.get(resourceId);
                if (aggregateNet == null) {
                    continue; // Resource filtered out (negligible production)
                }

                // Proportional distribution based on flow contribution
                long uuidNetProduction = calculateUUIDShare(
                    uuid, resourceId, uuidFlowDelta, aggregateFlowDelta, aggregateNet, trackedUUIDs);

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
                    entity.setRateForUUID(uuid, resourceId, ratePerTick);
                    FPSCompress.LOGGER.info(
                        "Calculated per-UUID rate for {} ({}): {} items/tick (net: {} over {} ticks)",
                        uuid.toString().substring(0, 8), resourceId, ratePerTick,
                        uuidNetProduction, totalTicks);
                }
            }
        }
    }

    /**
     * Calculate a UUID's share of aggregate net production.
     */
    private long calculateUUIDShare(UUID uuid, String resourceId, long uuidFlowDelta,
                                     long aggregateFlowDelta, long aggregateNet,
                                     Set<UUID> trackedUUIDs) {
        if (aggregateFlowDelta == 0) {
            // No flow delta - all UUIDs get equal share of storage delta
            int uuidCount = (int) trackedUUIDs.stream()
                .filter(u -> entity.deltaTracker.getTrackedResourcesForUUID(u).contains(resourceId))
                .count();
            return aggregateNet / Math.max(1, uuidCount);
        } else {
            // Proportional to flow contribution
            double proportion = (double) uuidFlowDelta / aggregateFlowDelta;
            return Math.round(aggregateNet * proportion);
        }
    }

    /**
     * Derive aggregate cached rates from per-UUID rates and auto-normalize.
     */
    private void deriveAggregateRates() {
        // Keep aggregate cachedRates for backward compatibility (GUI display)
        entity.clearCachedRates();
        for (Map<String, Double> uuidRates : entity.importerExporterRates.values()) {
            for (Map.Entry<String, Double> entry : uuidRates.entrySet()) {
                entity.cachedRates.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        FPSCompress.LOGGER.info("▶ Aggregate rates (for GUI): {} resources", entity.cachedRates.size());

        // Auto-normalize rates for better display
        if (!entity.cachedRates.isEmpty()) {
            RateNormalizer.NormalizationResult autoResult =
                RateNormalizer.autoNormalize(entity.cachedRates);
            entity.autoNormalizedTicks = autoResult.normalizedTicks();
            entity.currentDisplayMode = autoResult.suggestedMode();
            entity.autoNormalizedDisplayMode = autoResult.suggestedMode(); // Store original mode
            entity.focusedResourceId = null; // Clear focus on new simulation
            entity.useAutoNormalize = true; // Enable auto-normalize by default
            FPSCompress.LOGGER.info("Auto-normalized: {} ticks, mode {}",
                entity.autoNormalizedTicks, entity.currentDisplayMode.name());
        }
    }

    /**
     * Validate configuration and transition to CACHED state if valid.
     */
    private void validateAndTransition() {
        // Detect passthrough (activity occurred but net production is zero)
        if (entity.cachedRates.isEmpty()) {
            FPSCompress.LOGGER.info("Passthrough detected (net production = 0) - resetting to BUILDING");
            entity.lastSimulationResult = "Passthrough (no net production)";
            entity.setCurrentState(MachineState.BUILDING);
            return;
        }

        // Phase 6: Validate that all UUIDs with rates have faces mapped
        CachedConfigurationValidator.ValidationResult validation =
            CachedConfigurationValidator.validate(entity.importerExporterRates, entity.faceConfigs);

        if (!validation.success()) {
            // Configuration error - reset to BUILDING (requires face reconfiguration)
            FPSCompress.LOGGER.error("PreFab validation failed, resetting to BUILDING:");
            for (String error : validation.errors()) {
                FPSCompress.LOGGER.error("  {}", error);
            }
            entity.lastSimulationResult = "Configuration error: " + String.join("; ", validation.errors());
            entity.setCurrentState(MachineState.BUILDING);
            entity.setChanged();
            return;  // Don't transition to CACHED
        }

        // Log warnings (non-fatal)
        for (String warning : validation.warnings()) {
            FPSCompress.LOGGER.warn("PreFab validation warning: {}", warning);
        }

        // Record when CACHED state starts and reset production counters
        entity.cachedStateStartTick = entity.getLevel().getGameTime();
        entity.cachedProduction.clear();

        // Clear result message on success (only keep failure messages visible)
        entity.lastSimulationResult = "";

        // Transition state
        entity.setCurrentState(MachineState.CACHED);

        FPSCompress.LOGGER.info("PreFab at {} finished simulation (tick {}), cached {} rates",
            entity.getBlockPos(), entity.simulationEndTick, entity.cachedRates.size());
    }
}
