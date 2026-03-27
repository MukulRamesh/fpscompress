package com.mukulramesh.fpscompress.capabilities;

import com.mukulramesh.fpscompress.portal.VirtualBufferStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Virtual energy storage that wraps VirtualBufferStorage.
 *
 * This capability is attached to upgraded Compact Machines when in CACHED mode,
 * allowing external systems (cables, generators, consumers) to interact with
 * virtual energy storage.
 *
 * @author Dev 1 - Core Registry Team
 */
public class VirtualEnergyStorage implements IEnergyStorage {

    private final VirtualBufferStorage storage;

    /**
     * Constructor for VirtualEnergyStorage.
     *
     * @param storage The virtual buffer storage to wrap
     */
    public VirtualEnergyStorage(VirtualBufferStorage storage) {
        this.storage = storage;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) {
            return 0;
        }

        if (!simulate) {
            // Actually add to storage
            long added = storage.addEnergy(maxReceive);
            return (int) added;
        } else {
            // Simulate - check space
            long spaceLeft = storage.getEnergySpaceRemaining();
            return (int) Math.min(maxReceive, spaceLeft);
        }
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) {
            return 0;
        }

        long available = storage.getEnergyAmount();
        long toExtract = Math.min(maxExtract, available);

        if (!simulate && toExtract > 0) {
            // Actually extract
            storage.extractEnergy(toExtract);
        }

        return (int) toExtract;
    }

    @Override
    public int getEnergyStored() {
        // Cast to int (should be safe given our 1M FE cap)
        long energy = storage.getEnergyAmount();
        return (int) Math.min(energy, Integer.MAX_VALUE);
    }

    @Override
    public int getMaxEnergyStored() {
        // Return max capacity (1,000,000 FE)
        return (int) VirtualBufferStorage.MAX_ENERGY_FE;
    }

    @Override
    public boolean canExtract() {
        // Always allow extraction
        return true;
    }

    @Override
    public boolean canReceive() {
        // Always allow receiving
        return true;
    }

    // ===== Additional Helper Methods =====

    /**
     * Get the remaining energy capacity.
     *
     * @return The amount of energy (in FE) that can still be added
     */
    public long getEnergySpaceRemaining() {
        return storage.getEnergySpaceRemaining();
    }

    /**
     * Check if the storage is empty.
     *
     * @return true if no energy is stored
     */
    public boolean isEmpty() {
        return storage.getEnergyAmount() == 0;
    }

    /**
     * Check if the storage is full.
     *
     * @return true if at maximum capacity
     */
    public boolean isFull() {
        return storage.getEnergySpaceRemaining() == 0;
    }

    /**
     * Get the fill percentage.
     *
     * @return A value from 0.0 (empty) to 1.0 (full)
     */
    public double getFillPercentage() {
        return (double) storage.getEnergyAmount() / VirtualBufferStorage.MAX_ENERGY_FE;
    }
}
