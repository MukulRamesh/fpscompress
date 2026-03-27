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
 * Item that installs the TPS Cache Upgrade on Compact Machines.
 *
 * Right-click a Compact Machine to enable TPS caching mode.
 * Once installed, the machine can operate in "headless" mode with
 * virtual buffers instead of physical chunk simulation.
 *
 * @author Dev 1 - Core Registry Team
 */
public class TpsCacheUpgradeItem extends Item {

    /**
     * Constructor for TpsCacheUpgradeItem.
     *
     * @param properties The item properties
     */
    public TpsCacheUpgradeItem(Properties properties) {
        super(properties);
    }

    /**
     * Called when the player right-clicks a block with this item.
     *
     * Logic:
     * 1. Check if target is a Compact Machine BlockEntity
     * 2. Get or create VirtualMachineDataImpl attachment
     * 3. Check if already upgraded
     * 4. Install upgrade and consume item
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

        // Get or create VirtualMachineDataImpl attachment
        VirtualMachineDataWrapper wrapper = be.getData(FPSDataAttachments.VIRTUAL_MACHINE_DATA);

        // Reconstruct from wrapper (getData() never returns null due to supplier default)
        VirtualMachineDataImpl data = new VirtualMachineDataImpl(cmBE);
        if (wrapper.hasTpsUpgrade()) {
            // Apply existing data if upgrade was previously installed
            wrapper.applyTo(data);
            FPSCompress.LOGGER.info("Loaded existing VirtualMachineDataImpl for CM at {}", context.getClickedPos());
        } else {
            FPSCompress.LOGGER.info("Creating new VirtualMachineDataImpl for CM at {}", context.getClickedPos());
        }

        // Check if already upgraded
        if (data.hasTpsUpgrade()) {
            player.displayClientMessage(
                Component.literal("§eTPS upgrade already installed!"),
                true
            );
            return InteractionResult.FAIL;
        }

        // Install the upgrade
        data.setTpsUpgrade(true);

        // Save back to attachment
        VirtualMachineDataWrapper newWrapper = VirtualMachineDataWrapper.fromData(data);
        be.setData(FPSDataAttachments.VIRTUAL_MACHINE_DATA, newWrapper);

        // Mark block entity as changed for persistence
        be.setChanged();

        // Consume item (unless creative mode)
        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
        }

        // Success message
        player.displayClientMessage(
            Component.literal("§aTPS Cache Upgrade installed!"),
            true
        );

        FPSCompress.LOGGER.info("TPS Cache Upgrade installed on CM at {} by player {}",
            context.getClickedPos(), player.getName().getString());

        return InteractionResult.SUCCESS;
    }
}
