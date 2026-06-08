# Blueprint System

Blueprints let you scan a PreFab's factory configuration and print identical carbon-copy PreFabs. The Fabricator block handles both scanning and printing, with an NBT-aware system that ensures functionally-identical copies.

## Overview

The Blueprint system has three parts:

1. **Fabricator Block** — Scans PreFabs and prints Blueprints
2. **PreFab Blueprint Item** — Stores scanned factory data (production rates, resource costs, NBT)
3. **NBT-Aware Scanning** — Data pack-driven system for tracking essential NBT fields

### Why Blueprints?

Without Blueprints, every factory must be built by hand. Blueprints let you:

- **Duplicate factories** — Scan a working factory, print exact copies
- **Share factories** — Trade Blueprint items with other players
- **Archive designs** — Store factory layouts as items
- **Scale production** — Print multiple copies of proven designs

## Fabricator Block

The Fabricator is a block that scans PreFabs into Blueprints and prints new PreFabs from Blueprints.

### GUI Layout

```
┌──────────────────────────────────┐
│          Fabricator              │
│                                  │
│  [Input Slot]  →→→  [Output]    │
│                                  │
│  [Resource Slots 1-27]          │
│  ┌──┐ ┌──┐ ┌──┐     ┌──┐      │
│  │  │ │  │ │  │ ... │  │      │
│  └──┘ └──┘ └──┘     └──┘      │
│                                  │
│  [Scan Button]  [Print Button]  │
└──────────────────────────────────┘
```

**Input Slot** (top-left): Place a PreFab item or Blueprint item here.

**Output Slot** (top-right): Completed Blueprints (from scanning) or new PreFabs (from printing) appear here. Output-only — you cannot place items here.

**Resource Slots** (27 slots): During printing, place the required blocks and items here. Each slot shows:
- **Green background** — Resource requirement satisfied (correct item + NBT match if required)
- **Yellow blinking** — Correct item but insufficient count
- **Red blinking** — Wrong item or missing NBT
- **Ghost item** — Empty slot shows what resource is needed

**Scan Button**: Scans the PreFab in the input slot, creating a Blueprint in the output slot.

**Print Button**: Prints a new PreFab from the Blueprint in the input slot. Requires resources in the resource slots.

**Progress Arrow**: Between input and output slots. Fills during scanning (pulse animation) and printing (proportional fill).

### How Scanning Works

