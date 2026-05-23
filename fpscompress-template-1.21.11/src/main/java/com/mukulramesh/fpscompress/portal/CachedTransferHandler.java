package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Map;
import java.util.UUID;

/**
 * Phase 6: Handles cached resource transfers with UUID-based face filtering.
 * Enables multi-output routing by mapping UUIDs to specific faces.
 */
public final class CachedTransferHandler {

    private CachedTransferHandler() {
        // Utility class
    }

    /**
     * Transfer cached output from a specific Exporter UUID to Overworld via mapped faces.
     *
     * @param exporterUUID The UUID of the Exporter that produced this resource
     * @param resourceId The resource identifier (e.g., "minecraft:iron_ingot")
     * @param amount The number of items to transfer
     * @param level The world level
     * @param prefabPos The PreFab block position
     * @param faceConfigs Face configurations
     * @param cachedProduction Production tracking map
     * @return true if fully transferred, false if blocked
     */
    public static boolean transferCachedOutput(
            UUID exporterUUID,
            String resourceId,
            int amount,
            Level level,
            BlockPos prefabPos,
            Map<Direction, FaceConfig> faceConfigs,
            Map<String, Long> cachedProduction) {

        // Parse resource ID to get Item
        ResourceLocation resLoc;
        try {
            resLoc = ResourceLocation.parse(resourceId);
        } catch (Exception e) {
            FPSCompress.LOGGER.error("Invalid resource ID: {}", resourceId);
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(resLoc);
        if (item == Items.AIR) {
            FPSCompress.LOGGER.error("Unknown item: {}", resourceId);
            return false;
        }

        ItemStack toTransfer = new ItemStack(item, amount);

        // Phase 6: Only try faces mapped to this Exporter UUID
        for (Direction face : Direction.values()) {
            FaceConfig config = faceConfigs.get(face);

            // Filter: Must be PUSH mode AND mapped to this UUID
            if (config.getMode() != FaceMode.PUSH) {
                continue; // Not a PUSH face
            }
            if (!exporterUUID.equals(config.getTargetUUID())) {
                continue; // Skip faces not mapped to this Exporter
            }

            // Get Overworld adjacent block
            BlockPos overworldPos = prefabPos.relative(face);

            // Try to get item capability from Overworld block
            IItemHandler overworldHandler = level.getCapability(
                Capabilities.ItemHandler.BLOCK, overworldPos, face.getOpposite());
            if (overworldHandler == null) {
                continue; // No item capability
            }

            // Try inserting
            ItemStack remainder = toTransfer.copy();
            for (int slot = 0; slot < overworldHandler.getSlots(); slot++) {
                remainder = overworldHandler.insertItem(slot, remainder, false);
                if (remainder.isEmpty()) {
                    break; // All items inserted
                }
            }

            int transferred = toTransfer.getCount() - remainder.getCount();
            if (transferred > 0) {
                // Track production for GUI
                cachedProduction.merge(resourceId, (long) transferred, Long::sum);

                FPSCompress.LOGGER.debug("Cached OUTPUT: Transferred {} x{} via {} face",
                    resourceId, transferred, face);

                if (!remainder.isEmpty()) {
                    // Partial transfer - output is getting blocked
                    FPSCompress.LOGGER.warn("Output partially blocked for {} (transferred {}/{})",
                        resourceId, transferred, toTransfer.getCount());
                }

                return remainder.isEmpty(); // Success if fully transferred
            }
        }

        // No PUSH face could accept the items - output blocked
        FPSCompress.LOGGER.warn("Output blocked for {} - no PUSH face available", resourceId);
        return false;
    }

    /**
     * Transfer cached input from Overworld to a specific Importer UUID via mapped faces.
     *
     * @param importerUUID The UUID of the Importer that needs this resource
     * @param resourceId The resource identifier (e.g., "minecraft:coal")
     * @param amount The number of items to transfer
     * @param level The world level
     * @param prefabPos The PreFab block position
     * @param faceConfigs Face configurations
     * @return true if all items obtained, false if starved
     */
    public static boolean transferCachedInput(
            UUID importerUUID,
            String resourceId,
            int amount,
            Level level,
            BlockPos prefabPos,
            Map<Direction, FaceConfig> faceConfigs) {

        // Parse resource ID to get Item
        ResourceLocation resLoc;
        try {
            resLoc = ResourceLocation.parse(resourceId);
        } catch (Exception e) {
            FPSCompress.LOGGER.error("Invalid resource ID: {}", resourceId);
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(resLoc);
        if (item == Items.AIR) {
            FPSCompress.LOGGER.error("Unknown item: {}", resourceId);
            return false;
        }

        int stillNeeded = amount;

        FPSCompress.LOGGER.debug("Cached INPUT: Attempting to extract {} x{}", resourceId, amount);

        // Phase 6: Only try faces mapped to this Importer UUID
        for (Direction face : Direction.values()) {
            if (stillNeeded <= 0) {
                break; // Got all items needed
            }

            FaceConfig config = faceConfigs.get(face);

            // Filter: Must be PULL mode AND mapped to this UUID
            if (config.getMode() != FaceMode.PULL) {
                continue; // Not a PULL face
            }
            if (!importerUUID.equals(config.getTargetUUID())) {
                continue; // Skip faces not mapped to this Importer
            }

            FPSCompress.LOGGER.debug("Cached INPUT: Trying PULL face {}", face);

            // Get Overworld adjacent block
            BlockPos overworldPos = prefabPos.relative(face);

            // Try to get item capability from Overworld block
            IItemHandler overworldHandler = level.getCapability(
                Capabilities.ItemHandler.BLOCK, overworldPos, face.getOpposite());
            if (overworldHandler == null) {
                FPSCompress.LOGGER.debug("Cached INPUT: No item handler at {} (face {})",
                    overworldPos, face);
                continue; // No item capability
            }

            FPSCompress.LOGGER.debug("Cached INPUT: Found item handler at {} with {} slots",
                overworldPos, overworldHandler.getSlots());

            // Try extracting the specific item type
            for (int slot = 0; slot < overworldHandler.getSlots(); slot++) {
                ItemStack inSlot = overworldHandler.getStackInSlot(slot);
                if (inSlot.getItem() != item) {
                    continue; // Wrong item type
                }

                ItemStack extracted = overworldHandler.extractItem(slot, stillNeeded, false);
                if (!extracted.isEmpty()) {
                    stillNeeded -= extracted.getCount();
                    FPSCompress.LOGGER.debug("Cached INPUT: Extracted {} x{} via {} face",
                        resourceId, extracted.getCount(), face);

                    if (stillNeeded <= 0) {
                        break; // Got all items needed
                    }
                }
            }
        }

        if (stillNeeded > 0) {
            FPSCompress.LOGGER.warn("Input starved for {} - still need {} items", resourceId, stillNeeded);
        }

        return stillNeeded <= 0; // Success if got all items
    }
}
