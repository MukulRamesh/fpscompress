# Testing Plan: Capability Registration with Unlimited Storage

**Date Created**: 2026-04-26  
**Implementation**: Phase 2 - Capability Registration for PreFab Blocks  
**Goal**: Verify that PreFab blocks support unlimited storage and external mod interaction via NeoForge capabilities

---

## Prerequisites

- **Minecraft Version**: 1.21.11 with NeoForge 21.11.38-beta
- **Required Mods** (for comprehensive testing):
  - Compact Machines (dependency)
  - At least one of: Pipez, Mekanism Pipes, or vanilla hoppers
  - Optional: AE2, Refined Storage (for advanced testing)
- **Debug Commands Available**:
  - `/testbuffer` - Runs automated unlimited storage test suite (requires OP permission)
  - `/fps_dev2 diagnostics` - Shows chunk loading state and routing info

---

## Test Suite

### 🧪 Test 1: Mod Loading and Capability Registration

**Goal**: Verify capabilities are registered without errors

**Steps**:
1. Start Minecraft with the mod loaded
2. Check the logs for capability registration message

**Expected Result**:
```
[INFO] Registering virtual buffer capabilities for Compact Machines and PreFabs
[INFO] Virtual buffer capabilities registered successfully
```

**Troubleshooting**:
- If missing: Check `CapabilityRegistration.java` is annotated with `@EventBusSubscriber`
- If errors: Check CMInterceptorImpl instantiation

---

### 🧪 Test 2: PreFab Block Placement and Interaction

**Goal**: Verify PreFab blocks can be placed and retain basic functionality

**Steps**:
1. Give yourself a PreFab block: `/give @p fpscompress:prefab_machine`
2. Place the block in the world
3. Try right-clicking without Personal Shrinking Device
4. Observe the message

**Expected Result**:
- Block places successfully
- Message: "§eYou need a Personal Shrinking Device to enter this PreFab"

**Pass/Fail**: ✅ PASS | ❌ FAIL

---

### 🧪 Test 3: Automated Unlimited Storage Test

**Goal**: Verify unlimited storage works via automated test suite

**Steps**:
1. Ensure you have OP permissions (if not: `/op <your_username>`)
2. Run the test command: `/testbuffer`
3. Observe the output in chat

**Expected Result**:
```
§e=== Testing Virtual Buffer Unlimited Storage ===
§b[TEST 1] Unlimited Item Storage
  §a✓ Added 10,000 items (old limit: 1,728)
  §a✓ Added 100,000 more items (total: 110,000)
  §a✓ Total items: 110,000 (unlimited!)
  §a✓ Multiple item types: 210,000 total items
§b[TEST 2] Unlimited Fluid Storage
  §a✓ Added 1,000,000 mB water (old limit: 50,000)
  §a✓ Added 5,000,000 more mB (total: 6,000,000)
  §a✓ Total fluid: 6,000,000 mB (unlimited!)
  §a✓ Multiple fluid types: 8,000,000 mB total
§b[TEST 3] Unlimited Energy Storage
  §a✓ Added 10,000,000 FE (old limit: 1,000,000)
  §a✓ Added 100,000,000 more FE (total: 110,000,000)
  §a✓ Total energy: 110,000,000 FE (unlimited!)
  §a✓ Stored 1,000,000,000 FE (1 billion!)
§a=== All unlimited storage tests complete! ===
```

**Actual Result**:
- All tests showed §a✓: YES / NO
- Any §c✗ failures: ___________

**Pass/Fail**: ✅ PASS | ❌ FAIL

---

### 🧪 Test 4: Hopper Item Insertion (IItemHandler Capability)

**Goal**: Verify external blocks can insert items via capabilities

**Steps**:
1. Give yourself a PreFab block: `/give @p fpscompress:prefab_machine`
2. Place the PreFab block in the world
3. **Right-click the PreFab** to view storage (should show "Items: None")
4. Place a hopper pointing into the PreFab (from above or side)
5. Put items in the hopper (e.g., 64 cobblestone)
6. Wait 5-10 seconds
7. **Right-click the PreFab again** to view updated storage

