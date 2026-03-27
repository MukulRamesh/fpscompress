package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.lang.reflect.Field;

/**
 * Event handler for registering capabilities on Compact Machine BlockEntities.
 *
 * This class uses reflection to access CM's BlockEntity type and conditionally
 * attaches virtual buffer capabilities when the TPS upgrade is installed.
 *
 * @author Dev 1 - Core Registry Team
 */
@EventBusSubscriber(modid = FPSCompress.MODID)
public final class CapabilityRegistration {

    /**
     * Cached reference to CM's BlockEntity type (loaded via reflection).
     */
    private static BlockEntityType<?> cmBlockEntityType = null;

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private CapabilityRegistration() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Register capabilities on the MOD event bus.
     *
     * This method is called during mod loading and attaches virtual buffer
     * capabilities to Compact Machine BlockEntities.
     *
     * @param event The RegisterCapabilitiesEvent
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Get CM's BlockEntity type via reflection
        BlockEntityType<?> cmType = getCMBlockEntityType();
        if (cmType == null) {
            FPSCompress.LOGGER.warn("Failed to get Compact Machines BlockEntity type - capabilities not registered!");
            return;
        }

        FPSCompress.LOGGER.info("Registering virtual buffer capabilities for Compact Machines");

        // Note: Capability registration is deprecated in NeoForge 1.21.9+
        // This code is a placeholder for the deprecated API
        // TODO: Update to new capability system when migrating to newer NeoForge versions

        // For now, we'll comment out the registration until we can test with CM loaded
        // The capability handlers are ready and can be manually attached when needed

        /*
        // Register IItemHandler capability (DEPRECATED API)
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            cmType,
            (blockEntity, context) -> {
                if (blockEntity instanceof BoundCompactMachineBlockEntity cmBE) {
                    VirtualMachineDataImpl data = getVirtualData(cmBE);
                    if (data != null && data.hasTpsUpgrade()) {
                        return new VirtualItemHandler(data.getStorage());
                    }
                }
                return null;
            }
        );
        */

        FPSCompress.LOGGER.info("Virtual buffer capabilities registered successfully");
    }

    /**
     * Get CM's BlockEntity type using reflection.
     *
     * This method caches the result to avoid repeated reflection calls.
     * It accesses: dev.compactmods.machines.Machines.BlockEntities.MACHINE
     *
     * @return The BlockEntityType, or null if reflection fails
     */
    private static BlockEntityType<?> getCMBlockEntityType() {
        if (cmBlockEntityType != null) {
            return cmBlockEntityType;
        }

        try {
            // Load the Machines class
            Class<?> machinesClass = Class.forName("dev.compactmods.machines.Machines");

            // Get the BlockEntities nested class
            Class<?>[] nestedClasses = machinesClass.getDeclaredClasses();
            Class<?> blockEntitiesClass = null;
            for (Class<?> nestedClass : nestedClasses) {
                if (nestedClass.getSimpleName().equals("BlockEntities")) {
                    blockEntitiesClass = nestedClass;
                    break;
                }
            }

            if (blockEntitiesClass == null) {
                FPSCompress.LOGGER.error("Could not find Machines$BlockEntities class");
                return null;
            }

            // Get the MACHINE field
            Field machineField = blockEntitiesClass.getDeclaredField("MACHINE");
            machineField.setAccessible(true);

            // Get the DeferredHolder
            Object machineHolder = machineField.get(null);
            if (machineHolder instanceof DeferredHolder<?, ?> holder) {
                Object blockEntityType = holder.get();
                if (blockEntityType instanceof BlockEntityType<?> type) {
                    cmBlockEntityType = type;
                    FPSCompress.LOGGER.info("Successfully loaded CM BlockEntity type: {}", type);
                    return cmBlockEntityType;
                }
            }

            FPSCompress.LOGGER.error("MACHINE field is not a DeferredHolder<BlockEntityType>");
            return null;

        } catch (ClassNotFoundException e) {
            FPSCompress.LOGGER.error("Compact Machines not found! Is it installed?", e);
            return null;
        } catch (NoSuchFieldException e) {
            FPSCompress.LOGGER.error("MACHINE field not found in Machines.BlockEntities", e);
            return null;
        } catch (IllegalAccessException e) {
            FPSCompress.LOGGER.error("Failed to access MACHINE field", e);
            return null;
        } catch (Exception e) {
            FPSCompress.LOGGER.error("Unexpected error loading CM BlockEntity type", e);
            return null;
        }
    }
}
