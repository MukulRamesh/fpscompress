package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.gui.PreFabConfigMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * BlockEntity for PreFab blocks - upgraded Compact Machines that store factory state.
 *
 * Stores:
 * - Room linkage (roomCode, roomCenter coordinates)
 * - Machine state (BUILDING/SIMULATING/CACHED/HALTED)
 * - Face configurations (6 independent face settings)
 * - Cached production rates (for CACHED mode fractional math)
 */
public class PrefabBlockEntity extends BlockEntity implements MenuProvider {

    // Room linkage
    @Nullable
    private String roomCode;

    @Nullable
    private BlockPos roomCenter;

    // Machine state
    private MachineState currentState = MachineState.BUILDING;

    // Face configurations (Phase 1)
    private final Map<Direction, FaceConfig> faceConfigs = new EnumMap<>(Direction.class);

    // Cached production rates: resource ID → rate per tick (positive = output, negative = input)
    private final Map<String, Double> cachedRates = new HashMap<>();

    public PrefabBlockEntity(BlockPos pos, BlockState state) {
        super(FPSCompress.PREFAB_BE.get(), pos, state);

        // Initialize all 6 faces to DISABLED
        for (Direction dir : Direction.values()) {
            faceConfigs.put(dir, new FaceConfig());
        }
    }

    // ===== Room Linkage Accessors =====

    @Nullable
    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
        setChanged();
    }

    @Nullable
    public BlockPos getRoomCenter() {
        return roomCenter;
    }

    public void setRoomCenter(BlockPos roomCenter) {
        this.roomCenter = roomCenter;
        setChanged();
    }

    // ===== Machine State =====

    public MachineState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(MachineState state) {
        this.currentState = state;
        setChanged();
    }

    // ===== Face Configuration =====

    /**
     * Get the configuration for a specific face.
     *
     * @param direction The face direction
     * @return Face configuration (never null)
     */
    public FaceConfig getFaceConfig(Direction direction) {
        return faceConfigs.get(direction);
    }

    /**
     * Set the configuration for a specific face.
     *
     * @param direction The face direction
     * @param config New configuration
     */
    public void setFaceConfig(Direction direction, FaceConfig config) {
        faceConfigs.put(direction, config);
        setChanged();
    }

    /**
     * Get all face configurations.
     *
     * @return Map of all face configs
     */
    public Map<Direction, FaceConfig> getAllFaceConfigs() {
        return new EnumMap<>(faceConfigs);
    }

    // ===== Debug Methods (Phase 1 - Adjacent Block Detection) =====

    /**
     * Debug method to display adjacent blocks and their capabilities.
     * Used to validate Phase 1 - proves PreFab can detect adjacent blocks correctly.
     *
     * @param player The player to send chat messages to
     */
    public void debugAdjacentBlocks(Player player) {
        if (level == null) {
            player.displayClientMessage(Component.literal("§cError: Level is null"), false);
            return;
        }

        player.displayClientMessage(Component.literal("§6=== PreFab Adjacent Blocks ==="), false);

        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = this.getBlockPos().relative(dir);
            BlockEntity be = this.level.getBlockEntity(adjacentPos);

            if (be != null) {
                // Try to get capabilities from the adjacent block
                IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, adjacentPos, dir.getOpposite());
                IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, adjacentPos, dir.getOpposite());
                IEnergyStorage energyStorage = level.getCapability(Capabilities.EnergyStorage.BLOCK, adjacentPos, dir.getOpposite());

                String blockName = be.getBlockState().getBlock().getName().getString();

                player.displayClientMessage(Component.literal(
                    String.format("§6%s: §7%s §a[Items:%s Fluids:%s Energy:%s]",
                        dir.name(),
                        blockName,
                        itemHandler != null ? "✓" : "✗",
                        fluidHandler != null ? "✓" : "✗",
                        energyStorage != null ? "✓" : "✗"
                    )
                ), false);
            } else {
                player.displayClientMessage(Component.literal(
                    String.format("§6%s: §8No block entity", dir.name())
                ), false);
            }
        }
    }

    // ===== Cached Rates =====

    public Map<String, Double> getCachedRates() {
        return new HashMap<>(cachedRates);
    }

    public void setCachedRate(String resourceId, double ratePerTick) {
        cachedRates.put(resourceId, ratePerTick);
        setChanged();
    }

    public void clearCachedRates() {
        cachedRates.clear();
        setChanged();
    }

    // ===== NBT Serialization =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // Save room linkage
        if (roomCode != null) {
            tag.putString("roomCode", roomCode);
        }
        if (roomCenter != null) {
            tag.putLong("roomCenter", roomCenter.asLong());
        }

        // Save machine state
        tag.putString("state", currentState.name());

        // Save face configurations
        CompoundTag facesTag = new CompoundTag();
        for (Map.Entry<Direction, FaceConfig> entry : faceConfigs.entrySet()) {
            facesTag.put(entry.getKey().getName(), entry.getValue().toNBT());
        }
        tag.put("faceConfigs", facesTag);

        // Save cached rates
        if (!cachedRates.isEmpty()) {
            ListTag ratesList = new ListTag();
            for (Map.Entry<String, Double> entry : cachedRates.entrySet()) {
                CompoundTag rateEntry = new CompoundTag();
                rateEntry.putString("id", entry.getKey());
                rateEntry.putDouble("rate", entry.getValue());
                ratesList.add(rateEntry);
            }
            tag.put("rates", ratesList);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Load room linkage
        if (tag.contains("roomCode")) {
            roomCode = tag.getString("roomCode");
        }
        if (tag.contains("roomCenter")) {
            roomCenter = BlockPos.of(tag.getLong("roomCenter"));
        }

        // Load machine state
        if (tag.contains("state")) {
            try {
                currentState = MachineState.valueOf(tag.getString("state"));
            } catch (IllegalArgumentException e) {
                currentState = MachineState.BUILDING;
            }
        }

        // Load face configurations
        if (tag.contains("faceConfigs")) {
            CompoundTag facesTag = tag.getCompound("faceConfigs");
            for (Direction dir : Direction.values()) {
                if (facesTag.contains(dir.getName())) {
                    faceConfigs.put(dir, FaceConfig.fromNBT(facesTag.getCompound(dir.getName())));
                }
            }
        }

        // Load cached rates
        if (tag.contains("rates")) {
            cachedRates.clear();
            ListTag ratesList = tag.getList("rates", Tag.TAG_COMPOUND);
            for (int i = 0; i < ratesList.size(); i++) {
                CompoundTag rateEntry = ratesList.getCompound(i);
                String id = rateEntry.getString("id");
                double rate = rateEntry.getDouble("rate");
                cachedRates.put(id, rate);
            }
        }
    }

    // ===== MenuProvider Implementation =====

    @Override
    public Component getDisplayName() {
        return Component.literal("PreFab Configuration");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PreFabConfigMenu(containerId, playerInventory, this.getBlockPos());
    }
}
