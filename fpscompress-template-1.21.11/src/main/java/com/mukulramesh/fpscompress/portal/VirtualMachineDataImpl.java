package com.mukulramesh.fpscompress.portal;

import dev.compactmods.machines.machine.block.BoundCompactMachineBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Implementation of IVirtualMachineData that wraps a Compact Machine BlockEntity
 * and provides virtual buffer storage.
 *
 * This class serves as the "Dev 1" implementation that FactoryIntegrator uses
 * to interact with virtual resource storage.
 *
 * @author Dev 1 - Core Registry Team
 */
public class VirtualMachineDataImpl implements IVirtualMachineData {

    /**
     * Reference to the Compact Machine BlockEntity this data is attached to.
     */
    private final BoundCompactMachineBlockEntity blockEntity;

    /**
     * The actual virtual buffer storage for items, fluids, and energy.
     */
    private final VirtualBufferStorage storage;

    /**
     * Whether the TPS upgrade is installed on this machine.
     * When false, the machine operates as a normal Compact Machine.
     * When true, the machine can enter CACHED mode.
     */
    private boolean hasTpsUpgrade = false;

    /**
     * Constructor for VirtualMachineDataImpl.
     *
     * @param blockEntity The Compact Machine BlockEntity to wrap
     */
    public VirtualMachineDataImpl(BoundCompactMachineBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.storage = new VirtualBufferStorage();
    }

    // ===== IVirtualMachineData Implementation =====

    @Override
    public boolean hasTpsUpgrade() {
        return hasTpsUpgrade;
    }

    /**
     * Set whether the TPS upgrade is installed.
     * This is called when the player uses the TPS Cache Upgrade item.
     *
     * @param hasUpgrade true if upgrade is installed, false otherwise
     */
    public void setTpsUpgrade(boolean hasUpgrade) {
        this.hasTpsUpgrade = hasUpgrade;
        // Mark the BlockEntity as changed so it gets saved
        ((BlockEntity) blockEntity).setChanged();
    }

    @Override
    public int addToBuffer(ResourceType type, String resourceId, int amount) {
        if (!hasTpsUpgrade) {
            return 0; // Can't use virtual buffers without upgrade
        }

        int added = switch (type) {
            case ITEM -> storage.addItem(resourceId, amount);
            case FLUID -> storage.addFluid(resourceId, amount);
            case ENERGY -> (int) storage.addEnergy(amount);
        };

        // Mark BlockEntity as changed if we added anything
        if (added > 0) {
            ((BlockEntity) blockEntity).setChanged();
        }

        return added;
    }

    @Override
    public int extractFromBuffer(ResourceType type, String resourceId, int amount) {
        if (!hasTpsUpgrade) {
            return 0; // Can't use virtual buffers without upgrade
        }

        int extracted = switch (type) {
            case ITEM -> storage.extractItem(resourceId, amount);
            case FLUID -> storage.extractFluid(resourceId, amount);
            case ENERGY -> (int) storage.extractEnergy(amount);
        };

        // Mark BlockEntity as changed if we extracted anything
        if (extracted > 0) {
            ((BlockEntity) blockEntity).setChanged();
        }

        return extracted;
    }

    @Override
    public int getBufferAmount(ResourceType type, String resourceId) {
        return switch (type) {
            case ITEM -> storage.getItemAmount(resourceId);
            case FLUID -> storage.getFluidAmount(resourceId);
            case ENERGY -> (int) storage.getEnergyAmount();
        };
    }

    @Override
    public int getBufferCapacity(ResourceType type) {
        return switch (type) {
            case ITEM -> VirtualBufferStorage.MAX_ITEM_SLOTS;
            case FLUID -> VirtualBufferStorage.MAX_FLUID_MB;
            case ENERGY -> (int) VirtualBufferStorage.MAX_ENERGY_FE;
        };
    }

    // ===== Additional Helper Methods =====

    /**
     * Get the wrapped BlockEntity.
     *
     * @return The Compact Machine BlockEntity
     */
    public BoundCompactMachineBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /**
     * Get direct access to the storage (for capability providers).
     *
     * @return The VirtualBufferStorage instance
     */
    public VirtualBufferStorage getStorage() {
        return storage;
    }

    /**
     * Check if the buffer is empty.
     *
     * @return true if no resources are stored
     */
    public boolean isEmpty() {
        return storage.isEmpty();
    }

    /**
     * Clear all stored resources.
     * Use with caution - this deletes all virtual buffer contents!
     */
    public void clear() {
        storage.clear();
        ((BlockEntity) blockEntity).setChanged();
    }

    // ===== NBT Persistence =====

    /**
     * Save this data to NBT.
     *
     * This is used when persisting the data attachment to the BlockEntity.
     *
     * @return A CompoundTag containing all persistent data
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        // Save upgrade status
        tag.putBoolean("hasTpsUpgrade", hasTpsUpgrade);

        // Save storage data
        CompoundTag storageTag = new CompoundTag();
        storage.save(storageTag);
        tag.put("storage", storageTag);

        return tag;
    }

    /**
     * Load this data from NBT.
     *
     * This is used when restoring the data attachment from saved data.
     *
     * @param tag The CompoundTag to load from
     */
    public void load(CompoundTag tag) {
        // Load upgrade status
        hasTpsUpgrade = tag.getBoolean("hasTpsUpgrade");

        // Load storage data
        if (tag.contains("storage")) {
            CompoundTag storageTag = tag.getCompound("storage");
            storage.load(storageTag);
        }
    }

    // ===== Debug Methods =====

    @Override
    public String toString() {
        return String.format("VirtualMachineData[upgraded=%b, %s]",
            hasTpsUpgrade, storage.toString());
    }

    /**
     * Get the room code for this Compact Machine.
     * Uses CM's public getter method (no Access Transformer needed).
     *
     * Note: Currently commented out due to CM API interface not being in JAR.
     * Will be implemented when needed by FactoryIntegrator.
     *
     * @return The room code, or null if not connected
     */
    public String getRoomCode() {
        // TODO: Implement using reflection if CM API interface is not available
        // return blockEntity.connectedRoom();
        return null;
    }
}
