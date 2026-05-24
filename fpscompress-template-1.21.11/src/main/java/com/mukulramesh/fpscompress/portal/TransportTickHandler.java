package com.mukulramesh.fpscompress.portal;

/**
 * Handles resource transport and tick logic.
 * Routes items/fluids/energy between Overworld and CM dimension during SIMULATING state.
 */
public class TransportTickHandler {
    private final PrefabBlockEntity entity;

    public TransportTickHandler(PrefabBlockEntity entity) {
        this.entity = entity;
    }
}
