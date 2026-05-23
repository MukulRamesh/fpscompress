package com.mukulramesh.fpscompress.scanner;

import com.mojang.logging.LogUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Utility for scanning all BlockEntity inventories in a CM room.
 * Used for initial/final state capture in Enhanced Delta Accounting.
 *
 * <p>Performance optimization: Only iterates BlockEntities (not all blocks),
 * reducing scan from ~3,375 blocks to ~50 BlockEntities in typical room.
 *
 * @see <a href="../../../../../../VALIDATION_DELTA_ACCOUNTING.md">VALIDATION_DELTA_ACCOUNTING.md</a>
 */
public final class InventoryScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private InventoryScanner() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Scan room asynchronously with tick spreading to avoid lag spikes.
     * Scans up to BLOCK_ENTITIES_PER_TICK entities per tick.
     *
     * @param cmLevel The CM dimension level
     * @param roomCenter Center of the CM room (unused currently, kept for future optimization)
     * @param roomBounds AABB bounds of the room
     * @return CompletableFuture with map of resource ID to total quantity
     */
    public static CompletableFuture<Map<String, Long>> scanRoomAsync(
            ServerLevel cmLevel,
            BlockPos roomCenter,
            AABB roomBounds
    ) {
        CompletableFuture<Map<String, Long>> future = new CompletableFuture<>();

        // Collect all BlockEntity positions first (fast, no capabilities)
        java.util.List<BlockPos> positionsToScan = new java.util.ArrayList<>();

        try {
            ChunkPos minChunk = new ChunkPos(new BlockPos((int) roomBounds.minX, 0, (int) roomBounds.minZ));
            ChunkPos maxChunk = new ChunkPos(new BlockPos((int) roomBounds.maxX, 0, (int) roomBounds.maxZ));

            for (int cx = minChunk.x; cx <= maxChunk.x; cx++) {
                for (int cz = minChunk.z; cz <= maxChunk.z; cz++) {
                    LevelChunk chunk = cmLevel.getChunk(cx, cz);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getBlockPos();
                        if (roomBounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                            positionsToScan.add(pos.immutable());
                        }
                    }
                }
            }

            LOGGER.info("=== SCAN: Found {} BlockEntities, starting tick-spread scan ===",
                positionsToScan.size());

            // Start tick-spread scanning
            scanWithTickSpreading(cmLevel, positionsToScan, future);

        } catch (RuntimeException e) {
            LOGGER.error("Failed to collect BlockEntity positions", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    private static final int BLOCK_ENTITIES_PER_TICK = 20; // Scan 20 BE per tick max

    /**
     * Scan BlockEntities with tick spreading (20 per tick).
     */
    private static void scanWithTickSpreading(
            ServerLevel cmLevel,
            java.util.List<BlockPos> positions,
            CompletableFuture<Map<String, Long>> future
    ) {
        Map<String, Long> totals = new HashMap<>();
        scanNextBatch(cmLevel, positions, 0, totals, future);
    }

    /**
     * Scan one batch of BlockEntities, then schedule next batch for next tick.
     */
    private static void scanNextBatch(
            ServerLevel cmLevel,
            java.util.List<BlockPos> positions,
            int startIndex,
            Map<String, Long> totals,
            CompletableFuture<Map<String, Long>> future
    ) {
        try {
            int endIndex = Math.min(startIndex + BLOCK_ENTITIES_PER_TICK, positions.size());

            // Scan this batch
            for (int i = startIndex; i < endIndex; i++) {
                BlockPos pos = positions.get(i);
                scanBlockEntity(cmLevel, pos, totals);
            }

            LOGGER.debug("Scanned batch {}-{} of {} BlockEntities",
                startIndex, endIndex - 1, positions.size());

            // Check if done
            if (endIndex >= positions.size()) {
                LOGGER.info("=== SCAN COMPLETE: {} resources found ===", totals.size());
                future.complete(totals);
            } else {
                // Schedule next batch for next tick
                int nextStart = endIndex;
                cmLevel.getServer().tell(new net.minecraft.server.TickTask(
                    cmLevel.getServer().getTickCount() + 1,
                    () -> scanNextBatch(cmLevel, positions, nextStart, totals, future)
                ));
            }

        } catch (RuntimeException e) {
            LOGGER.error("Failed during batch scan", e);
            future.completeExceptionally(e);
        }
    }

    /**
     * Scan room synchronously (main thread, blocking).
     * Only use this for testing or when async is not possible.
     *
     * @param cmLevel The CM dimension level
     * @param roomCenter Center of the CM room (unused currently, kept for future optimization)
     * @param roomBounds AABB bounds of the room
     * @return Map of resource ID to total quantity
     */
    public static Map<String, Long> scanRoomSync(
            ServerLevel cmLevel,
            BlockPos roomCenter,
            AABB roomBounds
    ) {
        Map<String, Long> totals = new HashMap<>();
        int scannedCount = 0;
        int totalBlockEntities = 0;

        try {
            // Get chunk range from room bounds
            ChunkPos minChunk = new ChunkPos(new BlockPos((int) roomBounds.minX, 0, (int) roomBounds.minZ));
            ChunkPos maxChunk = new ChunkPos(new BlockPos((int) roomBounds.maxX, 0, (int) roomBounds.maxZ));

            // Iterate only chunks that intersect room bounds
            for (int cx = minChunk.x; cx <= maxChunk.x; cx++) {
                for (int cz = minChunk.z; cz <= maxChunk.z; cz++) {
                    LevelChunk chunk = cmLevel.getChunk(cx, cz);
                    totalBlockEntities += chunk.getBlockEntities().size();

                    // Iterate only BlockEntities in this chunk (performance optimization)
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getBlockPos();

                        // Check if BlockEntity is within room bounds
                        if (roomBounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                            scanBlockEntity(cmLevel, pos, totals);
                            scannedCount++;
                        }
                    }
                }
            }

            LOGGER.info("=== SCAN COMPLETE: Found {} total BlockEntities, scanned {} within bounds ===",
                    totalBlockEntities, scannedCount);
            LOGGER.info("Total resources found: {}", totals.size());
            return totals;

        } catch (RuntimeException e) {
            LOGGER.error("Failed to scan room at bounds {}", roomBounds, e);
            return totals; // Return partial results
        }
    }

    /**
     * Scan single BlockEntity and aggregate capabilities.
     * Queries with null direction to get full inventory access (all slots, regardless of sides).
     *
     * <p>SpotBugs suppression: We intentionally pass null as the direction to getCapability(),
     * which is the correct way to query capabilities without side restrictions in NeoForge.
     * This is necessary for sided inventories like furnaces where different slots are
     * exposed from different sides (fuel slot from DOWN, input from UP, output from SIDE).
     * Passing null returns a capability that exposes ALL slots regardless of side.
     *
     * @param level The server level (needed for capability queries)
     * @param pos The BlockEntity position
     * @param totals Accumulator map (modified in-place)
     */
    @SuppressFBWarnings(
            value = {"NP_LOAD_OF_KNOWN_NULL_VALUE", "NP_NONNULL_PARAM_VIOLATION"},
            justification = "Null direction is valid for getCapability - returns unrestricted capability with all slots"
    )
    private static void scanBlockEntity(ServerLevel level, BlockPos pos, Map<String, Long> totals) {
        try {
            // Use null direction to get full inventory access (not side-restricted)
            // This ensures we see ALL slots in sided inventories (e.g., furnace fuel slot)
            net.minecraft.core.Direction queryDir = null;

            // Query Items capability
            IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, queryDir);
            if (itemHandler != null) {
                int slots = itemHandler.getSlots();
                for (int slot = 0; slot < slots; slot++) {
                    ItemStack stack = itemHandler.getStackInSlot(slot);
                    if (!stack.isEmpty()) {
                        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        totals.merge(id, (long) stack.getCount(), Long::sum);
                    }
                }
            }

            // Query Fluids capability
            IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, queryDir);
            if (fluidHandler != null) {
                for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                    FluidStack fluid = fluidHandler.getFluidInTank(tank);
                    if (!fluid.isEmpty()) {
                        String id = BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
                        totals.merge(id, (long) fluid.getAmount(), Long::sum);
                    }
                }
            }

            // Query Energy capability
            IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, queryDir);
            if (energy != null) {
                int stored = energy.getEnergyStored();
                if (stored > 0) {
                    totals.merge("forge:energy", (long) stored, Long::sum);
                }
            }

        } catch (RuntimeException e) {
            LOGGER.warn("Failed to scan BlockEntity at {}: {}", pos, e.getMessage());
            // Continue scanning other BlockEntities (don't let one bad block stop the whole scan)
        }
    }
}