**Expected Result**:
- Items transfer from hopper to PreFab virtual buffer
- PreFab displays in chat:
  ```
  §6=== PreFab Virtual Storage ===
  §7Items: §a64 total §7(§b1 types§7)
  §7  - cobblestone: §a64
  ```
- Hopper should be empty (items successfully transferred)

**Actual Result**:
- Items shown in PreFab chat display: ___________
- Items transferred: YES / NO

**Pass/Fail**: ✅ PASS | ❌ FAIL

**Note**: Right-clicking PreFab shows storage stats in chat. No GUI needed!

---

### 🧪 Test 5: Smart Item Extraction (Single Item Type)

**Goal**: Verify smart extraction works when only one item type is stored

**Steps**:
1. Use a fresh PreFab block (newly placed)
2. Manually add items to PreFab via hopper:
   - Place hopper above PreFab
   - Put only iron ingots in the hopper (e.g., 64 iron ingots)
   - Wait for transfer to complete
3. Remove the top hopper
4. Place a hopper below the PreFab (to extract)
5. Observe the bottom hopper's inventory

**Expected Result**:
- Bottom hopper extracts iron ingots from PreFab
- Debug logs show: `[DEBUG] Smart extraction: extracted minecraft:iron_ingot x...`
- Items appear in bottom hopper

**Actual Result**:
- Extraction worked: YES / NO
- Items extracted to hopper: ___________

**Pass/Fail**: ✅ PASS | ❌ FAIL

---

### 🧪 Test 6: Smart Extraction Blocked (Multiple Item Types)

**Goal**: Verify extraction fails gracefully with multiple item types (prevents wrong extraction)

**Steps**:
1. Use a fresh PreFab block
2. Add TWO different item types via hopper:
   - Put both iron ingots and gold ingots in a hopper above PreFab
   - Wait for both to transfer to PreFab
3. Place a hopper below PreFab (extraction mode)
4. Wait 10 seconds
5. Check the bottom hopper

**Expected Result**:
- Bottom hopper does NOT extract items (stays empty)
- Debug logs show: `[DEBUG] Extract from virtual buffer by slot not supported (multiple types or empty)`
- This is **correct behavior** - prevents extracting wrong item type

**Actual Result**:
- Bottom hopper empty: YES / NO
- If items extracted (unexpected): ___________

**Pass/Fail**: ✅ PASS | ❌ FAIL

---

### 🧪 Test 7: Pipe Item Insertion (If Pipez/Mekanism installed)

**Goal**: Verify modded pipes can insert items

**Steps**:
1. Place a chest with items
2. Connect a pipe from chest to PreFab
3. Configure pipe to push items
4. Wait and check logs

**Expected Result**:
- Items transfer through pipe to PreFab
- Debug logs show item routing messages

**Actual Result**:
- Pipe transfer worked: YES / NO / N/A (mod not installed)

**Pass/Fail**: ✅ PASS | ❌ FAIL | ⚪ N/A

---

### 🧪 Test 8: Energy Transfer (If energy mod installed)

**Goal**: Verify energy cables can send energy to PreFab

**Steps**:
1. Place an energy generator (Mekanism, Flux Networks, etc.)
2. Connect energy cable to PreFab
3. Wait for energy transfer
4. Check debug logs

**Expected Result**:
- Energy transfers to PreFab virtual buffer
- Debug logs show: `[DEBUG] Routed X FE to virtual buffer`

**Actual Result**:
- Energy transfer worked: YES / NO / N/A (mod not installed)

**Pass/Fail**: ✅ PASS | ❌ FAIL | ⚪ N/A

---

### 🧪 Test 9: NBT Preservation via Hopper Test

**Goal**: Verify PreFab retains data when broken and placed

**Steps**:
1. Place a fresh PreFab block
2. Use a hopper to add items (e.g., 3 stacks of cobblestone = 192 items)
3. Wait for transfer to complete
4. Break the PreFab block (pick it up with pickaxe)
5. Place the PreFab in a new location
6. Place a hopper below the new PreFab to extract items

