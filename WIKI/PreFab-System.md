# PreFab System

PreFabs (Prefabricated Factories) are the core blocks of FPSCompress. They enable factory compression by caching production rates and simulating factories without chunk loading.

## What is a PreFab?

A **PreFab** is an upgraded Compact Machine block that acts as a **cross-dimensional conduit**. It routes resources between the Overworld and the Compact Machines dimension, measures production rates, and can run factories "virtually" using math.

### Key Features

- **6 Independent Faces**: Each face (North, South, East, West, Up, Down) can be configured separately
- **No Internal Storage**: PreFabs are routers, not chests - resources transport instantly
- **State Machine**: Controls when factory is loaded vs. unloaded (BUILDING/SIMULATING/CACHED/HALTED)
- **Rate Caching**: Measures actual production rates, then simulates them mathematically
- **Chunk Unloading**: CM dimension chunks stay unloaded during CACHED mode (performance gain)

## Creating a PreFab

### Step 1: Craft a PreFab Upgrade Template

(Check JEI for crafting recipe)

### Step 2: Upgrade a Compact Machine

1. Place a Compact Machine block in the world (any size)
2. Hold the PreFab Upgrade Template item
3. Right-click the Compact Machine
4. The CM block becomes a PreFab (texture changes)

## Face Configuration Overview

Each PreFab has 6 faces that can be independently configured:

### Face Modes

| Mode | Description | Direction |
|------|-------------|-----------|
| **PULL** | Extract from Overworld → Transport to CM Importer | Overworld → CM |
| **PUSH** | Extract from CM Exporter → Transport to Overworld | CM → Overworld |
| **DISABLED** | Face inactive (no resource transport) | N/A |

### Resource Filters

| Filter | Description | Status |
|--------|-------------|--------|
| **ITEMS** | Transfer items only (ItemStacks) | ✅ Implemented |
| **FLUIDS** | Transfer fluids only (FluidStacks) | 🔨 Future |
| **ENERGY** | Transfer energy only (FE/RF) | 🔨 Future |
| **ALL** | Transfer all resource types | 🔨 Future |

### Target Selection

Each face must link to a specific Importer or Exporter:
- **PULL faces** link to Importers (inside CM dimension)
- **PUSH faces** link to Exporters (inside CM dimension)
- Linking is done via UUID (unique identifier)
- Select target from dropdown in configuration GUI

## How PreFabs Work

### The Three-Block System

PreFabs don't work alone - they're part of a three-block system:

1. **PreFab Block** (Overworld)
   - Routes resources between dimensions
   - Controls state machine
   - No internal storage

2. **Importer Block** (CM Dimension)
   - Input gate where resources enter factory
   - Has small buffer (9 slots)
   - Actively pushes items into adjacent inventories

3. **Exporter Block** (CM Dimension)
   - Output gate where resources exit factory
   - Has small buffer (9 slots)
   - Actively pulls from adjacent inventories

💡 **Best practice**: Use intermediate chests between gates and machines for more reliable systems

### Resource Flow: PULL Mode

```
Overworld Chest
      ↓ (PreFab extracts via IItemHandler)
PreFab PULL Face
      ↓ (Transport across dimension)
Importer Block (CM dimension)
      ↓ (Importer pushes via IItemHandler)
Intermediate Chest (CM dimension)
      ↓ (Hopper/Pipe pulls from chest)
Furnace Input Slot
```

### Resource Flow: PUSH Mode

```
Furnace Output Slot
      ↓ (Hopper/Pipe pushes to chest)
Intermediate Chest (CM dimension)
      ↓ (Exporter pulls via IItemHandler)
Exporter Block (CM dimension)
      ↓ (Transport across dimension)
PreFab PUSH Face
      ↓ (PreFab inserts via IItemHandler)
Overworld Chest
```

## State Machine

PreFabs operate in one of four states:

### BUILDING State

**Purpose**: Initial setup and configuration

**Characteristics**:
- PreFab is idle
- Player configures faces via GUI
- Player builds factory inside PreFab
- Places Importers/Exporters inside CM
- Connects chests to PreFab faces in Overworld
- CM chunks may be loaded or unloaded

**Transitions**:
- Right-click with Simulation Wrench → **SIMULATING**

### SIMULATING State

**Purpose**: Measure actual production rates

**Characteristics**:
- CM chunks are **FORCE LOADED** (factory actually runs)
- PreFab counts resources transported per tick
- Tracks each resource type separately
- Calculates: Rate = Total_Transported / Time_Elapsed
- Example: 128 iron ingots in 600 ticks = 0.213 iron/tick

**Transitions**:
- Right-click with Simulation Wrench → **CACHED** (calculates rates, unloads chunks)

### CACHED State

**Purpose**: Run factory virtually without chunk loading

**Characteristics**:
- CM chunks are **UNLOADED** ← **This is the performance gain.**
- PreFab simulates production using fractional math
- Accumulates fractional rates: `accumulator += rate`
- When `accumulator >= 1.0`: Transport whole units
- Example: Rate 0.213 iron/tick → Every ~4.7 ticks, push 1 iron ingot

**This is the primary goal of the mod.**

