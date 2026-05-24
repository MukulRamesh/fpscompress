package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Event handler for registering capabilities on PreFab BlockEntities.
 *
 * NOTE: Face-based capability registration intentionally NOT implemented (see TODO.md).
 * Reference: TODO.md "Phase 8: Dynamic Capabilities (Deferred)"
 *
 * Reason: PreFab uses active transport (tick-based) instead of capability exposure.
 * Hoppers/pipes interact with Importers/Exporters directly, not PreFab faces.
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
     * Phase 8 intentionally deferred - see class javadoc and TODO.md for rationale.
     *
     * @param event The RegisterCapabilitiesEvent
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Phase 8 deferred: Active transport used instead of capability exposure
        // See TODO.md "Phase 8: Dynamic Capabilities (Deferred)"
        FPSCompress.LOGGER.info("PreFab capability registration - Phase 8 intentionally deferred");
    }
}
