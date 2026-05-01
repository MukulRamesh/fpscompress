# Phase 1 Testing Guide

## Overview
Testing face configuration and adjacent block detection for PreFab blocks.

## Test Prerequisites
- Minecraft client running with FPSCompress mod loaded
- Creative mode recommended
- Compact Machines mod installed (for CM blocks)

## Test 1: Face Config NBT Persistence

**Goal**: Verify face configurations save/load correctly from NBT

**Steps**:
1. Place a PreFab block in the world (if not available, place CM block first and upgrade with TPS Cache Upgrade item)
2. Right-click the PreFab without holding an item
3. Observe the debug output in chat (should show 6 faces, all DISABLED initially)
4. Break the PreFab block
5. Pick up the item (should preserve NBT)
6. Place the PreFab block elsewhere
7. Right-click again
8. **Expected**: Face configs should be preserved (currently all DISABLED since we haven't implemented GUI yet)

**Success Criteria**:
- ✅ PreFab block can be placed
- ✅ Right-click shows debug output
- ✅ Block drops as item when broken
- ✅ Item can be placed again
- ✅ NBT data persists across break/place cycles

---

## Test 2: Adjacent Block Detection (Main Test)

**Goal**: Verify PreFab can detect adjacent blocks and their capabilities

**Setup**:
1. Place a PreFab block
2. Place vanilla blocks adjacent to PreFab on all 6 faces:
   - NORTH: Chest (has IItemHandler)
   - SOUTH: Furnace (has IItemHandler + IEnergyStorage if mods present)
   - EAST: Barrel (has IItemHandler)
   - WEST: Hopper (has IItemHandler)
   - UP: Another chest
   - DOWN: Leave empty or place another container

**Test Procedure**:
1. Right-click the PreFab block without holding an item
2. Observe chat messages

**Expected Output** (example):
```
§6=== PreFab Adjacent Blocks ===
§6NORTH: §7Chest §a[Items:✓ Fluids:✗ Energy:✗]
§6SOUTH: §7Furnace §a[Items:✓ Fluids:✗ Energy:✗]
§6EAST: §7Barrel §a[Items:✓ Fluids:✗ Energy:✗]
§6WEST: §7Hopper §a[Items:✓ Fluids:✗ Energy:✗]
§6UP: §7Chest §a[Items:✓ Fluids:✗ Energy:✗]
§6DOWN: §8No block entity
```

**Success Criteria**:
- ✅ All 6 directions checked
- ✅ Blocks with IItemHandler show "Items:✓"
- ✅ Blocks without capabilities show "✗"
- ✅ Empty spaces show "No block entity"
- ✅ Block names display correctly

**Edge Cases to Test**:
- Place PreFab next to non-container blocks (e.g., stone, dirt) - should show "No block entity"
- Place PreFab next to modded containers (if available) - should detect capabilities
- Place PreFab in the air - should show all directions as empty

---

## Test 3: PreFab Status Display

**Goal**: Verify PreFab shows correct status information

**Steps**:
1. Right-click PreFab block
2. Observe status display after the adjacent blocks debug output

**Expected Output**:
```
§6=== PreFab Status ===
§7State: §eBUILDING
§7Room: §cNot linked (upgrade from CM first)
§8(Hold PSD and right-click to enter)
```

**Success Criteria**:
- ✅ Machine state shows BUILDING (yellow color)
- ✅ Room shows "Not linked" if PreFab not upgraded from CM
- ✅ Status display is readable and formatted correctly

---

## Test 4: PreFab Creation Flow

**Goal**: Test the full PreFab creation process

**Method 1 - If TPS Cache Upgrade Item exists**:
1. Place a Compact Machines block
2. Right-click CM block with TPS Cache Upgrade item
3. CM should transform into PreFab block
4. Right-click PreFab to verify it works

**Method 2 - Direct placement** (if PreFab item exists):
1. Get PreFab block from creative inventory
2. Place it in the world
3. Right-click to verify it works

**Success Criteria**:
- ✅ PreFab block can be obtained/placed
- ✅ PreFab has correct texture
- ✅ Right-click triggers debug output
- ✅ PreFab is indestructible by explosions (like bedrock)

---

## Test 5: Face Configuration Storage (Manual Verification)

**Goal**: Verify face configs are stored in NBT (manual check)

**Steps**:
1. Place PreFab block
2. Break it and check NBT data (use `/data get entity @p SelectedItem` while holding the item)

**Expected NBT Structure**:
```json
{
  "components": {
    "minecraft:block_entity_data": {
      "state": "BUILDING",
      "faceConfigs": {
        "north": {
          "mode": "DISABLED",
          "resourceType": "ALL"
        },
        "south": { ... },
        ... (6 faces total)
      }
    }
  }
}
```

**Success Criteria**:
- ✅ `faceConfigs` tag exists in NBT
- ✅ All 6 faces present (north, south, east, west, up, down)
- ✅ Each face has `mode` and `resourceType` fields
- ✅ Default values are DISABLED and ALL

---

## Known Limitations (Phase 1)

These features are **NOT** implemented yet and should NOT be expected to work:
- ❌ GUI for configuring faces (Phase 1 Part C - not done yet)
- ❌ Importer/Exporter blocks (Phase 2)
- ❌ Resource transport (Phase 3)
- ❌ Rate measurement (Phase 4)
- ❌ Cached production (Phase 5)
- ❌ Wrench state control (Phase 6)

---

## Troubleshooting

**Problem**: PreFab block not available in creative inventory
- **Solution**: Check that FPSCompress mod is loaded (`/mods list`)
- **Solution**: Try crafting or using commands to get the block

**Problem**: Right-click doesn't show debug output
- **Solution**: Make sure you're in **server side** (not client-only)
- **Solution**: Check console logs for errors

**Problem**: Capabilities not detected
- **Solution**: Verify the adjacent block is a BlockEntity (chests, furnaces, etc.)
- **Solution**: Some blocks only expose capabilities on certain sides

**Problem**: Game crashes on right-click
- **Solution**: Check console for stack trace
- **Solution**: Verify all imports are correct in the code
- **Solution**: Run `./gradlew build` to check for compilation errors

---

## Success Summary

Phase 1 Parts A & B are successful if:
1. ✅ Face configs save/load from NBT
2. ✅ Adjacent block detection works for all 6 directions
3. ✅ Capabilities (Items/Fluids/Energy) are detected correctly
4. ✅ Debug output displays in chat
5. ✅ PreFab block is placeable and breakable
6. ✅ Item preserves data when broken

---

## Next Steps After Testing

If all tests pass:
- ✅ Proceed to **Phase 1 Part C** - Simple face config GUI
- ✅ Add Shift+Right-click with Simulation Wrench to open GUI
- ✅ Implement network packets for client-server sync

If tests fail:
- 🐛 Debug the failing test
- 🔧 Fix the issue
- 🔄 Recompile and retest
