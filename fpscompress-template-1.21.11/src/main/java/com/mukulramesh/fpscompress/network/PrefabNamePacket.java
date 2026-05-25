package com.mukulramesh.fpscompress.network;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.portal.PrefabBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Client → Server packet to set PreFab custom name.
 * Sent when player clicks "Save Name" button in Status GUI.
 */
public record PrefabNamePacket(BlockPos prefabPos, @Nullable String prefabName)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PrefabNamePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            FPSCompress.MODID, "prefab_name"
        ));

    public static final StreamCodec<ByteBuf, PrefabNamePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            PrefabNamePacket::prefabPos,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
            packet -> Optional.ofNullable(packet.prefabName()),
            (pos, nameOpt) -> new PrefabNamePacket(pos, nameOpt.orElse(null))
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle packet on server side.
     * Updates PreFab name and triggers save.
     */
    public static void handle(PrefabNamePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = player.serverLevel();
            BlockEntity be = level.getBlockEntity(packet.prefabPos());

            if (!(be instanceof PrefabBlockEntity prefab)) {
                return;
            }

            // Update name (setter handles sanitization and setChanged())
            prefab.setPrefabName(packet.prefabName());

            // Sync to client (status GUI will update via next StatusGuiSyncPacket tick)
            level.sendBlockUpdated(packet.prefabPos(), prefab.getBlockState(),
                prefab.getBlockState(), 3);
        });
    }
}