1. Place a **PreFab item** (in CACHED state with production data) in the input slot
2. The Fabricator validates the PreFab:
   - Must be in CACHED state (has measured production rates)
   - Must not contain blacklisted blocks (see [PreFab System](PreFab-System#block-blacklist))
3. Click **Scan** (or the scan happens automatically)
4. The Fabricator performs an **async room scan**:
   - Counts all blocks and items inside the PreFab's CM room
   - Extracts NBT data for NBT-required blocks/items (see NBT-Aware Scanning below)
   - Reads cached production rates from the PreFab
5. The result is written to a **PreFab Blueprint** item in the output slot

**Scan caching**: After the first scan, results are cached in the PreFab's NBT. Re-inserting the same PreFab uses cached data instantly — no repeat async scan needed. The cache clears when the PreFab enters BUILDING state.

### How Printing Works

1. Place a **PreFab Blueprint** in the input slot
2. The resource slots populate with **ghost items** showing required resources
3. Place the required blocks and items in the resource slots
4. When all resources are satisfied (correct item + count + NBT), printing starts automatically
5. A progress arrow fills over `blueprintPrintTicks` (default: 20 ticks = 1 second)
6. A new **carbon copy PreFab** appears in the output slot:
   - Same production rates as the original
   - Same room code (stacks with other copies)
   - Named "CC of <original name>"

**Stacking**: Multiple copies of the same Blueprint stack up to 64. PreFabs with matching room codes also stack.

### Scan State Machine

The Fabricator cycles through these states:

| State | Description |
|-------|-------------|
| **Idle** (0) | No active scan or print |
| **Scanning** (1) | Async room scan in progress |
| **Resource Starved** (2) | Blueprint loaded but resources missing |
| **Ready to Print** (3) | All resources satisfied, waiting to print |
| **Printing** (4) | Auto-printing in progress |

### Config Options

In `fpscompress-server.toml`:

```toml
[blueprint]
    # Ticks to complete a print (default: 20 = 1 second)
    blueprintPrintTicks = 20

    # Whether the PreFab item is consumed when scanning (default: false)
    prefabConsumedOnScan = false
```

When `prefabConsumedOnScan` is `false` (default), the PreFab stays in the input slot after scanning — you can create multiple Blueprints from the same PreFab without re-inserting it.

---

## NBT-Aware Scanning

### Why NBT Matters

Most blocks and items only need their **type** and **count** in a blueprint (e.g., "64× Stone"). But some items are **functionally different** even when they share the same item ID — their NBT data determines what they actually do.

**Example: PreFab Blocks**

A PreFab producing 100 diamonds/tick and a PreFab producing 1 dirt/tick are both `fpscompress:prefab_machine` items. Without NBT tracking, a blueprint would accept either one interchangeably — but their cached production rates are completely different.

NBT-aware scanning ensures blueprints only accept items with matching NBT for the fields that matter.

### How It Works

1. **JSON schemas** define which blocks/items have important NBT fields (see Data Pack Configuration below)
2. **During scanning**, the Fabricator checks each block/item against the NBT registry
3. If a match is found, the specified NBT fields are extracted and stored in the blueprint
4. **During printing**, the Fabricator validates that provided resources match both the item ID **and** the required NBT fields
5. Items with wrong or missing NBT are automatically **rejected** (returned to player inventory or dropped)

### What Gets Tracked

By default, FPSCompress ships with one NBT requirement:

| Resource | Fields Tracked | Why |
|----------|---------------|-----|
| `fpscompress:prefab_machine` | `importerExporterRates` (list subset, ignoring UUIDs) | Ensures printed PreFabs have matching cached production rates |

You can add more via datapacks (see below).

---

## Data Pack-Driven Configuration

NBT requirements are configured through **JSON files in datapacks**. This means modpack developers can add, override, or remove NBT tracking without modifying the mod.

### Directory Structure

```
data/
  <namespace>/
    nbt_requirements/
      blocks/
        *.json     ← NBT requirements for placed blocks
      items/
        *.json     ← NBT requirements for inventory items
```

FPSCompress ships with:
```
data/fpscompress/nbt_requirements/blocks/prefab.json
```

### JSON Format

Each file defines NBT matching rules for a single resource:

```json
{
  "resource_id": "modid:item_or_block_name",
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

**`resource_id`** (required): The namespaced registry ID of the block or item.

**`match`** (required): A map of NBT paths to matching rules. Each path uses **dot notation** (e.g., `"BlockEntityTag.roomCode"`). Use `*` as a wildcard for list element paths (e.g., `"importerExporterRates.*.rates"`).

#### Match Rule Formats

Each match value can be a **shorthand string** or an **object**:

**String shorthand** (simple cases):
```json
"path.to.field": "exact"
```

**Object form** (with options):
```json
"path.to.field": {
  "strategy": "list_subset",
  "ignore": ["uuid", "timestamp"],
  "min": 0.0,
  "max": 100.0
}
```

### Match Strategies

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `exact` | Values must match exactly | Simple fields (strings, numbers) |
| `subset` | Required fields must exist in available; extra fields in available are OK | Compound tags where you care about specific sub-fields |
| `list_subset` | Every element in the required list must have a match in the available list (order-independent) | Lists of items/rates where order doesn't matter |
| `range` | Numeric value must fall within `[min, max]` | Energy levels, durability, progress values |

**Default behavior**: If no match rule is specified for a path, compound tags use `subset` and primitive tags use `exact`.

### The `ignore` Field

Use `ignore` to skip **transient** or **volatile** keys that differ between otherwise-identical items:

```json
{
  "resource_id": "fpscompress:prefab_machine",
  "match": {
    "importerExporterRates": {
      "strategy": "list_subset",
      "ignore": ["uuid"]
    }
  }
}
```

This says: "Compare `importerExporterRates` lists element-by-element, but skip the `uuid` field inside each element." UUIDs are assigned per-Importer/Exporter and differ between copies — ignoring them allows carbon copies to match.

Common things to ignore:
- `uuid` — Unique identifiers that differ per instance
- `timestamp` — Creation/modification times
- `x`, `y`, `z` — Block positions (differ between copies)

### Real Examples

**Example 1: PreFab Block** (shipped with the mod)

File: `data/fpscompress/nbt_requirements/blocks/prefab.json`

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

This ensures:
- The `importerExporterRates` list in a printed PreFab must match the blueprint's rates element-by-element
- UUIDs are ignored (carbon copies get new UUIDs for their own Importers/Exporters)
- The inner `rates` sub-list within each entry also uses `list_subset` matching

**Example 2: Enchanted Book** (hypothetical — add via datapack)

File: `data/minecraft/nbt_requirements/items/enchanted_book.json`

```json
{
  "resource_id": "minecraft:enchanted_book",
  "match": {
    "StoredEnchantments": "list_subset"
  }
}
```

Ensures a blueprint requiring an Efficiency V book won't accept a Sharpness III book.

**Example 3: Machine with Upgrades** (hypothetical — add via datapack)

File: `data/examplemod/nbt_requirements/blocks/super_furnace.json`

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

Ensures blueprints require matching speed/efficiency upgrade tiers but accepts any energy level (as long as it's within the machine's capacity).

---

## For Modpack Developers

### Adding Custom NBT Requirements

1. **Create a datapack** (or add to your existing datapack):
   ```
   datapacks/my_nbt_config/
   └── data/
       └── <your_namespace>/
           └── nbt_requirements/
               ├── blocks/
               │   └── your_block.json
               └── items/
                   └── your_item.json
   ```

2. **Write the JSON file** following the format above.

3. **Apply the datapack** — either:
   - Place it in the world's `datapacks/` folder
   - Use `/reload` to hot-reload without restarting

4. **Verify loading** — Check the game log for:
   ```
   [NbtRequirementRegistry] Loaded X NBT requirements from datapacks
   ```

### Overriding Defaults

Datapacks load **after** mod resources. If your datapack defines a requirement for the same `resource_id` as a mod default, your version takes precedence (last-loaded wins). This lets you:

- **Relax requirements** — Remove fields from the `match` map
- **Tighten requirements** — Add additional fields to track
- **Disable tracking** — Create an empty `"match": {}` (item is tracked by type only)

### Finding NBT Paths

Use in-game tools to inspect NBT structure:

1. Press **F3+H** to enable advanced tooltips — hover over items to see NBT
2. For blocks, use commands:
   ```
   /data get block ~ ~ ~
   ```
3. Navigate nested NBT using dot notation: `BlockEntityTag.Config.Upgrades`

**Tip**: NBT paths are **case-sensitive**. `BlockEntityTag` ≠ `blockentitytag`.

### Testing Your Configuration

1. Add your JSON file to a datapack
2. Run `/reload`
3. Check the log for `Loaded NBT requirement for <your_id>`
4. Scan a factory containing your block/item
5. Verify the blueprint contains correct NBT (right-click Blueprint to view in chat)
6. Test printing — try inserting an item with wrong NBT to confirm rejection

### Troubleshooting

**"NBT requirement not loaded"**:
- JSON syntax valid? (Use a JSON validator)
- File in correct directory? (`nbt_requirements/blocks/` or `nbt_requirements/items/`)
- `resource_id` matches registry name exactly? (Case-sensitive)
- Ran `/reload` after adding the file?

**"NBT not extracted during scanning"**:
- NBT paths case-sensitive? Check with F3+H or `/data get`
- Does the NBT field actually exist on the scanned block/item?
- Block has a BlockEntity? (Blocks without BlockEntity have no NBT)

**"Items wrongly rejected during printing"**:
- Are you tracking transient fields (UUIDs, timestamps)? Add them to `ignore`
- Using `exact` when `subset` would be more appropriate?
- Check the `match` paths — do they reach the right level of nesting?

---

## PreFab Blueprint Item

### Item Name

A Blueprint's display name comes from the source PreFab:
- Named PreFab: "Iron Smelter" → Blueprint named "Iron Smelter"
- Unnamed PreFab: Blueprint shows default "PreFab Blueprint" name

### Tooltip

Hovering over a Blueprint shows:
- **Source**: Original PreFab name (or room code)
- **Room dimensions**: X×Y×Z size of the factory room
- **Resources required**: List of blocks and items needed for printing
  - Items with NBT requirements show a `[NBT]` marker
- **Production rates**: Input rates (negative, consumed) and output rates (positive, produced)
  - Sorted by rate, outputs first

### Right-Click Chat Display

Right-click a Blueprint in hand to view its full contents in chat:

- Source PreFab name and room dimensions
- Complete resource cost list (all items, unlimited — chat scrolls)
- Full production rate list (all rates shown)
- Color-coded: gold headers, green section titles, white entries

Empty/blank Blueprints show: "This blueprint is blank."

---

## Technical Reference

### Schema Versioning

Blueprint data uses **schema version 2**:
- **v1** (legacy): `Map<String, Long>` — resource ID → count (no NBT)
- **v2** (current): `List<ResourceRequirement>` — id + count + optional NBT

Old v1 blueprints load with `null` NBT for all resources (any item of matching type accepted).

### NBT Path Notation

Paths use dot notation with `*` for list wildcards:

| Path | Accesses |
|------|----------|
| `roomCode` | `nbt.getString("roomCode")` |
| `BlockEntityTag.roomCode` | `nbt.getCompound("BlockEntityTag").getString("roomCode")` |
| `importerExporterRates.*.rates` | Each list element's `rates` sub-list |
| `Config.Upgrades.SpeedTier` | `nbt.getCompound("Config").getCompound("Upgrades").getInt("SpeedTier")` |

### Performance

- NBT extraction is fast — tracking a few dozen fields has negligible cost
- Registry lookups are cached on first access
- Scanning runs asynchronously (doesn't freeze the server)
- Use `subset`/`list_subset` over `exact` where possible (more robust to unrelated NBT changes)

### Key Classes

| Class | Purpose |
|-------|---------|
| `FabricatorBlockEntity` | GUI inventory, scan/print logic, resource validation |
| `PreFabBlueprintItem` | Blueprint item with tooltips and right-click display |
| `BlueprintData` | Data record for blueprint contents (schema v2) |
| `NbtRequirement` | Record with matching engine (exact, subset, list_subset, range) |
| `NbtRequirementRegistry` | Singleton — loads JSON from datapacks, provides lookups |
| `BlockScanner` | Async block scanning with NBT extraction |
| `InventoryScanner` | Async inventory scanning with NBT extraction |
| `BlueprintScanCache` | Caches scan results in PreFab NBT for instant re-scans |

---

## See Also

- [PreFab System](PreFab-System) — Understanding PreFabs and how they work
- [Cached Production](Cached-Production) — How production rates are measured and cached
- [Advanced Setup](Advanced-Setup) — Multi-PreFab factories and complex patterns
- [Troubleshooting](Troubleshooting) — Common issues and solutions
