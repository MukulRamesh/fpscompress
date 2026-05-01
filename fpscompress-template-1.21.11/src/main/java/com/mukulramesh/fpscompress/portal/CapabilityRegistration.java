package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Event handler for registering capabilities on PreFab BlockEntities.
 *
 * TODO Phase 8: Implement face-based capability registration for new conduit architecture.
 * The old virtual buffer capability system has been removed.
 *
 * @author Dev 1 - Core Registry Team
 */
@EventBusSubscriber(modid = FPSCompress.MODID)
public final class CapabilityRegistration {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private CapabilityRegistration() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Register capabilities on the MOD event bus.
     *
     * TODO Phase 8: Implement face-based capability registration.
     * Old virtual buffer capability system has been removed.
     *
     * @param event The RegisterCapabilitiesEvent
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // TODO Phase 8: Implement face-based capability registration
        // New architecture will expose capabilities based on face configuration (PULL/PUSH modes)
        FPSCompress.LOGGER.info("PreFab capability registration - awaiting Phase 8 implementation");
    }
}
