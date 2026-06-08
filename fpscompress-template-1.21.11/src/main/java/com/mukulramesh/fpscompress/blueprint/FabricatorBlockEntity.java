package com.mukulramesh.fpscompress.blueprint;

import com.mukulramesh.fpscompress.FPSCompress;
import com.mukulramesh.fpscompress.component.FPSDataComponents;
import com.mukulramesh.fpscompress.portal.MachineState;
import com.mukulramesh.fpscompress.scanner.BlockScanner;
import com.mukulramesh.fpscompress.scanner.InventoryScanner;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * BlockEntity for Fabricator blocks - Scans PreFabs and prints Blueprints.
 *
 * Phase 2: Structure only - inventory management
 * Phase 3+: Scanning and printing logic (future)
 *
 * Inventory layout:
 * - Slot 0: Input slot (accepts PreFab or Blueprint items)
 * - Slot 1: Output slot (produces Blueprint or PreFab items, output-only)
 * - Slots 2-28: Resource slots (27 slots for printing materials)
 */
public class FabricatorBlockEntity extends BlockEntity implements MenuProvider {

    // Slot constants
    static final int INPUT_SLOT = 0;
    static final int OUTPUT_SLOT = 1;
    static final int RESOURCE_START = 2;
    private static final int TOTAL_SLOTS = 29;

    // Inventory: 1 input + 1 output + 27 resources = 29 total slots
    private final FilteredItemStackHandler inventory = new FilteredItemStackHandler(TOTAL_SLOTS);

    // Phase 3: Scan state tracking
    private boolean scanningBlocks = false;
    private Map<String, Long> scannedBlocks = new HashMap<>();
    private Map<String, CompoundTag> scannedBlockNbt = new HashMap<>(); // Phase 5: NBT tracking
    private Map<String, Long> scannedItems = new HashMap<>(); // Phase 4: Item scanning
    private Map<String, CompoundTag> scannedItemNbt = new HashMap<>(); // Phase 5: NBT tracking
    private boolean hasScannedCurrentPrefab = false; // Prevent infinite scan loop

    // Phase 3: Validation state
    private boolean prefabValidForScan = false;
    @Nullable
    @SuppressWarnings("FieldCanBeLocal")
    private String validationError = null;

    // Phase 5: Resource checking for printing
    private boolean hasBlueprintInInput = false;
    private int requiredResourceCount = 0;
    private int availableResourceCount = 0;
    private int satisfiedSlotMask = 0; // bitmask: bit N=1 means slot N+2 satisfied
    private boolean rejectingNbtMismatch = false; // guard against recursive ejection
    @Nullable
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    private Player lastInteractingPlayer = null; // for returning rejected items to player

