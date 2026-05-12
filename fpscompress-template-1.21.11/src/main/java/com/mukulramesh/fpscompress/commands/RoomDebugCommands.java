package com.mukulramesh.fpscompress.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mukulramesh.fpscompress.portal.PlayerRoomContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug commands for room-based filtering system.
 *
 * Commands:
 * - /fpscompress room stack - Show current room context
 * - /fpscompress room clear - Clear room stack (OP only)
 */
public final class RoomDebugCommands {

    private RoomDebugCommands() {
        // Utility class
    }

    /**
     * Register room debug commands.
     *
     * @param dispatcher Command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fpscompress")
            .then(Commands.literal("room")
                .then(Commands.literal("stack")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String currentRoom = PlayerRoomContext.getCurrentRoom(player.getUUID());
                        ctx.getSource().sendSuccess(() ->
                            Component.literal("Current room: " + (currentRoom != null ? currentRoom : "none")),
                            false);
                        return 1;
                    })
                )
                .then(Commands.literal("clear")
                    .requires(src -> src.hasPermission(2)) // OP level 2
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        PlayerRoomContext.clearPlayer(player.getUUID());
                        ctx.getSource().sendSuccess(() ->
                            Component.literal("Room stack cleared"), false);
                        return 1;
                    })
                )
            )
        );
    }
}
