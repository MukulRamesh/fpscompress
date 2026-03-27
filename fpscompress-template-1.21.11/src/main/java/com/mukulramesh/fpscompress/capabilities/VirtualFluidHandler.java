package com.mukulramesh.fpscompress.capabilities;

import com.mukulramesh.fpscompress.portal.VirtualBufferStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Virtual fluid handler that wraps VirtualBufferStorage.
 *
 * This capability is attached to upgraded Compact Machines when in CACHED mode,
 * allowing external systems (pipes, tanks, etc.) to interact with virtual fluid storage.
 *
 * @author Dev 1 - Core Registry Team
 */
public class VirtualFluidHandler implements IFluidHandler {

    private final VirtualBufferStorage storage;

    /**
     * Constructor for VirtualFluidHandler.
     *
     * @param storage The virtual buffer storage to wrap
     */
    public VirtualFluidHandler(VirtualBufferStorage storage) {
        this.storage = storage;
    }

    @Override
    public int getTanks() {
        // Treat storage as a single large tank
        // (Could be multiple tanks, but single tank simplifies logic)
        return 1;
    }

    @Override
    @NotNull
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0) {
            return FluidStack.EMPTY;
        }

        // Get all fluids from storage
        Map<String, Integer> fluids = storage.getFluidSnapshot();

        if (fluids.isEmpty()) {
            return FluidStack.EMPTY;
        }

        // Return the first fluid type
        // Note: This is simplified - real impl might handle multiple fluid types differently
        for (Map.Entry<String, Integer> entry : fluids.entrySet()) {
            String fluidId = entry.getKey();
            int amount = entry.getValue();

            // Parse fluid ID and create FluidStack
            Fluid fluid = getFluidFromId(fluidId);
            if (fluid != Fluids.EMPTY) {
                return new FluidStack(fluid, amount);
            }
        }

        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        if (tank != 0) {
            return 0;
        }

        // Return max capacity
        return VirtualBufferStorage.MAX_FLUID_MB;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        // Accept all fluids
        return tank == 0 && !stack.isEmpty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }

        String fluidId = getFluidId(resource.getFluid());
        int amount = resource.getAmount();

        if (action.execute()) {
            // Actually add to storage
            return storage.addFluid(fluidId, amount);
        } else {
            // Simulate - check space
            int spaceLeft = storage.getFluidSpaceRemaining();
            return Math.min(amount, spaceLeft);
        }
    }

    @Override
    @NotNull
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }

        String fluidId = getFluidId(resource.getFluid());
        int amount = resource.getAmount();

        // Check if we have this fluid
        int available = storage.getFluidAmount(fluidId);
        if (available == 0) {
            return FluidStack.EMPTY;
        }

        int toDrain = Math.min(amount, available);

        if (action.execute()) {
            // Actually extract
            storage.extractFluid(fluidId, toDrain);
        }

        return new FluidStack(resource.getFluid(), toDrain);
    }

    @Override
    @NotNull
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return FluidStack.EMPTY;
        }

        // Get all fluids
        Map<String, Integer> fluids = storage.getFluidSnapshot();

        if (fluids.isEmpty()) {
            return FluidStack.EMPTY;
        }

        // Drain from the first fluid type
        for (Map.Entry<String, Integer> entry : fluids.entrySet()) {
            String fluidId = entry.getKey();
            int available = entry.getValue();

            if (available > 0) {
                int toDrain = Math.min(maxDrain, available);

                if (action.execute()) {
                    storage.extractFluid(fluidId, toDrain);
                }

                Fluid fluid = getFluidFromId(fluidId);
                return new FluidStack(fluid, toDrain);
            }
        }

        return FluidStack.EMPTY;
    }

    // ===== Helper Methods =====

    /**
     * Get a string identifier for a Fluid.
     *
     * @param fluid The Fluid
     * @return A string ID like "minecraft:water"
     */
    private String getFluidId(Fluid fluid) {
        var registryName = BuiltInRegistries.FLUID.getKey(fluid);
        return registryName.toString();
    }

    /**
     * Get a Fluid from its string identifier.
     *
     * @param fluidId The fluid ID like "minecraft:water"
     * @return The Fluid, or Fluids.EMPTY if not found
     */
    private Fluid getFluidFromId(String fluidId) {
        // Parse the string ID manually (e.g., "minecraft:water")
        String[] parts = fluidId.split(":", 2);
        if (parts.length != 2) {
            return Fluids.EMPTY;
        }

        // For now, just return EMPTY - proper implementation would use registry lookup
        // TODO: Implement proper fluid lookup from string ID
        return Fluids.EMPTY;
    }

    /**
     * Get the total amount of fluid currently stored.
     *
     * @return The sum of all fluid amounts in millibuckets
     */
    public int getTotalFluidAmount() {
        return storage.getTotalFluidAmount();
    }

    /**
     * Check if the storage has any fluids.
     *
     * @return true if no fluids are stored
     */
    public boolean isEmpty() {
        return storage.getTotalFluidAmount() == 0;
    }
}
