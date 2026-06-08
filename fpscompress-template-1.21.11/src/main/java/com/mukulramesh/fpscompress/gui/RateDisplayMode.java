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
     * Format a per-tick rate as a compact string in this time scale.
     * Uses K/M/B/T/Q/Qi/Sx/Sp/Oc/No/Dc suffixes for values ≥ 1000.
     *
     * <p>Examples:
     * <ul>
     *   <li>42.50 → "42.50"</li>
     *   <li>1200.00 → "1.20K"</li>
     *   <li>1234567.00 → "1.23M"</li>
     *   <li>5000000000.00 → "5.00B"</li>
     * </ul>
     *
     * @param perTickRate Rate in per-tick format
     * @return Compact formatted string (e.g., "10.00", "1.20K")
     */
    public String formatRate(double perTickRate) {
        double converted = convert(perTickRate);
        return compactFormat(converted);
    }

    /** Suffixes for compact number formatting (short scale, through decillion). */
    private static final String[] COMPACT_SUFFIXES = {
        "", "K", "M", "B", "T", "Q", "Qi", "Sx", "Sp", "Oc", "No", "Dc"
    };

    /**
     * Format a number with compact suffixes (K, M, B, T, Q, ...) for values ≥ 1000.
     * Values below 1000 are shown with 2 decimal places.
     *
     * @param value The value to format
     * @return Compact formatted string
     */
    static String compactFormat(double value) {
        double abs = Math.abs(value);
        if (abs < 1000.0) {
            return String.format("%.2f", value);
        }
        int tier = 0;
        double scaled = abs;
        while (scaled >= 1000.0 && tier < COMPACT_SUFFIXES.length - 1) {
            scaled /= 1000.0;
            tier++;
        }
        String sign = value < 0 ? "-" : "";
        return sign + String.format("%.2f%s", scaled, COMPACT_SUFFIXES[tier]);
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
