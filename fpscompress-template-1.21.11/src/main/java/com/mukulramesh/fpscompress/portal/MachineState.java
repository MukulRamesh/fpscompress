package com.mukulramesh.fpscompress.portal;

/**
 * Represents the current operational state of a PreFab factory.
 */
public enum MachineState {
    /**
     * Player is setting up the factory inside the room.
     * Chunks are loaded, routing is physical.
     */
    BUILDING,

    /**
     * System is observing production rates to calculate cached values.
     * Chunks are loaded, routing is physical.
     */
    SIMULATING,

    /**
     * Factory is running in virtual mode using cached rates.
     * Chunks are unloaded, routing is virtual.
     */
    CACHED,

    /**
     * Cache is invalid (starved inputs or blocked outputs).
     * Chunks are loaded, routing is physical, waiting for player intervention.
     */
    HALTED
}
