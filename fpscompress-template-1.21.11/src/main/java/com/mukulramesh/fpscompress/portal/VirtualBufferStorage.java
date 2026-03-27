package com.mukulramesh.fpscompress.portal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Virtual buffer storage for Items, Fluids, and Energy.
 *
 * This class manages the "headless" storage that replaces physical factory blocks
 * when a Compact Machine enters CACHED mode. All operations enforce hard capacity limits.
 *
 * Capacities (from CLAUDE.md):
 * - Items: 27 slots × 64 items/slot = 1,728 items total
 * - Fluids: 50,000 mB total
 * - Energy: 1,000,000 FE total
 *
 * @author Dev 1 - Core Registry Team
 */
public class VirtualBufferStorage {

    // ===== Hard Capacity Constants =====

    /** Maximum item slots (matches a double chest) */
    public static final int MAX_ITEM_SLOTS = 27;

    /** Maximum items per slot (standard stack size) */
    public static final int MAX_STACK_SIZE = 64;

    /** Maximum total items across all slots */
    public static final int MAX_TOTAL_ITEMS = MAX_ITEM_SLOTS * MAX_STACK_SIZE;

    /** Maximum fluid capacity in millibuckets */
    public static final int MAX_FLUID_MB = 50_000;

    /** Maximum energy capacity in Forge Energy */
    public static final long MAX_ENERGY_FE = 1_000_000L;

    // ===== Storage Maps =====

    /**
     * Item storage: resource ID → count
     * Example: "minecraft:iron_ingot" → 128
     */
    private final Map<String, Integer> itemBuffer = new HashMap<>();

    /**
     * Fluid storage: fluid ID → millibuckets
     * Example: "minecraft:water" → 5000
     */
    private final Map<String, Integer> fluidBuffer = new HashMap<>();

    /**
     * Energy storage: single long value in Forge Energy
     */
    private long energyBuffer = 0L;

    // ===== Current Totals (for capacity tracking) =====

    private int currentItemCount = 0;
    private int currentFluidAmount = 0;

    // ===== Item Operations =====

    /**
     * Add items to the virtual buffer.
     *
     * @param itemId The item resource ID (e.g., "minecraft:iron_ingot")
     * @param amount The number of items to add
     * @return The amount actually added (may be less if buffer is full)
     */
    public int addItem(String itemId, int amount) {
        if (amount <= 0) {
            return 0;
        }

        int spaceLeft = MAX_TOTAL_ITEMS - currentItemCount;
        int toAdd = Math.min(amount, spaceLeft);

        if (toAdd > 0) {
            itemBuffer.merge(itemId, toAdd, Integer::sum);
            currentItemCount += toAdd;
        }

        return toAdd;
    }

    /**
     * Extract items from the virtual buffer.
     *
     * @param itemId The item resource ID
     * @param amount The number of items to extract
     * @return The amount actually extracted (may be less if buffer lacks items)
     */
    public int extractItem(String itemId, int amount) {
        if (amount <= 0) {
            return 0;
        }

        int current = itemBuffer.getOrDefault(itemId, 0);
        int toExtract = Math.min(amount, current);

        if (toExtract > 0) {
            int remaining = current - toExtract;
            if (remaining > 0) {
                itemBuffer.put(itemId, remaining);
            } else {
                itemBuffer.remove(itemId);
            }
            currentItemCount -= toExtract;
        }

        return toExtract;
    }

    /**
     * Get the current count of a specific item in the buffer.
     *
     * @param itemId The item resource ID
     * @return The current count (0 if not present)
     */
    public int getItemAmount(String itemId) {
        return itemBuffer.getOrDefault(itemId, 0);
    }

    /**
     * Get the total number of items currently stored.
     *
     * @return The sum of all item counts
     */
    public int getTotalItemCount() {
        return currentItemCount;
    }

    /**
     * Get the remaining item capacity.
     *
     * @return The number of items that can still be added
     */
    public int getItemSpaceRemaining() {
        return MAX_TOTAL_ITEMS - currentItemCount;
    }

    // ===== Fluid Operations =====

    /**
     * Add fluid to the virtual buffer.
     *
     * @param fluidId The fluid resource ID (e.g., "minecraft:water")
     * @param amount The amount in millibuckets to add
     * @return The amount actually added (may be less if buffer is full)
     */
    public int addFluid(String fluidId, int amount) {
        if (amount <= 0) {
            return 0;
        }

        int spaceLeft = MAX_FLUID_MB - currentFluidAmount;
        int toAdd = Math.min(amount, spaceLeft);

        if (toAdd > 0) {
            fluidBuffer.merge(fluidId, toAdd, Integer::sum);
            currentFluidAmount += toAdd;
        }

        return toAdd;
    }

    /**
     * Extract fluid from the virtual buffer.
     *
     * @param fluidId The fluid resource ID
     * @param amount The amount in millibuckets to extract
     * @return The amount actually extracted (may be less if buffer lacks fluid)
     */
    public int extractFluid(String fluidId, int amount) {
        if (amount <= 0) {
            return 0;
        }

        int current = fluidBuffer.getOrDefault(fluidId, 0);
        int toExtract = Math.min(amount, current);

        if (toExtract > 0) {
            int remaining = current - toExtract;
            if (remaining > 0) {
                fluidBuffer.put(fluidId, remaining);
            } else {
                fluidBuffer.remove(fluidId);
            }
            currentFluidAmount -= toExtract;
        }

        return toExtract;
    }

    /**
     * Get the current amount of a specific fluid in the buffer.
     *
     * @param fluidId The fluid resource ID
     * @return The current amount in millibuckets (0 if not present)
     */
    public int getFluidAmount(String fluidId) {
        return fluidBuffer.getOrDefault(fluidId, 0);
    }

