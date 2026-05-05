package com.mukulramesh.fpscompress.portal;

import com.mojang.logging.LogUtils;
import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Exporter block - CM dimension output gate for PreFab resource transport.
 *
 * Features:
 * - Acts as output gate: supplies resources to PreFab PUSH faces
 * - Actively pulls from adjacent machines every tick
 * - Has unique UUID for PreFab face linking
 * - 9-slot internal buffer for extracted resources
 * - Can only be placed in CM dimension (enforced by player)
 */
public class ExporterBlock extends Block implements EntityBlock {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public ExporterBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(3.0f, 4.0f)
            .sound(SoundType.METAL)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExporterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level,
            BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide() ? null : (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof ExporterBlockEntity exporter) {
                ExporterBlockEntity.tick(level1, pos, state1, exporter);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              BlockHitResult hitResult) {
        // Debug output: show UUID, filter, buffer contents, and adjacent capabilities
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ExporterBlockEntity exporter) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                        String.format("§6=== %s ===", exporter.getDisplayName())
                    ),
                    false
                );
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                        String.format("§7UUID: §3%s", exporter.getExporterUUID().toString().substring(0, 8))
                    ),
                    false
                );

                // Show filter
                ItemStack filter = exporter.getFilterItem();
                if (!filter.isEmpty()) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            String.format("§7Filter: §e%s", filter.getHoverName().getString())
                        ),
                        false
                    );
                } else {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§7Filter: §cNone (right-click with item to set)"
                        ),
                        false
                    );
                }

                // Show buffer contents
                int itemCount = exporter.getBufferItemCount();
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                        String.format("§7Buffer: §a%d items stored", itemCount)
                    ),
                    false
                );

                // Show adjacent capabilities detected
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Adjacent Capabilities:"),
                    false
                );

                boolean foundAny = false;
                for (Direction dir : Direction.values()) {
                    BlockPos adjacentPos = pos.relative(dir);
                    IItemHandler handler = level.getCapability(
                        Capabilities.ItemHandler.BLOCK,
                        adjacentPos,
                        dir.getOpposite()
                    );

                    if (handler != null) {
                        BlockEntity adjacentBe = level.getBlockEntity(adjacentPos);
                        String blockName = adjacentBe != null
                            ? adjacentBe.getBlockState().getBlock().getName().getString()
                            : "Unknown";
                        player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                String.format("  §a%s: §7%s (Items: ✓)", dir.name(), blockName)
                            ),
                            false
                        );
                        foundAny = true;
                    }
                }

                if (!foundAny) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("  §8None found"),
                        false
                    );
                }

                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hitResult) {
        // Right-click with item to set filter
        if (!level.isClientSide() && !stack.isEmpty()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ExporterBlockEntity exporter) {
                exporter.setFilterItem(stack);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                        String.format("§aExporter filter set to: %s", stack.getHoverName().getString())
                    ),
                    true
                );
                return net.minecraft.world.ItemInteractionResult.SUCCESS;
            }
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public ItemStack getCloneItemStack(Level level, BlockPos pos, BlockState state) {
        // Don't preserve UUID for creative picks - each placement should generate new UUID
        // Only getDrops() preserves UUID (for survival block breaking)
        return new ItemStack(FPSCompress.EXPORTER_ITEM.get());
    }

    @Override
    public java.util.List<ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        // Always drop Exporter with UUID preserved
        BlockEntity be = builder.getOptionalParameter(
            net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        ItemStack stack = new ItemStack(FPSCompress.EXPORTER_ITEM.get());

        if (be instanceof ExporterBlockEntity exporter) {
            CompoundTag nbt = exporter.saveWithoutMetadata(builder.getLevel().registryAccess());
            nbt.putString("id", "fpscompress:exporter");
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(nbt));
        }

        return java.util.List.of(stack);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                           BlockState newState, boolean movedByPiston) {
        // Unregister from global registry when block is actually removed
        // (onRemove is ONLY called when block breaks, not on chunk unload)
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ExporterBlockEntity exporter) {
                ImporterExporterRegistry.unregisterExporter(exporter.getExporterUUID());
                LOGGER.info("[ExporterBlock] Unregistered {} from registry (block broken)",
                    exporter.getExporterUUID().toString().substring(0, 8));
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
