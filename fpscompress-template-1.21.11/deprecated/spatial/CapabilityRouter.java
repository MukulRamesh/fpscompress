package com.mukulramesh.fpscompress.spatial;

import com.mukulramesh.fpscompress.portal.IVirtualMachineData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Dev 2: Capability Routing Wrapper
 *
 * This class intercepts capability calls (IItemHandler, IFluidHandler, IEnergyStorage)
 * and routes them to either physical blocks or virtual buffers based on the routing state.
 *
 * ROUTING LOGIC:
 * - When routing state is PHYSICAL (false):
 *   → Pass through to the actual physical capability (Compact Machines' normal behavior)
 *
 * - When routing state is VIRTUAL (true):
 *   → Redirect to Dev 1's virtual buffers (IVirtualMachineData)
 *   → Physical blocks inside the CM room are not accessed
 *
 * INTEGRATION:
 * - Used by MachinePortalBlockEntity (Dev 1's block in the Overworld)
 * - Controlled by CMInterceptorImpl's routing state
 * - Coordinates with FactoryIntegrator's state machine transitions
 *
 * @author Dev 2 - Spatial Manager Team
 */
public class CapabilityRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CapabilityRouter.class);

    /**
     * Wraps an IItemHandler to route calls based on routing state.
     */
    public static class ItemHandlerRouter implements IItemHandler {

        private final IItemHandler physicalHandler;
        private final IVirtualMachineData virtualData;
        private final ICMInterceptor interceptor;

        public ItemHandlerRouter(
                IItemHandler physicalHandler,
                IVirtualMachineData virtualData,
                ICMInterceptor interceptor) {
            this.physicalHandler = physicalHandler;
            this.virtualData = virtualData;
            this.interceptor = interceptor;
        }

        @Override
        public int getSlots() {
            if (interceptor.isRoutingToVirtual()) {
                // Unlimited virtual storage
                return Integer.MAX_VALUE;
            }
            return physicalHandler.getSlots();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (interceptor.isRoutingToVirtual()) {
                // Virtual buffer doesn't have a slot-based structure
                // It stores items by resource ID with total counts
                // This method is primarily used for rendering, not critical for operation
                return ItemStack.EMPTY;
            }
            return physicalHandler.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (interceptor.isRoutingToVirtual()) {
                // Route to virtual buffer
                if (!simulate && !stack.isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    int inserted = virtualData.addToBuffer(
                            IVirtualMachineData.ResourceType.ITEM,
                            itemId,
                            stack.getCount()
                    );

                    LOGGER.debug("Routed {} x{} to virtual buffer (slot {}), {} inserted",
                            itemId, stack.getCount(), slot, inserted);

                    // Return remainder that couldn't be inserted
                    if (inserted < stack.getCount()) {
                        return stack.copyWithCount(stack.getCount() - inserted);
                    }
                }
                return ItemStack.EMPTY;
            }
            return physicalHandler.insertItem(slot, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (interceptor.isRoutingToVirtual()) {
                // Smart extraction: if only one item type stored, extract from it
                Map<String, Integer> items = virtualData.getItemSnapshot();
                if (items.size() == 1) {
                    String itemId = items.keySet().iterator().next();
                    if (!simulate) {
                        int extracted = virtualData.extractFromBuffer(
                            IVirtualMachineData.ResourceType.ITEM,
                            itemId,
                            amount
                        );
                        if (extracted > 0) {
                            // Convert resource ID back to ItemStack
                            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                            LOGGER.debug("Smart extraction: extracted {} x{} from virtual buffer",
                                itemId, extracted);
                            return new ItemStack(item, extracted);
                        }
                    } else {
                        // Simulate: check how much could be extracted
                        int available = virtualData.getBufferAmount(
                            IVirtualMachineData.ResourceType.ITEM,
                            itemId
                        );
                        int canExtract = Math.min(amount, available);
                        if (canExtract > 0) {
                            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                            return new ItemStack(item, canExtract);
                        }
                    }
                }
                // Multiple resource types or empty - return empty
                LOGGER.debug("Extract from virtual buffer by slot not supported (multiple types or empty)");
                return ItemStack.EMPTY;
            }
            return physicalHandler.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (interceptor.isRoutingToVirtual()) {
                // Virtual buffer uses standard stack size (64)
                return 64;
            }
            return physicalHandler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (interceptor.isRoutingToVirtual()) {
                // Virtual buffer accepts all items
                return true;
            }
            return physicalHandler.isItemValid(slot, stack);
        }
    }

    /**
     * Wraps an IFluidHandler to route calls based on routing state.
     */
    public static class FluidHandlerRouter implements IFluidHandler {

        private final IFluidHandler physicalHandler;
        private final IVirtualMachineData virtualData;
        private final ICMInterceptor interceptor;

        public FluidHandlerRouter(
                IFluidHandler physicalHandler,
                IVirtualMachineData virtualData,
                ICMInterceptor interceptor) {
            this.physicalHandler = physicalHandler;
            this.virtualData = virtualData;
            this.interceptor = interceptor;
        }

        @Override
        public int getTanks() {
            if (interceptor.isRoutingToVirtual()) {
                // Virtual buffer has 1 tank (per CLAUDE.md spec: 50,000 mB)
                return 1;
            }
            return physicalHandler.getTanks();
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            if (interceptor.isRoutingToVirtual()) {
                // Virtual buffer doesn't have a tank-based structure
                // It stores fluids by resource ID with total amounts
                // This method is primarily used for rendering, not critical for operation
                return FluidStack.EMPTY;
            }
            return physicalHandler.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            if (interceptor.isRoutingToVirtual()) {
                // Unlimited virtual storage
                return Integer.MAX_VALUE;
            }
            return physicalHandler.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            if (interceptor.isRoutingToVirtual()) {
                // Virtual buffer accepts all fluids
                return true;
            }
            return physicalHandler.isFluidValid(tank, stack);
        }

        @Override
        public int fill(@Nonnull FluidStack resource, @Nonnull FluidAction action) {
            if (interceptor.isRoutingToVirtual()) {
                // Route to virtual buffer
                if (action.execute() && !resource.isEmpty()) {
                    String fluidId = BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString();
                    int filled = virtualData.addToBuffer(
                            IVirtualMachineData.ResourceType.FLUID,
                            fluidId,
                            resource.getAmount()
                    );

                    LOGGER.debug("Routed {} x{} mB to virtual buffer, {} filled",
                            fluidId, resource.getAmount(), filled);

                    return filled;
                }
                return 0;
            }
            return physicalHandler.fill(resource, action);
        }

        @Nonnull
        @Override
        public FluidStack drain(@Nonnull FluidStack resource, @Nonnull FluidAction action) {
            if (interceptor.isRoutingToVirtual()) {
                // Extract from virtual buffer
                if (action.execute() && !resource.isEmpty()) {
                    String fluidId = BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString();
                    int drained = virtualData.extractFromBuffer(
                            IVirtualMachineData.ResourceType.FLUID,
                            fluidId,
                            resource.getAmount()
                    );

                    LOGGER.debug("Drained {} x{} mB from virtual buffer",
                            fluidId, drained);

                    if (drained > 0) {
                        return new FluidStack(resource.getFluid(), drained);
                    }
                }
                return FluidStack.EMPTY;
            }
            return physicalHandler.drain(resource, action);
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, @Nonnull FluidAction action) {
            if (interceptor.isRoutingToVirtual()) {
                // Smart extraction: if only one fluid type stored, drain from it
                Map<String, Integer> fluids = virtualData.getFluidSnapshot();
                if (fluids.size() == 1) {
                    String fluidId = fluids.keySet().iterator().next();
                    if (action.execute()) {
                        int drained = virtualData.extractFromBuffer(
                            IVirtualMachineData.ResourceType.FLUID,
                            fluidId,
                            maxDrain
                        );
                        if (drained > 0) {
                            Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluidId));
                            LOGGER.debug("Smart extraction: drained {} x{} mB from virtual buffer",
                                fluidId, drained);
                            return new FluidStack(fluid, drained);
                        }
                    } else {
                        // Simulate: check how much could be drained
                        int available = virtualData.getBufferAmount(
                            IVirtualMachineData.ResourceType.FLUID,
                            fluidId
                        );
                        int canDrain = Math.min(maxDrain, available);
                        if (canDrain > 0) {
                            Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluidId));
                            return new FluidStack(fluid, canDrain);
                        }
                    }
                }
                // Multiple resource types or empty - return empty
                LOGGER.debug("Drain from virtual buffer by amount not supported (multiple types or empty)");
                return FluidStack.EMPTY;
            }
            return physicalHandler.drain(maxDrain, action);
        }
    }

    /**
     * Wraps an IEnergyStorage to route calls based on routing state.
     */
    public static class EnergyStorageRouter implements IEnergyStorage {

        private final IEnergyStorage physicalHandler;
        private final IVirtualMachineData virtualData;
        private final ICMInterceptor interceptor;

        public EnergyStorageRouter(
                IEnergyStorage physicalHandler,
                IVirtualMachineData virtualData,
                ICMInterceptor interceptor) {
            this.physicalHandler = physicalHandler;
            this.virtualData = virtualData;
            this.interceptor = interceptor;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (interceptor.isRoutingToVirtual()) {
                // Route to virtual buffer
                if (!simulate && maxReceive > 0) {
                    // Energy doesn't have a resource ID - use a placeholder
                    int received = virtualData.addToBuffer(
                            IVirtualMachineData.ResourceType.ENERGY,
                            "energy", // Placeholder - not used by virtual buffer for energy
                            maxReceive
                    );

                    LOGGER.debug("Routed {} FE to virtual buffer, {} received",
                            maxReceive, received);

                    return received;
                }
                return 0;
            }
            return physicalHandler.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (interceptor.isRoutingToVirtual()) {
                // Extract from virtual buffer
                if (!simulate && maxExtract > 0) {
                    // Energy doesn't have a resource ID - use a placeholder
                    int extracted = virtualData.extractFromBuffer(
                            IVirtualMachineData.ResourceType.ENERGY,
                            "energy", // Placeholder - not used by virtual buffer for energy
                            maxExtract
                    );

                    LOGGER.debug("Extracted {} FE from virtual buffer", extracted);

                    return extracted;
                }
                return 0;
            }
            return physicalHandler.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            if (interceptor.isRoutingToVirtual()) {
                return virtualData.getBufferAmount(IVirtualMachineData.ResourceType.ENERGY, "energy");
            }
            return physicalHandler.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            if (interceptor.isRoutingToVirtual()) {
                // Unlimited virtual storage
                return Integer.MAX_VALUE;
            }
            return physicalHandler.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            if (interceptor.isRoutingToVirtual()) {
                // Virtual buffer allows extraction
                return true;
            }
            return physicalHandler.canExtract();
        }

        @Override
        public boolean canReceive() {
            if (interceptor.isRoutingToVirtual()) {
                // Virtual buffer allows receiving
                return true;
            }
            return physicalHandler.canReceive();
        }
    }
}
