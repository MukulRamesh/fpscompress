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
        *.json     - NBT requirements for placed blocks
      items/
        *.json     - NBT requirements for inventory items
```

## JSON Schema Format

Each JSON file defines matching rules for a single block or item type. Rules use a `match` map where each key is an NBT path and each value is a matching rule:

```json
{
  "resource_id": "modid:item_name",
  "match": {
    "path.to.field": "strategy",
    "path.to.nested.field": {
      "strategy": "strategy_name",
      "ignore": ["transient_key"],
      "min": 0.0,
      "max": 100.0
    }
  }
}
```

### Fields

**`resource_id`** (required, string):
- Namespaced ID of the block or item (e.g., `"fpscompress:prefab_machine"`)
- Must match the registry ID exactly (case-sensitive)

**`match`** (required, object):
- Map of NBT path → matching rule
- Each path uses dot notation (e.g., `"BlockEntityTag.roomCode"`)
- Use `*` as a wildcard for list element paths (e.g., `"list.*.subfield"`)
- Each rule can be a **string shorthand** or an **object** with options

#### Match Rule — String Shorthand

For simple cases where defaults suffice:

```json
"path.to.field": "exact"
```

#### Match Rule — Object Form

```json
"path.to.field": {
  "strategy": "list_subset",
  "ignore": ["uuid", "timestamp"],
  "min": 0.0,
  "max": 100.0
}
```

- **`strategy`** (required, string): One of `"exact"`, `"subset"`, `"list_subset"`, `"range"`
- **`ignore`** (optional, array of strings): Sub-keys to skip during comparison (transient fields like UUIDs, timestamps)
- **`min`** (optional, number): Lower bound — only used with `"range"` strategy
- **`max`** (optional, number): Upper bound — only used with `"range"` strategy

### Match Strategies

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `exact` | Values must match exactly (`Tag.equals()`) | Simple fields (strings, numbers) |
| `subset` | Required sub-keys must exist in available; extra keys are OK | Compound tags where only specific sub-fields matter |
| `list_subset` | Every element in required list must find a match in available (order-independent) | Lists where order doesn't matter; use `ignore` to skip volatile fields per element |
| `range` | Numeric value must fall within [`min`, `max`] | Energy levels, durability, progress values |

**Default strategies** (applied when no explicit rule exists for a path):
- CompoundTag children: `subset`
- Primitive tag children: `exact`

### The `ignore` Field

Use `ignore` to skip transient keys that differ between otherwise-identical items:

```json
"importerExporterRates": {
  "strategy": "list_subset",
  "ignore": ["uuid"]
}
```

This says: compare each element in the `importerExporterRates` list, but strip the `uuid` field before comparing. UUIDs are unique per Importer/Exporter and differ between copies — ignoring them allows carbon copies to match.

**Common candidates for `ignore`**: `uuid`, `timestamp`, `x`, `y`, `z` (block positions).

## How It Works

### During Scanning

1. Scanner encounters a block/item in the factory
2. Checks if `NbtRequirementRegistry.hasRequirement(resourceId)` returns true
3. If yes:
   - Extracts full NBT from block entity or ItemStack
   - Gets tracked field names via `getTrackedFields()` (top-level fields from match paths)
   - Filters NBT to only include those fields via `NbtRequirement.extractNbt()`
   - Stores filtered NBT in the blueprint (schema v2)
4. If no:
   - Only stores resource ID and count (null NBT = any item of correct type accepted)

### During Printing

1. Player inserts resources into Fabricator
2. Fabricator checks if the blueprint's ResourceRequirement has NBT
3. If yes:
   - Gets `NbtRequirement` from registry for this resource ID
   - Extracts NBT from player's items using `getTrackedFields()`
   - Calls `nbtRequirement.matches(requiredNbt, availableNbt)` — recursive strategy-based matching
   - Only accepts items where NBT matches per the declared strategies
   - Mismatches: item returned to player inventory or dropped
4. If no:
   - Accepts any item of the correct type

## Examples

### Example 1: PreFab Blocks (shipped with the mod)

**Why**: PreFabs have cached production rates. A PreFab producing 100 diamonds/tick should not be interchangeable with one producing 1 dirt/tick.

**File**: `blocks/prefab.json`
```json
{
  "resource_id": "fpscompress:prefab_machine",
  "match": {
    "importerExporterRates": {
      "strategy": "list_subset",
      "ignore": ["uuid"]
    },
    "importerExporterRates.*.rates": "list_subset"
  }
}
```

**Result**: Blueprints require PreFabs with matching `importerExporterRates`. UUIDs are ignored (carbon copies get new UUIDs for their own Importers/Exporters). The inner `rates` sub-lists also use list_subset matching.

### Example 2: Enchanted Books (hypothetical — add via datapack)

**Why**: Enchanted books with different enchantments are functionally different items.

**File**: `items/enchanted_book.json`
```json
{
  "resource_id": "minecraft:enchanted_book",
  "match": {
    "StoredEnchantments": "list_subset"
  }
}
```

**Result**: Blueprint requires books with the exact same enchantments. Every enchantment in the blueprint's book must match an enchantment in the provided book.

### Example 3: Modded Machine with Upgrades (hypothetical — add via datapack)

**Why**: A machine with 8x speed upgrades is different from one with no upgrades.

**File**: `blocks/super_furnace.json`
```json
{
  "resource_id": "examplemod:super_furnace",
  "match": {
    "BlockEntityTag.Upgrades.SpeedTier": "exact",
    "BlockEntityTag.Upgrades.EfficiencyTier": "exact",
    "BlockEntityTag.EnergyStored": {
      "strategy": "range",
      "min": 0.0,
      "max": 1000000.0
    }
  }
}
```

**Result**: Blueprint requires matching speed/efficiency upgrade tiers (exact) but accepts any energy level within the machine's capacity (range). The `Upgrades` key uses the default `subset` strategy for its unlisted sub-keys.

## For Modpack Developers

### Adding Custom Requirements

1. Create a datapack: `datapacks/my_nbt_requirements/`
2. Add directory: `data/your_modid/nbt_requirements/blocks/` (or `items/`)
3. Create JSON file: `your_item.json` following the format above
4. Reload datapacks: `/reload` command

Your NBT requirements will override or supplement FPSCompress defaults.

### Overriding Defaults

Datapacks load after mod resources. If your datapack defines a requirement for the same `resource_id`, your version takes precedence (last-loaded wins). This lets you:

- **Relax requirements**: Remove paths from the `match` map
- **Tighten requirements**: Add additional paths to track
- **Disable tracking**: Set `"match": {}` (item tracked by type only)

### Finding NBT Paths

Use F3+H to enable advanced tooltips, then hover over items to see NBT data. For blocks, place them and use commands:

```
/data get block ~ ~ ~
```

This shows the block's NBT structure. Use dot notation to reference nested fields:
- `"roomCode"` → top-level string
- `"BlockEntityTag.roomCode"` → nested under BlockEntityTag
- `"importerExporterRates.*.rates"` → each element's `rates` field in a list

### Testing

1. Create a test blueprint with your item
2. Check logs for: `"Loaded NBT requirement for <your_id>"`
3. Scan a factory containing your item
4. Right-click the Blueprint to view stored NBT data in chat
5. Test printing — insert wrong-NBT item to confirm rejection

### Troubleshooting

**"NBT requirement not loaded"**:
- Check JSON syntax (use a JSON validator)
- Verify file is in correct directory (`nbt_requirements/blocks/` or `nbt_requirements/items/`)
- Check `resource_id` matches registry name exactly (case-sensitive)
- Run `/reload` after adding files

**"NBT not extracted during scanning"**:
- Verify NBT paths are correct (case-sensitive!)
- Check if NBT exists on the scanned block/item — use F3+H or `/data get`
- Blocks without BlockEntities have no NBT

**"Items wrongly rejected during printing"**:
- Are you tracking transient fields (UUIDs, timestamps)? Add them to `ignore`
- Using `exact` when `list_subset` or `subset` would be more appropriate?
- Check path nesting — are you reaching the right depth?

## Performance Considerations

- **NBT extraction is fast** — Tracking a few dozen fields has negligible cost
- **Prefer `list_subset` / `subset`** — Avoid `exact` unless necessary (more fragile)
- **Track only essential fields** — Don't include decorative or transient data; use `ignore` for per-element exclusions
- **Test with large factories** — Ensure scanning remains <1 second per room

## Schema Versioning

This system uses FPSCompress Blueprint Schema v2:
- v1: No NBT tracking (legacy)
- v2: NBT tracking with backward compatibility (this system)

Old v1 blueprints will load as v2 with null NBT requirements (accept any item type).

## Technical Details

**Classes**:
- `NbtRequirement.java` — Record with `resourceId` + `rules` (`Map<String, MatchRule>`). Inner `MatchRule` record with `strategy`, `ignore`, `min`, `max`. Matching engine: `matchNode()`, `matchSubset()`, `matchListSubset()`, `matchRange()`.
- `NbtRequirementRegistry.java` — Singleton, extends `SimplePreparableReloadListener`. Loads JSON from `nbt_requirements/blocks/` and `nbt_requirements/items/` across all namespaces during datapack reload.
- `BlueprintData.java` — Stores `ResourceRequirement` records (id + count + optional NBT) in schema v2 format.
- `FabricatorBlockEntity.java` — Uses registry for printing validation via `checkRequiredResources()` and `validateAndEjectNbtMismatches()`.

**Loading**: Uses Minecraft's `SimplePreparableReloadListener` to load during datapack reload events. `prepare()` parses JSON on worker thread; `apply()` atomically swaps the map on main thread.

**Thread Safety**: Requirements are immutable after loading (`Collections.unmodifiableMap()`). Safe to query from async scanning threads.

## Credits

NBT Requirements system designed for FPSCompress blueprint scanning.
For support, see: https://github.com/mukulramesh/fpscompress/issues
