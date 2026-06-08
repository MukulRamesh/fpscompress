package com.mukulramesh.fpscompress.network;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.blueprint.FabricatorBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server packet to trigger a block scan on the Fabricator.
 * Sent when player clicks the "Scan" button in the Fabricator GUI.
 */
public record ScanRequestPacket(BlockPos fabricatorPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ScanRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            FPSCompress.MODID, "scan_request"
        ));

    public static final StreamCodec<ByteBuf, ScanRequestPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ScanRequestPacket::fabricatorPos,
            ScanRequestPacket::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle packet on server side.
     * Triggers the block scan on the FabricatorBlockEntity.
     */
    public static void handle(ScanRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            ServerLevel level = serverPlayer.serverLevel();
            BlockEntity be = level.getBlockEntity(packet.fabricatorPos());

            if (!(be instanceof FabricatorBlockEntity fabricator)) {
                FPSCompress.LOGGER.warn("ScanRequestPacket: No FabricatorBlockEntity at {}", packet.fabricatorPos());
                return;
            }

            // Trigger scan (validation and feedback handled inside triggerScan)
            boolean success = fabricator.triggerScan();

            if (!success) {
                serverPlayer.displayClientMessage(
                    Component.literal("§cCannot start scan - check input slot and output slot"),
                    true
                );
            }
        });
    }
}