**Transitions**:
- Input starved (can't pull from Overworld) → **HALTED**
- Output blocked (can't push to Overworld) → **HALTED**
- Right-click with Simulation Wrench → **SIMULATING** (recalibrate)

### HALTED State

**Purpose**: Cache broke, needs player intervention

**Characteristics**:
- CM chunks STAY **UNLOADED** (do not reload)
- Production pauses
- Preserves cached rates (does not forget measurements)
- Waiting for player to fix Overworld side

**Common Causes**:
- Input chest is empty (can't pull more items)
- Output chest is full (can't push more items)
- Importer/Exporter was broken

**Transitions**:
- Right-click with Simulation Wrench → **SIMULATING** (resumes, remeasures rates)

## Fractional Production System

PreFabs use **fractional accumulators** to handle production rates less than 1 item/tick.

### Why Fractional Math?

Most factories produce slower than 1 item/tick:
- Furnace: ~0.213 iron/tick (1 iron every ~4.7 ticks)
- Crusher: ~0.1 dust/tick (1 dust every 10 ticks)

Fractional math allows PreFabs to accurately simulate these rates.

### How It Works

**Step 1: Measure Rate (SIMULATING)**
```
Factory produced 128 iron ingots over 600 ticks
Rate = 128 / 600 = 0.2133 iron/tick
```

**Step 2: Accumulate (CACHED)**
```
Tick 1: accumulator = 0.0 + 0.2133 = 0.2133 (no output)
Tick 2: accumulator = 0.2133 + 0.2133 = 0.4266 (no output)
Tick 3: accumulator = 0.4266 + 0.2133 = 0.6399 (no output)
Tick 4: accumulator = 0.6399 + 0.2133 = 0.8532 (no output)
Tick 5: accumulator = 0.8532 + 0.2133 = 1.0665 (output 1 iron)
        accumulator = 1.0665 - 1.0 = 0.0665 (carry remainder)
```

**Result**: PreFab pushes 1 iron ingot every ~5 ticks, matching real factory rate.

### Multiple Resource Types

PreFabs track separate accumulators for each resource:
```
Iron Ingot: 0.213/tick → accumulator = 0.85
Gold Ingot: 0.15/tick → accumulator = 0.45
Copper Ingot: 0.5/tick → accumulator = 1.25 (ready to push 1)
```

## Capabilities and Interactions

### What PreFabs Expose

PreFabs don't expose capabilities to adjacent blocks - they actively extract/insert each tick.

**Why no capabilities:**
- PreFabs are routers, not inventories
- Active transport (PreFab initiates) vs. passive (adjacent blocks query)
- Simpler implementation

### What PreFabs Query

**PULL faces (extract from Overworld)**:
- `IItemHandler` - Extract items from chests, hoppers, machines
- `IFluidHandler` - Extract fluids from tanks (future)
- `IEnergyStorage` - Extract energy from batteries (future)

**PUSH faces (insert to Overworld)**:
- `IItemHandler` - Insert items to chests, hoppers, machines
- `IFluidHandler` - Insert fluids to tanks (future)
- `IEnergyStorage` - Insert energy to batteries (future)

## Advanced Features

### Portable PreFabs (PreFab-as-Item)

PreFabs can be broken and carried as items:
- All face configuration persists in item NBT
- Cached rates are preserved
- Importer/Exporter UUIDs remain linked
- Place PreFab elsewhere → Continues from same state

**Use cases**:
- Move factory to new location
- Store PreFab in Ender Chest
- Trade PreFabs with other players (entire factory in one item)

### Multiple Resource Inputs/Outputs

A single PreFab can handle complex factories:
```
NORTH: PULL ITEMS → Importer "Coal"
EAST:  PULL ITEMS → Importer "Iron Ore"
SOUTH: PULL ITEMS → Importer "Gold Ore"
WEST:  PUSH ITEMS ← Exporter "Iron Ingots"
UP:    PUSH ITEMS ← Exporter "Gold Ingots"
DOWN:  DISABLED
```

Each face operates independently.

### Multi-PreFab Coordination

**Status**: 🔨 Future feature (Factory Controller block)

Future versions will support:
- Factory Controller block holding multiple PreFabs
- Unified resource management
- Cross-PreFab dependencies (PreFab A output → PreFab B input)

## Performance Considerations

### TPS Impact

**Before FPSCompress** (factory in loaded CM):
- CM chunks stay loaded (6x6 chunks = 36 chunks)
- All machines tick every frame
- Entities move, hoppers check neighbors, etc.
- TPS impact: Significant (depends on factory complexity)

**After FPSCompress** (factory in CACHED mode):
- CM chunks unloaded (0 chunks loaded)
- PreFab runs simple math each tick
- No machine ticks, no entity updates
- TPS impact: Minimal (just the PreFab math)

**Typical improvement**: 10-20 TPS gain per complex factory (your mileage may vary)

### When to Use PreFabs

**Good use cases**:
- Large automated factories (many machines)
- Always-on production (ore processing, item crafting)
- Server-friendly automation (reduce chunk loading)

**Not ideal for**:
- Single machines (overhead not worth it)
- Farms with entity spawning (can't simulate mob drops accurately)
- Multi-Purpose Machines (once cached, only 1 "recipe" is used)

## Limitations

### Limitations (Current Version)

- ❌ Only ITEMS filter works (fluids/energy coming in future)
- ❌ No AE2/Refined Storage integration yet
- ❌ No anti-cheat validation (players can hide chests in CM for "free" items)
- ❌ No filters/whitelists (face transports ALL items)

### Design Limitations

- Factory must have consistent production over "long enough" intervals (can't handle random/conditional logic)
- Rates recalibrate on changes (add more machines → must re-simulate)
- Cannot simulate entity interactions (mob farms, animal breeding)

## See Also

- [Importer & Exporter Guide](Importer-Exporter-Guide) - Setting up input/output gates
- [Face Configuration](Face-Configuration) - Detailed GUI walkthrough
- [State Machine Guide](State-Machine-Guide) - Understanding state transitions
- [Cached Production](Cached-Production) - Deep dive into fractional math