    // Phase 5: ContainerData for GUI sync
    // Index 0: scanState, 1: requiredResourceCount, 2: availableResourceCount,
    //        3: prefabValidForScan, 4: satisfiedSlotMask
    private final net.minecraft.world.inventory.ContainerData fabricatorData =
        new net.minecraft.world.inventory.ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> getScanState();
                    case 1 -> requiredResourceCount;
                    case 2 -> availableResourceCount;
                    case 3 -> prefabValidForScan ? 1 : 0;
                    case 4 -> satisfiedSlotMask;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Values are computed server-side, ignore client sets
            }

            @Override
            public int getCount() {
                return 5;
            }
        };

    public FabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(FPSCompress.FABRICATOR_BE.get(), pos, state);
    }

    // ===== Inventory Access Methods =====

    /**
     * Get the inventory handler.
     * Public for FabricatorMenu to wrap slots via SlotItemHandler.
     *
     * @return The ItemStackHandler
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public ItemStackHandler getInventory() {
        return inventory;
    }

    /**
     * Get the stack in the input slot (slot 0).
     *
     * @return Input slot contents
     */
    public ItemStack getInputSlot() {
        return inventory.getStackInSlot(0);
    }

    /**
     * Get the stack in the output slot (slot 1).
     *
     * @return Output slot contents
     */
    public ItemStack getOutputSlot() {
        return inventory.getStackInSlot(1);
    }

    /**
     * Set the stack in the output slot (slot 1).
     *
     * @param stack New output stack
     */
    public void setOutputSlot(ItemStack stack) {
        inventory.setStackInSlot(1, stack);
        setChanged();
    }

    /**
     * Insert items into resource slots (slots 2-28).
     * Used for adding printing materials.
     *
     * @param stack Items to insert
     * @return Remainder stack (items that couldn't fit)
     */
    public ItemStack insertItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();

        // Insert into resource slots (2-28)
        for (int slot = 2; slot < inventory.getSlots(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
            if (remaining.isEmpty()) {
                break;
            }
        }

        setChanged();
        return remaining;
    }

    /**
     * Extract items from resource slots (slots 2-28).
     * Used for consuming materials during printing.
     *
     * @param maxAmount Maximum items to extract
     * @return Extracted items
     */
    public ItemStack extractItem(int maxAmount) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack result = ItemStack.EMPTY;
        int remaining = maxAmount;

        // Extract from resource slots (2-28)
        for (int slot = 2; slot < inventory.getSlots() && remaining > 0; slot++) {
            ItemStack stackInSlot = inventory.getStackInSlot(slot);
            if (stackInSlot.isEmpty()) {
                continue;
            }

            if (!result.isEmpty() && !ItemStack.isSameItemSameComponents(result, stackInSlot)) {
                continue;
            }

            int toExtract = Math.min(remaining, stackInSlot.getCount());
            ItemStack extracted = inventory.extractItem(slot, toExtract, false);

            if (!extracted.isEmpty()) {
                if (result.isEmpty()) {
                    result = extracted;
                } else {
                    result.grow(extracted.getCount());
                }
                remaining -= extracted.getCount();
            }
        }

        if (!result.isEmpty()) {
            setChanged();
        }

        return result;
    }

    // ===== Phase 3: PreFab Validation =====

    /**
     * Validate if the PreFab in the input slot is scannable.
     *
     * <p>Sets validationError field with reason if not valid.
     * <p>Phase 3: Logs validation errors to console for testing.
     *
     * @param prefabStack PreFab ItemStack to validate
     * @return true if scannable, false otherwise
     */
    private boolean validatePrefabForScanning(ItemStack prefabStack) {
        if (prefabStack.isEmpty()) {
            validationError = "No PreFab in input slot";
            return false;
        }

        if (prefabStack.getItem() != FPSCompress.PREFAB_ITEM.get()) {
            validationError = "Not a PreFab item";
            return false;
        }

        CustomData customData = prefabStack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (customData == null) {
            validationError = "PreFab has no data";
            return false;
        }

        CompoundTag nbt = customData.copyTag();

        if (!nbt.contains("roomCode")) {
            validationError = "PreFab missing roomCode";
            return false;
        }

        String roomCode = nbt.getString("roomCode");
        if (roomCode.isEmpty()) {
            validationError = "PreFab has empty roomCode";
            return false;
        }

        if (roomCode.startsWith("fake_")) {
            validationError = "Cannot scan test PreFabs (fake room)";
            return false;
        }

        if (roomCode.startsWith("cc_")) {
            validationError = "Cannot scan carbon copy PreFabs";
            return false;
        }

        if (!nbt.contains("state")) {
            validationError = "PreFab missing state";
            return false;
        }

        String stateStr = nbt.getString("state");
        MachineState state;
        try {
            state = MachineState.valueOf(stateStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            validationError = "PreFab has invalid state: " + stateStr;
            return false;
        }

        if (state != MachineState.CACHED && state != MachineState.HALTED) {
            validationError = "PreFab must be CACHED or HALTED (current: " + state + ")";
            return false;
        }

        validationError = null;
        return true;
    }

    /**
     * Check if block scanning can start.
     *
     * @return true if all conditions met
     */
    private boolean canStartBlockScan() {
        // Not already scanning
        if (scanningBlocks) {
            return false;
        }

        // Haven't already scanned this PreFab
        if (hasScannedCurrentPrefab) {
            return false;
        }

        // Input slot has valid PreFab
        if (!prefabValidForScan) {
            return false;
        }

        // Output slot is empty (room for blueprint)
        if (!getOutputSlot().isEmpty()) {
            return false;
        }

        // Server level available
        if (level == null || level.isClientSide()) {
            return false;
        }

        return true;
    }

    // ===== Phase 3: Scan Execution =====

    /**
     * Start block scanning operation for PreFab in input slot.
     *
     * <p>Phase 3: Stores results in scannedBlocks field.
     * <p>Phase 4: Will create blueprint from scanned data.
     */
    private void startBlockScan() {
        if (level == null || level.isClientSide()) {
            return;
        }

        ItemStack prefabStack = getInputSlot();
        if (prefabStack.isEmpty()) {
            return;
        }

        CustomData customData = prefabStack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (customData == null) {
            FPSCompress.LOGGER.error("PreFab has no NBT data for scanning");
            return;
        }

        CompoundTag nbt = customData.copyTag();

        // Extract room information
        String roomCode = nbt.getString("roomCode");

        // Access CM dimension
        ServerLevel cmLevel = getCMLevel();
        if (cmLevel == null) {
            FPSCompress.LOGGER.error("Cannot access CM dimension for scanning");
            return;
        }

        // Get room center from cache
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) {
            FPSCompress.LOGGER.error("Server not available for scanning");
            return;
        }

        com.mukulramesh.fpscompress.portal.RoomCoordinateCache cache =
                com.mukulramesh.fpscompress.portal.RoomCoordinateCache.get(server);
        BlockPos roomCenter = cache.getRoomCenterByRoomCode(roomCode);
        if (roomCenter == null) {
            FPSCompress.LOGGER.error("Room center not found for roomCode: {}", roomCode);
            return;
        }

        // Get room dimensions from PreFab NBT (avoids expensive wall scanning)
        int roomSizeX = nbt.contains("roomSizeX") ? nbt.getInt("roomSizeX") : 0;
        int roomSizeY = nbt.contains("roomSizeY") ? nbt.getInt("roomSizeY") : 0;
        int roomSizeZ = nbt.contains("roomSizeZ") ? nbt.getInt("roomSizeZ") : 0;

        // Fallback: Scan walls if dimensions not in NBT (shouldn't happen for valid PreFabs)
        AABB roomBounds;
        if (roomSizeX > 0 && roomSizeY > 0 && roomSizeZ > 0) {
            // Calculate AABB from room center and dimensions
            int halfX = roomSizeX / 2;
            int halfY = roomSizeY / 2;
            int halfZ = roomSizeZ / 2;
            roomBounds = new AABB(
                roomCenter.getX() - halfX,
                roomCenter.getY() - halfY,
                roomCenter.getZ() - halfZ,
                roomCenter.getX() + halfX,
                roomCenter.getY() + halfY,
                roomCenter.getZ() + halfZ
            );
            FPSCompress.LOGGER.debug("Using room dimensions from PreFab NBT: {}x{}x{}",
                roomSizeX, roomSizeY, roomSizeZ);
        } else {
            // Fallback: Scan walls (expensive, only for old PreFabs without dimensions)
            FPSCompress.LOGGER.warn("Room dimensions not in PreFab NBT, falling back to wall scanning");
            try {
                roomBounds = getRoomBoundsFromCM(cmLevel, roomCenter);
            } catch (Exception e) {
                FPSCompress.LOGGER.error("Failed to determine room bounds for {}: {}",
                    roomCode, e.getMessage());
                return;
            }
        }

        // Set scanning state
        scanningBlocks = true;
        scannedBlocks.clear();
        scannedBlockNbt.clear();  // Phase 5: Clear NBT templates
        scannedItems.clear();
        scannedItemNbt.clear();   // Phase 5: Clear NBT templates
        hasScannedCurrentPrefab = true; // Mark as scanned to prevent re-scan
        setChanged();

        FPSCompress.LOGGER.info("Starting block and item scan for room: {}", roomCode);

        // Phase 4: Execute both scans in parallel
        CompletableFuture<BlockScanner.ScanResult> blockFuture =
                BlockScanner.scanBlocksAsync(cmLevel, roomCenter, roomBounds);
        CompletableFuture<BlockScanner.ScanResult> itemFuture =
                InventoryScanner.scanRoomAsyncWithNbt(cmLevel, roomCenter, roomBounds);

        CompletableFuture.allOf(blockFuture, itemFuture)
                .thenAccept(v -> {
                    level.getServer().execute(() -> {
                        // Main thread callback - both scans complete
                        BlockScanner.ScanResult blockResult = blockFuture.join();
                        scannedBlocks = blockResult.counts();
                        scannedBlockNbt = blockResult.nbtTemplates();
                        BlockScanner.ScanResult itemResult = itemFuture.join();
                        scannedItems = itemResult.counts();
                        scannedItemNbt = itemResult.nbtTemplates();
                        scanningBlocks = false;
                        setChanged();

                        int nbtTracked = (int) scannedBlockNbt.keySet().stream()
                            .filter(k -> k.contains("#")).count();
                        FPSCompress.LOGGER.info("Scan complete: {} block types ({} with NBT), {} item types",
                            scannedBlocks.size(), nbtTracked, scannedItems.size());

                        // Phase 4: Create blueprint from scan results
                        createBlueprintFromScan();
                    });
                })
                .exceptionally(ex -> {
                    level.getServer().execute(() -> {
                        FPSCompress.LOGGER.error("Scan failed for room {}", roomCode, ex);
                        scanningBlocks = false;
                        scannedBlocks.clear();
                        scannedBlockNbt.clear();
                        scannedItems.clear();
                        scannedItemNbt.clear();
                        setChanged();
                    });
                    return null;
                });
    }

    /**
     * Create Blueprint item from scan results.
     * Called after both block and item scans complete.
     *
     * <p>Phase 4: Extracts cached rates, room dimensions, and custom name from PreFab NBT,
     * then creates Blueprint item with all data.
     */
    private void createBlueprintFromScan() {
        // Validate preconditions
        if (scannedBlocks.isEmpty() && scannedItems.isEmpty()) {
            FPSCompress.LOGGER.error("Cannot create blueprint: No resources scanned");
            return;
        }

        if (!getOutputSlot().isEmpty()) {
            FPSCompress.LOGGER.error("Cannot create blueprint: Output slot occupied");
            // Leave PreFab in input slot for retry
            return;
        }

        ItemStack prefabStack = getInputSlot();
        if (prefabStack.isEmpty()) {
            FPSCompress.LOGGER.error("Cannot create blueprint: PreFab removed during scan");
            return;
        }

        // Extract PreFab NBT data
        CustomData customData = prefabStack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (customData == null) {
            FPSCompress.LOGGER.error("PreFab has no NBT data");
            return;
        }

        CompoundTag nbt = customData.copyTag();

        // Extract cached rates (following PrefabNBTSerializer.loadRatesFromNBT() pattern)
        Map<UUID, List<BlueprintData.ResourceRate>> cachedRates = new HashMap<>();

        // Load per-UUID rates (schema v2)
        if (nbt.contains("importerExporterRates")) {
            ListTag uuidRatesList = nbt.getList("importerExporterRates", Tag.TAG_COMPOUND);
            for (int i = 0; i < uuidRatesList.size(); i++) {
                CompoundTag uuidTag = uuidRatesList.getCompound(i);
                UUID uuid = uuidTag.getUUID("uuid");

                List<BlueprintData.ResourceRate> rates = new ArrayList<>();
                ListTag resourceRatesList = uuidTag.getList("rates", Tag.TAG_COMPOUND);
                for (int j = 0; j < resourceRatesList.size(); j++) {
                    CompoundTag rateTag = resourceRatesList.getCompound(j);
                    String id = rateTag.getString("id");
                    double rate = rateTag.getDouble("rate");
                    rates.add(new BlueprintData.ResourceRate(id, rate));
                }

                cachedRates.put(uuid, rates);
            }
        }

        // Extract room dimensions
        int roomSizeX = nbt.contains("roomSizeX") ? nbt.getInt("roomSizeX") : 0;
        int roomSizeY = nbt.contains("roomSizeY") ? nbt.getInt("roomSizeY") : 0;
        int roomSizeZ = nbt.contains("roomSizeZ") ? nbt.getInt("roomSizeZ") : 0;

        if (roomSizeX == 0 || roomSizeY == 0 || roomSizeZ == 0) {
            FPSCompress.LOGGER.warn("PreFab missing room dimensions in NBT");
        }

        // Extract custom name
        String sourcePrefabName = nbt.contains("prefabName") ? nbt.getString("prefabName") : null;

        // Convert scanned resources to ResourceRequirement lists (v2 schema with NBT)
        FPSCompress.LOGGER.info("[Blueprint Create] scannedBlocks keys: {}", scannedBlocks.keySet());
        FPSCompress.LOGGER.info("[Blueprint Create] scannedBlockNbt keys: {}", scannedBlockNbt.keySet());

        List<BlueprintData.ResourceRequirement> blockRequirements = new ArrayList<>();
        for (Map.Entry<String, Long> entry : scannedBlocks.entrySet()) {
            String key = entry.getKey();
            String resourceId = BlockScanner.extractResourceId(key);
            CompoundTag requiredNbt = scannedBlockNbt.get(key);
            FPSCompress.LOGGER.info("[Blueprint Create] Block req: key={} id={} count={} nbtKeys={}",
                key, resourceId, entry.getValue(),
                requiredNbt != null ? requiredNbt.getAllKeys() : "none");
            blockRequirements.add(new BlueprintData.ResourceRequirement(
                resourceId,
                entry.getValue(),
                requiredNbt
            ));
        }

        List<BlueprintData.ResourceRequirement> itemRequirements = new ArrayList<>();
        for (Map.Entry<String, Long> entry : scannedItems.entrySet()) {
            String key = entry.getKey();
            String resourceId = key.contains("#") ? key.substring(0, key.indexOf('#')) : key;
            CompoundTag requiredNbt = scannedItemNbt.get(key);
            itemRequirements.add(new BlueprintData.ResourceRequirement(
                resourceId,
                entry.getValue(),
                requiredNbt  // Populated during item scanning (Phase 3)
            ));
        }

        // Create BlueprintData object
        BlueprintData blueprintData = new BlueprintData(
            blockRequirements,  // List<ResourceRequirement> (schema v2)
            itemRequirements,   // List<ResourceRequirement> (schema v2)
            cachedRates,        // Map<UUID, List<ResourceRate>>
            roomSizeX,
            roomSizeY,
            roomSizeZ,
            sourcePrefabName    // String (nullable)
        );

        // Create Blueprint ItemStack
        ItemStack blueprint = new ItemStack(FPSCompress.PREFAB_BLUEPRINT.get());
        blueprint.set(FPSDataComponents.BLUEPRINT_DATA.get(), blueprintData.toNBT());

        // Place in output slot and consume input
        setOutputSlot(blueprint);
        inventory.setStackInSlot(0, ItemStack.EMPTY); // Clear input slot
        setChanged();

        FPSCompress.LOGGER.info("Blueprint created successfully");

        // Send chat feedback
        sendChatFeedback(scannedBlocks.size(), scannedItems.size());
    }

    /**
     * Send chat feedback to nearest player when blueprint is created.
     *
     * <p>Phase 4: Notifies player of scan completion with resource counts and lists all resources.
     *
     * @param blockTypeCount Number of unique block types scanned
     * @param itemTypeCount Number of unique item types scanned
     */
    private void sendChatFeedback(int blockTypeCount, int itemTypeCount) {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Find nearest player within 64 blocks
        Player nearestPlayer = level.getNearestPlayer(
            worldPosition.getX() + 0.5,
            worldPosition.getY() + 0.5,
            worldPosition.getZ() + 0.5,
            64.0,  // Max distance
            false  // Not spectators
        );

        if (nearestPlayer == null) {
            return; // No player nearby, skip message
        }

        // Calculate total resource counts
        long totalBlocks = scannedBlocks.values().stream().mapToLong(Long::longValue).sum();
        long totalItems = scannedItems.values().stream().mapToLong(Long::longValue).sum();

        // Send success header message
        Component header = Component.literal("Blueprint created: ")
            .withStyle(ChatFormatting.GREEN)
            .append(Component.literal(blockTypeCount + " block types, " + itemTypeCount + " item types")
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" (Total: " + totalBlocks + " blocks, " + totalItems + " items)")
                .withStyle(ChatFormatting.GRAY));

        nearestPlayer.sendSystemMessage(header);

        // List scanned blocks
        if (!scannedBlocks.isEmpty()) {
            Component blocksHeader = Component.literal("Blocks: ")
                .withStyle(ChatFormatting.AQUA);
            nearestPlayer.sendSystemMessage(blocksHeader);

            for (Map.Entry<String, Long> entry : scannedBlocks.entrySet()) {
                String displayId = BlockScanner.extractResourceId(entry.getKey());
                Component blockLine = Component.literal("  - ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(displayId)
                        .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" x" + entry.getValue())
                        .withStyle(ChatFormatting.YELLOW));
                nearestPlayer.sendSystemMessage(blockLine);
            }
        }

        // List scanned items
        if (!scannedItems.isEmpty()) {
            Component itemsHeader = Component.literal("Items: ")
                .withStyle(ChatFormatting.AQUA);
            nearestPlayer.sendSystemMessage(itemsHeader);

            for (Map.Entry<String, Long> entry : scannedItems.entrySet()) {
                String displayId = entry.getKey().contains("#")
                    ? entry.getKey().substring(0, entry.getKey().indexOf('#')) : entry.getKey();
                Component itemLine = Component.literal("  - ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(displayId)
                        .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" x" + entry.getValue())
                        .withStyle(ChatFormatting.YELLOW));
                nearestPlayer.sendSystemMessage(itemLine);
            }
        }
    }

    /**
     * Get room bounds by scanning for CM wall blocks.
     * Pattern copied from InventoryScanningService.getRoomBoundsFromCM()
     *
     * @param cmLevel CM dimension level
     * @param center Room center position
     * @return Room AABB bounds (interior only, walls excluded)
     * @throws IllegalStateException if walls cannot be found
     */
    private AABB getRoomBoundsFromCM(ServerLevel cmLevel, BlockPos center) {
        // Try multiple known wall block IDs (CM versions may differ)
        Block wallBlock = null;
        String[] wallBlockIds = {
            "compactmachines:solid_wall",  // Primary (most common)
            "compactmachines:wall",         // Fallback
            "compactmachines:machine_wall"  // Alternative name
        };

        for (String blockId : wallBlockIds) {
            Block candidate = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
            if (!candidate.equals(net.minecraft.world.level.block.Blocks.AIR)) {
                wallBlock = candidate;
                FPSCompress.LOGGER.debug("Found wall block: {}", blockId);
                break;
            }
        }

        if (wallBlock == null) {
            throw new IllegalStateException("No valid CM wall block found in registry");
        }

        // Scan outward from center in all 6 directions to find walls
        int minX = scanForWall(cmLevel, center, Direction.WEST, wallBlock);
        int maxX = scanForWall(cmLevel, center, Direction.EAST, wallBlock);
        int minY = scanForWall(cmLevel, center, Direction.DOWN, wallBlock);
        int maxY = scanForWall(cmLevel, center, Direction.UP, wallBlock);
        int minZ = scanForWall(cmLevel, center, Direction.NORTH, wallBlock);
        int maxZ = scanForWall(cmLevel, center, Direction.SOUTH, wallBlock);

        // Add +1 to min coordinates to exclude walls from interior scan
        AABB bounds = new AABB(minX + 1, minY + 1, minZ + 1, maxX, maxY, maxZ);
        FPSCompress.LOGGER.info("Room bounds via wall scan: {}", bounds);
        return bounds;
    }

    /**
     * Scan outward from center until CM wall found.
     *
     * @param cmLevel CM dimension level
     * @param center Center position
     * @param dir Direction to scan
     * @param wallBlock Wall block to search for
     * @return Coordinate of wall in direction's axis
     * @throws IllegalStateException if wall not found
     */
    private int scanForWall(ServerLevel cmLevel, BlockPos center, Direction dir, Block wallBlock) {
        BlockPos.MutableBlockPos pos = center.mutable();
        int maxDistance = 20; // Safety limit (largest CM is 13x13x13)

        for (int i = 0; i < maxDistance; i++) {
            pos.move(dir);
            BlockState state = cmLevel.getBlockState(pos);

            if (state.is(wallBlock)) {
                FPSCompress.LOGGER.debug("Found wall in {} at distance {}", dir, i);
                Direction.Axis axis = dir.getAxis();
                if (axis == Direction.Axis.X) {
                    return pos.getX();
                } else if (axis == Direction.Axis.Y) {
                    return pos.getY();
                } else {
                    return pos.getZ();
                }
            }
        }

        throw new IllegalStateException("Wall not found in direction " + dir + " within " + maxDistance + " blocks");
    }

    /**
     * Get CM dimension ServerLevel.
     *
     * <p>Pattern copied from StateTransitionManager.getCMLevel()
     *
     * @return CM dimension ServerLevel, or null if unavailable
     */
    @Nullable
    private ServerLevel getCMLevel() {
        if (level == null || level.isClientSide()) {
            return null;
        }

        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }

        return server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse("compactmachines:compact_world")
                )
        );
    }

    // ===== MenuProvider Implementation =====

    @Override
    public Component getDisplayName() {
        return Component.literal("Fabricator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // Phase 5: Use custom FabricatorMenu with GUI buttons and resource checking
        this.lastInteractingPlayer = player;
        return new com.mukulramesh.fpscompress.gui.FabricatorMenu(containerId, playerInventory, worldPosition);
    }

    // ===== Phase 5: Scan State =====

    /**
     * Get the current scan state for GUI sync.
     *
     * @return 0=idle (no scan, no blueprint), 1=scanning (scanning or PreFab ready),
     *         2=ready_to_print (blueprint in input)
     */
    public int getScanState() {
        if (scanningBlocks) {
            return 2; // Scanning in progress
        }
        if (hasBlueprintInInput) {
            return 3; // Ready to print
        }
        if (prefabValidForScan) {
            return 1; // PreFab ready for scan
        }
        return 0; // Idle
    }

    /**
     * Get the ContainerData for GUI sync.
     *
     * @return ContainerData with scanState, requiredResourceCount, availableResourceCount
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public net.minecraft.world.inventory.ContainerData getFabricatorData() {
        return fabricatorData;
    }

    /**
     * Trigger a block scan (called by ScanRequestPacket handler).
     *
     * @return true if scan started, false if conditions not met
     */
    public boolean triggerScan() {
        if (level == null || level.isClientSide()) {
            return false;
        }

        // Validate PreFab in input slot
        ItemStack inputStack = getInputSlot();
        if (inputStack.isEmpty() || !inputStack.is(FPSCompress.PREFAB_ITEM.get())) {
            return false;
        }

        // Check output slot is empty
        if (!getOutputSlot().isEmpty()) {
            return false;
        }

        // Check can start scan
        if (!canStartBlockScan()) {
            return false;
        }

        // Start the scan
        startBlockScan();
        return true;
    }

    /**
     * Trigger printing (called by PrintRequestPacket handler).
     * Phase 5: Placeholder implementation.
     * Phase 6: Will implement actual PreFab carbon copy creation.
     *
     * @return true if printing conditions met, false otherwise
     */
    public boolean triggerPrint() {
        if (level == null || level.isClientSide()) {
            return false;
        }

        // Check blueprint in input slot
        if (!hasBlueprintInInput) {
            return false;
        }

        // Check output slot is empty
        if (!getOutputSlot().isEmpty()) {
            return false;
        }

        // Check all resources are satisfied
        if (availableResourceCount < requiredResourceCount || requiredResourceCount == 0) {
            return false;
        }

        // Phase 5: Placeholder - printing not yet implemented
        // Phase 6 will consume resources and create PreFab
        FPSCompress.LOGGER.info("Print requested - Phase 6 will implement actual printing");

        return true;
    }

    /**
     * Check resource slots against blueprint requirements.
     * Called when a Blueprint is detected in the input slot.
     *
     * <p>Reads blueprint data from the input slot item, then iterates
     * through resource slots (2-28) to count which requirements are satisfied.
     *
     * <p>NBT-aware matching: For resources with NBT requirements (e.g., PreFab blocks
     * with specific roomCode), uses NbtRequirement.subset matching.
     */
    public void checkRequiredResources() {
        FPSCompress.LOGGER.info("[Fabricator ResCheck] === BEGIN ===");
        requiredResourceCount = 0;
        availableResourceCount = 0;
        satisfiedSlotMask = 0;

        ItemStack blueprintStack = getInputSlot();
        if (blueprintStack.isEmpty()) {
            FPSCompress.LOGGER.info("[Fabricator ResCheck] No blueprint in input");
            return;
        }

        CompoundTag nbt = blueprintStack.get(FPSDataComponents.BLUEPRINT_DATA.get());
        if (nbt == null) {
            FPSCompress.LOGGER.info("[Fabricator ResCheck] Blueprint has no data component");
            return;
        }

        BlueprintData blueprint = BlueprintData.fromNBT(nbt);
        if (blueprint.isEmpty()) {
            FPSCompress.LOGGER.info("[Fabricator ResCheck] Blueprint data is empty");
            return;
        }

        // Combine block and item requirements
        List<BlueprintData.ResourceRequirement> allReqs = new ArrayList<>();
        allReqs.addAll(blueprint.getBlockResources());
        allReqs.addAll(blueprint.getItemResources());

        FPSCompress.LOGGER.info("[Fabricator ResCheck] Blueprint has {} block + {} item = {} total requirements",
            blueprint.getBlockResources().size(), blueprint.getItemResources().size(), allReqs.size());

        requiredResourceCount = allReqs.size();

        // Assign each requirement to a resource slot (slot 2 → 3 → 4...)
        inventory.clearAllFilters();
        if (!allReqs.isEmpty()) {
            int slot = RESOURCE_START;
            for (BlueprintData.ResourceRequirement req : allReqs) {
                if (slot >= TOTAL_SLOTS) {
                    break; // Too many resource types for 27 slots
                }
                net.minecraft.world.item.Item reqItem = BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse(req.id()));
                if (reqItem != net.minecraft.world.item.Items.AIR) {
                    inventory.setSlotFilter(slot, reqItem);
                }
                slot++;
            }
        }

        if (allReqs.isEmpty()) {
            availableResourceCount = 0;
            return;
        }

        // Build a map of what's available in resource slots
        Map<String, Long> availableMap = new HashMap<>();
        Map<String, List<CompoundTag>> availableNbtMap = new HashMap<>();

        for (int slot = 2; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            String resourceId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            availableMap.merge(resourceId, (long) stack.getCount(), Long::sum);

            // Track NBT data for items that have it
            boolean hasBlockData = stack.has(DataComponents.BLOCK_ENTITY_DATA);
            if (hasBlockData) {
                CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                if (customData != null) {
                    availableNbtMap.computeIfAbsent(resourceId, k -> new ArrayList<>())
                        .add(customData.copyTag());
                }
            }
            FPSCompress.LOGGER.info("[Fabricator ResCheck] Slot {}: {} x{} hasBlockData={}",
                slot, resourceId, stack.getCount(), hasBlockData);
        }

        FPSCompress.LOGGER.info("[Fabricator ResCheck] Available NBT map keys: {}",
            availableNbtMap.keySet());

        // Check each requirement against available resources
        int satisfied = 0;
        int mask = 0;
        for (int reqIndex = 0; reqIndex < allReqs.size(); reqIndex++) {
            BlueprintData.ResourceRequirement req = allReqs.get(reqIndex);
            FPSCompress.LOGGER.info("[Fabricator ResCheck] Checking req: id={} count={} hasNbt={}",
                req.id(), req.count(), req.nbt() != null && !req.nbt().isEmpty());

            Long availableCount = availableMap.getOrDefault(req.id(), 0L);
            if (availableCount < req.count()) {
                FPSCompress.LOGGER.info("[Fabricator ResCheck]   → FAIL (count: need {} have {})",
                    req.count(), availableCount);
                continue;
            }

            // Check NBT requirements if present
            CompoundTag requiredNbt = req.nbt();
            if (requiredNbt != null && !requiredNbt.isEmpty()) {
                FPSCompress.LOGGER.info("[Fabricator ResCheck]   NBT required, keys: {}",
                    requiredNbt.getAllKeys());
                List<CompoundTag> availableNbts = availableNbtMap.get(req.id());
                if (availableNbts == null || availableNbts.isEmpty()) {
                    FPSCompress.LOGGER.info("[Fabricator ResCheck]   → FAIL (NBT required but none on items)");
                    continue;
                }

                FPSCompress.LOGGER.info("[Fabricator ResCheck]   Checking {} available NBT(s)", availableNbts.size());
                boolean nbtMatches = false;
                for (int i = 0; i < availableNbts.size(); i++) {
                    CompoundTag availableNbt = availableNbts.get(i);
                    FPSCompress.LOGGER.info("[Fabricator ResCheck]   Available NBT[{}] keys: {}",
                        i, availableNbt.getAllKeys());
                    NbtRequirement nbtReq = NbtRequirementRegistry.getInstance()
                        .getRequirement(req.id()).orElse(null);
                    boolean match = nbtReq != null
                        && nbtReq.matches(requiredNbt, availableNbt);
                    FPSCompress.LOGGER.info("[Fabricator ResCheck]   NBT match[{}] = {}", i, match);
                    if (match) {
                        nbtMatches = true;
                        break;
                    }
                }
                if (!nbtMatches) {
                    FPSCompress.LOGGER.info("[Fabricator ResCheck]   → FAIL (NBT mismatch)");
                    continue;
                }
                FPSCompress.LOGGER.info("[Fabricator ResCheck]   NBT matched ✓");
            }

            satisfied++;
            mask |= (1 << reqIndex);
            FPSCompress.LOGGER.info("[Fabricator ResCheck]   → SATISFIED ({}/{})",
                satisfied, requiredResourceCount);
        }

        // Per-slot NBT validation: eject items with wrong/missing NBT
        validateAndEjectNbtMismatches(allReqs);

        availableResourceCount = satisfied;
        satisfiedSlotMask = mask;
        FPSCompress.LOGGER.info("[Fabricator ResCheck] === END: {}/{} satisfied ===",
            availableResourceCount, requiredResourceCount);
    }

    /**
     * Validate each resource slot's NBT against its blueprint requirement.
     * Items with wrong/missing NBT are ejected into the world.
     * Only runs server-side and when not already ejecting (guard flag).
     *
     * @param allReqs the combined list of block + item requirements
     */
    private void validateAndEjectNbtMismatches(
            List<BlueprintData.ResourceRequirement> allReqs) {
        if (level.isClientSide() || rejectingNbtMismatch) {
            return;
        }

        for (int slot = RESOURCE_START; slot < RESOURCE_START + allReqs.size()
                && slot < TOTAL_SLOTS; slot++) {
            int ri = slot - RESOURCE_START;
            BlueprintData.ResourceRequirement req = allReqs.get(ri);
            CompoundTag requiredNbt = req.nbt();
            if (requiredNbt == null || requiredNbt.isEmpty()) {
                continue;
            }

            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            // Only validate NBT for items matching the requirement type
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!itemId.equals(req.id())) {
                continue;
            }

            CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            boolean nbtOk = false;
            if (customData != null) {
                NbtRequirement nbtReq = NbtRequirementRegistry.getInstance()
                    .getRequirement(req.id()).orElse(null);
                nbtOk = nbtReq != null
                    && nbtReq.matches(requiredNbt, customData.copyTag());
            }

            if (!nbtOk) {
                FPSCompress.LOGGER.info(
                    "[Fabricator ResCheck]   Ejecting slot {}: NBT mismatch", slot);
                ejectItemFromSlot(slot, stack);
            }
        }
    }

    /**
     * Eject an item from a resource slot. Tries to return it to the last
     * interacting player's inventory first; any remainder is dropped in the world.
     * Uses a guard flag (rejectingNbtMismatch) to prevent recursive calls
     * through onContentsChanged.
     *
     * @param slot  The resource slot to clear
     * @param stack The ItemStack to eject (non-empty)
     */
    private void ejectItemFromSlot(int slot, ItemStack stack) {
        if (level == null || level.isClientSide() || stack.isEmpty()) {
            return;
        }

        rejectingNbtMismatch = true;
        inventory.setStackInSlot(slot, ItemStack.EMPTY);

        // Try player inventory first (add() modifies stack in-place, returns
        // boolean for whether the full stack was inserted)
        ItemStack remainder = stack.copy();
        if (lastInteractingPlayer != null && lastInteractingPlayer.isAlive()) {
            lastInteractingPlayer.getInventory().add(remainder);
        }

        // Drop any remainder in the world
        if (!remainder.isEmpty()) {
            double x = worldPosition.getX() + 0.5;
            double y = worldPosition.getY() + 1.0;
            double z = worldPosition.getZ() + 0.5;
            net.minecraft.world.entity.item.ItemEntity entity =
                new net.minecraft.world.entity.item.ItemEntity(level, x, y, z, remainder);
            entity.setPickUpDelay(10);
            level.addFreshEntity(entity);
        }

        rejectingNbtMismatch = false;
    }

    // ===== NBT Persistence =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // Save inventory
        tag.put("Inventory", inventory.serializeNBT(registries));

        // Phase 3: Save scan state
        tag.putBoolean("scanningBlocks", scanningBlocks);

        // Phase 3: Save scanned blocks (if any)
        if (!scannedBlocks.isEmpty()) {
            CompoundTag blocksTag = new CompoundTag();
            for (Map.Entry<String, Long> entry : scannedBlocks.entrySet()) {
                blocksTag.putLong(entry.getKey(), entry.getValue());
            }
            tag.put("scannedBlocks", blocksTag);
        }

        // Phase 4: Save scanned items (if any)
        if (!scannedItems.isEmpty()) {
            CompoundTag itemsTag = new CompoundTag();
            for (Map.Entry<String, Long> entry : scannedItems.entrySet()) {
                itemsTag.putLong(entry.getKey(), entry.getValue());
            }
            tag.put("scannedItems", itemsTag);
        }

        // Phase 5: Save block NBT templates
        if (!scannedBlockNbt.isEmpty()) {
            CompoundTag blockNbtTag = new CompoundTag();
            for (Map.Entry<String, CompoundTag> entry : scannedBlockNbt.entrySet()) {
                blockNbtTag.put(entry.getKey(), entry.getValue());
            }
            tag.put("scannedBlockNbt", blockNbtTag);
        }

        // Phase 5: Save item NBT templates
        if (!scannedItemNbt.isEmpty()) {
            CompoundTag itemNbtTag = new CompoundTag();
            for (Map.Entry<String, CompoundTag> entry : scannedItemNbt.entrySet()) {
                itemNbtTag.put(entry.getKey(), entry.getValue());
            }
            tag.put("scannedItemNbt", itemNbtTag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Load inventory
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }

        // Phase 3: Load scan state
        scanningBlocks = tag.getBoolean("scanningBlocks");

        // Phase 3: Load scanned blocks
        scannedBlocks.clear();
        if (tag.contains("scannedBlocks")) {
            CompoundTag blocksTag = tag.getCompound("scannedBlocks");
            for (String key : blocksTag.getAllKeys()) {
                scannedBlocks.put(key, blocksTag.getLong(key));
            }
        }

        // Phase 4: Load scanned items
        scannedItems.clear();
        if (tag.contains("scannedItems")) {
            CompoundTag itemsTag = tag.getCompound("scannedItems");
            for (String key : itemsTag.getAllKeys()) {
                scannedItems.put(key, itemsTag.getLong(key));
            }
        }

        // Phase 5: Load block NBT templates
        scannedBlockNbt.clear();
        if (tag.contains("scannedBlockNbt")) {
            CompoundTag blockNbtTag = tag.getCompound("scannedBlockNbt");
            for (String key : blockNbtTag.getAllKeys()) {
                scannedBlockNbt.put(key, blockNbtTag.getCompound(key));
            }
        }

        // Phase 5: Load item NBT templates
        scannedItemNbt.clear();
        if (tag.contains("scannedItemNbt")) {
            CompoundTag itemNbtTag = tag.getCompound("scannedItemNbt");
            for (String key : itemNbtTag.getAllKeys()) {
                scannedItemNbt.put(key, itemNbtTag.getCompound(key));
            }
        }
    }

    // ===== Client Sync =====

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    // ===== Server-side Ticking =====

    /**
     * Server-side tick method.
     * Phase 3: Implements PreFab validation and block scanning.
     * Phase 4+: Will add blueprint creation and printing.
     *
     * @param level Level instance
     * @param pos Block position
     * @param state Block state
     * @param fabricator Fabricator BlockEntity
     */
    public static void tick(Level level, BlockPos pos, BlockState state, FabricatorBlockEntity fabricator) {
        if (level.isClientSide()) {
            return;
        }

        // Phase 3: Update validation state each tick
        ItemStack inputStack = fabricator.getInputSlot();
        if (!inputStack.isEmpty()) {
            fabricator.prefabValidForScan = fabricator.validatePrefabForScanning(inputStack);
        } else {
            // Input slot empty - reset scan state
            fabricator.prefabValidForScan = false;
            fabricator.validationError = null;
            fabricator.hasScannedCurrentPrefab = false;
        }

        // Phase 5: Check blueprint in input slot for resource tracking
        boolean isBlueprint = !inputStack.isEmpty()
            && inputStack.get(FPSDataComponents.BLUEPRINT_DATA.get()) != null;
        if (isBlueprint != fabricator.hasBlueprintInInput) {
            fabricator.hasBlueprintInInput = isBlueprint;
            if (isBlueprint) {
                fabricator.checkRequiredResources();
            } else {
                fabricator.requiredResourceCount = 0;
                fabricator.availableResourceCount = 0;
                fabricator.satisfiedSlotMask = 0;
                fabricator.inventory.clearAllFilters();
            }
        }

        // Phase 5: Re-check resources when Blueprint in input and inventory changes
        if (fabricator.hasBlueprintInInput && fabricator.getLevel() != null
                && fabricator.getLevel().getGameTime() % 20 == 0) {
            fabricator.checkRequiredResources();
        }
    }

    // ===== Hopper/Pipe I/O =====

    /**
     * Get an I/O-restricted item handler for external automation (hoppers, pipes).
     * Insert only into resource slots (2-28), extract only from output slot (1).
     *
     * @return The restricted handler wrapper
     */
    public IItemHandler getIOWrapper() {
        return new IORestrictedHandler(inventory);
    }

    /**
     * IItemHandler wrapper that restricts external access:
     * Insert only into resource slots (2-28), extract only from output slot (1).
     */
    private static class IORestrictedHandler implements IItemHandler {

        private final IItemHandler delegate;

        IORestrictedHandler(IItemHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // Only allow insertion into resource slots (2-28)
            if (slot < RESOURCE_START) {
                return stack;
            }
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Only allow extraction from output slot (1)
            if (slot != OUTPUT_SLOT) {
                return ItemStack.EMPTY;
            }
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }
    }

    /**
     * Custom ItemStackHandler with blueprint-driven slot filtering and unlimited resource capacity.
     */
    class FilteredItemStackHandler extends ItemStackHandler {
        private final Map<Integer, net.minecraft.world.item.Item> slotFilters = new HashMap<>();

        FilteredItemStackHandler(int size) {
            super(size);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // Trigger immediate re-check when items change in resource slots
            // Skip while ejecting to prevent infinite recursion
            if (slot >= RESOURCE_START && hasBlueprintInInput && !rejectingNbtMismatch) {
                checkRequiredResources();
            }
        }

        void setSlotFilter(int slot, net.minecraft.world.item.Item item) {
            slotFilters.put(slot, item);
        }

        void clearAllFilters() {
            slotFilters.clear();
        }

        net.minecraft.world.item.Item getSlotFilter(int slot) {
            return slotFilters.get(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // Slot 1 (output) is output-only
            if (slot == OUTPUT_SLOT) {
                return false;
            }
            // Resource slots: only accept items matching the blueprint filter
            if (slot >= RESOURCE_START) {
                net.minecraft.world.item.Item filter = slotFilters.get(slot);
                return filter != null && stack.is(filter);
            }
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
            // Resource slots have unlimited capacity
            if (slot >= RESOURCE_START) {
                return Integer.MAX_VALUE;
            }
            return super.getSlotLimit(slot);
        }
    }
}
