package com.mukulramesh.fpscompress.portal;

import com.mojang.logging.LogUtils;
import com.mukulramesh.fpscompress.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * Prevents players from placing blacklisted blocks inside PreFab rooms.
 *
 * <p>Uses the {@link PlayerRoomContext} FILO stack to detect when a player
 * is inside a PreFab room, and checks placed blocks against the
 * {@code prefabRoomBlacklistedBlocks} config list (with glob pattern support).
 */
public class PrefabRoomBlockListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        // Only care about players placing blocks
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Is the player inside any CM room?
        String roomCode = PlayerRoomContext.getCurrentRoom(player.getUUID());
        if (roomCode == null) {
            return;
        }

        // Is it a PreFab room? (skip regular CM rooms)
        if (!PlayerRoomContext.isPrefabRoom(roomCode)) {
            return;
        }

        // Get blacklist patterns from config (empty = allow everything)
        List<? extends String> blacklist = Config.SERVER.getPrefabRoomBlacklistedBlocks();
        if (blacklist.isEmpty()) {
            return;
        }

        // Check block ID against glob patterns
        String blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        if (!Config.matchesBlockBlacklist(blockId, blacklist)) {
            return;
        }

        // Cancel placement + notify player
        event.setCanceled(true);
        player.displayClientMessage(
            Component.literal("§cThis block is blacklisted and cannot be placed inside a PreFab room."),
            true);
        LOGGER.debug("Blocked placement of {} by {} in PreFab room {}",
            blockId, player.getName().getString(), roomCode);
    }
}
