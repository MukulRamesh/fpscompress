package com.mukulramesh.fpscompress.portal;

/**
 * Calculates production rates from delta tracking during simulation.
 * Implements delta accounting formula: Net = (Final - Initial) + (Exported - Imported).
 */
public class RateCalculationEngine {
    private final PrefabBlockEntity entity;

    public RateCalculationEngine(PrefabBlockEntity entity) {
        this.entity = entity;
    }
}
