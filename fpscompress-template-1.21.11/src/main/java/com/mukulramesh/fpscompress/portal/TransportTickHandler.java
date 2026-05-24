package com.mukulramesh.fpscompress.portal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Handles resource transport and tick logic.
 * Routes items/fluids/energy between Overworld and CM dimension during SIMULATING state.
 */
public class TransportTickHandler {
    private final PrefabBlockEntity entity;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
        justification = "Service class intentionally holds reference to entity for delegation pattern")
    public TransportTickHandler(PrefabBlockEntity entity) {
        this.entity = entity;
    }

    /**
     * Server-side tick method for resource transport.
     * Called every tick by BlockEntityTicker registered in PrefabBlock.getTicker().
     *
     * @param level The level (server-side)
     * @param pos The PreFab position
     * @param state The block state
     */
    public void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            return;
        }

        // SIMULATING state: Increment elapsed time counter
        if (entity.getCurrentState() == MachineState.SIMULATING) {
            entity.incrementSimulationElapsed();
        }

        // Phase 5: Handle CACHED and HALTED modes (fractional production without CM chunks)
        if (entity.getCurrentState() == MachineState.CACHED
                || entity.getCurrentState() == MachineState.HALTED) {
            entity.getCachedHandler().tickCachedProduction();
            return; // Don't process faces during CACHED/HALTED modes
        }

        // Process each configured face (SIMULATING mode only, due to Phase 4 restriction)
        for (Direction face : Direction.values()) {
            FaceConfig config = entity.getFaceConfig(face);
            if (config.getMode() == FaceMode.DISABLED) {
                continue; // Skip disabled faces
            }

            // Only handle ITEMS for now (fluids/energy support planned for future)
            if (config.getResourceType() != ResourceFilter.ITEMS
                    && config.getResourceType() != ResourceFilter.ALL) {
                continue;
            }

            if (config.getMode() == FaceMode.PULL) {
                handlePullFace(face, config);
            } else if (config.getMode() == FaceMode.PUSH) {
                handlePushFace(face, config);
            }
        }
    }

    /**
     * Handle PULL mode: Extract from Overworld → Insert to Importer.
     *
     * @param face The face direction
     * @param config The face configuration
     */
    private void handlePullFace(Direction face, FaceConfig config) {
        // Only transport during SIMULATING state
        if (entity.currentState != MachineState.SIMULATING) {
            return;
        }

        // 1. Get CM dimension
        ServerLevel cmLevel = getCMLevel();
        if (cmLevel == null) {
            return; // CM dimension not loaded
        }

        // 2. Query adjacent Overworld block capability
        BlockPos overworldPos = entity.getBlockPos().relative(face);
        IItemHandler overworldHandler = entity.getLevel().getCapability(
            Capabilities.ItemHandler.BLOCK,
            overworldPos,
            face.getOpposite()
        );
        if (overworldHandler == null) {
            return; // No inventory adjacent
        }

        // 3. Find target Importer by UUID
        UUID targetUUID = config.getTargetUUID();
        if (targetUUID == null) {
            return; // Face not linked to Importer
        }

        ImporterBlockEntity importer = entity.findImporterByUUID(cmLevel, targetUUID);
        if (importer == null) {
            // Cache miss - try building cache from registry
            ImporterExporterRegistry.Entry entry = ImporterExporterRegistry.getImporter(targetUUID);
            if (entry != null) {
                entity.cacheImporterPosition(targetUUID, entry.pos());
                importer = entity.findImporterByUUID(cmLevel, targetUUID);
            }
            if (importer == null) {
                return; // Importer broken/missing
            }
        }

        // 4. Try extracting from Overworld (up to 64 items per tick)
        net.minecraft.world.item.ItemStack extracted = net.minecraft.world.item.ItemStack.EMPTY;
        for (int slot = 0; slot < overworldHandler.getSlots(); slot++) {
            extracted = overworldHandler.extractItem(slot, 64, false);
            if (!extracted.isEmpty()) {
                break;
            }
        }
        if (extracted.isEmpty()) {
            return; // Nothing to transport
        }

        // 5. Insert to Importer buffer
        net.minecraft.world.item.ItemStack remainder = importer.insertItem(extracted);

        // Phase 4: Track imports during SIMULATING (Phase 6: with UUID)
        int transferred = extracted.getCount() - remainder.getCount();
        if (transferred > 0 && entity.currentState == MachineState.SIMULATING) {
            String resourceId = BuiltInRegistries.ITEM.getKey(extracted.getItem()).toString();
            UUID importerUUID = config.getTargetUUID();  // Already validated non-null above
            entity.deltaTracker.recordImport(importerUUID, resourceId, transferred,
                entity.getLevel().getGameTime());
        }

        // 6. Put remainder back if Importer buffer full
        if (!remainder.isEmpty()) {
            for (int slot = 0; slot < overworldHandler.getSlots(); slot++) {
                remainder = overworldHandler.insertItem(slot, remainder, false);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
    }

    /**
     * Handle PUSH mode: Extract from Exporter → Insert to Overworld.
     *
     * @param face The face direction
     * @param config The face configuration
     */
    private void handlePushFace(Direction face, FaceConfig config) {
        // Only transport during SIMULATING state
        if (entity.currentState != MachineState.SIMULATING) {
            return;
        }

        // 1. Get CM dimension
        ServerLevel cmLevel = getCMLevel();
        if (cmLevel == null) {
            return; // CM dimension not loaded
        }

        // 2. Find target Exporter by UUID
        UUID targetUUID = config.getTargetUUID();
        if (targetUUID == null) {
            return; // Face not linked to Exporter
        }

        ExporterBlockEntity exporter = entity.findExporterByUUID(cmLevel, targetUUID);
        if (exporter == null) {
            // Cache miss - try building cache from registry
            ImporterExporterRegistry.Entry entry = ImporterExporterRegistry.getExporter(targetUUID);
            if (entry != null) {
                entity.cacheExporterPosition(targetUUID, entry.pos());
                exporter = entity.findExporterByUUID(cmLevel, targetUUID);
            }
            if (exporter == null) {
                return; // Exporter broken/missing
            }
        }

        // 3. Try extracting from Exporter buffer (up to 64 items per tick)
        net.minecraft.world.item.ItemStack extracted = exporter.extractFromBuffer(64);
        if (extracted.isEmpty()) {
            return; // Nothing to transport
        }

        // 4. Query adjacent Overworld block capability
        BlockPos overworldPos = entity.getBlockPos().relative(face);
        IItemHandler overworldHandler = entity.getLevel().getCapability(
            Capabilities.ItemHandler.BLOCK,
            overworldPos,
            face.getOpposite()
        );
        if (overworldHandler == null) {
            // Can't insert to Overworld - put back in Exporter
            exporter.insertItem(extracted);
            return;
        }

        // 5. Insert to Overworld
        net.minecraft.world.item.ItemStack remainder = extracted;
        for (int slot = 0; slot < overworldHandler.getSlots(); slot++) {
            remainder = overworldHandler.insertItem(slot, remainder, false);
            if (remainder.isEmpty()) {
                break;
            }
        }

        // Phase 4: Track exports during SIMULATING (Phase 6: with UUID)
        int transferred = extracted.getCount() - remainder.getCount();
        if (transferred > 0 && entity.currentState == MachineState.SIMULATING) {
            String resourceId = BuiltInRegistries.ITEM.getKey(extracted.getItem()).toString();
            UUID exporterUUID = config.getTargetUUID();  // Already validated non-null above
            entity.deltaTracker.recordExport(exporterUUID, resourceId, transferred,
                entity.getLevel().getGameTime());
        }

        // 6. Put remainder back in Exporter if Overworld full
        if (!remainder.isEmpty()) {
            exporter.insertItem(remainder);
        }
    }

    /**
     * Get CM dimension level.
     *
     * @return The CM dimension ServerLevel, or null if not available
     */
    @Nullable
    private ServerLevel getCMLevel() {
        if (entity.getLevel() == null || entity.getLevel().isClientSide()) {
            return null;
        }
        net.minecraft.server.MinecraftServer server = entity.getLevel().getServer();
        if (server == null) {
            return null;
        }
        return server.getLevel(
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse("compactmachines:compact_world")
            )
        );
    }
}
