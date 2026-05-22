# Troubleshooting Guide

Common issues and solutions for FPSCompress.

## Installation Issues

### Problem: "Mod won't load / Crashes on startup"

**Possible causes**:
- Missing Compact Machines dependency
- Wrong Minecraft/NeoForge version
- Java version mismatch

**Solutions**:
1. Check Minecraft version is 1.21.1
2. Install Compact Machines mod (required dependency)
3. Verify NeoForge 21.1.221+ is installed
4. Ensure Java 21 is installed (`java -version`)
5. Check crash log for specific error

### Problem: "Compact Machines not found"

**Cause**: Compact Machines mod not installed

**Solution**:
1. Download Compact Machines for 1.21.1
2. Place in `mods/` folder
3. Restart Minecraft

---

## PreFab Setup Issues

### Problem: "Can't craft PreFab Upgrade Template"

**Solution**:
- Check JEI (Just Enough Items) for recipe
- Ensure all materials are available
- Recipe may require specific mod progression

### Problem: "Right-clicking CM with PreFab Upgrade Template doesn't work"

**Checklist**:
- ✅ CM block placed in world (not in inventory)
- ✅ Holding PreFab Upgrade Template item
- ✅ Right-clicking (not shift-right-clicking)
- ✅ Not in spectator / adventure mode

### Problem: "Face Configuration GUI won't open"

**Cause**: Not using correct method

**Solution**:
1. Hold Simulation Wrench (not PreFab Upgrade Template)
2. **Right-click** PreFab
3. Make sure PreFab is placed (not just CM)

---

## Importer/Exporter Issues

### Problem: "No Importers/Exporters in target dropdown"

**Cause**: No gates placed in CM dimension

**Solution**:
1. Enter CM dimension (right-click CM with Personal Shrinking Device)
2. Place at least one Importer or Exporter
3. Exit CM dimension
4. Reopen PreFab GUI - dropdown should populate

### Problem: "Importers/Exporters have no UUID/Name in GUI"

**Cause**: Gates not named with frequency system

**Solution** (optional):
1. Hold an item (e.g., Coal)
2. Right-click Importer/Exporter
3. Gate now shows as "Coal Importer" in dropdown

If still showing UUID only:
- This is normal behavior (UUID is the unique ID)
- Frequency system just adds a friendly name

---

## Resource Transport Issues

### Problem: "Resources not entering factory (PULL face)"

**Diagnostic checklist**:

**1. PreFab face configuration**:
- ✅ Face set to PULL mode (not DISABLED)?
- ✅ Face linked to Importer (check target dropdown)?
- ✅ Resource filter set to ITEMS (or correct type)?

**2. Overworld setup**:
- ✅ Chest adjacent to PreFab face?
- ✅ Chest has items to extract?
- ✅ Chest exposes IItemHandler capability (vanilla chests do)?

**3. CM dimension setup**:
- ✅ Importer placed inside CM?
- ✅ Importer accessible (not broken)?
- ✅ Machines connected to Importer?

**4. PreFab state**:
- ✅ PreFab in SIMULATING or CACHED state (not BUILDING)?
- ✅ PreFab not in HALTED state?

### Problem: "Resources not exiting factory (PUSH face)"

**Diagnostic checklist**:

**1. PreFab face configuration**:
- ✅ Face set to PUSH mode (not DISABLED)?
- ✅ Face linked to Exporter (check target dropdown)?
- ✅ Resource filter set to ITEMS?

**2. CM dimension setup**:
- ✅ Exporter placed next to machine output?
- ✅ Machine producing items?
- ✅ Exporter can extract from machine?

**3. Overworld setup**:
- ✅ Chest adjacent to PreFab face?
- ✅ Chest has space for items?
- ✅ Chest not full?

**4. PreFab state**:
- ✅ PreFab in SIMULATING or CACHED state?
- ✅ PreFab not in HALTED?

### Problem: "Items disappearing / Not reaching destination"

**Possible causes**:
1. **Importer buffer full**: Machines not pulling fast enough
   - Solution: Add more machines, speed up processing

2. **Exporter not pulling**: Adjacent machine has no items
   - Solution: Verify machine is producing, check machine output

3. **Wrong face linked**: PULL face linked to Exporter (wrong)
   - Solution: PULL → Importer, PUSH → Exporter

4. **Item voiding** (bug):
   - Solution: Report bug with reproduction steps

---

## State Machine Issues

### Problem: "PreFab stuck in BUILDING, can't start simulation"

**Possible causes**:
- No faces configured (all DISABLED)
- No Importers/Exporters placed

**Solution**:
- Configure at least one PULL or PUSH face
- Link faces to Importers/Exporters in CM
- Right-click with Simulation Wrench to start

### Problem: "PreFab immediately enters HALTED after SIMULATING"

**Cause**: Input/output problem detected during rate measurement

**Common reasons**:
1. Input chest empty during simulation
2. Output chest filled during simulation
3. Importer/Exporter buffer full

**Solution**:
- Stock input chests before CACHED mode
- Use larger output chests (barrels, deep storage)
- Fix issue, then right-click with wrench to resume

