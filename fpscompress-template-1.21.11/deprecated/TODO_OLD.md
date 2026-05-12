# FPSCompress TODO - DEPRECATED

**Date**: 2026-04-28  
**Status**: This file is DEPRECATED

---

## ⚠️ Architecture Changed

The original virtual buffer storage architecture has been replaced with a **conduit-based caching system**.

**See new documentation**:
- **MVP_SCOPE.md** - What's in/out of MVP scope (START HERE)
- **TODO_NEW.md** - New implementation roadmap
- **ARCHITECTURE_CONDUIT.md** - Complete conduit system specification
- **ARCHITECTURE_PIVOT.md** - Why we changed and migration guide
- **CLAUDE.md** - Updated project overview

---

## Key Changes

### Old Architecture (DEPRECATED)
- ❌ PreFabs stored items/fluids/energy in unlimited virtual buffers
- ❌ Complex capability routing with smart extraction
- ❌ VirtualBufferStorage class with Map<String, Integer> storage

### New Architecture (CURRENT)
- ✅ PreFabs are cross-dimensional conduits (no internal storage)
- ✅ Face configuration system (6 faces, PULL/PUSH modes)
- ✅ Direct transport between Overworld and CM dimension
- ✅ Rate measurement → Cached production using fractional math

### What This Means
**Primary Goal**: Cache factory input/output rates to run factories without chunk loading.

**Everything else** (GUIs, features, integrations) exists only to enable caching. MVP focuses on getting caching to work for ONE PreFab block.

---

## Files to Remove

The following files/features from old architecture are deprecated:

### Code Files (Move to deprecated/)
- `portal/VirtualBufferStorage.java`
- `capabilities/VirtualItemHandler.java`
- `capabilities/VirtualFluidHandler.java`
- `capabilities/VirtualEnergyStorage.java`
- `spatial/CapabilityRouter.java`
- `debug/BufferTestCommand.java`

### Documentation Files
- `TESTING_CAPABILITY_REGISTRATION.md`
- `TESTING_QUICK_START.md`
- `STORAGE_VIEWER_FEATURE.md`
- `TEST_BUFFER_CAPACITY.md`
- `DEV2_IMPLEMENTATION.md` (chunk loading info moved to CLAUDE.md)
- `DEV2_INTEGRATION_COMPLETE.md`
- `DEV2_QA_SUMMARY.md`
- `QA_TESTING_GUIDE_DEV2.md`

---

## Next Steps

1. **Read MVP_SCOPE.md** to understand what we're building
2. **Follow TODO_NEW.md** for implementation phases
3. **Reference ARCHITECTURE_CONDUIT.md** for technical details
4. **Don't implement anything marked "Post-MVP"** until core caching works

---

## Quick Summary

**What changed**: From storage-based to transport-based architecture  
**Why**: Better aligned with goal (caching, not storing)  
**Impact**: Simpler code, smaller NBT, no extraction ambiguity  
**Status**: Starting fresh with new implementation plan  

**Read MVP_SCOPE.md to get started!**
