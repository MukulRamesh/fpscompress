package com.mukulramesh.fpscompress;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

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
        private final ModConfigSpec.ConfigValue<List<? extends String>> blueprintExcludedBlocks;

        @SuppressWarnings("deprecation") // defineListAllowEmpty is the correct API for NeoForge 21.1
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

            builder.push("blueprints");

            blueprintExcludedBlocks = builder
                .comment("Block IDs to exclude from blueprint scanning",
                         "These blocks will not be counted as resources when scanning a PreFab",
                         "Default: Compact Machines wall blocks (should not be part of resource costs)",
                         "Format: List of namespaced block IDs (e.g., \"minecraft:bedrock\")",
                         "Modpack developers: Add custom dimension wall blocks here")
                .defineListAllowEmpty(
                    "blueprintExcludedBlocks",
                    List.of(
                        "compactmachines:solid_wall",
                        "compactmachines:wall",
                        "compactmachines:machine_wall"
                    ),
                    obj -> obj instanceof String
                );

            builder.pop();
        }

        /**
         * Get minimum simulation time in ticks.
         * @return Minimum ticks required before survival players can finish simulation
         */
        public int getMinimumSimulationTicks() {
            return minimumSimulationTicks.get();
        }

        /**
         * Get list of block IDs to exclude from blueprint scanning.
         * @return List of namespaced block IDs (e.g., "compactmachines:solid_wall")
         */
        public List<? extends String> getBlueprintExcludedBlocks() {
            return blueprintExcludedBlocks.get();
        }
    }
}
