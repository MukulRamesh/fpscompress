package com.mukulramesh.fpscompress.network;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.portal.MachineState;
import com.mukulramesh.fpscompress.portal.PrefabBlockEntity;
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
 * Client → Server packet to trigger PreFab state transitions.
 * Sent when player clicks the control button in PreFab status GUI.
 */
public record SimulationControlPacket(BlockPos prefabPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SimulationControlPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            FPSCompress.MODID, "simulation_control"
        ));

    public static final StreamCodec<ByteBuf, SimulationControlPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SimulationControlPacket::prefabPos,
            SimulationControlPacket::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle packet on server side.
     * Transitions PreFab state based on current state.
     */
    public static void handle(SimulationControlPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = player.serverLevel();
            BlockEntity be = level.getBlockEntity(packet.prefabPos());

            if (!(be instanceof PrefabBlockEntity prefab)) {
                return;
            }

            // Call appropriate state transition based on current state
            MachineState currentState = prefab.getCurrentState();
            switch (currentState) {
                case BUILDING -> {
                    prefab.startSimulation();
                    player.displayClientMessage(
                        Component.literal("§aStarted simulation - measuring rates..."),
                        true
                    );
                }
                case SIMULATING -> {
                    prefab.finishSimulation();
                    MachineState newState = prefab.getCurrentState();
                    if (newState == MachineState.CACHED) {
                        player.displayClientMessage(
                            Component.literal("§aCaching complete - running virtually!"),
                            true
                        );
                    } else {
                        player.displayClientMessage(
                            Component.literal("§cSimulation failed - no activity detected"),
                            true
                        );
                    }
                }
                case CACHED -> {
                    prefab.resetToBuilding();
                    player.displayClientMessage(
                        Component.literal("§eReset to building mode"),
                        true
                    );
                }
                case HALTED -> {
                    prefab.resumeSimulation();
                    player.displayClientMessage(
                        Component.literal("§aResumed simulation"),
                        true
                    );
                }
                default -> {
                    // Should never happen, but satisfies checkstyle
                }
            }

            // Sync BlockEntity to client (so GUI updates)
            prefab.setChanged();
            level.sendBlockUpdated(packet.prefabPos(), prefab.getBlockState(),
                prefab.getBlockState(), 3);
        });
    }
}
