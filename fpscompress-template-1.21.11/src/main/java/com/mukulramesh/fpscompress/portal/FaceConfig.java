package com.mukulramesh.fpscompress.portal;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/**
 * Configuration data for a single PreFab face.
 *
 * <p>Each of the 6 faces of a PreFab block has independent configuration:
 * <ul>
 *   <li>mode - Whether the face is DISABLED, PULL, or PUSH</li>
 *   <li>resourceType - Filter for which resources to transport (ALL/ITEMS/FLUIDS/ENERGY)</li>
 *   <li>targetUUID - Links to specific Importer/Exporter in CM dimension (Phase 2)</li>
 * </ul>
 */
public class FaceConfig {
    /**
     * Operational mode of this face.
     */
    private FaceMode mode;

    /**
     * Resource filter for this face.
     */
    private ResourceFilter resourceType;

    /**
     * UUID of the target Importer/Exporter block in CM dimension.
     * Null if not yet configured (Phase 2 feature).
     */
    private UUID targetUUID;

    /**
     * Create a default disabled face configuration.
     */
    public FaceConfig() {
        this.mode = FaceMode.DISABLED;
        this.resourceType = ResourceFilter.ALL;
        this.targetUUID = null;
    }

    /**
     * Create a face configuration with specific settings.
     *
     * @param mode Face mode
     * @param resourceType Resource filter
     * @param targetUUID Target Importer/Exporter UUID (can be null)
     */
    public FaceConfig(FaceMode mode, ResourceFilter resourceType, UUID targetUUID) {
        this.mode = mode;
        this.resourceType = resourceType;
        this.targetUUID = targetUUID;
    }

    // Getters and setters

    public FaceMode getMode() {
        return mode;
    }

    public void setMode(FaceMode mode) {
        this.mode = mode;
    }

    public ResourceFilter getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceFilter resourceType) {
        this.resourceType = resourceType;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public void setTargetUUID(UUID targetUUID) {
        this.targetUUID = targetUUID;
    }

    // NBT Serialization

    /**
     * Serialize this configuration to NBT.
     *
     * @return NBT tag containing all configuration data
     */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putString("resourceType", resourceType.name());
        if (targetUUID != null) {
            tag.putUUID("targetUUID", targetUUID);
        }
        return tag;
    }

    /**
     * Deserialize configuration from NBT.
     *
     * @param tag NBT tag containing configuration data
     * @return FaceConfig instance loaded from NBT
     */
    public static FaceConfig fromNBT(CompoundTag tag) {
        FaceConfig config = new FaceConfig();

        if (tag.contains("mode")) {
            try {
                config.mode = FaceMode.valueOf(tag.getString("mode"));
            } catch (IllegalArgumentException e) {
                config.mode = FaceMode.DISABLED;
            }
        }

        if (tag.contains("resourceType")) {
            try {
                config.resourceType = ResourceFilter.valueOf(tag.getString("resourceType"));
            } catch (IllegalArgumentException e) {
                config.resourceType = ResourceFilter.ALL;
            }
        }

        if (tag.hasUUID("targetUUID")) {
            config.targetUUID = tag.getUUID("targetUUID");
        }

        return config;
    }

    @Override
    public String toString() {
        return String.format("FaceConfig{mode=%s, resourceType=%s, targetUUID=%s}",
                mode, resourceType, targetUUID);
    }
}