**Expected Result**:
- Items transfer to the hopper after placing PreFab in new location
- Same number of items extracted as originally inserted
- **No data loss when moving PreFab block**

**Actual Result**:
- Items before breaking: ___________
- Items after placing: ___________
- Data preserved: YES / NO

**Pass/Fail**: ✅ PASS | ❌ FAIL

---

### 🧪 Test 10: Multiple Resource Types

**Goal**: Verify PreFab can store many different resource types

**Steps**:
1. Place a fresh PreFab block
2. Use hoppers to add multiple different item types:
   - Hopper 1: Iron ingots
   - Hopper 2: Gold ingots  
   - Hopper 3: Diamonds
   - Hopper 4: Emeralds
   - Hopper 5: Coal
3. Wait for all transfers to complete
4. Try to extract with a bottom hopper

**Expected Result**:
- All item types accepted and stored
- Bottom hopper does NOT extract (multiple types, as expected)
- Debug logs show multiple `Routed minecraft:xxx` messages
- **No resource conflicts or overwrites**

**Actual Result**:
- Different item types transferred: ___________
- Any errors in logs: ___________

**Pass/Fail**: ✅ PASS | ❌ FAIL

---

### 🧪 Test 11: Capacity Values Check

**Goal**: Verify capability methods return unlimited capacity (internal check)

**Steps**:
1. This test is automatically validated by Test 3 (/testbuffer command)
2. The automated test suite verifies storage beyond old limits
3. If Test 3 passed, this test passes

**Expected Result**:
- Internal capacity checks return unlimited values
- No "buffer full" rejections occur

**Actual Result**:
- Test 3 result: ___________

**Pass/Fail**: ✅ PASS | ❌ FAIL (same as Test 3)

---

### 🧪 Test 12: Room Entry/Exit Still Works

**Goal**: Verify PreFab teleportation is unaffected by capability changes

**Steps**:
1. Create a Compact Machine room (or reuse existing)
2. Use TPS Cache Upgrade item on CM to create PreFab
3. Use Personal Shrinking Device on PreFab to enter
4. Verify you teleport into CM dimension
5. Use Personal Shrinking Device in CM dimension to exit
6. Verify you return to Overworld

**Expected Result**:
- Entry works: teleport to CM dimension
- Exit works: teleport back to entry position

**Actual Result**:
- Entry worked: YES / NO
- Exit worked: YES / NO

**Pass/Fail**: ✅ PASS | ❌ FAIL

---

### 🧪 Test 13: Stress Test - Automated Extreme Values

**Goal**: Verify extreme storage values work correctly (already tested by /testbuffer)

**Steps**:
1. Review the output of Test 3 (`/testbuffer` command)
2. Specifically check the final sub-test that stores 1 billion FE
3. If that passed, extreme values are handled correctly

**Expected Result**:
- Test 3 showed: `§a✓ Stored 1,000,000,000 FE (1 billion!)`
- No crashes or corruption with large values

**Actual Result**:
- Billion FE test result from Test 3: ___________

**Pass/Fail**: ✅ PASS | ❌ FAIL (same as Test 3)

---

## Test Results Summary

| Test # | Test Name | Status | Notes |
|--------|-----------|--------|-------|
| 1 | Mod Loading | ⬜ | Check logs for capability registration |
| 2 | Block Placement | ⬜ | Basic PreFab interaction |
| 3 | Automated Storage Tests | ⬜ | **/testbuffer command - most important** |
| 4 | Hopper Insertion | ⬜ | External mod capability test |
| 5 | Smart Extraction (1 type) | ⬜ | Tests smart extraction logic |
| 6 | Extraction Block (multi-type) | ⬜ | Verifies safe extraction behavior |
| 7 | Pipe Insertion | ⬜ | Optional if mod available |
| 8 | Energy Transfer | ⬜ | Optional if mod available |
| 9 | NBT Preservation | ⬜ | Critical for portability |
| 10 | Multiple Types | ⬜ | Tests resource isolation |
| 11 | Capacity Values | ⬜ | Auto-validated by Test 3 |
| 12 | Room Entry/Exit | ⬜ | Regression test |
| 13 | Stress Test | ⬜ | Auto-validated by Test 3 |

