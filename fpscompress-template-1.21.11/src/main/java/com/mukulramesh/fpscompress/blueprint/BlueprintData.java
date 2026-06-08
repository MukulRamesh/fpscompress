package com.mukulramesh.fpscompress.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data structure for PreFab Blueprint items.
 * Stores scanned factory configuration including blocks, items, and cached production rates.
 *
 * <p>Schema Evolution:
 * <ul>
 *   <li>v1: Simple ID + count for resources (no NBT tracking)</li>
 *   <li>v2: ID + count + optional NBT requirements</li>
 * </ul>
 */
public final class BlueprintData {
    private static final int SCHEMA_VERSION = 2;

    private final List<ResourceRequirement> blockResources;
    private final List<ResourceRequirement> itemResources;
    private final Map<UUID, List<ResourceRate>> cachedRates;
    private final int roomSizeX;
    private final int roomSizeY;
    private final int roomSizeZ;
    private final String sourcePrefabName;

    /**
     * Create blueprint data with all fields.
     *
     * @param blockResources List of block resource requirements
     * @param itemResources List of item resource requirements
     * @param cachedRates Map of UUID to cached production rates
     * @param roomSizeX Room width in blocks
     * @param roomSizeY Room height in blocks
     * @param roomSizeZ Room depth in blocks
     * @param sourcePrefabName Optional custom name (null for default)
     */
    public BlueprintData(List<ResourceRequirement> blockResources,
                        List<ResourceRequirement> itemResources,
                        Map<UUID, List<ResourceRate>> cachedRates,
                        int roomSizeX,
                        int roomSizeY,
                        int roomSizeZ,
                        String sourcePrefabName) {
        this.blockResources = List.copyOf(blockResources);
        this.itemResources = List.copyOf(itemResources);
        this.cachedRates = new HashMap<>(cachedRates);
        this.roomSizeX = roomSizeX;
        this.roomSizeY = roomSizeY;
        this.roomSizeZ = roomSizeZ;
        this.sourcePrefabName = sourcePrefabName;
    }

    /**
     * Serialize blueprint data to NBT (schema v2).
     *
     * @return CompoundTag containing all blueprint data
     */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();

        tag.putInt("schemaVersion", SCHEMA_VERSION);

        // Save block resources (v2 format with optional NBT)
        ListTag blockList = new ListTag();
        for (ResourceRequirement req : blockResources) {
            blockList.add(req.toNBT());
        }
        tag.put("blockResources", blockList);

        // Save item resources (v2 format with optional NBT)
        ListTag itemList = new ListTag();
        for (ResourceRequirement req : itemResources) {
            itemList.add(req.toNBT());
        }
        tag.put("itemResources", itemList);

        // Save cached rates (UUID-based format from PreFab)
        ListTag ratesList = new ListTag();
        for (Map.Entry<UUID, List<ResourceRate>> uuidEntry : cachedRates.entrySet()) {
            CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID("uuid", uuidEntry.getKey());

            ListTag resourceRatesList = new ListTag();
            for (ResourceRate rate : uuidEntry.getValue()) {
                resourceRatesList.add(rate.toNBT());
            }
            uuidTag.put("rates", resourceRatesList);
            ratesList.add(uuidTag);
        }
        tag.put("cachedRates", ratesList);

        // Save room dimensions
        tag.putInt("roomSizeX", roomSizeX);
        tag.putInt("roomSizeY", roomSizeY);
        tag.putInt("roomSizeZ", roomSizeZ);

        // Save custom name (optional)
        if (sourcePrefabName != null) {
            tag.putString("sourcePrefabName", sourcePrefabName);
        }

