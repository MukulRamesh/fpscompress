# Quick Phase 1 Test Checklist

## 🎯 Main Test: Adjacent Block Detection

**What we're testing**: PreFab can detect adjacent blocks and their capabilities

### Setup (In-Game):
1. Open creative mode
2. Get these blocks:
   - PreFab block (or CM block + TPS Cache Upgrade item)
   - Chest (x2)
   - Furnace
   - Hopper
   - Barrel

### Test Procedure:

**Step 1**: Place PreFab
```
Place a PreFab block in the world
(If you don't have PreFab item, place CM block and right-click with TPS Cache Upgrade)
```

**Step 2**: Arrange adjacent blocks
```
Place blocks around the PreFab:
  - North: Chest
  - South: Furnace  
  - East: Barrel
  - West: Hopper
  - Up: Another Chest
  - Down: Leave empty
```

**Step 3**: Test detection
```
Right-click the PreFab block (empty hand, no items)
```

**Step 4**: Read chat output
```
You should see:
  §6=== PreFab Adjacent Blocks ===
  §6NORTH: §7Chest §a[Items:✓ Fluids:✗ Energy:✗]
  §6SOUTH: §7Furnace §a[Items:✓ Fluids:✗ Energy:✗]
  §6EAST: §7Barrel §a[Items:✓ Fluids:✗ Energy:✗]
  §6WEST: §7Hopper §a[Items:✓ Fluids:✗ Energy:✗]
  §6UP: §7Chest §a[Items:✓ Fluids:✗ Energy:✗]
  §6DOWN: §8No block entity
  
  §6=== PreFab Status ===
  §7State: §eBUILDING
  §7Room: §cNot linked (upgrade from CM first)
```

---

## ✅ Pass Criteria

- [ ] All 6 directions show up in output
- [ ] Chests/Barrels/Hoppers show "Items:✓" (green checkmark)
- [ ] Empty spaces show "No block entity"
- [ ] PreFab state shows "BUILDING" (yellow)
- [ ] No crashes or errors

---

## 🧪 Bonus Tests (If time permits)

### Test: NBT Persistence
1. Break the PreFab block
2. Pick up the item
3. Place it elsewhere
4. Right-click again
5. **Expected**: Should still work (configs persist in item NBT)

### Test: Edge Cases
1. Place PreFab in the air (no adjacent blocks)
   - **Expected**: All 6 directions show "No block entity"
2. Place PreFab next to solid blocks (stone, dirt, etc.)
   - **Expected**: Shows "No block entity" (solid blocks aren't BlockEntities)

---

## 🐛 What to Report

If something breaks, report:
1. What you did (exact steps)
2. What you expected
3. What actually happened
4. Any error messages in chat or console
5. Screenshots if helpful

---

## 📝 Current Implementation Status

**What WORKS** (Phase 1 A & B):
- ✅ Face configuration data structures (FaceMode, ResourceFilter, FaceConfig)
- ✅ NBT serialization (face configs save/load)
- ✅ Adjacent block detection
- ✅ Capability queries (Items/Fluids/Energy)
- ✅ Debug output display

**What DOESN'T work yet** (Coming soon):
- ❌ GUI for configuring faces (Phase 1 Part C - next step)
- ❌ Importer/Exporter blocks (Phase 2)
- ❌ Actual resource transport (Phase 3)
- ❌ Rate measurement (Phase 4)
- ❌ Cached production (Phase 5)

---

## 🎮 How to Get PreFab Block

**Option 1**: Use TPS Cache Upgrade item
```
1. Place Compact Machines block
2. Right-click with "TPS Cache Upgrade" item
3. CM transforms into PreFab
```

**Option 2**: Get from creative inventory
```
Search for "prefab" or "fpscompress" in creative search
```

**Option 3**: Use command (if registered)
```
/give @p fpscompress:prefab_block
```

---

Good luck with testing! 🚀
