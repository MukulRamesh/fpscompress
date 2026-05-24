package com.mukulramesh.fpscompress.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for normalizing production rates to human-readable formats.
 * Provides LCM-based auto-normalization and item-focused normalization.
 */
public final class RateNormalizer {

    private RateNormalizer() {
        // Utility class, no instantiation
    }

    /**
     * Automatically normalize rates to whole numbers using LCM with cascading time scales.
     * Avoids awkward displays like "50,000 iron per 10,000 ticks" by escalating to larger time units.
     *
     * @param rates Map of resource IDs to per-tick rates
     * @return Normalization result with ticks, suggested mode, and whole number rates
     */
    public static NormalizationResult autoNormalize(Map<String, Double> rates) {
        if (rates == null || rates.isEmpty()) {
            return new NormalizationResult(1, RateDisplayMode.PER_TICK, new HashMap<>());
        }

        // Step 1: Convert rates to fractions
        List<Fraction> fractions = new ArrayList<>();
        for (Double rate : rates.values()) {
            if (rate != null && rate != 0.0) {
                fractions.add(toFraction(Math.abs(rate), 10000)); // Max denominator = 10,000
            }
        }

        if (fractions.isEmpty()) {
            return new NormalizationResult(1, RateDisplayMode.PER_TICK, new HashMap<>());
        }

        // Step 2: Calculate LCM of denominators
        long lcm = calculateLCM(fractions);

        // Step 3: Cascading time scale logic
        // Try ticks first (within 5 seconds = 100 ticks)
        if (lcm <= 100) {
            return new NormalizationResult(
                (int) lcm,
                RateDisplayMode.PER_TICK,
                calculateWholeNumbers(rates, lcm)
            );
        }

        // Convert to per-second and retry (within 100 seconds)
        Map<String, Double> perSecondRates = convertRates(rates, 20.0);
        List<Fraction> secondFractions = new ArrayList<>();
        for (Double rate : perSecondRates.values()) {
            if (rate != null && rate != 0.0) {
                secondFractions.add(toFraction(Math.abs(rate), 10000));
            }
        }
        long lcmSeconds = calculateLCM(secondFractions);
        if (lcmSeconds <= 100) {
            return new NormalizationResult(
                (int) (lcmSeconds * 20),
                RateDisplayMode.PER_SECOND,
                calculateWholeNumbers(perSecondRates, lcmSeconds)
            );
        }

        // Convert to per-minute and retry (within 10 minutes)
        Map<String, Double> perMinuteRates = convertRates(rates, 1200.0);
        List<Fraction> minuteFractions = new ArrayList<>();
        for (Double rate : perMinuteRates.values()) {
            if (rate != null && rate != 0.0) {
                minuteFractions.add(toFraction(Math.abs(rate), 10000));
            }
        }
        long lcmMinutes = calculateLCM(minuteFractions);
        if (lcmMinutes <= 10) {
            return new NormalizationResult(
                (int) (lcmMinutes * 1200),
                RateDisplayMode.PER_MINUTE,
                calculateWholeNumbers(perMinuteRates, lcmMinutes)
            );
        }

        // Fallback: per-hour with 2 decimal truncation
        Map<String, Double> perHourRates = convertRates(rates, 72000.0);
        return new NormalizationResult(
            72000,
            RateDisplayMode.PER_HOUR,
            calculateRoundedRates(perHourRates, 1) // Show as per 1 hour
        );
    }

