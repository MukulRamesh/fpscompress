package com.mukulramesh.fpscompress.network;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.blueprint.FabricatorBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server packet to trigger printing on the Fabricator.
 * Sent when player clicks the "Print" button in the Fabricator GUI.
 *
 * <p>Phase 5: Placeholder that validates resource requirements.
 * Phase 6: Will implement actual PreFab carbon copy creation.
 */
public record PrintRequestPacket(BlockPos fabricatorPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PrintRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            FPSCompress.MODID, "print_request"
        ));

    public static final StreamCodec<ByteBuf, PrintRequestPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            PrintRequestPacket::fabricatorPos,
            PrintRequestPacket::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle packet on server side.
     * Triggers the print operation on the FabricatorBlockEntity.
     */
    public static void handle(PrintRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            ServerLevel level = serverPlayer.serverLevel();
            BlockEntity be = level.getBlockEntity(packet.fabricatorPos());

            if (!(be instanceof FabricatorBlockEntity fabricator)) {
                FPSCompress.LOGGER.warn("PrintRequestPacket: No FabricatorBlockEntity at {}",
                    packet.fabricatorPos());
                return;
            }

            // Trigger print (Phase 6: real resource consumption + carbon copy creation)
            boolean success = fabricator.triggerPrint();

            if (success) {
                serverPlayer.displayClientMessage(
                    Component.literal("§aPreFab printed successfully! Check output slot."),
                    true
                );
            } else {
                // Determine specific failure reason for better feedback
                String errorMsg = "§cCannot print - ";
                ItemStack outputStack = fabricator.getOutputSlot();
                if (!outputStack.isEmpty()) {
                    errorMsg += "output slot is occupied";
                } else if (fabricator.getInputSlot().isEmpty()) {
                    errorMsg += "insert a Blueprint in the input slot";
                } else {
                    errorMsg += "check resource requirements";
                }
                serverPlayer.displayClientMessage(
                    Component.literal(errorMsg),
                    true
                );
            }
        });
    }
}
