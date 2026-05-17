package com.mukulramesh.fpscompress.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mukulramesh.fpscompress.portal.ExporterBlockEntity;
import com.mukulramesh.fpscompress.portal.ImporterBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Renders frequency items on all 6 sides of Importer/Exporter blocks.
 * Style: 3D item rendering similar to item frames, with full brightness and proper lighting.
 *
 * @param <T> Block entity type (ImporterBlockEntity or ExporterBlockEntity)
 */
public class FrequencyIndicatorRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

    private final ItemRenderer itemRenderer;

    public FrequencyIndicatorRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        ItemStack frequencyItem = getFrequencyItem(blockEntity);
        if (frequencyItem.isEmpty()) {
            return;
        }

        // Render on all 6 faces
        for (Direction direction : Direction.values()) {
            renderFrequencyOnFace(frequencyItem, direction, poseStack, bufferSource,
                                  combinedLight, combinedOverlay, blockEntity);
        }
    }

    /**
     * Render frequency item on a specific face.
     * Uses full brightness and 3D rendering like item frames.
     */
    private void renderFrequencyOnFace(ItemStack item, Direction face, PoseStack poseStack,
                                       MultiBufferSource bufferSource, int light, int overlay,
                                       T blockEntity) {
        poseStack.pushPose();

        // Move to block center
        poseStack.translate(0.5, 0.5, 0.5);

        // Rotate and position based on face direction
        switch (face) {
            case DOWN:
                poseStack.mulPose(Axis.XP.rotationDegrees(90));
                poseStack.translate(0, 0, -0.51);
                break;
            case UP:
                poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                poseStack.translate(0, 0, -0.51);
                break;
            case SOUTH:
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
                poseStack.translate(0, 0, -0.51);
                break;
            case WEST:
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
                poseStack.translate(0, 0, -0.51);
                break;
            case EAST:
                poseStack.mulPose(Axis.YP.rotationDegrees(-90));
                poseStack.translate(0, 0, -0.51);
                break;
            case NORTH:
            default:
                // NORTH and default: No rotation needed (facing north is the default orientation)
                poseStack.translate(0, 0, -0.51);
                break;
        }

        // Scale down to 50% (3D rendering, not flattened)
        poseStack.scale(0.5f, 0.5f, 0.5f);

        // Render the item with full brightness (like item frames)
        // Use LightTexture.FULL_BRIGHT for consistent visibility regardless of ambient lighting
        itemRenderer.renderStatic(item, ItemDisplayContext.FIXED,
                                  LightTexture.FULL_BRIGHT, overlay,
                                  poseStack, bufferSource, blockEntity.getLevel(), 0);

        poseStack.popPose();
    }

    /**
     * Extract frequency item from block entity (supports both Importer and Exporter).
     */
    private ItemStack getFrequencyItem(T blockEntity) {
        if (blockEntity instanceof ImporterBlockEntity importer) {
            return importer.getFrequencyItem();
        } else if (blockEntity instanceof ExporterBlockEntity exporter) {
            return exporter.getFrequencyItem();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getViewDistance() {
        return 64; // Render up to 64 blocks away (similar to signs)
    }
}
