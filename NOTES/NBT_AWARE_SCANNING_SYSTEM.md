# NBT-Aware Scanning System - Developer Documentation

**Created**: 2026-05-25  
**Status**: Phase 1-2 Complete, Phase 3-4 In Progress  
**Related Files**: `NbtRequirement.java`, `NbtRequirementRegistry.java`, `BlueprintData.java`

---

## Overview

The NBT-Aware Scanning System extends the blueprint scanner to capture and validate NBT data for specific blocks/items. This ensures that functionally-different items (e.g., PreFabs with different cached rates) are distinguished during scanning and printing.

**Problem Solved**: Without NBT tracking, the scanner treats all items of the same type as identical. A PreFab producing 100 iron/tick would be considered the same as one producing 1 dirt/tick.

**Solution**: JSON schemas define which blocks/items have important NBT fields. During scanning, these fields are extracted and stored in the blueprint. During printing (Phase 6), the Fabricator validates that provided resources match both ID and NBT.

---

## Architecture

### Three-Layer Design

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: JSON Schema Definitions                           │
│  data/fpscompress/nbt_requirements/{blocks,items}/*.json    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Registry & Requirement Classes                    │
│  NbtRequirementRegistry (singleton, datapack reload)        │
│  NbtRequirement (record: resourceId, nbtFields, matchMode)  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 3: Scanner & Blueprint Integration                   │
│  BlockScanner / InventoryScanner (extract NBT)              │
│  BlueprintData.ResourceRequirement (store NBT)              │
│  FabricatorBlockEntity (create blueprints with NBT)         │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

**Scanning Phase** (Phase 3-4):
```
1. Scanner encounters block/item in CM room
2. Check: NbtRequirementRegistry.hasRequirement(resourceId)?
3. If yes:
   a. Get NbtRequirement from registry
   b. Extract full NBT from BlockEntity or ItemStack
   c. Filter NBT using nbtFields list (e.g., ["BlockEntityTag.roomCode"])
   d. Create ResourceRequirement with filtered NBT
4. If no:
   a. Create ResourceRequirement with null NBT (any item accepted)
5. Store in blueprint as schema v2
```

**Printing Phase** (Phase 6, future):
```
1. Read blueprint ResourceRequirement
2. Check player's resources in Fabricator slots
3. If requirement has NBT:
   a. Extract NBT from player's item using same nbtFields
   b. Compare using matchMode (SUBSET or EXACT)
   c. Only accept if NBT matches
4. If requirement has null NBT:
   a. Accept any item of correct type
```

---

## JSON Schema Format

### File Location

```
src/main/resources/data/<namespace>/nbt_requirements/
  blocks/
    prefab.json           ← Block NBT requirements
    super_smelter.json
  items/
    enchanted_book.json   ← Item NBT requirements
```

**Note**: These are datapack-compatible. Modpack developers can override via datapacks.

### Schema Structure

```json
{
  "resource_id": "modid:item_name",
  "nbt_fields": [
    "path.to.field1",
    "path.to.field2"
  ],
  "match_mode": "subset",
  "description": "Human-readable explanation"
}
```

**Fields**:
- `resource_id` (string, required): Namespaced ID from registry
- `nbt_fields` (array, required): Dot-notation paths to NBT fields
- `match_mode` (string, optional, default "subset"): "subset" or "exact"
- `description` (string, optional): For debugging/documentation

### NBT Field Paths (Dot Notation)

**Examples**:
```json
"BlockEntityTag.roomCode"           → nbt.getCompound("BlockEntityTag").getString("roomCode")
"BlockEntityTag.importerExporterRates" → nbt.getCompound("BlockEntityTag").get("importerExporterRates")
"StoredEnchantments"                → nbt.get("StoredEnchantments")
"Upgrades.SpeedTier"                → nbt.getCompound("Upgrades").getInt("SpeedTier")
```

**Path Resolution** (see `NbtRequirement.getNestedTag()`):
1. Split path by `.` delimiter
2. Navigate through nested CompoundTags
3. Return final Tag (can be any type: IntTag, StringTag, ListTag, etc.)
4. If path broken, return null

---

## Implementation Details

### Phase 1: JSON Schema System ✅ Complete

**Files Created**:
1. `NbtRequirement.java` (168 lines)
   - Record class: `resourceId`, `nbtFields`, `matchMode`, `description`
   - `fromJson(JsonObject)`: Deserialize from JSON
   - `extractNbt(CompoundTag, List<String>)`: Filter NBT by field paths
   - `matches(CompoundTag, CompoundTag)`: Compare NBT (SUBSET or EXACT)
   - Pattern matching: SUBSET checks all required fields exist, EXACT checks full equality

2. `NbtRequirementRegistry.java` (161 lines)
   - Singleton pattern: `getInstance()`
   - Extends `SimplePreparableReloadListener<Map<String, NbtRequirement>>`
   - Loads during datapack reload: `prepare()` → `apply()`
   - Query API: `hasRequirement(String)`, `getRequirement(String)`
   - Logs: "Loaded X NBT requirements from datapacks"

3. `prefab.json` (9 lines)
   - Example schema for PreFab blocks
   - Tracks: `roomCode`, `importerExporterRates`, `prefabName`
   - Mode: `subset` (only these fields must match)

4. `README.md` (200+ lines)
   - Documentation for modpack developers
   - JSON schema specification
   - Examples and troubleshooting

**Registration**:
```java
// FPSCompress.java
@SubscribeEvent
public void onAddReloadListener(AddReloadListenerEvent event) {
    event.addListener(NbtRequirementRegistry.getInstance());
}
```

**SpotBugs Exclusions**:
- `EI_EXPOSE_REP` for `NbtRequirement.nbtFields()` - List.copyOf() in canonical constructor
- `BC_VACUOUS_INSTANCEOF` for `getNestedTag()` - Defensive programming
- `RCN_REDUNDANT_NULLCHECK` for `getNestedTag()` - Validates empty tag case
- `REC_CATCH_EXCEPTION` for registry - Resilient datapack loading

### Phase 2: BlueprintData Structure ✅ Complete

**Schema Evolution**:
- v1: `Map<String, Long>` for resources (ID → count)
- v2: `List<ResourceRequirement>` with optional NBT

**Changes to BlueprintData.java**:
```java
// Old (v1)
private final Map<String, Long> blockResources;
private final Map<String, Long> itemResources;

// New (v2)
private final List<ResourceRequirement> blockResources;
private final List<ResourceRequirement> itemResources;

// New record
public record ResourceRequirement(String id, long count, CompoundTag nbt) {
    // Canonical constructor does defensive copy
    public ResourceRequirement {
        nbt = nbt != null ? nbt.copy() : null;
    }
}
```

**Migration in `fromNBT()`**:
```java
int schemaVersion = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 1;

if (schemaVersion >= 2 && entry.contains("nbt")) {
    // v2 format: has optional NBT field
    itemResources.add(ResourceRequirement.fromNBT(entry));
} else {
    // v1 format: migrate with null NBT
    itemResources.add(new ResourceRequirement(id, count, null));
}
```

**API Changes**:
```java
// Old
public Map<String, Long> getBlockResources();
public Map<String, Long> getItemResources();

// New
public List<ResourceRequirement> getBlockResources();
public List<ResourceRequirement> getItemResources();
```

**FabricatorBlockEntity Update**:
```java
// Convert Map<String, Long> to List<ResourceRequirement>
List<ResourceRequirement> blockRequirements = new ArrayList<>();
for (Map.Entry<String, Long> entry : scannedBlocks.entrySet()) {
    blockRequirements.add(new ResourceRequirement(
        entry.getKey(),
        entry.getValue(),
        null  // Phase 3 will populate NBT
    ));
}
```

**SpotBugs Exclusions**:
- `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` for `ResourceRequirement.nbt()` - CompoundTag.copy() in canonical constructor

### Phase 3: Update Scanners (In Progress)

**Goal**: Modify `BlockScanner` and `InventoryScanner` to extract NBT when required.

**Changes to BlockScanner.java**:
```java
// Current return type
public static CompletableFuture<Map<String, Long>> scanBlocksAsync(...)

// New return type (Phase 3)
public static CompletableFuture<List<ScannedResource>> scanBlocksAsync(...)

// New record
public record ScannedResource(String id, CompoundTag nbt) {}

// Scanning logic
for (BlockPos pos : roomBounds) {
    BlockState state = level.getBlockState(pos);
    String blockId = getBlockId(state);
    
    // Check if NBT tracking required
    if (NbtRequirementRegistry.getInstance().hasRequirement(blockId)) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            CompoundTag fullNbt = be.saveWithoutMetadata(level.registryAccess());
            NbtRequirement req = NbtRequirementRegistry.getInstance()
                .getRequirement(blockId).orElseThrow();
            CompoundTag filteredNbt = NbtRequirement.extractNbt(fullNbt, req.nbtFields());
            
            scannedResources.add(new ScannedResource(blockId, filteredNbt));
        }
    } else {
        scannedResources.add(new ScannedResource(blockId, null));
    }
}
```

**Changes to InventoryScanner.java**:
```java
// Similar pattern, but extract from ItemStack
ItemStack stack = itemHandler.getStackInSlot(slot);
String itemId = getItemId(stack);

if (NbtRequirementRegistry.getInstance().hasRequirement(itemId)) {
    CompoundTag fullNbt = stack.getOrCreateTag();
    NbtRequirement req = NbtRequirementRegistry.getInstance()
        .getRequirement(itemId).orElseThrow();
    CompoundTag filteredNbt = NbtRequirement.extractNbt(fullNbt, req.nbtFields());
    
    scannedResources.add(new ScannedResource(itemId, filteredNbt));
} else {
    scannedResources.add(new ScannedResource(itemId, null));
}
```

**Grouping Strategy**:
After scanning, group by ID + NBT hash:
```java
Map<String, Long> grouped = new HashMap<>();
for (ScannedResource res : scannedResources) {
    String key = res.nbt() != null 
        ? res.id() + "_" + res.nbt().hashCode()
        : res.id();
    grouped.merge(key, 1L, Long::sum);
}
```

### Phase 4: Blueprint Integration (In Progress)

**Goal**: Update `FabricatorBlockEntity.createBlueprintFromScan()` to store NBT in blueprints.

**Changes**:
```java
// Convert scanned resources to ResourceRequirements
List<ResourceRequirement> blockRequirements = new ArrayList<>();
for (Map.Entry<String, ScannedResource> entry : scannedBlocks.entrySet()) {
    ScannedResource res = entry.getValue();
    blockRequirements.add(new ResourceRequirement(
        res.id(),
        entry.getValue(),  // count
        res.nbt()          // Now populated from scanner!
    ));
}
```

**Result**: Blueprints now contain NBT requirements for configured blocks/items.

---

## Key Design Decisions

### 1. Why JSON Instead of Code?

**Rejected**: Hardcode NBT requirements in `Config.java` or constants
- ❌ Requires mod rebuild for changes
- ❌ Modpack devs can't customize
- ❌ Verbose for complex NBT structures

**Chosen**: JSON schema files in datapacks
- ✅ Override via datapacks (no mod rebuild)
- ✅ Standard Minecraft pattern
- ✅ Clean separation of data and logic

### 2. Why Dot Notation for NBT Paths?

**Rejected**: Hardcode NBT extraction per item type
- ❌ Not extensible
- ❌ Couples schema to code

**Chosen**: Dot notation paths (e.g., `"BlockEntityTag.roomCode"`)
- ✅ Self-documenting
- ✅ Flexible for nested structures
- ✅ Similar to JSON path or XPath

### 3. Why SUBSET Match Mode by Default?

**SUBSET**: Only specified fields must match (default)
- ✅ Stable - insensitive to other NBT changes
- ✅ Focused - only validates essential fields
- ❌ Could miss important differences

**EXACT**: Full NBT must match
- ✅ Strictest validation
- ❌ Fragile - breaks on unrelated NBT changes (timestamps, UUIDs)
- ❌ Rarely needed

**Decision**: Default to SUBSET, allow EXACT for rare cases (e.g., exact enchantment books)

### 4. Why Schema Versioning in BlueprintData?

**Problem**: Changing blueprint format breaks old blueprints
**Solution**: Schema versions with migration

```java
// v1 blueprints load as v2 with null NBT
if (schemaVersion >= 2 && entry.contains("nbt")) {
    // v2 format
} else {
    // v1 format - migrate
}
```

**Future-proof**: Can add v3, v4, etc. without breaking compatibility

### 5. Why Record Classes?

**Records** (Java 16+):
- ✅ Immutable by default
- ✅ Automatic equals/hashCode/toString
- ✅ Compact syntax
- ✅ Pattern matching support (Java 21+)

**Used for**:
- `NbtRequirement`
- `BlueprintData.ResourceRequirement`
- Future: `ScannedResource`

**Canonical Constructor Pattern**:
```java
public record ResourceRequirement(String id, long count, CompoundTag nbt) {
    // Ensures immutability by defensive copy
    public ResourceRequirement {
        nbt = nbt != null ? nbt.copy() : null;
    }
}
```

---

## Testing Strategy

### Phase 1 Testing (JSON Loading)

**Test Case 1**: Load default prefab.json
```
1. Start game
2. Check logs: "Loaded 1 NBT requirements from datapacks"
3. Expected: NbtRequirementRegistry has "fpscompress:prefab_block"
```

**Test Case 2**: Override with datapack
```
1. Create datapack: `datapacks/test/data/fpscompress/nbt_requirements/blocks/prefab.json`
2. Change nbt_fields to ["BlockEntityTag.roomCode"]
3. Run /reload
4. Expected: Only roomCode tracked (not rates)
```

**Test Case 3**: Invalid JSON
```
1. Create malformed JSON (missing "resource_id")
2. Run /reload
3. Expected: Error logged, registry continues loading other files
```

### Phase 2 Testing (BlueprintData Migration)

**Test Case 1**: Create v2 blueprint programmatically
```java
List<ResourceRequirement> blocks = List.of(
    new ResourceRequirement("minecraft:stone", 64, null)
);
BlueprintData data = new BlueprintData(blocks, items, rates, 5, 5, 5, null);
CompoundTag nbt = data.toNBT();
assert nbt.getInt("schemaVersion") == 2;
```

**Test Case 2**: Load v1 blueprint (migration)
```
1. Create v1 blueprint item with /give command (no NBT field)
2. Break blueprint, hover with F3+H
3. Expected: Loads as v2 with null NBT requirements
```

**Test Case 3**: Round-trip v2 blueprint
```java
BlueprintData original = new BlueprintData(...);
CompoundTag nbt = original.toNBT();
BlueprintData loaded = BlueprintData.fromNBT(nbt);
assert original.equals(loaded); // Record automatic equals
```

### Phase 3-4 Testing (Scanning with NBT)

**Test Case 1**: Scan factory with PreFab
```
1. Build factory in CM room with chest + PreFab (CACHED, 10 iron/tick)
2. Insert PreFab into Fabricator
3. Trigger scan
4. Check logs: "Scanned PreFab with NBT: {roomCode: xyz, importerExporterRates: [...]}"
5. Verify blueprint contains NBT requirements
```

**Test Case 2**: Scan factory without NBT-required items
```
1. Build factory with only vanilla blocks (stone, furnace)
2. Scan
3. Expected: Blueprint has null NBT for all resources
```

**Test Case 3**: Scan mixed factory
```
1. Build factory with 64 stone + 1 PreFab
2. Scan
3. Expected: Stone has null NBT, PreFab has roomCode + rates NBT
```

---

## Common Patterns

### Adding a New NBT Requirement

**Example**: Track enchantments on enchanted books

1. **Create JSON schema**:
```json
// data/minecraft/nbt_requirements/items/enchanted_book.json
{
  "resource_id": "minecraft:enchanted_book",
  "nbt_fields": [
    "StoredEnchantments"
  ],
  "match_mode": "subset",
  "description": "Enchanted books must have matching enchantments"
}
```

2. **Test loading**:
```
/reload
Check logs: "Loaded 2 NBT requirements" (prefab + book)
```

3. **Verify scanning** (Phase 3+):
- Scan factory with enchanted book
- Blueprint should contain `StoredEnchantments` NBT

4. **Verify printing** (Phase 6):
- Print blueprint, insert wrong enchanted book
- Should reject (NBT mismatch)

### Debugging NBT Extraction

**Issue**: NBT field not found during scanning

**Debug Steps**:
1. Check item NBT with F3+H tooltip or `/data get`
2. Verify path in JSON (case-sensitive!)
3. Add logging in `NbtRequirement.extractNbt()`:
```java
Tag value = getNestedTag(fullNbt, fieldPath);
if (value == null) {
    LOGGER.warn("NBT field not found: {} in {}", fieldPath, fullNbt);
}
```

4. Check `NbtRequirement.getNestedTag()` navigation:
- Path: `"BlockEntityTag.roomCode"`
- Navigation: `fullNbt.getCompound("BlockEntityTag").getString("roomCode")`

### Handling Complex NBT

**Example**: Track nested structure

```json
{
  "resource_id": "examplemod:complex_machine",
  "nbt_fields": [
    "BlockEntityTag.Config.Tier",
    "BlockEntityTag.Config.Upgrades",
    "BlockEntityTag.EnergyStored"
  ],
  "match_mode": "subset"
}
```

**NBT Structure**:
```
{
  BlockEntityTag: {
    Config: {
      Tier: 3,
      Upgrades: [...list...]
    },
    EnergyStored: 1000000
  }
}
```

**Extracted NBT** (stored in blueprint):
```
{
  BlockEntityTag: {
    Config: {
      Tier: 3,
      Upgrades: [...]
    },
    EnergyStored: 1000000
  }
}
```

---

## Performance Considerations

### Registry Lookup

**Pattern**: Cache registry lookups in hot paths

```java
// Bad: Query registry every iteration
for (ItemStack stack : items) {
    if (NbtRequirementRegistry.getInstance().hasRequirement(getId(stack))) {
        // ...
    }
}

// Good: Batch query or cache
Map<String, NbtRequirement> cache = new HashMap<>();
for (ItemStack stack : items) {
    String id = getId(stack);
    NbtRequirement req = cache.computeIfAbsent(id,
        k -> NbtRequirementRegistry.getInstance().getRequirement(k).orElse(null)
    );
}
```

### NBT Extraction Cost

**NBT operations are relatively cheap**:
- `CompoundTag.getCompound()`: O(1) hash lookup
- `CompoundTag.copy()`: O(n) where n = tag size
- Typical PreFab NBT: ~1KB (negligible)

**Avoid**:
- Copying full NBT multiple times
- Deep cloning when shallow clone suffices

**Current Implementation** (efficient):
- Extract once during scanning
- Store filtered copy in blueprint
- Canonical constructor does single defensive copy

### Datapack Reload

**Pattern**: Registry reload is async-safe

```java
// prepare() runs on worker thread
protected Map<String, NbtRequirement> prepare(...) {
    // Parse JSON, build map
    // No access to game state
}

// apply() runs on main thread
protected void apply(Map<String, NbtRequirement> prepared, ...) {
    this.requirements = prepared;  // Atomic swap
}
```

**Thread Safety**:
- Preparation (JSON parsing) is off main thread
- Application (map swap) is atomic on main thread
- Query methods are read-only (safe from async scanners)

---

## Future Enhancements

### Phase 5: Printing Validation (Not Yet Implemented)

**Goal**: Check NBT matches during printing

**Pseudocode**:
```java
boolean validateResources(Blueprint blueprint, Fabricator fabricator) {
    for (ResourceRequirement req : blueprint.getItemResources()) {
        ItemStack available = findInFabricator(req.id());
        
        if (req.nbt() != null) {
            // NBT required - extract and compare
            NbtRequirement nbtReq = NbtRequirementRegistry.getInstance()
                .getRequirement(req.id()).orElseThrow();
            
            CompoundTag availableNbt = NbtRequirement.extractNbt(
                available.getOrCreateTag(),
                nbtReq.nbtFields()
            );
            
            if (!nbtReq.matches(req.nbt(), availableNbt)) {
                return false; // NBT mismatch
            }
        }
    }
    return true;
}
```

### GUI Display (Phase 5+)

**Show NBT requirements in GUI**:
```
Required Resources:
 ✓ 64x Stone
 ✗ 1x PreFab (roomCode: abc123, 10 iron/tick)  ← NBT shown
```

**Tooltip on hover**:
```
PreFab Requirements:
  - roomCode: abc123
  - Rate: 10.0 iron_ingot/tick (output)
  - Name: "Iron Factory v1"
```

### Config Options

**Potential additions to `Config.java`**:
```java
// Disable NBT validation (testing/creative mode)
ModConfigSpec.BooleanValue disableNbtValidation;

// Verbosity for NBT logging
ModConfigSpec.EnumValue<NbtLogLevel> nbtLogLevel;

// Allow partial NBT matches (fuzzy matching)
ModConfigSpec.DoubleValue nbtMatchThreshold; // 0.0-1.0
```

### Advanced Match Modes

**FUZZY**: Allow small differences
```java
public enum MatchMode {
    SUBSET,   // Current: Only specified fields must match
    EXACT,    // Current: Full NBT must match
    FUZZY,    // Future: Allow threshold-based matching
    RANGE     // Future: Numeric fields within range (e.g., energy 90%-110%)
}
```

---

## Troubleshooting

### Issue: "NBT requirement not loaded"

**Symptoms**: Scanner doesn't track NBT for configured item

**Checks**:
1. JSON file in correct location: `data/<namespace>/nbt_requirements/{blocks,items}/`
2. JSON syntax valid (use validator)
3. `resource_id` matches registry exactly (case-sensitive)
4. Run `/reload` after adding file
5. Check logs: "Loaded X NBT requirements"

**Debug Command** (add in Phase 5):
```
/fps_dev2 nbt-requirements list
/fps_dev2 nbt-requirements get fpscompress:prefab_block
```

### Issue: "NBT field not found during scanning"

**Symptoms**: Blueprint has null NBT despite JSON schema

**Checks**:
1. NBT path case-sensitive: `BlockEntityTag` not `blockentitytag`
2. Path exists on scanned item: Use `/data get` or F3+H
3. Path navigation correct: `A.B.C` requires nested structure
4. Block has BlockEntity: Blocks without TileEntity have no NBT

**Example Debug**:
```java
// Add to InventoryScanner.scanBlockEntity()
CompoundTag fullNbt = stack.getOrCreateTag();
LOGGER.debug("Scanning {}: {}", itemId, fullNbt);

NbtRequirement req = NbtRequirementRegistry.getInstance()
    .getRequirement(itemId).orElse(null);
if (req != null) {
    CompoundTag filtered = NbtRequirement.extractNbt(fullNbt, req.nbtFields());
    LOGGER.debug("Filtered NBT: {}", filtered);
}
```

### Issue: "Too many unique item types in blueprint"

**Symptoms**: Blueprint has separate entries for identical items with minor NBT differences

**Cause**: Tracking non-essential NBT fields (timestamps, UUIDs, etc.)

**Fix**: Update JSON to only track essential fields
```json
// Bad: Tracks everything
"nbt_fields": ["BlockEntityTag"]

// Good: Tracks only rates
"nbt_fields": ["BlockEntityTag.importerExporterRates"]
```

---

## Code References

**Key Files**:
- `NbtRequirement.java` - NBT extraction and matching logic
- `NbtRequirementRegistry.java` - Singleton registry, datapack loading
- `BlueprintData.java` - Blueprint storage with NBT (schema v2)
- `FabricatorBlockEntity.java:createBlueprintFromScan()` - Blueprint creation
- `BlockScanner.java` (Phase 3) - Block NBT extraction
- `InventoryScanner.java` (Phase 3) - Item NBT extraction

**Related Systems**:
- `PrefabNBTSerializer.java` - PreFab NBT serialization (reference pattern)
- `DataComponents.BLOCK_ENTITY_DATA` - How items store BlockEntity NBT
- `CustomData.copyTag()` - Extract NBT from ItemStack components

**Testing Files**:
- `data/fpscompress/nbt_requirements/blocks/prefab.json` - Example schema
- `data/fpscompress/nbt_requirements/README.md` - User documentation
- `config/spotbugs/spotbugs-excludes.xml` - Linter exclusions

---

## Credits

**Designed & Implemented**: 2026-05-25  
**Pattern Inspired By**: Minecraft datapack system, PrefabNBTSerializer schema versioning  
**Related Feature**: PreFab Blueprint System (Scanner & Printer)

For questions or issues, see main project documentation or create GitHub issue.
