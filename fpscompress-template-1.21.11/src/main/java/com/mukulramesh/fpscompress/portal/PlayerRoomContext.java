package com.mukulramesh.fpscompress.portal;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which CM room each player is currently in using a FILO stack.
 * Handles nested PreFabs (PreFab placed inside another CM room).
 */
public final class PlayerRoomContext {
    private static final Map<UUID, Deque<String>> PLAYER_STACKS = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerRoomContext() {
        // Utility class
    }

    /**
     * Push a room code onto the player's stack when entering a CM room.
     *
     * @param playerUUID Player's UUID
     * @param roomCode Room code being entered
     */
    public static void enterRoom(UUID playerUUID, String roomCode) {
        PLAYER_STACKS.computeIfAbsent(playerUUID, k -> new ArrayDeque<>()).push(roomCode);
        LOGGER.debug("Player {} entered room {} (depth: {})",
            playerUUID, roomCode, PLAYER_STACKS.get(playerUUID).size());
    }

    /**
     * Pop a room code from the player's stack when exiting a CM room.
     *
     * @param playerUUID Player's UUID
     */
    public static void exitRoom(UUID playerUUID) {
        Deque<String> stack = PLAYER_STACKS.get(playerUUID);
        if (stack != null && !stack.isEmpty()) {
            String roomCode = stack.pop();
            LOGGER.debug("Player {} exited room {} (depth: {})",
                playerUUID, roomCode, stack.size());
            if (stack.isEmpty()) {
                PLAYER_STACKS.remove(playerUUID);
            }
        }
    }

    /**
     * Get the current room the player is in (peek top of stack).
     * Returns null if player is in Overworld or outside any CM room.
     *
     * @param playerUUID Player's UUID
     * @return Current room code or null
     */
    @Nullable
    public static String getCurrentRoom(UUID playerUUID) {
        Deque<String> stack = PLAYER_STACKS.get(playerUUID);
        return (stack != null && !stack.isEmpty()) ? stack.peek() : null;
    }

    /**
     * Clear the entire stack for a player (on disconnect).
     *
     * @param playerUUID Player's UUID
     */
    public static void clearPlayer(UUID playerUUID) {
        PLAYER_STACKS.remove(playerUUID);
        LOGGER.debug("Cleared room stack for player {}", playerUUID);
    }

    /**
     * Clear all stacks (server shutdown/restart).
     */
    public static void clearAll() {
        PLAYER_STACKS.clear();
        LOGGER.info("Cleared all room context stacks");
    }
}
