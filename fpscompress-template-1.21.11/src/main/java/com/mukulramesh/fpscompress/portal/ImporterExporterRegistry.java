package com.mukulramesh.fpscompress.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    // Secondary indexes for O(1) room-based lookup
    // Map: roomCode -> Set of Entries in that room
    private static final ConcurrentHashMap<String, Set<Entry>> IMPORTERS_BY_ROOM = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<Entry>> EXPORTERS_BY_ROOM = new ConcurrentHashMap<>();

    // Track entries with null roomCode (Overworld/legacy blocks)
    private static final Set<Entry> IMPORTERS_WITHOUT_ROOM = ConcurrentHashMap.newKeySet();
    private static final Set<Entry> EXPORTERS_WITHOUT_ROOM = ConcurrentHashMap.newKeySet();

    private ImporterExporterRegistry() {
        // Utility class
    }

    /**
     * Register an Importer block with its display name and room code.
     * Called when Importer is placed or loaded.
     *
     * @param uuid Importer UUID
     * @param pos Importer position
     * @param displayName Display name (e.g., "Apple Importer")
     * @param roomCode Room code where block is placed (null if Overworld/legacy)
     */
    public static void registerImporter(UUID uuid, BlockPos pos, String displayName, @Nullable String roomCode) {
        Entry entry = new Entry(uuid, pos, displayName, roomCode);
        IMPORTERS.put(uuid, entry);

        // Update secondary index for O(1) room-based lookup
        if (roomCode != null) {
            IMPORTERS_BY_ROOM.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet())
                .add(entry);
        } else {
            IMPORTERS_WITHOUT_ROOM.add(entry);
        }

        com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
            "[Registry] Registered Importer: {} at {} in room {} (Total: {})",
            displayName, pos, roomCode != null ? roomCode : "none", IMPORTERS.size()
        );
    }

    /**
     * Register an Exporter block with its display name and room code.
     * Called when Exporter is placed or loaded.
     *
     * @param uuid Exporter UUID
     * @param pos Exporter position
     * @param displayName Display name (e.g., "Diamond Exporter")
     * @param roomCode Room code where block is placed (null if Overworld/legacy)
     */
    public static void registerExporter(UUID uuid, BlockPos pos, String displayName, @Nullable String roomCode) {
        Entry entry = new Entry(uuid, pos, displayName, roomCode);
        EXPORTERS.put(uuid, entry);

        // Update secondary index for O(1) room-based lookup
        if (roomCode != null) {
            EXPORTERS_BY_ROOM.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet())
                .add(entry);
        } else {
            EXPORTERS_WITHOUT_ROOM.add(entry);
        }

        com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
            "[Registry] Registered Exporter: {} at {} in room {} (Total: {})",
            displayName, pos, roomCode != null ? roomCode : "none", EXPORTERS.size()
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
            // Clean secondary index
            if (removed.roomCode() != null) {
                Set<Entry> roomSet = IMPORTERS_BY_ROOM.get(removed.roomCode());
                if (roomSet != null) {
                    roomSet.remove(removed);
                    if (roomSet.isEmpty()) {
                        IMPORTERS_BY_ROOM.remove(removed.roomCode());
                    }
                }
            } else {
                IMPORTERS_WITHOUT_ROOM.remove(removed);
            }

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
            // Clean secondary index
            if (removed.roomCode() != null) {
                Set<Entry> roomSet = EXPORTERS_BY_ROOM.get(removed.roomCode());
                if (roomSet != null) {
                    roomSet.remove(removed);
                    if (roomSet.isEmpty()) {
                        EXPORTERS_BY_ROOM.remove(removed.roomCode());
                    }
                }
            } else {
                EXPORTERS_WITHOUT_ROOM.remove(removed);
            }

            com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                "[Registry] Unregistered Exporter: {} (Total: {})",
                removed.displayName(), EXPORTERS.size()
            );
        }
    }

    /**
     * Get all registered Importers, optionally filtered by room code.
     * Performance: O(1) lookup via secondary index (not O(n) filtering!).
     *
     * @param server Minecraft server (unused, kept for API compatibility)
     * @param roomCodeFilter Room code to filter by (null = return all)
     * @return List of Importer entries matching the filter
     */
    public static List<Entry> getAllImporters(MinecraftServer server, @Nullable String roomCodeFilter) {
        if (roomCodeFilter == null) {
            // Return all Importers
            com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                "[Registry] Query getAllImporters (unfiltered): {} entries",
                IMPORTERS.size()
            );
            return new ArrayList<>(IMPORTERS.values());
        }

        // O(1) lookup via secondary index
        Set<Entry> roomSet = IMPORTERS_BY_ROOM.get(roomCodeFilter);
        if (roomSet != null) {
            com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                "[Registry] Query getAllImporters (room={}): {} entries",
                roomCodeFilter, roomSet.size()
            );
            return new ArrayList<>(roomSet);
        }

        com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
            "[Registry] Query getAllImporters (room={}): 0 entries (room not found)",
            roomCodeFilter
        );
        return new ArrayList<>(); // Room has no Importers
    }

    /**
     * Get all registered Exporters, optionally filtered by room code.
     * Performance: O(1) lookup via secondary index.
     *
     * @param server Minecraft server (unused, kept for API compatibility)
     * @param roomCodeFilter Room code to filter by (null = return all)
     * @return List of Exporter entries matching the filter
     */
    public static List<Entry> getAllExporters(MinecraftServer server, @Nullable String roomCodeFilter) {
        if (roomCodeFilter == null) {
            // Return all Exporters
            com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                "[Registry] Query getAllExporters (unfiltered): {} entries",
                EXPORTERS.size()
            );
            return new ArrayList<>(EXPORTERS.values());
        }

        // O(1) lookup via secondary index
        Set<Entry> roomSet = EXPORTERS_BY_ROOM.get(roomCodeFilter);
        if (roomSet != null) {
            com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                "[Registry] Query getAllExporters (room={}): {} entries",
                roomCodeFilter, roomSet.size()
            );
            return new ArrayList<>(roomSet);
        }

        com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
            "[Registry] Query getAllExporters (room={}): 0 entries (room not found)",
            roomCodeFilter
        );
        return new ArrayList<>(); // Room has no Exporters
    }

    /**
     * Backward-compatible method - returns all Importers without filtering.
     *
     * @param server Minecraft server (unused, kept for API compatibility)
     * @return List of all Importer entries
     */
    public static List<Entry> getAllImporters(MinecraftServer server) {
        return getAllImporters(server, null);
    }

    /**
     * Backward-compatible method - returns all Exporters without filtering.
     *
     * @param server Minecraft server (unused, kept for API compatibility)
     * @return List of all Exporter entries
     */
    public static List<Entry> getAllExporters(MinecraftServer server) {
        return getAllExporters(server, null);
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
        IMPORTERS_BY_ROOM.clear();
        EXPORTERS_BY_ROOM.clear();
        IMPORTERS_WITHOUT_ROOM.clear();
        EXPORTERS_WITHOUT_ROOM.clear();
    }

    /**
     * Entry representing an Importer or Exporter registration.
     *
     * @param uuid Block UUID
     * @param pos Block position
     * @param displayName Human-readable name (e.g., "Apple Importer")
     * @param roomCode Room code where block is placed (null if Overworld/legacy)
     */
    public record Entry(UUID uuid, BlockPos pos, String displayName, @Nullable String roomCode) {
        @Override
        public String toString() {
            return String.format("%s at (%d, %d, %d) [room: %s]",
                displayName,
                pos.getX(), pos.getY(), pos.getZ(),
                roomCode != null ? roomCode : "none");
        }
    }
}
