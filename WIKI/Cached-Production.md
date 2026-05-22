# Cached Production System

Cached Production is the **core feature** of FPSCompress - it allows factories to run "virtually" without chunk loading by using mathematical simulation based on measured production rates.

## What is Cached Production?

**Cached Production** = Running factories using math instead of actually loading chunks

### The Problem

In vanilla Minecraft:
- Factories require chunk loading (chunks must be active)
- Every machine ticks every frame
- Entities move, hoppers check neighbors, etc.
- **Result**: Significant TPS (ticks per second) impact

### The FPSCompress Solution

With cached production:
1. **SIMULATING phase**: Measure actual factory production rates (chunks loaded)
2. **CACHED phase**: Simulate production using fractional math (chunks **unloaded**)
3. **Result**: Factory "runs" without loading CM dimension chunks

**Performance gain**: 10-20 TPS improvement per complex factory (varies by factory)

## How It Works

### Phase 1: Rate Measurement (SIMULATING)

**Duration**: 30-60 seconds (player controlled)

**What happens**:
1. CM dimension chunks LOAD (factory actually runs)
2. PreFab counts every resource transported
3. Tracks resources per tick for each item type
4. Calculates rate: `Rate = Total_Transported / Time_Elapsed`

**Example measurement**:
```
Simulation Duration: 600 ticks (30 seconds)
Resources Transported:
  - Coal: 600 items → Rate = 600/600 = 1.0 coal/tick
  - Iron Ore: 600 items → Rate = 600/600 = 1.0 ore/tick
  - Iron Ingot: 128 items → Rate = 128/600 = 0.213 ingots/tick
```

### Phase 2: Fractional Accumulation (CACHED)

**What happens**:
1. CM dimension chunks UNLOAD (performance gain)
2. PreFab uses fractional math each tick
3. Accumulates fractional production: `accumulator += rate`
4. When `accumulator >= 1.0`: Transport whole units
5. Remainder carries to next tick

**Example (Iron Ingot production at 0.213/tick)**:
```
Tick 1: accumulator = 0.0 + 0.213 = 0.213 (no output)
Tick 2: accumulator = 0.213 + 0.213 = 0.426 (no output)
Tick 3: accumulator = 0.426 + 0.213 = 0.639 (no output)
Tick 4: accumulator = 0.639 + 0.213 = 0.852 (no output)
Tick 5: accumulator = 0.852 + 0.213 = 1.065
        → Push 1 iron ingot to Overworld
        → accumulator = 1.065 - 1.0 = 0.065 (remainder)
Tick 6: accumulator = 0.065 + 0.213 = 0.278 (no output)
...
```

**Result**: PreFab pushes 1 iron ingot every ~4.7 ticks, matching real furnace rate.

## Fractional Math Explained

### Multiple Resource Types

PreFabs track **separate accumulators** for each resource:

```
Resource: Iron Ingot
  Rate: 0.213/tick
  Accumulator: 0.85 (not ready)

Resource: Gold Ingot
  Rate: 0.15/tick
  Accumulator: 0.45 (not ready)

Resource: Copper Ingot
  Rate: 0.5/tick
  Accumulator: 1.25 (ready. Push 1, remainder 0.25)
```

Each resource accumulates independently.

## Rate Measurement Accuracy

### Factors Affecting Accuracy

**Simulation Duration**:
- Short simulation (10 seconds): Less accurate
- Long simulation (60 seconds): More accurate
- Recommendation: 30-60 seconds minimum

**Factory Consistency**:
- Consistent production (furnaces): Accurate ✅
- Variable production (random drops): Inaccurate ❌
- Cyclic production (batch processes): Accurate if full cycle measured ✅

**Input Availability**:
- Steady input supply: Accurate ✅
- Input starvation during measurement: Inaccurate ❌

### Best Practices for Accurate Rates

✅ **Run simulation for 60+ seconds**
✅ **Ensure steady input supply during measurement**
✅ **Let factory reach steady state before finishing simulation**
✅ **For batch processes, measure full cycle**
✅ **Avoid player interference during simulation**

❌ Don't end simulation too early
❌ Don't let inputs run out during measurement
❌ Don't modify factory during simulation

## Delta Accounting System

FPSCompress uses **delta accounting** to measure production accurately:

### What is Delta Accounting?

**Delta** = Change in inventory over time

**Formula**:
```
Net Production = (Final Inventory - Initial Inventory)
                 + (Exported - Imported)
```

### Why Delta Accounting?

**Problem with naive counting**:
- Just counting imports/exports misses items stored in buffers
- Importer buffer: 5 items initially → Factory consumes 3 → 2 remain
- Naive count: 0 imports (wrong)
- Delta accounting: -3 consumption (correct)

**Solution**: Track inventory changes AND import/export

### How It Works

**At simulation start** (entering SIMULATING):
1. Scan all Importers/Exporters
2. Record initial inventory counts
3. Reset import/export counters

**During simulation** (SIMULATING state):
1. Count every item imported (Overworld → Importer)
2. Count every item exported (Exporter → Overworld)
3. Record in tracking tables

**At simulation end** (entering CACHED):
1. Scan all Importers/Exporters again
2. Record final inventory counts
3. Calculate delta: `(Final - Initial) + (Exported - Imported)`
4. Calculate rate: `delta / time_elapsed`

### Example Delta Calculation

**Initial state** (simulation start):
- Importer buffer: 10 coal
- Exporter buffer: 2 iron ingots

