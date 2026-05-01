package com.mukulramesh.fpscompress.gui;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.network.FaceConfigPacket;
import com.mukulramesh.fpscompress.portal.FaceConfig;
import com.mukulramesh.fpscompress.portal.PrefabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
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

    public PreFabConfigMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos(), Direction.from3DDataValue(extraData.readByte()));
    }

    public PreFabConfigMenu(int containerId, Inventory playerInventory, BlockPos prefabPos) {
        this(containerId, playerInventory, prefabPos, Direction.NORTH);
    }

    public PreFabConfigMenu(int containerId, Inventory playerInventory, BlockPos prefabPos, Direction defaultFace) {
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
    }

    public FaceConfig getFaceConfig(Direction direction) {
        return faceConfigs.get(direction);
    }

    public Direction getDefaultFace() {
        return defaultFace;
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
