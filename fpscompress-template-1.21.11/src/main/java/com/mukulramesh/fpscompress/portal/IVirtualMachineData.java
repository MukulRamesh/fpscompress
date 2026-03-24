package com.mukulramesh.fpscompress.portal;

/**
 * Interface for Dev 1: Core Registry & Block Shell
 *
 * This interface represents the physical Overworld block (machine_portal) and its virtual buffers.
 * When the machine enters CACHED mode, resources are routed to/from these virtual buffers instead
 * of the physical factory interior.
 *
 * RESPONSIBILITIES:
 * - Track whether the TPS upgrade is installed in this machine
 * - Manage virtual buffers for Items, Fluids, and Energy
 * - Provide API for inserting/extracting resources during cached mode
 *
 * IMPLEMENTATION NOTES:
 * - Virtual buffers should be stored as custom Data Components on the BlockEntity
 * - Use Codec/StreamCodec for serialization (NeoForge 1.21+ pattern)
 * - Should integrate with NeoForge capabilities (IItemHandler, IFluidHandler, IEnergyStorage)
 *
 * @author Dev 1 - Core Registry Team
 */
public interface IVirtualMachineData {

    /**
     * Check if this machine has the TPS upgrade installed.
     *
     * Without this upgrade, the machine operates as a normal Compact Machine.
     * With this upgrade, the machine can enter CACHED mode.
     *
     * @return true if TPS upgrade is installed, false otherwise
     */
    boolean hasTpsUpgrade();

    /**
     * Add resources to the virtual buffer.
     *
     * This is called during CACHED mode when Dev 4's logic produces outputs.
     *
     * @param type The resource type (ITEM, FLUID, or ENERGY)
     * @param resourceId The resource identifier (e.g., "minecraft:iron_ingot")
     * @param amount The amount to add
     * @return The amount actually added (may be less if buffer is full)
     */
    int addToBuffer(ResourceType type, String resourceId, int amount);

    /**
     * Extract resources from the virtual buffer.
     *
     * This is called during CACHED mode when Dev 4's logic consumes inputs.
     *
     * @param type The resource type (ITEM, FLUID, or ENERGY)
     * @param resourceId The resource identifier (e.g., "minecraft:iron_ingot")
     * @param amount The amount to extract
     * @return The amount actually extracted (may be less if buffer lacks resources)
     */
    int extractFromBuffer(ResourceType type, String resourceId, int amount);

    /**
     * Query the current amount of a resource in the virtual buffer.
     *
     * @param type The resource type (ITEM, FLUID, or ENERGY)
     * @param resourceId The resource identifier
     * @return The current amount in the buffer
     */
    int getBufferAmount(ResourceType type, String resourceId);

    /**
     * Get the maximum capacity of the virtual buffer for a resource type.
     *
     * Default capacities (from CLAUDE.md):
     * - ITEM: 27 slots
     * - FLUID: 50,000 mB
     * - ENERGY: 1,000,000 FE
     *
     * @param type The resource type
     * @return The maximum capacity
     */
    int getBufferCapacity(ResourceType type);

    /**
     * Resource types supported by the virtual buffers.
     */
    enum ResourceType {
        ITEM,
        FLUID,
        ENERGY
    }
}
