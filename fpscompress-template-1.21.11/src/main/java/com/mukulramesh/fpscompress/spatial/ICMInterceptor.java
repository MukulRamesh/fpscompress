package com.mukulramesh.fpscompress.spatial;

import net.minecraft.server.level.ServerLevel;

/**
 * Interface for Dev 3: Spatial & Dimension Manager (Chunk Loading Control)
 *
 * This interface handles chunk loading/unloading in the Compact Machines dimension
 * and controls whether resources route to the physical factory or virtual buffers.
 *
 * RESPONSIBILITIES:
 * - Load/unload chunks in the CM dimension for a specific room
 * - Toggle routing state (physical vs virtual)
 * - Use NeoForge's TicketHelper for chunk force-loading
 *
 * IMPLEMENTATION NOTES:
 * - Chunk loading should use a 3x3 area (from CLAUDE.md)
 * - When routing is true, resources go to virtual buffers (CACHED mode)
 * - When routing is false, resources go to physical blocks (BUILDING/HALTED mode)
 * - Must integrate with Compact Machines' dimension API
 *
 * @author Dev 3 - Spatial Manager Team
 */
public interface ICMInterceptor {

    /**
     * Set the chunk loading state for a specific room in the CM dimension.
     *
     * When loaded = true:
     * - The 3x3 chunk area around the room is force-loaded
     * - The factory interior is physically ticking
     * - Players can enter and interact with the factory
     *
     * When loaded = false:
     * - Chunks are unloaded to save TPS
     * - The factory runs in math-only CACHED mode
     * - Players cannot enter (portal should be blocked)
     *
     * @param dimension The Compact Machines ServerLevel
     * @param roomCode The room identifier
     * @param loaded true to load chunks, false to unload
     */
    void setRoomChunkState(ServerLevel dimension, String roomCode, boolean loaded);

    /**
     * Set the routing state for resource flow.
     *
     * When routingToVirtual = true:
     * - Resources inserted into the Overworld block go to virtual buffers
     * - Resources extracted from the Overworld block come from virtual buffers
     * - The physical factory interior is not accessed
     *
     * When routingToVirtual = false:
     * - Resources flow normally through the portal to physical blocks
     * - Standard Compact Machines behavior
     *
     * @param routingToVirtual true for virtual routing (CACHED mode), false for physical
     */
    void setRoutingState(boolean routingToVirtual);

    /**
     * Check if chunks are currently loaded for a room.
     *
     * @param dimension The Compact Machines ServerLevel
     * @param roomCode The room identifier
     * @return true if chunks are loaded, false otherwise
     */
    boolean areChunksLoaded(ServerLevel dimension, String roomCode);

    /**
     * Check if routing is currently set to virtual buffers.
     *
     * @return true if routing to virtual, false if routing to physical
     */
    boolean isRoutingToVirtual();
}
