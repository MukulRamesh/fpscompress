# Quick Start: Testing Capability Registration

**For the full detailed plan, see: `TESTING_CAPABILITY_REGISTRATION.md`**

---

## What Changed

Phase 2 implemented:
- ✅ Unlimited storage (removed 1,728 item / 50,000 mB / 1,000,000 FE limits)
- ✅ Capability registration (hoppers, pipes, AE2 can interact with PreFabs)
- ✅ Smart extraction (auto-extracts when only 1 resource type stored)

## Quick Test (5 minutes)

### 1. Start Minecraft
- Load world with NeoForge + Compact Machines + FPSCompress

### 2. Run Automated Test
```
/op <your_username>
/testbuffer
```

**Expected**: All tests show green checkmarks (§a✓)

### 3. Test Hopper Integration
```
/give @p fpscompress:prefab_machine
```
- Place PreFab block
- **Right-click PreFab** to view storage (should show "None")
- Place hopper above PreFab
- Put items in hopper (e.g., 64 cobblestone)
- Wait 5 seconds
- **Right-click PreFab again** to view storage
- Should show: "Items: 64 total (1 types)"

### 4. Test Smart Extraction
- Remove top hopper
- Place hopper below PreFab
- Items should extract back to hopper (single item type = smart extraction works)
- **Right-click PreFab** - storage should decrease as hopper extracts

### 5. Test Multi-Type Blocking
- Add different item type (e.g., iron ingots) to PreFab via another hopper
- **Right-click PreFab** - should show "Items: X total (2 types)"
- Bottom hopper should STOP extracting (correct behavior - prevents wrong extraction)

---

## Success Criteria

✅ **PASS** if:
1. `/testbuffer` shows all green checkmarks
2. Hoppers can insert items into PreFab
3. Hopper extracts items when only 1 type stored
4. Hopper stops extracting when 2+ types stored
5. No crashes or errors in logs

❌ **FAIL** if:
- Any red X marks (§c✗) in `/testbuffer` output
- Hoppers don't transfer items
- Game crashes
- "Buffer full" warnings in logs

---

## Common Issues

### "Command not found"
**Solution**: You're not OP. Run `/op <username>` first

### Hopper doesn't insert items
**Possible causes**:
1. **PreFab has no room code** - Must upgrade from CM with TPS Cache Upgrade first
2. **Capability not created** - Check logs for: `Created ItemHandler capability for PreFab at ...`
3. **Wrong routing state** - Should see: `Routing state changed: PHYSICAL -> VIRTUAL`

**Debug steps**:
1. Open `logs/latest.log`
2. Search for "Created ItemHandler capability" - should appear when placing PreFab
3. Search for "Routed minecraft:" - should appear when hopper transfers items
4. If you see routing messages, it worked! (Items are in virtual buffer, not visible)

### How do I see what's in the PreFab?
**Just right-click it!** The PreFab will display storage stats in chat:
```
§6=== PreFab Virtual Storage ===
§7Items: §a64 total §7(§b1 types§7)
§7  - cobblestone: §a64
§7Fluids: §eNone
§7Energy: §eNone
§7Room: §3ABCD-1234-5678
§8(Hold PSD and right-click to enter)
```

Success indicators:
- ✅ Items leave the hopper
- ✅ Right-click shows item count increasing
- ✅ Logs show: `Routed minecraft:cobblestone x64 to virtual buffer`

### Hopper won't extract items
**Check**:
1. Is there only ONE item type in PreFab? (Smart extraction only works with 1 type)
2. Did you add items via top hopper first?
3. Check logs for: `Smart extraction: extracted minecraft:...`

If logs show `Extract from virtual buffer by slot not supported (multiple types or empty)`:
- ✅ This is **correct** - you have multiple item types (extraction blocked for safety)

---

## Full Test Plan

For comprehensive testing (15 tests with edge cases), see:
**`TESTING_CAPABILITY_REGISTRATION.md`**
