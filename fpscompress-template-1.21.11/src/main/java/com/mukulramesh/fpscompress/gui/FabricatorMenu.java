package com.mukulramesh.fpscompress.gui;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.blueprint.FabricatorBlockEntity;
import com.mukulramesh.fpscompress.component.FPSDataComponents;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Server-side container menu for the Fabricator block.
 *
 * <p>Slot layout (29 fabricator + 36 player = 65 total):
 * <ul>
 *   <li>Slot 0: Input (accepts PreFab or Blueprint items)</li>
 *   <li>Slot 1: Output (output-only, produces Blueprint or PreFab items)</li>
 *   <li>Slots 2-28: Resource slots (27 slots for printing materials)</li>
 *   <li>Slots 29-55: Player main inventory (3 rows of 9)</li>
 *   <li>Slots 56-64: Player hotbar (1 row of 9)</li>
 * </ul>
 *
 * <p>ContainerData indices (synced server→client):
 * <ul>
 *   <li>0: scanState — 0=idle, 1=ready_to_scan, 2=scanning, 3=ready_to_print</li>
 *   <li>1: requiredResourceCount — total resources needed by blueprint</li>
 *   <li>2: availableResourceCount — how many are satisfied</li>
 *   <li>3: prefabValidForScan — 0=invalid/none, 1=valid PreFab ready</li>
 *   <li>4: satisfiedSlotMask — bitmask of satisfied resource slots</li>
 * </ul>
 */
public class FabricatorMenu extends AbstractContainerMenu {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final int RESOURCE_START = 2;
    private static final int RESOURCE_COUNT = 27;
    private static final int RESOURCE_END = RESOURCE_START + RESOURCE_COUNT;
    private static final int PLAYER_INV_START = RESOURCE_END;
    private static final int PLAYER_INV_COUNT = 27;
    private static final int PLAYER_HOTBAR_START = PLAYER_INV_START + PLAYER_INV_COUNT;
    private static final int PLAYER_HOTBAR_COUNT = 9;
    private static final int TOTAL_SLOTS = PLAYER_HOTBAR_START + PLAYER_HOTBAR_COUNT;

    private final BlockPos fabricatorPos;
    private final SimpleContainerData containerData;
    private final FabricatorBlockEntity fabricator; // null on client
    private final IItemHandler itemHandler;

    /**
     * Server-side constructor.
     *
     * @param containerId Container ID
     * @param playerInventory Player inventory
     * @param pos Fabricator block position
     */
    @SuppressWarnings("this-escape")
    @SuppressFBWarnings("MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR")
    public FabricatorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(FPSCompress.FABRICATOR_MENU.get(), containerId);
        this.fabricatorPos = pos;
        this.containerData = new SimpleContainerData(5);

        // Get the Fabricator's ItemStackHandler (server) or use a dummy (client)
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        IItemHandler handler;
        if (be instanceof FabricatorBlockEntity fab) {
            this.fabricator = fab;
            handler = fab.getInventory();
        } else {
            this.fabricator = null;
            handler = new ItemStackHandler(29);
        }
        this.itemHandler = handler;

