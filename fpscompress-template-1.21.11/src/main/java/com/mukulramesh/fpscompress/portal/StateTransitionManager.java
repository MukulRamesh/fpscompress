package com.mukulramesh.fpscompress.portal;

/**
 * Manages state transitions for PreFab blocks.
 * Handles BUILDING → SIMULATING → CACHED → HALTED state machine.
 */
public class StateTransitionManager {
    private final PrefabBlockEntity entity;

    public StateTransitionManager(PrefabBlockEntity entity) {
        this.entity = entity;
    }
}
