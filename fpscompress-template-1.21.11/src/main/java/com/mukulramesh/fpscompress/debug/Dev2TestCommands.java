package com.mukulramesh.fpscompress.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
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
 * - /fps_dev2 give-test-prefab - Give PreFab with dirt→diamond conversion rates
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
        CommandSourceStack source = context.getSource();

        try {
            // Must be executed by a player
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("§c[Dev2 Test] This command must be run by a player"));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("§e[Dev2 Test] Creating test PreFab..."), false);

            // Create PreFab item
            ItemStack prefabItem = new ItemStack(FPSCompress.PREFAB_ITEM.get());

            // Build NBT data for CACHED PreFab
            CompoundTag nbt = new CompoundTag();

            // Schema version
            nbt.putInt("schemaVersion", 1);

            // Machine state: CACHED
            nbt.putString("state", "CACHED");

            // Room code (fake)
            nbt.putString("roomCode", "test_5x5x5");

            // Face configs (3 faces configured: 1 PULL, 2 PUSH)
            CompoundTag facesTag = new CompoundTag();

            // NORTH face: PULL (input)
            CompoundTag northFace = new CompoundTag();
            northFace.putString("mode", "PULL");
            northFace.putString("resourceType", "ITEMS");
            facesTag.put("north", northFace);

            // SOUTH face: PUSH (output)
            CompoundTag southFace = new CompoundTag();
            southFace.putString("mode", "PUSH");
            southFace.putString("resourceType", "ITEMS");
            facesTag.put("south", southFace);

            // EAST face: PUSH (secondary output)
            CompoundTag eastFace = new CompoundTag();
            eastFace.putString("mode", "PUSH");
            eastFace.putString("resourceType", "ITEMS");
            facesTag.put("east", eastFace);

            nbt.put("faceConfigs", facesTag);

            // Cached rates: INPUT 1 dirt/tick, OUTPUT 1 diamond/tick
            ListTag ratesList = new ListTag();

            // Input: minecraft:dirt at -1.0/tick (negative = consumption)
            CompoundTag dirtRate = new CompoundTag();
            dirtRate.putString("id", "minecraft:dirt");
            dirtRate.putDouble("rate", -1.0);
            ratesList.add(dirtRate);

            // Output: minecraft:diamond at 1.0/tick (positive = production)
            CompoundTag diamondRate = new CompoundTag();
            diamondRate.putString("id", "minecraft:diamond");
            diamondRate.putDouble("rate", 1.0);
            ratesList.add(diamondRate);

            nbt.put("rates", ratesList);

            // Add block entity ID (required for proper loading)
            nbt.putString("id", "fpscompress:prefab_machine");

            // Set NBT on item using DataComponents system
            prefabItem.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(nbt));

            // Give item to player
            if (player.addItem(prefabItem)) {
                source.sendSuccess(() -> Component.literal(
                    "§a[Dev2 Test] ✓ Test PreFab given! Hover over it to see the tooltip"), true);
                source.sendSuccess(() -> Component.literal(
                    "§7  Input: 1 Dirt/tick → Output: 1 Diamond/tick"), false);
                LOGGER.info("Test PreFab given to player {}", player.getName().getString());
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
}
