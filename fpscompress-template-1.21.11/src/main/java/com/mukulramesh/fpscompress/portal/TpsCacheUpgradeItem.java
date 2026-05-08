package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import dev.compactmods.machines.machine.block.BoundCompactMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * Item that installs the TPS Cache Upgrade on Compact Machines.
 *
 * Right-click a Compact Machine to enable TPS caching mode.
 * Once installed, the machine can operate in "headless" mode with
 * virtual buffers instead of physical chunk simulation.
 *
 * @author Dev 1 - Core Registry Team
 */
public class TpsCacheUpgradeItem extends Item {

    /**
     * Constructor for TpsCacheUpgradeItem.
     *
     * @param properties The item properties
     */
    public TpsCacheUpgradeItem(Properties properties) {
        super(properties);
    }

    /**
     * Called when the player right-clicks a block with this item.
     *
     * Logic:
     * 1. Check if target is a Compact Machine BlockEntity
     * 2. Get or create VirtualMachineDataImpl attachment
     * 3. Check if already upgraded
     * 4. Install upgrade and consume item
     *
     * @param context The use context
     * @return The interaction result
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        // Only process on server side
        if (level.isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());

        // Check if target is a Compact Machine
        if (!(blockEntity instanceof BoundCompactMachineBlockEntity cmBE)) {
            player.displayClientMessage(
                Component.literal("§cThis only works on Compact Machines!"),
                true
            );
            return InteractionResult.FAIL;
        }

        // Get CM's room code and size via reflection
        FPSCompress.LOGGER.info("Attempting to upgrade CM at {} to PreFab", context.getClickedPos());
        String roomCode = getRoomCodeFromCM(cmBE);
        if (roomCode == null) {
            FPSCompress.LOGGER.error("Failed to get room code from CM");
            player.displayClientMessage(
                Component.literal("§cFailed to read room data from Compact Machine"),
                true
            );
            return InteractionResult.FAIL;
        }
        FPSCompress.LOGGER.info("Got room code: {}", roomCode);

        // Get room size (internal dimensions - can be non-cubic)
        int[] roomDimensions = getRoomDimensionsFromCM(cmBE);
        if (roomDimensions == null) {
            FPSCompress.LOGGER.warn("Failed to get room dimensions from CM - defaulting to null");
            // Non-fatal: We can still upgrade, but validation will be limited
        } else {
            FPSCompress.LOGGER.info("Got room dimensions: {}x{}x{}",
                roomDimensions[0], roomDimensions[1], roomDimensions[2]);
        }

        // Replace block: CM → PreFab
        BlockPos pos = context.getClickedPos();
        FPSCompress.LOGGER.info("Replacing block at {} with PreFab", pos);

        try {
            // CRITICAL: Remove old BlockEntity first to prevent stale cache
            level.removeBlockEntity(pos);

            // Use flag 3: notify neighbors + send to clients
            boolean success = level.setBlock(pos, FPSCompress.PREFAB_BLOCK.get().defaultBlockState(), 3);
            if (!success) {
                FPSCompress.LOGGER.error("setBlock returned false");
                player.displayClientMessage(
                    Component.literal("§cFailed to place PreFab block"),
                    true
                );
                return InteractionResult.FAIL;
            }

            FPSCompress.LOGGER.info("Block replaced successfully");
        } catch (Exception e) {
            FPSCompress.LOGGER.error("Failed to replace block", e);
            player.displayClientMessage(
                Component.literal("§cFailed to place PreFab block"),
                true
            );
            return InteractionResult.FAIL;
        }

        // Initialize PreFab BlockEntity (force chunk to refresh)
        level.getChunkAt(pos).setUnsaved(true);
        BlockEntity newBE = level.getBlockEntity(pos);
        FPSCompress.LOGGER.info("New BlockEntity: {}", newBE != null ? newBE.getClass().getName() : "null");

        if (newBE instanceof PrefabBlockEntity prefabBE) {
            FPSCompress.LOGGER.info("Initializing PreFab BlockEntity");
            prefabBE.setRoomCode(roomCode);
            prefabBE.setCurrentState(MachineState.BUILDING);

            // Set room dimensions if available
            if (roomDimensions != null) {
                prefabBE.setRoomSize(roomDimensions[0], roomDimensions[1], roomDimensions[2]);
                FPSCompress.LOGGER.info("Set room dimensions to {}x{}x{}",
                    roomDimensions[0], roomDimensions[1], roomDimensions[2]);
            }

            // Try to get cached coordinates (with null checks)
            try {
                if (level.getServer() != null) {
                    RoomCoordinateCache cache = RoomCoordinateCache.get(level.getServer());
                    BlockPos roomCenter = cache.getRoomCenter(pos);
                    if (roomCenter != null) {
                        prefabBE.setRoomCenter(roomCenter);
                        FPSCompress.LOGGER.info("PreFab initialized with cached room center: {}", roomCenter);
                    } else {
                        FPSCompress.LOGGER.info("No cached coordinates available");
                    }
                } else {
                    FPSCompress.LOGGER.warn("Server is null, skipping coordinate cache");
                }
            } catch (Exception e) {
                FPSCompress.LOGGER.error("Failed to get cached coordinates (non-fatal)", e);
                // Continue anyway
            }

            prefabBE.setChanged();
            FPSCompress.LOGGER.info("PreFab BlockEntity initialized successfully");

            // Success message
            player.displayClientMessage(
                Component.literal("§aCompact Machine upgraded to PreFab!"),
                true
            );

            // Consume item (unless creative mode)
            if (!player.isCreative()) {
                context.getItemInHand().shrink(1);
            }

            FPSCompress.LOGGER.info("CM at {} upgraded to PreFab by player {}",
                                   pos, player.getName().getString());

            return InteractionResult.SUCCESS;
        }

        player.displayClientMessage(
            Component.literal("§cFailed to create PreFab BlockEntity"),
            true
        );
        return InteractionResult.FAIL;
    }

    /**
     * Get the room code from a Compact Machine BlockEntity using reflection.
     *
     * @param cmBE The Compact Machine BlockEntity
     * @return The room code string, or null if not available
     */
    @Nullable
    private String getRoomCodeFromCM(BoundCompactMachineBlockEntity cmBE) {
        // Cast to Object immediately to avoid compile-time interface check
        Object cmObj = cmBE;

        FPSCompress.LOGGER.info("=== DEBUG: Getting room code from CM ===");
        FPSCompress.LOGGER.info("CM BlockEntity class: {}", cmObj.getClass().getName());

        try {

            // List all available methods for debugging
            FPSCompress.LOGGER.info("Available methods on CM BlockEntity:");
            for (Method m : cmObj.getClass().getMethods()) {
                if (m.getName().contains("room") || m.getName().contains("Room")
                    || m.getName().contains("connected") || m.getName().contains("Connected")) {
                    FPSCompress.LOGGER.info("  - {} returns {}", m.getName(), m.getReturnType().getName());
                }
            }

            // Try to get connectedRoom method
            Method connectedRoomMethod = cmObj.getClass().getMethod("connectedRoom");
            FPSCompress.LOGGER.info("Found connectedRoom() method, return type: {}",
                                   connectedRoomMethod.getReturnType().getName());

            // Invoke the method
            Object roomResult = connectedRoomMethod.invoke(cmObj);
            FPSCompress.LOGGER.info("connectedRoom() returned: {} (type: {})",
                                   roomResult,
                                   roomResult != null ? roomResult.getClass().getName() : "null");

            // Handle both String and Optional<String> return types
            if (roomResult instanceof String roomCodeStr) {
                // Direct String return (CM 7.0.81 behavior)
                if (!roomCodeStr.isEmpty()) {
                    FPSCompress.LOGGER.info("SUCCESS: Room code is '{}'", roomCodeStr);
                    return roomCodeStr;
                } else {
                    FPSCompress.LOGGER.warn("connectedRoom() returned empty string");
                }
            } else if (roomResult instanceof Optional<?> opt) {
                // Optional return type (alternative CM API version)
                FPSCompress.LOGGER.info("Result is Optional, isPresent: {}", opt.isPresent());

                if (opt.isPresent()) {
                    Object roomKey = opt.get();
                    FPSCompress.LOGGER.info("Optional contains: {} (type: {})",
                                           roomKey, roomKey.getClass().getName());

                    String roomCodeStr = roomKey.toString();
                    FPSCompress.LOGGER.info("SUCCESS: Room code is '{}'", roomCodeStr);
                    return roomCodeStr;
                } else {
                    FPSCompress.LOGGER.warn("connectedRoom() returned empty Optional - "
                                          + "CM may not be bound to a room yet");
                }
            } else {
                FPSCompress.LOGGER.error("connectedRoom() returned unexpected type: {}",
                                        roomResult != null ? roomResult.getClass().getName() : "null");
            }
        } catch (NoSuchMethodException e) {
            FPSCompress.LOGGER.error("connectedRoom() method not found - CM API may have changed", e);
        } catch (Exception e) {
            FPSCompress.LOGGER.error("Failed to get room code from CM block", e);
            e.printStackTrace();
        }

        FPSCompress.LOGGER.error("=== DEBUG: Failed to get room code ===");
        return null;
    }

