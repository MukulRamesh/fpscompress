package com.mukulramesh.fpscompress;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for FPSCompress mod.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Future configuration options will be added here as needed

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
