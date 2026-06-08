package com.mukulramesh.fpscompress.blueprint;

import com.mukulramesh.fpscompress.component.FPSDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PreFab Blueprint item - Stores scanned factory configuration.
 * Created by scanning a PreFab in CACHED state.
 * Used to print carbon copy PreFabs via Fabricator block.
 */
public class PreFabBlueprintItem extends Item {

    public PreFabBlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag nbt = stack.get(FPSDataComponents.BLUEPRINT_DATA.get());
        if (nbt != null) {
            BlueprintData data = BlueprintData.fromNBT(nbt);
            if (data.getSourcePrefabName() != null) {
                return Component.literal(data.getSourcePrefabName());
            }
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                               List<Component> tooltipComponents, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipComponents, flag);

        CompoundTag nbt = stack.get(FPSDataComponents.BLUEPRINT_DATA.get());
        if (nbt == null) {
            tooltipComponents.add(Component.translatable("item.fpscompress.prefab_blueprint.state.new")
                .withStyle(ChatFormatting.GRAY));
            return;
        }

        BlueprintData data = BlueprintData.fromNBT(nbt);

        if (data.isEmpty()) {
            tooltipComponents.add(Component.translatable("item.fpscompress.prefab_blueprint.state.new")
                .withStyle(ChatFormatting.GRAY));
            return;
        }

        // Line 1: Resource counts
        tooltipComponents.add(Component.translatable(
            "item.fpscompress.prefab_blueprint.resources",
            Component.literal(String.valueOf(data.getBlockTypeCount())).withStyle(ChatFormatting.YELLOW),
            Component.literal(String.valueOf(data.getItemTypeCount())).withStyle(ChatFormatting.YELLOW)
        ).withStyle(ChatFormatting.GRAY));

        // Line 2: Room size
        tooltipComponents.add(Component.translatable(
            "item.fpscompress.prefab_blueprint.room_size",
            Component.literal(String.valueOf(data.getRoomSizeX())).withStyle(ChatFormatting.AQUA),
            Component.literal(String.valueOf(data.getRoomSizeY())).withStyle(ChatFormatting.AQUA),
            Component.literal(String.valueOf(data.getRoomSizeZ())).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.GRAY));

