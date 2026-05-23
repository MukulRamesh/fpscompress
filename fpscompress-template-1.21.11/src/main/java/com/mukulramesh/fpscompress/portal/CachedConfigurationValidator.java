package com.mukulramesh.fpscompress.portal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 6: Validates that all UUIDs with cached rates have at least one face mapped.
 * Ensures multi-output routing is properly configured.
 */
public final class CachedConfigurationValidator {

    private CachedConfigurationValidator() {
        // Utility class
    }

    /**
     * Validates that all UUIDs with cached rates have at least one face mapped.
     * Called during state transitions to CACHED and during face reconfiguration.
     *
     * @param importerExporterRates Per-UUID rate storage
     * @param faceConfigs Face configurations
     * @return ValidationResult with success flag and error/warning messages
     */
    public static ValidationResult validate(
            Map<UUID, Map<String, Double>> importerExporterRates,
            Map<Direction, FaceConfig> faceConfigs) {

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Build reverse map: UUID → Set<Direction> (which faces map to this UUID)
        Map<UUID, Set<Direction>> uuidToFaces = new HashMap<>();
        for (Map.Entry<Direction, FaceConfig> entry : faceConfigs.entrySet()) {
            FaceConfig config = entry.getValue();
            if (config.getMode() != FaceMode.DISABLED && config.getTargetUUID() != null) {
                uuidToFaces.computeIfAbsent(config.getTargetUUID(), k -> new HashSet<>())
                           .add(entry.getKey());
            }
        }

        // Check 1: All UUIDs with rates MUST have at least one face mapped
        for (Map.Entry<UUID, Map<String, Double>> entry : importerExporterRates.entrySet()) {
            UUID uuid = entry.getKey();
            if (!uuidToFaces.containsKey(uuid)) {
                // Find which resources this UUID produces/consumes
                Map<String, Double> rates = entry.getValue();
                List<String> unmappedResources = new ArrayList<>(rates.keySet());

                // Determine if Importer or Exporter based on rate sign
                boolean isExporter = rates.values().stream().anyMatch(rate -> rate > 0);
                String equipmentType = isExporter ? "Exporter" : "Importer";

                errors.add(String.format(
                    "OUTPUT NOT ROUTED: %s %s produces %s but has no face mapped (reconnect to face)",
                    equipmentType, uuid.toString().substring(0, 8),
                    String.join(", ", unmappedResources)
                ));
            }
        }

        // Check 2: Warn if faces map to UUIDs with no rates (idle equipment, not fatal)
        for (Map.Entry<UUID, Set<Direction>> entry : uuidToFaces.entrySet()) {
            UUID uuid = entry.getKey();
            if (!importerExporterRates.containsKey(uuid)) {
                warnings.add(String.format(
                    "IDLE EQUIPMENT: UUID %s mapped to faces %s but has no cached rates (may be unused)",
                    uuid.toString().substring(0, 8),
                    entry.getValue().stream().map(Direction::getName)
                        .collect(Collectors.joining(", "))
                ));
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Result of validation check.
     * Uses unmodifiable lists to prevent external modification.
     *
     * @param success True if validation passed (no errors)
     * @param errors Unmodifiable list of error messages (configuration problems requiring player action)
     * @param warnings Unmodifiable list of warning messages (non-fatal issues)
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
        justification = "Lists are made unmodifiable via List.copyOf() in constructor")
    public record ValidationResult(boolean success, List<String> errors, List<String> warnings) {
        /**
         * Compact canonical constructor that creates defensive unmodifiable copies.
         */
        public ValidationResult {
            errors = errors != null ? List.copyOf(errors) : Collections.emptyList();
            warnings = warnings != null ? List.copyOf(warnings) : Collections.emptyList();
        }
    }
}
