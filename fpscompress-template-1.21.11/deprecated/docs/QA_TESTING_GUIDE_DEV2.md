# QA Testing Guide - Dev 2: Chunk Manager & Interceptor

**Module**: Dev 2 - Spatial Manager
**Test Commands**: `/fps_dev2`
**Prerequisites**: Op level 2, Compact Machines mod installed
**Estimated Time**: 15-20 minutes

---

## Table of Contents

1. [Setup & Prerequisites](#setup--prerequisites)
2. [Test Commands Reference](#test-commands-reference)
3. [Manual Test Procedures](#manual-test-procedures)
4. [Expected Results](#expected-results)
5. [Troubleshooting](#troubleshooting)
6. [Test Results Template](#test-results-template)

---

## Setup & Prerequisites

### Required Setup

1. **Server/World Setup**:
   ```bash
   # Start Minecraft with the mod loaded
   # Ensure you have OP permissions (level 2 or higher)
   /op <your_username>
   ```

2. **Create a Test Compact Machine**:
   - Place a Compact Machine block in the Overworld
   - Note its room code (check with F3 debug or CM commands)
   - Example room code: `room_abc123`

3. **Verify Mod Loaded**:
   ```
   /fps_dev2
   ```
   - Should show command syntax if properly loaded
   - If error: Check server logs for mod loading issues

### Environment Variables

For automated testing, you can set:
```bash
TEST_ROOM_CODE="room_abc123"  # Replace with your actual room code
```

---

## Test Commands Reference

### Command Syntax

```
/fps_dev2 chunks <roomCode> <true|false>
/fps_dev2 routing <true|false>
/fps_dev2 diagnostics
/fps_dev2 test-room <roomCode>
/fps_dev2 cleanup
```

### Command Descriptions

| Command | Purpose | Example |
|---------|---------|---------|
| `chunks` | Load/unload chunks for a room | `/fps_dev2 chunks room_abc123 true` |
| `routing` | Set routing state (physical/virtual) | `/fps_dev2 routing true` |
| `diagnostics` | Show current interceptor state | `/fps_dev2 diagnostics` |
| `test-room` | Run full test suite for a room | `/fps_dev2 test-room room_abc123` |
| `cleanup` | Clean up all chunk tickets | `/fps_dev2 cleanup` |

---

## Manual Test Procedures

### Test 1: Chunk Loading ✅

**Objective**: Verify chunks load when requested

**Steps**:
1. Run command:
   ```
   /fps_dev2 chunks <YOUR_ROOM_CODE> true
   ```

2. Check output:
   - Should show: `§a[Dev2 Test] ✓ SUCCESS: Chunks are LOADED`

3. Verify in-game:
   - Use F3 debug screen
   - Navigate to the room's coordinates
   - Chunks should be visible/loaded

**Expected Result**: ✅ Chunks are loaded and visible

**Pass Criteria**:
- ✓ Command returns success message
- ✓ Chunks are actually loaded (F3 shows loaded)
- ✓ No errors in server log

---

### Test 2: Chunk Unloading ✅

**Objective**: Verify chunks unload when requested

**Steps**:
1. First ensure chunks are loaded (Test 1)

2. Run command:
   ```
   /fps_dev2 chunks <YOUR_ROOM_CODE> false
   ```

3. Check output:
   - Should show: `§a[Dev2 Test] ✓ SUCCESS: Chunks are UNLOADED`

4. Verify in-game:
   - Chunks should unload (may take 1-2 seconds)
   - F3 should show chunks as unloaded

**Expected Result**: ✅ Chunks are unloaded

**Pass Criteria**:
- ✓ Command returns success message
- ✓ Chunks are actually unloaded
- ✓ No errors in server log

---

### Test 3: Routing State Toggle ✅

**Objective**: Verify routing state switches correctly

**Steps**:
1. Set routing to VIRTUAL:
   ```
   /fps_dev2 routing true
   ```
   - Should show: `§a[Dev2 Test] ✓ SUCCESS: Routing is VIRTUAL`

2. Set routing to PHYSICAL:
   ```
   /fps_dev2 routing false
   ```
   - Should show: `§a[Dev2 Test] ✓ SUCCESS: Routing is PHYSICAL`

3. Check diagnostics:
   ```
   /fps_dev2 diagnostics
   ```
   - Should show current routing state

**Expected Result**: ✅ Routing toggles correctly

**Pass Criteria**:
- ✓ Both commands return success
- ✓ Diagnostics show correct state
- ✓ State persists between checks

---

### Test 4: Diagnostics Output ✅

**Objective**: Verify diagnostics show correct information

**Steps**:
1. Run diagnostics:
   ```
   /fps_dev2 diagnostics
   ```

2. Check output format:
   ```
   CMInterceptor Diagnostics:
     Loaded Rooms: <number>
     Current Routing: <VIRTUAL|PHYSICAL>
     Rooms with Virtual Routing: <number>
   ```

3. Verify values make sense:
   - Loaded rooms count matches your tests
   - Current routing matches what you set
   - Virtual routing count is accurate

**Expected Result**: ✅ Diagnostics show accurate state

**Pass Criteria**:
- ✓ Output is formatted correctly
- ✓ Values are accurate
- ✓ No missing information

---

### Test 5: Cleanup Function ✅

**Objective**: Verify cleanup removes all chunk tickets

**Steps**:
1. Load chunks for a room (Test 1)

2. Check diagnostics - should show loaded rooms

3. Run cleanup:
   ```
   /fps_dev2 cleanup
   ```
   - Should show: `§a[Dev2 Test] ✓ Cleanup complete`

4. Check diagnostics again:
   ```
   /fps_dev2 diagnostics
   ```
   - Should show: `Loaded Rooms: 0`

**Expected Result**: ✅ All chunk tickets removed

**Pass Criteria**:
- ✓ Cleanup completes successfully
- ✓ Diagnostics show 0 loaded rooms
- ✓ Chunks are actually unloaded

---

### Test 6: Comprehensive Room Test ✅

**Objective**: Run full automated test suite

**Steps**:
1. Run comprehensive test:
   ```
   /fps_dev2 test-room <YOUR_ROOM_CODE>
   ```

2. Watch the output:
   ```
   [1/5] Testing chunk loading...
     ✓ Chunks loaded successfully
   [2/5] Verifying chunk persistence...
     ✓ Chunks remain loaded
   [3/5] Testing chunk unloading...
     ✓ Chunks unloaded successfully
   [4/5] Testing virtual routing...
     ✓ Routing set to VIRTUAL
   [5/5] Testing physical routing...
     ✓ Routing set to PHYSICAL
   Results: 5/5 tests passed ✓ ALL TESTS PASSED
   ```

3. If any test fails, note which one

**Expected Result**: ✅ 5/5 tests pass

**Pass Criteria**:
- ✓ All 5 sub-tests pass
- ✓ Final message shows "ALL TESTS PASSED"
- ✓ No errors in server log

---

## Integration Testing

### Test 7: State Machine Integration 🔄

**Objective**: Verify integration with FactoryIntegrator state transitions

**Prerequisites**:
- FactoryIntegrator implemented
- Test Compact Machine with TPS upgrade

**Steps**:

1. **BUILDING → SIMULATING Transition**:
   ```
   # Chunks should be loaded, routing physical
   /fps_dev2 diagnostics
   ```
   - Expected: Loaded rooms ≥ 1, Routing: PHYSICAL

2. **SIMULATING → CACHED Transition**:
   ```
   # After simulation ends, chunks should unload, routing virtual
   /fps_dev2 diagnostics
   ```
   - Expected: Loaded rooms = 0 (if no other factories), Routing: VIRTUAL

3. **CACHED → HALTED Transition**:
   ```
   # If cache breaks, chunks should reload
   /fps_dev2 diagnostics
   ```
   - Expected: Loaded rooms ≥ 1, Routing: PHYSICAL

**Pass Criteria**:
- ✓ Chunks load/unload at correct times
- ✓ Routing switches at correct times
- ✓ No chunk loading leaks (use `/fps_dev2 cleanup` to verify)

---

### Test 8: Resource Routing Verification 🔄

**Objective**: Verify resources route to correct destination

**Prerequisites**:
- Virtual buffer integration complete (Dev 1)
- Test setup with hopper → CM block → chest

**Steps**:

1. **Physical Routing Test**:
   ```
   /fps_dev2 routing false
   ```
   - Place items in hopper above CM
   - Expected: Items go to physical chest inside CM room

2. **Virtual Routing Test**:
   ```
   /fps_dev2 routing true
   ```
   - Place items in hopper above CM
   - Expected: Items go to virtual buffer (not physical chest)
   - Use `/testbuffer` command to verify virtual storage

3. **Verify No Cross-Contamination**:
   - Physical mode items should NOT go to virtual buffer
   - Virtual mode items should NOT go to physical chest

**Pass Criteria**:
- ✓ Physical mode routes to physical blocks
- ✓ Virtual mode routes to virtual buffers
- ✓ No items lost or duplicated
- ✓ Clean separation between modes

---

## Expected Results

### Success Indicators

✅ **All tests pass**:
- Commands return success messages
- Diagnostics show correct state
- Chunks load/unload properly
- Routing toggles correctly
- No chunk loading leaks

### Failure Indicators

❌ **Test failures**:
- Error messages in red text
- Diagnostics show incorrect state
- Chunks don't load/unload
- Server logs show errors
- Memory leak (chunks don't unload)

---

## Troubleshooting

### Common Issues

#### Issue 1: "Room code not found"

**Symptoms**:
```
§c[Dev2 Test] ERROR: Room code 'xyz' not found
```

**Causes**:
- Room doesn't exist in CM registrar
- Wrong room code format
- CM mod not loaded properly

**Solutions**:
1. Verify room exists: Use CM commands to list rooms
2. Check room code format: Should match CM's format
3. Try with a different room that you know exists
4. Check server logs for CM API access errors

---

#### Issue 2: "Chunks fail to load"

**Symptoms**:
```
§c[Dev2 Test] ✗ FAILED: Expected chunks to be LOADED, but they are UNLOADED
```

**Causes**:
- CM room coordinates not resolved
- Reflection failed to access CM API
- Chunk ticket system issue

**Solutions**:
1. Check server logs for ERROR messages about room coordinates
2. Look for reflection errors in DEBUG logs
3. Verify CM version compatibility (tested with 7.0.81)
4. Try `/fps_dev2 cleanup` and retry

---

#### Issue 3: "Routing state doesn't change"

**Symptoms**:
```
§c[Dev2 Test] ✗ FAILED: Expected routing to be VIRTUAL, but it is PHYSICAL
```

**Causes**:
- Interceptor instance issue
- State not persisting

**Solutions**:
1. Run `/fps_dev2 cleanup` to reset state
2. Restart server and retry
3. Check for mod conflicts
4. Verify command completed without errors

---

#### Issue 4: "Chunk loading leak"

**Symptoms**:
- Chunks remain loaded after cleanup
- Server memory usage increases over time
- `/fps_dev2 diagnostics` shows rooms loaded when none should be

**Causes**:
- Chunk tickets not removed properly
- Multiple interceptor instances
- Cleanup not called

**Solutions**:
1. Run `/fps_dev2 cleanup` explicitly
2. Restart server to force-clear tickets
3. Check for duplicate command registrations
4. Report as bug with diagnostics output

---

## Test Results Template

### Test Report Form

```
==============================================
FPSCompress Dev 2 QA Test Report
==============================================

Tester: _____________________
Date: _______________________
Minecraft Version: __________
Mod Version: ________________
Compact Machines Version: ___

==============================================
Test Results
==============================================

[ ] Test 1: Chunk Loading
    Status: PASS / FAIL
    Notes: ________________________________

[ ] Test 2: Chunk Unloading
    Status: PASS / FAIL
    Notes: ________________________________

[ ] Test 3: Routing State Toggle
    Status: PASS / FAIL
    Notes: ________________________________

[ ] Test 4: Diagnostics Output
    Status: PASS / FAIL
    Notes: ________________________________

[ ] Test 5: Cleanup Function
    Status: PASS / FAIL
    Notes: ________________________________

[ ] Test 6: Comprehensive Room Test
    Status: PASS / FAIL
    Result: ___/5 tests passed
    Notes: ________________________________

[ ] Test 7: State Machine Integration
    Status: PASS / FAIL / NOT TESTED
    Notes: ________________________________

[ ] Test 8: Resource Routing Verification
    Status: PASS / FAIL / NOT TESTED
    Notes: ________________________________

==============================================
Overall Status
==============================================

Total Tests: ____ / 8
Pass Rate: ____%

Critical Issues Found:
1. ____________________________________
2. ____________________________________
3. ____________________________________

Non-Critical Issues:
1. ____________________________________
2. ____________________________________

==============================================
Recommendations
==============================================

[ ] APPROVED FOR RELEASE
[ ] NEEDS FIXES - See critical issues
[ ] NEEDS MORE TESTING

Tester Signature: ___________________
Date: ______________________________
```

---

## Quick Reference Scripts

### Bash Script for Linux/Mac

Save as `test_dev2.sh`:
```bash
#!/bin/bash

ROOM_CODE="${1:-room_test}"

echo "=== FPSCompress Dev 2 Quick Test ==="
echo "Room Code: $ROOM_CODE"
echo ""

# Test 1: Chunk Loading
echo "[1/6] Testing chunk loading..."
# Run in Minecraft: /fps_dev2 chunks $ROOM_CODE true

# Test 2: Chunk Unloading
echo "[2/6] Testing chunk unloading..."
# Run in Minecraft: /fps_dev2 chunks $ROOM_CODE false

# Test 3: Routing
echo "[3/6] Testing routing..."
# Run in Minecraft: /fps_dev2 routing true
# Run in Minecraft: /fps_dev2 routing false

# Test 4: Diagnostics
echo "[4/6] Checking diagnostics..."
# Run in Minecraft: /fps_dev2 diagnostics

# Test 5: Comprehensive Test
echo "[5/6] Running comprehensive test..."
# Run in Minecraft: /fps_dev2 test-room $ROOM_CODE

# Test 6: Cleanup
echo "[6/6] Cleaning up..."
# Run in Minecraft: /fps_dev2 cleanup

echo ""
echo "=== Test sequence complete ==="
echo "Check Minecraft chat for results"
```

### Windows PowerShell Script

Save as `test_dev2.ps1`:
```powershell
param(
    [string]$RoomCode = "room_test"
)

Write-Host "=== FPSCompress Dev 2 Quick Test ===" -ForegroundColor Cyan
Write-Host "Room Code: $RoomCode" -ForegroundColor Yellow
Write-Host ""

$commands = @(
    "/fps_dev2 chunks $RoomCode true",
    "/fps_dev2 chunks $RoomCode false",
    "/fps_dev2 routing true",
    "/fps_dev2 routing false",
    "/fps_dev2 diagnostics",
    "/fps_dev2 test-room $RoomCode",
    "/fps_dev2 cleanup"
)

Write-Host "Copy these commands into Minecraft:" -ForegroundColor Green
foreach ($cmd in $commands) {
    Write-Host $cmd -ForegroundColor White
}
```

---

## Support & Bug Reporting

If you encounter issues:

1. **Collect Information**:
   - Run `/fps_dev2 diagnostics` and screenshot output
   - Check server logs for ERROR/WARN messages
   - Note exact steps to reproduce

2. **Check Known Issues**:
   - See Troubleshooting section above
   - Check `CM_API_INTEGRATION.md` for API issues

3. **Report Bug**:
   - Include test results template (filled out)
   - Include diagnostics output
   - Include relevant server log snippets
   - Specify MC version, mod version, CM version

---

**Last Updated**: 2026-03-27
**Tested With**: Minecraft 1.21.11, NeoForge 21.11.38-beta, Compact Machines 7.0.81
