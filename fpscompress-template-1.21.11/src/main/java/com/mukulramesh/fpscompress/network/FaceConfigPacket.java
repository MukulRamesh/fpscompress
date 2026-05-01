package com.mukulramesh.fpscompress.network;

import com.mukulramesh.fpscompress.portal.FaceConfig;
import com.mukulramesh.fpscompress.portal.PrefabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.EnumMap;
import java.util.Map;

/**
 * Network packet to sync face configurations from client to server.
 *
 * Sent when player clicks "Save" in PreFab config GUI.
 */
public record FaceConfigPacket(BlockPos prefabPos, Map<Direction, FaceConfig> faceConfigs) implements CustomPacketPayload {

    public static final Type<FaceConfigPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("fpscompress", "face_config")
    );

    public static final StreamCodec<FriendlyByteBuf, FaceConfigPacket> STREAM_CODEC = StreamCodec.ofMember(
        FaceConfigPacket::encode,
        FaceConfigPacket::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(FaceConfigPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.prefabPos);

        // Write 6 face configs
        for (Direction dir : Direction.values()) {
            FaceConfig config = packet.faceConfigs.get(dir);
            CompoundTag tag = config.toNBT();
            buf.writeNbt(tag);
        }
    }

    public static FaceConfigPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Map<Direction, FaceConfig> configs = new EnumMap<>(Direction.class);

        // Read 6 face configs (same order as encode)
        for (Direction dir : Direction.values()) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                configs.put(dir, FaceConfig.fromNBT(tag));
            } else {
                configs.put(dir, new FaceConfig());
            }
        }

        return new FaceConfigPacket(pos, configs);
    }

    /**
     * Handle packet on server side - update PreFab BlockEntity with new configs.
     */
    public static void handle(FaceConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                BlockEntity be = serverPlayer.level().getBlockEntity(packet.prefabPos);
                if (be instanceof PrefabBlockEntity prefab) {
                    // Update all 6 face configurations
                    for (Direction dir : Direction.values()) {
                        FaceConfig config = packet.faceConfigs.get(dir);
                        prefab.setFaceConfig(dir, config);
                    }

                    // Mark dirty to trigger save
                    prefab.setChanged();

                    serverPlayer.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§aPreFab configuration saved!"),
                        true
                    );
                }
            }
        });
    }
}
