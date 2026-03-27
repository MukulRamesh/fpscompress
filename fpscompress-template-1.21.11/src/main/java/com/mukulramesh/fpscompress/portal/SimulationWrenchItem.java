package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.portal.FPSDataAttachments.VirtualMachineDataWrapper;
import dev.compactmods.machines.machine.block.BoundCompactMachineBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Control tool for managing factory simulation states.
 *
 * Right-click a Compact Machine to control simulation:
 * - BUILDING → SIMULATING: Start observing production rates
 * - SIMULATING → CACHED: Complete observation and switch to math-only mode
 * - CACHED → (any issue) → HALTED: When machine runs out of inputs/outputs
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
     * Logic:
     * 1. Check if target is a Compact Machine BlockEntity
     * 2. Check if TPS upgrade is installed
     * 3. Determine current state and transition accordingly
     * 4. Call appropriate FactoryIntegrator methods
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

        // Check if target is a Compact Machine
        if (!(blockEntity instanceof BoundCompactMachineBlockEntity cmBE)) {
            player.displayClientMessage(
                Component.literal("§cThis only works on Compact Machines!"),
                true
            );
            return InteractionResult.FAIL;
        }

        // Cast to BlockEntity for attachment access
        BlockEntity be = (BlockEntity) cmBE;

        // Get VirtualMachineDataImpl attachment
        VirtualMachineDataWrapper wrapper = be.getData(FPSDataAttachments.VIRTUAL_MACHINE_DATA);

        // Check if upgrade is installed (wrapper is never null due to getData() contract)
        if (!wrapper.hasTpsUpgrade()) {
            player.displayClientMessage(
                Component.literal("§cThis machine needs a TPS Cache Upgrade first!"),
                true
            );
            return InteractionResult.FAIL;
        }

        // TODO: Get FactoryIntegrator instance for this machine
        // For now, simulate state transitions with placeholder logic

        // Placeholder state tracking (will be replaced by IMachineLogic)
        // For MVP, we'll just toggle between BUILDING and SIMULATING states
        String currentState = "BUILDING"; // Default state

        FPSCompress.LOGGER.info("Simulation Wrench used on CM at {} by player {}",
            context.getClickedPos(), player.getName().getString());

        // State transition logic (placeholder until FactoryIntegrator is ready)
        switch (currentState) {
            case "BUILDING":
                // Start simulation
                player.displayClientMessage(
                    Component.literal("§aSimulation started! Observing production rates..."),
                    true
                );
                FPSCompress.LOGGER.info("Would call: integrator.beginSimulation()");
                // TODO: integrator.beginSimulation()
                break;

            case "SIMULATING":
                // End simulation and enter CACHED mode
                player.displayClientMessage(
                    Component.literal("§aSimulation complete! Machine cached."),
                    true
                );
                FPSCompress.LOGGER.info("Would call: integrator.endSimulation()");
                // TODO: integrator.endSimulation()
                break;

            case "CACHED":
                // Already cached
                player.displayClientMessage(
                    Component.literal("§eMachine is already running in cached mode!"),
                    true
                );
                break;

            case "HALTED":
                // Machine halted due to starvation/blockage
                player.displayClientMessage(
                    Component.literal("§cMachine halted! Check inputs/outputs."),
                    true
                );
                FPSCompress.LOGGER.warn("Machine at {} is halted", context.getClickedPos());
                break;

            default:
                player.displayClientMessage(
                    Component.literal("§cUnknown machine state!"),
                    true
                );
                break;
        }

        return InteractionResult.SUCCESS;
    }
}
