package com.mukulramesh.fpscompress.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registry for data attachments used by FPSCompress.
 *
 * Data attachments allow us to store custom data on BlockEntities without
 * modifying their classes. This is used to attach VirtualMachineDataImpl
 * to Compact Machine BlockEntities.
 *
 * @author Dev 1 - Core Registry Team
 */
public final class FPSDataAttachments {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private FPSDataAttachments() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * DeferredRegister for all attachment types in this mod.
     */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FPSCompress.MODID);

    /**
     * Attachment for VirtualMachineDataImpl on Compact Machine BlockEntities.
     *
     * This stores the virtual buffer data and TPS upgrade status.
     * The data persists across world save/load cycles.
     *
     * Note: We use a wrapper because we can't directly attach VirtualMachineDataImpl
     * (it holds a BlockEntity reference which can't be serialized).
     */
    public static final Supplier<AttachmentType<VirtualMachineDataWrapper>> VIRTUAL_MACHINE_DATA =
        ATTACHMENT_TYPES.register("virtual_machine_data", () ->
            AttachmentType.builder(() -> new VirtualMachineDataWrapper(false, new CompoundTag()))
                .build()
        );

    /**
     * Wrapper class for VirtualMachineDataImpl serialization.
     *
     * This wraps the essential data (upgrade status + storage NBT) for codec serialization.
     * We can't directly serialize VirtualMachineDataImpl because it holds a BlockEntity reference.
     */
    public static class VirtualMachineDataWrapper {
        private final boolean hasTpsUpgrade;
        private final CompoundTag storageData;

        /**
         * Codec for serializing VirtualMachineDataWrapper.
         */
        public static final Codec<VirtualMachineDataWrapper> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.BOOL.fieldOf("hasTpsUpgrade").forGetter(w -> w.hasTpsUpgrade),
                CompoundTag.CODEC.fieldOf("storageData").forGetter(w -> w.storageData)
            ).apply(instance, VirtualMachineDataWrapper::new)
        );

        /**
         * Constructor for VirtualMachineDataWrapper.
         *
         * @param hasTpsUpgrade Whether the TPS upgrade is installed
         * @param storageData The storage NBT data
         */
        public VirtualMachineDataWrapper(boolean hasTpsUpgrade, CompoundTag storageData) {
            this.hasTpsUpgrade = hasTpsUpgrade;
            this.storageData = storageData;
        }

        /**
         * Check if TPS upgrade is installed.
         *
         * @return true if upgraded
         */
        public boolean hasTpsUpgrade() {
            return hasTpsUpgrade;
        }

        /**
         * Get the storage data.
         *
         * @return The CompoundTag containing storage data
         */
        public CompoundTag getStorageData() {
            return storageData;
        }

        /**
         * Create a wrapper from VirtualMachineDataImpl.
         *
         * @param data The VirtualMachineDataImpl to wrap
         * @return A new VirtualMachineDataWrapper
         */
        public static VirtualMachineDataWrapper fromData(VirtualMachineDataImpl data) {
            CompoundTag tag = data.save();
            CompoundTag storageTag = tag.contains("storage")
                ? tag.getCompound("storage")
                : new CompoundTag();
            return new VirtualMachineDataWrapper(
                data.hasTpsUpgrade(),
                storageTag
            );
        }

        /**
         * Apply this wrapper's data to a VirtualMachineDataImpl.
         *
         * @param data The VirtualMachineDataImpl to update
         */
        public void applyTo(VirtualMachineDataImpl data) {
            CompoundTag fullTag = new CompoundTag();
            fullTag.putBoolean("hasTpsUpgrade", hasTpsUpgrade);
            fullTag.put("storage", storageData);
            data.load(fullTag);
        }
    }
}
