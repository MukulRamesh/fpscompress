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
        // Try reflection first (fast), fallback to wall scanning (works always)
        int[] roomDimensions = getRoomDimensionsFromCM(cmBE);
        if (roomDimensions == null) {
            FPSCompress.LOGGER.info("Reflection failed, falling back to wall scanning for room dimensions");
            roomDimensions = getRoomDimensionsViaWallScanning(level, context.getClickedPos(), roomCode);
            if (roomDimensions == null) {
                FPSCompress.LOGGER.warn("Wall scanning also failed - PreFab will have no dimensions");
            } else {
                FPSCompress.LOGGER.info("Wall scanning succeeded: {}x{}x{}",
                    roomDimensions[0], roomDimensions[1], roomDimensions[2]);
            }
        } else {
            FPSCompress.LOGGER.info("Got room dimensions via reflection: {}x{}x{}",
                roomDimensions[0], roomDimensions[1], roomDimensions[2]);
        }

        // Replace block: CM → PreFab and initialize
        return replaceAndInitializePreFab(context, level, player, roomCode, roomDimensions);
    }

    /**
     * Replace CM block with PreFab block and initialize the BlockEntity.
     *
     * @param context The use context
     * @param level The level
     * @param player The player
     * @param roomCode The room code
     * @param roomDimensions Room dimensions [x, y, z] or null
     * @return InteractionResult indicating success or failure
     */
    private InteractionResult replaceAndInitializePreFab(UseOnContext context, Level level,
                                                        Player player, String roomCode,
                                                        int[] roomDimensions) {
        BlockPos pos = context.getClickedPos();
        FPSCompress.LOGGER.info("Replacing block at {} with PreFab", pos);

        try {
            // CRITICAL: Remove old BlockEntity first to prevent stale cache
            level.removeBlockEntity(pos);

            // Use flag 3: notify neighbors + send to clients
            boolean success = level.setBlock(pos,
                FPSCompress.PREFAB_BLOCK.get().defaultBlockState(), 3);
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

        if (!(newBE instanceof PrefabBlockEntity prefabBE)) {
            player.displayClientMessage(
                Component.literal("§cFailed to create PreFab BlockEntity"),
                true
            );
            return InteractionResult.FAIL;
        }

        prefabBE.setRoomCode(roomCode);
        prefabBE.setCurrentState(MachineState.BUILDING);

        // Set room dimensions if available
        if (roomDimensions != null) {
            prefabBE.setRoomSize(roomDimensions[0], roomDimensions[1], roomDimensions[2]);
            FPSCompress.LOGGER.debug("[UPGRADE] Set room dimensions: {}x{}x{} at tick {}",
                roomDimensions[0], roomDimensions[1], roomDimensions[2], level.getGameTime());
        }

        // Try to get cached coordinates
        try {
            if (level.getServer() != null) {
                RoomCoordinateCache cache = RoomCoordinateCache.get(level.getServer());
                BlockPos roomCenter = cache.getRoomCenter(pos);
                if (roomCenter != null) {
                    prefabBE.setRoomCenter(roomCenter);
                }
            }
        } catch (Exception e) {
            FPSCompress.LOGGER.error("Failed to get cached coordinates (non-fatal)", e);
        }

        prefabBE.setChanged();

        // Force immediate NBT save and verify
        net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
        prefabBE.saveAdditional(nbt, level.registryAccess());
        level.getChunkAt(pos).setUnsaved(true);

        player.displayClientMessage(
            Component.literal("§aCompact Machine upgraded to PreFab!"),
            true
        );

        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
        }

        FPSCompress.LOGGER.info("CM at {} upgraded to PreFab", pos);
        return InteractionResult.SUCCESS;
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

    /**
     * Get room dimensions by scanning for CM wall blocks (fallback method).
     * Uses the same wall-scanning logic as FabricatorBlockEntity.
     *
     * @param level Server level
     * @param cmPos Position of the CM block in Overworld
     * @param roomCode Room code to look up center coordinates
     * @return Array of [sizeX, sizeY, sizeZ], or null if scanning failed
     */
    @Nullable
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "PZLA_PREFER_ZERO_LENGTH_ARRAYS",
        justification = "Null indicates failure (no room found), zero-length array would be ambiguous"
    )
    private int[] getRoomDimensionsViaWallScanning(Level level, BlockPos cmPos, String roomCode) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }

        try {
            // Get CM dimension
            net.minecraft.server.MinecraftServer server = serverLevel.getServer();
            net.minecraft.server.level.ServerLevel cmLevel = server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse("compactmachines:compact_world")
                )
            );

            if (cmLevel == null) {
                FPSCompress.LOGGER.error("Cannot access CM dimension for wall scanning");
                return null;
            }

            // Get room center from cache
            RoomCoordinateCache cache = RoomCoordinateCache.get(server);
            BlockPos roomCenter = cache.getRoomCenterByRoomCode(roomCode);
            if (roomCenter == null) {
                FPSCompress.LOGGER.error("Room center not found for roomCode: {}", roomCode);
                return null;
            }

            // Find wall blocks
            String[] wallBlockIds = {
                "compactmachines:solid_wall",
                "compactmachines:wall",
                "compactmachines:machine_wall"
            };

            net.minecraft.world.level.block.Block wallBlock = null;
            for (String blockId : wallBlockIds) {
                net.minecraft.world.level.block.Block candidate =
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                        net.minecraft.resources.ResourceLocation.parse(blockId));
                if (!candidate.equals(net.minecraft.world.level.block.Blocks.AIR)) {
                    wallBlock = candidate;
                    break;
                }
            }

            if (wallBlock == null) {
                FPSCompress.LOGGER.error("No valid CM wall block found in registry");
                return null;
            }

            // Scan for walls in each direction
            int minX = scanForWall(cmLevel, roomCenter, net.minecraft.core.Direction.WEST, wallBlock);
            int maxX = scanForWall(cmLevel, roomCenter, net.minecraft.core.Direction.EAST, wallBlock);
            int minY = scanForWall(cmLevel, roomCenter, net.minecraft.core.Direction.DOWN, wallBlock);
            int maxY = scanForWall(cmLevel, roomCenter, net.minecraft.core.Direction.UP, wallBlock);
            int minZ = scanForWall(cmLevel, roomCenter, net.minecraft.core.Direction.NORTH, wallBlock);
            int maxZ = scanForWall(cmLevel, roomCenter, net.minecraft.core.Direction.SOUTH, wallBlock);

            // Calculate interior dimensions (exclude walls)
            int sizeX = maxX - minX - 1; // -1 to exclude both walls
            int sizeY = maxY - minY - 1;
            int sizeZ = maxZ - minZ - 1;

            return new int[]{sizeX, sizeY, sizeZ};

        } catch (Exception e) {
            FPSCompress.LOGGER.error("Wall scanning failed", e);
            return null;
        }
    }

    /**
     * Scan outward from center until CM wall found.
     *
     * @param cmLevel CM dimension level
     * @param center Center position
     * @param dir Direction to scan
     * @param wallBlock Wall block to search for
     * @return Coordinate of wall in direction's axis
     * @throws IllegalStateException if wall not found
     */
    private int scanForWall(net.minecraft.server.level.ServerLevel cmLevel, BlockPos center,
                           net.minecraft.core.Direction dir,
                           net.minecraft.world.level.block.Block wallBlock) {
        BlockPos.MutableBlockPos pos = center.mutable();
        int maxDistance = 20;

        for (int i = 0; i < maxDistance; i++) {
            pos.move(dir);
            net.minecraft.world.level.block.state.BlockState state = cmLevel.getBlockState(pos);

            if (state.is(wallBlock)) {
                net.minecraft.core.Direction.Axis axis = dir.getAxis();
                if (axis == net.minecraft.core.Direction.Axis.X) {
                    return pos.getX();
                } else if (axis == net.minecraft.core.Direction.Axis.Y) {
                    return pos.getY();
                } else {
                    return pos.getZ();
                }
            }
        }

        throw new IllegalStateException("Wall not found in direction " + dir + " within " + maxDistance + " blocks");
    }
}