    /**
     * Get the total amount of fluid currently stored.
     *
     * @return The sum of all fluid amounts in millibuckets
     */
    public int getTotalFluidAmount() {
        return currentFluidAmount;
    }

    /**
     * Get the remaining fluid capacity.
     *
     * @return The amount of fluid (in mB) that can still be added
     */
    public int getFluidSpaceRemaining() {
        return MAX_FLUID_MB - currentFluidAmount;
    }

    // ===== Energy Operations =====

    /**
     * Add energy to the virtual buffer.
     *
     * @param amount The amount in Forge Energy to add
     * @return The amount actually added (may be less if buffer is full)
     */
    public long addEnergy(long amount) {
        if (amount <= 0) {
            return 0;
        }

        long spaceLeft = MAX_ENERGY_FE - energyBuffer;
        long toAdd = Math.min(amount, spaceLeft);

        energyBuffer += toAdd;
        return toAdd;
    }

    /**
     * Extract energy from the virtual buffer.
     *
     * @param amount The amount in Forge Energy to extract
     * @return The amount actually extracted (may be less if buffer lacks energy)
     */
    public long extractEnergy(long amount) {
        if (amount <= 0) {
            return 0;
        }

        long toExtract = Math.min(amount, energyBuffer);
        energyBuffer -= toExtract;
        return toExtract;
    }

    /**
     * Get the current energy stored in the buffer.
     *
     * @return The current energy in Forge Energy
     */
    public long getEnergyAmount() {
        return energyBuffer;
    }

    /**
     * Get the remaining energy capacity.
     *
     * @return The amount of energy (in FE) that can still be added
     */
    public long getEnergySpaceRemaining() {
        return MAX_ENERGY_FE - energyBuffer;
    }

    // ===== Utility Methods =====

    /**
     * Check if the buffer is completely empty.
     *
     * @return true if no items, fluids, or energy are stored
     */
    public boolean isEmpty() {
        return currentItemCount == 0 && currentFluidAmount == 0 && energyBuffer == 0;
    }

    /**
     * Clear all stored resources.
     */
    public void clear() {
        itemBuffer.clear();
        fluidBuffer.clear();
        energyBuffer = 0;
        currentItemCount = 0;
        currentFluidAmount = 0;
    }

    // ===== NBT Serialization =====

    /**
     * Save the buffer state to NBT.
     *
     * @param tag The CompoundTag to write to
     * @return The same CompoundTag (for chaining)
     */
    public CompoundTag save(CompoundTag tag) {
        // Save item buffer
        ListTag itemList = new ListTag();
        for (Map.Entry<String, Integer> entry : itemBuffer.entrySet()) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("id", entry.getKey());
            itemTag.putInt("count", entry.getValue());
            itemList.add(itemTag);
        }
        tag.put("Items", itemList);
        tag.putInt("TotalItems", currentItemCount);

        // Save fluid buffer
        ListTag fluidList = new ListTag();
        for (Map.Entry<String, Integer> entry : fluidBuffer.entrySet()) {
            CompoundTag fluidTag = new CompoundTag();
            fluidTag.putString("id", entry.getKey());
            fluidTag.putInt("amount", entry.getValue());
            fluidList.add(fluidTag);
        }
        tag.put("Fluids", fluidList);
        tag.putInt("TotalFluids", currentFluidAmount);

        // Save energy
        tag.putLong("Energy", energyBuffer);

        return tag;
    }

    /**
     * Load the buffer state from NBT.
     *
     * @param tag The CompoundTag to read from
     */
    public void load(CompoundTag tag) {
        // Clear existing data
        clear();

        // Load item buffer
        ListTag itemList = tag.getList("Items", Tag.TAG_COMPOUND);
        for (Tag itemElement : itemList) {
            if (itemElement instanceof CompoundTag itemTag) {
                String id = itemTag.getString("id");
                int count = itemTag.getInt("count");
                if (!id.isEmpty() && count > 0) {
                    itemBuffer.put(id, count);
                }
            }
        }
        currentItemCount = tag.getInt("TotalItems");

        // Load fluid buffer
        ListTag fluidList = tag.getList("Fluids", Tag.TAG_COMPOUND);
        for (Tag fluidElement : fluidList) {
            if (fluidElement instanceof CompoundTag fluidTag) {
                String id = fluidTag.getString("id");
                int amount = fluidTag.getInt("amount");
                if (!id.isEmpty() && amount > 0) {
                    fluidBuffer.put(id, amount);
                }
            }
        }
        currentFluidAmount = tag.getInt("TotalFluids");

        // Load energy
        energyBuffer = tag.getLong("Energy");
    }

    // ===== Debug Methods =====

    /**
     * Get a string representation of the buffer state.
     * Useful for debugging and logging.
     *
     * @return A human-readable summary
     */
    @Override
    public String toString() {
        return String.format("VirtualBufferStorage[Items: %d/%d, Fluids: %d/%d mB, Energy: %d/%d FE]",
            currentItemCount, MAX_TOTAL_ITEMS,
            currentFluidAmount, MAX_FLUID_MB,
            energyBuffer, MAX_ENERGY_FE);
    }

    /**
     * Get detailed information about stored items.
     *
     * @return A map of item IDs to counts
     */
    public Map<String, Integer> getItemSnapshot() {
        return new HashMap<>(itemBuffer);
    }

    /**
     * Get detailed information about stored fluids.
     *
     * @return A map of fluid IDs to amounts
     */
    public Map<String, Integer> getFluidSnapshot() {
        return new HashMap<>(fluidBuffer);
    }
}
