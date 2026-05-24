package com.mukulramesh.fpscompress.portal;

/**
 * Handles cached production using fractional accumulator math.
 * Executes virtual factory production without loading CM chunks.
 */
public class CachedProductionHandler {
    private final PrefabBlockEntity entity;

    public CachedProductionHandler(PrefabBlockEntity entity) {
        this.entity = entity;
    }
}