        // Line 3+: Cached rates (if present)
        addCachedRates(data, tooltipComponents);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        CompoundTag nbt = stack.get(FPSDataComponents.BLUEPRINT_DATA.get());
        if (nbt == null) {
            player.displayClientMessage(
                Component.translatable("item.fpscompress.prefab_blueprint.chat.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        BlueprintData data = BlueprintData.fromNBT(nbt);
        if (data.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("item.fpscompress.prefab_blueprint.chat.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // === Header ===
        String name = data.getSourcePrefabName();
        if (name != null && !name.isEmpty()) {
            player.sendSystemMessage(Component.literal("=== " + name + " ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            player.sendSystemMessage(Component.translatable(
                "item.fpscompress.prefab_blueprint.chat.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }

        // Room size
        player.sendSystemMessage(Component.translatable(
            "item.fpscompress.prefab_blueprint.room_size",
            Component.literal(String.valueOf(data.getRoomSizeX())).withStyle(ChatFormatting.AQUA),
            Component.literal(String.valueOf(data.getRoomSizeY())).withStyle(ChatFormatting.AQUA),
            Component.literal(String.valueOf(data.getRoomSizeZ())).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.GRAY));

        // === Resource Costs ===
        List<BlueprintData.ResourceRequirement> blockResources = data.getBlockResources();
        List<BlueprintData.ResourceRequirement> itemResources = data.getItemResources();

        if (!blockResources.isEmpty() || !itemResources.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "item.fpscompress.prefab_blueprint.chat.resources_header")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

            for (BlueprintData.ResourceRequirement req : blockResources) {
                String displayId = req.id().contains(":")
                    ? req.id().substring(req.id().indexOf(':') + 1) : req.id();
                player.sendSystemMessage(Component.literal("  - ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(displayId).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" x" + req.count()).withStyle(ChatFormatting.YELLOW)));
            }

            for (BlueprintData.ResourceRequirement req : itemResources) {
                String displayId = req.id().contains(":")
                    ? req.id().substring(req.id().indexOf(':') + 1) : req.id();
                player.sendSystemMessage(Component.literal("  - ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(displayId).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" x" + req.count()).withStyle(ChatFormatting.YELLOW)));
            }
        }

        // === Production Rates ===
        Map<UUID, List<BlueprintData.ResourceRate>> cachedRates = data.getCachedRates();
        if (!cachedRates.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "item.fpscompress.prefab_blueprint.chat.rates_header")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

            // Flatten all rates from all UUIDs into single list
            List<RateEntry> allRates = new ArrayList<>();
            for (List<BlueprintData.ResourceRate> rateList : cachedRates.values()) {
                for (BlueprintData.ResourceRate rate : rateList) {
                    allRates.add(new RateEntry(rate.getResourceId(), rate.getRate()));
                }
            }

            // Sort by rate (outputs first, then inputs)
            Collections.sort(allRates, (a, b) -> Double.compare(b.getRate(), a.getRate()));

            // Display ALL rates in chat (no limit — chat scrolls)
            for (RateEntry entry : allRates) {
                player.sendSystemMessage(formatRateLine(entry.getResourceId(), entry.getRate()));
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Add cached production rates to tooltip.
     *
     * @param data Blueprint data
     * @param tooltipComponents Tooltip components list
     */
    private void addCachedRates(BlueprintData data, List<Component> tooltipComponents) {
        Map<UUID, List<BlueprintData.ResourceRate>> cachedRates = data.getCachedRates();
        if (cachedRates.isEmpty()) {
            return;
        }

        // Flatten all rates from all UUIDs into single list
        List<RateEntry> allRates = new ArrayList<>();
        for (List<BlueprintData.ResourceRate> rateList : cachedRates.values()) {
            for (BlueprintData.ResourceRate rate : rateList) {
                allRates.add(new RateEntry(rate.getResourceId(), rate.getRate()));
            }
        }

        if (allRates.isEmpty()) {
            return;
        }

        // Header line
        tooltipComponents.add(Component.translatable("item.fpscompress.prefab_blueprint.rates_header")
            .withStyle(ChatFormatting.DARK_GRAY));

        // Sort by rate (outputs first positive, then inputs negative)
        Collections.sort(allRates, (a, b) -> Double.compare(b.getRate(), a.getRate()));

        // Display top 5 rates
        int displayCount = Math.min(allRates.size(), 5);
        for (int i = 0; i < displayCount; i++) {
            RateEntry entry = allRates.get(i);
            tooltipComponents.add(formatRateLine(entry.getResourceId(), entry.getRate()));
        }

        // Show "and X more..." if more than 5 rates
        if (allRates.size() > 5) {
            int remaining = allRates.size() - 5;
            tooltipComponents.add(Component.translatable(
                "item.fpscompress.prefab_blueprint.rates_more",
                Component.literal(String.valueOf(remaining)).withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * Format a single rate line for tooltip.
     *
     * @param resourceId Resource identifier
     * @param rate Production rate per tick
     * @return Formatted component
     */
    private Component formatRateLine(String resourceId, double rate) {
        Item item = getItemFromResourceId(resourceId);
        String itemName = getItemDisplayName(item, resourceId);

        String rateText = String.format("%.3f/t", Math.abs(rate));
        ChatFormatting rateColor;
        String prefix;

        if (rate > 0) {
            rateColor = ChatFormatting.GREEN;
            prefix = "+ ";
        } else {
            rateColor = ChatFormatting.RED;
            prefix = "- ";
        }

        return Component.literal("  " + prefix)
            .withStyle(rateColor)
            .append(Component.literal(itemName + ": ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(rateText).withStyle(rateColor));
    }

    /**
     * Get Item from resource ID.
     *
     * @param resourceId Resource identifier
     * @return Item instance, or AIR if invalid
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
     * @param item Item instance
     * @param resourceId Fallback resource ID
     * @return Display name
     */
    private String getItemDisplayName(Item item, String resourceId) {
        if (item != Items.AIR) {
            return item.getName(new ItemStack(item)).getString();
        }

        return resourceId.contains(":")
            ? resourceId.substring(resourceId.indexOf(':') + 1)
            : resourceId;
    }

    /**
     * Helper class for sorting rates.
     */
    private static final class RateEntry {
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
