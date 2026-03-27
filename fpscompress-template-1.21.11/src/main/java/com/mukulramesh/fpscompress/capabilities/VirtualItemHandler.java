package com.mukulramesh.fpscompress.capabilities;

import com.mukulramesh.fpscompress.portal.VirtualBufferStorage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Virtual item handler that wraps VirtualBufferStorage.
 *
 * This capability is attached to upgraded Compact Machines when in CACHED mode,
 * allowing external systems (pipes, hoppers, etc.) to interact with virtual storage
 * instead of physical blocks.
 *
 * @author Dev 1 - Core Registry Team
 */
public class VirtualItemHandler implements IItemHandler {

    private final VirtualBufferStorage storage;

    /**
     * Constructor for VirtualItemHandler.
     *
     * @param storage The virtual buffer storage to wrap
     */
    public VirtualItemHandler(VirtualBufferStorage storage) {
        this.storage = storage;
    }

    @Override
    public int getSlots() {
        // Return max slots (27 for double chest equivalent)
        return VirtualBufferStorage.MAX_ITEM_SLOTS;
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            return ItemStack.EMPTY;
        }

        // For simplicity, we distribute items across "virtual slots"
        // This is a simplified view - actual impl maps storage to slots
        Map<String, Integer> items = storage.getItemSnapshot();

        if (items.isEmpty() || slot >= items.size()) {
            return ItemStack.EMPTY;
        }

        // TODO: Proper implementation would iterate to the Nth entry and create ItemStack
        // For now, return empty as this is placeholder code
        return ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (slot < 0 || slot >= getSlots()) {
            return stack; // Invalid slot, return full stack
        }

        // Get item ID from stack
        String itemId = getItemId(stack);
        int amount = stack.getCount();

        if (!simulate) {
            // Actually add to storage
            int added = storage.addItem(itemId, amount);

            if (added < amount) {
                // Return remainder that didn't fit
                ItemStack remainder = stack.copy();
                remainder.setCount(amount - added);
                return remainder;
            }
        } else {
            // Simulate - check if there's space
            int spaceLeft = storage.getItemSpaceRemaining();
            if (spaceLeft < amount) {
                // Return remainder that wouldn't fit
                ItemStack remainder = stack.copy();
                remainder.setCount(amount - spaceLeft);
                return remainder;
            }
        }

        return ItemStack.EMPTY; // All items accepted
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= getSlots()) {
            return ItemStack.EMPTY;
        }

        // Get items from storage
        Map<String, Integer> items = storage.getItemSnapshot();

        if (items.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Get the Nth item type
        int currentSlot = 0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (currentSlot == slot) {
                String itemId = entry.getKey();
                int available = entry.getValue();
                int toExtract = Math.min(amount, available);

                if (!simulate && toExtract > 0) {
                    // Actually extract
                    storage.extractItem(itemId, toExtract);
                }

                // TODO: Create proper ItemStack from itemId
                return ItemStack.EMPTY; // Placeholder
            }
            currentSlot++;
        }

        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        // Standard stack size
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        // Accept all items (storage has its own capacity limits)
        return !stack.isEmpty() && slot >= 0 && slot < getSlots();
    }

    // ===== Helper Methods =====

    /**
     * Get a string identifier for an ItemStack.
     *
     * @param stack The ItemStack
     * @return A string ID like "minecraft:iron_ingot"
     */
    private String getItemId(ItemStack stack) {
        // Use the registry name as the ID
        var item = stack.getItem();
        var registryName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return registryName.toString();
    }

    /**
     * Get the total number of items currently stored.
     *
     * @return The sum of all item counts
     */
    public int getTotalItemCount() {
        return storage.getTotalItemCount();
    }

    /**
     * Check if the storage is empty.
     *
     * @return true if no items are stored
     */
    public boolean isEmpty() {
        return storage.getTotalItemCount() == 0;
    }
}
