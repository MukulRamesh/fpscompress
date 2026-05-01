package com.mukulramesh.fpscompress.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Dev 2 Implementation: Chunk Loading Control and Resource Routing Interceptor
 *
 * This class manages chunk loading/unloading in the Compact Machines dimension
 * and controls whether resources route to physical blocks or virtual buffers.
 *
 * RESPONSIBILITIES:
 * 1. Force-load/unload 3x3 chunk areas around factory rooms
 * 2. Toggle routing mode (physical vs virtual)
 * 3. Track chunk loading state per room
 *
 * INTEGRATION POINTS:
 * - Used by FactoryIntegrator to transition between BUILDING/SIMULATING/CACHED states
 * - Chunk loading affects whether the factory interior physically ticks
 * - Routing state determines where IItemHandler/IFluidHandler/IEnergyStorage go
 *
 * PERFORMANCE:
 * - Uses NeoForge's chunk ticket system (TicketType.PORTAL equivalent)
 * - Minimal overhead - only affects registered rooms
 * - Chunk unloading saves TPS when many factories are in CACHED mode
 *
 * @author Dev 2 - Spatial Manager Team
 */
public class CMInterceptorImpl implements ICMInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CMInterceptorImpl.class);

    /**
     * Custom ticket type for FPSCompress factory chunk loading.
     * This is similar to how portals keep chunks loaded.
     */
    private static final TicketType<BlockPos> FACTORY_TICKET =
            TicketType.create("fpscompress:factory", BlockPos::compareTo, 600);

    /**
     * Tracks which rooms have chunks currently loaded.
     * Key: roomCode, Value: center BlockPos used for ticket
     */
    private final Map<String, BlockPos> loadedRooms = new HashMap<>();

    /**
     * Tracks which rooms are currently routing to virtual buffers.
     * Key: roomCode, Value: true if routing to virtual, false if physical
     */
    private final Map<String, Boolean> routingStates = new HashMap<>();

    /**
     * Tracks the set of ChunkPos being loaded for each room (for cleanup).
     * Key: roomCode, Value: Set of ChunkPos
     */
    private final Map<String, Set<ChunkPos>> roomChunks = new HashMap<>();

    /**
     * Current routing state for the managed room.
     * Default is false (physical routing).
     */
    private boolean currentRoutingState = false;

    @Override
    public void setRoomChunkState(ServerLevel dimension, String roomCode, boolean loaded) {
        if (dimension == null || roomCode == null) {
            LOGGER.error("Cannot set chunk state: dimension or roomCode is null");
            return;
        }

        if (loaded) {
            loadChunksForRoom(dimension, roomCode);
        } else {
            unloadChunksForRoom(dimension, roomCode);
        }
    }

    @Override
    public void setRoutingState(boolean routingToVirtual) {
        boolean previousState = this.currentRoutingState;
        this.currentRoutingState = routingToVirtual;

        if (previousState != routingToVirtual) {
            LOGGER.debug("Routing state changed: {} -> {} (virtual={})",
                    previousState ? "VIRTUAL" : "PHYSICAL",
                    routingToVirtual ? "VIRTUAL" : "PHYSICAL",
                    routingToVirtual);
        }
    }

    @Override
    public boolean areChunksLoaded(ServerLevel dimension, String roomCode) {
        return loadedRooms.containsKey(roomCode);
    }

    @Override
    public boolean isRoutingToVirtual() {
        return currentRoutingState;
    }

    /**
     * Loads a 3x3 chunk area around the factory room.
     *
     * ALGORITHM:
     * 1. Calculate room center position from Compact Machines room code
     * 2. Determine 3x3 chunk grid centered on that position
     * 3. Force-load chunks using ServerLevel's chunk ticket system
     * 4. Track loaded chunks for cleanup later
     *
     * NOTE: This method assumes Compact Machines uses a predictable coordinate
     * scheme for rooms. You may need to query CM's API to get the actual room bounds.
     *
     * @param dimension The Compact Machines ServerLevel
     * @param roomCode The room identifier (e.g., "room_0", "room_1", etc.)
     */
    private void loadChunksForRoom(ServerLevel dimension, String roomCode) {
        if (loadedRooms.containsKey(roomCode)) {
            LOGGER.debug("Room {} already has chunks loaded", roomCode);
            return;
        }

        // Query Compact Machines API to get actual room center coordinates
        BlockPos roomCenter = getRoomCenterFromCode(dimension, roomCode);

        if (roomCenter == null) {
            LOGGER.error("Could not determine room center for room code: {}", roomCode);
            return;
        }

        ServerChunkCache chunkSource = dimension.getChunkSource();
        ChunkPos centerChunk = new ChunkPos(roomCenter);

        Set<ChunkPos> loadedChunkPositions = new HashSet<>();

        // Load 3x3 chunk area centered on the room
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos chunkPos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);

                // Add a chunk ticket to force-load this chunk
                // The ticket level 2 ensures the chunk is fully loaded and ticking
                chunkSource.addRegionTicket(FACTORY_TICKET, chunkPos, 2, roomCenter);

                loadedChunkPositions.add(chunkPos);

                LOGGER.debug("Loaded chunk {} for room {}", chunkPos, roomCode);
            }
        }

        loadedRooms.put(roomCode, roomCenter);
        roomChunks.put(roomCode, loadedChunkPositions);

        LOGGER.info("Successfully loaded 3x3 chunk area for room {} at center {}",
                roomCode, roomCenter);
    }

    /**
     * Unloads all chunks associated with a factory room.
     *
     * ALGORITHM:
     * 1. Look up tracked chunks for this room
     * 2. Remove chunk tickets for each chunk
     * 3. Clean up tracking data structures
     *
     * @param dimension The Compact Machines ServerLevel
     * @param roomCode The room identifier
     */
    private void unloadChunksForRoom(ServerLevel dimension, String roomCode) {
        if (!loadedRooms.containsKey(roomCode)) {
            LOGGER.debug("Room {} does not have chunks loaded (already unloaded?)", roomCode);
            return;
        }

        BlockPos roomCenter = loadedRooms.get(roomCode);
        Set<ChunkPos> chunksToUnload = roomChunks.get(roomCode);

        if (chunksToUnload == null) {
            LOGGER.warn("Room {} was in loadedRooms but had no chunk tracking data", roomCode);
            loadedRooms.remove(roomCode);
            return;
        }

        ServerChunkCache chunkSource = dimension.getChunkSource();

        // Remove chunk tickets to allow chunks to unload
        for (ChunkPos chunkPos : chunksToUnload) {
            chunkSource.removeRegionTicket(FACTORY_TICKET, chunkPos, 2, roomCenter);
            LOGGER.debug("Unloaded chunk {} for room {}", chunkPos, roomCode);
        }

        loadedRooms.remove(roomCode);
        roomChunks.remove(roomCode);

        LOGGER.info("Successfully unloaded chunks for room {} at center {}",
                roomCode, roomCenter);
    }

    /**
     * Calculates the center BlockPos of a room from its room code.
     *
     * COMPACT MACHINES INTEGRATION:
     * Compact Machines does not expose a public API for accessing room data in version 7.0.81.
     * This implementation uses reflection to access internal CM classes.
     *
     * FAILURE MODES:
     * - If reflection fails, this method returns null and logs an ERROR
     * - Chunk loading will fail if room coordinates cannot be determined
     * - This is intentionally NOT silent - failures are explicit
     *
     * INTEGRATION OPTIONS:
     * 1. Reflection (current): Attempts to access CM's internal RoomRegistrarData
     * 2. Future API: When CM exposes public API, replace with direct calls
     *
     * @param dimension The Compact Machines ServerLevel
     * @param roomCode The room identifier from CM
     * @return The center BlockPos of the room, or null if not found (with ERROR logged)
     */
    private BlockPos getRoomCenterFromCode(ServerLevel dimension, String roomCode) {
        // Try cache lookup first (for PreFabs)
        com.mukulramesh.fpscompress.portal.RoomCoordinateCache cache =
            com.mukulramesh.fpscompress.portal.RoomCoordinateCache.get(dimension.getServer());
        BlockPos cached = cache.getRoomCenterByRoomCode(roomCode);
        if (cached != null) {
            LOGGER.debug("Using cached coordinates for room {}: {}", roomCode, cached);
            return cached;
        }

        // Fall back to reflection for regular CMs
        BlockPos reflectionResult = getRoomCenterViaReflection(dimension, roomCode);

        if (reflectionResult != null) {
            return reflectionResult;
        }

        // Both cache and reflection failed - log ERROR and return null
        // This causes chunk loading to fail, which is the correct behavior
        LOGGER.error("FAILED to resolve room coordinates for '{}'. "
                + "Chunk loading will not work for this room. "
                + "This may be due to: "
                + "(1) Room does not exist in CM registrar, "
                + "(2) CM internal API changed, "
                + "(3) Reflection access denied, or "
                + "(4) Room coordinates not cached (player has not entered room yet).",
                roomCode);

        return null;
    }

    /**
     * Attempts to get room center using reflection to access CM's internal classes.
     *
     * IMPORTANT: This uses reflection because CM 7.0.81 doesn't expose a public API.
     * If this fails, chunk loading for the factory will NOT work - this is intentional.
     *
     * REFLECTION SAFETY:
     * - All reflection failures are caught and logged at DEBUG level
     * - Returns null on any failure (which triggers ERROR in caller)
     * - May break with CM updates, but will fail explicitly, not silently
     *
     * @param dimension The CM dimension
     * @param roomCode The room code
     * @return BlockPos of room center, or null if reflection fails (with DEBUG log)
     */
    private BlockPos getRoomCenterViaReflection(ServerLevel dimension, String roomCode) {
        try {
            // Try to access CM's RoomRegistrarData through reflection
            Class<?> roomRegistrarClass = Class.forName("dev.compactmods.machines.room.RoomRegistrarData");
            LOGGER.debug("Found RoomRegistrarData class");

            // Get the data storage and figure out what Factory type it needs
            Object dataStorage = dimension.getServer().overworld().getDataStorage();

            // Find computeIfAbsent method and get its Factory parameter type
            Method computeMethod = null;
            Class<?> factoryClass = null;
            for (Method m : dataStorage.getClass().getMethods()) {
                if (m.getName().equals("computeIfAbsent") && m.getParameterCount() == 2) {
                    computeMethod = m;
                    factoryClass = m.getParameterTypes()[0];
                    LOGGER.debug("Found Factory type: {}", factoryClass.getName());
                    break;
                }
            }

            if (factoryClass == null || computeMethod == null) {
                LOGGER.debug("Could not find computeIfAbsent method or Factory type");
                return null;
            }

            // Create a Factory proxy that creates RoomRegistrarData instances
            Object factory = java.lang.reflect.Proxy.newProxyInstance(
                    factoryClass.getClassLoader(),
                    new Class<?>[]{factoryClass},
                    (proxy, method, args) -> {
                        try {
                            // Factory has methods: create() and parse(CompoundTag)
                            if (method.getName().equals("parse") && args != null && args.length > 0) {
                                // Load from NBT
                                Method loadMethod = roomRegistrarClass.getMethod("load",
                                        Class.forName("net.minecraft.nbt.CompoundTag"),
                                        Class.forName("net.minecraft.core.HolderLookup$Provider"));
                                return loadMethod.invoke(null, args[0], dimension.registryAccess());
                            } else {
                                // Create new instance
                                return roomRegistrarClass
                                        .getConstructor(net.minecraft.server.MinecraftServer.class)
                                        .newInstance(dimension.getServer());
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Failed in Factory proxy: {}", e.getMessage());
                            return null;
                        }
                    }
            );

            // Use the computeMethod we found earlier
            Object savedData = computeMethod.invoke(dataStorage, factory, "compactmachines_rooms");

            if (savedData == null) {
                LOGGER.debug("Could not load CM room registrar data");
                return null;
            }

            // Call get(String roomCode) method on registrar
            Method getRoomMethod = roomRegistrarClass.getMethod("get", String.class);
            Object optionalRoomNode = getRoomMethod.invoke(savedData, roomCode);

            // Check if Optional is present
            Method isPresentMethod = optionalRoomNode.getClass().getMethod("isPresent");
            if (!(Boolean) isPresentMethod.invoke(optionalRoomNode)) {
                LOGGER.debug("Room code {} not found in CM registrar", roomCode);
                return null;
            }

            // Get the RoomRegistrationNode from Optional
            Method getOptionalMethod = optionalRoomNode.getClass().getMethod("get");
            Object roomNode = getOptionalMethod.invoke(optionalRoomNode);

            // Call outerBounds() to get AABB
            Method outerBoundsMethod = roomNode.getClass().getMethod("outerBounds");
            Object aabb = outerBoundsMethod.invoke(roomNode);

            // Extract center from AABB using public fields
            Class<?> aabbClass = Class.forName("net.minecraft.world.phys.AABB");
            double minX = aabbClass.getField("minX").getDouble(aabb);
            double maxX = aabbClass.getField("maxX").getDouble(aabb);
            double minY = aabbClass.getField("minY").getDouble(aabb);
            double maxY = aabbClass.getField("maxY").getDouble(aabb);
            double minZ = aabbClass.getField("minZ").getDouble(aabb);
            double maxZ = aabbClass.getField("maxZ").getDouble(aabb);

            int centerX = (int) ((minX + maxX) / 2.0);
            int centerY = (int) ((minY + maxY) / 2.0);
            int centerZ = (int) ((minZ + maxZ) / 2.0);

            BlockPos center = new BlockPos(centerX, centerY, centerZ);
            LOGGER.info("Successfully resolved room {} via reflection to center: {}", roomCode, center);

            return center;

        } catch (Exception e) {
            LOGGER.debug(
                "Reflection-based room lookup failed for '{}': {} "
                + "(this is expected if CM API changed or room doesn't exist)",
                roomCode, e.getMessage());
            // Return null - caller will log ERROR and fail explicitly
            return null;
        }
    }


    /**
     * Sets the routing state for a specific room.
     * This allows per-room routing control.
     *
     * @param roomCode The room identifier
     * @param routingToVirtual true for virtual routing, false for physical
     */
    public void setRoomRoutingState(String roomCode, boolean routingToVirtual) {
        boolean previousState = routingStates.getOrDefault(roomCode, false);
        routingStates.put(roomCode, routingToVirtual);

        LOGGER.debug("Room {} routing state: {} -> {}",
                roomCode,
                previousState ? "VIRTUAL" : "PHYSICAL",
                routingToVirtual ? "VIRTUAL" : "PHYSICAL");
    }

    /**
     * Gets the routing state for a specific room.
     *
     * @param roomCode The room identifier
     * @return true if routing to virtual, false if physical (default)
     */
    public boolean isRoomRoutingToVirtual(String roomCode) {
        return routingStates.getOrDefault(roomCode, false);
    }

    /**
     * Cleans up all chunk loading tickets and routing state.
     * Should be called when the mod is shutting down or when clearing state.
     *
     * @param dimension The Compact Machines ServerLevel
     */
    public void cleanup(ServerLevel dimension) {
        LOGGER.info("Cleaning up {} loaded rooms", loadedRooms.size());

        // Unload all tracked rooms
        for (String roomCode : new HashSet<>(loadedRooms.keySet())) {
            unloadChunksForRoom(dimension, roomCode);
        }

        routingStates.clear();

        LOGGER.info("Cleanup complete");
    }

    /**
     * Gets diagnostic information about current chunk loading state.
     * Useful for debugging and monitoring.
     *
     * @return A formatted string with current state information
     */
    public String getDiagnostics() {
        return String.format(
            "CMInterceptor Diagnostics:%n"
            + "  Loaded Rooms: %d%n"
            + "  Current Routing: %s%n"
            + "  Rooms with Virtual Routing: %d",
            loadedRooms.size(),
            currentRoutingState ? "VIRTUAL" : "PHYSICAL",
            (int) routingStates.values().stream().filter(b -> b).count()
        );
    }
}
