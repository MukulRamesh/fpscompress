# Codebase Cleanup Summary

**Date**: 2026-04-28 (archived) → 2026-05-12 (deleted)
**Action**: Deleted deprecated code from virtual buffer architecture (preserved in git history)

---

## Files Deleted from Project

### Java Source Files (8 files)

#### capabilities/ (3 files)
- ✅ `VirtualEnergyStorage.java` - Unlimited energy storage
- ✅ `VirtualFluidHandler.java` - Unlimited fluid storage
- ✅ `VirtualItemHandler.java` - Unlimited item storage with smart extraction

#### portal/ (2 files)
- ✅ `VirtualBufferStorage.java` - Core storage with Map<String, Integer>
- ✅ `VirtualMachineDataImpl.java` - Virtual buffer implementation

#### spatial/ (1 file)
- ✅ `CapabilityRouter.java` - Complex physical/virtual routing

#### debug/ (1 file)
- ✅ `BufferTestCommand.java` - Buffer capacity tests

#### integration/ (1 file)
- ✅ `FactoryIntegrator.java` - Virtual buffer coordinator

#### logic/ (0 files - was just interface)
- ✅ `IMachineLogic.java` - State machine interface (moved)

#### scanner/ (0 files - was just interface)
- ✅ `IAntiCheatScanner.java` - Anti-cheat interface (moved)

### Documentation Files (12 files)

#### Testing Docs (4 files)
- ✅ `TESTING_CAPABILITY_REGISTRATION.md`
- ✅ `TESTING_QUICK_START.md`
- ✅ `STORAGE_VIEWER_FEATURE.md`
- ✅ `TEST_BUFFER_CAPACITY.md`

#### Implementation Docs (4 files)
- ✅ `DEV2_IMPLEMENTATION.md`
- ✅ `DEV2_INTEGRATION_COMPLETE.md`
- ✅ `DEV2_QA_SUMMARY.md`
- ✅ `QA_TESTING_GUIDE_DEV2.md`

#### Other Docs (4 files)
- ✅ `DEV1_HANDOFF.md`
- ✅ `CM_DEPENDENCY_ANALYSIS.md`
- ✅ `BRANCH_WORKFLOW.md`
- ✅ `QA.md`

---

## Files Kept (Active Codebase)

### Core Mod Files (3 files)
- ✅ `FPSCompress.java` - Main mod class
- ✅ `FPSCompressClient.java` - Client-side setup
- ✅ `Config.java` - Configuration

### Components (1 file)
- ✅ `FPSDataComponents.java` - Custom data components

### Portal System (11 files) - **Need to refactor for MVP**
- ✅ `CapabilityRegistration.java` - **NEEDS REWRITE** for face-based capabilities
- ✅ `DimensionTeleportListener.java` - **KEEP** for coordinate caching
- ✅ `FPSDataAttachments.java` - **REVIEW** (might be deprecated)
- ✅ `IVirtualMachineData.java` - **RENAME to IPreFabData**, remove buffer methods
- ✅ `MachineState.java` - **KEEP** for BUILDING/SIMULATING/CACHED/HALTED
- ✅ `PSDExitListener.java` - **KEEP** for teleportation
- ✅ `PlayerPositionCache.java` - **KEEP** for teleportation
- ✅ `PrefabBlock.java` - **KEEP, MODIFY** for face config GUI trigger
- ✅ `PrefabBlockEntity.java` - **KEEP, REFACTOR** for face configs + transport logic
- ✅ `RoomCoordinateCache.java` - **KEEP** for coordinate mapping
- ✅ `SimulationWrenchItem.java` - **KEEP, MODIFY** for state transitions
- ✅ `TpsCacheUpgradeItem.java` - **KEEP** for CM → PreFab upgrade

### Spatial System (2 files)
- ✅ `CMInterceptorImpl.java` - **KEEP** for chunk loading control
- ✅ `ICMInterceptor.java` - **KEEP** interface

### Debug (1 file)
- ✅ `Dev2TestCommands.java` - **KEEP** for diagnostics

---

## Next Steps for Remaining Files

### Files That Need Refactoring

**1. PrefabBlockEntity.java** - HIGH PRIORITY
- ❌ Remove: `VirtualBufferStorage storage` field
- ❌ Remove: `addToBuffer()`, `extractFromBuffer()`, `getItemSnapshot()` methods
- ✅ Add: `Map<Direction, FaceConfig> faceConfigs` field
- ✅ Add: `Map<String, Double> itemRates` field (rate caching)
- ✅ Add: `Map<String, Double> itemAccumulators` field (fractional math)
- ✅ Add: `tick()` method for transport logic

**2. CapabilityRegistration.java** - HIGH PRIORITY
- ❌ Remove: Virtual buffer routing logic
- ❌ Remove: `CMInterceptorImpl.setRoutingState(true)` pattern
- ✅ Add: Face-based capability registration
- ✅ Add: Context-aware capability providers (query face config)

**3. IVirtualMachineData.java** - MEDIUM PRIORITY
- ❌ Rename to: `IPreFabData.java`
- ❌ Remove: Buffer methods (`addToBuffer`, `extractFromBuffer`, `getBufferAmount`)
- ✅ Add: Face config methods (`getFaceConfig`, `setFaceConfig`)
- ✅ Add: State methods (`getState`, `setState`)
- ✅ Add: Rate methods (`getRates`)

