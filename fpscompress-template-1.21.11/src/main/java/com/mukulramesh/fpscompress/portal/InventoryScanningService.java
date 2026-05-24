package com.mukulramesh.fpscompress.portal;

/**
 * Handles inventory scanning operations for PreFab blocks.
 * Scans Importer/Exporter buffers and room boundaries.
 */
public class InventoryScanningService {
    private final PrefabBlockEntity entity;

    public InventoryScanningService(PrefabBlockEntity entity) {
        this.entity = entity;
    }
}
