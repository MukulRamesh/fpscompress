package com.mukulramesh.fpscompress.blueprint;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Map;

/**
 * Utility class for reading/writing cached blueprint scan data on PreFab item NBT.
 * Extracted from FabricatorBlockEntity to avoid exceeding file-length limits.
 */
public final class BlueprintScanCache {

    private BlueprintScanCache() { }

    /**
     * Load cached scan data from a PreFab item's NBT into the given maps.
     *
     * @return true if cached data was found and loaded, false otherwise
     */
    public static boolean loadFromPrefab(ItemStack prefabStack,
            Map<String, Long> scannedBlocks,
            Map<String, CompoundTag> scannedBlockNbt,
            Map<String, Long> scannedItems,
            Map<String, CompoundTag> scannedItemNbt) {
        CustomData customData = prefabStack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (customData == null) {
            return false;
        }
        CompoundTag nbt = customData.copyTag();
        if (!nbt.contains("blueprintScanData")) {
            return false;
        }
        CompoundTag scanData = nbt.getCompound("blueprintScanData");
        if (scanData.isEmpty()) {
            return false;
        }

        scannedBlocks.clear();
        scannedBlockNbt.clear();
        scannedItems.clear();
        scannedItemNbt.clear();
        loadResources(scanData, "blockResources", scannedBlocks, scannedBlockNbt);
        loadResources(scanData, "itemResources", scannedItems, scannedItemNbt);

        int totalTypes = scannedBlocks.size() + scannedItems.size();
        FPSCompress.LOGGER.info("Using cached blueprint scan data ({} resource types)", totalTypes);
        return !scannedBlocks.isEmpty() || !scannedItems.isEmpty();
    }

    /**
     * Write scan results into a PreFab ItemStack's NBT for future fast-path re-scans.
     * Cache is invalidated when the PreFab enters BUILDING state.
     */
    public static void writeToPrefab(ItemStack prefabStack,
            List<BlueprintData.ResourceRequirement> blockResources,
            List<BlueprintData.ResourceRequirement> itemResources) {
        CustomData existing = prefabStack.get(DataComponents.BLOCK_ENTITY_DATA);
        CompoundTag nbt = existing != null ? existing.copyTag() : new CompoundTag();
        CompoundTag scanData = new CompoundTag();
        ListTag blockList = new ListTag();
        for (BlueprintData.ResourceRequirement req : blockResources) {
            blockList.add(req.toNBT());
        }
        scanData.put("blockResources", blockList);
        ListTag itemList = new ListTag();
        for (BlueprintData.ResourceRequirement req : itemResources) {
            itemList.add(req.toNBT());
        }
        scanData.put("itemResources", itemList);
        nbt.put("blueprintScanData", scanData);
        prefabStack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(nbt));
        FPSCompress.LOGGER.debug("Cached scan data in PreFab NBT ({} blocks, {} items)",
            blockResources.size(), itemResources.size());
    }

    /** Deserialize a list of ResourceRequirements from scan data NBT into the given maps. */
    private static void loadResources(CompoundTag scanData, String listKey,
            Map<String, Long> counts, Map<String, CompoundTag> nbtMap) {
        if (!scanData.contains(listKey)) {
            return;
        }
        ListTag list = scanData.getList(listKey, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString("id");
            long count = entry.getLong("count");
            CompoundTag entryNbt = entry.contains("nbt")
                ? entry.getCompound("nbt") : null;
            String mapKey = id;
            if (entryNbt != null && !entryNbt.isEmpty()) {
                mapKey = id + "#" + entryNbt.toString().hashCode();
                nbtMap.put(mapKey, entryNbt);
            }
            counts.put(mapKey, count);
        }
    }
}
