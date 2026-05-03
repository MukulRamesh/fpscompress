package com.mukulramesh.fpscompress.portal;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Represents the current operational state of a PreFab factory.
 */
public enum MachineState implements StringRepresentable {
    /**
     * Player is setting up the factory inside the room.
     * Chunks are loaded, routing is physical.
     */
    BUILDING,

    /**
     * System is observing production rates to calculate cached values.
     * Chunks are loaded, routing is physical.
     */
    SIMULATING,

    /**
     * Factory is running in virtual mode using cached rates.
     * Chunks are unloaded, routing is virtual.
     */
    CACHED,

    /**
     * Cache is invalid (starved inputs or blocked outputs).
     * Chunks are loaded, routing is physical, waiting for player intervention.
     */
    HALTED;

    public static final StreamCodec<ByteBuf, MachineState> STREAM_CODEC =
        ByteBufCodecs.idMapper(i -> values()[i], MachineState::ordinal);

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase();
    }
}
