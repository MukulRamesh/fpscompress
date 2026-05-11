package com.mukulramesh.fpscompress.portal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Custom BlockItem for PreFab Machine with tooltip support.
 * Displays machine state, room code, and configured face count when hovering over item.
 */
public class PrefabBlockItem extends BlockItem {

    public PrefabBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                               List<Component> tooltipComponents, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipComponents, flag);

        // Extract NBT from BLOCK_ENTITY_DATA component (NeoForge 1.21.11 DataComponents system)
        CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (customData == null) {
            // Fresh PreFab (never placed) - show default state
            tooltipComponents.add(Component.translatable("item.fpscompress.prefab_machine.state.new")
                .withStyle(ChatFormatting.GRAY));
            return;
        }

        CompoundTag blockEntityTag = customData.copyTag();

        // Line 1: Machine State (color-coded)
        if (blockEntityTag.contains("state")) {
            String stateStr = blockEntityTag.getString("state");
            try {
                MachineState state = MachineState.valueOf(stateStr);
                Component stateComponent = Component.translatable(
                    "item.fpscompress.prefab_machine.state",
                    getStateDisplayComponent(state)
                ).withStyle(ChatFormatting.GRAY);
                tooltipComponents.add(stateComponent);
            } catch (IllegalArgumentException e) {
                // Invalid state string - skip
            }
        }

        // Line 2: Room Code (if linked)
        if (blockEntityTag.contains("roomCode")) {
            String roomCode = blockEntityTag.getString("roomCode");
            tooltipComponents.add(Component.translatable(
                "item.fpscompress.prefab_machine.room",
                Component.literal(roomCode).withStyle(ChatFormatting.AQUA)
            ).withStyle(ChatFormatting.GRAY));
        }

        // Line 3: Configured Faces Count
        int configuredCount = countConfiguredFaces(blockEntityTag);
        tooltipComponents.add(Component.translatable(
            "item.fpscompress.prefab_machine.faces",
            Component.literal(String.valueOf(configuredCount)).withStyle(ChatFormatting.YELLOW)
        ).withStyle(ChatFormatting.GRAY));

        // Line 4+: Cached Rates (only if CACHED state)
        if (blockEntityTag.contains("state") && "CACHED".equals(blockEntityTag.getString("state"))) {
            addCachedRates(blockEntityTag, tooltipComponents);
        }
    }

    /**
     * Get color-coded display component for machine state.
     *
     * @param state The machine state
     * @return Formatted component with appropriate color
     */
    private Component getStateDisplayComponent(MachineState state) {
        return switch (state) {
            case BUILDING -> Component.translatable("fpscompress.state.building")
                .withStyle(ChatFormatting.YELLOW);
            case SIMULATING -> Component.translatable("fpscompress.state.simulating")
                .withStyle(ChatFormatting.AQUA);
            case CACHED -> Component.translatable("fpscompress.state.cached")
                .withStyle(ChatFormatting.GREEN);
            case HALTED -> Component.translatable("fpscompress.state.halted")
                .withStyle(ChatFormatting.RED);
        };
    }

    /**
     * Count number of configured (non-DISABLED) faces.
     *
     * @param blockEntityTag The PreFab NBT data
     * @return Number of configured faces (0-6)
     */
    private int countConfiguredFaces(CompoundTag blockEntityTag) {
        if (!blockEntityTag.contains("faceConfigs")) {
            return 0;
        }

        CompoundTag facesTag = blockEntityTag.getCompound("faceConfigs");
        int count = 0;

        for (Direction dir : Direction.values()) {
            if (facesTag.contains(dir.getName())) {
                CompoundTag faceConfig = facesTag.getCompound(dir.getName());
                if (faceConfig.contains("mode")) {
                    String mode = faceConfig.getString("mode");
                    if (!"DISABLED".equals(mode)) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    /**
     * Add cached production rates to tooltip (CACHED state only).
     *
     * @param blockEntityTag The PreFab NBT data
     * @param tooltipComponents The tooltip components list to add to
     */
    private void addCachedRates(CompoundTag blockEntityTag, List<Component> tooltipComponents) {
        if (!blockEntityTag.contains("rates")) {
            return; // No rates cached yet
        }

        ListTag ratesList = blockEntityTag.getList("rates", Tag.TAG_COMPOUND);
        if (ratesList.isEmpty()) {
            return;
        }

        // Header line
        tooltipComponents.add(Component.translatable("item.fpscompress.prefab_machine.rates_header")
            .withStyle(ChatFormatting.DARK_GRAY));

        // Collect all rates into a list for sorting
        List<RateEntry> rates = new ArrayList<>();
        for (int i = 0; i < ratesList.size(); i++) {
            CompoundTag rateEntry = ratesList.getCompound(i);
            String resourceId = rateEntry.getString("id");
            double rate = rateEntry.getDouble("rate");
            rates.add(new RateEntry(resourceId, rate));
        }

        // Sort by rate (outputs first [positive], then inputs [negative])
        Collections.sort(rates, (a, b) -> Double.compare(b.getRate(), a.getRate()));

        // Display each rate (limit to 5 to avoid cluttering tooltip)
        int displayCount = Math.min(rates.size(), 5);
        for (int i = 0; i < displayCount; i++) {
            RateEntry entry = rates.get(i);
            tooltipComponents.add(formatRateLine(entry.getResourceId(), entry.getRate()));
        }

        // Show "and X more..." if there are more than 5 rates
        if (rates.size() > 5) {
            int remaining = rates.size() - 5;
            tooltipComponents.add(Component.translatable(
                "item.fpscompress.prefab_machine.rates_more",
                Component.literal(String.valueOf(remaining)).withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * Format a single rate line for tooltip.
     *
     * @param resourceId The resource ID (e.g., "minecraft:iron_ingot")
     * @param rate The production rate per tick (positive = output, negative = input)
     * @return Formatted component
     */
    private Component formatRateLine(String resourceId, double rate) {
        // Parse resource ID to get Item
        Item item = getItemFromResourceId(resourceId);
        String itemName = getItemDisplayName(item, resourceId);

        // Format rate (show absolute value with sign prefix)
        String rateText = String.format("%.3f/t", Math.abs(rate));
        ChatFormatting rateColor;
        String prefix;

        if (rate > 0) {
            // Positive rate = Output (production)
            rateColor = ChatFormatting.GREEN;
            prefix = "+ ";
        } else {
            // Negative rate = Input (consumption)
            rateColor = ChatFormatting.RED;
            prefix = "- ";
        }

        // Format: "  + Iron Ingot: 0.213/t"
        return Component.literal("  " + prefix)
            .withStyle(rateColor)
            .append(Component.literal(itemName + ": ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(rateText).withStyle(rateColor));
    }

    /**
     * Get Item from resource ID.
     *
     * @param resourceId The resource ID (e.g., "minecraft:iron_ingot")
     * @return The Item, or AIR if invalid
     */
    private Item getItemFromResourceId(String resourceId) {
        try {
            ResourceLocation resLoc = ResourceLocation.parse(resourceId);
            return BuiltInRegistries.ITEM.get(resLoc);
        } catch (Exception e) {
            return Items.AIR;
        }
    }

    /**
     * Get display name for item.
     *
     * @param item The item
     * @param resourceId Fallback resource ID
     * @return Display name
     */
    private String getItemDisplayName(Item item, String resourceId) {
        if (item != Items.AIR) {
            return item.getName(new ItemStack(item)).getString();
        }

        // Fallback: Extract name from resource ID (e.g., "minecraft:iron_ingot" -> "iron_ingot")
        return resourceId.contains(":") ? resourceId.substring(resourceId.indexOf(':') + 1) : resourceId;
    }

    /**
     * Helper class to store rate entries for sorting.
     */
    private static class RateEntry {
        private final String resourceId;
        private final double rate;

        RateEntry(String resourceId, double rate) {
            this.resourceId = resourceId;
            this.rate = rate;
        }

        public String getResourceId() {
            return resourceId;
        }

        public double getRate() {
            return rate;
        }
    }
}
