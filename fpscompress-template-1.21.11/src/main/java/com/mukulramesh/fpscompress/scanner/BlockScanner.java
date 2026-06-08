package com.mukulramesh.fpscompress.scanner;

import com.mukulramesh.fpscompress.Config;
import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.blueprint.NbtRequirement;
import com.mukulramesh.fpscompress.blueprint.NbtRequirementRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Utility for scanning blocks in a Compact Machines room.
 *
 * <p>Performs async scanning with tick-spreading to prevent server lag.
 * Excludes air blocks and configured excluded blocks (e.g., CM wall blocks) from results.
 * When NBT requirements are registered for a block type, extracts and stores NBT data.
 *
 * <p>Phase 3: Added NBT-aware scanning via NbtRequirementRegistry
 */
public final class BlockScanner {

    private static final int BLOCKS_PER_TICK = 100;

    // Cached exclusion set for O(1) lookups (rebuilt from config as needed)
    private static volatile Set<String> excludedBlocksCache = null;

    private BlockScanner() {
        // Utility class
    }

    /**
     * Combined scan result containing block counts and optional NBT templates.
     *
     * @param counts Map of grouping key → count (key may include NBT hash for tracked blocks)
     * @param nbtTemplates Map of grouping key → extracted NBT (only for NBT-tracked blocks)
     */
    public record ScanResult(Map<String, Long> counts, Map<String, CompoundTag> nbtTemplates) {
        /**
         * Canonical constructor — stores mutable copies so callers (StateTransitionManager,
         * FabricatorBlockEntity) can further aggregate into the maps.
         *
         * <p>SpotBugs EI_EXPOSE_REP suppressed via spotbugs-excludes.xml.
         */
        public ScanResult {
            counts = new HashMap<>(counts);
            nbtTemplates = new HashMap<>(nbtTemplates);
        }

        public static ScanResult empty() {
            return new ScanResult(new HashMap<>(), new HashMap<>());
        }
    }

    /**
     * Get or build the cached exclusion set from config.
     * Uses double-checked locking for thread-safe lazy initialization.
     *
     * @return Cached set of excluded block IDs
     */
    private static Set<String> getExcludedBlocksCache() {
        if (excludedBlocksCache == null) {
            synchronized (BlockScanner.class) {
                if (excludedBlocksCache == null) {
                    excludedBlocksCache = new HashSet<>(Config.SERVER.getBlueprintExcludedBlocks());
                    FPSCompress.LOGGER.debug("BlockScanner: Built exclusion cache with {} block IDs",
                        excludedBlocksCache.size());
                }
            }
        }
        return excludedBlocksCache;
    }

    /**
     * Check if a block should be excluded from scanning.
     *
     * @param blockId Namespaced block ID (e.g., "minecraft:stone")
     * @return true if block should be excluded
     */
    private static boolean isBlockExcluded(String blockId) {
        return getExcludedBlocksCache().contains(blockId);
    }

    /**
     * Build a grouping key for a scanned block.
     * For blocks with NBT requirements, appends NBT hash to distinguish different variants.
     * For blocks without NBT requirements, uses the plain resource ID.
     *
     * @param blockId Namespaced block ID
     * @param nbt Extracted NBT (null if no requirements)
     * @return Grouping key
     */
    static String buildGroupKey(String blockId, CompoundTag nbt) {
        if (nbt != null && !nbt.isEmpty()) {
            return blockId + "#" + nbt.hashCode();
        }
        return blockId;
    }

    /**
     * Extract the resource ID from a grouping key.
     * For NBT-tracked blocks, the key is "resource_id#hash" — this returns "resource_id".
     * For non-NBT blocks, the key is just the resource ID.
     *
     * @param groupKey Grouping key from scan results
     * @return Plain resource ID (e.g., "fpscompress:prefab_machine")
     */
    public static String extractResourceId(String groupKey) {
        int hashIdx = groupKey.indexOf('#');
        return hashIdx >= 0 ? groupKey.substring(0, hashIdx) : groupKey;
    }

