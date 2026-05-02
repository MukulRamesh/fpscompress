package com.mukulramesh.fpscompress.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caches player positions before entering PreFab rooms for exit teleportation.
 * This allows players to exit PreFab rooms using the Personal Shrinking Device.
 */
public final class PlayerPositionCache {
    private static final Map<UUID, CachedPosition> ENTRY_POSITIONS = new HashMap<>();

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private PlayerPositionCache() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Store player's position, dimension, and rotation before entering a PreFab room.
     */
    public static void cacheEntryPosition(UUID playerId, ResourceKey<Level> dimension,
            BlockPos pos, float yaw, float pitch) {
        ENTRY_POSITIONS.put(playerId, new CachedPosition(dimension, pos, yaw, pitch));
    }

    /**
     * Get player's cached entry position (for exiting).
     */
    public static CachedPosition getEntryPosition(UUID playerId) {
        return ENTRY_POSITIONS.get(playerId);
    }

    /**
     * Clear cached position after player exits.
     */
    public static void clearEntryPosition(UUID playerId) {
        ENTRY_POSITIONS.remove(playerId);
    }

    /**
     * Check if player has a cached entry position.
     */
    public static boolean hasCachedPosition(UUID playerId) {
        return ENTRY_POSITIONS.containsKey(playerId);
    }

    /**
     * Stores a player's entry position, dimension, and rotation.
     */
    public static class CachedPosition {
        private final ResourceKey<Level> dimension;
        private final BlockPos position;
        private final float yaw;
        private final float pitch;

        public CachedPosition(ResourceKey<Level> dimension, BlockPos position, float yaw, float pitch) {
            this.dimension = dimension;
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public ResourceKey<Level> getDimension() {
            return dimension;
        }

        public BlockPos getPosition() {
            return position;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }
    }
}
