# Validation via Delta Accounting

**Status**: Design proposal (not implemented)  
**Date**: 2026-05-02  
**Purpose**: Measure factory production/consumption rates using input/output deltas

---

## Core Concept

Track four quantities for each resource type (e.g., "iron_ingot"):

1. **Total Imported** - Sum of all items pulled from Overworld into CM dimension during simulation
2. **Total Exported** - Sum of all items pushed from CM dimension to Overworld during simulation
3. **Initial State** - Count of items present in factory machines/inventories when simulation starts
4. **Final State** - Count of items present in factory machines/inventories when simulation ends

**IMPORTANT**: For MVP, only "Total Imported" and "Total Exported" are tracked. "Initial State" and "Final State" scanning requires a robust inventory scanner that walks all BlockEntities in the CM dimension—this is **POST-MVP anti-cheat validation** (see VALIDATION_REDSTONE_PROTOCOL.md).

**Net Production Formula**:
```
Net = (Final - Initial) + (Exported - Imported)
```

- **Positive Net** = Factory produced this resource (output)
- **Negative Net** = Factory consumed this resource (input/cost)
- **Zero Net** = Resource neither produced nor consumed (passthrough)

---

## Examples

### Example 1: Passthrough (No Production)
```
Iron Ingots:
  Imported: 10
  Exported: 0
  Initial:  0
  Final:    10

Net = (10 - 0) + (0 - 10) = 0
```
**Interpretation**: 10 ingots were imported and are sitting in the factory. No production or consumption occurred.

### Example 2: Consumption (Factory Used Resource)
```
Iron Ingots:
  Imported: 0
  Exported: 0
  Initial:  10
  Final:    0

Net = (0 - 10) + (0 - 0) = -10
```
**Interpretation**: 10 ingots that were already in the factory were consumed during production. Cost = 10 ingots.

### Example 3: Production (Factory Created Resource)
```
Iron Ingots:
  Imported: 0
  Exported: 5
  Initial:  0
  Final:    0

Net = (0 - 0) + (5 - 0) = +5
```
**Interpretation**: 5 ingots were produced and exported. Production rate = 5 ingots / simulation_time.

### Example 4: Mixed (Import + Consumption + Production)
```
Coal:
  Imported: 100
  Exported: 0
  Initial:  10
  Final:    5

Net = (5 - 10) + (0 - 100) = -105

Iron Ingots:
  Imported: 0
  Exported: 80
  Initial:  0
  Final:    2

Net = (2 - 0) + (80 - 0) = +82
```
**Interpretation**: Smelting factory consumed 105 coal to produce 82 iron ingots (2 still in furnace output).

---

## Rate Calculation

**Proposed Time Window**: First import tick → Last export tick

```java
long startTick = firstImportTick;  // When first resource entered factory
long endTick = lastExportTick;     // When last resource left factory
long activeTicks = endTick - startTick;

double productionRate = netProduction / (double) activeTicks;
```

**Rationale**: Excludes padding time when factory is idle at start/end of simulation.

---

## Logical Issues & Edge Cases

### 1. **Hidden Storage Cheat** (DOES NOT SOLVE)
**Problem**: Player places chest in CM dimension, hides resources there.

**Example**:
```
Iron Ingots:
  Imported: 0
  Exported: 100  ← System thinks factory produced 100 ingots
  Initial:  0
  Final:    0

Reality: 100 ingots were in hidden chest, factory produced 0 ingots
```

**Why this is exploitable**: Player places 100 iron ingots in a hidden chest during SIMULATING, then exports them via Exporter. PreFab thinks the factory produced them. In CACHED mode, the factory "produces" infinite iron ingots from nothing (100 ingots every X ticks, forever).

**Why This Happens**: The system only sees import/export events. Hidden storage inside the CM dimension can inject or extract resources without the PreFab knowing. Initial/Final state scanning (scanning ALL BlockEntity inventories in the CM room) would detect this, but that's POST-MVP anti-cheat validation.

**MVP Limitation**: For MVP, we accept that players can cheat this way. The focus is proving the caching system improves performance, not preventing exploits. See VALIDATION_REDSTONE_PROTOCOL.md for the post-MVP anti-cheat system that solves this.

### 2. **Intermediate Products** (Tracking Complexity)
**Problem**: How to track transformations?

**Example**: Iron ore → Iron ingot → Iron plate → Circuit
```
Iron Ore:
  Imported: 10, Final: 0  → Net = -10 (consumed)

Iron Ingot:
  Imported: 0, Final: 5   → Net = +5 (produced)
```

**Solution**: Track each resource type independently. The system naturally shows ore consumption and ingot production as separate rates.

### 3. **Time Window Issues**

**Issue A: Startup Phase**
- Factory imports coal, heats furnaces (no output yet)
- First import at tick 0, first export at tick 300
- Those 300 ticks include non-productive startup time

**Issue B: Bursty Production**
- Factory imports 100 iron ore in one batch
- Processes over 600 ticks
- Exports 64 ingots at tick 600
- Time window = 600 ticks, but production was continuous

**Issue C: Output Buffering**
- Factory finishes producing at tick 500
- Exporter slowly pushes items to Overworld until tick 700
- Time window = 700 ticks, but active production ended at 500

**Proposed Fix**: Use **full simulation time** instead of first→last window. This averages out bursty behavior and includes startup/shutdown:
```java
long activeTicks = simulationEndTick - simulationStartTick;
```

### 4. **In-Flight Items at Simulation End**
**Problem**: Items currently being processed by machines.

**Example**: Furnace is 50% done smelting an ore when simulation ends.
- "Final State" scan might count the ore as present (input slot)
- But it's partially consumed (50% progress lost if we cache now)

