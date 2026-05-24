package com.mukulramesh.fpscompress.network;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.portal.MachineState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Server → Client packet to sync PreFab status GUI data.
 * Sent periodically while GUI is open to update live stats.
 */
public record StatusGuiSyncPacket(
    MachineState state,
    long simulationStartTick,
    long simulationEndTick,
    long cachedStateStartTick,
    long currentTick,
    Map<String, long[]> liveStats, // resourceId → [imported, exported]
    Map<String, Double> cachedRates, // resourceId → rate
    Map<String, Long> cachedProduction, // resourceId → total produced during CACHED
    String lastSimulationResult, // Result message (e.g., "Passthrough detected", "Success")
    long simulationElapsedTicks, // Elapsed ticks in SIMULATING state (for minimum time enforcement)
    long simulationRequiredTicks // Required ticks from config snapshot (for minimum time enforcement)
) implements CustomPacketPayload {

    public static final Type<StatusGuiSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FPSCompress.MODID, "status_gui_sync"));

    private static final StreamCodec<ByteBuf, Map<String, long[]>> LIVE_STATS_CODEC =
        StreamCodec.of(
            (buf, map) -> {
                ByteBufCodecs.VAR_INT.encode(buf, map.size());
                for (Map.Entry<String, long[]> entry : map.entrySet()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                    ByteBufCodecs.VAR_LONG.encode(buf, entry.getValue()[0]);
                    ByteBufCodecs.VAR_LONG.encode(buf, entry.getValue()[1]);
                }
            },
            buf -> {
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                Map<String, long[]> map = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    String key = ByteBufCodecs.STRING_UTF8.decode(buf);
                    long imported = ByteBufCodecs.VAR_LONG.decode(buf);
                    long exported = ByteBufCodecs.VAR_LONG.decode(buf);
                    map.put(key, new long[]{imported, exported});
                }
                return map;
            }
        );

    private static final StreamCodec<ByteBuf, Map<String, Double>> CACHED_RATES_CODEC =
        StreamCodec.of(
            (buf, map) -> {
                ByteBufCodecs.VAR_INT.encode(buf, map.size());
                for (Map.Entry<String, Double> entry : map.entrySet()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                    ByteBufCodecs.DOUBLE.encode(buf, entry.getValue());
                }
            },
            buf -> {
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                Map<String, Double> map = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    String key = ByteBufCodecs.STRING_UTF8.decode(buf);
                    double value = ByteBufCodecs.DOUBLE.decode(buf);
                    map.put(key, value);
                }
                return map;
            }
        );

    private static final StreamCodec<ByteBuf, Map<String, Long>> CACHED_PRODUCTION_CODEC =
        StreamCodec.of(
            (buf, map) -> {
                ByteBufCodecs.VAR_INT.encode(buf, map.size());
                for (Map.Entry<String, Long> entry : map.entrySet()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                    ByteBufCodecs.VAR_LONG.encode(buf, entry.getValue());
                }
            },
            buf -> {
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                Map<String, Long> map = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    String key = ByteBufCodecs.STRING_UTF8.decode(buf);
                    long value = ByteBufCodecs.VAR_LONG.decode(buf);
                    map.put(key, value);
                }
                return map;
            }
        );

    public static final StreamCodec<ByteBuf, StatusGuiSyncPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            com.mukulramesh.fpscompress.portal.MachineState.STREAM_CODEC.encode(buf, packet.state);
            ByteBufCodecs.VAR_LONG.encode(buf, packet.simulationStartTick);
            ByteBufCodecs.VAR_LONG.encode(buf, packet.simulationEndTick);
            ByteBufCodecs.VAR_LONG.encode(buf, packet.cachedStateStartTick);
            ByteBufCodecs.VAR_LONG.encode(buf, packet.currentTick);
            LIVE_STATS_CODEC.encode(buf, packet.liveStats);
            CACHED_RATES_CODEC.encode(buf, packet.cachedRates);
            CACHED_PRODUCTION_CODEC.encode(buf, packet.cachedProduction);
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.lastSimulationResult);
            ByteBufCodecs.VAR_LONG.encode(buf, packet.simulationElapsedTicks);
            ByteBufCodecs.VAR_LONG.encode(buf, packet.simulationRequiredTicks);
        },
        buf -> {
            MachineState state = com.mukulramesh.fpscompress.portal.MachineState.STREAM_CODEC.decode(buf);
            long simulationStartTick = ByteBufCodecs.VAR_LONG.decode(buf);
            long simulationEndTick = ByteBufCodecs.VAR_LONG.decode(buf);
            long cachedStateStartTick = ByteBufCodecs.VAR_LONG.decode(buf);
            long currentTick = ByteBufCodecs.VAR_LONG.decode(buf);
            Map<String, long[]> liveStats = LIVE_STATS_CODEC.decode(buf);
            Map<String, Double> cachedRates = CACHED_RATES_CODEC.decode(buf);
            Map<String, Long> cachedProduction = CACHED_PRODUCTION_CODEC.decode(buf);
            String lastSimulationResult = ByteBufCodecs.STRING_UTF8.decode(buf);
            long simulationElapsedTicks = ByteBufCodecs.VAR_LONG.decode(buf);
            long simulationRequiredTicks = ByteBufCodecs.VAR_LONG.decode(buf);
            return new StatusGuiSyncPacket(state, simulationStartTick, simulationEndTick,
                cachedStateStartTick, currentTick, liveStats, cachedRates, cachedProduction,
                lastSimulationResult, simulationElapsedTicks, simulationRequiredTicks);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler.
     * Updates the GUI with fresh data from server.
     */
    public static void handle(StatusGuiSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Find open PreFabStatusScreen and update it
            if (net.minecraft.client.Minecraft.getInstance().screen
                    instanceof com.mukulramesh.fpscompress.gui.PreFabStatusScreen screen) {
                screen.updateFromServer(
                    packet.state,
                    packet.simulationStartTick,
                    packet.simulationEndTick,
                    packet.cachedStateStartTick,
                    packet.currentTick,
                    packet.liveStats,
                    packet.cachedRates,
                    packet.cachedProduction,
                    packet.lastSimulationResult,
                    packet.simulationElapsedTicks,
                    packet.simulationRequiredTicks
                );
            }
        });
    }
}
