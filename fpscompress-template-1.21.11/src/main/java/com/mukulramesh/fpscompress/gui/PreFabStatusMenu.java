package com.mukulramesh.fpscompress.gui;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.portal.MachineState;
import com.mukulramesh.fpscompress.portal.PrefabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side container menu for PreFab status/control GUI.
 * Shows current state and provides control button for state transitions.
 */
public class PreFabStatusMenu extends AbstractContainerMenu {

    private final BlockPos prefabPos;
    @Nullable
    private final PrefabBlockEntity prefabEntity;
    private final boolean isCreativeMode;

    /**
     * Server-side constructor.
     *
     * @param containerId Container ID
     * @param playerInventory Player inventory
     * @param prefabPos PreFab position
     */
    public PreFabStatusMenu(int containerId, Inventory playerInventory, BlockPos prefabPos) {
        super(FPSCompress.PREFAB_STATUS_MENU.get(), containerId);
        this.prefabPos = prefabPos;
        this.isCreativeMode = playerInventory.player.isCreative();

        // Get PreFab BlockEntity (server-side only)
        BlockEntity be = playerInventory.player.level().getBlockEntity(prefabPos);
        this.prefabEntity = be instanceof PrefabBlockEntity ? (PrefabBlockEntity) be : null;
    }

    /**
     * Client-side constructor (reads from packet buffer).
     *
     * @param containerId Container ID
     * @param playerInventory Player inventory
     * @param buf Packet buffer
     */
    public PreFabStatusMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public boolean isCreativeMode() {
        return isCreativeMode;
    }

    @Override
    public boolean stillValid(Player player) {
        // Check if PreFab still exists and player is close enough
        if (prefabEntity == null || prefabEntity.isRemoved()) {
            return false;
        }
        return player.distanceToSqr(
            prefabPos.getX() + 0.5,
            prefabPos.getY() + 0.5,
            prefabPos.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        // Send sync packet to client every tick (server-side only)
        if (prefabEntity != null && prefabEntity.getLevel() != null
                && !prefabEntity.getLevel().isClientSide()) {
            // Find player who has this menu open
            for (net.minecraft.world.entity.player.Player player
                    : prefabEntity.getLevel().players()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                        && serverPlayer.containerMenu == this) {
                    // Send sync packet
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.mukulramesh.fpscompress.network.StatusGuiSyncPacket(
                            prefabEntity.getCurrentState(),
                            prefabEntity.getSimulationStartTick(),
                            prefabEntity.getSimulationEndTick(),
                            prefabEntity.getCachedStateStartTick(),
                            prefabEntity.getLevel().getGameTime(),
                            prefabEntity.getLiveStats(),
                            prefabEntity.getCachedRates(),
                            prefabEntity.getCachedProduction(),
                            prefabEntity.getLastSimulationResult(),
                            prefabEntity.getSimulationElapsedTicks(),
                            prefabEntity.getSimulationRequiredTicks(),
                            prefabEntity.getCurrentDisplayMode(),
                            prefabEntity.getFocusedResourceId(),
                            prefabEntity.getAutoNormalizedTicks(),
                            prefabEntity.getUseAutoNormalize(),
                            prefabEntity.getAutoNormalizedDisplayMode(),
                            prefabEntity.getPrefabName()
                        )
                    );
                }
            }
        }
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        // No inventory slots in status GUI, so no quick move
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /**
     * Get PreFab position.
     *
     * @return PreFab block position
     */
    public BlockPos getPrefabPos() {
        return prefabPos;
    }

    /**
     * Get current machine state.
     *
     * @return Current state, or BUILDING if PreFab not found
     */
    public MachineState getCurrentState() {
        return prefabEntity != null ? prefabEntity.getCurrentState() : MachineState.BUILDING;
    }

    /**
     * Get room code.
     *
     * @return Room code, or null if not linked
     */
    @Nullable
    public String getRoomCode() {
        return prefabEntity != null ? prefabEntity.getRoomCode() : null;
    }

    /**
     * Get number of cached rates.
     *
     * @return Number of cached resource types
     */
    public int getCachedRatesCount() {
        return prefabEntity != null ? prefabEntity.getCachedRates().size() : 0;
    }

    /**
     * Get simulation start tick.
     *
     * @return Start tick, or 0 if not started
     */
    public long getSimulationStartTick() {
        return prefabEntity != null ? prefabEntity.getSimulationStartTick() : 0;
    }

    /**
     * Get current game time.
     *
     * @return Current tick
     */
    public long getCurrentTick() {
        if (prefabEntity == null || prefabEntity.getLevel() == null) {
            return 0;
        }
        return prefabEntity.getLevel().getGameTime();
    }

    /**
     * Get live import/export stats from deltaTracker.
     *
     * @return Map of resource ID → [imported, exported]
     */
    public java.util.Map<String, long[]> getLiveStats() {
        if (prefabEntity == null) {
            return java.util.Collections.emptyMap();
        }
        return prefabEntity.getLiveStats();
    }
}
