package com.mukulramesh.fpscompress.portal;

/**
 * Handles NBT serialization and deserialization for PreFab blocks.
 * Manages schema migration, validation, and fake registry loading.
 */
public class PrefabNBTSerializer {
    private final PrefabBlockEntity entity;

    public PrefabNBTSerializer(PrefabBlockEntity entity) {
        this.entity = entity;
    }
}
