package com.mukulramesh.fpscompress;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for FPSCompress mod.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ServerConfig SERVER = new ServerConfig(BUILDER);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Server-side configuration options.
     * These settings sync to clients and can be changed without server restart.
     */
    public static class ServerConfig {
        private final ModConfigSpec.IntValue minimumSimulationTicks;

        public ServerConfig(ModConfigSpec.Builder builder) {
            builder.comment("=== FPSCompress Server Configuration ===");
            builder.push("simulation");

            minimumSimulationTicks = builder
                .comment("Minimum ticks required in SIMULATING state before survival players can cache rates",
                         "20 ticks = 1 second, 2400 ticks = 2 minutes",
                         "Set to 0 to disable minimum time requirement",
                         "Creative mode players bypass this restriction",
                         "Range: 0 to 72000 (0 to 1 hour)")
                .defineInRange("minimumSimulationTicks", 2400, 0, 72000);

            builder.pop();
        }

        /**
         * Get minimum simulation time in ticks.
         * @return Minimum ticks required before survival players can finish simulation
         */
        public int getMinimumSimulationTicks() {
            return minimumSimulationTicks.get();
        }
    }
}
