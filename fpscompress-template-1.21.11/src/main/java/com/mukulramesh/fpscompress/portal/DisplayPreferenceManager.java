package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.gui.RateDisplayMode;
import org.jetbrains.annotations.Nullable;

/**
 * Manages display preferences for PreFab status GUI.
 * Handles rate display modes, focused resources, and auto-normalization settings.
 */
public class DisplayPreferenceManager {
    private final PrefabBlockEntity entity;

    public DisplayPreferenceManager(PrefabBlockEntity entity) {
        this.entity = entity;
    }

    /**
     * Get current display mode for rate visualization.
     *
     * @return Display mode (PER_TICK, PER_SECOND, etc.)
     */
    public RateDisplayMode getCurrentDisplayMode() {
        return entity.currentDisplayMode;
    }

    /**
     * Set display mode for rate visualization.
     *
     * @param mode New display mode
     */
    public void setCurrentDisplayMode(RateDisplayMode mode) {
        entity.currentDisplayMode = mode;
        entity.setChanged();
    }

    /**
     * Get focused resource ID (null if no focus).
     *
     * @return Resource ID or null
     */
    @Nullable
    public String getFocusedResourceId() {
        return entity.focusedResourceId;
    }

    /**
     * Set focused resource ID for normalization.
     *
     * @param id Resource ID to focus on (null to clear focus)
     */
    public void setFocusedResourceId(@Nullable String id) {
        entity.focusedResourceId = id;
        entity.setChanged();
    }

    /**
     * Get auto-normalized ticks (LCM result).
     *
     * @return Normalized ticks (1 = no normalization)
     */
    public int getAutoNormalizedTicks() {
        return entity.autoNormalizedTicks;
    }

    /**
     * Set auto-normalized ticks (LCM result).
     *
     * @param ticks Normalized ticks (minimum 1)
     */
    public void setAutoNormalizedTicks(int ticks) {
        entity.autoNormalizedTicks = Math.max(1, ticks);
        entity.setChanged();
    }

    /**
     * Get whether to use auto-normalized display.
     *
     * @return true if using auto-normalize, false for manual time scale
     */
    public boolean getUseAutoNormalize() {
        return entity.useAutoNormalize;
    }

    /**
     * Set whether to use auto-normalized display.
     *
     * @param use true to use auto-normalize, false for manual time scale
     */
    public void setUseAutoNormalize(boolean use) {
        entity.useAutoNormalize = use;
        entity.setChanged();
    }

    /**
     * Get the original auto-normalized display mode (from LCM calculation).
     *
     * @return Display mode suggested by auto-normalize
     */
    public RateDisplayMode getAutoNormalizedDisplayMode() {
        return entity.autoNormalizedDisplayMode;
    }

    /**
     * Set the original auto-normalized display mode.
     *
     * @param mode Display mode from auto-normalize
     */
    public void setAutoNormalizedDisplayMode(RateDisplayMode mode) {
        entity.autoNormalizedDisplayMode = mode;
        entity.setChanged();
    }
}
