package com.mukulramesh.fpscompress.blueprint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for NBT requirements loaded from datapacks.
 * Singleton pattern ensures only one registry exists per game instance.
 *
 * <p>JSON files are loaded from {@code data/<namespace>/nbt_requirements/blocks/}
 * and {@code data/<namespace>/nbt_requirements/items/} directories.
 *
 * <p>Thread-safe: Requirements are loaded during datapack reload (main thread)
 * and accessed read-only during scanning (may be async).
 */
public final class NbtRequirementRegistry extends SimplePreparableReloadListener<Map<String, NbtRequirement>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NbtRequirementRegistry.class);

    private static final String BLOCKS_PATH = "nbt_requirements/blocks";
    private static final String ITEMS_PATH = "nbt_requirements/items";

    private static NbtRequirementRegistry instance;

    private Map<String, NbtRequirement> requirements = new HashMap<>();

    private NbtRequirementRegistry() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance.
     * @return Global NBT requirement registry
     */
    public static synchronized NbtRequirementRegistry getInstance() {
        if (instance == null) {
            instance = new NbtRequirementRegistry();
        }
        return instance;
    }

    /**
     * Check if a resource ID has NBT requirements.
     *
     * @param resourceId Namespaced resource ID (e.g., "fpscompress:prefab_block")
     * @return true if NBT should be tracked for this resource
     */
    public boolean hasRequirement(String resourceId) {
        return requirements.containsKey(resourceId);
    }

    /**
     * Get NBT requirement for a resource ID.
     *
     * @param resourceId Namespaced resource ID
     * @return Optional containing requirement if registered, empty otherwise
     */
    public Optional<NbtRequirement> getRequirement(String resourceId) {
        return Optional.ofNullable(requirements.get(resourceId));
    }

    /**
     * Get count of loaded requirements (for debugging).
     * @return Number of registered NBT requirements
     */
    public int getRequirementCount() {
        return requirements.size();
    }

    /**
     * Get all loaded requirements (for debugging/commands).
     * @return Immutable map of resource ID to requirement
     */
    public Map<String, NbtRequirement> getAllRequirements() {
        return Map.copyOf(requirements);
    }

    // === ResourceReloadListener Implementation ===

    @Override
    protected Map<String, NbtRequirement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, NbtRequirement> loadedRequirements = new HashMap<>();

        // Load block requirements
        profiler.push("nbt_requirements_blocks");
        loadRequirementsFromPath(resourceManager, BLOCKS_PATH, loadedRequirements);
        profiler.pop();

        // Load item requirements
        profiler.push("nbt_requirements_items");
        loadRequirementsFromPath(resourceManager, ITEMS_PATH, loadedRequirements);
        profiler.pop();

        LOGGER.info("Loaded {} NBT requirements from datapacks", loadedRequirements.size());
        return loadedRequirements;
    }

    @Override
    protected void apply(Map<String, NbtRequirement> prepared, ResourceManager resourceManager,
                        ProfilerFiller profiler) {
        this.requirements = prepared;
    }

    /**
     * Load NBT requirements from a specific path in all datapacks.
     *
     * @param resourceManager Resource manager with datapack access
     * @param path Path within data/ directory (e.g., "nbt_requirements/blocks")
     * @param output Map to populate with loaded requirements
     */
    private void loadRequirementsFromPath(ResourceManager resourceManager, String path,
                                         Map<String, NbtRequirement> output) {
        // List all JSON files in this directory across all namespaces
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
            path,
            loc -> loc.getPath().endsWith(".json")
        );

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            Resource resource = entry.getValue();

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {

                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                NbtRequirement requirement = NbtRequirement.fromJson(json);

                if (requirement != null) {
                    String resourceId = requirement.resourceId();
                    if (output.containsKey(resourceId)) {
                        LOGGER.warn("Duplicate NBT requirement for {} (from {}), overwriting previous",
                            resourceId, location);
                    }
                    output.put(resourceId, requirement);
                    LOGGER.debug("Loaded NBT requirement for {} from {}", resourceId, location);
                } else {
                    LOGGER.error("Failed to parse NBT requirement from {}", location);
                }

            } catch (Exception e) {
                LOGGER.error("Error loading NBT requirement from {}", location, e);
            }
        }
    }
}
