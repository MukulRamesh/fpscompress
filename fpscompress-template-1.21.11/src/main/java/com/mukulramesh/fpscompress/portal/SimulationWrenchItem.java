package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Control tool for managing factory simulation states.
 *
 * Right-click a PreFab block to control simulation:
 * - BUILDING → SIMULATING: Start observing production rates
 * - SIMULATING → CACHED: Complete observation and switch to math-only mode
 * - CACHED → BUILDING: Reset to configuration mode
 * - HALTED → SIMULATING: Resume after fixing inputs/outputs
 *
 * TODO Phase 6: Implement state transitions with CMInterceptorImpl chunk loading
 *
 * @author Dev 1 - Core Registry Team
 */
public class SimulationWrenchItem extends Item {

    /**
     * Constructor for SimulationWrenchItem.
     *
     * @param properties The item properties
     */
    public SimulationWrenchItem(Properties properties) {
        super(properties);
    }

    /**
     * Called when the player right-clicks a block with this item.
     *
     * Phase 1 Part C: Shift+Right-click opens face config GUI
     * TODO Phase 6: Implement full state machine
     *
     * @param context The use context
     * @return The interaction result
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        // Only process on server side
        if (level.isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());

        // Check if target is a PreFab block
        if (!(blockEntity instanceof PrefabBlockEntity prefab)) {
            player.displayClientMessage(
                Component.literal("§cThis only works on PreFab blocks!"),
                true
            );
            return InteractionResult.FAIL;
        }

        // Phase 1 Part C:
        // Shift+Right-click: Break the block (drop as item)
        // Right-click: Open face config GUI
        if (player.isShiftKeyDown()) {
            // Break the PreFab block and drop it as an item
            level.destroyBlock(context.getClickedPos(), true);
            player.displayClientMessage(
                Component.literal("§ePreFab removed"),
                true
            );
            return InteractionResult.SUCCESS;
        } else {
            // Open face configuration GUI
            if (player instanceof ServerPlayer serverPlayer) {
                // Set the clicked face so GUI defaults to it
                prefab.setClickedFace(context.getClickedFace());

                // Open menu with custom data (BlockPos + Direction + Importer/Exporter lists)
                serverPlayer.openMenu(prefab, buf -> {
                    buf.writeBlockPos(context.getClickedPos());
                    buf.writeByte(context.getClickedFace().get3DDataValue());

                    // Get PreFab's room code for filtering
                    String prefabRoomCode = prefab.getRoomCode();
                    buf.writeBoolean(prefabRoomCode != null);
                    if (prefabRoomCode != null) {
                        buf.writeUtf(prefabRoomCode);
                        FPSCompress.LOGGER.debug("Opening GUI filtered by room: {}", prefabRoomCode);
                    }

                    // Send filtered Importer/Exporter lists for GUI dropdown
                    net.minecraft.server.MinecraftServer server = serverPlayer.getServer();
                    if (server != null) {
                        // Use O(1) filtered queries
                        java.util.List<ImporterExporterRegistry.Entry> importers =
                            ImporterExporterRegistry.getAllImporters(server, prefabRoomCode);
                        java.util.List<ImporterExporterRegistry.Entry> exporters =
                            ImporterExporterRegistry.getAllExporters(server, prefabRoomCode);

                        // Write Importers
                        buf.writeInt(importers.size());
                        for (ImporterExporterRegistry.Entry entry : importers) {
                            buf.writeUUID(entry.uuid());
                            buf.writeBlockPos(entry.pos());
                            buf.writeUtf(entry.displayName());
                            // Write roomCode
                            buf.writeBoolean(entry.roomCode() != null);
                            if (entry.roomCode() != null) {
                                buf.writeUtf(entry.roomCode());
                            }
                        }

                        // Write Exporters
                        buf.writeInt(exporters.size());
                        for (ImporterExporterRegistry.Entry entry : exporters) {
                            buf.writeUUID(entry.uuid());
                            buf.writeBlockPos(entry.pos());
                            buf.writeUtf(entry.displayName());
                            // Write roomCode
                            buf.writeBoolean(entry.roomCode() != null);
                            if (entry.roomCode() != null) {
                                buf.writeUtf(entry.roomCode());
                            }
                        }

                        FPSCompress.LOGGER.info("Sending {} importers, {} exporters for room {}",
                            importers.size(), exporters.size(), prefabRoomCode != null ? prefabRoomCode : "ALL");
                    } else {
                        // No server - write empty lists
                        buf.writeInt(0);
                        buf.writeInt(0);
                    }
                });

                FPSCompress.LOGGER.info("Opened PreFab config GUI for player {} at {} (clicked face: {})",
                    player.getName().getString(), context.getClickedPos(), context.getClickedFace());
            }
            return InteractionResult.SUCCESS;
        }
    }
}
