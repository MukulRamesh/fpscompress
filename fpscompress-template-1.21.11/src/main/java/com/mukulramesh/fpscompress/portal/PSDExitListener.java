package com.mukulramesh.fpscompress.portal;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

/**
 * Listens for players right-clicking with Personal Shrinking Device to exit PreFab rooms.
 */
public class PSDExitListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();

        // Only process on server side
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // Check if player is holding Personal Shrinking Device
        ItemStack stack = event.getItemStack();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!itemId.equals("compactmachines:personal_shrinking_device")) {
            return;
        }

        // Check if player is in CM dimension
        String dimensionKey = serverPlayer.level().dimension().location().toString();
        if (!dimensionKey.contains("compactmachines")) {
            return;
        }

        // Check if player has a cached entry position
        if (!PlayerPositionCache.hasCachedPosition(serverPlayer.getUUID())) {
            // Silently do nothing - player entered via regular CM, not PreFab
            // This is normal behavior, don't spam messages
            return;
        }

        // Get cached position
        PlayerPositionCache.CachedPosition cached =
            PlayerPositionCache.getEntryPosition(serverPlayer.getUUID());

        if (cached == null) {
            LOGGER.error("Cached position is null for player {}", serverPlayer.getName().getString());
            return;
        }

        // Get the exit dimension
        ServerLevel exitDimension = serverPlayer.getServer().getLevel(cached.getDimension());
        if (exitDimension == null) {
            LOGGER.error("Exit dimension not found: {}", cached.getDimension());
            serverPlayer.displayClientMessage(
                Component.literal("§cExit dimension not found"),
                true
            );
            return;
        }

        // Teleport player back with original rotation
        BlockPos exitPos = cached.getPosition();
        serverPlayer.teleportTo(
            exitDimension,
            exitPos.getX() + 0.5,
            exitPos.getY(),
            exitPos.getZ() + 0.5,
            cached.getYaw(),
            cached.getPitch()
        );

        // Clear cached position
        PlayerPositionCache.clearEntryPosition(serverPlayer.getUUID());

        serverPlayer.displayClientMessage(
            Component.literal("§aExited PreFab room"),
            true
        );

        LOGGER.info("Player {} exited PreFab room to {} at {}",
                   serverPlayer.getName().getString(),
                   cached.getDimension().location(),
                   exitPos);
    }
}
