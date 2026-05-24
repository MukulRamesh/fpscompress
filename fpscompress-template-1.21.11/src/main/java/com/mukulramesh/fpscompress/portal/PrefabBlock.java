package com.mukulramesh.fpscompress.portal;

import com.mojang.logging.LogUtils;
import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
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
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public PrefabBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(1.5f, 6.0f) // Fast to break with pickaxe (iron block hardness)
            .sound(SoundType.METAL)
            .explosionResistance(1200.0f) // Immune to explosions (same as bedrock)
            .requiresCorrectToolForDrops() // Pickaxe is faster tool
            // Always drops itself with NBT via getDrops() override
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        // Default state (FACING=NORTH) is automatically registered by Block constructor
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        // Transfer NBT from item to block entity (including fake registries)
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PrefabBlockEntity prefab) {
            CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (customData != null) {
                CompoundTag nbt = customData.copyTag();
                // Load all data including fake registries
                prefab.loadAdditional(nbt, level.registryAccess());
                prefab.setChanged();
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrefabBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        // Only tick on server side
        if (level.isClientSide()) {
            return null;
        }

        // Return ticker for PrefabBlockEntity
        return type == FPSCompress.PREFAB_BE.get()
            ? (level1, pos, state1, blockEntity) -> {
                if (blockEntity instanceof PrefabBlockEntity prefab) {
                    PrefabBlockEntity.tick(level1, pos, state1, prefab);
                }
            }
            : null;
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

        // Phase 4: Open status/control GUI when right-clicked with empty hand
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof PrefabBlockEntity)) {
            return InteractionResult.FAIL;
        }

        // Open status/control GUI (create anonymous MenuProvider for PreFabStatusMenu)
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new net.minecraft.world.MenuProvider() {
                @Override
                public net.minecraft.network.chat.Component getDisplayName() {
                    return net.minecraft.network.chat.Component.literal("PreFab Status & Control");
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                        int containerId,
                        net.minecraft.world.entity.player.Inventory playerInventory,
                        net.minecraft.world.entity.player.Player playerEntity) {
                    return new com.mukulramesh.fpscompress.gui.PreFabStatusMenu(
                        containerId, playerInventory, pos
                    );
                }
            }, buf -> {
                buf.writeBlockPos(pos);
            });
        }

        return InteractionResult.SUCCESS;
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

        // Prevent non-privileged players from entering PreFabs during active operation
        // Only allow entry during BUILDING state (player is setting up the factory)
        // Creative players and operators (permission level >= 2) can always enter for debugging
        MachineState currentState = prefab.getCurrentState();
        boolean isCreative = serverPlayer.isCreative();

        LOGGER.info("PreFab entry check: state={}, creative={}, player={}",
            currentState, isCreative, serverPlayer.getName().getString());

        // Block non-BUILDING entry for survival players
        // TODO: Add config option to allow specific permission levels to bypass (e.g., level 4 for server admins)
        // For now, only creative mode can bypass the restriction
        if (currentState != MachineState.BUILDING && !isCreative) {
            LOGGER.warn("Blocking entry for player {} - state is {} (not BUILDING)",
                serverPlayer.getName().getString(), currentState);
            serverPlayer.displayClientMessage(
                Component.translatable("fpscompress.prefab.entry_blocked")
                    .withStyle(ChatFormatting.RED),
                false
            );
            return net.minecraft.world.ItemInteractionResult.FAIL;
        }

        LOGGER.info("Allowing entry for player {} - state={}, creative={}",
            serverPlayer.getName().getString(), currentState, isCreative);

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