    /**
     * Get the room dimensions from a Compact Machine BlockEntity using reflection.
     * CM rooms can be non-cubic (e.g., 5x3x7).
     *
     * @param cmBE The Compact Machine BlockEntity
     * @return Array of [sizeX, sizeY, sizeZ], or null if not available
     */
    @Nullable
    private int[] getRoomDimensionsFromCM(BoundCompactMachineBlockEntity cmBE) {
        // Cast to Object to avoid compile-time interface check
        Object cmObj = cmBE;

        FPSCompress.LOGGER.info("=== DEBUG: Getting room dimensions from CM ===");

        try {
            // List methods that might contain room size info
            FPSCompress.LOGGER.info("Looking for room dimension methods:");
            for (Method m : cmObj.getClass().getMethods()) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                if (name.contains("size") || name.contains("dimension") || name.contains("bounds")) {
                    FPSCompress.LOGGER.info("  - {} returns {}", m.getName(), m.getReturnType().getName());
                }
            }

            // Strategy 1: Try roomSize() which might return a BlockPos or similar
            try {
                Method roomSizeMethod = cmObj.getClass().getMethod("roomSize");
                Object result = roomSizeMethod.invoke(cmObj);

                if (result instanceof BlockPos pos) {
                    // BlockPos used to represent dimensions
                    int[] dims = new int[]{pos.getX(), pos.getY(), pos.getZ()};
                    FPSCompress.LOGGER.info("SUCCESS: Room dimensions from BlockPos: {}x{}x{}",
                        dims[0], dims[1], dims[2]);
                    return dims;
                } else if (result instanceof Integer size) {
                    // Single integer = cubic room
                    int[] dims = new int[]{size, size, size};
                    FPSCompress.LOGGER.info("SUCCESS: Cubic room {}x{}x{}", size, size, size);
                    return dims;
                } else if (result != null) {
                    FPSCompress.LOGGER.info("roomSize() returned unexpected type: {}",
                        result.getClass().getName());
                }
            } catch (NoSuchMethodException e) {
                FPSCompress.LOGGER.debug("roomSize() method not found");
            }

            // Strategy 2: Try separate width/height/depth methods
            try {
                Method widthMethod = cmObj.getClass().getMethod("width");
                Method heightMethod = cmObj.getClass().getMethod("height");
                Method depthMethod = cmObj.getClass().getMethod("depth");

                int width = (Integer) widthMethod.invoke(cmObj);
                int height = (Integer) heightMethod.invoke(cmObj);
                int depth = (Integer) depthMethod.invoke(cmObj);

                int[] dims = new int[]{width, height, depth};
                FPSCompress.LOGGER.info("SUCCESS: Room dimensions {}x{}x{}", width, height, depth);
                return dims;
            } catch (NoSuchMethodException e) {
                FPSCompress.LOGGER.debug("width/height/depth methods not found");
            }

            // Strategy 3: Try outerBounds() and calculate from AABB
            try {
                Method boundsMethod = cmObj.getClass().getMethod("outerBounds");
                Object aabbResult = boundsMethod.invoke(cmObj);

                if (aabbResult instanceof net.minecraft.world.phys.AABB aabb) {
                    int sizeX = (int) (aabb.maxX - aabb.minX);
                    int sizeY = (int) (aabb.maxY - aabb.minY);
                    int sizeZ = (int) (aabb.maxZ - aabb.minZ);

                    int[] dims = new int[]{sizeX, sizeY, sizeZ};
                    FPSCompress.LOGGER.info("SUCCESS: Room dimensions from AABB: {}x{}x{}",
                        sizeX, sizeY, sizeZ);
                    return dims;
                }
            } catch (NoSuchMethodException e) {
                FPSCompress.LOGGER.debug("outerBounds() method not found");
            }

        } catch (Exception e) {
            FPSCompress.LOGGER.error("Failed to get room dimensions from CM block", e);
        }

        FPSCompress.LOGGER.warn("=== DEBUG: Could not determine room dimensions ===");
        return null;
    }
}
