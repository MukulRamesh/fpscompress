# PreFab Blueprint System Implementation Plan

## Context

The PreFab Blueprint System enables players to scan configured PreFab factories to create reusable blueprints, then print copies with resource costs. This is a large feature that will be built incrementally over 8 phases to ensure stability and testability at each step.

**Why this matters**: Enables mass production of PreFabs for Fractal Factory integration, creates a progression system with resource sinks, and allows blueprint trading between players.

**Key constraints**:
- PreFabs with fake rooms (`fake_*`) and carbon copies (`cc_*`) cannot be scanned
- Printed PreFabs are "carbon copies" with no room linkage (roomCode = `cc_<uuid>`)
- Blueprints must store complete resource lists and cached rates
- Printing requires full resource costs + configurable constant costs

---

## Phase 1: Blueprint Item & Basic NBT Storage ✅ COMPLETE

**Goal**: Create the PreFab Blueprint item with NBT storage for scan data (no scanning/printing logic yet).

**Completion Date**: 2026-05-25  
**Actual Time**: ~4 hours

### Implementation Summary

Created a complete blueprint item system with custom DataComponent for NBT storage, tooltip rendering showing resource counts and cached rates, and full NBT serialization/deserialization.

**Key Design Decision**: Used custom `BLUEPRINT_DATA` DataComponent instead of `BLOCK_ENTITY_DATA` (which only works for BlockItems). This allows regular Item class to store complex NBT data.

### Files Created (4 files)

1. **`src/main/java/com/mukulramesh/fpscompress/blueprint/BlueprintData.java`** (297 lines)
   - NBT serialization helper with schema versioning
   - Fields: `blockResources`, `itemResources`, `cachedRates`, `roomSizeX/Y/Z`, `sourcePrefabName`
   - Methods: `toNBT()`, `fromNBT(CompoundTag)`, `createEmpty()`, getters
   - Inner class: `ResourceRate` (resourceId + rate with NBT serialization)
   - Uses ListTag pattern from `PrefabNBTSerializer` for collections

2. **`src/main/java/com/mukulramesh/fpscompress/blueprint/PreFabBlueprintItem.java`** (215 lines)
   - Extends `Item` (NOT BlockItem)
   - Custom `getName()` - displays `sourcePrefabName` if present
   - Custom `appendHoverText()` - shows:
     - "Empty Blueprint" (gray) if no data
     - "X block types, Y item types" (yellow)
     - "Room Size: XxYxZ" (aqua)
     - Top 5 cached rates (green for outputs, red for inputs)
     - "...and X more" if >5 rates
   - Accesses data via `stack.get(FPSDataComponents.BLUEPRINT_DATA.get())`

3. **`src/main/resources/assets/fpscompress/models/item/prefab_blueprint.json`**
   - Standard "generated" item model pointing to texture

4. **`src/main/resources/assets/fpscompress/textures/item/prefab_blueprint.png`**
   - Placeholder texture (copied from prefab_upgrade_template.png)
   - TODO Phase 7: Replace with custom blueprint design

### Files Modified (3 files)

1. **`src/main/java/com/mukulramesh/fpscompress/FPSCompress.java`**
   - Added import: `com.mukulramesh.fpscompress.blueprint.PreFabBlueprintItem`
   - Registered item: `PREFAB_BLUEPRINT` with `.stacksTo(1).fireResistant()`
   - Added to creative tab: `output.accept(PREFAB_BLUEPRINT.get())`

2. **`src/main/java/com/mukulramesh/fpscompress/component/FPSDataComponents.java`**
   - Added custom component: `BLUEPRINT_DATA` (stores `CompoundTag`)
   - Uses `CompoundTag.CODEC` for persistence and `ByteBufCodecs.COMPOUND_TAG` for network sync
   - **Critical**: This is what allows regular Items to store complex NBT data

3. **`src/main/resources/assets/fpscompress/lang/en_us.json`**
   - Added 6 translation keys:
     - `item.fpscompress.prefab_blueprint`
     - `item.fpscompress.prefab_blueprint.state.new`
     - `item.fpscompress.prefab_blueprint.resources` (format: 2 params)
     - `item.fpscompress.prefab_blueprint.room_size` (format: 3 params)
     - `item.fpscompress.prefab_blueprint.rates_header`
     - `item.fpscompress.prefab_blueprint.rates_more` (format: 1 param)

### Testing Commands

**Empty blueprint:**
```minecraft
/give @p fpscompress:prefab_blueprint
```

**Blueprint with resources:**
```minecraft
/give @p fpscompress:prefab_blueprint[fpscompress:blueprint_data={schemaVersion:1,blockResources:[{id:"minecraft:stone",count:64L},{id:"minecraft:iron_ore",count:16L}],itemResources:[{id:"minecraft:iron_ingot",count:32L}],roomSizeX:5,roomSizeY:5,roomSizeZ:5,sourcePrefabName:"Test Blueprint"}]
```

**Blueprint with cached rates:**
```minecraft
/give @p fpscompress:prefab_blueprint[fpscompress:blueprint_data={schemaVersion:1,cachedRates:[{uuid:[I;1,2,3,4],rates:[{id:"minecraft:iron_ingot",rate:0.213d},{id:"minecraft:coal",rate:-0.150d}]}],blockResources:[],itemResources:[],roomSizeX:5,roomSizeY:5,roomSizeZ:5}]
```

