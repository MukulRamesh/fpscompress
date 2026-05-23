package com.mukulramesh.fpscompress.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.spatial.CMInterceptorImpl;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Debug commands for testing Dev 2's Chunk Manager and Interceptor implementation.
 *
 * These commands are intended for QA testing and should be disabled in production builds.
 *
 * Available commands:
 * - /fps_dev2 chunks <roomCode> <load|unload> - Test chunk loading/unloading
 * - /fps_dev2 routing <physical|virtual> - Test routing state changes
 * - /fps_dev2 diagnostics - Show current interceptor state
 * - /fps_dev2 test-room <roomCode> - Run comprehensive room test
 * - /fps_dev2 cleanup - Clean up all chunk tickets
 * - /fps_dev2 give-test-prefab [inputItem] [inputRate] [outputItem] [outputRate] - Give PreFab with custom
 *     conversion rates
 * - /fps_dev2 give-test-prefab list <inputList> <outputList> - Give PreFab with multiple inputs/outputs
 *     (format: "item:rate,item:rate,...")
 *
 * @author Dev 2 - Testing Team
 */
public final class Dev2TestCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(Dev2TestCommands.class);

    // Singleton interceptor instance for testing
    private static CMInterceptorImpl testInterceptor = null;

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private Dev2TestCommands() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Register all Dev 2 test commands.
     *
     * @param dispatcher The command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("fps_dev2")
                        .requires(source -> source.hasPermission(2)) // Require OP level 2
                        .then(Commands.literal("chunks")
                                .then(Commands.argument("roomCode", StringArgumentType.string())
                                        .then(Commands.argument("load", BoolArgumentType.bool())
                                                .executes(Dev2TestCommands::testChunkLoading)
                                        )
                                )
                        )
                        .then(Commands.literal("routing")
                                .then(Commands.argument("virtual", BoolArgumentType.bool())
                                        .executes(Dev2TestCommands::testRouting)
                                )
                        )
                        .then(Commands.literal("diagnostics")
                                .executes(Dev2TestCommands::showDiagnostics)
                        )
                        .then(Commands.literal("test-room")
                                .then(Commands.argument("roomCode", StringArgumentType.string())
                                        .executes(Dev2TestCommands::testRoom)
                                )
                        )
                        .then(Commands.literal("debug-reflection")
                                .then(Commands.argument("roomCode", StringArgumentType.string())
                                        .executes(Dev2TestCommands::debugReflection)
                                )
                        )
                        .then(Commands.literal("cleanup")
                                .executes(Dev2TestCommands::cleanup)
                        )
                        .then(Commands.literal("give-test-prefab")
                                .executes(Dev2TestCommands::giveTestPrefab)
                                .then(Commands.literal("list")
                                        .then(Commands.argument("inputList", StringArgumentType.string())
                                                .then(Commands.argument("outputList", StringArgumentType.string())
                                                        .executes(Dev2TestCommands::giveTestPrefabList)
                                                )
                                        )
                                )
                                .then(Commands.argument("inputItem", StringArgumentType.string())
                                        .then(Commands.argument("inputRate",
                                                DoubleArgumentType.doubleArg(0.0))
                                                .then(Commands.argument("outputItem",
                                                        StringArgumentType.string())
                                                        .then(Commands.argument("outputRate",
                                                                DoubleArgumentType.doubleArg(0.0))
                                                                .executes(Dev2TestCommands::giveTestPrefabCustom)
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }

    /**
     * Get or create the test interceptor instance.
     */
    private static CMInterceptorImpl getInterceptor() {
        if (testInterceptor == null) {
            testInterceptor = new CMInterceptorImpl();
        }
        return testInterceptor;
    }

    /**
     * Test chunk loading/unloading for a specific room.
     *
     * Command: /fps_dev2 chunks <roomCode> <true|false>
     */
    private static int testChunkLoading(CommandContext<CommandSourceStack> context) {
        String roomCode = StringArgumentType.getString(context, "roomCode");
        boolean load = BoolArgumentType.getBool(context, "load");
        CommandSourceStack source = context.getSource();

        try {
            ServerLevel dimension = source.getLevel();
            CMInterceptorImpl interceptor = getInterceptor();

            source.sendSuccess(() -> Component.literal(
                    String.format("§e[Dev2 Test] %s chunks for room: %s",
                            load ? "Loading" : "Unloading", roomCode)
            ), true);

            // Attempt to load/unload chunks
            interceptor.setRoomChunkState(dimension, roomCode, load);

            // Check if chunks are loaded
            boolean areLoaded = interceptor.areChunksLoaded(dimension, roomCode);

            if (areLoaded == load) {
                source.sendSuccess(() -> Component.literal(
                        String.format("§a[Dev2 Test] ✓ SUCCESS: Chunks are %s",
                                areLoaded ? "LOADED" : "UNLOADED")
                ), true);
            } else {
                source.sendFailure(Component.literal(
                        String.format("§c[Dev2 Test] ✗ FAILED: Expected chunks to be %s, but they are %s",
                                load ? "LOADED" : "UNLOADED",
                                areLoaded ? "LOADED" : "UNLOADED")
                ));
            }

            LOGGER.info("Chunk test completed for room {}: load={}, actual={}", roomCode, load, areLoaded);
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal(
                    String.format("§c[Dev2 Test] ERROR: %s", e.getMessage())
            ));
            LOGGER.error("Chunk test failed for room {}", roomCode, e);
            return 0;
        }
    }

    /**
     * Test routing state changes.
     *
     * Command: /fps_dev2 routing <true|false>
     */
    private static int testRouting(CommandContext<CommandSourceStack> context) {
        boolean routeToVirtual = BoolArgumentType.getBool(context, "virtual");
        CommandSourceStack source = context.getSource();

        try {
            CMInterceptorImpl interceptor = getInterceptor();

            source.sendSuccess(() -> Component.literal(
                    String.format("§e[Dev2 Test] Setting routing to: %s",
                            routeToVirtual ? "VIRTUAL" : "PHYSICAL")
            ), true);

            interceptor.setRoutingState(routeToVirtual);

            boolean actualState = interceptor.isRoutingToVirtual();

            if (actualState == routeToVirtual) {
                source.sendSuccess(() -> Component.literal(
                        String.format("§a[Dev2 Test] ✓ SUCCESS: Routing is %s",
                                actualState ? "VIRTUAL" : "PHYSICAL")
                ), true);
            } else {
                source.sendFailure(Component.literal(
                        String.format("§c[Dev2 Test] ✗ FAILED: Expected routing to be %s, but it is %s",
                                routeToVirtual ? "VIRTUAL" : "PHYSICAL",
                                actualState ? "VIRTUAL" : "PHYSICAL")
                ));
            }

            LOGGER.info("Routing test completed: expected={}, actual={}", routeToVirtual, actualState);
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal(
                    String.format("§c[Dev2 Test] ERROR: %s", e.getMessage())
            ));
            LOGGER.error("Routing test failed", e);
            return 0;
        }
    }

    /**
     * Show diagnostics information.
     *
     * Command: /fps_dev2 diagnostics
     */
    private static int showDiagnostics(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerLevel dimension = source.getLevel();
            CMInterceptorImpl interceptor = getInterceptor();
            String diagnostics = interceptor.getDiagnostics();

            source.sendSuccess(() -> Component.literal("§e[Dev2 Test] Diagnostics:"), false);

            for (String line : diagnostics.split("\n")) {
                String finalLine = line;
                source.sendSuccess(() -> Component.literal("§7  " + finalLine), false);
            }

            // Add dimension info
            source.sendSuccess(() -> Component.literal(String.format("§7  Current Dimension: %s",
                    dimension.dimension().location())), false);

            LOGGER.info("Diagnostics: {}", diagnostics);
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal(
                    String.format("§c[Dev2 Test] ERROR: %s", e.getMessage())
            ));
            LOGGER.error("Diagnostics failed", e);
            return 0;
        }
    }

    /**
     * Run comprehensive test suite for a room.
     *
     * Command: /fps_dev2 test-room <roomCode>
     */
    private static int testRoom(CommandContext<CommandSourceStack> context) {
        String roomCode = StringArgumentType.getString(context, "roomCode");
        CommandSourceStack source = context.getSource();

        try {
            ServerLevel dimension = source.getLevel();
            CMInterceptorImpl interceptor = getInterceptor();

            source.sendSuccess(() -> Component.literal(
                    String.format("§e[Dev2 Test] Running comprehensive test for room: %s", roomCode)
            ), true);

            int passed = 0;
            int total = 5;

            // Test 1: Load chunks
            source.sendSuccess(() -> Component.literal("§7[1/5] Testing chunk loading..."), false);
            interceptor.setRoomChunkState(dimension, roomCode, true);
            if (interceptor.areChunksLoaded(dimension, roomCode)) {
                source.sendSuccess(() -> Component.literal("§a  ✓ Chunks loaded successfully"), false);
                passed++;
            } else {
                source.sendFailure(Component.literal("§c  ✗ Chunks failed to load"));
            }

            // Test 2: Verify chunks stay loaded
            source.sendSuccess(() -> Component.literal("§7[2/5] Verifying chunk persistence..."), false);
            if (interceptor.areChunksLoaded(dimension, roomCode)) {
                source.sendSuccess(() -> Component.literal("§a  ✓ Chunks remain loaded"), false);
                passed++;
            } else {
                source.sendFailure(Component.literal("§c  ✗ Chunks unexpectedly unloaded"));
            }

            // Test 3: Unload chunks
            source.sendSuccess(() -> Component.literal("§7[3/5] Testing chunk unloading..."), false);
            interceptor.setRoomChunkState(dimension, roomCode, false);
            if (!interceptor.areChunksLoaded(dimension, roomCode)) {
                source.sendSuccess(() -> Component.literal("§a  ✓ Chunks unloaded successfully"), false);
                passed++;
            } else {
                source.sendFailure(Component.literal("§c  ✗ Chunks failed to unload"));
            }

            // Test 4: Test routing to virtual
            source.sendSuccess(() -> Component.literal("§7[4/5] Testing virtual routing..."), false);
            interceptor.setRoutingState(true);
            if (interceptor.isRoutingToVirtual()) {
                source.sendSuccess(() -> Component.literal("§a  ✓ Routing set to VIRTUAL"), false);
                passed++;
            } else {
                source.sendFailure(Component.literal("§c  ✗ Routing failed to set to VIRTUAL"));
            }

            // Test 5: Test routing to physical
            source.sendSuccess(() -> Component.literal("§7[5/5] Testing physical routing..."), false);
            interceptor.setRoutingState(false);
            if (!interceptor.isRoutingToVirtual()) {
                source.sendSuccess(() -> Component.literal("§a  ✓ Routing set to PHYSICAL"), false);
                passed++;
            } else {
                source.sendFailure(Component.literal("§c  ✗ Routing failed to set to PHYSICAL"));
            }

            // Summary
            String summary = String.format("§e[Dev2 Test] Results: %d/%d tests passed", passed, total);
            if (passed == total) {
                source.sendSuccess(() -> Component.literal(summary + " §a✓ ALL TESTS PASSED"), true);
            } else {
                source.sendFailure(Component.literal(summary + String.format(" §c(%d failed)", total - passed)));
            }

            LOGGER.info("Room test completed for {}: {}/{} passed", roomCode, passed, total);
            return passed == total ? 1 : 0;

        } catch (Exception e) {
            source.sendFailure(Component.literal(
                    String.format("§c[Dev2 Test] ERROR: %s", e.getMessage())
            ));
            LOGGER.error("Room test failed for {}", roomCode, e);
            return 0;
        }
    }

    /**
     * Debug reflection lookup for a room code.
     *
     * Command: /fps_dev2 debug-reflection <roomCode>
     */
    // CHECKSTYLE.OFF: MethodLength - Debug command with extensive reflection logging
    private static int debugReflection(CommandContext<CommandSourceStack> context) {
        String roomCode = StringArgumentType.getString(context, "roomCode");
        CommandSourceStack source = context.getSource();

        try {
            ServerLevel dimension = source.getLevel();

            source.sendSuccess(() -> Component.literal(
                    String.format("§e[Dev2 Test] Debugging reflection for room: %s", roomCode)
            ), true);

            // Show dimension info
            source.sendSuccess(() -> Component.literal(String.format("§7  Dimension: %s",
                    dimension.dimension().location())), false);

            // Try to access CM's room registrar via reflection
            source.sendSuccess(() -> Component.literal("§7  Attempting to load RoomRegistrarData..."), false);

            try {
                Class<?> roomRegistrarClass = Class.forName("dev.compactmods.machines.room.RoomRegistrarData");
                source.sendSuccess(() -> Component.literal("§a  ✓ RoomRegistrarData class found"), false);

                // Get the data storage
                Object dataStorage = dimension.getServer().overworld().getDataStorage();
                source.sendSuccess(() -> Component.literal("§a  ✓ Data storage obtained"), false);

                // Get Factory type from the computeIfAbsent method signature
                Class<?> dataStorageClass = dataStorage.getClass();
                java.lang.reflect.Method computeMethod = null;
                Class<?> factoryClassTemp = null;

                for (java.lang.reflect.Method m : dataStorageClass.getMethods()) {
                    if (m.getName().equals("computeIfAbsent") && m.getParameterCount() == 2) {
                        computeMethod = m;
                        factoryClassTemp = m.getParameterTypes()[0];
                        break;
                    }
                }

                if (factoryClassTemp == null || computeMethod == null) {
                    source.sendFailure(Component.literal("§c  ✗ Could not find computeIfAbsent method"));
                    return 0;
                }

                final Class<?> factoryClass = factoryClassTemp;
                final java.lang.reflect.Method finalComputeMethod = computeMethod;

                source.sendSuccess(() -> Component.literal(String.format("§a  ✓ Found Factory type: %s",
                        factoryClass.getName())), false);

                // Create a Factory proxy that creates RoomRegistrarData instances
                Object factory = java.lang.reflect.Proxy.newProxyInstance(
                        factoryClass.getClassLoader(),
                        new Class<?>[]{factoryClass},
                        (proxy, method, args) -> {
                            try {
                                // Factory has methods: create() and parse(CompoundTag)
                                if (method.getName().equals("parse") && args != null && args.length > 0) {
                                    // Load from NBT
                                    java.lang.reflect.Method loadMethod = roomRegistrarClass.getMethod("load",
                                            Class.forName("net.minecraft.nbt.CompoundTag"),
                                            Class.forName("net.minecraft.core.HolderLookup$Provider"));
                                    return loadMethod.invoke(null, args[0], dimension.registryAccess());
                                } else {
                                    // Create new instance
                                    return roomRegistrarClass
                                            .getConstructor(net.minecraft.server.MinecraftServer.class)
                                            .newInstance(dimension.getServer());
                                }
                            } catch (Exception e) {
                                LOGGER.debug("Failed in Factory proxy: {}", e.getMessage());
                                return null;
                            }
                        }
                );
                source.sendSuccess(() -> Component.literal("§a  ✓ Factory proxy created"), false);

                Object savedData = finalComputeMethod.invoke(dataStorage, factory, "compactmachines_rooms");

                if (savedData == null) {
                    source.sendFailure(Component.literal("§c  ✗ Could not load CM room registrar data"));
                    source.sendFailure(Component.literal(
                        "§c    This usually means Compact Machines hasn't created any rooms yet"));
                    return 0;
                }

                source.sendSuccess(() -> Component.literal("§a  ✓ Room registrar data loaded"), false);

                // Try to get the specific room
                Object optionalRoomNode = roomRegistrarClass.getMethod("get", String.class)
                        .invoke(savedData, roomCode);

                boolean isPresent = (Boolean) optionalRoomNode.getClass().getMethod("isPresent")
                        .invoke(optionalRoomNode);

                if (!isPresent) {
                    source.sendFailure(Component.literal(String.format(
                            "§c  ✗ Room '%s' not found in CM registrar", roomCode)));
                    source.sendFailure(Component.literal(
                            "§c    Make sure you've entered a Compact Machine and used the correct room code"));
                    return 0;
                }

                source.sendSuccess(() -> Component.literal(
                    String.format("§a  ✓ Room '%s' found in registrar", roomCode)), false);

                // Get the room bounds
                Object roomNode = optionalRoomNode.getClass().getMethod("get").invoke(optionalRoomNode);
                Object aabb = roomNode.getClass().getMethod("outerBounds").invoke(roomNode);

                Class<?> aabbClass = Class.forName("net.minecraft.world.phys.AABB");
                double minX = aabbClass.getField("minX").getDouble(aabb);
                double maxX = aabbClass.getField("maxX").getDouble(aabb);
                double minY = aabbClass.getField("minY").getDouble(aabb);
                double maxY = aabbClass.getField("maxY").getDouble(aabb);
                double minZ = aabbClass.getField("minZ").getDouble(aabb);
                double maxZ = aabbClass.getField("maxZ").getDouble(aabb);

                int centerX = (int) ((minX + maxX) / 2.0);
                int centerY = (int) ((minY + maxY) / 2.0);
                int centerZ = (int) ((minZ + maxZ) / 2.0);

                source.sendSuccess(() -> Component.literal(String.format(
                        "§a  ✓ Room center: [%d, %d, %d]", centerX, centerY, centerZ)), false);
                source.sendSuccess(() -> Component.literal(String.format(
                        "§7    Bounds: [%.1f, %.1f, %.1f] to [%.1f, %.1f, %.1f]",
                        minX, minY, minZ, maxX, maxY, maxZ)), false);

                source.sendSuccess(() -> Component.literal("§a[Dev2 Test] ✓ Reflection working correctly"), true);
                return 1;

            } catch (ClassNotFoundException e) {
                source.sendFailure(Component.literal(String.format(
                        "§c  ✗ Could not find class: %s", e.getMessage())));
                source.sendFailure(Component.literal(
                        "§c    Is Compact Machines installed?"));
                return 0;
            } catch (Exception e) {
                source.sendFailure(Component.literal(String.format(
                        "§c  ✗ Reflection failed: %s", e.getMessage())));
                LOGGER.error("Reflection debug failed", e);
                return 0;
            }

        } catch (Exception e) {
            source.sendFailure(Component.literal(
                    String.format("§c[Dev2 Test] ERROR: %s", e.getMessage())
            ));
            LOGGER.error("Debug reflection failed for {}", roomCode, e);
            return 0;
        }
    }
    // CHECKSTYLE.ON: MethodLength

    /**
     * Clean up all chunk tickets and reset state.
     *
     * Command: /fps_dev2 cleanup
     */
    private static int cleanup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerLevel dimension = source.getLevel();
            CMInterceptorImpl interceptor = getInterceptor();

            source.sendSuccess(() -> Component.literal("§e[Dev2 Test] Cleaning up chunk tickets..."), true);

            interceptor.cleanup(dimension);

            source.sendSuccess(() -> Component.literal("§a[Dev2 Test] ✓ Cleanup complete"), true);

            LOGGER.info("Cleanup completed successfully");
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal(
                    String.format("§c[Dev2 Test] ERROR: %s", e.getMessage())
            ));
            LOGGER.error("Cleanup failed", e);
            return 0;
        }
    }

    /**
     * Give player a test PreFab with pre-configured cached rates.
     * Converts 1 dirt/tick → 1 diamond/tick.
     *
     * Command: /fps_dev2 give-test-prefab
     */
    private static int giveTestPrefab(CommandContext<CommandSourceStack> context) {
        // Default: 1 Importer (dirt input), 1 Exporter (diamond output)
        ListTag importers = new ListTag();
        CompoundTag importer1 = new CompoundTag();
        importer1.putUUID("uuid", UUID.randomUUID());
        importer1.putString("resource", "minecraft:dirt");
        importer1.putDouble("rate", 1.0);
        importers.add(importer1);

        ListTag exporters = new ListTag();
        CompoundTag exporter1 = new CompoundTag();
        exporter1.putUUID("uuid", UUID.randomUUID());
        exporter1.putString("resource", "minecraft:diamond");
        exporter1.putDouble("rate", 1.0);
        exporters.add(exporter1);

        return giveTestPrefabInternal(context, importers, exporters);
    }

    /**
     * Give player a test PreFab with custom cached rates.
     *
     * Command: /fps_dev2 give-test-prefab <inputItem> <inputRate> <outputItem> <outputRate>
     */
    private static int giveTestPrefabCustom(CommandContext<CommandSourceStack> context) {
        String inputItem = StringArgumentType.getString(context, "inputItem");
        double inputRate = DoubleArgumentType.getDouble(context, "inputRate");
        String outputItem = StringArgumentType.getString(context, "outputItem");
        double outputRate = DoubleArgumentType.getDouble(context, "outputRate");

        // Create 1 Importer and 1 Exporter with custom rates
        ListTag importers = new ListTag();
        CompoundTag importer1 = new CompoundTag();
        importer1.putUUID("uuid", UUID.randomUUID());
        importer1.putString("resource", inputItem);
        importer1.putDouble("rate", inputRate);
        importers.add(importer1);

        ListTag exporters = new ListTag();
        CompoundTag exporter1 = new CompoundTag();
        exporter1.putUUID("uuid", UUID.randomUUID());
        exporter1.putString("resource", outputItem);
        exporter1.putDouble("rate", outputRate);
        exporters.add(exporter1);

        return giveTestPrefabInternal(context, importers, exporters);
    }

    /**
     * Give player a test PreFab with multiple inputs/outputs from comma-separated lists.
     *
     * Command: /fps_dev2 give-test-prefab list <inputList> <outputList>
     * Format: "item:rate,item:rate,..."
     * Example: /fps_dev2 give-test-prefab list "minecraft:dirt:1.0,minecraft:cobblestone:2.0" "minecraft:diamond:0.5"
     *
     * Note: Each item in the list creates a separate Importer/Exporter with a unique UUID
     */
    private static int giveTestPrefabList(CommandContext<CommandSourceStack> context) {
        String inputListStr = StringArgumentType.getString(context, "inputList");
        String outputListStr = StringArgumentType.getString(context, "outputList");
        CommandSourceStack source = context.getSource();

        try {
            ListTag importers = parseEquipmentList(inputListStr, true);
            ListTag exporters = parseEquipmentList(outputListStr, false);

            if (importers.isEmpty() && exporters.isEmpty()) {
                source.sendFailure(Component.literal(
                    "§c[Dev2 Test] ERROR: Must specify at least one input or output"));
                return 0;
            }

            return giveTestPrefabInternal(context, importers, exporters);

        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(
                String.format("§c[Dev2 Test] ERROR: %s", e.getMessage())
            ));
            source.sendFailure(Component.literal(
                "§c  Format: \"item:rate,item:rate,...\""
            ));
            source.sendFailure(Component.literal(
                "§c  Example: \"minecraft:dirt:1.0,minecraft:cobblestone:2.0\""
            ));
            return 0;
        }
    }

    /**
     * Parse a comma-separated list of "item:rate" pairs into Importer/Exporter equipment NBT.
     * Each entry creates a separate equipment piece with a unique UUID.
     *
     * @param listStr The input string (e.g., "minecraft:dirt:1.0,minecraft:cobblestone:2.0")
     * @param isImporter True for Importers (inputs), false for Exporters (outputs)
     * @return ListTag containing equipment NBT (uuid, resource, rate)
     * @throws IllegalArgumentException if the format is invalid
     */
    private static ListTag parseEquipmentList(String listStr, boolean isImporter) throws IllegalArgumentException {
        ListTag equipmentList = new ListTag();

        if (listStr == null || listStr.trim().isEmpty()) {
            return equipmentList;
        }

        String[] entries = listStr.split(",");
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            // Split by last colon to handle namespaced IDs like "minecraft:dirt:1.0"
            int lastColon = entry.lastIndexOf(':');
            if (lastColon == -1 || lastColon == entry.length() - 1) {
                throw new IllegalArgumentException(
                    String.format("Invalid format for entry '%s' - expected 'item:rate'", entry)
                );
            }

            String itemId = entry.substring(0, lastColon);
            String rateStr = entry.substring(lastColon + 1);

            // Validate item ID has namespace
            if (!itemId.contains(":")) {
                throw new IllegalArgumentException(
                    String.format("Item ID '%s' must include namespace (e.g., 'minecraft:dirt')", itemId)
                );
            }

            double rate;
            try {
                rate = Double.parseDouble(rateStr);
                if (rate <= 0.0) {
                    throw new IllegalArgumentException(
                        String.format("Rate '%s' must be positive", rateStr)
                    );
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    String.format("Invalid rate '%s' for item '%s' - must be a number", rateStr, itemId)
                );
            }

            // Create equipment entry: UUID + resource + rate
            CompoundTag equipment = new CompoundTag();
            equipment.putUUID("uuid", UUID.randomUUID());
            equipment.putString("resource", itemId);
            equipment.putDouble("rate", rate);
            equipmentList.add(equipment);
        }

        return equipmentList;
    }

    /**
     * Internal method to create and give a test PreFab with UUID-based rate storage.
     *
     * @param context The command context
     * @param importers ListTag of Importer equipment (each has uuid, resource, rate)
     * @param exporters ListTag of Exporter equipment (each has uuid, resource, rate)
     * @return 1 on success, 0 on failure
     */
    private static int giveTestPrefabInternal(CommandContext<CommandSourceStack> context,
                                               ListTag importers, ListTag exporters) {
        CommandSourceStack source = context.getSource();

        try {
            // Must be executed by a player
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("§c[Dev2 Test] This command must be run by a player"));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("§e[Dev2 Test] Creating test PreFab..."), false);

            // Create PreFab item with test data
            ItemStack prefabItem = new ItemStack(FPSCompress.PREFAB_ITEM.get());
            CompoundTag nbt = buildTestPrefabNBT(importers, exporters);

            LOGGER.info("Created test PreFab NBT - schemaVersion: {}, has importerExporterRates: {}, entries: {}",
                nbt.getInt("schemaVersion"),
                nbt.contains("importerExporterRates"),
                nbt.contains("importerExporterRates") ? nbt.getList("importerExporterRates", 10).size() : 0);

            prefabItem.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(nbt));

            // Give item to player
            if (player.addItem(prefabItem)) {
                sendSuccessMessages(source, importers, exporters, player);
                return 1;
            } else {
                source.sendFailure(Component.literal(
                    "§c[Dev2 Test] Failed to give item (inventory full?)"));
                return 0;
            }

        } catch (Exception e) {
            source.sendFailure(Component.literal(
                    String.format("§c[Dev2 Test] ERROR: %s", e.getMessage())
            ));
            LOGGER.error("give-test-prefab failed", e);
            return 0;
        }
    }

    /**
     * Build NBT data for a test PreFab with UUID-based rate storage.
     */
    private static CompoundTag buildTestPrefabNBT(ListTag importers, ListTag exporters) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("schemaVersion", 2);  // Schema v2 = per-UUID rates (importerExporterRates)
        nbt.putString("state", "CACHED");

        // Generate unique fake roomCode for this test PreFab
        // Format: "fake_<uuid>" so we know it's a test room and can isolate equipment per PreFab
        String fakeRoomCode = "fake_" + UUID.randomUUID().toString().substring(0, 8);
        nbt.putString("roomCode", fakeRoomCode);

        // Face configs: Link faces to equipment UUIDs
        nbt.put("faceConfigs", buildFaceConfigs(importers, exporters));

        // UUID-based rate storage
        nbt.put("importerExporterRates", buildUUIDRates(importers, exporters));

        // Fake Importer/Exporter registries with frequency items
        nbt.put("importerRegistry", buildImporterRegistry(importers, fakeRoomCode));
        nbt.put("exporterRegistry", buildExporterRegistry(exporters, fakeRoomCode));

        // Block entity ID
        nbt.putString("id", "fpscompress:prefab_machine");

        return nbt;
    }

    /**
     * Build face configurations linking faces to Importer/Exporter UUIDs.
     */
    private static CompoundTag buildFaceConfigs(ListTag importers, ListTag exporters) {
        CompoundTag facesTag = new CompoundTag();

        // Link NORTH faces to Importers (PULL mode)
        String[] inputFaces = {"north", "west", "down"};
        for (int i = 0; i < Math.min(importers.size(), 3); i++) {
            CompoundTag importer = importers.getCompound(i);
            UUID uuid = importer.getUUID("uuid");

            CompoundTag faceConfig = new CompoundTag();
            faceConfig.putString("mode", "PULL");
            faceConfig.putString("resourceType", "ITEMS");
            faceConfig.putUUID("targetUUID", uuid);
            facesTag.put(inputFaces[i], faceConfig);
        }

        // Link SOUTH faces to Exporters (PUSH mode)
        String[] outputFaces = {"south", "east", "up"};
        for (int i = 0; i < Math.min(exporters.size(), 3); i++) {
            CompoundTag exporter = exporters.getCompound(i);
            UUID uuid = exporter.getUUID("uuid");

            CompoundTag faceConfig = new CompoundTag();
            faceConfig.putString("mode", "PUSH");
            faceConfig.putString("resourceType", "ITEMS");
            faceConfig.putUUID("targetUUID", uuid);
            facesTag.put(outputFaces[i], faceConfig);
        }

        return facesTag;
    }

    /**
     * Build UUID-based rate storage: Map<UUID, Map<String, Double>>.
     */
    private static ListTag buildUUIDRates(ListTag importers, ListTag exporters) {
        ListTag uuidRatesList = new ListTag();

        // Add Importer rates (negative = consumption)
        for (int i = 0; i < importers.size(); i++) {
            CompoundTag importer = importers.getCompound(i);
            UUID uuid = importer.getUUID("uuid");
            String resource = importer.getString("resource");
            double rate = importer.getDouble("rate");

            CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID("uuid", uuid);

            ListTag resourceRatesList = new ListTag();
            CompoundTag rateTag = new CompoundTag();
            rateTag.putString("id", resource);
            rateTag.putDouble("rate", -Math.abs(rate)); // Negative for inputs
            resourceRatesList.add(rateTag);

            uuidTag.put("rates", resourceRatesList);
            uuidRatesList.add(uuidTag);
        }

        // Add Exporter rates (positive = production)
        for (int i = 0; i < exporters.size(); i++) {
            CompoundTag exporter = exporters.getCompound(i);
            UUID uuid = exporter.getUUID("uuid");
            String resource = exporter.getString("resource");
            double rate = exporter.getDouble("rate");

            CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID("uuid", uuid);

            ListTag resourceRatesList = new ListTag();
            CompoundTag rateTag = new CompoundTag();
            rateTag.putString("id", resource);
            rateTag.putDouble("rate", Math.abs(rate)); // Positive for outputs
            resourceRatesList.add(rateTag);

            uuidTag.put("rates", resourceRatesList);
            uuidRatesList.add(uuidTag);
        }

        return uuidRatesList;
    }

    /**
     * Build fake Importer registry with frequency items matching resources.
     *
     * @param importers ListTag of Importer equipment
     * @param roomCode Unique fake roomCode for this test PreFab (e.g., "fake_a1b2c3d4")
     * @return CompoundTag registry mapping UUID → Importer data
     */
    private static CompoundTag buildImporterRegistry(ListTag importers, String roomCode) {
        CompoundTag registry = new CompoundTag();

        for (int i = 0; i < importers.size(); i++) {
            CompoundTag importer = importers.getCompound(i);
            UUID uuid = importer.getUUID("uuid");
            String resource = importer.getString("resource");

            // Create fake Importer data with frequency item
            CompoundTag importerData = new CompoundTag();
            importerData.putUUID("uuid", uuid);
            importerData.putString("roomCode", roomCode);  // Use unique roomCode per PreFab

            // Set frequency item ID to match the resource (simple string storage)
            CompoundTag frequencyItemTag = new CompoundTag();
            frequencyItemTag.putString("id", resource);
            frequencyItemTag.putInt("count", 1);
            importerData.put("frequencyItem", frequencyItemTag);

            registry.put(uuid.toString(), importerData);
        }

        return registry;
    }

    /**
     * Build fake Exporter registry with frequency items matching resources.
     *
     * @param exporters ListTag of Exporter equipment
     * @param roomCode Unique fake roomCode for this test PreFab (e.g., "fake_a1b2c3d4")
     * @return CompoundTag registry mapping UUID → Exporter data
     */
    private static CompoundTag buildExporterRegistry(ListTag exporters, String roomCode) {
        CompoundTag registry = new CompoundTag();

        for (int i = 0; i < exporters.size(); i++) {
            CompoundTag exporter = exporters.getCompound(i);
            UUID uuid = exporter.getUUID("uuid");
            String resource = exporter.getString("resource");

            // Create fake Exporter data with frequency item
            CompoundTag exporterData = new CompoundTag();
            exporterData.putUUID("uuid", uuid);
            exporterData.putString("roomCode", roomCode);  // Use unique roomCode per PreFab

            // Set frequency item ID to match the resource (simple string storage)
            CompoundTag frequencyItemTag = new CompoundTag();
            frequencyItemTag.putString("id", resource);
            frequencyItemTag.putInt("count", 1);
            exporterData.put("frequencyItem", frequencyItemTag);

            registry.put(uuid.toString(), exporterData);
        }

        return registry;
    }

    /**
     * Send success messages to player with summary of configured rates.
     */
    private static void sendSuccessMessages(CommandSourceStack source, ListTag importers,
                                            ListTag exporters, ServerPlayer player) {
        source.sendSuccess(() -> Component.literal(
            "§a[Dev2 Test] ✓ Test PreFab given! Hover over it to see the tooltip"), true);

        // Build summary message
        StringBuilder summary = new StringBuilder("§7  ");

        if (!importers.isEmpty()) {
            summary.append("Inputs: ");
            for (int i = 0; i < importers.size(); i++) {
                CompoundTag importer = importers.getCompound(i);
                String resource = importer.getString("resource");
                double rate = importer.getDouble("rate");
                String itemName = resource.contains(":") ? resource.split(":")[1] : resource;

                if (i > 0) {
                    summary.append(", ");
                }
                summary.append(String.format("%.2f %s/tick", rate, itemName));
            }
        }

        if (!exporters.isEmpty()) {
            if (!importers.isEmpty()) {
                summary.append(" → ");
            }
            summary.append("Outputs: ");
            for (int i = 0; i < exporters.size(); i++) {
                CompoundTag exporter = exporters.getCompound(i);
                String resource = exporter.getString("resource");
                double rate = exporter.getDouble("rate");
                String itemName = resource.contains(":") ? resource.split(":")[1] : resource;

                if (i > 0) {
                    summary.append(", ");
                }
                summary.append(String.format("%.2f %s/tick", rate, itemName));
            }
        }

        String finalSummary = summary.toString();
        source.sendSuccess(() -> Component.literal(finalSummary), false);

        LOGGER.info("Test PreFab given to player {} with {} Importers and {} Exporters",
            player.getName().getString(), importers.size(), exporters.size());
    }
}
