# Phase 1 Complete - Quick Test Guide

## Status: ✅ All Parts Complete (A, B, C)

### What to Test

**Test 1: Adjacent Block Detection**
1. Launch Minecraft: `./gradlew runClient`
2. Create new world
3. Get items: `/give @s fpscompress:tps_cache_upgrade` and `/give @s compactmachines:machine_small`
4. Place CM block, upgrade with TPS item → PreFab
5. Place chests around PreFab
6. **Right-click PreFab (empty hand)** → Should show debug output with capabilities

**Test 2: Face Configuration GUI**
1. Get Simulation Wrench: `/give @s fpscompress:simulation_wrench`
2. **Shift+Right-click PreFab with wrench** → GUI opens
3. Click face direction buttons (NORTH/SOUTH/etc.)
4. Change mode (DISABLED/PULL/PUSH)
5. Change filter (ALL/ITEMS/FLUIDS/ENERGY)
6. Click Save → Configs saved to server

**Test 3: NBT Persistence**
1. Configure faces in GUI (set different modes/filters)
2. Click Save
3. Break PreFab → Drops as item
4. Place PreFab elsewhere
5. Shift+Right-click with wrench → **Verify configs preserved**

### Expected Results
- ✅ Debug shows adjacent block capabilities
- ✅ GUI opens on shift+right-click
- ✅ Face selection updates button highlights
- ✅ Mode/filter buttons show active state (green)
- ✅ Save closes GUI and shows "PreFab configuration saved!"
- ✅ Configs persist after break/place

### Known Issues
- PreFab texture: Purple/black checkerboard (not implemented yet - Phase 7)
- No Importer/Exporter linking (Phase 2 feature)

---

**Phase 1 Complete!**
Ready for Phase 2: Importer/Exporter blocks