    /**
     * Scan blocks in a room asynchronously with tick-spreading.
     *
     * <p>Processes 100 blocks per tick to avoid lag. Excludes air blocks,
     * Importer/Exporter blocks, and configured excluded blocks.
     *
     * <p>For blocks with registered NBT requirements, extracts BlockEntity NBT
     * and groups variants separately.
     *
     * @param cmLevel CM dimension ServerLevel
     * @param roomCenter Center position of the room (unused, kept for consistency)
     * @param roomBounds AABB bounds of the room to scan
     * @return CompletableFuture with ScanResult containing counts and NBT templates
     */
    public static CompletableFuture<ScanResult> scanBlocksAsync(
            ServerLevel cmLevel,
            BlockPos roomCenter,
            AABB roomBounds
    ) {
        CompletableFuture<ScanResult> future = new CompletableFuture<>();
        Map<String, Long> totals = new HashMap<>();
        Map<String, CompoundTag> nbtTemplates = new HashMap<>();

        // Build list of all positions to scan
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                (int) roomBounds.minX, (int) roomBounds.minY, (int) roomBounds.minZ,
                (int) roomBounds.maxX, (int) roomBounds.maxY, (int) roomBounds.maxZ
        )) {
            positions.add(pos.immutable());
        }

        FPSCompress.LOGGER.info("BlockScanner: Scanning {} positions in room", positions.size());

        // Start scanning first batch
        scanNextBatch(cmLevel, positions, 0, totals, nbtTemplates, future);

        return future;
    }

    /**
     * Scan blocks in a room synchronously (for testing).
     */
    public static ScanResult scanBlocksSync(
            ServerLevel cmLevel,
            BlockPos roomCenter,
            AABB roomBounds
    ) {
        Map<String, Long> totals = new HashMap<>();
        Map<String, CompoundTag> nbtTemplates = new HashMap<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                (int) roomBounds.minX, (int) roomBounds.minY, (int) roomBounds.minZ,
                (int) roomBounds.maxX, (int) roomBounds.maxY, (int) roomBounds.maxZ
        )) {
            BlockState state = cmLevel.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            Block block = state.getBlock();
            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();

            if (isBlockExcluded(blockId)) {
                continue;
            }

            scanAndCount(cmLevel, pos, blockId, totals, nbtTemplates);
        }

        int nbtTracked = (int) nbtTemplates.keySet().stream()
            .filter(k -> k.contains("#")).count();
        FPSCompress.LOGGER.info("BlockScanner: Sync scan complete - {} unique groups ({} with NBT)",
            totals.size(), nbtTracked);

        return new ScanResult(totals, nbtTemplates);
    }

    /**
     * Scan next batch, then schedule the next batch via TickTask.
     */
    private static void scanNextBatch(
            ServerLevel cmLevel,
            List<BlockPos> positions,
            int startIndex,
            Map<String, Long> totals,
            Map<String, CompoundTag> nbtTemplates,
            CompletableFuture<ScanResult> future
    ) {
        int endIndex = Math.min(startIndex + BLOCKS_PER_TICK, positions.size());

        for (int i = startIndex; i < endIndex; i++) {
            BlockPos pos = positions.get(i);
            BlockState state = cmLevel.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            Block block = state.getBlock();
            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();

            if (isBlockExcluded(blockId)) {
                continue;
            }

            scanAndCount(cmLevel, pos, blockId, totals, nbtTemplates);
        }

        if (endIndex < positions.size()) {
            cmLevel.getServer().tell(new TickTask(
                    cmLevel.getServer().getTickCount() + 1,
                    () -> scanNextBatch(cmLevel, positions, endIndex, totals, nbtTemplates, future)
            ));
        } else {
            int nbtTracked = (int) nbtTemplates.keySet().stream()
                .filter(k -> k.contains("#")).count();
            FPSCompress.LOGGER.info("BlockScanner: Scan complete - {} unique groups ({} with NBT tracking)",
                totals.size(), nbtTracked);
            future.complete(new ScanResult(totals, nbtTemplates));
        }
    }

    /**
     * Scan a single block position and add to results.
     * Extracts NBT if NBT requirements exist for this block type.
     */
    private static void scanAndCount(
            ServerLevel cmLevel,
            BlockPos pos,
            String blockId,
            Map<String, Long> totals,
            Map<String, CompoundTag> nbtTemplates
    ) {
        // Check if this block type has NBT requirements
        NbtRequirementRegistry registry = NbtRequirementRegistry.getInstance();
        java.util.Optional<NbtRequirement> optReq = registry.getRequirement(blockId);
        if (optReq.isPresent()) {
            FPSCompress.LOGGER.info("[BlockScanner] NBT required for {}: fields={}",
                blockId, optReq.get().getTrackedFields());
        }
        CompoundTag extractedNbt = null;

        if (optReq.isPresent()) {
            NbtRequirement req = optReq.get();
            BlockEntity be = cmLevel.getBlockEntity(pos);

            if (be != null) {
                try {
                    CompoundTag fullNbt = be.saveWithoutMetadata(cmLevel.registryAccess());
                    extractedNbt = NbtRequirement.extractNbt(fullNbt, req.getTrackedFields());
                    FPSCompress.LOGGER.info("[BlockScanner] {} extracted NBT keys: {}",
                        blockId,
                        extractedNbt != null && !extractedNbt.isEmpty()
                            ? extractedNbt.getAllKeys() : "empty");
                } catch (Exception e) {
                    FPSCompress.LOGGER.warn("BlockScanner: Failed to extract NBT for {} at {}: {}",
                        blockId, pos, e.getMessage());
                }
            } else {
                FPSCompress.LOGGER.info("[BlockScanner] No BlockEntity for {} at {}", blockId, pos);
            }
        }

        // Build grouping key (includes NBT hash for tracked blocks)
        String key = buildGroupKey(blockId, extractedNbt);
        totals.merge(key, 1L, Long::sum);

        // Store NBT template (only first occurrence)
        if (extractedNbt != null && !extractedNbt.isEmpty() && !nbtTemplates.containsKey(key)) {
            nbtTemplates.put(key, extractedNbt);
        }
    }
}
