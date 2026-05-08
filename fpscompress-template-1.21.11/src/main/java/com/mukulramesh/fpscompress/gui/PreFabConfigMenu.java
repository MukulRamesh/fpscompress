package com.mukulramesh.fpscompress.gui;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.network.FaceConfigPacket;
import com.mukulramesh.fpscompress.portal.FaceConfig;
import com.mukulramesh.fpscompress.portal.ImporterExporterRegistry;
import com.mukulramesh.fpscompress.portal.PrefabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side container menu for PreFab configuration.
 *
 * Manages face configuration state and syncs changes to server.
 */
public class PreFabConfigMenu extends AbstractContainerMenu {
    private final BlockPos prefabPos;
    private final Map<Direction, FaceConfig> faceConfigs = new EnumMap<>(Direction.class);
    private final Direction defaultFace;

    // Cached lists populated on server, accessible from client
    private final List<ImporterExporterRegistry.Entry> cachedImporters = new ArrayList<>();
    private final List<ImporterExporterRegistry.Entry> cachedExporters = new ArrayList<>();

    /**
     * Wrapper for passing Importer/Exporter lists between constructors.
     */
    private record ImportExportLists(
        List<ImporterExporterRegistry.Entry> importers,
        List<ImporterExporterRegistry.Entry> exporters
    ) { }

    public PreFabConfigMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos(), Direction.from3DDataValue(extraData.readByte()),
            readImporterExporterLists(extraData));
    }

    /**
     * Read Importer/Exporter lists from packet buffer.
     */
    private static ImportExportLists readImporterExporterLists(FriendlyByteBuf buf) {
        List<ImporterExporterRegistry.Entry> importers = new ArrayList<>();
        List<ImporterExporterRegistry.Entry> exporters = new ArrayList<>();

        // Read Importers
        int importerCount = buf.readInt();
        for (int i = 0; i < importerCount; i++) {
            java.util.UUID uuid = buf.readUUID();
            BlockPos pos = buf.readBlockPos();
            String displayName = buf.readUtf();
            importers.add(new ImporterExporterRegistry.Entry(uuid, pos, displayName));
        }

        // Read Exporters
        int exporterCount = buf.readInt();
        for (int i = 0; i < exporterCount; i++) {
            java.util.UUID uuid = buf.readUUID();
            BlockPos pos = buf.readBlockPos();
            String displayName = buf.readUtf();
            exporters.add(new ImporterExporterRegistry.Entry(uuid, pos, displayName));
        }

        return new ImportExportLists(importers, exporters);
    }

    public PreFabConfigMenu(int containerId, Inventory playerInventory, BlockPos prefabPos) {
        this(containerId, playerInventory, prefabPos, Direction.NORTH, (ImportExportLists) null);
    }

    public PreFabConfigMenu(int containerId, Inventory playerInventory, BlockPos prefabPos, Direction defaultFace,
                           ImportExportLists listsFromPacket) {
        super(FPSCompress.PREFAB_CONFIG_MENU.get(), containerId);
        this.prefabPos = prefabPos;
        this.defaultFace = defaultFace;

        // Load current configs from BlockEntity
        BlockEntity be = playerInventory.player.level().getBlockEntity(prefabPos);
        if (be instanceof PrefabBlockEntity prefab) {
            for (Direction dir : Direction.values()) {
                FaceConfig original = prefab.getFaceConfig(dir);
                // Create copy to avoid modifying BlockEntity directly
                faceConfigs.put(dir, new FaceConfig(
                    original.getMode(),
                    original.getResourceType(),
                    original.getTargetUUID()
                ));
            }
        } else {
            // Initialize defaults if BlockEntity not found
            for (Direction dir : Direction.values()) {
                faceConfigs.put(dir, new FaceConfig());
            }
        }

        // Populate lists from packet (client) or query server (server)
        if (listsFromPacket != null) {
            // Client-side: use lists from packet
            cachedImporters.addAll(listsFromPacket.importers());
            cachedExporters.addAll(listsFromPacket.exporters());
            com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                "PreFabConfigMenu created on client: {} Importers, {} Exporters",
                cachedImporters.size(), cachedExporters.size()
            );
        } else if (!playerInventory.player.level().isClientSide()) {
            // Server-side: query registry directly
            net.minecraft.server.MinecraftServer server = playerInventory.player.getServer();
            if (server != null) {
                cachedImporters.addAll(ImporterExporterRegistry.getAllImporters(server));
                cachedExporters.addAll(ImporterExporterRegistry.getAllExporters(server));
                com.mukulramesh.fpscompress.FPSCompress.LOGGER.info(
                    "PreFabConfigMenu created on server: {} Importers, {} Exporters",
                    cachedImporters.size(), cachedExporters.size()
                );
            }
        }
    }

    /**
     * Get available Importers for GUI dropdown.
     * Returns cached list populated when menu was created on server.
     *
     * @return List of Importer entries with display names
     */
    public List<ImporterExporterRegistry.Entry> getAvailableImporters() {
        return new ArrayList<>(cachedImporters);
    }

    /**
     * Get available Exporters for GUI dropdown.
     * Returns cached list populated when menu was created on server.
     *
     * @return List of Exporter entries with display names
     */
    public List<ImporterExporterRegistry.Entry> getAvailableExporters() {
        return new ArrayList<>(cachedExporters);
    }

    public FaceConfig getFaceConfig(Direction direction) {
        return faceConfigs.get(direction);
    }

    public Direction getDefaultFace() {
        return defaultFace;
    }

    /**
     * Get PreFab BlockEntity.
     */
    private PrefabBlockEntity getPrefabBlockEntity(Player player) {
        BlockEntity be = player.level().getBlockEntity(prefabPos);
        if (be instanceof PrefabBlockEntity prefab) {
            return prefab;
        }
        return null;
    }

    /**
     * Get all tracked resources from the PreFab's delta tracker.
     * Used for GUI display of initial/final state (creative mode only).
     */
    public java.util.Set<String> getTrackedResources(Player player) {
        PrefabBlockEntity prefab = getPrefabBlockEntity(player);
        if (prefab != null) {
            return prefab.getDeltaTracker().getAllTrackedResources();
        }
        return java.util.Collections.emptySet();
    }

    /**
     * Get initial state for a resource (creative mode only).
     */
    public long getInitialState(Player player, String resourceId) {
        PrefabBlockEntity prefab = getPrefabBlockEntity(player);
        if (prefab != null) {
            return prefab.getDeltaTracker().getInitialState(resourceId);
        }
        return 0;
    }

    /**
     * Get final state for a resource (creative mode only).
     */
    public long getFinalState(Player player, String resourceId) {
        PrefabBlockEntity prefab = getPrefabBlockEntity(player);
        if (prefab != null) {
            return prefab.getDeltaTracker().getFinalState(resourceId);
        }
        return 0;
    }

    /**
     * Send face configurations to server for saving.
     */
    public void saveToServer() {
        PacketDistributor.sendToServer(new FaceConfigPacket(prefabPos, faceConfigs));
    }

    @Override
    public boolean stillValid(Player player) {
        // Check if player is still close enough to PreFab
        return player.distanceToSqr(prefabPos.getX() + 0.5, prefabPos.getY() + 0.5, prefabPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Clean up if needed
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        // No inventory slots in this menu
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
