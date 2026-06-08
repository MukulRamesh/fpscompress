package com.mukulramesh.fpscompress.portal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.mukulramesh.fpscompress.gui.RateDisplayMode;
import org.jetbrains.annotations.Nullable;

/**
 * Manages display preferences for PreFab status GUI.
 * Handles rate display modes, focused resources, and auto-normalization settings.
 */
public class DisplayPreferenceManager {
    private final PrefabBlockEntity entity;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
        justification = "Service class intentionally holds reference to entity for delegation pattern")
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

}
