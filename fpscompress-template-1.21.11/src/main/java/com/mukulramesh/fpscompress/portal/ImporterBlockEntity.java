package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

/**
 * BlockEntity for Importer blocks - CM dimension input gates.
 *
 * Features:
 * - Unique UUID for PreFab face linking
 * - 9-slot internal buffer for resource passthrough
 * - Exposes IItemHandler capability to adjacent machines (Phase 8)
 * - Persists UUID across block break/place cycles
 */
public class ImporterBlockEntity extends BlockEntity {

    // Unique identifier for PreFab linking
    private UUID importerUUID;

    // Filter item for GUI display (e.g., "Apple Importer" instead of UUID)
    // Player right-clicks with an item to set the filter
    private ItemStack filterItem = ItemStack.EMPTY;

    // Internal buffer (9 slots, items only for MVP)
    private final ItemStackHandler inventory = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public ImporterBlockEntity(BlockPos pos, BlockState state) {
        super(FPSCompress.IMPORTER_BE.get(), pos, state);

        // Generate UUID if new placement (will be overridden by NBT if loading saved data)
        this.importerUUID = UUID.randomUUID();
    }

    /**
     * Get the unique UUID of this Importer.
     * Used by PreFab faces to link to this specific Importer.
     *
     * @return The Importer's UUID
     */
    public UUID getImporterUUID() {
        return importerUUID;
    }

    /**
     * Get the filter item for GUI display.
     *
     * @return The filter item (e.g., Apple for "Apple Importer")
     */
    public ItemStack getFilterItem() {
        return filterItem.copy();
    }

    /**
     * Set the filter item for GUI display.
     * Player right-clicks Importer with an item to set the filter.
     *
     * @param item The filter item
     */
    public void setFilterItem(ItemStack item) {
        this.filterItem = item.copyWithCount(1); // Store only 1 item for display
        setChanged();
    }

    /**
     * Get display name for this Importer.
     * Shows filter item name if set, otherwise "Unnamed Importer".
     *
     * @return Display name
     */
    public String getDisplayName() {
        if (!filterItem.isEmpty()) {
            return filterItem.getHoverName().getString() + " Importer";
        }
        return "Unnamed Importer";
    }

    /**
     * Insert items into this Importer's buffer.
     * Called by PreFab PULL faces during transport.
     *
     * @param stack Items to insert
     * @return Remainder stack (items that couldn't fit)
     */
    public ItemStack insertItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();

        // Try inserting into each slot until all items placed or buffer full
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
            if (remaining.isEmpty()) {
                break;
            }
        }

        setChanged();
        return remaining;
    }

    /**
     * Push items from buffer to adjacent machines.
     * Called every tick during active pushing behavior.
     */
    public void pushToAdjacentMachines() {
        net.minecraft.world.level.Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos pos = getBlockPos();

        // Scan all 6 adjacent positions for IItemHandler capabilities
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos adjacentPos = pos.relative(dir);

            // Query capability from adjacent block
            net.neoforged.neoforge.items.IItemHandler handler = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                adjacentPos,
                dir.getOpposite() // Adjacent block exposes capability on the side facing us
            );

            if (handler == null) {
                continue;
            }

            // Try pushing items from our buffer to adjacent machine
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stackInSlot = inventory.getStackInSlot(slot);
                if (stackInSlot.isEmpty()) {
                    continue;
                }

                // Try extracting from our buffer
                int countInSlot = stackInSlot.getCount();
                ItemStack extracted = inventory.extractItem(slot, countInSlot, false);
                if (extracted.isEmpty()) {
                    continue;
                }

                // Try inserting into adjacent machine
                ItemStack remainder = ItemStack.EMPTY;
                for (int targetSlot = 0; targetSlot < handler.getSlots(); targetSlot++) {
                    remainder = handler.insertItem(targetSlot, extracted, false);
                    if (remainder.isEmpty()) {
                        break; // All items inserted
                    }
                    extracted = remainder;
                }

                // If we couldn't insert everything, put remainder back
                if (!remainder.isEmpty()) {
                    ItemStack stillRemaining = inventory.insertItem(slot, remainder, false);
                    if (!stillRemaining.isEmpty()) {
                        // Buffer full - stop pushing for this tick
                        return;
                    }
                }

                // Successfully pushed from this slot - continue to next slot
                setChanged();
            }
        }
    }

    /**
     * Get total item count in buffer (for debug display).
     *
     * @return Number of items stored
     */
    public int getBufferItemCount() {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            count += inventory.getStackInSlot(slot).getCount();
        }
        return count;
    }

    /**
     * Get the internal inventory handler (for capability exposure in Phase 8).
     * Package-private to avoid exposing mutable internal state.
     *
     * @return The ItemStackHandler
     */
    ItemStackHandler getInventory() {
        return inventory;
    }

    // ===== Block Entity Ticking =====

    public static void tick(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                           net.minecraft.world.level.block.state.BlockState state, ImporterBlockEntity importer) {
        if (level.isClientSide()) {
            return;
        }

        // Push from buffer to adjacent machines every tick
        importer.pushToAdjacentMachines();
    }

    // ===== NBT Serialization =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // Save UUID
        tag.putUUID("ImporterUUID", importerUUID);

        // Save filter item
        if (!filterItem.isEmpty()) {
            tag.put("FilterItem", filterItem.save(registries));
        }

        // Save inventory
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Load UUID (preserve across block break/place)
        if (tag.contains("ImporterUUID")) {
            importerUUID = tag.getUUID("ImporterUUID");
        }

        // Load filter item
        if (tag.contains("FilterItem")) {
            filterItem = ItemStack.parseOptional(registries, tag.getCompound("FilterItem"));
        }

        // Load inventory
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }

        // Register this Importer in the global registry (for GUI scanning)
        if (level != null && !level.isClientSide()) {
            ImporterExporterRegistry.registerImporter(importerUUID, getBlockPos(), getDisplayName());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
            "[Importer] setRemoved() called - isRemoved={}, level={}, clientSide={}",
            isRemoved(), level != null, level != null && level.isClientSide()
        );
        // Only unregister if actually removed (not just chunk unload)
        // onLoad() will re-register when chunk loads again
        if (level != null && !level.isClientSide() && isRemoved()) {
            ImporterExporterRegistry.unregisterImporter(importerUUID);
            com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                "[Importer] Unregistered {} (block broken)",
                importerUUID.toString().substring(0, 8)
            );
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
            "[Importer] onLoad() called - level={}, clientSide={}",
            level != null, level != null && level.isClientSide()
        );
        // Register when chunk loads
        if (level != null && !level.isClientSide()) {
            ImporterExporterRegistry.registerImporter(importerUUID, getBlockPos(), getDisplayName());
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        // Update registry when filter changes
        if (level != null && !level.isClientSide()) {
            ImporterExporterRegistry.registerImporter(importerUUID, getBlockPos(), getDisplayName());
        }
    }

    // ===== Client Sync =====

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }
}
