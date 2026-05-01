package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.FPSCompress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * BlockEntity for PreFab blocks - upgraded Compact Machines that store factory state.
 *
 * Stores:
 * - Room linkage (roomCode, roomCenter coordinates)
 * - Machine state (BUILDING/SIMULATING/CACHED/HALTED)
 * - Cached production rates (for CACHED mode fractional math)
 *
 * TODO Phase 1: Add Map<Direction, FaceConfig> faceConfigs field
 */
public class PrefabBlockEntity extends BlockEntity {

    // Room linkage
    @Nullable
    private String roomCode;

    @Nullable
    private BlockPos roomCenter;

    // Machine state
    private MachineState currentState = MachineState.BUILDING;

    // Cached production rates: resource ID → rate per tick (positive = output, negative = input)
    private final Map<String, Double> cachedRates = new HashMap<>();

    public PrefabBlockEntity(BlockPos pos, BlockState state) {
        super(FPSCompress.PREFAB_BE.get(), pos, state);
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
}