        // Add fabricator slots
        // Slot 0: Input slot — accepts PreFab or Blueprint items
        addSlot(new SlotItemHandler(itemHandler, INPUT_SLOT, 44, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(FPSCompress.PREFAB_ITEM.get())
                    || stack.is(FPSCompress.PREFAB_BLUEPRINT.get());
            }
        });

        // Slot 1: Output slot — output-only (no manual insertion)
        addSlot(new SlotItemHandler(itemHandler, OUTPUT_SLOT, 116, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Slots 2-28: Resource slots (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = RESOURCE_START + col + row * 9;
                int x = 8 + col * 18;
                int y = 70 + row * 18;
                addSlot(new SlotItemHandler(itemHandler, slotIndex, x, y) {
                    @Override
                    public int getMaxStackSize(ItemStack stack) {
                        // Allow unlimited stacking in resource slots
                        return itemHandler.getSlotLimit(slotIndex);
                    }
                });
            }
        }

        // Add player main inventory (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = col + row * 9 + 9;
                int x = 8 + col * 18;
                int y = 138 + row * 18;
                addSlot(new Slot(playerInventory, slotIndex, x, y));
            }
        }

        // Add player hotbar (1 row of 9)
        for (int col = 0; col < 9; col++) {
            int x = 8 + col * 18;
            int y = 196;
            addSlot(new Slot(playerInventory, col, x, y));
        }

        // Register ContainerData for client sync
        addDataSlots(this.containerData);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        // Server side: copy live data from block entity into local containerData
        // so the DataSlots detect changes and sync to the client
        if (this.fabricator != null) {
            net.minecraft.world.inventory.ContainerData fd =
                this.fabricator.getFabricatorData();
            for (int i = 0; i < fd.getCount(); i++) {
                this.containerData.set(i, fd.get(i));
            }
        }
    }

    /**
     * Client-side constructor (reads BlockPos from packet buffer).
     *
     * @param containerId Container ID
     * @param playerInventory Player inventory
     * @param buf Packet buffer containing BlockPos
     */
    public FabricatorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    /**
     * Get the Fabricator block position.
     *
     * @return Fabricator BlockPos
     */
    public BlockPos getFabricatorPos() {
        return fabricatorPos;
    }

    /**
     * Get the container data for syncing scan state and resource counts.
     *
     * @return ContainerData with scanState, requiredResourceCount, availableResourceCount
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public ContainerData getContainerData() {
        return containerData;
    }

    /**
     * Get the current scan state from container data.
     *
     * @return 0=idle, 1=ready_to_scan, 2=scanning, 3=ready_to_print
     */
    public int getScanState() {
        return containerData.get(0);
    }

    /**
     * Get the required resource count from container data.
     *
     * @return Total number of unique resources needed
     */
    public int getRequiredResourceCount() {
        return containerData.get(1);
    }

    /**
     * Get the available resource count from container data.
     *
     * @return Number of resources satisfied
     */
    public int getAvailableResourceCount() {
        return containerData.get(2);
    }

    /**
     * Check if the PreFab in the input slot has passed server-side validation.
     *
     * @return true if PreFab is valid for scanning (CACHED/HALTED state, has NBT, etc.)
     */
    public boolean isPrefabValid() {
        return containerData.get(3) == 1;
    }

    /**
     * Check if a specific resource requirement index is satisfied server-side
     * (passed both count and NBT validation).
     *
     * @param reqIndex 0-based index into the blueprint's requirement list
     * @return true if this requirement is satisfied
     */
    public boolean isSlotSatisfied(int reqIndex) {
        return (containerData.get(4) & (1 << reqIndex)) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        ItemStack copy = stackInSlot.copy();

        if (index == OUTPUT_SLOT) {
            // Shift-click from output slot → player inventory
            if (!this.moveItemStackTo(stackInSlot, PLAYER_INV_START, TOTAL_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < RESOURCE_END) {
            // From fabricator slots (input or resources) → player inventory
            if (!this.moveItemStackTo(stackInSlot, PLAYER_INV_START, TOTAL_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From player inventory → fabricator slots
            // Try input slot first if PreFab or Blueprint
            if (copy.is(FPSCompress.PREFAB_ITEM.get())
                    || copy.is(FPSCompress.PREFAB_BLUEPRINT.get())) {
                if (this.moveItemStackTo(stackInSlot, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                    return copy;
                }
            }

            // Try resource slots
            if (!this.moveItemStackTo(stackInSlot, RESOURCE_START, RESOURCE_END, false)) {
                // If resource slots are full, transfer within player inventory
                if (index >= PLAYER_HOTBAR_START) {
                    if (!this.moveItemStackTo(stackInSlot, PLAYER_INV_START,
                            PLAYER_HOTBAR_START, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    if (!this.moveItemStackTo(stackInSlot, PLAYER_HOTBAR_START,
                            TOTAL_SLOTS, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }

        if (stackInSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
            fabricatorPos.getX() + 0.5,
            fabricatorPos.getY() + 0.5,
            fabricatorPos.getZ() + 0.5
        ) <= 64.0;
    }

    /**
     * Check if the input slot contains a PreFab item.
     *
     * @return true if PreFab in input slot
     */
    public boolean hasPreFabInInput() {
        Slot slot = this.slots.get(INPUT_SLOT);
        return slot.hasItem() && slot.getItem().is(FPSCompress.PREFAB_ITEM.get());
    }

    /**
     * Check if the input slot contains a Blueprint item.
     *
     * @return true if Blueprint in input slot
     */
    public boolean hasBlueprintInInput() {
        Slot slot = this.slots.get(INPUT_SLOT);
        if (!slot.hasItem()) {
            return false;
        }
        ItemStack stack = slot.getItem();
        return stack.is(FPSCompress.PREFAB_BLUEPRINT.get())
            && stack.get(FPSDataComponents.BLUEPRINT_DATA.get()) != null;
    }

    /**
     * Check if the output slot is empty.
     *
     * @return true if output slot is empty
     */
    public boolean isOutputSlotEmpty() {
        Slot slot = this.slots.get(OUTPUT_SLOT);
        return !slot.hasItem();
    }
}
