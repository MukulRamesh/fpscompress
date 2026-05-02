package com.mukulramesh.fpscompress.portal;

import com.mojang.logging.LogUtils;
import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * PreFab block - an upgraded Compact Machine that stores factory state in its BlockEntity.
 *
 * Features:
 * - Stores virtual buffers, cached rates, and room linkage
 * - Supports player teleportation (mimics CM behavior via reflection)
 * - Preserves all data when broken (via getCloneItemStack)
 */
public class PrefabBlock extends Block implements EntityBlock {
    private static final Logger LOGGER = LogUtils.getLogger();

    public PrefabBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(5.0f, 6.0f)
            .sound(SoundType.METAL)
            .explosionResistance(1200.0f) // Immune to explosions (same as bedrock)
            // Breakable with any tool, always drops itself with NBT
        );
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrefabBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              BlockHitResult hitResult) {
        // Check if player is holding a Simulation Wrench - let the item handle it
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() == FPSCompress.SIMULATION_WRENCH.get()) {
            return InteractionResult.PASS;
        }

        // Show storage contents when right-clicking without wrench or PSD
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PrefabBlockEntity prefab) {
                // Phase 1: Debug adjacent blocks
                prefab.debugAdjacentBlocks(player);

                // Show storage stats in chat
                displayStorageStats(player, prefab);
                return InteractionResult.SUCCESS;
            }

            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    "§eUse Personal Shrinking Device to enter this PreFab"
                ),
                false
            );
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Display PreFab status to the player.
     * TODO Phase 1+: Add face configuration display, cached rates display
     */
    private void displayStorageStats(Player player, PrefabBlockEntity prefab) {
        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal("§6=== PreFab Status ==="),
            false
        );

        // Show machine state
        MachineState state = prefab.getCurrentState();
        String stateColor = switch (state) {
            case BUILDING -> "§e";
            case SIMULATING -> "§b";
            case CACHED -> "§a";
            case HALTED -> "§c";
        };
        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(
                String.format("§7State: %s%s", stateColor, state.name())
            ),
            false
        );

        // Show room info
        String roomCode = prefab.getRoomCode();
        if (roomCode != null) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    String.format("§7Room: §3%s", roomCode)
                ),
                false
            );
        } else {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§7Room: §cNot linked (upgrade from CM first)"),
                false
            );
        }

        // Show cached rates count (if any)
        int rateCount = prefab.getCachedRates().size();
        if (rateCount > 0) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    String.format("§7Cached Rates: §a%d resource types", rateCount)
                ),
                false
            );
        }

        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal("§8(Hold PSD and right-click to enter)"),
            false
        );
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
                                         net.minecraft.world.item.ItemStack stack,
                                         BlockState state, Level level, BlockPos pos,
                                         Player player, net.minecraft.world.InteractionHand hand,
                                         BlockHitResult hitResult) {
        // Check if player is using Personal Shrinking Device
        if (!isPersonalShrinkingDevice(stack)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Handle teleportation
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PrefabBlockEntity prefab)) {
            return net.minecraft.world.ItemInteractionResult.FAIL;
        }

        String roomCode = prefab.getRoomCode();
        if (roomCode == null) {
            LOGGER.warn("PreFab at {} has no room code", pos);
            return net.minecraft.world.ItemInteractionResult.FAIL;
        }

        // Cache player's current position and rotation for exit
        PlayerPositionCache.cacheEntryPosition(
            serverPlayer.getUUID(),
            serverPlayer.level().dimension(),
            serverPlayer.blockPosition(),
            serverPlayer.getYRot(),
            serverPlayer.getXRot()
        );

        // Try to teleport player using CM's API
        boolean teleported = teleportPlayerToCMRoom(serverPlayer, roomCode, pos);
        if (teleported) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }

        LOGGER.error("Failed to teleport player to room {}", roomCode);
        return net.minecraft.world.ItemInteractionResult.FAIL;
    }

    /**
     * Check if the item is a Personal Shrinking Device from Compact Machines.
     */
    private boolean isPersonalShrinkingDevice(net.minecraft.world.item.ItemStack stack) {
        // Check for CM's Personal Shrinking Device
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(stack.getItem()).toString();
        return itemId.equals("compactmachines:personal_shrinking_device");
    }

    /**
     * Teleport player to CM room using cached coordinates.
     */
    private boolean teleportPlayerToCMRoom(ServerPlayer player, String roomCode, BlockPos prefabPos) {
        try {
            // Get CM dimension
            ServerLevel cmDimension = player.getServer().getLevel(
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse("compactmachines:compact_world")
                )
            );

            if (cmDimension == null) {
                LOGGER.error("CM dimension not found");
                return false;
            }

            // Parse room coordinates from room code
            // Room code format: "compactmachines:room_X" or similar
            // For now, use cached coordinates from RoomCoordinateCache
            RoomCoordinateCache cache = RoomCoordinateCache.get(player.getServer());
            BlockPos roomCenter = cache.getRoomCenterByRoomCode(roomCode);

            if (roomCenter == null) {
                LOGGER.error("Room coordinates not cached for {}", roomCode);
                return false;
            }

            // Teleport player to room center
            player.teleportTo(cmDimension, roomCenter.getX() + 0.5,
                             roomCenter.getY(), roomCenter.getZ() + 0.5,
                             player.getYRot(), player.getXRot());

            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to teleport player via reflection", e);
            return false;
        }
    }

    public ItemStack getCloneItemStack(Level level, BlockPos pos, BlockState state) {
        // Preserve all BlockEntity data in item NBT
        ItemStack stack = new ItemStack(FPSCompress.PREFAB_ITEM.get());
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof PrefabBlockEntity prefab) {
            // Save BlockEntity NBT to item with proper ID
            CompoundTag nbt = prefab.saveWithoutMetadata(level.registryAccess());
            nbt.putString("id", "fpscompress:prefab");
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(nbt));
        }

        return stack;
    }

    @Override
    public java.util.List<ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        // Always drop PreFab with NBT, regardless of tool or silk touch
        BlockEntity be = builder.getOptionalParameter(
            net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        ItemStack stack = new ItemStack(FPSCompress.PREFAB_ITEM.get());

        if (be instanceof PrefabBlockEntity prefab) {
            // Save BlockEntity NBT to item with proper ID
            CompoundTag nbt = prefab.saveWithoutMetadata(builder.getLevel().registryAccess());
            nbt.putString("id", "fpscompress:prefab");
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(nbt));
        }

        return java.util.List.of(stack);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                           BlockState newState, boolean movedByPiston) {
        // Clear coordinate cache when block is broken
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            RoomCoordinateCache cache = RoomCoordinateCache.get(
                level.getServer()
            );
            cache.clearCache(pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
