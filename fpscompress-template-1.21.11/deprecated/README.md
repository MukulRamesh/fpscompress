# Deprecated Code and Documentation

**Date Archived**: 2026-04-28  
**Reason**: Architecture changed from virtual buffer storage to conduit-based caching system

---

## What's in This Folder

This folder contains code and documentation from the **old virtual buffer storage architecture** that was deprecated when the project pivoted to a simpler conduit-based system.

### Why These Were Deprecated

**Old Goal**: Store unlimited items/fluids/energy in virtual buffers  
**New Goal**: Transport resources between dimensions, cache production rates

**The old system was over-engineered for the actual goal** (caching, not storage).

---

## Archived Code

### capabilities/
- `VirtualEnergyStorage.java` - Unlimited energy storage wrapper
- `VirtualFluidHandler.java` - Unlimited fluid storage wrapper
- `VirtualItemHandler.java` - Unlimited item storage with smart extraction

**Why deprecated**: PreFabs don't store resources anymore, they transport them instantly.

### portal/
- `VirtualBufferStorage.java` - Core unlimited storage with Map<String, Integer>
- `VirtualMachineDataImpl.java` - Implementation of virtual buffer interface

**Why deprecated**: No storage needed, just transport logic.

### spatial/
- `CapabilityRouter.java` - Complex routing logic between physical/virtual handlers

**Why deprecated**: Simpler face-based routing in new architecture.

### debug/
- `BufferTestCommand.java` - Tests for unlimited storage capacity

**Why deprecated**: No buffers to test in new system.

### integration/
- `FactoryIntegrator.java` - Coordinated virtual buffers with state machine

**Why deprecated**: MVP doesn't need complex integration layer yet.

### logic/
- `IMachineLogic.java` - Interface for state machine and fractional math

**Why deprecated**: MVP will implement simpler version directly in PreFabBlockEntity.

### scanner/
- `IAntiCheatScanner.java` - Anti-cheat validation interface

**Why deprecated**: Post-MVP feature, not needed for core caching proof-of-concept.

---

## Archived Documentation

### Testing Docs
- `TESTING_CAPABILITY_REGISTRATION.md` - Tested virtual buffer capability registration
- `TESTING_QUICK_START.md` - Quick test guide for virtual buffers
- `STORAGE_VIEWER_FEATURE.md` - Chat display of virtual buffer contents
- `TEST_BUFFER_CAPACITY.md` - Capacity limit tests

**Why deprecated**: Virtual buffers removed, so tests are invalid.

### Implementation Docs
- `DEV2_IMPLEMENTATION.md` - Chunk manager implementation notes
- `DEV2_INTEGRATION_COMPLETE.md` - Integration completion summary
- `DEV2_QA_SUMMARY.md` - QA testing summary
- `QA_TESTING_GUIDE_DEV2.md` - Detailed QA procedures

**Why deprecated**: Dev 1-5 module system replaced with simpler MVP architecture.

### Other Docs
- `DEV1_HANDOFF.md` - Original Dev 1 handoff document
- `CM_DEPENDENCY_ANALYSIS.md` - Compact Machines API analysis
- `BRANCH_WORKFLOW.md` - Git branch workflow (outdated)
- `QA.md` - Old QA notes

**Why deprecated**: Old architecture, superseded by new docs.

---

## Current Architecture Documentation

**See project root for current docs**:
- `README_ARCHITECTURE.md` - High-level overview (START HERE)
- `MVP_SCOPE.md` - What's in/out of MVP
- `TODO_NEW.md` - Implementation roadmap
- `ARCHITECTURE_CONDUIT.md` - Technical specification
- `ARCHITECTURE_PIVOT.md` - Why we changed
- `CLAUDE.md` - Updated project guidelines

---

## Could This Code Be Useful Later?

**Probably not in its current form**, but you could reference it for:

1. **VirtualBufferStorage.java** - Map-based storage pattern (if we ever need internal storage)
2. **CapabilityRouter.java** - Capability wrapping pattern (could adapt for face-based routing)
3. **BufferTestCommand.java** - Command structure example (for future debug commands)

**However**: The new conduit architecture is fundamentally different, so direct reuse is unlikely.

---

## Can I Delete This Folder?

**Not recommended yet.** Keep it around until:
- ✅ MVP is fully implemented and working
- ✅ New architecture has been tested extensively
- ✅ No major blockers that would force a rollback

**After MVP is stable**: You can delete this folder or move it to a separate branch.

---

**Questions?** See `ARCHITECTURE_PIVOT.md` in project root for detailed migration guide.
