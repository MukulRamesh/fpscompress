package com.mukulramesh.fpscompress.portal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

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
}