**Note**: Use `fpscompress:blueprint_data` NOT `block_entity_data` (that's only for BlockItems)

### Validation Results

✅ Compilation: `BUILD SUCCESSFUL`  
✅ Checkstyle: No violations  
✅ SpotBugs: No warnings  
✅ In-game: Blueprint appears in creative tab, tooltips render correctly

### For Future Developers

**How to add blueprint data to an ItemStack programmatically:**
```java
import com.mukulramesh.fpscompress.component.FPSDataComponents;
import com.mukulramesh.fpscompress.blueprint.BlueprintData;

// Create blueprint data
BlueprintData data = new BlueprintData(blockResources, itemResources, cachedRates,
    roomSizeX, roomSizeY, roomSizeZ, "Factory Name");

// Set on ItemStack
ItemStack blueprint = new ItemStack(FPSCompress.PREFAB_BLUEPRINT.get());
blueprint.set(FPSDataComponents.BLUEPRINT_DATA.get(), data.toNBT());

// Read from ItemStack
CompoundTag nbt = blueprint.get(FPSDataComponents.BLUEPRINT_DATA.get());
if (nbt != null) {
    BlueprintData loadedData = BlueprintData.fromNBT(nbt);
}
```

**Next Phase**: Phase 2 will create the Fabricator block (structure + inventory, no GUI yet)

---

## Phase 2: Fabricator Block (Structure Only) ✅ COMPLETE

**Goal**: Create the Fabricator block with inventory slots but no GUI or logic yet.

**Completion Date**: 2026-05-25  
**Actual Time**: ~4 hours

### Implementation Summary

Created a complete Fabricator block with 29-slot inventory system (1 input + 1 output + 27 resources), NBT persistence, and client sync. Block properties match PreFab for consistent gameplay experience.

### Files Created (7 files)

1. **`src/main/java/com/mukulramesh/fpscompress/blueprint/FabricatorBlock.java`** (145 lines)
   - Extends `Block` and implements `EntityBlock` interface
   - Uses `BlockStateProperties.HORIZONTAL_FACING` (4 directions, rotates opposite to player facing)
   - Block properties: `.strength(1.5f, 6.0f)` + `.explosionResistance(1200.0f)` + `.requiresCorrectToolForDrops()` (same as PreFab)
   - `useWithoutItem()` shows placeholder message: "Fabricator - GUI coming in Phase 5"
   - `getCloneItemStack()` returns fresh item (no NBT preservation in creative mode)
   - `getDrops()` preserves inventory via `DataComponents.BLOCK_ENTITY_DATA`
   - `onRemove()` handles cleanup when block is broken
   - `getTicker()` returns server-side ticker for future scanning/printing logic

2. **`src/main/java/com/mukulramesh/fpscompress/blueprint/FabricatorBlockEntity.java`** (249 lines)
   - Extends `BlockEntity` and implements `MenuProvider` interface
   - Inventory structure (29 slots total):
     - Slot 0: Input slot (accepts PreFab or Blueprint items)
     - Slot 1: Output slot (output-only via `isItemValid()` override)
     - Slots 2-28: Resource slots (27 slots for printing materials)
   - Uses `ItemStackHandler` with `onContentsChanged()` calling `setChanged()`
   - Inventory access methods:
     - `getInventory()` - package-private to prevent external modification (addresses SpotBugs EI_EXPOSE_REP)
     - `getInputSlot()` / `getOutputSlot()` / `setOutputSlot(ItemStack)` - slot accessors
     - `insertItem(ItemStack)` - inserts to resource slots (2-28), returns remainder
     - `extractItem(int)` - extracts from resource slots for printing operations
   - NBT persistence:
     - `saveAdditional()` - serializes inventory via `inventory.serializeNBT(registries)`
     - `loadAdditional()` - deserializes inventory via `inventory.deserializeNBT(registries, nbt)`
   - Client sync:
     - `getUpdatePacket()` - returns `ClientboundBlockEntityDataPacket.create(this)`
     - `getUpdateTag()` - calls `saveAdditional()` and returns tag
     - `handleUpdateTag()` - calls `loadAdditional()` with received tag
   - MenuProvider implementation:
     - `getDisplayName()` - returns "Fabricator" component
     - `createMenu()` - returns null (Phase 5 will implement)
   - Static `tick()` method - placeholder for Phase 3+ scanning/printing logic

3. **`src/main/resources/assets/fpscompress/blockstates/fabricator.json`**
   - 4 variants: `facing=north/south/east/west` with Y rotation (0°/180°/270°/90°)
   - Model: `fpscompress:block/fabricator`

4. **`src/main/resources/assets/fpscompress/models/block/fabricator.json`**
   - Parent: `minecraft:block/orientable`
   - Placeholder textures: `minecraft:block/purple_concrete` (top/side), `minecraft:block/magenta_concrete` (front)
   - **TODO Phase 7**: Replace with custom textures

5. **`src/main/resources/assets/fpscompress/models/item/fabricator.json`**
   - Parent: `fpscompress:block/fabricator` (references block model)

6. **`src/main/resources/data/fpscompress/loot_table/blocks/fabricator.json`**
   - Uses `minecraft:copy_components` function to preserve `minecraft:custom_data` from block entity
   - Includes `minecraft:survives_explosion` condition
   - Ensures inventory persists when block is broken

7. **`src/main/resources/assets/fpscompress/lang/en_us.json`** (modified)
   - Added: `"block.fpscompress.fabricator": "Fabricator"`

### Files Modified (2 files)

1. **`src/main/java/com/mukulramesh/fpscompress/FPSCompress.java`**
   - Added imports: `FabricatorBlock`, `FabricatorBlockEntity`
   - Registered `FABRICATOR_BLOCK` in BLOCKS register (line ~107)
   - Registered `FABRICATOR_BE` in BLOCK_ENTITIES register (line ~133-136)
   - Registered `FABRICATOR_ITEM` in ITEMS register (line ~202-204)
   - Added to creative tab: `output.accept(FABRICATOR_ITEM.get())` (after PREFAB_BLUEPRINT)

2. **`config/spotbugs/spotbugs-excludes.xml`**
   - Added `lambda$static$3` to NP_NONNULL_PARAM_VIOLATION exclusion (line ~84)
     - Justification: `BlockEntityType.Builder.build(null)` is standard NeoForge pattern
   - Added UC_USELESS_VOID_METHOD exclusion for `FabricatorBlockEntity.tick()` (line ~217-223)
     - Justification: Placeholder for Phase 3+ implementation, required for ticker registration

### Key Design Decisions

**Why 29 slots (not 27)?**
- Slot 0: Input (PreFab for scanning, Blueprint for printing)
- Slot 1: Output (Blueprint from scanning, PreFab from printing)
- Slots 2-28: Resources (27 slots = 3x9 grid like double chest)
- Separating input/output prevents automation conflicts in Phase 5+

**Why package-private `getInventory()`?**
- Prevents external code from modifying inventory directly
- Forces use of safe accessor methods (`insertItem()`, `extractItem()`)
- Addresses SpotBugs EI_EXPOSE_REP warning without @SuppressFBWarnings

**Why same properties as PreFab?**
- Hardness 1.5f = fast to break with pickaxe (iron block speed)
- Explosion resistance 1200.0f = immune to creeper/TNT (same as bedrock)
- Prevents accidental inventory loss from explosions
- Consistent gameplay experience across mod blocks

**Why HORIZONTAL_FACING (not full FACING)?**
- Fabricator is a crafting-like block (similar to furnace/crafting table)
- Only needs 4 horizontal directions (up/down placement not meaningful)
- Simplifies blockstate JSON and model rotation

### Validation Results

✅ Compilation: `BUILD SUCCESSFUL`  
✅ Checkstyle: No violations  
✅ SpotBugs: No warnings (with proper exclusions)  
✅ In-game: Block places correctly, rotates properly, placeholder message works

### Testing Commands

**Give Fabricator:**
```minecraft
/give @p fpscompress:fabricator
```

**Test NBT preservation (with F3+H advanced tooltips):**
1. Place Fabricator, break it, check tooltip for NBT data
2. Or use: `/give @p fpscompress:fabricator[minecraft:block_entity_data={Inventory:{Size:29}}]`

**Test rotation:**
- Place while facing different directions, check F3 debug screen for `facing=` value

### For Future Developers

**Adding new inventory methods:**
- Modify `FabricatorBlockEntity.java`
- Follow pattern: check slot bounds, call `setChanged()`, return appropriate value
- Keep `getInventory()` package-private for safety

**Changing slot layout:**
- Update `ItemStackHandler` size in constructor (line ~37)
- Update `insertItem()` and `extractItem()` slot ranges (currently 2-28)
- Update Phase 5 GUI to match new slot count

**Adding to ticker logic:**
- Modify `FabricatorBlockEntity.tick()` static method
- Always check `level.isClientSide()` first (return early on client)
- Use `fabricator.setChanged()` when modifying state
- Phase 3+ will add scanning logic here

**Troubleshooting SpotBugs:**
- If adding new lambda to `FPSCompress.java` for BlockEntityType registration, add `lambda$static$X` to `spotbugs-excludes.xml` line ~84
- Check lambda index with: `grep "lambda\$static" build/reports/spotbugs/main.html`

### Next Phase Preview

**Phase 3: Scanning Mode - Block Scanning**

Dependencies on Phase 2:
- ✅ FabricatorBlockEntity exists with input/output slots
- ✅ Input slot accepts PreFab items
- ✅ Output slot can hold Blueprint items
- ✅ Ticker infrastructure ready for scanning logic

Phase 3 will add:
- `BlockScanner.java` utility for async block scanning
- PreFab validation in input slot (reject fake rooms, carbon copies)
- Block scanning execution when PreFab inserted
- Store scanned block types in temporary field

Estimated time: 4-5 days

---

## Phase 3: Scanning Mode - Block Scanning ✅ COMPLETE

**Goal**: Implement block scanning to detect all blocks in a CM room (not items yet).

**Completion Date**: 2026-05-25  
**Actual Time**: ~3 hours

### Implementation Summary

Created a complete block scanning system with async tick-spreading, PreFab validation, and automatic scan triggering when valid PreFabs are inserted into the Fabricator.

### Files Created (1 file)

1. **`src/main/java/com/mukulramesh/fpscompress/scanner/BlockScanner.java`** (167 lines)
   - `scanBlocksAsync(ServerLevel, BlockPos, AABB)` - Returns `CompletableFuture<Map<String, Long>>`
   - `scanBlocksSync(ServerLevel, BlockPos, AABB)` - Synchronous variant for testing
   - `scanNextBatch()` - Private method for tick-spreading (100 blocks per tick)
   - Exclusions: Air blocks, ImporterBlock, ExporterBlock
   - Resource ID format: `BuiltInRegistries.BLOCK.getKey(block).toString()`
   - Logging: Start and completion messages with block type counts

### Files Modified (2 files)

1. **`src/main/java/com/mukulramesh/fpscompress/blueprint/FabricatorBlockEntity.java`**
   - Added imports: MachineState, BlockScanner, ServerLevel, AABB, CustomData, DataComponents, HashMap, Map, Locale
   - Added fields (line ~58-65):
     - `scanningBlocks` - Boolean tracking scan state
     - `scannedBlocks` - Map<String, Long> storing block counts
     - `prefabValidForScan` - Boolean validation state
     - `validationError` - String for GUI feedback (Phase 5)
   - Added method `validatePrefabForScanning()` (~70 lines):
     - Checks PreFab item type
     - Validates NBT data via DataComponents.BLOCK_ENTITY_DATA
     - Rejects fake rooms (`fake_*`)
     - Rejects carbon copies (`cc_*`)
     - Validates state is CACHED or HALTED
     - Uses `Locale.ROOT` for case conversion (SpotBugs compliant)
   - Added method `canStartBlockScan()` (~20 lines):
     - Pre-conditions: not scanning, valid PreFab, empty output slot, server-side
   - Added method `startBlockScan()` (~90 lines):
     - Reads PreFab NBT via CustomData.copyTag()
     - Extracts roomCode, roomCenter, roomSize
     - Accesses CM dimension
     - Calculates AABB bounds
     - Calls BlockScanner.scanBlocksAsync()
     - Async completion handlers with main thread callbacks
     - Error handling with logging
   - Added method `getCMLevel()` (~20 lines):
     - Pattern from StateTransitionManager
     - Returns CM dimension ServerLevel
   - Modified `tick()` method (~15 lines):
     - Updates validation state each tick
     - Auto-starts scan when valid PreFab inserted (Phase 3 testing)
   - Modified `saveAdditional()` (+15 lines):
     - Saves scanningBlocks boolean
     - Saves scannedBlocks map to NBT
   - Modified `loadAdditional()` (+15 lines):
     - Loads scanningBlocks boolean
     - Loads scannedBlocks map from NBT
   - **Total additions**: ~245 lines
   - **New total**: ~494 lines (from 249)

2. **`config/spotbugs/spotbugs-excludes.xml`**
   - Added URF_UNREAD_FIELD exclusion for `validationError` field (lines ~227-236)
   - Justification: Field written in Phase 3, will be read by GUI in Phase 5

### Validation Results

✅ Compilation: `BUILD SUCCESSFUL`  
✅ Checkstyle: No violations  
✅ SpotBugs: No warnings (with proper exclusions)  
✅ Code complete: All methods implemented and integrated

### Key Design Decisions

**Why 100 blocks per tick (vs InventoryScanner's 20)?**
- BlockState checks are faster than capability queries
- 100 blocks/tick keeps TPS at 20 even for max CM rooms (13x13x13 = 2197 blocks = ~22 ticks)

**Why CustomData instead of getTag()?**
- NeoForge 1.21 uses DataComponents system for item NBT
- BLOCK_ENTITY_DATA component stores PreFab data
- Pattern from PrefabBlockItem.appendHoverText()

**Why auto-scan in tick()?**
- Phase 3 testing: Immediate feedback when PreFab inserted
- Phase 5 will replace with button-triggered scan
- Allows validation without GUI

**Why validationError field now?**
- Phase 5 GUI will show tooltips on disabled buttons
- Writing field during validation avoids duplicate logic later
- SpotBugs exclusion documents future use

### For Future Developers

**How to trigger scan programmatically:**
```java
FabricatorBlockEntity fabricator = ...;
ItemStack prefab = new ItemStack(FPSCompress.PREFAB_ITEM.get());
// ... set PreFab NBT via DataComponents.BLOCK_ENTITY_DATA ...
fabricator.getInventory().setStackInSlot(0, prefab); // Input slot
// Scan will auto-start on next tick if valid
```

**How to read scan results:**
```java
Map<String, Long> results = fabricator.scannedBlocks;
// Example: {"minecraft:stone": 64, "minecraft:iron_ore": 12}
```

**Phase 4 handoff:**
- Call `InventoryScanner.scanRoomAsync()` after block scan completes
- Use `CompletableFuture.allOf(blockFuture, itemFuture)` to wait for both
- Create BlueprintData from `scannedBlocks` + `scannedItems`

### Next Phase Preview

**Phase 4: Item Scanning & Blueprint Creation**

Dependencies on Phase 3:
- ✅ BlockScanner utility exists
- ✅ scannedBlocks field populated after scan
- ✅ startBlockScan() method structure established
- ✅ Async completion pattern proven

Phase 4 will add:
- InventoryScanner integration (call after block scan)
- `scannedItems` field for item counts
- `createBlueprintFromScan()` method
- Blueprint item creation in output slot
- PreFab consumption from input slot

Estimated time: 3-4 days

---

## Phase 3: Tasks (REFERENCE ONLY - SEE SUMMARY ABOVE)

1. **Create Block Scanner Utility**
   - Create `BlockScanner.java` in `com.mukulramesh.fpscompress.scanner`
   - Implement `scanBlocksAsync(ServerLevel cmLevel, BlockPos roomCenter, AABB roomBounds)`
   - Return `CompletableFuture<Map<String, Long>>` (resource ID → count)
   - Iterate through AABB bounds, check BlockState at each position
   - Exclude air blocks, Importer/Exporter blocks (player shouldn't pay for gates)
   - Use tick-spreading pattern from `InventoryScanner` (process N blocks per tick)

2. **Add Scan Detection Logic to Fabricator**
   - Detect PreFab item in input slot
   - Validate PreFab is scannable:
     - Must be in CACHED or HALTED state
     - roomCode must NOT start with `fake_` or `cc_`
     - roomCode must be valid (non-null, non-empty)
   - Store scan-ready state in BlockEntity field: `boolean canScan`

3. **Create Scan Execution Method**
   - Add method `startBlockScan()` in `FabricatorBlockEntity`
   - Read roomCode from PreFab NBT
   - Load CM dimension via `server.getLevel(cmDimensionKey)`
   - Get room bounds (AABB) from roomCenter + roomSize
   - Call `BlockScanner.scanBlocksAsync()`
   - Store result in temporary field: `Map<String, Long> scannedBlocks`
   - Set scan state: `scanningBlocks = true` (for GUI feedback)

### Validation

- **Linting**: Run `./gradlew clean compileJava checkstyleMain spotbugsMain`
- **In-game test**:
  - Use `/fps_dev2 give-test-prefab` to create a test PreFab with fake room
  - Modify test command to create PreFab with REAL room (place actual CM block)
  - Insert PreFab into Fabricator input slot
  - Manually trigger scan (via debug command if no GUI yet)
  - Check console logs: Should print "Scanned X unique block types"
  - Verify scan excludes air and gates

### Estimated Time: 4-5 days

---

## Phase 4: Scanning Mode - Item Scanning & Blueprint Creation ✅ COMPLETE

**Goal**: Add item scanning (reuse InventoryScanner) and create blueprint items.

**Completion Date**: 2026-05-25  
**Actual Time**: ~3 hours

### Implementation Summary

Integrated `InventoryScanner` to run parallel with `BlockScanner`, extracted cached rates and metadata from PreFab NBT, and created Blueprint items with complete factory data. Added chat feedback for scan completion.

### Files Modified (1 file)

1. **`FabricatorBlockEntity.java`** (~150 lines added, ~494 → ~753 total)
   - Added field: `scannedItems` (Map<String, Long>)
   - Modified `startBlockScan()`: Run block and item scans in parallel with `CompletableFuture.allOf()`
   - Added `createBlueprintFromScan()`: Extract PreFab NBT data and create Blueprint item (~100 lines)
   - Added `sendChatFeedback()`: Send chat message to nearest player with resource counts (~40 lines)
   - Modified `saveAdditional()`: Save `scannedItems` to NBT (+10 lines)
   - Modified `loadAdditional()`: Load `scannedItems` from NBT (+10 lines)

### Key Implementation Details

**Parallel Scanning**: Used `CompletableFuture.allOf(blockFuture, itemFuture)` to run both scans concurrently, reducing total time from ~25 ticks to ~22 ticks.

**NBT Extraction Pattern**: Followed `PrefabNBTSerializer.loadRatesFromNBT()` pattern to extract:
- `importerExporterRates` (schema v2 per-UUID rates)
- `roomSizeX/Y/Z` (room dimensions)
- `prefabName` (custom name for blueprint display)

**Chat Feedback**: Sends detailed multi-line message to player within 64 blocks:
```
"Blueprint created: X block types, Y item types (Total: A blocks, B items)"
"Blocks:"
"  - minecraft:stone x64"
"  - minecraft:iron_ore x12"
"Items:"
"  - minecraft:iron_ingot x32"
"  - minecraft:coal x16"
```

**Edge Case Handling**:
- Empty scans: Creates blueprint with 0 resources (valid)
- Output slot occupied: Logs error, leaves PreFab in input (retry-able)
- PreFab removed during scan: Logs error, aborts blueprint creation
- Missing room dimensions: Logs warning, uses `0` (Blueprint shows "0x0x0")

### Validation Results

✅ Compilation: `BUILD SUCCESSFUL`  
✅ Checkstyle: No violations  
✅ SpotBugs: No warnings  
✅ Code complete: All methods implemented and integrated

### For Future Developers

**Blueprint creation flow**:
1. Player inserts CACHED PreFab into Fabricator input slot
2. Validation checks pass (not fake room, not carbon copy)
3. `startBlockScan()` triggers both block and item scans in parallel
4. After ~22 ticks, both scans complete
5. `createBlueprintFromScan()` extracts PreFab NBT data
6. Blueprint created with all data, placed in output slot
7. PreFab consumed from input slot
8. Chat message sent to player

**Next Phase**: Phase 5 will add GUI with "Scan" button to manually trigger scans (instead of auto-scan on insertion).

---

## Phase 4: Tasks (REFERENCE ONLY - SEE SUMMARY ABOVE)

1. **Integrate InventoryScanner**
   - After `BlockScanner` completes, call `InventoryScanner.scanRoomAsync()`
   - Store result in temporary field: `Map<String, Long> scannedItems`
   - Wait for both futures: `CompletableFuture.allOf(blockFuture, itemFuture)`

2. **Create Blueprint from Scan Results**
   - Add method `createBlueprintFromScan()` in `FabricatorBlockEntity`
   - Read PreFab cached rates from NBT (use `PrefabNBTSerializer.loadRatesFromNBT()`)
   - Create `BlueprintData` object with:
     - `blockResources = scannedBlocks`
     - `itemResources = scannedItems`
     - `cachedRates` (from PreFab NBT)
     - `roomSizeX/Y/Z` (from PreFab NBT)
     - `sourcePrefabName` (from PreFab NBT if present)
   - Create new `ItemStack` of `PreFabBlueprintItem`
   - Store `BlueprintData` in ItemStack NBT
   - Place blueprint in output slot
   - Consume input PreFab (clear input slot)

3. **Add Chat Feedback**
   - When scan completes, send chat message to nearest player:
     - "Blueprint created: X block types, Y item types"
     - Total resource count summary

### Validation

- **Linting**: Run `./gradlew clean compileJava checkstyleMain spotbugsMain`
- **In-game test**:
  - Build simple factory in CM room (chest + furnace + some items)
  - Place real PreFab block, configure faces, run simulation, enter CACHED state
  - Break PreFab → Get item
  - Insert PreFab into Fabricator
  - Trigger scan (manual or via GUI)
  - Wait for async scan to complete
  - Check output slot: Should contain Blueprint item
  - Check input slot: PreFab should be consumed
  - Verify blueprint tooltip shows resource counts

### Estimated Time: 3-4 days

---

## Phase 5: Printing Mode - Resource Detection & GUI ✅ COMPLETE

**Goal**: Implement resource checking and create Fabricator GUI with Scan/Print buttons.

**Completion Date**: 2026-06-07  
**Actual Effort**: Delivered together with Phase 4 in commit `56d294c`  
**Commits**: `56d294c` (foundation) → `b0219d0` (race condition fix)

### Implementation Summary

The Fabricator block is a dual-mode machine: **Scanning** (PreFab → Blueprint) and **Printing** (Blueprint + Resources → PreFab). The GUI uses a single **state-aware action button** that changes label and active state based on input contents and scan progress. Resource slots show **ghost items** for what's needed, with **color-coded status indicators** driven by a server-side bitmask.

**Key Design Decision**: Rather than separate Scan/Print buttons, a single action button adapts (Idle → Scan → Scanning... → Print → Resource Starved) based on context. This keeps the GUI simple and prevents invalid actions.

**Print is a placeholder**: `triggerPrint()` validates resources but does not create a PreFab yet — that's Phase 6. The full print pipeline (resource consumption → carbon copy creation) is wired but stubbed.

### Scan State Machine

```
0 = IDLE           — No input, or input not recognized
1 = READY_TO_SCAN  — Valid PreFab in input slot, output empty
2 = SCANNING       — Async scan in progress (blocks + items)
3 = READY_TO_PRINT — Blueprint in input slot, resources being checked
```

ContainerData syncs 5 fields server→client: `scanState`, `requiredResourceCount`, `availableResourceCount`, `prefabValidForScan` (bool), `satisfiedSlotMask` (bitmask).

### Files Created (12 files)

| File | Lines | Purpose |
|------|-------|---------|
| `blueprint/FabricatorBlockEntity.java` | 1,435 | Main BE: inventory, scanning state machine, resource checking, NBT validation, deferred ejection |
| `gui/FabricatorScreen.java` | 473 | Client GUI: action button, slot status indicators, ghost item rendering, tooltips |
| `gui/FabricatorMenu.java` | 332 | Container: 65 slots (29 fab + 36 player), ContainerData sync, `quickMoveStack()` |
| `blueprint/FabricatorBlock.java` | 144 | Block: horizontal facing, drops with NBT preservation, GUI opening via `openMenu()` |
| `blueprint/NbtRequirement.java` | 329 | NBT matching engine: EXACT, SUBSET, LIST_SUBSET, RANGE strategies |
| `blueprint/NbtRequirementRegistry.java` | 159 | Datapack-driven per-resource-type NBT requirement definitions (`data/<ns>/nbt_requirements/`) |
| `network/ScanRequestPacket.java` | 68 | Client→Server: triggers `FabricatorBlockEntity.triggerScan()` |
| `network/PrintRequestPacket.java` | 77 | Client→Server: triggers `FabricatorBlockEntity.triggerPrint()` (Phase 6 placeholder) |
| `blueprint/BlueprintData.java` | 365 | Data model: `ResourceRequirement` (id, count, NBT) + `ResourceRate` (id, rate), schema v2 |
| `blueprint/PreFabBlueprintItem.java` | 211 | Item: tooltips showing resource counts, NBT requirements, cached rates |
| `scanner/BlockScanner.java` | 300 | Async NBT-aware block scanning with `NbtRequirement.extractNbt()` |
| `scanner/InventoryScanner.java` | 181+ | Refactored for NBT-aware item scanning with BLOCK_ENTITY_DATA extraction |

### Files Modified (7+ files)

- **`FPSCompress.java`**: Registered Fabricator block, item, BE type, menu type, Blueprint item, ScanRequestPacket, PrintRequestPacket, BLUEPRINT_DATA component
- **`FPSCompressClient.java`**: `MenuScreens.register(FabricatorMenu, FabricatorScreen::new)`
- **`FPSDataComponents.java`**: Added `BLUEPRINT_DATA` DataComponent using CompoundTag codec
- **`Config.java`**: Added `blueprintExcludedBlocks` server-side config (default excludes CM walls)
- **`Dev2TestCommands.java`**: `/fps_dev2 give-test-blueprint` command with custom rates and costs
- **`en_us.json`**: Blueprint-related translation keys (GUI strings, button labels, tooltips)
- **Asset files**: `fabricator.json` (block model, blockstate, item, loot table), `prefab_blueprint.png` texture

### Key Architecture Details

**Inventory Layout** (29 Fabricator slots + 36 player = 65 total):
- Slot 0: Input (accepts PreFab OR Blueprint items)
- Slot 1: Output (output-only: Blueprint from scan, PreFab from print)
- Slots 2–28: Resource slots (27 for printing materials)
- Slots 29–64: Player inventory + hotbar

**Resource Slot Filtering**: When a Blueprint is in the input slot, `checkRequiredResources()` sets per-slot `FilteredItemStackHandler` filters. Each requirement gets a dedicated slot — only that exact item type is accepted. Filters are cleared on Blueprint removal.

**Slot Status Indicators** (client-side rendering):
- **Green** (solid): Requirement satisfied (count ≥ required, NBT matches if applicable)
- **Yellow** (blink): Partial — some items present but not enough (≈2.5s sine wave)
- **Red** (blink): Empty — no items in slot; ghost item icon rendered showing what's needed
- NBT-required slots use the server-side `satisfiedSlotMask` bitmask for authoritative status

**NBT-Aware Resource Checking**: Items with NBT requirements (e.g., PreFab blocks with specific roomCode) are validated via `NbtRequirement.subset()` matching against `BLOCK_ENTITY_DATA`. The `satisfiedSlotMask` bitmask communicates per-slot NBT satisfaction from server to client.

**NBT Mismatch Ejection**: Items with wrong/missing NBT are ejected from resource slots. Uses a **deferred ejection system** to avoid racing with `quickMoveStack`:
1. `checkRequiredResources()` calls `validateAndEjectNbtMismatches()` which **queues** bad slots into `pendingEjections`
2. `tick()` processes the queue safely after `quickMoveStack` completes
3. 2-pass player inventory merge: try merging into existing partial stacks first, then place into first empty slot
4. Falls back to world drop if player inventory is full
5. `rejectingNbtMismatch` guard flag prevents infinite recursion from ejection → `onContentsChanged` → re-check

### Bugs Found & Fixed

- **reqIndex compaction bug** (`56d294c`): `satisfiedSlotMask` bitmask was misaligned with client-side slot indices. For-each loop only incremented `reqIndex` on satisfied requirements, compacting the bitmask. Fixed by converting to indexed for-loop so `reqIndex` increments unconditionally (even after `continue`).
- **NBT mismatch ejection race condition** (`b0219d0`): `validateAndEjectNbtMismatches()` could eject items into player inventory while `quickMoveStack` was simultaneously overwriting the same slot with `setByPlayer(EMPTY)`. Fixed by deferring ejections to next tick via `pendingEjections` queue.

### What's Deferred to Phase 6

- `triggerPrint()` is a **placeholder** — validates resources but does not consume them or create a PreFab
- Actual PreFab carbon copy creation (`createCarbonCopyPrefab()`)
- Resource consumption logic (`consumeRequiredResources()`)
- `cc_*` roomCode generation for printed PreFabs
- Constant cost config entries (blueprintConstantCosts, blueprintResourceMultiplier)

### Validation

- **Linting**: `./gradlew clean compileJava checkstyleMain spotbugsMain` — all passing
- **In-game test**:
  - Right-click Fabricator → 176×222 GUI with input/output slots and 3×9 resource grid
  - Insert PreFab → Button shows "Scan" (green, active)
  - Click "Scan" → Button shows "Scanning..." (gray, disabled); Blueprint appears in output
  - Insert Blueprint → Resource slots populate with filters; button shows "Print" or "Resource Starved"
  - Fill resource slots → Count label updates: "Blueprint Resources: X / Y"
  - All resources satisfied → "Print" button enables (green); click sends PrintRequestPacket
  - Wrong/missing NBT items → Auto-ejected to player inventory (deferred, race-safe)
  - Close/reopen GUI → State preserved via ContainerData sync

---

## Phase 6: Printing Mode - PreFab Creation & Carbon Copy System

**Goal**: Implement actual printing logic to create carbon copy PreFabs.

### Tasks

1. **Create Carbon Copy PreFab**
   - Add method `createCarbonCopyPrefab()` in `FabricatorBlockEntity`
   - Generate unique roomCode: `"cc_" + UUID.randomUUID().toString()`
   - Create new `ItemStack` of `PrefabBlock` item
   - Copy cached rates from Blueprint to PreFab NBT:
     - Use schema version 2 (UUID-based rates)
     - Copy `importerExporterRates` list
   - Set PreFab state to CACHED
   - Set `roomCode` to generated carbon copy code
   - Set `roomCenter = null` (no physical room linkage)
   - Do NOT copy simulation ticks or accumulators (start fresh)

2. **Implement Resource Consumption**
   - Add method `consumeRequiredResources()` in `FabricatorBlockEntity`
   - Iterate through required resources (from Blueprint + config)
   - Remove items from resource slots
   - Return boolean success (true if all consumed, false if insufficient)

3. **Connect Print Button Logic**
   - In `startPrinting()` method:
     - Validate Blueprint in input slot
     - Validate all resources available
     - Call `consumeRequiredResources()`
     - If successful:
       - Call `createCarbonCopyPrefab()`
       - Place carbon copy PreFab in output slot
       - Keep Blueprint in input slot (reusable)
       - Send chat message: "PreFab printed successfully"
     - If failed:
       - Send error message: "Insufficient resources"

4. **Update PrefabBlockEntity Validation**
   - Modify `validateLoadedData()` in `PrefabBlockEntity`
   - Allow carbon copy rooms (`cc_*`) without roomCenter validation
   - Carbon copies skip CM dimension checks (no physical room)

5. **Add Configuration Options**
   - Create config entries in `ModConfigSpec`:
     - `blueprintConstantCosts` (List of item IDs + counts)
     - `blueprintResourceMultiplier` (default 1.0)
     - `blueprintReusable` (default true)
   - Apply multiplier to scanned resource counts
   - Apply constant costs when checking/consuming resources

### Validation

- **Linting**: Run `./gradlew clean compileJava checkstyleMain spotbugsMain`
- **In-game test**:
  - Complete full workflow:
    1. Build factory in CM room
    2. Configure PreFab, run simulation, enter CACHED
    3. Scan PreFab → Get Blueprint
    4. Insert Blueprint + required resources
    5. Click "Print" → Get carbon copy PreFab
  - Test carbon copy PreFab:
    - Place carbon copy in world
    - Right-click → Open status GUI
    - Verify cached rates displayed correctly
    - Connect chests to faces
    - Verify resources transport (CACHED mode active)
    - Break and replace → NBT preserved
  - Test blueprint reusability:
    - Print multiple copies from same blueprint
    - Blueprint should remain in input slot
  - Test insufficient resources:
    - Remove some resources, try to print
    - Should show error, no PreFab created

### Estimated Time: 4-5 days

---

## Phase 7: Visual Polish & Error Handling

**Goal**: Add visual feedback, animations, and robust error handling.

### Tasks

1. **Add Visual Feedback**
   - Scanning progress bar in GUI (0-100%)
   - Printing animation (particles, crafting-like effect)
   - Sound effects:
     - Scan start: Mechanical whirring sound
     - Scan complete: Success chime
     - Print complete: Crafting sound

2. **Improve Resource Checklist UI**
   - Group resources by type (Blocks, Items, Constant Costs)
   - Show item icons next to resource names
   - Color-code: Green (satisfied), Yellow (partial), Red (missing)
   - Scrollable list if many resources

3. **Add Error Handling**
   - Handle invalid PreFabs gracefully:
     - Fake rooms: Show error tooltip "Cannot scan test PreFabs"
     - Carbon copies: Show error tooltip "Cannot scan carbon copies"
     - Wrong state: Show error tooltip "PreFab must be CACHED or HALTED"
   - Handle scan failures:
     - CM dimension not loaded
     - Room not found
     - Scan timeout
   - Handle print failures:
     - Blueprint corrupted
     - Insufficient inventory space

4. **Add Cancel Functionality**
   - Cancel button during scanning
   - Clear scan state and temporary data
   - Return PreFab to output slot

### Validation

- **Linting**: Run `./gradlew clean compileJava checkstyleMain spotbugsMain`
- **In-game test**:
  - Try to scan fake PreFab → Error message shown
  - Try to scan carbon copy PreFab → Error message shown
  - Start scan, click cancel → Scan stops, PreFab returned
  - Scan large room → Progress bar updates smoothly
  - Print PreFab → Particles and sound play
  - Verify all error messages are clear and helpful

### Estimated Time: 3-4 days

---

## Phase 8: Integration Testing & Documentation

**Goal**: Comprehensive testing and documentation for release.

### Tasks

1. **Test Edge Cases**
   - Empty CM room (no blocks/items)
   - Extremely large room (performance test)
   - Room with modded blocks/items
   - Multiple Fabricators running simultaneously
   - Blueprint with missing data (corrupted NBT)
   - Server restart mid-scan
   - Player disconnects during scan/print

2. **Test Fractal Factory Integration**
   - Create blueprint, print multiple copies
   - Insert carbon copy PreFabs into Fractal Factory (if implemented)
   - Verify Fractal Factory absorbs rates correctly
   - Verify carbon copies cannot enter CM rooms

3. **Performance Testing**
   - Scan 10x10x10 room (1000 blocks)
   - Scan 20x20x20 room (8000 blocks)
   - Monitor TPS during scan
   - Verify tick-spreading prevents lag

4. **Update Documentation**
   - Add section to WIKI/Getting-Started.md:
     - "Blueprints: Copying Your Factories"
     - Step-by-step workflow with screenshots
   - Add section to CHANGELOG.md:
     - New feature: PreFab Blueprint System
     - Items: PreFab Blueprint, Fabricator Block
     - Commands: (if any debug commands added)
   - Update CLAUDE.md if architecture changed

5. **Create Tutorial Content**
   - In-game Patchouli entry (via WIKI conversion)
   - Example blueprint (starter factory)

### Validation

- **Linting**: Run `./gradlew clean compileJava checkstyleMain spotbugsMain`
- **In-game test**:
  - Run all test cases from above
  - Verify no console errors or warnings
  - Verify no performance degradation
  - Test in multiplayer (if possible)

### Estimated Time: 3-4 days

---

## Critical Files to Create

New files (28 total):

**Core Classes (6 files)**:
- `src/main/java/com/mukulramesh/fpscompress/blueprint/PreFabBlueprintItem.java`
- `src/main/java/com/mukulramesh/fpscompress/blueprint/BlueprintData.java`
- `src/main/java/com/mukulramesh/fpscompress/blueprint/FabricatorBlock.java`
- `src/main/java/com/mukulramesh/fpscompress/blueprint/FabricatorBlockEntity.java`
- `src/main/java/com/mukulramesh/fpscompress/scanner/BlockScanner.java`
- `src/main/java/com/mukulramesh/fpscompress/config/BlueprintConfig.java`

**GUI Classes (4 files)**:
- `src/main/java/com/mukulramesh/fpscompress/gui/FabricatorMenu.java`
- `src/main/java/com/mukulramesh/fpscompress/gui/FabricatorScreen.java`
- `src/main/java/com/mukulramesh/fpscompress/network/ScanRequestPacket.java`
- `src/main/java/com/mukulramesh/fpscompress/network/PrintRequestPacket.java`

**Assets (12 files)**:
- `assets/fpscompress/textures/item/prefab_blueprint.png` *(use missing texture placeholder initially)*
- `assets/fpscompress/textures/block/fabricator_front.png` *(use missing texture placeholder initially)*
- `assets/fpscompress/textures/block/fabricator_side.png` *(use missing texture placeholder initially)*
- `assets/fpscompress/textures/block/fabricator_top.png` *(use missing texture placeholder initially)*
- `assets/fpscompress/models/item/prefab_blueprint.json`
- `assets/fpscompress/models/block/fabricator.json`
- `assets/fpscompress/blockstates/fabricator.json`
- `assets/fpscompress/textures/gui/fabricator.png` *(use missing texture placeholder initially)*
- `assets/fpscompress/sounds/scan_start.ogg` *(add in Phase 7)*
- `assets/fpscompress/sounds/scan_complete.ogg` *(add in Phase 7)*
- `assets/fpscompress/sounds/print_complete.ogg` *(add in Phase 7)*
- `assets/fpscompress/sounds.json` *(add in Phase 7)*

**Note**: Use Minecraft's default "missing texture" (purple/black checkerboard) for initial development. Create proper textures in Phase 7 during visual polish.

**Data (6 files)**:
- `data/fpscompress/loot_table/blocks/fabricator.json`
- `data/fpscompress/recipe/fabricator.json` (crafting recipe)
- `data/fpscompress/lang/en_us.json` (update with new translations)
- `WIKI/Blueprint-System.md` (new documentation page)
- `CHANGELOG.md` (update with new feature)
- `config/fpscompress-common.toml` (update with blueprint config)

---

## Files to Modify

Existing files (5 total):

1. **FPSCompress.java**
   - Add BLOCKS registration for Fabricator
   - Add ITEMS registration for Blueprint
   - Add BLOCK_ENTITY_TYPES registration
   - Add MENU_TYPES registration

2. **PrefabBlockEntity.java**
   - Modify `validateLoadedData()` to allow carbon copy rooms (`cc_*`)

3. **ClientSetup.java** (or equivalent)
   - Register `FabricatorScreen` for menu rendering

4. **NetworkHandler.java** (or create if missing)
   - Register packet handlers for ScanRequestPacket and PrintRequestPacket

5. **ModConfigSpec.java** (or equivalent)
   - Add blueprint configuration options

---

## Risk Mitigation

**High-Risk Areas**:
1. **Async scanning**: Could cause lag if not tick-spread properly
   - Mitigation: Follow InventoryScanner pattern, process N blocks per tick
   - Test with large rooms (20x20x20)

2. **NBT serialization**: Blueprint data could be large (thousands of resources)
   - Mitigation: Use compressed NBT format, aggregate similar items
   - Test with complex factories

3. **Carbon copy validation**: PreFabs without rooms could break assumptions
   - Mitigation: Add explicit checks for `cc_*` roomCodes in all room-lookup code
   - Grep for all uses of roomCode before Phase 6

4. **Resource checking**: Could miss edge cases (stackable vs non-stackable)
   - Mitigation: Use vanilla ItemStack.isSameItemSameComponents()
   - Test with tools, armor, enchanted items

**Testing Strategy**:
- Run full linting after EVERY phase (compileJava + checkstyle + spotbugs)
- Test in-game after EVERY phase (even if feature incomplete)
- Keep test world save with example factories
- Document any bugs found in TODO.md for tracking

---

## Estimated Total Time: 25-32 days (5-6 weeks)

**Phase breakdown**:
- Phase 1: 2-3 days
- Phase 2: 3-4 days
- Phase 3: 4-5 days
- Phase 4: 3-4 days
- Phase 5: 5-6 days
- Phase 6: 4-5 days
- Phase 7: 3-4 days
- Phase 8: 3-4 days

**Recommendation**: Work on one phase at a time, don't move to next phase until current phase passes all validation checks. Budget 1-2 extra days per phase for debugging and polish.

---

## Success Criteria

The Blueprint System is complete when:
- [ ] Player can scan a CACHED PreFab to create a Blueprint
- [ ] Blueprint shows correct resource counts in tooltip
- [ ] Player can print multiple copies from one Blueprint
- [ ] Printed PreFabs have correct cached rates
- [ ] Printed PreFabs work in CACHED mode (no room linkage needed)
- [ ] Cannot scan fake rooms or carbon copies
- [ ] Fabricator GUI is clear and intuitive
- [ ] No lag during scanning (even large rooms)
- [ ] All linters pass (no warnings)
- [ ] Documentation updated in WIKI and CHANGELOG

---

## Next Steps After This Plan

1. **Review this plan** - Are there any missing edge cases? Any phase order issues?
2. **Start Phase 1** - Create Blueprint item and NBT structure
3. **Validate incrementally** - Test after every phase, don't rush
4. **Update TODO.md** - Track progress and blockers
5. **Ask questions** - If anything unclear, ask before implementing
