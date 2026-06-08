package com.mukulramesh.fpscompress.gui;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for normalizing production rates to human-readable formats.
 * Provides item-focused normalization for "per 1 unit" display.
 */
public final class RateNormalizer {

    private RateNormalizer() {
        // Utility class, no instantiation
    }

    /**
     * Normalize all rates to "per 1 unit of focused item".
     * Scales all rates proportionally based on the time needed to produce 1 unit of the focused item.
     *
     * @param rates Map of resource IDs to per-tick rates
     * @param focusedItemId Resource ID to focus on
     * @return Map of resource IDs to normalized rates
     */
    public static Map<String, Double> normalizeToItem(Map<String, Double> rates, String focusedItemId) {
        if (rates == null || focusedItemId == null) {
            return new HashMap<>();
        }
        if (!rates.containsKey(focusedItemId)) {
            return new HashMap<>(rates);
        }

        Double focusedRate = rates.get(focusedItemId);
        if (focusedRate == null || focusedRate == 0.0) {
            return new HashMap<>(rates);
        }

        // Calculate time needed for 1 unit of focused item
        double timeForOneUnit = 1.0 / Math.abs(focusedRate);

        // Scale all rates by this time
        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() * timeForOneUnit);
        }

        return normalized;
    }
}
