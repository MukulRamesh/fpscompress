# Enhanced Delta Accounting - Testing Guide

## Overview

This guide walks you through testing the Enhanced Delta Accounting system, which accurately measures factory production rates by scanning ALL resources in a CM room (not just PreFab imports/exports).

**What changed**: The system now performs initial and final inventory scans of the entire CM room to detect hidden storage, internal buffers, and other resource sources.

**Why test this**: To verify that the system correctly handles:
- Legitimate factories (only PreFab imports/exports)
- Hidden storage consumption (chests placed by player)
- Hidden storage production (resources taken from hidden chests)
- Passthrough detection (net = 0, no production)
- Performance (async scanning doesn't lag game)

---

## Prerequisites

### 1. Build the Mod

```bash
cd "fpscompress-template-1.21.11"
./gradlew build
```

The built JAR will be at: `build/libs/fpscompress-template-1.21.11-1.0.0.jar`

### 2. Install Minecraft Test Environment

**Required mods**:
- NeoForge 1.21.11 (correct version for your Minecraft 1.21.11)
- Compact Machines (latest 1.21 version)
- FPSCompress (your built JAR)

**Installation**:
1. Install NeoForge 1.21.11
2. Place mods in `.minecraft/mods/` folder
3. Launch Minecraft

### 3. Create Test World

- **Game Mode**: Creative (for spawning items and viewing detailed stats)
- **World Type**: Default
- **Cheats**: Enabled (for `/time set day`, `/tp`, etc.)

---

## Test 1: Baseline - Legitimate Factory (No Hidden Storage)

**Goal**: Verify the system works correctly when there's NO hidden storage (pure PreFab imports/exports).

### Setup

1. **Place Compact Machine** (any size, recommend "Medium" 7×7×7 for visibility)
2. **Enter CM room**
3. **Build simple furnace factory**:
   ```
   [Importer #1] → [Furnace] → [Exporter #1]
   ```
   - Place Importer block (new block from FPSCompress)
   - Place Furnace next to Importer
   - Place Exporter block next to Furnace output
   - **DO NOT place any chests or storage**

4. **Exit CM room** (return to Overworld)

5. **Upgrade CM to PreFab**:
   - Right-click CM block with "PreFab Upgrade Template" item
   - Block should change appearance

6. **Configure PreFab faces**:
   - Shift+Right-click PreFab with Simulation Wrench
   - Configure NORTH face:
     - Mode: PULL
     - Filter: ITEMS
     - Target: Select "Importer #1" from dropdown
   - Configure SOUTH face:
     - Mode: PUSH
     - Filter: ITEMS
     - Target: Select "Exporter #1" from dropdown
   - Save configuration

7. **Place input/output chests**:
   - NORTH side: Chest with 128 Coal
   - SOUTH side: Empty chest

### Execute Test

1. **Open PreFab Status GUI**:
   - Right-click PreFab with Simulation Wrench (without Shift)
   - Verify state shows "BUILDING"

2. **Start Simulation**:
   - Click "Start Simulation" button
   - Watch chat for messages

3. **Monitor logs** (check `logs/latest.log`):
   ```
   [FPSCompress] PreFab at [...] starting initial scan (chunks loaded)
   [FPSCompress] Scanned X BlockEntities in room bounds AABB[...]
   [FPSCompress] Initial scan complete: Y resource types
   [FPSCompress] PreFab at [...] entered SIMULATING state
   ```

4. **Wait for production** (~30-60 seconds):
   - Open Status GUI periodically
   - Verify "Live Stats" shows increasing import/export counts
   - Expected to see:
     - Coal: ↓128 (imported)
     - Iron Ingot: ↑64 (exported, approximately)

5. **Finish Simulation**:
   - Click "Finish Simulation" button
   - Watch for final scan logs:
     ```
     [FPSCompress] PreFab at [...] unloaded chunks for final scan
     [FPSCompress] Final scan complete: Y resource types
     [FPSCompress]   minecraft:coal: Initial=0, Final=0, Imported=128, Exported=0, Net=-128
     [FPSCompress]   minecraft:iron_ingot: Initial=0, Final=0, Imported=0, Exported=64, Net=+64
     ```

### Expected Results

**Console logs**:
- Initial scan: "Initial=0, Final=0" for all resources (no hidden storage found)
- Coal: `Net = (0-0) + (0-128) = -128` (consumed via imports)
- Iron: `Net = (0-0) + (64-0) = +64` (produced via exports)

**GUI**:
- State: CACHED
- Cached Rates:
  - Coal: -0.213/t (or similar negative rate)
  - Iron Ingot: +0.107/t (or similar positive rate)

**Verification**:
- ✅ No passthrough detection (rates were cached)
- ✅ PreFab entered CACHED mode
- ✅ Initial/Final states are zero (no hidden storage detected)
- ✅ Formula matches: `(Final - Initial) + (Exported - Imported) = Net`

---

## Test 2: Hidden Storage Consumption (Player Cheating)

**Goal**: Verify system detects when player places hidden chest with resources inside CM room.

### Setup

1. **Reuse PreFab from Test 1** or create new one
2. **Enter CM room**
3. **Build factory with hidden storage**:
   ```
   [Hidden Chest: 64 Diamonds]
   [Importer #1] → [Furnace] → [Exporter #1]
   ```
   - Place chest AWAY from Importer/Exporter (hidden in corner)
   - Fill chest with 64 Diamonds
   - Build same furnace setup as Test 1

4. **Exit and configure PreFab** (same as Test 1)

5. **Prepare inputs**:
   - NORTH chest: 128 Coal
   - SOUTH chest: Empty

### Execute Test

1. **Start Simulation** (same as Test 1)

2. **During simulation**:
   - Open Status GUI
   - If in creative mode, verify "[Delta Accounting Active]" appears below elapsed time

3. **Manually trigger diamond export** (to simulate factory "producing" diamonds):
   - While simulation running, enter CM room
   - Take 64 diamonds from hidden chest
   - Place in Exporter manually
   - Exit room

4. **Finish Simulation**

### Expected Results

**Console logs**:
```
[FPSCompress] Initial scan complete: 3 resource types
[FPSCompress]   minecraft:coal: Initial=0, Final=0, Imported=128, Exported=0, Net=-128
[FPSCompress]   minecraft:iron_ingot: Initial=0, Final=0, Imported=0, Exported=64, Net=+64
[FPSCompress]   minecraft:diamond: Initial=64, Final=0, Imported=0, Exported=64, Net=0
[FPSCompress]     Passthrough from internal storage (internal: -64), excluding from rates
```

**Key observations**:
- Diamond: `Net = (0-64) + (64-0) = 0` (consumed from storage, exported same amount)
- System detects: net = 0 BUT internal change = -64 (passthrough!)
- Diamond is **excluded** from cached rates (correctly)

**GUI**:
- State: CACHED
- Cached Rates:
  - Coal: -0.213/t
  - Iron Ingot: +0.107/t
  - **Diamonds NOT listed** (passthrough excluded)

**Verification**:
- ✅ System detected hidden chest via initial scan (Initial=64)
- ✅ System calculated net = 0 (no actual production)
- ✅ Passthrough detection triggered
- ✅ Diamonds excluded from cached rates
- ✅ In CACHED mode, PreFab won't request diamonds from Overworld (correct behavior)

---

## Test 3: Hidden Storage Production (Legitimate with Buffering)

**Goal**: Verify system correctly handles factories that use internal storage as production buffer.

### Setup

1. **Enter CM room**
2. **Build factory with buffer chest**:
   ```
   [Importer #1] → [Furnace Array: 10 Furnaces] → [Buffer Chest] → [Exporter #1]
   ```
   - 10 furnaces in parallel
   - Buffer chest collects all furnace outputs
   - Exporter pulls from buffer chest

3. **Configure PreFab** (same as Test 1)

4. **Prepare inputs**:
   - NORTH chest: 640 Coal (more resources for longer simulation)
   - SOUTH chest: Empty

### Execute Test

1. **Start Simulation**

2. **During simulation** (~60 seconds):
   - Factory will produce iron ingots faster than Exporter can push
   - Buffer chest will accumulate iron ingots (internal storage increase)

3. **Finish Simulation**

### Expected Results

**Console logs**:
```
[FPSCompress]   minecraft:coal: Initial=0, Final=0, Imported=640, Exported=0, Net=-640
[FPSCompress]   minecraft:iron_ingot: Initial=0, Final=128, Imported=0, Exported=192, Net=+320
```

**Calculation breakdown**:
- Iron produced by furnaces: 320 ingots total
- Iron exported via Exporter: 192 ingots
- Iron remaining in buffer chest: 128 ingots
- Formula: `Net = (128-0) + (192-0) = +320` ✓

**GUI**:
- State: CACHED
- Cached Rates:
  - Coal: -1.067/t (10 furnaces consume faster)
  - Iron Ingot: +0.533/t (true production rate including buffered items)

**Verification**:
- ✅ System accounts for buffered items (Final=128)
- ✅ True production rate calculated (320 total, not just 192 exported)
- ✅ In CACHED mode, PreFab will produce at correct rate (0.533/t)

---

## Test 4: Pure Passthrough (No Production)

**Goal**: Verify system detects when factory just moves items without production.

### Setup

1. **Enter CM room**
2. **Build passthrough pipeline**:
   ```
   [Importer #1] → [Chest] → [Hopper] → [Exporter #1]
   ```
   - No processing (no furnace, no crafting)
   - Just storage and transport

3. **Configure PreFab** (same as Test 1)

4. **Prepare inputs**:
   - NORTH chest: 64 Iron Ingots
   - SOUTH chest: Empty

### Execute Test

1. **Start Simulation**
2. **Wait** (items will flow through pipeline)
3. **Finish Simulation**

### Expected Results

**Console logs**:
```
[FPSCompress]   minecraft:iron_ingot: Initial=0, Final=64, Imported=64, Exported=0, Net=0
[FPSCompress] Passthrough detected (net production = 0) - resetting to BUILDING
```

**Calculation**:
- `Net = (64-0) + (0-64) = 0` (items imported, stored, not exported yet)

**GUI**:
- State: **BUILDING** (reset, no caching)
- Last Simulation Result: "Passthrough (no net production)"

**Verification**:
- ✅ System detected net = 0
- ✅ No rates cached (passthrough has no production)
- ✅ PreFab reset to BUILDING (player must reconfigure)

---

## Test 5: Mixed Production (Multiple Resources)

**Goal**: Verify system handles complex factories with multiple inputs/outputs.

### Setup

1. **Enter CM room**
2. **Build multi-resource factory**:
   ```
   [Importer #1: Coal] → [Furnace Array] → [Exporter #1: Iron Ingot]
   [Importer #2: Logs] → [Furnace Array] → [Exporter #2: Charcoal]
   ```

3. **Configure PreFab**:
   - NORTH: PULL ITEMS → Importer #1
   - SOUTH: PUSH ITEMS → Exporter #1
   - EAST: PULL ITEMS → Importer #2
   - WEST: PUSH ITEMS → Exporter #2

4. **Prepare inputs**:
   - NORTH chest: 128 Coal + 64 Iron Ore
   - EAST chest: 128 Oak Logs
   - SOUTH chest: Empty
   - WEST chest: Empty

### Execute Test

1. **Start Simulation**
2. **Wait** (~60 seconds for mixed production)
3. **Finish Simulation**

### Expected Results

**Console logs**:
```
[FPSCompress]   minecraft:coal: Initial=0, Final=0, Imported=128, Exported=0, Net=-128
[FPSCompress]   minecraft:iron_ore: Initial=0, Final=0, Imported=64, Exported=0, Net=-64
[FPSCompress]   minecraft:iron_ingot: Initial=0, Final=0, Imported=0, Exported=64, Net=+64
[FPSCompress]   minecraft:oak_log: Initial=0, Final=0, Imported=128, Exported=0, Net=-128
[FPSCompress]   minecraft:charcoal: Initial=0, Final=0, Imported=0, Exported=128, Net=+128
```

**GUI**:
- State: CACHED
- Cached Rates (5 resources):
  - Coal: -X/t (input)
  - Iron Ore: -Y/t (input)
  - Iron Ingot: +Z/t (output)
  - Oak Log: -A/t (input)
  - Charcoal: +B/t (output)

**Verification**:
- ✅ All 5 resources tracked independently
- ✅ Rates calculated correctly for each
- ✅ PreFab entered CACHED mode with multiple rates

---

## Test 6: Performance Test (Large Room)

**Goal**: Verify async scanning doesn't cause lag in large rooms.

### Setup

1. **Place GIANT Compact Machine** (13×13×13 if available, or largest size)
2. **Enter CM room**
3. **Fill room with storage**:
   - Place 50+ chests/barrels (scattered throughout)
   - Fill random chests with various items
   - Add 20+ furnaces
   - Add Importer/Exporter as usual

4. **Configure PreFab** (standard setup)

### Execute Test

1. **Start Simulation**
2. **Observe game behavior**:
   - Can you move around?
   - Does chat respond?
   - Check F3 debug screen: TPS should stay ~20
3. **Check logs for scan time**:
   ```
   [FPSCompress] Scanned 78 BlockEntities in room bounds [...] (time: ~100ms)
   ```

### Expected Results

**Performance**:
- No visible lag during scan
- Game remains playable (you can move, open GUIs)
- Scan completes in <500ms even with 100+ BlockEntities
- TPS stays stable (19-20)

**Verification**:
- ✅ Async scanning works (main thread not blocked)
- ✅ Performance acceptable even in large rooms
- ✅ All BlockEntities scanned successfully

---

## Test 7: HALTED State Recovery

**Goal**: Verify HALTED state doesn't reload CM chunks (optimization) and resets to BUILDING.

### Setup

1. **Use PreFab from Test 1 in CACHED mode**
2. **Remove input chest** (or empty it)

### Execute Test

1. **Wait for PreFab to enter HALTED** (input starved):
   - Status GUI will show "State: HALTED"
   - Message shown: "Input starved: 3and (128 needed)"

2. **Check CM dimension**:
   - Verify chunks are UNLOADED (use `/forge chunks` or dimension viewer mod)

3. **Fix Overworld side**:
   - Place chest with 128 Coal on NORTH face

4. **Reset Cache**:
   - Open Status GUI
   - Button says "Reset Cache"
   - Click "Reset Cache" (requires two clicks for confirmation: first shows "Are you sure?")

### Expected Results

**Console logs**:
```
[FPSCompress] PreFab at [...] entered HALTED state (input starved)
[FPSCompress] Chunks remain UNLOADED (HALTED optimization)
[FPSCompress] Retrying input pull with exponential backoff: 2 ticks
[FPSCompress] PreFab at [...] reset to BUILDING mode (chunks remain unloaded)
```

**GUI (Before Reset)**:
- State: HALTED (red)
- Message: "Input starved: 3and (128 needed)"
- **Cached Rates visible** (shows what factory needs):
  - Header: "Factory Requirements (HALTED):"
  - Coal: -0.213/t (red, needs input)
  - Iron Ingot: +0.107/t (green, produces output)
- Button: "Reset Cache"

**GUI (After Reset)**:
- State: BUILDING (yellow)
- Cached rates cleared
- Button: "Start Simulation"
- Chat message: "Reset cache to building mode"

**Verification**:
- ✅ CM chunks stayed unloaded during HALTED
- ✅ Exponential backoff prevents spam (retry intervals: 1, 2, 4, 8, 16... ticks)
- ✅ "Reset Cache" button clears cached rates
- ✅ PreFab returns to BUILDING (player must reconfigure and re-simulate)
- ✅ Player can now fix factory layout, change face configs, etc. before re-simulating

---

## Test 8: Crash Recovery (NBT Persistence)

**Goal**: Verify initial/final state survives server restart.

### Setup

1. **Start simulation** (but don't finish)
2. **Check NBT** (while SIMULATING):
   ```bash
   # View PreFab NBT data
   /data get block <prefab_pos>
   ```
   - Should see `deltaTracker` with `initialState` values

3. **Save and quit**

4. **Restart world**

5. **Check PreFab state**:
   - Open Status GUI
   - Verify still in SIMULATING state
   - Verify live stats still accurate

### Expected Results

**Console logs on load**:
```
[FPSCompress] Loaded PreFab at [...] in state SIMULATING
[FPSCompress] Delta tracker has 3 tracked resources
```

**Verification**:
- ✅ State persisted correctly
- ✅ Delta tracker data survived restart
- ✅ Can continue simulation after reload

---

## Debugging Tips

### Enable Verbose Logging

**Edit `fpscompress-template-1.21.11/src/main/resources/META-INF/mods.toml`**:
```toml
[logging]
level = "debug"  # Change from "info" to "debug"
```

Rebuild mod for detailed logs.

### Common Issues

**Issue**: "Room center not cached for room: ..."
- **Cause**: PreFab not linked to CM room
- **Fix**: Re-place PreFab, ensure upgraded from valid CM block

**Issue**: "Machine Wall not found in direction ..."
- **Cause**: Room bounds detection failed or wrong wall block ID
- **Fix**: 
  1. Check logs for "Found wall block: compactmachines:..." message
  2. System tries multiple IDs: `solid_wall`, `wall`, `machine_wall`
  3. If none found, use `/setblock` to verify wall block ID:
     ```
     /execute in compactmachines:compact_world run setblock ~ ~ ~ air
     ```
     Then check what block was there before
  4. Ensure CM chunks are loaded (force-load messages should appear in logs)

**Issue**: "Scan failed - check logs"
- **Cause**: Async scan threw exception
- **Fix**: Check `logs/latest.log` for stack trace, likely capability query failed

**Issue**: Cached rates are wrong
- **Cause**: Factory not at steady state during simulation
- **Fix**: Run longer simulation (let machines fill up first)

### Diagnostic: Check Wall Block ID

If you're getting "Machine Wall not found" errors, verify which wall block CM uses:

**Method 1 - Check logs**:
```bash
grep "Found wall block" logs/latest.log
```
Should show: `[FPSCompress] Found wall block: compactmachines:solid_wall`

**Method 2 - In-game inspection**:
1. Enter CM room (`/execute in compactmachines:compact_world run tp -1016 2 -1016`)
2. Look at wall block with F3+H (advanced tooltips enabled)
3. Note the block ID (e.g., `compactmachines:solid_wall`)

**Method 3 - Debug scan output**:
Look for these logs during startSimulation:
```
[FPSCompress] Scanning west at distance 0: minecraft:air at (-1017, 2, -1016)
[FPSCompress] Scanning west at distance 1: compactmachines:solid_wall at (-1018, 2, -1016)
[FPSCompress] Found wall block compactmachines:solid_wall in direction west at distance 1
```

If the system can't find walls, the error will show what blocks it DID find:
```
[ERROR] Machine Wall (compactmachines:solid_wall) not found in direction west after 20 blocks.
Scanned from: (-1016, 2, -1016)
Blocks found:
  Distance 1: minecraft:air
  Distance 2: minecraft:air
  Distance 3: minecraft:furnace
```

### Log Analysis

**Search for key events**:
```bash
# Initial scan
grep "Initial scan complete" logs/latest.log

# Final scan
grep "Final scan complete" logs/latest.log

# Rate calculation
grep "Resource.*Initial=.*Final=" logs/latest.log

# Passthrough detection
grep "Passthrough" logs/latest.log
```

**Expected log sequence**:
1. "starting initial scan (chunks loaded)"
2. "Scanned X BlockEntities"
3. "Initial scan complete: Y resource types"
4. "entered SIMULATING state"
5. ... (player waits) ...
6. "unloaded chunks for final scan"
7. "Final scan complete: Y resource types"
8. "Resource X: Initial=..., Final=..., Imported=..., Exported=..., Net=..."
9. "finished simulation, cached Z rates"

---

## Success Criteria

### All Tests Pass If:

✅ **Test 1**: Legitimate factory works, Initial/Final = 0  
✅ **Test 2**: Hidden storage detected, passthrough excluded  
✅ **Test 3**: Buffered production calculated correctly  
✅ **Test 4**: Pure passthrough resets to BUILDING  
✅ **Test 5**: Multi-resource factories track all rates  
✅ **Test 6**: No lag during scans, game playable  
✅ **Test 7**: HALTED keeps chunks unloaded  
✅ **Test 8**: State survives restart  

### Overall Verification:

- No crashes or exceptions
- All linters pass (checkstyle, spotbugs)
- Logs show correct formula calculations
- GUI displays accurate states
- Performance acceptable (<500ms scan time)

---

## Next Steps After Testing

### If Tests Pass:
1. Update `TODO.md` to mark Phase 5 complete
2. Proceed to Phase 6 (Wrench control & state transitions)
3. Consider adding unit tests for `InventoryScanner` class

### If Tests Fail:
1. Capture logs and screenshots
2. Identify which test failed
3. Check "Debugging Tips" section
4. Review code in relevant phase (see plan document)
5. File issue if bug found

---

## Advanced Testing (Optional)

### Modded Block Compatibility

Test with other mods' machines:
- Thermal Expansion machines
- Mekanism machines
- Create contraptions

Expected: Scanner should detect their inventories via capabilities.

### Fluid & Energy Testing

1. Place Fluid Tanks in CM room (from Mekanism, Thermal, etc.)
2. Place Energy Cells (from Ender IO, Mekanism, etc.)
3. Run simulation
4. Verify fluids/energy tracked:
   ```
   minecraft:lava: Initial=16000mb, Final=0mb, Net=-16000mb
   forge:energy: Initial=100000FE, Final=50000FE, Net=-50000FE
   ```

### Stress Testing

- **100+ BlockEntities**: Fill giant room, verify no stack overflow
- **1000+ items per chest**: Test large inventories don't slow scan
- **Multiple PreFabs**: Test 5+ PreFabs in same world
- **Chunk unload during scan**: Force chunk unload while scanning (should fail gracefully)

---

**Happy Testing!** 🧪

If you encounter issues not covered in this guide, check:
- `VALIDATION_DELTA_ACCOUNTING.md` (formula specification)
- `ARCHITECTURE_CONDUIT.md` (system architecture)
- `logs/latest.log` (detailed runtime logs)