        return tag;
    }

    /**
     * Deserialize blueprint data from NBT.
     * Supports both v1 (no NBT) and v2 (with NBT) schemas.
     *
     * @param tag CompoundTag containing blueprint data
     * @return BlueprintData instance, or empty blueprint if tag is null/invalid
     */
    public static BlueprintData fromNBT(CompoundTag tag) {
        if (tag == null) {
            return createEmpty();
        }

        int schemaVersion = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 1;

        List<ResourceRequirement> blockResources = new ArrayList<>();
        List<ResourceRequirement> itemResources = new ArrayList<>();
        Map<UUID, List<ResourceRate>> cachedRates = new HashMap<>();
        int roomSizeX = 0;
        int roomSizeY = 0;
        int roomSizeZ = 0;
        String sourcePrefabName = null;

        // Load block resources (v1 or v2)
        if (tag.contains("blockResources")) {
            ListTag blockList = tag.getList("blockResources", Tag.TAG_COMPOUND);
            for (int i = 0; i < blockList.size(); i++) {
                CompoundTag entry = blockList.getCompound(i);
                if (schemaVersion >= 2 && entry.contains("nbt")) {
                    // v2 format: has optional NBT field
                    blockResources.add(ResourceRequirement.fromNBT(entry));
                } else {
                    // v1 format: only ID + count, migrate to v2 with null NBT
                    String id = entry.getString("id");
                    long count = entry.getLong("count");
                    blockResources.add(new ResourceRequirement(id, count, null));
                }
            }
        }

        // Load item resources (v1 or v2)
        if (tag.contains("itemResources")) {
            ListTag itemList = tag.getList("itemResources", Tag.TAG_COMPOUND);
            for (int i = 0; i < itemList.size(); i++) {
                CompoundTag entry = itemList.getCompound(i);
                if (schemaVersion >= 2 && entry.contains("nbt")) {
                    // v2 format: has optional NBT field
                    itemResources.add(ResourceRequirement.fromNBT(entry));
                } else {
                    // v1 format: only ID + count, migrate to v2 with null NBT
                    String id = entry.getString("id");
                    long count = entry.getLong("count");
                    itemResources.add(new ResourceRequirement(id, count, null));
                }
            }
        }

        // Load cached rates (unchanged between v1/v2)
        if (tag.contains("cachedRates")) {
            ListTag ratesList = tag.getList("cachedRates", Tag.TAG_COMPOUND);
            for (int i = 0; i < ratesList.size(); i++) {
                CompoundTag uuidTag = ratesList.getCompound(i);
                UUID uuid = uuidTag.getUUID("uuid");

                List<ResourceRate> rates = new ArrayList<>();
                if (uuidTag.contains("rates")) {
                    ListTag resourceRatesList = uuidTag.getList("rates", Tag.TAG_COMPOUND);
                    for (int j = 0; j < resourceRatesList.size(); j++) {
                        CompoundTag rateTag = resourceRatesList.getCompound(j);
                        rates.add(ResourceRate.fromNBT(rateTag));
                    }
                }
                cachedRates.put(uuid, rates);
            }
        }

        // Load room dimensions
        if (tag.contains("roomSizeX")) {
            roomSizeX = tag.getInt("roomSizeX");
        }
        if (tag.contains("roomSizeY")) {
            roomSizeY = tag.getInt("roomSizeY");
        }
        if (tag.contains("roomSizeZ")) {
            roomSizeZ = tag.getInt("roomSizeZ");
        }

        // Load custom name
        if (tag.contains("sourcePrefabName")) {
            sourcePrefabName = tag.getString("sourcePrefabName");
        }

        return new BlueprintData(blockResources, itemResources, cachedRates,
                roomSizeX, roomSizeY, roomSizeZ, sourcePrefabName);
    }

    /**
     * Create empty blueprint (no data).
     *
     * @return Empty BlueprintData instance
     */
    public static BlueprintData createEmpty() {
        return new BlueprintData(new ArrayList<>(), new ArrayList<>(), new HashMap<>(),
                0, 0, 0, null);
    }

    public List<ResourceRequirement> getBlockResources() {
        return List.copyOf(blockResources);
    }

    public List<ResourceRequirement> getItemResources() {
        return List.copyOf(itemResources);
    }

    public Map<UUID, List<ResourceRate>> getCachedRates() {
        return new HashMap<>(cachedRates);
    }

    public int getRoomSizeX() {
        return roomSizeX;
    }

    public int getRoomSizeY() {
        return roomSizeY;
    }

    public int getRoomSizeZ() {
        return roomSizeZ;
    }

    public String getSourcePrefabName() {
        return sourcePrefabName;
    }

    public boolean isEmpty() {
        return blockResources.isEmpty() && itemResources.isEmpty() && cachedRates.isEmpty();
    }

    /**
     * Get total number of unique block types.
     *
     * @return Block type count
     */
    public int getBlockTypeCount() {
        return blockResources.size();
    }

    /**
     * Get total number of unique item types.
     *
     * @return Item type count
     */
    public int getItemTypeCount() {
        return itemResources.size();
    }

    /**
     * Resource requirement with optional NBT constraints.
     * Represents a single resource type needed for printing a blueprint.
     *
     * @param id Resource ID (e.g., "minecraft:stone")
     * @param count Number of items/blocks required
     * @param nbt Required NBT fields (null = no NBT requirements, any item of this type accepted)
     */
    public record ResourceRequirement(String id, long count, CompoundTag nbt) {
        /**
         * Canonical constructor with defensive copy of NBT.
         * Ensures immutability by copying the CompoundTag.
         */
        public ResourceRequirement {
            nbt = nbt != null ? nbt.copy() : null;
        }
        /**
         * Serialize requirement to NBT.
         *
         * @return CompoundTag containing requirement data
         */
        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            tag.putLong("count", count);
            if (nbt != null && !nbt.isEmpty()) {
                tag.put("nbt", nbt); // Already copied in canonical constructor
            }
            return tag;
        }

        /**
         * Deserialize requirement from NBT.
         *
         * @param tag CompoundTag containing requirement data
         * @return ResourceRequirement instance
         */
        public static ResourceRequirement fromNBT(CompoundTag tag) {
            String id = tag.getString("id");
            long count = tag.getLong("count");
            CompoundTag nbt = tag.contains("nbt", Tag.TAG_COMPOUND)
                ? tag.getCompound("nbt")  // Canonical constructor will copy
                : null;
            return new ResourceRequirement(id, count, nbt);
        }
    }

    /**
     * Represents a single resource production rate.
     */
    public static final class ResourceRate {
        private final String resourceId;
        private final double rate;

        /**
         * Create resource rate.
         *
         * @param resourceId Resource identifier (e.g., "minecraft:iron_ingot")
         * @param rate Production rate per tick (positive = output, negative = input)
         */
        public ResourceRate(String resourceId, double rate) {
            this.resourceId = resourceId;
            this.rate = rate;
        }

        /**
         * Serialize rate to NBT.
         *
         * @return CompoundTag containing rate data
         */
        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", resourceId);
            tag.putDouble("rate", rate);
            return tag;
        }

        /**
         * Deserialize rate from NBT.
         *
         * @param tag CompoundTag containing rate data
         * @return ResourceRate instance
         */
        public static ResourceRate fromNBT(CompoundTag tag) {
            String resourceId = tag.getString("id");
            double rate = tag.getDouble("rate");
            return new ResourceRate(resourceId, rate);
        }

        public String getResourceId() {
            return resourceId;
        }

        public double getRate() {
            return rate;
        }
    }
}
