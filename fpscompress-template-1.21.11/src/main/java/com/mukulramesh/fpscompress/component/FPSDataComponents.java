package com.mukulramesh.fpscompress.component;

import com.mojang.serialization.Codec;
import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry for custom data components used by FPSCompress.
 *
 * Data components are NeoForge 1.21's modern replacement for NBT tags.
 * They provide type-safe, codec-based serialization for persistent data.
 */
public final class FPSDataComponents {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private FPSDataComponents() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * DeferredRegister for all data components in this mod.
     */
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, FPSCompress.MODID);

    /**
     * Tracks if TPS upgrade is installed on a Compact Machine.
     *
     * When true, the machine can:
     * - Enter CACHED mode (math-only simulation)
     * - Use virtual buffers instead of physical blocks
     * - Unload chunks to save TPS
     *
     * This component persists on the block item when broken and moved.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>>
        TPS_UPGRADE_INSTALLED = DATA_COMPONENTS.register("tps_upgrade_installed",
            () -> DataComponentType.<Boolean>builder()
                .persistent(Codec.BOOL)
                .networkSynchronized(ByteBufCodecs.BOOL)
                .build()
        );

    /**
     * Stores blueprint data for PreFab Blueprint items.
     *
     * Contains scanned factory configuration including:
     * - Block resources (resource ID → count)
     * - Item resources (resource ID → count)
     * - Cached production rates (UUID → rates)
     * - Room dimensions
     * - Custom name
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.nbt.CompoundTag>>
        BLUEPRINT_DATA = DATA_COMPONENTS.register("blueprint_data",
            () -> DataComponentType.<net.minecraft.nbt.CompoundTag>builder()
                .persistent(net.minecraft.nbt.CompoundTag.CODEC)
                .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG)
                .build()
        );
}