**During simulation** (600 ticks):
- Imported: 600 coal (from Overworld)
- Exported: 130 iron ingots (to Overworld)

**Final state** (simulation end):
- Importer buffer: 8 coal
- Exporter buffer: 0 iron ingots

**Delta calculation**:
```
Coal consumption:
  = (Final - Initial) + (Exported - Imported)
  = (8 - 10) + (0 - 600)
  = -2 + (-600)
  = -602 coal consumed
  Rate = -602 / 600 = -1.003 coal/tick (input)

Iron production:
  = (0 - 2) + (130 - 0)
  = -2 + 130
  = 128 iron ingots produced
  Rate = 128 / 600 = 0.213 iron/tick (output)
```

**Result**: PreFab knows factory consumes ~1 coal/tick and produces ~0.213 iron/tick.

## CACHED Mode Behavior

### Resource Flow in CACHED

**Input resources** (negative rates):
1. PreFab accumulates: `accumulator += (-1.003)`
2. When `accumulator <= -1.0`: Extract from Overworld chest
3. Insert to Importer buffer (even though chunks unloaded)

**Output resources** (positive rates):
1. PreFab accumulates: `accumulator += 0.213`
2. When `accumulator >= 1.0`: Extract from Exporter buffer
3. Insert to Overworld chest

**Magic**: Importer/Exporter buffers update even with chunks unloaded.

### Chunk Loading State

**In CACHED mode**:
- CM dimension chunks: **UNLOADED** ✅
- Machines: Not ticking (not loaded)
- Importers/Exporters: Accessible via block entity lookup
- PreFab: Ticking normally (in Overworld)

**How PreFab accesses unloaded Importers/Exporters**:
- Block entities persist even when chunks unload
- PreFab looks up by UUID (cached position)
- Direct NBT access (no chunk loading required)

### What Happens to the Factory?

**During CACHED mode**, inside the CM dimension:
- Machines frozen (not ticking)
- Items in machine slots don't move
- Hoppers don't transfer
- Entities don't move

**But from Overworld perspective**:
- Inputs consumed at measured rate
- Outputs produced at measured rate
- Factory "appears" to be running

**Simulation is perfect** (as long as rates were measured accurately)

## Performance Benefits

### Chunk Loading Comparison

**Without FPSCompress** (factory running normally):
```
CM Dimension: 6x6 chunks = 36 chunks loaded
TPS Impact: 5-10 TPS loss (depends on factory complexity)
```

**With FPSCompress** (CACHED mode):
```
CM Dimension: 0 chunks loaded
TPS Impact: <0.1 TPS loss (just PreFab math)
```

**Improvement**: ~5-10 TPS per factory (can scale to 20+ for complex factories)

### Scaling Benefits

**One factory**:
- Improvement: ~5 TPS

**Ten factories**:
- Without FPSCompress: ~50 TPS loss → Server unplayable
- With FPSCompress: ~1 TPS loss → Server runs smooth

**FPSCompress enables massive factory scaling.**

## Limitations and Edge Cases

### What CACHED Mode Can't Simulate

❌ **Entity-based production**:
- Mob farms (mob spawning)
- Animal breeding (entity AI)
- Item entity collection (entity physics)

❌ **Random/variable production**:
- Ore processing with random bonus outputs
- Machines with chance-based mechanics

❌ **Player-dependent systems**:
- Machines requiring player interaction
- Contraptions using player detection

❌ **Redstone logic**:
- State machines (redstone clocks frozen)
- Conditional production (redstone gates frozen)

### When Rates Become Invalid

Cached rates are invalid if:
- Factory structure changes (added machines)
- Machine upgrades (speed upgrades)
- Input changed (different ore type)
- Importer/Exporter moved or broken

**Solution**: Re-simulate to measure new rates.

### Handling HALTED State

**What causes HALTED**:
- Input starved (can't extract from Overworld chest)
- Output blocked (can't insert to Overworld chest)

**HALTED behavior**:
- CM chunks STAY unloaded (do not reload)
- Cached rates preserved
- Production pauses

**Recovery**:
- Fix Overworld side (add inputs, clear outputs)
- Right-click with wrench → Resume (enters SIMULATING again)

## Advanced Topics

### Passthrough Detection

**Passthrough** = Resources enter AND exit without net change

**Example**:
```
Factory: [Importer] → [Chest] → [Exporter]
(No processing, just storage)

Measurement:
- Imported: 100 items
- Exported: 100 items
- Net production: 0 items

Rate: 0.0 items/tick (passthrough detected)
```

PreFab detects passthrough and may warn player (future feature)

### Negative Rates (Inputs)

**Input resources** have negative rates:
```
Coal consumption: -1.0 coal/tick
```

**Accumulation**:
```
accumulator = 0.0
accumulator += (-1.0) = -1.0 (extract 1 coal from chest)
```

**Why negative:** Indicates resources leaving Overworld (inputs to factory)

## Debugging CACHED Mode

### Verifying Chunks Are Unloaded

1. Enter CACHED mode
2. Press F3 (debug screen)
3. Check "C:" value (chunks loaded)
4. Value should be lower than during SIMULATING

**Example**:
- SIMULATING: C: 441/256 (CM chunks loaded)
- CACHED: C: 305/256 (CM chunks unloaded)

## See Also

- [PreFab System](PreFab-System) - Understanding PreFabs
- [State Machine Guide](State-Machine-Guide) - SIMULATING → CACHED transitions
- [Troubleshooting](Troubleshooting) - Common caching issues
