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

    /**
     * Current expected config version. Bump this when Java defaults change
     * so the migration system auto-updates stale TOML files on next launch.
     *
     * <p>Migration history:
     * <ul>
     *   <li>0 → 1: blueprintPrintTicks default changed from 1 to 20</li>
     *   <li>1 → 2: prefabConsumedOnScan default changed from true to false</li>
     * </ul>
     */
    public static final int CURRENT_CONFIG_VERSION = 2;

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
        private final ModConfigSpec.ConfigValue<List<? extends String>> blueprintConstantCosts;
        private final ModConfigSpec.DoubleValue blueprintResourceMultiplier;
        private final ModConfigSpec.BooleanValue blueprintReusable;
        private final ModConfigSpec.BooleanValue prefabConsumedOnScan;
        private final ModConfigSpec.IntValue blueprintPrintTicks;
        private final ModConfigSpec.IntValue configVersion;

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

            blueprintConstantCosts = builder
                .comment("Constant resource costs added to every blueprint print",
                         "These are always required regardless of what was scanned",
                         "Format: \"modid:item_id:count\" (e.g., \"minecraft:diamond:5\")",
                         "Example: [\"minecraft:diamond:5\", \"minecraft:netherite_ingot:1\"]",
                         "Default: empty (no extra costs)")
                .defineListAllowEmpty(
                    "blueprintConstantCosts",
                    List.of(),
                    obj -> obj instanceof String
                );

            blueprintResourceMultiplier = builder
                .comment("Multiplier applied to all scanned resource counts",
                         "1.0 = exact scan count, 2.0 = double cost, 0.5 = half cost",
                         "Does NOT affect constant costs (above) — those are always exact",
                         "Range: 0.0 to 100.0")
                .defineInRange("blueprintResourceMultiplier", 1.0, 0.0, 100.0);

            blueprintReusable = builder
                .comment("Whether Blueprints are reusable after printing",
                         "true = Blueprint stays in input slot (default, infinite copies)",
                         "false = Blueprint consumed on each print (one-time use)")
                .define("blueprintReusable", true);

            prefabConsumedOnScan = builder
                .comment("Whether the PreFab item is consumed when scanned in the Fabricator",
                         "true = PreFab consumed after scanning (one scan per PreFab)",
                         "false = PreFab stays in input slot after scanning (default, can be reused)")
                .define("prefabConsumedOnScan", false);

            blueprintPrintTicks = builder
                .comment("Number of ticks required to print a PreFab from a Blueprint",
                         "20 ticks = 1 second. Set to 0 for instant printing.",
                         "Range: 0 to 1200 (0 to 1 minute)")
                .defineInRange("blueprintPrintTicks", 20, 0, 1200);

            configVersion = builder
                .comment("Internal: config version for auto-migration (do not edit manually)",
                         "When the mod detects a stale version, it auto-updates changed defaults")
                .defineInRange("configVersion", 0, 0, Integer.MAX_VALUE);

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

        /**
         * Get constant resource costs for blueprint printing.
         * Format: "modid:item_id:count" (e.g., "minecraft:diamond:5")
         * @return List of constant cost strings
         */
        public List<? extends String> getBlueprintConstantCosts() {
            return blueprintConstantCosts.get();
        }

        /**
         * Get multiplier applied to scanned resource counts.
         * @return Multiplier value (1.0 = exact, 2.0 = double)
         */
        public double getBlueprintResourceMultiplier() {
            return blueprintResourceMultiplier.get();
        }

        /**
         * Get whether blueprints are reusable after printing.
         * @return true if reusable, false if consumed on print
         */
        public boolean getBlueprintReusable() {
            return blueprintReusable.get();
        }

        /**
         * Get whether the PreFab item is consumed when scanned in the Fabricator.
         * @return true if consumed on scan, false if PreFab stays in input slot
         */
        public boolean getPrefabConsumedOnScan() {
            return prefabConsumedOnScan.get();
        }

        /**
         * Get number of ticks required to print a PreFab.
         * @return Print duration in ticks (0 = instant)
         */
        public int getBlueprintPrintTicks() {
            return blueprintPrintTicks.get();
        }

        /**
         * Get the config file version (used for auto-migration).
         * @return Config version from TOML file, 0 if old file without version field
         */
        public int getConfigVersion() {
            return configVersion.get();
        }
    }
}
