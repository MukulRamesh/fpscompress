package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

/**
 * BlockEntity for Exporter blocks - CM dimension output gates.
 *
 * Features:
 * - Unique UUID for PreFab face linking
 * - 9-slot internal buffer for extracted resources
 * - Actively pulls from adjacent machines every tick
 * - Supplies resources to PreFab PUSH faces
 * - Persists UUID across block break/place cycles
 */
public class ExporterBlockEntity extends BlockEntity {

    // Unique identifier for PreFab linking
    private UUID exporterUUID;

    // Filter item for GUI display (e.g., "Iron Exporter" instead of UUID)
    // Player right-clicks with an item to set the filter
    private ItemStack filterItem = ItemStack.EMPTY;

    // Internal buffer (9 slots, items only for MVP)
    private final ItemStackHandler inventory = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public ExporterBlockEntity(BlockPos pos, BlockState state) {
        super(FPSCompress.EXPORTER_BE.get(), pos, state);

        // Generate UUID if new placement (will be overridden by NBT if loading saved data)
        this.exporterUUID = UUID.randomUUID();
    }

    /**
     * Get the unique UUID of this Exporter.
     * Used by PreFab faces to link to this specific Exporter.
     *
     * @return The Exporter's UUID
     */
    public UUID getExporterUUID() {
        return exporterUUID;
    }

    /**
     * Get the filter item for GUI display.
     *
     * @return The filter item (e.g., Iron Ingot for "Iron Ingot Exporter")
     */
    public ItemStack getFilterItem() {
        return filterItem.copy();
    }

    /**
     * Set the filter item for GUI display.
     * Player right-clicks Exporter with an item to set the filter.
     *
     * @param item The filter item
     */
    public void setFilterItem(ItemStack item) {
        this.filterItem = item.copyWithCount(1); // Store only 1 item for display
        setChanged();
    }

    /**
     * Get display name for this Exporter.
     * Shows filter item name if set, otherwise "Unnamed Exporter".
     *
     * @return Display name
     */
    public String getDisplayName() {
        if (!filterItem.isEmpty()) {
            return filterItem.getHoverName().getString() + " Exporter";
        }
        return "Unnamed Exporter";
    }

    /**
     * Extract items from this Exporter's buffer.
     * Called by PreFab PUSH faces during transport.
     *
     * @param maxAmount Maximum number of items to extract
     * @return Extracted items (may be less than requested if buffer low)
     */
    public ItemStack extractFromBuffer(int maxAmount) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }

        // Try extracting from each slot until requested amount obtained
        ItemStack result = ItemStack.EMPTY;
        int remaining = maxAmount;

        for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
            ItemStack stackInSlot = inventory.getStackInSlot(slot);
            if (stackInSlot.isEmpty()) {
                continue;
            }

            // If we already have items, only extract matching type
            if (!result.isEmpty() && !ItemStack.isSameItemSameComponents(result, stackInSlot)) {
                continue;
            }

            // Extract what we can from this slot
            int toExtract = Math.min(remaining, stackInSlot.getCount());
            ItemStack extracted = inventory.extractItem(slot, toExtract, false);

            if (!extracted.isEmpty()) {
                if (result.isEmpty()) {
                    result = extracted;
                } else {
                    result.grow(extracted.getCount());
                }
                remaining -= extracted.getCount();
            }
        }

        if (!result.isEmpty()) {
            setChanged();
        }

        return result;
    }

    /**
     * Pull items from adjacent machines into this Exporter's buffer.
     * Called every tick during active pulling behavior.
     */
    public void pullFromAdjacentMachines() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos pos = getBlockPos();

        // Scan all 6 adjacent positions for IItemHandler capabilities
        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = pos.relative(dir);

            // Query capability from adjacent block
            IItemHandler handler = level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                adjacentPos,
                dir.getOpposite() // Adjacent block exposes capability on the side facing us
            );

            if (handler == null) {
                continue;
            }

            // Try extracting items from adjacent machine (slot-by-slot)
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stackInSlot = handler.getStackInSlot(slot);
                if (stackInSlot.isEmpty()) {
                    continue;
                }

                // Try extracting entire stack (respects item's max stack size)
                int countInSlot = stackInSlot.getCount();
                ItemStack extracted = handler.extractItem(slot, countInSlot, false);
                if (extracted.isEmpty()) {
                    continue;
                }

                // Try inserting into our buffer
                ItemStack remainder = insertIntoBuffer(extracted);

                // If buffer full, put remainder back
                if (!remainder.isEmpty()) {
                    // Try to put remainder back into the same slot
                    ItemStack stillRemaining = handler.insertItem(slot, remainder, false);

                    // If we couldn't put it back, stop pulling (buffer full)
                    if (!stillRemaining.isEmpty()) {
                        return;
                    }
                }

                // Successfully pulled from this slot - continue to next slot
                // This allows pulling from multiple slots per tick if buffer has space
            }
        }
    }

    /**
     * Insert items into buffer (public for PreFab to use when putting back remainder).
     *
     * @param stack Items to insert
     * @return Remainder (items that didn't fit)
     */
    public ItemStack insertItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
            if (remaining.isEmpty()) {
                break;
            }
        }

        if (!remaining.equals(stack)) {
            setChanged();
        }

        return remaining;
    }

    /**
     * Insert items into buffer (internal helper - kept for backward compatibility).
     *
     * @param stack Items to insert
     * @return Remainder (items that didn't fit)
     */
    private ItemStack insertIntoBuffer(ItemStack stack) {
        return insertItem(stack);
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

    public static void tick(Level level, BlockPos pos, BlockState state, ExporterBlockEntity exporter) {
        if (level.isClientSide()) {
            return;
        }

        // Pull from adjacent machines every tick
        exporter.pullFromAdjacentMachines();
    }

    // ===== NBT Serialization =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // Save UUID
        tag.putUUID("ExporterUUID", exporterUUID);

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
        if (tag.contains("ExporterUUID")) {
            exporterUUID = tag.getUUID("ExporterUUID");
        }

        // Load filter item
        if (tag.contains("FilterItem")) {
            filterItem = ItemStack.parseOptional(registries, tag.getCompound("FilterItem"));
        }

        // Load inventory
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }

        // Register this Exporter in the global registry (for GUI scanning)
        if (level != null && !level.isClientSide()) {
            ImporterExporterRegistry.registerExporter(exporterUUID, getBlockPos(), getDisplayName());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Don't unregister here - setRemoved() is called for both chunk unload AND block break
        // Unregistration happens in ExporterBlock.onRemove() instead
        com.mukulramesh.fpscompress.FPSCompress.LOGGER.debug(
            "[Exporter] setRemoved() called for {}",
            exporterUUID.toString().substring(0, 8)
        );
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Register when chunk loads
        if (level != null && !level.isClientSide()) {
            ImporterExporterRegistry.registerExporter(exporterUUID, getBlockPos(), getDisplayName());
            com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                "Registered Exporter {} at {} (Display: {})",
                exporterUUID.toString().substring(0, 8),
                getBlockPos(),
                getDisplayName()
            );
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        // Registry updates only needed on load (onLoad) and NBT deserialization (loadAdditional)
        // No need to re-register on every setChanged() call
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
