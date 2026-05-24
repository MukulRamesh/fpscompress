package com.mukulramesh.fpscompress.network;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.gui.RateDisplayMode;
import com.mukulramesh.fpscompress.portal.PrefabBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Client → Server packet to update PreFab display preferences.
 * Sent when player clicks time scale button, reset button, or item in grid.
 */
public record RateDisplayPreferencePacket(
    BlockPos prefabPos,
    RateDisplayMode displayMode,
    @Nullable String focusedResourceId,
    int autoNormalizedTicks,
    boolean useAutoNormalize
) implements CustomPacketPayload {

    public static final Type<RateDisplayPreferencePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FPSCompress.MODID, "rate_display_preference"));

    private static final StreamCodec<ByteBuf, RateDisplayMode> DISPLAY_MODE_CODEC =
        StreamCodec.of(
            (buf, mode) -> ByteBufCodecs.STRING_UTF8.encode(buf, mode.name()),
            buf -> {
                try {
                    return RateDisplayMode.valueOf(ByteBufCodecs.STRING_UTF8.decode(buf));
                } catch (IllegalArgumentException e) {
                    return RateDisplayMode.PER_TICK; // Fallback
                }
            }
        );

    public static final StreamCodec<ByteBuf, RateDisplayPreferencePacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            BlockPos.STREAM_CODEC.encode(buf, packet.prefabPos);
            DISPLAY_MODE_CODEC.encode(buf, packet.displayMode);
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf,
                Optional.ofNullable(packet.focusedResourceId));
            ByteBufCodecs.VAR_INT.encode(buf, packet.autoNormalizedTicks);
            ByteBufCodecs.BOOL.encode(buf, packet.useAutoNormalize);
        },
        buf -> {
            BlockPos prefabPos = BlockPos.STREAM_CODEC.decode(buf);
            RateDisplayMode displayMode = DISPLAY_MODE_CODEC.decode(buf);
            String focusedResourceId = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8)
                .decode(buf).orElse(null);
            int autoNormalizedTicks = ByteBufCodecs.VAR_INT.decode(buf);
            boolean useAutoNormalize = ByteBufCodecs.BOOL.decode(buf);
            return new RateDisplayPreferencePacket(prefabPos, displayMode, focusedResourceId,
                autoNormalizedTicks, useAutoNormalize);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Server-side handler.
     * Updates PreFab display preferences and triggers sync to all clients.
     */
    public static void handle(RateDisplayPreferencePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlockEntity be = player.level().getBlockEntity(packet.prefabPos);

                if (be instanceof PrefabBlockEntity prefab) {
                    prefab.setCurrentDisplayMode(packet.displayMode);
                    prefab.setFocusedResourceId(packet.focusedResourceId);
                    prefab.setAutoNormalizedTicks(packet.autoNormalizedTicks);
                    prefab.setUseAutoNormalize(packet.useAutoNormalize);
                    // Next tick: StatusGuiSyncPacket will sync to all clients via broadcastChanges()
                }
            }
        });
    }
}
