package com.mukulramesh.fpscompress.blueprint;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

/**
 * Fabricator block - Scans PreFabs to create Blueprints, prints PreFabs from Blueprints.
 *
 * Phase 2: Structure only - inventory management and block entity
 * Phase 3+: Scanning and printing logic (future)
 *
 * Features:
 * - 29-slot inventory (1 input + 1 output + 27 resources)
 * - Input slot accepts PreFab items (for scanning) or Blueprint items (for printing)
 * - Output slot produces Blueprint items (scanning) or PreFab items (printing)
 * - Resource slots hold materials required for printing
 * - Inventory persists when block is broken
 */
public class FabricatorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public FabricatorBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(1.5f, 6.0f) // Same as PreFab - fast to break with pickaxe
            .sound(SoundType.METAL)
            .explosionResistance(1200.0f) // Immune to explosions (same as PreFab/bedrock)
            .requiresCorrectToolForDrops() // Pickaxe is faster tool
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face opposite to player's look direction (like crafting table)
        return this.defaultBlockState().setValue(FACING,
            context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FabricatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        // Only tick on server side
        if (level.isClientSide()) {
            return null;
        }

        // Return ticker for FabricatorBlockEntity
        return type == FPSCompress.FABRICATOR_BE.get()
            ? (level1, pos, state1, blockEntity) -> {
                if (blockEntity instanceof FabricatorBlockEntity fabricator) {
                    FabricatorBlockEntity.tick(level1, pos, state1, fabricator);
                }
            }
            : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              BlockHitResult hitResult) {
        // Phase 5: Open custom Fabricator GUI with Scan/Print buttons
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof FabricatorBlockEntity fabricator) {
                serverPlayer.openMenu(fabricator, buf -> {
                    buf.writeBlockPos(pos);
                });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public ItemStack getCloneItemStack(Level level, BlockPos pos, BlockState state) {
        // Creative mode picks: don't preserve inventory (start fresh)
        return new ItemStack(FPSCompress.FABRICATOR_ITEM.get());
    }

    @Override
    public java.util.List<ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        // Drop a clean Fabricator item (inventory contents drop separately in onRemove)
        return java.util.List.of(new ItemStack(FPSCompress.FABRICATOR_ITEM.get()));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                           BlockState newState, boolean movedByPiston) {
        // Drop all inventory contents as item entities when block is broken
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FabricatorBlockEntity fabricator) {
                var inventory = fabricator.getInventory();
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    net.minecraft.world.item.ItemStack stack = inventory.getStackInSlot(slot);
                    if (!stack.isEmpty()) {
                        double x = pos.getX() + 0.5;
                        double y = pos.getY() + 0.5;
                        double z = pos.getZ() + 0.5;
                        net.minecraft.world.entity.item.ItemEntity entity =
                            new net.minecraft.world.entity.item.ItemEntity(
                                level, x, y, z, stack.copy());
                        level.addFreshEntity(entity);
                    }
                }
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