### Problem: "Can't exit SIMULATING state"

**Cause**: Not using correct method

**Solution**:
- Right-click PreFab with Simulation Wrench (no shift)
- Wait for click to register (may have slight delay)

### Problem: "PreFab stuck in HALTED"

**Cause**: Underlying issue not fixed

**Steps to recover**:
1. **Diagnose**: Check input/output chests
2. **Fix**: Restock inputs, clear outputs
3. **Resume**: Right-click with wrench → Goes to SIMULATING
4. **Wait**: Let simulate for 10-30 seconds
5. **Finish**: Right-click again → Back to CACHED

---

## Performance Issues

### Problem: "Chunks not unloading in CACHED mode"

**Verification**:
1. Enter CACHED mode
2. Press F3 (debug screen)
3. Check "C:" value (total chunks loaded)
4. Value should be lower than during SIMULATING

**If chunks still loaded**:
- CM dimension may be loaded for another reason:
  - Player inside CM dimension
  - Another CM machine active in same dimension
  - Chunk loader nearby

**Solution**:
- Ensure no players in CM dimension
- Check for other active PreFabs/CMs
- Verify specific room chunks using F3 coordinates

### Problem: "Server TPS still low with PreFabs in CACHED"

**Possible causes**:
1. Other lag sources (unrelated to FPSCompress)
2. PreFabs still in SIMULATING (chunks loaded)
3. Other mods causing lag

**Debugging**:
- Use profiler (`/forge tps` or similar command)
- Check which dimensions are loaded
- Verify PreFabs are in CACHED (not SIMULATING)

---

## Rate Measurement Issues

### Problem: "Measured rates seem inaccurate"

**Common causes**:

**1. Simulation too short**:
- Simulated < 30 seconds → Inaccurate
- Solution: Simulate for 60+ seconds

**2. Input starvation during measurement**:
- Input chest ran out during SIMULATING
- Solution: Stock chests well before simulation

**3. Factory not at steady state**:
- Started simulation immediately after setup
- Solution: Let factory run for a few seconds before measurement

**4. Player interference**:
- Removed items from chests during simulation
- Solution: Don't touch factory during SIMULATING

### Problem: "Negative production rates"

**This is normal.**

**Explanation**:
- Input resources have negative rates (consumed, not produced)
- Example: Coal rate = -1.0/tick (factory consumes 1 coal/tick)

**Not a bug**: Negative = input, Positive = output

### Problem: "Rate of 0.0 for a resource"

**Possible causes**:
1. **Passthrough**: Item enters and exits without change
   - Example: [Importer] → [Chest] → [Exporter]
   - Net production = 0

2. **No activity**: Resource not used during simulation
   - Solution: Verify factory uses that resource

3. **Exact balance**: Rare, but possible (e.g., recycling loop)

---

## Debug Commands

### `/fpscompress debug`

**Usage**:
```
/fpscompress debug
```

**Shows**:
- PreFab states (BUILDING/SIMULATING/CACHED/HALTED)
- Face configurations
- Linked Importers/Exporters
- Cached rates (if available)

**Note**: Debug commands may require operator permission

### `/fpscompress reload`

**Usage** (future feature):
```
/fpscompress reload
```

**Purpose**:
- Reload config files
- Refresh Importer/Exporter registry
- Force re-scan of CM dimension

---

## Known Issues

### Issue: "Fluids/Energy not supported"

**Status**: Not yet implemented (MVP limitation)

**Workaround**: Use item-based transport only

**Future**: Fluid/energy support planned for post-MVP

### Issue: "AE2 integration not working"

**Status**: Not yet implemented

**Workaround**: Use vanilla chests/hoppers

**Future**: AE2 integration planned for post-MVP

### Issue: "PreFab-as-Item loses data"

**Status**: Should be fixed in v0.1.0-alpha

**If still occurs**:
- Report bug with reproduction steps
- Include NBT data dump

---

## Getting Help

### Log Files

**Location**: `.minecraft/logs/latest.log`

**Look for**:
- `[FPSCompress]` log lines
- Error stack traces
- Warning messages

**Include in bug reports**:
- Full log file (or relevant excerpt)
- Steps to reproduce
- Mod version

### Bug Reports

**Report at**: GitHub Issues (check mod page for link)

**Include**:
1. FPSCompress version
2. Minecraft version
3. NeoForge version
4. Compact Machines version
5. Steps to reproduce
6. Log file
7. Screenshots (if applicable)

### Common Log Errors

**Error: "Can't find Importer UUID abc-123"**
- Importer was broken or moved
- Solution: Replace Importer, reconfigure PreFab face

**Error: "CM room not found for code XYZ"**
- CM dimension issue
- Solution: Re-enter CM to initialize, replace PreFab

**Error: "Capability not found"**
- Adjacent block doesn't support IItemHandler
- Solution: Use supported blocks (chests, hoppers, machines)

---

## See Also

- [Getting Started](Getting-Started) - First-time setup
- [State Machine Guide](State-Machine-Guide) - Understanding states
- [Face Configuration](Face-Configuration) - Configuring faces correctly
- [Cached Production](Cached-Production) - How rates work
