package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.gui.PreFabConfigMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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

        // Phase 1 Part C: Shift+Right-click opens face config GUI
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                // Open face configuration GUI with BlockPos as extra data
                serverPlayer.openMenu(prefab, context.getClickedPos());

                FPSCompress.LOGGER.info("Opened PreFab config GUI for player {} at {}",
                    player.getName().getString(), context.getClickedPos());
            }
            return InteractionResult.SUCCESS;
        }

        // Get current machine state
        MachineState currentState = prefab.getCurrentState();

        FPSCompress.LOGGER.info("Simulation Wrench used on PreFab at {} by player {}, current state: {}",
            context.getClickedPos(), player.getName().getString(), currentState);

        // TODO Phase 6: Implement state transitions
        // For now, just show current state
        player.displayClientMessage(
            Component.literal(String.format("§ePreFab State: §b%s", currentState.name())),
            true
        );
        player.displayClientMessage(
            Component.literal("§7(State transitions not yet implemented - Phase 6)"),
            true
        );

        return InteractionResult.SUCCESS;
    }
}