    /**
     * Normalize all rates to "per 1 unit of focused item".
     * Scales all rates proportionally based on the time needed to produce 1 unit of the focused item.
     *
     * @param rates Map of resource IDs to per-tick rates
     * @param focusedItemId Resource ID to focus on
     * @return Map of resource IDs to normalized rates
     */
    public static Map<String, Double> normalizeToItem(Map<String, Double> rates, String focusedItemId) {
        if (rates == null || focusedItemId == null) {
            return new HashMap<>();
        }
        if (!rates.containsKey(focusedItemId)) {
            return new HashMap<>(rates);
        }

        Double focusedRate = rates.get(focusedItemId);
        if (focusedRate == null || focusedRate == 0.0) {
            return new HashMap<>(rates);
        }

        // Calculate time needed for 1 unit of focused item
        double timeForOneUnit = 1.0 / Math.abs(focusedRate);

        // Scale all rates by this time
        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() * timeForOneUnit);
        }

        return normalized;
    }

    /**
     * Convert double rate to rational fraction using continued fractions algorithm.
     *
     * @param rate Rate to convert
     * @param maxDenominator Maximum allowed denominator
     * @return Fraction approximation
     */
    private static Fraction toFraction(double rate, int maxDenominator) {
        if (rate == 0.0) {
            return new Fraction(0, 1);
        }

        double error = Math.abs(rate);
        long bestNumerator = 0;
        long bestDenominator = 1;

        for (long denom = 1; denom <= maxDenominator; denom++) {
            long numer = Math.round(rate * denom);
            double testError = Math.abs(rate - (double) numer / denom);

            if (testError < error) {
                error = testError;
                bestNumerator = numer;
                bestDenominator = denom;
            }

            if (error < 1e-10) {
                break; // Close enough
            }
        }

        return new Fraction(bestNumerator, bestDenominator);
    }

    /**
     * Calculate LCM of all fraction denominators.
     *
     * @param fractions List of fractions
     * @return LCM of all denominators
     */
    private static long calculateLCM(List<Fraction> fractions) {
        if (fractions.isEmpty()) {
            return 1;
        }

        long lcm = fractions.get(0).denominator();
        for (int i = 1; i < fractions.size(); i++) {
            lcm = lcm(lcm, fractions.get(i).denominator());
        }

        return lcm;
    }

    /**
     * Calculate least common multiple of two numbers.
     *
     * @param a First number
     * @param b Second number
     * @return LCM of a and b
     */
    private static long lcm(long a, long b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return Math.abs(a * b) / gcd(a, b);
    }

    /**
     * Calculate greatest common divisor using Euclidean algorithm.
     *
     * @param a First number
     * @param b Second number
     * @return GCD of a and b
     */
    private static long gcd(long a, long b) {
        while (b != 0) {
            long temp = b;
            b = a % b;
            a = temp;
        }
        return Math.abs(a);
    }

    /**
     * Convert rates to a different time scale.
     *
     * @param rates Map of resource IDs to per-tick rates
     * @param multiplier Time scale multiplier (20.0 for seconds, 1200.0 for minutes, etc.)
     * @return Map of resource IDs to converted rates
     */
    private static Map<String, Double> convertRates(Map<String, Double> rates, double multiplier) {
        Map<String, Double> converted = new HashMap<>();
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            converted.put(entry.getKey(), entry.getValue() * multiplier);
        }
        return converted;
    }

    /**
     * Calculate whole number rates by multiplying by LCM.
     *
     * @param rates Map of resource IDs to rates
     * @param lcm LCM multiplier
     * @return Map of resource IDs to whole number rates
     */
    private static Map<String, Long> calculateWholeNumbers(Map<String, Double> rates, long lcm) {
        Map<String, Long> wholeNumbers = new HashMap<>();
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            long wholeNumber = Math.round(entry.getValue() * lcm);
            wholeNumbers.put(entry.getKey(), wholeNumber);
        }
        return wholeNumbers;
    }

    /**
     * Calculate rounded rates (for fallback per-hour display).
     *
     * @param rates Map of resource IDs to rates
     * @param multiplier Multiplier (usually 1 for per-hour)
     * @return Map of resource IDs to rounded rates
     */
    private static Map<String, Long> calculateRoundedRates(Map<String, Double> rates, long multiplier) {
        Map<String, Long> rounded = new HashMap<>();
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            long roundedValue = Math.round(entry.getValue() * multiplier);
            rounded.put(entry.getKey(), roundedValue);
        }
        return rounded;
    }

    /**
     * Result of auto-normalization containing LCM ticks, suggested display mode, and whole number rates.
     *
     * @param normalizedTicks Number of ticks for normalization (LCM result)
     * @param suggestedMode Suggested display mode (PER_TICK, PER_SECOND, etc.)
     * @param wholeNumberRates Map of resource IDs to whole number rates
     */
    public record NormalizationResult(
        int normalizedTicks,
        RateDisplayMode suggestedMode,
        Map<String, Long> wholeNumberRates
    ) {
        /**
         * Constructor that creates defensive copy of mutable map.
         */
        public NormalizationResult {
            wholeNumberRates = java.util.Collections.unmodifiableMap(
                new java.util.HashMap<>(wholeNumberRates));
        }
    }

    /**
     * Rational fraction representation.
     *
     * @param numerator Numerator
     * @param denominator Denominator
     */
    private record Fraction(
        long numerator,
        long denominator
    ) {
    }
}
