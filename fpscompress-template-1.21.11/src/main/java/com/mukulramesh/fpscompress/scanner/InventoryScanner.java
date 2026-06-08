package com.mukulramesh.fpscompress.scanner;

import com.mojang.logging.LogUtils;
import com.mukulramesh.fpscompress.blueprint.NbtRequirement;
import com.mukulramesh.fpscompress.blueprint.NbtRequirementRegistry;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Utility for scanning all BlockEntity inventories in a CM room.
 * Used for initial/final state capture in Enhanced Delta Accounting.
 *
 * <p>Performance optimization: Only iterates BlockEntities (not all blocks),
 * reducing scan from ~3,375 blocks to ~50 BlockEntities in typical room.
 *
 * <p>Phase 5: Added NBT-aware scanning via NbtRequirementRegistry
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
     * Scan room asynchronously with tick spreading (backward-compatible, no NBT).
     * Used by StateTransitionManager for delta accounting.
     *
     * @param cmLevel The CM dimension level
     * @param roomCenter Center of the CM room
     * @param roomBounds AABB bounds of the room
     * @return CompletableFuture with map of resource ID to total quantity
     */
    public static CompletableFuture<Map<String, Long>> scanRoomAsync(
            ServerLevel cmLevel,
            BlockPos roomCenter,
            AABB roomBounds
    ) {
        return scanRoomAsyncWithNbt(cmLevel, roomCenter, roomBounds)
            .thenApply(BlockScanner.ScanResult::counts);
    }

    /**
     * Scan room asynchronously with NBT extraction for registered items.
     * Used by FabricatorBlockEntity for blueprint creation.
     *
     * @param cmLevel The CM dimension level
     * @param roomCenter Center of the CM room
     * @param roomBounds AABB bounds of the room
     * @return CompletableFuture with ScanResult containing counts and NBT templates
     */
    public static CompletableFuture<BlockScanner.ScanResult> scanRoomAsyncWithNbt(
            ServerLevel cmLevel,
            BlockPos roomCenter,
            AABB roomBounds
    ) {
        CompletableFuture<BlockScanner.ScanResult> future = new CompletableFuture<>();

        // Collect all BlockEntity positions first
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

    private static final int BLOCK_ENTITIES_PER_TICK = 20;

    /**
     * Scan BlockEntities with tick spreading (20 per tick).
     */
    private static void scanWithTickSpreading(
            ServerLevel cmLevel,
            java.util.List<BlockPos> positions,
            CompletableFuture<BlockScanner.ScanResult> future
    ) {
        Map<String, Long> totals = new HashMap<>();
        Map<String, CompoundTag> nbtTemplates = new HashMap<>();
        scanNextBatch(cmLevel, positions, 0, totals, nbtTemplates, future);
    }

    /**
     * Scan one batch of BlockEntities, then schedule next batch for next tick.
     */
    private static void scanNextBatch(
            ServerLevel cmLevel,
            java.util.List<BlockPos> positions,
            int startIndex,
            Map<String, Long> totals,
            Map<String, CompoundTag> nbtTemplates,
            CompletableFuture<BlockScanner.ScanResult> future
    ) {
        try {
            int endIndex = Math.min(startIndex + BLOCK_ENTITIES_PER_TICK, positions.size());

            for (int i = startIndex; i < endIndex; i++) {
                BlockPos pos = positions.get(i);
                scanBlockEntity(cmLevel, pos, totals, nbtTemplates);
            }

            LOGGER.debug("Scanned batch {}-{} of {} BlockEntities",
                startIndex, endIndex - 1, positions.size());

            if (endIndex >= positions.size()) {
                int nbtTracked = (int) nbtTemplates.keySet().stream()
                    .filter(k -> k.contains("#")).count();
                LOGGER.info("=== SCAN COMPLETE: {} resources found ({} with NBT tracking) ===",
                    totals.size(), nbtTracked);
                future.complete(new BlockScanner.ScanResult(totals, nbtTemplates));
            } else {
                int nextStart = endIndex;
                cmLevel.getServer().tell(new net.minecraft.server.TickTask(
                    cmLevel.getServer().getTickCount() + 1,
                    () -> scanNextBatch(cmLevel, positions, nextStart, totals, nbtTemplates, future)
                ));
            }

        } catch (RuntimeException e) {
            LOGGER.error("Failed during batch scan", e);
            future.completeExceptionally(e);
        }
    }

    /**
     * Scan room synchronously (backward-compatible, no NBT).
     */
    public static Map<String, Long> scanRoomSync(
            ServerLevel cmLevel,
            BlockPos roomCenter,
            AABB roomBounds
    ) {
        return scanRoomSyncWithNbt(cmLevel, roomCenter, roomBounds).counts();
    }

    /**
     * Scan room synchronously with NBT extraction.
     */
    public static BlockScanner.ScanResult scanRoomSyncWithNbt(
            ServerLevel cmLevel,
            BlockPos roomCenter,
            AABB roomBounds
    ) {
        Map<String, Long> totals = new HashMap<>();
        Map<String, CompoundTag> nbtTemplates = new HashMap<>();
        int scannedCount = 0;
        int totalBlockEntities = 0;

        try {
            ChunkPos minChunk = new ChunkPos(new BlockPos((int) roomBounds.minX, 0, (int) roomBounds.minZ));
            ChunkPos maxChunk = new ChunkPos(new BlockPos((int) roomBounds.maxX, 0, (int) roomBounds.maxZ));

            for (int cx = minChunk.x; cx <= maxChunk.x; cx++) {
                for (int cz = minChunk.z; cz <= maxChunk.z; cz++) {
                    LevelChunk chunk = cmLevel.getChunk(cx, cz);
                    totalBlockEntities += chunk.getBlockEntities().size();

                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getBlockPos();
                        if (roomBounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                            scanBlockEntity(cmLevel, pos, totals, nbtTemplates);
                            scannedCount++;
                        }
                    }
                }
            }

            LOGGER.info("=== SCAN COMPLETE: Found {} total BlockEntities, scanned {} within bounds ===",
                    totalBlockEntities, scannedCount);
            LOGGER.info("Total resources found: {}", totals.size());
            return new BlockScanner.ScanResult(totals, nbtTemplates);

        } catch (RuntimeException e) {
            LOGGER.error("Failed to scan room at bounds {}", roomBounds, e);
            return new BlockScanner.ScanResult(totals, nbtTemplates);
        }
    }

    /**
     * Scan single BlockEntity and aggregate capabilities with NBT tracking.
     */
    @SuppressFBWarnings(
            value = {"NP_LOAD_OF_KNOWN_NULL_VALUE", "NP_NONNULL_PARAM_VIOLATION"},
            justification = "Null direction is valid for getCapability - returns unrestricted capability with all slots"
    )
    private static void scanBlockEntity(ServerLevel level, BlockPos pos,
                                       Map<String, Long> totals,
                                       Map<String, CompoundTag> nbtTemplates) {
        try {
            net.minecraft.core.Direction queryDir = null;

            // Query Items capability (with NBT extraction)
            IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, queryDir);
            if (itemHandler != null) {
                scanItemHandler(itemHandler, totals, nbtTemplates, level.registryAccess());
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
        }
    }

    /**
     * Scan items in an IItemHandler with NBT extraction.
     */
    private static void scanItemHandler(
            IItemHandler itemHandler,
            Map<String, Long> totals,
            Map<String, CompoundTag> nbtTemplates,
            HolderLookup.Provider registries
    ) {
        NbtRequirementRegistry registry = NbtRequirementRegistry.getInstance();

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack stack = itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            CompoundTag extractedNbt = null;

            // Check if this item type has NBT requirements
            Optional<NbtRequirement> optReq = registry.getRequirement(itemId);
            if (optReq.isPresent()) {
                NbtRequirement req = optReq.get();
                try {
                    // Try BLOCK_ENTITY_DATA first (for BlockItems like PreFabs — flat structure)
                    // Fall back to full ItemStack save (for items with tag-based NBT)
                    CompoundTag fullNbt;
                    CustomData blockData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                    if (blockData != null) {
                        fullNbt = blockData.copyTag();
                    } else {
                        fullNbt = new CompoundTag();
                        stack.save(registries, fullNbt);
                    }
                    extractedNbt = NbtRequirement.extractNbt(fullNbt, req.getTrackedFields());

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("InventoryScanner: Extracted NBT for {}: {}",
                            itemId, extractedNbt);
                    }
                } catch (Exception e) {
                    LOGGER.warn("InventoryScanner: Failed to extract NBT for {}: {}",
                        itemId, e.getMessage());
                }
            }

            // Build grouping key and tally
            String key = BlockScanner.buildGroupKey(itemId, extractedNbt);
            totals.merge(key, (long) stack.getCount(), Long::sum);

            // Store NBT template (only first occurrence)
            if (extractedNbt != null && !extractedNbt.isEmpty() && !nbtTemplates.containsKey(key)) {
                nbtTemplates.put(key, extractedNbt);
            }
        }
    }
}
