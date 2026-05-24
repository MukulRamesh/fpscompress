package com.mukulramesh.fpscompress.portal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.mukulramesh.fpscompress.Config;
import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.scanner.InventoryScanner;
import com.mukulramesh.fpscompress.spatial.CMInterceptorImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;


/**
 * Manages state transitions for PreFab blocks.
 * Handles BUILDING → SIMULATING → CACHED → HALTED state machine.
 */
public class StateTransitionManager {
    private final PrefabBlockEntity entity;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
        justification = "Service class intentionally holds reference to entity for delegation pattern")
    public StateTransitionManager(PrefabBlockEntity entity) {
        this.entity = entity;
    }

    /**
     * Transition BUILDING → SIMULATING.
     * Performs initial inventory scan, then loads CM chunks and starts rate measurement.
     *
     * @param player The player starting the simulation (for debug chat output)
     */
    public void startSimulation(@Nullable Player player) {
        if (entity.currentState != MachineState.BUILDING) {
            FPSCompress.LOGGER.warn("Cannot start simulation from state {}", entity.currentState);
            return;
        }

        ServerLevel cmLevel = getCMLevel();
        if (cmLevel == null || entity.roomCode == null) {
            FPSCompress.LOGGER.error("Cannot start simulation: CM dimension or roomCode not available");
            return;
        }

        // Ensure room center is cached (needed for chunk loading)
        if (entity.roomCenter != null && entity.getLevel() != null && !entity.getLevel().isClientSide()) {
            net.minecraft.server.MinecraftServer server = entity.getLevel().getServer();
            if (server != null) {
                RoomCoordinateCache cache = RoomCoordinateCache.get(server);
                cache.setRoomCenter(entity.getBlockPos(), entity.roomCode, entity.roomCenter);
                FPSCompress.LOGGER.debug("Cached room center {} for room {}",
                    entity.roomCenter, entity.roomCode);
            }
        }

        // Phase 1: Load chunks temporarily for initial scan
        CMInterceptorImpl interceptor = CMInterceptorImpl.getInstance();
        interceptor.setRoomChunkState(cmLevel, entity.roomCode, true);
        FPSCompress.LOGGER.info("PreFab at {} starting initial scan (chunks loaded)", entity.getBlockPos());

        try {
            AABB roomBounds = entity.getScanningService().getRoomBoundsFromCM(entity.roomCode);

            // Get room center from cache for scanner
            net.minecraft.server.MinecraftServer server = entity.getLevel().getServer();
            RoomCoordinateCache cache = RoomCoordinateCache.get(server);
            BlockPos roomCtr = cache.getRoomCenterByRoomCode(entity.roomCode);

            // Phase 2: Async scan (off main thread, game stays playable)
            InventoryScanner.scanRoomAsync(cmLevel, roomCtr, roomBounds)
                .thenAccept(inventory -> {
                    // Callback to main thread
                    entity.getLevel().getServer().execute(() -> {
                        try {
                            // Phase 3a: Add Importer/Exporter buffer contents to inventory
                            entity.getScanningService().scanImporterExporterBuffers(cmLevel, inventory);
                            FPSCompress.LOGGER.info("Added Importer/Exporter buffer contents to initial scan");

                            // Phase 3b: Store initial state (including buffers)
                            entity.deltaTracker = new ResourceDeltaTracker();
                            entity.deltaTracker.captureInitialState(inventory);
                            FPSCompress.LOGGER.info("Initial scan complete: {} resource types (includes buffers)",
                                inventory.size());

                            // Debug: Send scan results to player chat
                            entity.getScanningService().sendInventoryScanToChat(player, inventory, "Initial");

                            // Phase 4: Unload chunks (return to deterministic state)
                            interceptor.setRoomChunkState(cmLevel, entity.roomCode, false);

                            // Phase 5: Load chunks and start simulation
                            interceptor.setRoomChunkState(cmLevel, entity.roomCode, true);

                            // Phase 6: Clear previous result and enter SIMULATING
                            entity.lastSimulationResult = "";
                            entity.simulationStartTick = entity.getLevel().getGameTime();

                            // Capture config value at simulation start (snapshot behavior)
                            entity.simulationRequiredTicks = Config.SERVER.getMinimumSimulationTicks();
                            entity.simulationElapsedTicks = 0; // Reset counter
                            FPSCompress.LOGGER.debug("Simulation started with minimum time requirement: {} ticks",
                                entity.simulationRequiredTicks);

                            entity.setCurrentState(MachineState.SIMULATING);

                            FPSCompress.LOGGER.info("PreFab at {} entered SIMULATING state", entity.getBlockPos());
                        } catch (Exception e) {
                            FPSCompress.LOGGER.error("Failed to complete initial scan", e);
                        }
                    });
                })
                .exceptionally(ex -> {
                    entity.getLevel().getServer().execute(() -> {
                        FPSCompress.LOGGER.error("Initial scan failed for PreFab at {}", entity.getBlockPos(), ex);
                        interceptor.setRoomChunkState(cmLevel, entity.roomCode, false); // Cleanup
                    });
                    return null;
                });
        } catch (Exception e) {
            FPSCompress.LOGGER.error("Failed to start initial scan", e);
            interceptor.setRoomChunkState(cmLevel, entity.roomCode, false); // Cleanup
        }
    }

    /**
     * Transition SIMULATING → CACHED.
     * Performs final inventory scan, calculates rates using full formula, unloads CM chunks.
     *
     * @param player The player finishing the simulation (for debug chat output)
     */
    public void finishSimulation(@Nullable Player player) {
        if (entity.currentState != MachineState.SIMULATING) {
            FPSCompress.LOGGER.warn("Cannot finish simulation from state {}", entity.currentState);
            return;
        }

        ServerLevel cmLevel = getCMLevel();
        if (cmLevel == null || entity.roomCode == null) {
            FPSCompress.LOGGER.error("Cannot finish simulation: CM dimension or roomCode not available");
            return;
        }

        // Creative mode bypass: Allow instant finish
        if (player != null && isCreativeMode(player)) {
            FPSCompress.LOGGER.debug("Creative mode player finishing simulation (bypassed minimum time)");
            // Continue to normal finish logic
        } else {
            // Survival mode: Enforce minimum time
            if (entity.simulationElapsedTicks < entity.simulationRequiredTicks) {
                long remainingTicks = entity.simulationRequiredTicks - entity.simulationElapsedTicks;
                long remainingSeconds = remainingTicks / 20;
                long totalMinutes = entity.simulationRequiredTicks / 1200;

                String timeMessage = String.format(
                    "Simulation incomplete. Minimum time: %d minutes (%d seconds remaining)",
                    totalMinutes,
                    remainingSeconds
                );

                if (player != null) {
                    player.displayClientMessage(
                        Component.literal("§c" + timeMessage),
                        false
                    );
                }

                FPSCompress.LOGGER.info("Finish simulation blocked: {} ticks remaining", remainingTicks);
                return; // Abort finish
            }
        }

        // Check if any activity occurred
        if (!entity.deltaTracker.hasActivity()) {
            FPSCompress.LOGGER.warn("No activity detected - entering HALTED state");
            entity.lastSimulationResult = "No activity";
            entity.setCurrentState(MachineState.HALTED);
            return;
        }

        // Use activity-based time window (first input to last output)
        entity.simulationStartTick = entity.deltaTracker.getFirstActivityTick();
        entity.simulationEndTick = entity.deltaTracker.getLastActivityTick();

        // Phase 1: Unload chunks (stop factory)
        CMInterceptorImpl interceptor = CMInterceptorImpl.getInstance();
        interceptor.setRoomChunkState(cmLevel, entity.roomCode, false);
        FPSCompress.LOGGER.info("PreFab at {} unloaded chunks for final scan", entity.getBlockPos());

        // Phase 2: Load chunks temporarily for final scan
        interceptor.setRoomChunkState(cmLevel, entity.roomCode, true);

        try {
            AABB roomBounds = entity.getScanningService().getRoomBoundsFromCM(entity.roomCode);

            // Get room center from cache for scanner
            net.minecraft.server.MinecraftServer server = entity.getLevel().getServer();
            RoomCoordinateCache cache = RoomCoordinateCache.get(server);
            BlockPos roomCtr = cache.getRoomCenterByRoomCode(entity.roomCode);

            // Phase 3: Async final scan (game stays playable)
            InventoryScanner.scanRoomAsync(cmLevel, roomCtr, roomBounds)
                .thenAccept(finalInventory -> {
                    entity.getLevel().getServer().execute(() -> {
                        try {
                            // Phase 4a: Add Importer/Exporter buffer contents to final inventory
                            entity.getScanningService().scanImporterExporterBuffers(cmLevel, finalInventory);
                            FPSCompress.LOGGER.info("Added Importer/Exporter buffer contents to final scan");

                            // Phase 4b: Store final state (including buffers)
                            entity.deltaTracker.captureFinalState(finalInventory);
                            FPSCompress.LOGGER.info("Final scan complete: {} resource types (includes buffers)",
                                finalInventory.size());

                            // Debug: Send scan results to player chat
                            entity.getScanningService().sendInventoryScanToChat(player, finalInventory, "Final");

                            // Phase 5: Unload chunks (return to unloaded state)
                            interceptor.setRoomChunkState(cmLevel, entity.roomCode, false);

                            // Phase 6: Calculate rates using FULL formula
                            entity.getRateEngine().calculateRatesAndTransition();

                        } catch (Exception e) {
                            FPSCompress.LOGGER.error("Failed to process final scan", e);
                            entity.lastSimulationResult = "Scan processing failed";
                            entity.setCurrentState(MachineState.HALTED);
                            entity.setChanged();
                        }
                    });
                })
                .exceptionally(ex -> {
                    entity.getLevel().getServer().execute(() -> {
                        FPSCompress.LOGGER.error("Final scan failed", ex);
                        entity.lastSimulationResult = "Scan failed";
                        entity.setCurrentState(MachineState.HALTED);
                        interceptor.setRoomChunkState(cmLevel, entity.roomCode, false); // Cleanup
                        entity.setChanged();
                    });
                    return null;
                });
        } catch (Exception e) {
            FPSCompress.LOGGER.error("Failed to start final scan", e);
            entity.lastSimulationResult = "Failed to start final scan";
            entity.setCurrentState(MachineState.HALTED);
            interceptor.setRoomChunkState(cmLevel, entity.roomCode, false);
            entity.setChanged();
        }
    }

    /**
     * Transition CACHED/HALTED → BUILDING.
     * Clears rates, keeps CM chunks UNLOADED (player must manually enter to reconfigure).
     */
    public void resetToBuilding() {
        if (entity.currentState != MachineState.CACHED && entity.currentState != MachineState.HALTED) {
            FPSCompress.LOGGER.warn("Cannot reset from state {}", entity.currentState);
            return;
        }

        // Clear cached rates and accumulators
        entity.clearCachedRates();
        entity.clearImporterExporterRates(); // Phase 6: Clear per-UUID rates
        entity.deltaTracker = new ResourceDeltaTracker();
        entity.itemAccumulators.clear(); // Phase 5: Clear fractional accumulators
        entity.cachedProduction.clear();

        // Clear display preferences
        entity.currentDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK;
        entity.focusedResourceId = null;
        entity.autoNormalizedTicks = 1;
        entity.useAutoNormalize = true;
        entity.autoNormalizedDisplayMode = com.mukulramesh.fpscompress.gui.RateDisplayMode.PER_TICK;

        // Transition state
        entity.setCurrentState(MachineState.BUILDING);

        FPSCompress.LOGGER.info("PreFab at {} reset to BUILDING mode (chunks remain unloaded)", entity.getBlockPos());
    }

    /**
     * Transition HALTED → SIMULATING.
     * Resumes measurement after player fixes inputs/outputs.
     */
    public void resumeSimulation() {
        if (entity.currentState != MachineState.HALTED) {
            FPSCompress.LOGGER.warn("Cannot resume from state {}", entity.currentState);
            return;
        }

        // Treat as fresh simulation start
        entity.setCurrentState(MachineState.BUILDING);
        startSimulation(null); // No player context (unused method)

        FPSCompress.LOGGER.info("PreFab at {} resumed simulation", entity.getBlockPos());
    }

    /**
     * Check if player is in creative mode.
     */
    private boolean isCreativeMode(Player player) {
        return player != null && player.getAbilities().instabuild;
    }

    /**
     * Get CM dimension level.
     *
     * @return The CM dimension ServerLevel, or null if not available
     */
    @Nullable
    private ServerLevel getCMLevel() {
        if (entity.getLevel() == null || entity.getLevel().isClientSide()) {
            return null;
        }
        net.minecraft.server.MinecraftServer server = entity.getLevel().getServer();
        if (server == null) {
            return null;
        }
        return server.getLevel(
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse("compactmachines:compact_world")
            )
        );
    }
}