**Mitigation**: Only end simulation when factory reaches steady state (input/output queues stable for N ticks).

### 5. **Zero Activity**
**Problem**: Nothing imported AND nothing exported during entire simulation.

**Example**: Factory has internal resources but is misconfigured (no Importers/Exporters linked).
```
Coal:
  Imported: 0, Exported: 0, Initial: 10, Final: 10
  Net = 0
```

**Solution**: Detect zero activity and transition to HALTED state (simulation failed, cannot cache).

---

## Relationship to VALIDATION_REDSTONE_PROTOCOL.md

**This document** (Delta Accounting):
- ✓ Measures production rates for caching system
- ✓ Works for MVP (vanilla blocks, honest players)
- ✗ Does NOT prevent cheating (hidden storage)
- ✗ Does NOT verify factory legitimacy

**VALIDATION_REDSTONE_PROTOCOL.md** (Anti-Cheat):
- ✓ Detects cheating via graceful shutdown protocol
- ✓ Verifies factory can operate without hidden storage
- ✗ Post-MVP feature (not needed until v1.0+)

**Use Delta Accounting for MVP** to calculate rates. Add redstone validation later to catch cheaters.

---

## Implementation Notes (POST-MVP)

**⚠️ WARNING**: The code below shows how to implement initial/final state scanning for anti-cheat validation. This is **NOT part of MVP scope**. MVP only tracks imports/exports during transport (no inventory scanning needed).

For MVP Phase 4 (Rate Measurement):
- ✅ Track `totalImported` and `totalExported` during transport ticks
- ✅ Calculate rates using full simulation time window
- ❌ Do NOT implement `captureInitialState()` or `captureFinalState()`
- ❌ Do NOT implement `scanAllMachineInventories()`

The inventory scanning code below is for **v1.0+ anti-cheat** work (post-MVP).

---

### Data Structure (Per BlockEntity)
```java
public class ResourceDeltaTracker {
    private Map<ResourceKey, ResourceDeltas> deltas = new HashMap<>();
    
    public void recordImport(ResourceKey resource, long amount) {
        deltas.computeIfAbsent(resource, k -> new ResourceDeltas())
              .totalImported += amount;
    }
    
    public void recordExport(ResourceKey resource, long amount) {
        deltas.get(resource).totalExported += amount;
    }
    
    public void captureInitialState(Map<ResourceKey, Long> inventory) {
        for (var entry : inventory.entrySet()) {
            deltas.computeIfAbsent(entry.getKey(), k -> new ResourceDeltas())
                  .initialState = entry.getValue();
        }
    }
    
    public void captureFinalState(Map<ResourceKey, Long> inventory) {
        for (var entry : inventory.entrySet()) {
            deltas.get(entry.getKey()).finalState = entry.getValue();
        }
    }
    
    public long calculateNet(ResourceKey resource) {
        ResourceDeltas d = deltas.get(resource);
        return (d.finalState - d.initialState) + (d.totalExported - d.totalImported);
    }
}

class ResourceDeltas {
    long totalImported = 0;
    long totalExported = 0;
    long initialState = 0;
    long finalState = 0;
}
```

### State Scanning for Initial/Final
```java
// When entering SIMULATING state
Map<ResourceKey, Long> initialInventory = scanAllMachineInventories(cmLevel);
tracker.captureInitialState(initialInventory);

// When exiting SIMULATING state
Map<ResourceKey, Long> finalInventory = scanAllMachineInventories(cmLevel);
tracker.captureFinalState(finalInventory);

// Helper method
private Map<ResourceKey, Long> scanAllMachineInventories(ServerLevel level) {
    Map<ResourceKey, Long> totals = new HashMap<>();
    
    // Scan all loaded block entities in CM dimension
    for (BlockEntity be : level.blockEntities()) {
        IItemHandler handler = be.getCapability(ItemHandler.BLOCK);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    ResourceKey key = ResourceKey.of(stack.getItem());
                    totals.merge(key, (long) stack.getCount(), Long::sum);
                }
            }
        }
        // Similar for fluids, energy
    }
    
    return totals;
}
```

---

## Verdict: Does This Work for MVP?

**Yes, with caveats:**

✅ **What it DOES solve**:
- Measures actual resource flow (imports/exports) during transport ← MVP tracks this
- Calculates production/consumption rates from import/export deltas
- Initial/Final state scanning enables anti-cheat validation ← POST-MVP feature
- Works with vanilla blocks (furnaces, chests, hoppers)
- Simple accounting, easy to implement

✅ **Acceptable MVP limitations**:
- Players can cheat with hidden chests → Address in v1.0+ with redstone protocol
- Doesn't track intermediate transformations → Each resource tracked independently is fine
- Time window approximation → Use full simulation time, require steady state

❌ **Must Fix Before Using**:
- **Don't use first→last time window** → Use full simulation time instead
- **Detect zero activity** → Transition to HALTED if no imports/exports
- **Require steady state** → Don't end simulation with items in-flight

---

## Recommended Next Steps

1. **Implement ResourceDeltaTracker** in Phase 4 (Rate Measurement)
2. **Use full simulation time for rate calculation** (not first→last window)
3. **Add steady-state detection** (output queue stable for 100+ ticks)
4. **Document MVP cheat limitation** in player-facing docs ("This mod trusts that your factory is legitimate")
5. **Plan redstone validation for v1.0+** when anti-cheat becomes priority

---

**Summary**: Delta accounting works for MVP rate measurement. It measures *what happened*, not *whether it's legitimate*. That's acceptable for MVP scope (see CLAUDE.md: "Players could cheat, but that's post-MVP validation").
