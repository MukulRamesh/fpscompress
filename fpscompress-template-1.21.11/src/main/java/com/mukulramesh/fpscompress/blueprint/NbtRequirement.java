package com.mukulramesh.fpscompress.blueprint;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Defines NBT requirements for a specific block or item type.
 * Uses a path-based matching protocol declared in data pack JSON files.
 *
 * <p>Each JSON file declares a "match" map of NBT path → strategy.
 * Paths use dot notation with "*" for list elements.
 *
 * @param resourceId Namespaced resource ID (e.g., "fpscompress:prefab_machine")
 * @param rules Map of NBT path to matching rule
 */
public record NbtRequirement(String resourceId, Map<String, MatchRule> rules) {
    public NbtRequirement {
        rules = Collections.unmodifiableMap(new HashMap<>(rules));
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(NbtRequirement.class);

    /** Matching strategy for an NBT node. */
    public enum Strategy {
        EXACT, SUBSET, LIST_SUBSET, RANGE
    }

    /**
     * Matching rule at a specific NBT path.
     *
     * @param strategy How to compare values at this path
     * @param ignore Sub-keys to skip during comparison (for CompoundTag/LIST_SUBSET)
     * @param min Lower bound for RANGE strategy
     * @param max Upper bound for RANGE strategy
     */
    public record MatchRule(Strategy strategy, Set<String> ignore,
                            Optional<Double> min, Optional<Double> max) {
        public MatchRule {
            ignore = Collections.unmodifiableSet(new HashSet<>(ignore));
        }
        static final MatchRule DEFAULT_COMPOUND = new MatchRule(
            Strategy.SUBSET, Set.of(), Optional.empty(), Optional.empty());
        static final MatchRule DEFAULT_PRIMITIVE = new MatchRule(
            Strategy.EXACT, Set.of(), Optional.empty(), Optional.empty());
    }

    // ===== JSON Parsing =====

    /**
     * Deserialize from JSON.
     * {"resource_id": "...", "match": {"path": "strategy", ...}}
     */
    public static NbtRequirement fromJson(JsonObject json) {
        try {
            if (!json.has("resource_id")) {
                LOGGER.error("NBT requirement missing resource_id");
                return null;
            }
            String resourceId = json.get("resource_id").getAsString();

            Map<String, MatchRule> rules = new HashMap<>();
            if (json.has("match")) {
                JsonObject matchObj = json.getAsJsonObject("match");
                for (Map.Entry<String, JsonElement> entry : matchObj.entrySet()) {
                    String path = entry.getKey();
                    MatchRule rule = parseRule(entry.getValue());
                    if (rule != null) {
                        rules.put(path, rule);
                    }
                }
            }

            if (rules.isEmpty()) {
                LOGGER.warn("NBT requirement for {} has no match rules", resourceId);
            }

            return new NbtRequirement(resourceId, Collections.unmodifiableMap(rules));
        } catch (Exception e) {
            LOGGER.error("Failed to parse NBT requirement from JSON", e);
            return null;
        }
    }

    private static MatchRule parseRule(JsonElement element) {
        // String shorthand: "exact"
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            Strategy s = parseStrategy(element.getAsString());
            return new MatchRule(s, Set.of(), Optional.empty(), Optional.empty());
        }
        // Object form: {"strategy": "list_subset", "ignore": ["uuid"], "min": 0}
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Strategy s = obj.has("strategy")
                ? parseStrategy(obj.get("strategy").getAsString())
                : Strategy.EXACT;

            Set<String> ignore = Set.of();
            if (obj.has("ignore")) {
                Set<String> set = new HashSet<>();
                for (JsonElement e : obj.getAsJsonArray("ignore")) {
                    set.add(e.getAsString());
                }
                ignore = Collections.unmodifiableSet(set);
            }

            Optional<Double> min = obj.has("min")
                ? Optional.of(obj.get("min").getAsDouble()) : Optional.empty();
            Optional<Double> max = obj.has("max")
                ? Optional.of(obj.get("max").getAsDouble()) : Optional.empty();

            return new MatchRule(s, ignore, min, max);
        }
        LOGGER.error("Invalid match rule: {}", element);
        return null;
    }

    private static Strategy parseStrategy(String s) {
        try {
            return Strategy.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown match strategy '{}', using EXACT", s);
            return Strategy.EXACT;
        }
    }

    // ===== Field Extraction (for scanning) =====

    /**
     * Get top-level field names to extract during block/item scanning.
     * Returns the first segment of each path.
     */
    public List<String> getTrackedFields() {
        Set<String> fields = new HashSet<>();
        for (String path : rules.keySet()) {
            int dot = path.indexOf('.');
            int star = path.indexOf('*');
            int end = path.length();
            if (dot >= 0) {
                end = dot;
            }
            if (star >= 0 && star < end) {
                end = star;
            }
            fields.add(path.substring(0, end));
        }
        return new ArrayList<>(fields);
    }

    // ===== NBT Extraction =====

    /**
     * Extract specific fields from a full NBT tag using dot-notation paths.
     */
    public static CompoundTag extractNbt(CompoundTag fullNbt, List<String> fields) {
        if (fullNbt == null || fields.isEmpty()) {
            return new CompoundTag();
        }
        CompoundTag result = new CompoundTag();
        for (String fieldPath : fields) {
            Tag value = getNestedTag(fullNbt, fieldPath);
            if (value != null) {
                putNestedTag(result, fieldPath, value);
            }
        }
        return result;
    }

    private static Tag getNestedTag(CompoundTag tag, String path) {
        String[] parts = path.split("\\.");
        Tag current = tag;
        for (int i = 0; i < parts.length; i++) {
            if (!(current instanceof CompoundTag compound)) {
                return null;
            }
            if (i == parts.length - 1) {
                return compound.contains(parts[i]) ? compound.get(parts[i]) : null;
            }
            if (!compound.contains(parts[i])) {
                return null;
            }
            current = compound.get(parts[i]);
        }
        return null;
    }

    private static void putNestedTag(CompoundTag tag, String path, Tag value) {
        String[] parts = path.split("\\.");
        CompoundTag current = tag;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.contains(parts[i], Tag.TAG_COMPOUND)) {
                current.put(parts[i], new CompoundTag());
            }
            current = current.getCompound(parts[i]);
        }
        current.put(parts[parts.length - 1], value);
    }

    // ===== Matching Engine =====

    /**
     * Check if available NBT satisfies the required NBT according to declared rules.
     */
    public boolean matches(CompoundTag required, CompoundTag available) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        if (available == null) {
            return false;
        }
        return matchNode(required, available, "");
    }

    /**
     * Recursively compare required vs available at a given path.
     */
    private boolean matchNode(Tag required, Tag available, String path) {
        if (required == null) {
            return true;
        }
        if (available == null) {
            return false;
        }
        if (required.getId() != available.getId()) {
            return false;
        }

        MatchRule rule = rules.getOrDefault(path,
            required instanceof CompoundTag
                ? MatchRule.DEFAULT_COMPOUND
                : MatchRule.DEFAULT_PRIMITIVE);

        return switch (rule.strategy()) {
            case EXACT -> required.equals(available);
            case SUBSET -> matchSubset((CompoundTag) required, (CompoundTag) available,
                path, rule.ignore());
            case LIST_SUBSET -> matchListSubset((ListTag) required, (ListTag) available,
                path, rule.ignore());
            case RANGE -> matchRange((NumericTag) required, rule.min(), rule.max());
        };
    }

    private boolean matchSubset(CompoundTag required, CompoundTag available,
                                 String path, Set<String> ignore) {
        for (String key : required.getAllKeys()) {
            if (ignore.contains(key)) {
                continue;
            }
            if (!available.contains(key)) {
                return false;
            }
            String childPath = path.isEmpty() ? key : path + "." + key;
            if (!matchNode(required.get(key), available.get(key), childPath)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchListSubset(ListTag required, ListTag available,
                                     String path, Set<String> ignore) {
        for (int i = 0; i < required.size(); i++) {
            Tag reqElem = required.get(i);
            boolean found = false;
            for (int j = 0; j < available.size(); j++) {
                Tag availElem = available.get(j);
                // Strip ignored keys from compound elements before comparing
                if (reqElem instanceof CompoundTag reqCt
                        && availElem instanceof CompoundTag availCt) {
                    CompoundTag reqFiltered = stripKeys(reqCt, ignore);
                    CompoundTag availFiltered = stripKeys(availCt, ignore);
                    String elemPath = path.isEmpty() ? "*" : path + ".*";
                    if (matchNode(reqFiltered, availFiltered, elemPath)) {
                        found = true;
                        break;
                    }
                } else if (matchNode(reqElem, availElem, path.isEmpty() ? "*" : path + ".*")) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private boolean matchRange(NumericTag required, Optional<Double> min,
                                Optional<Double> max) {
        double value = required.getAsDouble();
        if (min.isPresent() && value < min.get()) {
            return false;
        }
        if (max.isPresent() && value > max.get()) {
            return false;
        }
        return true;
    }

    /**
     * Create a copy of the tag with specified keys removed.
     */
    private static CompoundTag stripKeys(CompoundTag tag, Set<String> keys) {
        if (keys.isEmpty()) {
            return tag;
        }
        CompoundTag copy = tag.copy();
        for (String key : keys) {
            copy.remove(key);
        }
        return copy;
    }
}
