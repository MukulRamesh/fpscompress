package com.mukulramesh.fpscompress.portal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Handles inventory scanning and room bounds detection.
 * Scans Importer/Exporter buffers and determines room boundaries via Machine Wall detection.
 */
public class InventoryScanningService {
    private final PrefabBlockEntity entity;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
        justification = "Service class intentionally holds reference to entity for delegation pattern")
    public InventoryScanningService(PrefabBlockEntity entity) {
        this.entity = entity;
    }

    /**
     * Get room bounds AABB for inventory scanning.
     * Uses Machine Wall scanning to find room boundaries.
     *
     * @param roomCode The room code
     * @return AABB bounds of the room
     * @throws IllegalStateException if room bounds cannot be determined
     */
    public AABB getRoomBoundsFromCM(String roomCode) {
        try {
            ServerLevel cmLevel = getCMLevel();
            if (cmLevel == null || entity.getLevel() == null || entity.getLevel().isClientSide()) {
                throw new IllegalStateException("CM level not available");
            }

            net.minecraft.server.MinecraftServer server = entity.getLevel().getServer();
            if (server == null) {
                throw new IllegalStateException("Server not available");
            }

            RoomCoordinateCache cache = RoomCoordinateCache.get(server);
            BlockPos center = cache.getRoomCenterByRoomCode(roomCode);

            if (center == null) {
                throw new IllegalStateException("Room center not cached for room: " + roomCode);
            }

            // Try multiple known wall block IDs (CM versions may differ)
            Block wallBlock = null;
            String[] wallBlockIds = {
                "compactmachines:solid_wall",  // Primary (most common)
                "compactmachines:wall",         // Fallback
                "compactmachines:machine_wall"  // Alternative name
            };

            for (String blockId : wallBlockIds) {
                Block candidate = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
                // Registry.get() never returns null for DefaultedRegistry, but may return AIR
                if (!candidate.equals(net.minecraft.world.level.block.Blocks.AIR)) {
                    wallBlock = candidate;
                    FPSCompress.LOGGER.debug("Found wall block: {}", blockId);
                    break;
                }
            }

            if (wallBlock == null) {
                throw new IllegalStateException("No valid CM wall block found in registry");
            }

            // Scan outward from center in all 6 directions to find walls
            int minX = scanForWall(cmLevel, center, Direction.WEST, wallBlock);
            int maxX = scanForWall(cmLevel, center, Direction.EAST, wallBlock);
            int minY = scanForWall(cmLevel, center, Direction.DOWN, wallBlock);
            int maxY = scanForWall(cmLevel, center, Direction.UP, wallBlock);
            int minZ = scanForWall(cmLevel, center, Direction.NORTH, wallBlock);
            int maxZ = scanForWall(cmLevel, center, Direction.SOUTH, wallBlock);

            // Add +1 to max coordinates to make AABB inclusive of interior (exclude walls)
            AABB bounds = new AABB(minX + 1, minY + 1, minZ + 1, maxX, maxY, maxZ);
            FPSCompress.LOGGER.info("Room bounds via Machine Wall scan: {}", bounds);
            return bounds;

        } catch (IllegalStateException e) {
            // Re-throw ISE as-is (expected failure cases)
            throw e;
        } catch (RuntimeException e) {
            // Wrap unexpected runtime exceptions
            FPSCompress.LOGGER.error("Failed to determine room bounds: {}", e.getMessage());
            throw new RuntimeException("Cannot determine room bounds", e);
        }
    }

    /**
     * Scan outward from center in given direction until Machine Wall found.
     * Returns coordinate of wall (inclusive).
     *
     * @param level The level
     * @param center Center position
     * @param dir Direction to scan
     * @param wallBlock The wall block to look for
     * @return Coordinate of wall in the direction's axis
     * @throws IllegalStateException if wall not found within max distance
     */
    private int scanForWall(ServerLevel level, BlockPos center, Direction dir, Block wallBlock) {
        BlockPos.MutableBlockPos pos = center.mutable();
        int maxDistance = 20; // Safety limit (largest CM room is ~13x13x13)

        for (int i = 0; i < maxDistance; i++) {
            pos.move(dir);
            BlockState state = level.getBlockState(pos);

            // Debug: Log first few blocks encountered
            if (i < 3) {
                FPSCompress.LOGGER.debug("Scanning {} at distance {}: {} at {}",
                    dir, i, BuiltInRegistries.BLOCK.getKey(state.getBlock()), pos.toShortString());
            }

            if (state.is(wallBlock)) {
                FPSCompress.LOGGER.debug("Found wall block {} in direction {} at distance {}",
                    BuiltInRegistries.BLOCK.getKey(wallBlock), dir, i);
                // Return coordinate based on direction
                return switch (dir.getAxis()) {
                    case X -> pos.getX();
                    case Y -> pos.getY();
                    case Z -> pos.getZ();
                };
            }
        }

        // Enhanced error message with what we actually found
        BlockPos.MutableBlockPos debugPos = center.mutable();
        StringBuilder foundBlocks = new StringBuilder();
        for (int i = 0; i < Math.min(5, maxDistance); i++) {
            debugPos.move(dir);
            BlockState state = level.getBlockState(debugPos);
            foundBlocks.append("\n  Distance ").append(i + 1).append(": ")
                .append(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }

        throw new IllegalStateException(
            "Machine Wall (" + BuiltInRegistries.BLOCK.getKey(wallBlock)
            + ") not found in direction " + dir + " after " + maxDistance + " blocks."
            + "\nScanned from: " + center.toShortString()
            + "\nBlocks found:" + foundBlocks.toString());
    }

    /**
     * Scan Importer/Exporter buffers and add to resource totals.
     * This prevents buffer contents from inflating rate calculations.
     *
     * @param cmLevel The CM dimension level
     * @param totals The resource totals map (modified in-place)
     */
    public void scanImporterExporterBuffers(ServerLevel cmLevel, Map<String, Long> totals) {
        // Scan all configured faces for Importer/Exporter links
        for (Direction face : Direction.values()) {
            FaceConfig config = entity.getFaceConfig(face);
            UUID targetUUID = config.getTargetUUID();
            if (targetUUID == null) {
                continue; // Face not linked
            }

            if (config.getMode() == FaceMode.PULL) {
                // PULL face links to Importer - scan its buffer
                ImporterBlockEntity importer = entity.findImporterByUUID(cmLevel, targetUUID);
                if (importer == null) {
                    // Cache miss - try building cache from registry
                    ImporterExporterRegistry.Entry entry = ImporterExporterRegistry.getImporter(targetUUID);
                    if (entry != null) {
                        entity.cacheImporterPosition(targetUUID, entry.pos());
                        importer = entity.findImporterByUUID(cmLevel, targetUUID);
                    }
                }
                if (importer != null) {
                    scanImporterBuffer(importer, totals);
                } else {
                    FPSCompress.LOGGER.warn("Failed to find Importer {} for buffer scan", targetUUID);
                }
            } else if (config.getMode() == FaceMode.PUSH) {
                // PUSH face links to Exporter - scan its buffer
                ExporterBlockEntity exporter = entity.findExporterByUUID(cmLevel, targetUUID);
                if (exporter == null) {
                    // Cache miss - try building cache from registry
                    ImporterExporterRegistry.Entry entry = ImporterExporterRegistry.getExporter(targetUUID);
                    if (entry != null) {
                        entity.cacheExporterPosition(targetUUID, entry.pos());
                        exporter = entity.findExporterByUUID(cmLevel, targetUUID);
                    }
                }
                if (exporter != null) {
                    scanExporterBuffer(exporter, totals);
                } else {
                    FPSCompress.LOGGER.warn("Failed to find Exporter {} for buffer scan", targetUUID);
                }
            }
        }
    }

    /**
     * Scan Importer buffer and add items to totals.
     *
     * @param importer The Importer block entity
     * @param totals The resource totals map (modified in-place)
     */
    private void scanImporterBuffer(ImporterBlockEntity importer, Map<String, Long> totals) {
        ItemStackHandler inventory = importer.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                String resourceId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                totals.merge(resourceId, (long) stack.getCount(), Long::sum);
                FPSCompress.LOGGER.debug("Importer {} buffer slot {}: {} x{}",
                    importer.getImporterUUID(), slot, resourceId, stack.getCount());
            }
        }
    }

    /**
     * Scan Exporter buffer and add items to totals.
     *
     * @param exporter The Exporter block entity
     * @param totals The resource totals map (modified in-place)
     */
    private void scanExporterBuffer(ExporterBlockEntity exporter, Map<String, Long> totals) {
        ItemStackHandler inventory = exporter.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                String resourceId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                totals.merge(resourceId, (long) stack.getCount(), Long::sum);
                FPSCompress.LOGGER.debug("Exporter {} buffer slot {}: {} x{}",
                    exporter.getExporterUUID(), slot, resourceId, stack.getCount());
            }
        }
    }

    /**
     * Send inventory scan results to player chat (debug output).
     * Shows each resource and its count.
     *
     * @param player The player to send messages to
     * @param inventory The scanned inventory (resource ID -> count)
     * @param label Label for the scan ("Initial" or "Final")
     */
    public void sendInventoryScanToChat(@Nullable Player player, Map<String, Long> inventory, String label) {
        if (player == null) {
            return; // No player to send to (shouldn't happen, but safety check)
        }

        player.displayClientMessage(
            Component.literal("§6=== " + label + " Scan Results ==="), false);

        if (inventory.isEmpty()) {
            player.displayClientMessage(
                Component.literal("§7No resources found in room"), false);
        } else {
            player.displayClientMessage(
                Component.literal("§7Found " + inventory.size() + " resource types:"), false);

            // Sort by resource ID for consistent display
            inventory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String resourceId = entry.getKey();
                    long count = entry.getValue();

                    // Get localized name (or fallback to ID)
                    String displayName = getLocalizedResourceName(resourceId);

                    // Format: "  Iron Ingot: 64"
                    player.displayClientMessage(
                        Component.literal("  §3" + displayName + "§7: §e" + count), false);
                });
        }
    }

    /**
     * Get localized display name for a resource ID.
     * Handles items, fluids, and energy.
     *
     * @param resourceId The resource ID (e.g., "minecraft:iron_ingot" or "forge:energy")
     * @return Localized display name
     */
    private String getLocalizedResourceName(String resourceId) {
        // Special case: forge:energy
        if ("forge:energy".equals(resourceId)) {
            return "Energy (FE)";
        }

        try {
            // Try as item (DefaultedRegistry.get() never returns null, but may return AIR)
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM
                .get(ResourceLocation.parse(resourceId));
            if (!item.equals(net.minecraft.world.item.Items.AIR)) {
                return item.getName(new ItemStack(item)).getString();
            }

            // Try as fluid (DefaultedRegistry.get() never returns null, but may return EMPTY)
            net.minecraft.world.level.material.Fluid fluid = BuiltInRegistries.FLUID
                .get(ResourceLocation.parse(resourceId));
            if (!fluid.equals(net.minecraft.world.level.material.Fluids.EMPTY)) {
                return fluid.getFluidType().getDescription().getString();
            }
        } catch (RuntimeException e) {
            // Fallback to ID if lookup fails (e.g., invalid resource location)
        }

        // Fallback: return short ID (remove namespace)
        return resourceId.contains(":") ? resourceId.substring(resourceId.indexOf(':') + 1) : resourceId;
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
                ResourceLocation.parse("compactmachines:compact_world")
            )
        );
    }
}
