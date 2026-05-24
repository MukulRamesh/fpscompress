package com.mukulramesh.fpscompress.gui;

/**
 * Time scale modes for displaying production rates in PreFab status GUI.
 * Controls how rates are converted from per-tick base format to user-friendly time scales.
 */
public enum RateDisplayMode {
    PER_TICK(1.0, "Per Tick"),
    PER_SECOND(20.0, "Per Second"),      // 20 ticks = 1 second
    PER_MINUTE(1200.0, "Per Minute"),    // 1200 ticks = 1 minute
    PER_HOUR(72000.0, "Per Hour");       // 72000 ticks = 1 hour

    private final double multiplier;
    private final String displayName;

    RateDisplayMode(double multiplier, String displayName) {
        this.multiplier = multiplier;
        this.displayName = displayName;
    }

    /**
     * Get the multiplier to convert from per-tick rates.
     *
     * @return Multiplier value (1.0, 20.0, 1200.0, or 72000.0)
     */
    public double getMultiplier() {
        return multiplier;
    }

    /**
     * Get the display name for this time scale.
     *
     * @return Display name (e.g., "Per Second")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Cycle to the next display mode in order.
     * Wraps around from PER_HOUR back to PER_TICK.
     *
     * @return Next mode in cycle
     */
    public RateDisplayMode next() {
        RateDisplayMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    /**
     * Convert a per-tick rate to this time scale.
     *
     * @param perTickRate Rate in per-tick format
     * @return Rate converted to this time scale
     */
    public double convert(double perTickRate) {
        return perTickRate * multiplier;
    }

    /**
     * Format a per-tick rate as a string with 2 decimal places in this time scale.
     * Uses locale-specific formatting for thousands separators.
     *
     * @param perTickRate Rate in per-tick format
     * @return Formatted string (e.g., "10.00", "23,998.40")
     */
    public String formatRate(double perTickRate) {
        double converted = convert(perTickRate);
        return String.format("%.2f", converted);
    }

    /**
     * Get mode by ordinal (for NBT deserialization).
     * Returns PER_TICK if ordinal is out of range.
     *
     * @param ordinal Ordinal value
     * @return Mode at ordinal, or PER_TICK if invalid
     */
    public static RateDisplayMode fromOrdinal(int ordinal) {
        RateDisplayMode[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return PER_TICK; // Default fallback
        }
        return values[ordinal];
    }
}
