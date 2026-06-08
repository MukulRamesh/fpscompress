# NBT Requirements System

This directory contains JSON schemas that define which blocks and items require NBT data to be tracked during blueprint scanning.

## Purpose

When scanning a factory for blueprints, most blocks/items only need their type and count (e.g., "64x minecraft:stone"). However, some blocks/items have important NBT data that affects their function. For example:

- **PreFabs** have cached production rates - a PreFab producing 100 iron/tick is NOT the same as one producing 10 iron/tick
- **Enchanted tools** have different enchantments - Efficiency V pickaxe vs unenchanted pickaxe
- **Configured machines** from other mods may have speed/efficiency upgrades stored in NBT

Without NBT tracking, blueprints would accept any item of the correct type, even if functionally different.

## Directory Structure

```
data/
  <namespace>/
    nbt_requirements/
      blocks/
        *.json     - NBT requirements for blocks
      items/
        *.json     - NBT requirements for items
```

## JSON Schema Format

Each JSON file defines NBT requirements for a single block or item type:

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

### Fields

**`resource_id`** (required, string):
- Namespaced ID of the block or item (e.g., `"minecraft:enchanted_book"`)
- Must match the registry ID exactly

**`nbt_fields`** (required, array of strings):
- List of NBT field paths to extract and track
- Uses dot notation for nested tags (e.g., `"BlockEntityTag.roomCode"`)
- Only these fields will be stored in blueprints and checked during printing
- Empty array is valid but pointless (nothing will be tracked)

**`match_mode`** (optional, string, default: `"subset"`):
- `"subset"`: Only the specified `nbt_fields` must match (recommended)
- `"exact"`: Full NBT data must match exactly (rarely needed)
- Most use cases should use `"subset"` to avoid fragile comparisons

**`description`** (optional, string):
- Human-readable explanation of why NBT tracking is needed
- Helps modpack developers understand the requirement
- Not used by code, purely documentation

## How It Works

### During Scanning

1. Scanner encounters a block/item in the factory
2. Checks if `NbtRequirementRegistry.hasRequirement(resourceId)` returns true
3. If yes:
   - Extracts full NBT from block entity or ItemStack
   - Filters NBT to only include paths listed in `nbt_fields`
   - Stores filtered NBT in the blueprint
4. If no:
   - Only stores resource ID and count (current behavior)

### During Printing (Phase 6)

1. Player inserts resources into Fabricator
2. Fabricator checks if blueprint has NBT requirements
3. If yes:
   - Extracts NBT from player's items using same `nbt_fields` paths
   - Compares extracted NBT to blueprint requirements
   - Only accepts items where NBT matches
4. If no:
   - Accepts any item of the correct type (current behavior)

## Examples

### Example 1: PreFab Blocks

**Why**: PreFabs have cached production rates. A PreFab producing 100 diamonds/tick should not be interchangeable with one producing 1 dirt/tick.

**File**: `blocks/prefab.json`
```json
{
  "resource_id": "fpscompress:prefab_block",
  "nbt_fields": [
    "BlockEntityTag.roomCode",
    "BlockEntityTag.importerExporterRates",
    "BlockEntityTag.prefabName"
  ],
  "match_mode": "subset",
  "description": "PreFabs require matching cached rates"
}
```

**Result**: Blueprints will require PreFabs with the same `roomCode` and `importerExporterRates`. Carbon copies (`cc_*`) and fake PreFabs (`fake_*`) with matching rates will be accepted.

### Example 2: Enchanted Books (Hypothetical)

**Why**: Enchanted books with different enchantments are functionally different items.

**File**: `items/enchanted_book.json`
```json
{
  "resource_id": "minecraft:enchanted_book",
  "nbt_fields": [
    "StoredEnchantments"
  ],
  "match_mode": "subset",
  "description": "Enchanted books must have matching enchantments"
}
```

**Result**: Blueprint requires books with the exact same enchantments.

### Example 3: Modded Machine with Upgrades (Hypothetical)

**Why**: A machine with 8x speed upgrades is different from one with no upgrades.

**File**: `blocks/super_smelter.json`
```json
{
  "resource_id": "examplemod:super_smelter",
  "nbt_fields": [
    "BlockEntityTag.Upgrades",
    "BlockEntityTag.EnergyCapacity"
  ],
  "match_mode": "subset",
  "description": "Super Smelter upgrades affect performance"
}
```

**Result**: Blueprint requires super smelters with matching upgrade configuration.

## For Modpack Developers

### Adding Custom Requirements

1. Create a datapack: `datapacks/my_nbt_requirements/`
2. Add directory: `data/your_modid/nbt_requirements/blocks/` (or `items/`)
3. Create JSON file: `your_item.json`
4. Reload datapacks: `/reload` command

Your NBT requirements will override or supplement FPSCompress defaults.

### Finding NBT Paths

Use F3+H to enable advanced tooltips, then hover over items to see NBT data. For blocks, place them and use commands:

```
/data get block ~ ~ ~
```

This shows the block's NBT structure. Use dot notation to reference nested fields.

### Testing

1. Create a test blueprint with your item
2. Check logs for: `"Loaded NBT requirement for <your_id>"`
3. Scan a factory containing your item
4. Check logs for: `"Scanned <your_id> with NBT: {...}"`
5. Verify blueprint contains the correct NBT fields

### Troubleshooting

**"NBT requirement not loaded"**:
- Check JSON syntax (use a JSON validator)
- Verify file is in correct directory
- Check `resource_id` matches registry name exactly
- Run `/reload` after adding files

**"NBT fields not found during scanning"**:
- Verify NBT paths are correct (case-sensitive!)
- Check if NBT exists on the scanned block/item
- Use `/data get` commands to inspect actual NBT structure

**"Too many/too few items required during printing"**:
- Check `match_mode` - use `"subset"` not `"exact"`
- Verify `nbt_fields` only includes essential fields
- Avoid tracking timestamps, UUIDs, or other transient data

## Performance Considerations

- **NBT extraction is fast** - Don't worry about tracking a few dozen fields
- **Use `subset` mode** - Avoid `exact` unless necessary (more fragile)
- **Track only essential fields** - Don't include decorative or transient data
- **Test with large factories** - Ensure scanning remains <1 second per room

## Schema Versioning

This system uses FPSCompress Blueprint Schema v2:
- v1: No NBT tracking (legacy)
- v2: NBT tracking with backward compatibility

Old v1 blueprints will load as v2 with null NBT requirements (accept any item type).

## Technical Details

**Classes**:
- `NbtRequirement.java` - Record class for JSON deserialization
- `NbtRequirementRegistry.java` - Singleton registry, loads during datapack reload
- `BlueprintData.java` - Stores NBT requirements in schema v2 format

**Loading**: Uses Minecraft's `SimplePreparableReloadListener` to load during datapack reload events.

**Thread Safety**: Requirements are immutable after loading. Safe to query from async scanning threads.

## Credits

NBT Requirements system designed for FPSCompress blueprint scanning.
For support, see: https://github.com/your-repo/issues
