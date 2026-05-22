# State Machine Guide

PreFabs operate in one of four states: BUILDING, SIMULATING, CACHED, and HALTED. Understanding these states is crucial for using FPSCompress effectively.

## State Overview

```
┌─────────────┐
│  BUILDING   │ ← Initial state (setup)
└──────┬──────┘
       │ Right-click with wrench
       ▼
┌─────────────┐
│ SIMULATING  │ ← Measuring rates (chunks LOADED)
└──────┬──────┘
       │ Right-click with wrench
       ▼
┌─────────────┐
│   CACHED    │ ← Virtual production (chunks UNLOADED)
└──────┬──────┘
       │ Input starved or output blocked
       ▼
┌─────────────┐
│   HALTED    │ ← Input/Output Problem detected (chunks stay UNLOADED)
└──────┬──────┘
       │ Fix issue
       └─────→ Back to CACHED
```

## BUILDING State

### Purpose

Initial state for setup and configuration.

### Characteristics

- PreFab is idle (no resource transport)
- Player configures faces via GUI
- Player places Importers/Exporters inside CM
- Player connects chests to PreFab faces in Overworld
- CM chunks may be loaded or unloaded (doesn't matter)

### What You Should Do

1. **Open Face Configuration GUI**:
   - Shift + Right-click PreFab with Simulation Wrench

2. **Configure faces**:
   - Set mode (PULL/PUSH/DISABLED)
   - Set filter (ITEMS/FLUIDS/ENERGY)
   - Select target Importer/Exporter

3. **Place Importers/Exporters** inside CM:
   - Enter CM dimension
   - Place Importers next to machine inputs
   - Place Exporters next to machine outputs
   - Optionally: Name them with frequency system

4. **Connect chests** in Overworld:
   - Place chests adjacent to PreFab faces
   - PULL faces: Chest with input materials
   - PUSH faces: Empty chest for outputs

### Transitions

**From BUILDING to SIMULATING**:
- Enter PreFab Config (empty hand right click)
- Click button to start simulating
- PreFab begins measuring production rates

**Visual indicator** (future feature): Block texture changes to show state

---

## SIMULATING State

### Purpose

Measure actual factory production rates.

### Characteristics

- CM dimension chunks are **LOADED** (factory actually runs)
- PreFab counts resources transported every tick
- Resources flow normally: Overworld → Importer → Machines → Exporter → Overworld
- Delta accounting tracks net production per resource type
- Time counter tracks simulation duration

### What's Happening Behind the Scenes

**At simulation start**:
1. CM chunks load via `CMInterceptorImpl`
2. PreFab scans Importer/Exporter inventories (initial state)
3. Resets import/export counters
4. Starts tick counter

**Every tick during SIMULATING**:
1. PreFab transports resources (PULL/PUSH faces)
2. Counts every item imported/exported
3. Records in tracking tables

**Resources being tracked**:
```
Coal:        598 imported, 0 exported
Iron Ore:    602 imported, 0 exported
Iron Ingot:  0 imported, 128 exported
```

### How Long to Simulate?

**Minimum Default**: 120 seconds (2400 ticks)
**For batch processes**: Measure full cycle (e.g., 5 minutes)

**Why longer is better**:
- More samples = more accurate rate calculation
- Averages out random variations
- Ensures factory reaches steady state

### What You Should Do

1. **Start simulation**: Right-click PreFab with wrench
2. **Wait**: Let factory run for 30-60 seconds
3. **Monitor** (optional):
   - Check input chests (should be draining)
   - Check output chests (should be filling)
   - Verify factory is running (enter CM, see machines working)
4. **Finish simulation**: Right-click PreFab with wrench again

**Don't**:
- ❌ End simulation too early (inaccurate rates)
- ❌ Let inputs run out (skews measurement negatively)

### Transitions

**From SIMULATING to CACHED**:
- Enter PreFab Config (empty hand right click)
- Click button to stop simulating
- PreFab unloads CM chunks
- PreFab enters CACHED state

**From SIMULATING to BUILDING**:
- Enter PreFab Config (empty hand right click)
- Click button to stop simulating
- Click button to enter building state

**IMPORTANT:** If some items were still unprocessed/in-transit when the simulation ends, *they get output through the output side*. This is to make sure that you always get your resources back, even if the simulation ending cuts off production.
---

## CACHED State

### Purpose

Run factory virtually using fractional math (no chunk loading).

### Characteristics

- CM dimension chunks are **UNLOADED** -> **Performance gain.**
- PreFab simulates production using cached rates
- Fractional accumulators per resource type
- Resources flow based on math, not actual machines

### What's Happening Behind the Scenes

**Every tick during CACHED**:
1. For each resource with cached rate:
   - `accumulator += rate`
2. For inputs (negative rates):
   - If `accumulator <= -1.0`: Extract from Overworld, insert to Importer buffer
3. For outputs (positive rates):
   - If `accumulator >= 1.0`: Extract from Exporter buffer, insert to Overworld

**Example** (Iron Ingot at 0.213/tick):
```
Tick 1: accumulator = 0.213 (no action)
Tick 2: accumulator = 0.426 (no action)
Tick 3: accumulator = 0.639 (no action)
Tick 4: accumulator = 0.852 (no action)
Tick 5: accumulator = 1.065 → Push 1 iron, remainder 0.065
```

### What You Should Do

1. **Monitor output**: Check output chests for products
2. **Keep inputs stocked**: Ensure input chests have materials
3. **Let it run**: CACHED mode is the goal - leave it alone.

**Verification**:
- Press F3: Check "C:" value (chunks loaded should be lower)
- Output chest should fill at steady rate
- Input chests should drain at steady rate

### Transitions

**From CACHED to HALTED**:
- Input starved (can't extract from Overworld chest)
- Output blocked (can't insert to Overworld chest)
- Automatic transition, no player action

**From CACHED to SIMULATING**:
- Right-click PreFab with Simulation Wrench
- Used to recalibrate rates (if you need to make factory changes)

---

## HALTED State

### Purpose

Production paused due to input starvation or output blockage.

### Characteristics

- CM chunks STAY **UNLOADED** (do not reload)
- Production pauses (accumulators frozen)
- Cached rates are preserved (not forgotten)
- Waiting for player to fix Overworld side

### Common Causes

**Input starvation**:
- Input chest is empty
- Can't extract items for PULL face
- Example: Coal chest empty, furnace can't run

**Output blockage**:
- Output chest is full
- Can't insert items from PUSH face
- Example: Iron chest full, nowhere to put ingots

**Importer/Exporter broken**:
- Player entered CM dimension and broke gate
- PreFab can't find target by UUID

### What's Happening Behind the Scenes

**When HALTED triggered**:
1. PreFab detects: "Can't pull from chest" or "Can't push to chest"
2. PreFab transitions to HALTED
3. CM chunks STAY unloaded (no reload)
4. Cached rates preserved in NBT
5. Accumulators frozen at current values

**Why chunks stay unloaded**:
- Problem is on Overworld side (not factory side)
- Reloading chunks would waste TPS
- Player can fix issue from Overworld

### What You Should Do

**Step 1: Diagnose the problem**
- Check input chests (are they empty?)
- Check output chests (are they full?)
- Enter CM (are Importers/Exporters still there?)

**Step 2: Fix the issue**
- Add items to input chests
- Clear space in output chests
- Replace broken Importers/Exporters and reconfigure faces

**Step 3: Resume production**
- PreFab transitions to CACHED automatically

### Transitions

**From HALTED to SIMULATING**:
- Player fixes the issue
- Prefab automatically resumes

**From HALTED to BUILDING**:
- Enter prefab config (empty hand right click) and reset cache

---

## State Transition Summary

| Current State | Action | Next State |
|---------------|--------|------------|
| BUILDING | Right-click | SIMULATING |
| SIMULATING | Right-click | CACHED |
| SIMULATING | Right-click | BUILDING |
| CACHED | Input/output problem | HALTED |
| CACHED | Right-click | SIMULATING |
| HALTED | Fix | SIMULATING |

## Visual Indicators

### Current Indicators

**Block texture** (future feature):
- Different texture per state (visual feedback)

**GUI indicators**:
- Open PreFab GUI → Shows current state

### Debug Information

**Press F3** (debug screen):
- Look at PreFab block
- Hover text shows state (future feature)

**Chat messages** (current):
- Some transitions show chat messages (e.g., "Entering SIMULATING state")

---

## Best Practices

### ✅ Do This

1. **Configure in BUILDING**: Set up all faces before starting simulation
2. **Simulate long enough**: 60+ seconds for accurate rates
3. **Stock inputs before SIMULATING**: Ensure steady supply during measurement
4. **Let CACHED run**: Don't interfere, just monitor
5. **Fix HALTED quickly**: Add inputs, clear outputs

### ❌ Don't Do This

1. **Don't end simulation early**: Inaccurate rates
2. **Don't modify factory during SIMULATING**: Invalidates measurement
3. **Don't expect instant CACHED results**: Production is rate-based (may be slow)
4. **Don't reload/forceload CM chunks in HALTED**: Defeats the purpose (stay unloaded)

---

## Troubleshooting State Transitions

### Problem: "Can't transition from BUILDING to SIMULATING"

**Possible causes**:
- No faces configured (all faces DISABLED)
- No Importers/Exporters linked
- CM block not upgraded to PreFab

**Solution**:
- Configure at least one PULL or PUSH face
- Link faces to Importers/Exporters

### Problem: "PreFab immediately enters HALTED after SIMULATING"

**Possible causes**:
- Input chest was emptied during simulation
- Output chest filled up during simulation
- No chests adjacent to PreFab faces

**Solution**:
- Stock input chests before CACHED mode
- Use larger output chests (barrels, storage drawers)

### Problem: "Production very slow in CACHED"

**Not a problem.** This is expected if:
- Factory is slow (low rate measured during SIMULATING)
- Example: 0.05 items/tick = 1 item per second

**Solution**: Add more machines or speed upgrades, then re-simulate

### Problem: "F3 screen shows chunks still loaded in CACHED"

**Possible causes**:
- CM dimension is loaded for another reason (player inside, other CM active)
- Check specific CM room code chunks

**Verification**:
- Note chunk count before CACHED
- Enter CACHED
- Check chunk count after (should be lower)

---

## Advanced Topics

### Recalibrating Rates

**When to recalibrate**:
- Factory structure needs to change
- Need to upgrade machines
- Different input resources (iron ore → gold ore)

**How to recalibrate**:
1. Right-click PreFab (enter config menu)
2. Wait 60 seconds
3. Right-click again (stop simulation from menu)
4. New rates measured and cached.

### State Persistence

**States persist across**:
- ✅ Chunk unload/reload
- ✅ Server restart (if saved properly)
- ✅ PreFab picked up as item (PreFab-as-Item feature)

**Stored in**: PreFab's NBT data

### Multiple PreFabs

**Each PreFab has independent state**:
- PreFab A: CACHED
- PreFab B: SIMULATING
- PreFab C: HALTED

No coordination (yet) - future Factory Controller will manage multiple PreFabs

---

## See Also

- [Getting Started](Getting-Started) - First-time setup
- [PreFab System](PreFab-System) - Understanding PreFabs
- [Cached Production](Cached-Production) - How CACHED mode works
- [Troubleshooting](Troubleshooting) - Common state issues
