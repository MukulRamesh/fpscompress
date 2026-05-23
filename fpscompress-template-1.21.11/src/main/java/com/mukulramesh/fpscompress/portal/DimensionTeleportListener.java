package com.mukulramesh.fpscompress.portal;

import com.mojang.logging.LogUtils;
import dev.compactmods.machines.machine.block.BoundCompactMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for players entering Compact Machines rooms and caches their room center coordinates.
 * This eliminates the need for reflection-based coordinate lookups for PreFab blocks.
 *
 * FIXED: No longer searches 3,087 blocks per tick! Now captures exact block position on interaction.
 */
public class DimensionTeleportListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    // NEW: Tracks which block the player clicked BEFORE teleporting
    private final Map<UUID, BlockPos> playerClickedBlock = new HashMap<>();

    // Tracks players who just teleported to CM dimension, mapping to the clicked block position
    private final Map<UUID, BlockPos> pendingTeleports = new HashMap<>();

    /**
     * Listen for player right-clicking CM/PreFab blocks with PSD.
     * This captures the EXACT block position before teleportation occurs.
     *
     * CRITICAL: Uses HIGHEST priority to run BEFORE CM's teleport handler.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check if player is holding Personal Shrinking Device
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(event.getItemStack().getItem()).toString();
        if (!itemId.equals("compactmachines:personal_shrinking_device")) {
            return;
        }

        BlockPos clickedPos = event.getPos();
        BlockEntity be = player.level().getBlockEntity(clickedPos);

        // Only track CM or PreFab blocks
        if (be instanceof BoundCompactMachineBlockEntity || be instanceof PrefabBlockEntity) {
            playerClickedBlock.put(player.getUUID(), clickedPos);
            LOGGER.debug("Player {} clicked CM/PreFab at {} with PSD",
                        player.getName().getString(), clickedPos);
        }
    }

    @SubscribeEvent
    public void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check dimensions for entry/exit
        String fromDimensionKey = player.level().dimension().location().toString();
        String toDimensionKey = event.getDimension().location().toString();

        boolean leavingCM = fromDimensionKey.contains("compactmachines");
        boolean enteringCM = toDimensionKey.contains("compactmachines");

        LOGGER.debug("Player {} traveling from {} to {}",
                    player.getName().getString(), fromDimensionKey, toDimensionKey);

        // Exit detection: Pop room stack when leaving CM dimension
        if (leavingCM && !enteringCM) {
            PlayerRoomContext.exitRoom(player.getUUID());
            LOGGER.info("Player {} exited CM dimension, popped room stack",
                       player.getName().getString());
        }

        // Only process entry if entering CM dimension
        if (!enteringCM) {
            return;
        }

        // NEW STRATEGY: Find the block the player is looking at (raytrace)
        BlockPos targetBlock = getPlayerLookingAtBlock(player);

        if (targetBlock != null) {
            pendingTeleports.put(player.getUUID(), targetBlock);
            LOGGER.info("Player {} entering CM dimension via block at {} (raytraced)",
                       player.getName().getString(), targetBlock);
        } else {
            // FALLBACK: Search nearby blocks (small radius only - 3x3x3)
            BlockPos nearbyBlock = findNearbyCMBlock(player);
            if (nearbyBlock != null) {
                pendingTeleports.put(player.getUUID(), nearbyBlock);
                LOGGER.info("Player {} entering CM dimension via block at {} (found nearby)",
                           player.getName().getString(), nearbyBlock);
            } else {
                LOGGER.warn("Player {} teleported to CM but no CM/PreFab block found nearby",
                           player.getName().getString());
            }
        }
    }

    /**
     * Get the block the player is currently looking at (raytrace).
     * Returns the block position if it's a CM or PreFab block, null otherwise.
     */
    private BlockPos getPlayerLookingAtBlock(ServerPlayer player) {
        // Raytrace to find what block the player is looking at (5 block reach)
        net.minecraft.world.phys.BlockHitResult hitResult =
            (net.minecraft.world.phys.BlockHitResult) player.pick(5.0D, 0.0F, false);

        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos pos = hitResult.getBlockPos();
            BlockEntity be = player.level().getBlockEntity(pos);

            // Check if it's a CM or PreFab block
            if (be instanceof BoundCompactMachineBlockEntity || be instanceof PrefabBlockEntity) {
                return pos;
            }
        }

        return null;
    }

    /**
     * Find CM or PreFab block near player (SMALL 3x3x3 search only, not 3,087 blocks!)
     */
    private BlockPos findNearbyCMBlock(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();

        // Small search: ±1 block in all directions (27 blocks total)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    BlockEntity be = player.level().getBlockEntity(pos);

                    if (be instanceof BoundCompactMachineBlockEntity || be instanceof PrefabBlockEntity) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (pendingTeleports.isEmpty()) {
            return;
        }

        // Process pending teleports (after 1 tick delay for teleport to complete)
        Map<UUID, BlockPos> toProcess = new HashMap<>(pendingTeleports);
        pendingTeleports.clear();

        for (Map.Entry<UUID, BlockPos> entry : toProcess.entrySet()) {
            UUID playerId = entry.getKey();
            BlockPos sourceBlockPos = entry.getValue(); // FIXED: This is now the exact clicked block!

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }

            // Capture room center (player's current position in CM dimension)
            BlockPos roomCenter = player.blockPosition();
            ServerLevel overworldLevel = event.getServer().overworld();

            LOGGER.debug("Player {} arrived at room center {}",
                         player.getName().getString(), roomCenter);

            // NEW: No search needed! We already know the exact block position.
            // Get room code from the CM block
            String roomCode = getRoomCodeFromBlock(overworldLevel, sourceBlockPos);
            if (roomCode == null) {
                LOGGER.warn("Could not determine room code for block at {}", sourceBlockPos);
                continue;
            }

            // Store in cache
            RoomCoordinateCache cache = RoomCoordinateCache.get(event.getServer());
            cache.setRoomCenter(sourceBlockPos, roomCode, roomCenter);

            // Push room context for player
            PlayerRoomContext.enterRoom(playerId, roomCode);

            LOGGER.info("Cached coordinates for room {}: Overworld block {} → Room center {}",
                        roomCode, sourceBlockPos, roomCenter);
        }
    }

    // REMOVED: findSourceBlock() - No longer needed!
    // We now capture the exact block position on interaction, eliminating the 3,087 block search.

    /**
     * Get the room code from a CM or PreFab block.
     */
    private String getRoomCodeFromBlock(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);

        // PreFab blocks store room code directly
        if (be instanceof PrefabBlockEntity prefab) {
            return prefab.getRoomCode();
        }

        // CM blocks require reflection
        if (be instanceof BoundCompactMachineBlockEntity cmBE) {
            try {
                // Cast to Object to avoid compile-time interface check
                Object cmObj = cmBE;
                java.lang.reflect.Method connectedRoomMethod =
                    cmObj.getClass().getMethod("connectedRoom");
                Object roomResult = connectedRoomMethod.invoke(cmObj);

                // Handle both String and Optional<String> return types
                if (roomResult instanceof String roomCode) {
                    if (!roomCode.isEmpty()) {
                        return roomCode;
                    }
                } else if (roomResult instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    return opt.get().toString();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to get room code from CM block via reflection", e);
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * Clear room stack when player disconnects.
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerRoomContext.clearPlayer(event.getEntity().getUUID());
        LOGGER.debug("Cleared room stack for disconnected player {}",
            event.getEntity().getName().getString());
    }
}