**4. SimulationWrenchItem.java** - MEDIUM PRIORITY
- ✅ Keep: Basic structure
- ✅ Modify: State transition logic (BUILDING → SIMULATING → CACHED)
- ✅ Add: Chunk loading/unloading calls
- ✅ Add: Rate calculation trigger

**5. PrefabBlock.java** - LOW PRIORITY
- ✅ Keep: Basic structure
- ❌ Remove: `displayStorageStats()` method (no storage to display)
- ✅ Modify: Shift+Right-click → Open face config GUI
- ✅ Add: Status display (show rates, state, face configs)

### Files That Can Stay As-Is (For Now)

- ✅ `FPSCompress.java` - Mod registration
- ✅ `FPSCompressClient.java` - Client setup
- ✅ `Config.java` - Configuration
- ✅ `MachineState.java` - State enum (perfect as-is)
- ✅ `RoomCoordinateCache.java` - Coordinate mapping (reuse for transport)
- ✅ `CMInterceptorImpl.java` - Chunk loading (reuse as-is)
- ✅ `ICMInterceptor.java` - Interface (reuse as-is)
- ✅ `DimensionTeleportListener.java` - Coordinate caching (keep)
- ✅ `PSDExitListener.java` - Exit handling (keep)
- ✅ `PlayerPositionCache.java` - Position tracking (keep)
- ✅ `TpsCacheUpgradeItem.java` - CM upgrade (keep)
- ✅ `Dev2TestCommands.java` - Debug commands (keep, maybe add new ones)

### Files to Review (Might Be Deprecated)

**FPSDataAttachments.java** - REVIEW
- Check if still needed for new architecture
- If storing virtual buffer data → deprecated
- If storing face configs → might keep

**FPSDataComponents.java** - REVIEW
- Check what data components are registered
- If virtual buffer components → deprecated
- If face config components → might keep

---

## New Files to Create (MVP Implementation)

### Phase 1: Data Structures
- `portal/FaceMode.java` - Enum (DISABLED, PULL, PUSH)
- `portal/ResourceFilter.java` - Enum (ALL, ITEMS, FLUIDS, ENERGY)
- `portal/FaceConfig.java` - Face configuration data class

### Phase 2: Transport Logic
- `portal/ResourceTransporter.java` - Core transport between dimensions

### Phase 6: GUI (Optional for MVP)
- `gui/PreFabConfigScreen.java` - Face config GUI
- `gui/PreFabConfigMenu.java` - Server-side container
- `network/FaceConfigPacket.java` - Network sync

### Phase 7: Dynamic Capabilities
- `capabilities/FaceItemHandler.java` - Per-face IItemHandler
- `capabilities/FaceFluidHandler.java` - Per-face IFluidHandler
- `capabilities/FaceEnergyStorage.java` - Per-face IEnergyStorage

---

## Documentation Status

### Active Documentation (Project Root)
- ✅ `README_ARCHITECTURE.md` - **NEW** - High-level overview
- ✅ `MVP_SCOPE.md` - **NEW** - Scope definition
- ✅ `TODO.md` - **NEW** - Implementation roadmap
- ✅ `ARCHITECTURE_CONDUIT.md` - **NEW** - Technical spec
- ✅ `ARCHITECTURE_PIVOT.md` - **NEW** - Migration guide
- ✅ `CLAUDE.md` - **UPDATED** - Project guidelines
- ✅ `CM_API_INTEGRATION.md` - **KEEP** - CM integration details
- ✅ `TODO.md` - **DEPRECATED** - Points to new docs

### Deprecated Documentation
- ❌ All testing docs → `deprecated/docs/`
- ❌ All DEV2 docs → `deprecated/docs/`
- ❌ All handoff docs → `deprecated/docs/`

---

## Statistics

**Total Files Deleted**: 24 files (10 Java + 14 docs)  
**Total Files Remaining**: 19 Java files  
**Files Needing Refactor**: 5 files (PrefabBlockEntity, CapabilityRegistration, IVirtualMachineData, SimulationWrenchItem, PrefabBlock)  
**New Files to Create**: ~10 files (Phase 1-7)

**Code Reduction**: ~35% fewer source files  
**Documentation Reduction**: ~65% fewer doc files

---

## Compilation Status

**Before cleanup**: Compiles ✅ (but with deprecated virtual buffer code)  
**After cleanup**: Needs refactoring ❌ (PrefabBlockEntity references VirtualBufferStorage)

**Next step**: Fix compilation errors by refactoring PrefabBlockEntity to remove virtual buffer dependencies.

---

## Recovery Instructions

If you need to restore deleted files, they are preserved in git history:

```bash
# View deleted files
git log --all --full-history -- "fpscompress-template-1.21.11/deprecated/**"

# Restore specific file from git history (use commit before deletion)
git checkout 60bedae -- fpscompress-template-1.21.11/deprecated/portal/VirtualBufferStorage.java

# Or restore entire deprecated folder
git checkout 60bedae -- fpscompress-template-1.21.11/deprecated/
```

**Note**: Files were deleted in commit `11737a0` (2026-05-12). Use commit `60bedae` to restore.

---

**All deleted code is permanently preserved in git history.**