**Legend**: ⬜ Not tested | ✅ Pass | ❌ Fail | ⚠️ Partial | ⚪ N/A

---

## Common Issues and Troubleshooting

### Issue: "Capability not registered" errors in logs
**Solution**: Verify `CapabilityRegistration.java` has `@EventBusSubscriber` annotation

### Issue: Hopper doesn't transfer items
**Solution**: 
1. Check PreFab has a room code set (use TPS Upgrade on CM first)
2. Verify `CMInterceptorImpl.isRoutingToVirtual()` returns true
3. Check debug logs for routing state

### Issue: Items disappear when breaking PreFab
**Solution**: 
1. Verify `PrefabBlock.getDrops()` properly serializes NBT
2. Check `DataComponents.BLOCK_ENTITY_DATA` is set correctly

### Issue: Smart extraction doesn't work with single item type
**Solution**:
1. Verify `getItemSnapshot()` returns non-empty map
2. Check `ResourceLocation.parse()` doesn't throw exceptions
3. Confirm debug logs show "Smart extraction" message

### Issue: NBT too large / Minecraft crashes
**Solution**:
1. This is expected if storing billions of items
2. Reduce test values to millions instead of max int
3. Consider adding optional soft limits in production

---

## Performance Considerations

### Expected Performance Characteristics

1. **Memory Usage**: Grows with number of unique resource types, not total quantity
   - 100 resource types ≈ 10 KB memory
   - 1,000 resource types ≈ 100 KB memory

2. **NBT Serialization**: Linear with number of resource types
   - 10 types: < 1ms
   - 100 types: < 10ms
   - 1,000 types: < 100ms

3. **Capability Queries**: O(1) constant time
   - No performance degradation with storage size

### Warning Signs

⚠️ If you observe any of these, report as potential issue:
- Minecraft freezes when placing PreFab with lots of resources
- Chunk loading lags near PreFab blocks
- Memory usage spikes unexpectedly
- NBT save/load takes > 1 second

---

## Acceptance Criteria

**Minimum requirements for Phase 2 completion:**

- ✅ Test 3 passes all checks (automated /testbuffer command)
- ✅ Test 4 passes (hoppers can insert items into PreFab blocks)
- ✅ Test 5 passes (smart extraction works for single-item-type scenarios)
- ✅ Test 9 passes (NBT preserves data when breaking/placing)
- ✅ No crashes or errors in game logs during testing

**Nice to have (not blocking):**
- ⭐ Pipe mod integration tested
- ⭐ Energy mod integration tested
- ⭐ AE2/RS integration tested

---

## Next Steps After Testing

**If all tests pass:**
1. Update TODO.md to mark Phase 2 complete
2. Commit changes with message: "Phase 2 complete: Capability registration with unlimited storage"
3. Begin Phase 3: SimulationWrenchItem integration (from TODO.md)

**If tests fail:**
1. Document failures in this file (fill in "Actual Result" sections)
2. Check critical files listed in implementation plan
3. Review SpotBugs/Checkstyle warnings for hints
4. Debug using `/testbuffer` commands and log output

---

## Testing Checklist

- [ ] Compiled successfully (`./gradlew compileJava`)
- [ ] Checkstyle passed (or only pre-existing warnings)
- [ ] SpotBugs passed (or only low-priority warnings)
- [ ] Ran at least Tests 1-8 (core functionality)
- [ ] Tested NBT preservation (Test 11)
- [ ] Documented any failures or unexpected behavior
- [ ] Updated TODO.md with test results

---

**Tester**: ___________  
**Date Tested**: ___________  
**Minecraft Version**: ___________  
**Mod Version**: ___________  
**Overall Result**: ⬜ PASS | ⬜ FAIL | ⬜ PARTIAL
