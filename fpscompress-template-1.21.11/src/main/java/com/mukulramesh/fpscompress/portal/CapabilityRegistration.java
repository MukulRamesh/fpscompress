package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Event handler for registering capabilities on block entities.
 */
@EventBusSubscriber(modid = FPSCompress.MODID)
public final class CapabilityRegistration {

    private CapabilityRegistration() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Register capabilities on the MOD event bus.
     *
     * @param event The RegisterCapabilitiesEvent
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Fabricator: IItemHandler for hopper/pipe I/O
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            FPSCompress.FABRICATOR_BE.get(),
            (fabricator, side) -> fabricator.getIOWrapper()
        );
        FPSCompress.LOGGER.info("Registered Fabricator IItemHandler capability");
    }
}
