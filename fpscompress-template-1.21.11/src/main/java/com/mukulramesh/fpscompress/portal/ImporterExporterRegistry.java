package com.mukulramesh.fpscompress.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking Importer/Exporter blocks in the world.
 *
 * <p>This is a Phase 2 solution for GUI scanning. Phase 3+ will use a more
 * sophisticated approach via capabilities or chunk scanning.
 *
 * <p>Thread-safe for concurrent access during chunk loading/unloading.
 */
public final class ImporterExporterRegistry {
    // Store full Entry objects (including display names) to avoid chunk loading issues
    private static final ConcurrentHashMap<UUID, Entry> IMPORTERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Entry> EXPORTERS = new ConcurrentHashMap<>();

    private ImporterExporterRegistry() {
        // Utility class
    }

    /**
     * Register an Importer block with its display name.
     * Called when Importer is placed or loaded.
     *
     * @param uuid Importer UUID
     * @param pos Importer position
     * @param displayName Display name (e.g., "Apple Importer")
     */
    public static void registerImporter(UUID uuid, BlockPos pos, String displayName) {
        IMPORTERS.put(uuid, new Entry(uuid, pos, displayName));
        com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
            "[Registry] Registered Importer: {} at {} (Total: {})",
            displayName, pos, IMPORTERS.size()
        );
    }

    /**
     * Register an Exporter block with its display name.
     * Called when Exporter is placed or loaded.
     *
     * @param uuid Exporter UUID
     * @param pos Exporter position
     * @param displayName Display name (e.g., "Diamond Exporter")
     */
    public static void registerExporter(UUID uuid, BlockPos pos, String displayName) {
        EXPORTERS.put(uuid, new Entry(uuid, pos, displayName));
        com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
            "[Registry] Registered Exporter: {} at {} (Total: {})",
            displayName, pos, EXPORTERS.size()
        );
    }

    /**
     * Unregister an Importer block.
     * Called when Importer is broken or unloaded.
     *
     * @param uuid Importer UUID
     */
    public static void unregisterImporter(UUID uuid) {
        Entry removed = IMPORTERS.remove(uuid);
        if (removed != null) {
            com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                "[Registry] Unregistered Importer: {} (Total: {})",
                removed.displayName(), IMPORTERS.size()
            );
        }
    }

    /**
     * Unregister an Exporter block.
     * Called when Exporter is broken or unloaded.
     *
     * @param uuid Exporter UUID
     */
    public static void unregisterExporter(UUID uuid) {
        Entry removed = EXPORTERS.remove(uuid);
        if (removed != null) {
            com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                "[Registry] Unregistered Exporter: {} (Total: {})",
                removed.displayName(), EXPORTERS.size()
            );
        }
    }

    /**
     * Get all registered Importers with display names.
     * No chunk loading required - display names are cached in registry.
     *
     * @param server Minecraft server (unused, kept for API compatibility)
     * @return List of Importer entries (UUID → position → display name)
     */
    public static List<Entry> getAllImporters(MinecraftServer server) {
        com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
            "[Registry] Query getAllImporters: {} entries",
            IMPORTERS.size()
        );
        return new ArrayList<>(IMPORTERS.values());
    }

    /**
     * Get all registered Exporters with display names.
     * No chunk loading required - display names are cached in registry.
     *
     * @param server Minecraft server (unused, kept for API compatibility)
     * @return List of Exporter entries (UUID → position → display name)
     */
    public static List<Entry> getAllExporters(MinecraftServer server) {
        com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
            "[Registry] Query getAllExporters: {} entries",
            EXPORTERS.size()
        );
        return new ArrayList<>(EXPORTERS.values());
    }

    /**
     * Get Importer entry by UUID.
     *
     * @param uuid Importer UUID
     * @return Entry, or null if not registered
     */
    public static Entry getImporter(UUID uuid) {
        return IMPORTERS.get(uuid);
    }

    /**
     * Get Exporter entry by UUID.
     *
     * @param uuid Exporter UUID
     * @return Entry, or null if not registered
     */
    public static Entry getExporter(UUID uuid) {
        return EXPORTERS.get(uuid);
    }

    /**
     * Get Importer position by UUID.
     *
     * @param uuid Importer UUID
     * @return Position, or null if not registered
     */
    public static BlockPos getImporterPosition(UUID uuid) {
        Entry entry = IMPORTERS.get(uuid);
        return entry != null ? entry.pos() : null;
    }

    /**
     * Get Exporter position by UUID.
     *
     * @param uuid Exporter UUID
     * @return Position, or null if not registered
     */
    public static BlockPos getExporterPosition(UUID uuid) {
        Entry entry = EXPORTERS.get(uuid);
        return entry != null ? entry.pos() : null;
    }

    /**
     * Clear all registrations.
     * Called on server stop or world unload.
     *
     * @param server The server (unused, for future per-world registries)
     */
    public static void clear(MinecraftServer server) {
        IMPORTERS.clear();
        EXPORTERS.clear();
    }

    /**
     * Entry representing an Importer or Exporter registration.
     *
     * @param uuid Block UUID
     * @param pos Block position
     * @param displayName Human-readable name (e.g., "Apple Importer")
     */
    public record Entry(UUID uuid, BlockPos pos, String displayName) {
        @Override
        public String toString() {
            return String.format("%s at (%d, %d, %d)",
                displayName,
                pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
