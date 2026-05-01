package com.mukulramesh.fpscompress.portal;

/**
 * Defines what types of resources a PreFab face can transport.
 *
 * <p>Each face can filter which resource types to handle:
 * <ul>
 *   <li>ALL - Accept all resource types (Items, Fluids, Energy)</li>
 *   <li>ITEMS - Only transport items (IItemHandler)</li>
 *   <li>FLUIDS - Only transport fluids (IFluidHandler)</li>
 *   <li>ENERGY - Only transport energy (IEnergyStorage)</li>
 * </ul>
 */
public enum ResourceFilter {
    /**
     * Allow all resource types.
     */
    ALL,

    /**
     * Only transport items (IItemHandler capability).
     */
    ITEMS,

    /**
     * Only transport fluids (IFluidHandler capability).
     */
    FLUIDS,

    /**
     * Only transport energy (IEnergyStorage capability).
     */
    ENERGY;

    /**
     * Check if this filter allows item transport.
     *
     * @return true if items are allowed
     */
    public boolean allowsItems() {
        return this == ALL || this == ITEMS;
    }

    /**
     * Check if this filter allows fluid transport.
     *
     * @return true if fluids are allowed
     */
    public boolean allowsFluids() {
        return this == ALL || this == FLUIDS;
    }

    /**
     * Check if this filter allows energy transport.
     *
     * @return true if energy is allowed
     */
    public boolean allowsEnergy() {
        return this == ALL || this == ENERGY;
    }
}
