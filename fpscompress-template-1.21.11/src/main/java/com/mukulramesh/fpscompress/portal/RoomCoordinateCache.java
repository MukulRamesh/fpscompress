package com.mukulramesh.fpscompress.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * World-persistent cache mapping Overworld CM/PreFab block positions to CM dimension room centers.
 * Populated when players enter CM rooms, used to avoid reflection lookups for PreFab blocks.
 */
public class RoomCoordinateCache extends SavedData {
    private static final String DATA_NAME = "fpscompress_room_coords";

    // Maps: Overworld block position → CM dimension room center
    private final Map<BlockPos, BlockPos> positionToCenter = new HashMap<>();

    // Reverse lookup: roomCode → CM dimension room center
    private final Map<String, BlockPos> roomCodeIndex = new HashMap<>();

    /**
     * Get the singleton instance for the given server.
     */
    public static RoomCoordinateCache get(MinecraftServer server) {
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(
                    RoomCoordinateCache::new,
                    RoomCoordinateCache::load
                ),
                DATA_NAME
            );
    }

    /**
     * Create a new empty cache.
     */
    public RoomCoordinateCache() {
        // Empty constructor for factory
    }

    /**
     * Load cache from NBT.
     */
    public static RoomCoordinateCache load(CompoundTag tag, HolderLookup.Provider provider) {
        RoomCoordinateCache cache = new RoomCoordinateCache();

        // Load position → center mappings
        ListTag positionList = tag.getList("Positions", Tag.TAG_COMPOUND);
        for (int i = 0; i < positionList.size(); i++) {
            CompoundTag entry = positionList.getCompound(i);
            BlockPos overworldPos = NbtUtils.readBlockPos(entry, "OverworldPos").orElse(null);
            BlockPos roomCenter = NbtUtils.readBlockPos(entry, "RoomCenter").orElse(null);

            if (overworldPos != null && roomCenter != null) {
                cache.positionToCenter.put(overworldPos, roomCenter);
            }
        }

        // Load roomCode → center mappings
        ListTag roomCodeList = tag.getList("RoomCodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < roomCodeList.size(); i++) {
            CompoundTag entry = roomCodeList.getCompound(i);
            String roomCode = entry.getString("RoomCode");
            BlockPos roomCenter = NbtUtils.readBlockPos(entry, "RoomCenter").orElse(null);

            if (!roomCode.isEmpty() && roomCenter != null) {
                cache.roomCodeIndex.put(roomCode, roomCenter);
            }
        }

        return cache;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        // Save position → center mappings
        ListTag positionList = new ListTag();
        for (Map.Entry<BlockPos, BlockPos> entry : positionToCenter.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("OverworldPos", NbtUtils.writeBlockPos(entry.getKey()));
            entryTag.put("RoomCenter", NbtUtils.writeBlockPos(entry.getValue()));
            positionList.add(entryTag);
        }
        tag.put("Positions", positionList);

        // Save roomCode → center mappings
        ListTag roomCodeList = new ListTag();
        for (Map.Entry<String, BlockPos> entry : roomCodeIndex.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("RoomCode", entry.getKey());
            entryTag.put("RoomCenter", NbtUtils.writeBlockPos(entry.getValue()));
            roomCodeList.add(entryTag);
        }
        tag.put("RoomCodes", roomCodeList);

        return tag;
    }

    /**
     * Get cached room center coordinates for an Overworld block position.
     * @param overworldPos The Overworld position of the CM/PreFab block
     * @return Room center in CM dimension, or null if not cached
     */
    @Nullable
    public BlockPos getRoomCenter(BlockPos overworldPos) {
        return positionToCenter.get(overworldPos);
    }

    /**
     * Store room center coordinates for an Overworld block position.
     * @param overworldPos The Overworld position of the CM/PreFab block
     * @param roomCode The CM room identifier
     * @param roomCenter The center coordinates in the CM dimension
     */
    public void setRoomCenter(BlockPos overworldPos, String roomCode, BlockPos roomCenter) {
        positionToCenter.put(overworldPos, roomCenter);
        roomCodeIndex.put(roomCode, roomCenter);
        setDirty();
    }

    /**
     * Get cached room center by room code (reverse lookup).
     * @param roomCode The CM room identifier
     * @return Room center in CM dimension, or null if not cached
     */
    @Nullable
    public BlockPos getRoomCenterByRoomCode(String roomCode) {
        return roomCodeIndex.get(roomCode);
    }

    /**
     * Clear cached coordinates for a specific Overworld position.
     * Called when a CM/PreFab block is broken.
     * @param overworldPos The Overworld position to clear
     */
    public void clearCache(BlockPos overworldPos) {
        BlockPos removed = positionToCenter.remove(overworldPos);
        if (removed != null) {
            setDirty();
        }
    }

    /**
     * Clear all cached coordinates (for debugging/admin commands).
     */
    public void clearAll() {
        positionToCenter.clear();
        roomCodeIndex.clear();
        setDirty();
    }

    /**
     * Get the number of cached positions (for diagnostics).
     */
    public int size() {
        return positionToCenter.size();
    }
}
