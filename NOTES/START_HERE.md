# FPSCompress - Start Here

**Welcome!** This is the entry point for understanding the FPSCompress mod architecture.

**Last Updated**: 2026-04-28  
**Status**: Clean codebase, ready for MVP implementation

---

## 🚀 Quick Start (New Contributors)

**Read these in order**:

1. **README_ARCHITECTURE.md** (5 min read)
   - High-level overview of what the mod does
   - Core concept: Cache factory rates to run without chunk loading
   - Player experience walkthrough

2. **MVP_SCOPE.md** (10 min read)
   - Crystal-clear definition of what IS and ISN'T in MVP
   - Success criteria
   - Common scope creep to avoid

3. **TODO_NEW.md** (15 min read)
   - 7-phase implementation roadmap
   - Tasks for each phase
   - Testing plan

4. **ARCHITECTURE_CONDUIT.md** (20 min read)
   - Complete technical specification
   - Face configuration system
   - Transport logic details
   - Code examples

5. **CLAUDE.md** (10 min read)
   - Project guidelines
   - Build commands
   - Code quality requirements

**Total reading time**: ~1 hour to fully understand the project

---

## 📂 Documentation Index

### Essential Reading (Start Here)
- ✅ **README_ARCHITECTURE.md** - What is FPSCompress?
- ✅ **IMPORTER_EXPORTER_SYSTEM.md** - How resources enter/exit factories (READ THIS!)
- ✅ **MVP_SCOPE.md** - What's in MVP scope?
- ✅ **TODO_NEW.md** - Implementation roadmap
- ✅ **ARCHITECTURE_CONDUIT.md** - Technical specification

### Reference Documentation
- 📘 **CLAUDE.md** - Project guidelines and build commands
- 📘 **CM_API_INTEGRATION.md** - Compact Machines integration details
- 📘 **ARCHITECTURE_PIVOT.md** - Why we changed architectures (history)
- 📘 **CLEANUP_SUMMARY.md** - What was archived (history)

### Deprecated Documentation
- 📦 **TODO.md** - OLD roadmap (points to TODO_NEW.md)
- 📦 **fpscompress-template-1.21.11/deprecated/** - Archived code and docs

### Meta Documentation
- 📄 **notes.md** - Original design notes
- 📄 **START_HERE.md** - This file

---

## 🏗️ Codebase Status

### Current State
- ✅ **Cleaned up** - Deprecated code archived to `deprecated/` folder
- ⚠️ **Needs refactoring** - Some files still reference old virtual buffer system
- 🚧 **Ready for MVP** - Clear path forward with new conduit architecture

### Active Source Files (19 files)
Located in `fpscompress-template-1.21.11/src/main/java/com/mukulramesh/fpscompress/`

**Core mod files** (3):
- `FPSCompress.java`
- `FPSCompressClient.java`
- `Config.java`

**Portal system** (11):
- `PrefabBlock.java` ← **NEEDS REFACTOR**
- `PrefabBlockEntity.java` ← **NEEDS REFACTOR**
- `CapabilityRegistration.java` ← **NEEDS REWRITE**
- `IVirtualMachineData.java` ← **NEEDS RENAME**
- `SimulationWrenchItem.java` ← **NEEDS MODIFY**
- `MachineState.java` ✅ Keep as-is
- `RoomCoordinateCache.java` ✅ Keep as-is
- `TpsCacheUpgradeItem.java` ✅ Keep as-is
- `DimensionTeleportListener.java` ✅ Keep as-is
- `PSDExitListener.java` ✅ Keep as-is
- `PlayerPositionCache.java` ✅ Keep as-is

**Spatial system** (2):
- `CMInterceptorImpl.java` ✅ Keep as-is (chunk loading)
- `ICMInterceptor.java` ✅ Keep as-is (interface)

**Other** (3):
- `component/FPSDataComponents.java` ⚠️ Review (might deprecate)
- `portal/FPSDataAttachments.java` ⚠️ Review (might deprecate)
- `debug/Dev2TestCommands.java` ✅ Keep (debug commands)

### Archived Files (23 files)
Located in `fpscompress-template-1.21.11/deprecated/`

**Java files** (8):
- Virtual buffer storage system
- Capability routers
- Old test commands
- Unused interfaces

**Documentation** (15):
- Old testing guides
- Implementation notes from virtual buffer architecture
- Deprecated handoff docs

**See `CLEANUP_SUMMARY.md` for complete list.**

---

## 🎯 What to Do Next

### For Developers (Implementing MVP)

**Option 1: Start fresh with MVP** (Recommended)
1. Read MVP_SCOPE.md to understand scope
2. Follow TODO_NEW.md Phase 1: Create face config data structures
3. Skip refactoring old files until Phase 2+
4. Build new features alongside old code

**Option 2: Refactor first, then implement**
1. Read CLEANUP_SUMMARY.md "Files That Need Refactoring" section
2. Remove VirtualBufferStorage dependencies from PrefabBlockEntity
3. Rewrite CapabilityRegistration for face-based capabilities
4. Then start TODO_NEW.md Phase 1

**Recommendation**: **Option 1** - Start fresh. Don't spend time refactoring code that will be replaced anyway.

### For Testers (Testing MVP)

**Wait for MVP Phase 4** (Cached Production) to be complete, then:
1. Read MVP_SCOPE.md "MVP Test Case" section
2. Follow testing plan in TODO_NEW.md
3. Test with vanilla blocks only (chests, furnaces, hoppers)
4. Verify chunks unload during CACHED mode (F3 screen)

### For Architects (Understanding System)

**Read in this order**:
1. ARCHITECTURE_CONDUIT.md - Technical spec
2. ARCHITECTURE_PIVOT.md - Why we changed
3. CLAUDE.md - Project constraints
4. CM_API_INTEGRATION.md - How we integrate with Compact Machines

---

## ❓ Common Questions

### Q: Where do I start coding?
**A**: Read MVP_SCOPE.md, then follow TODO_NEW.md Phase 1 (Face Config Data Structures).

### Q: Why is there deprecated code?
**A**: Architecture changed from virtual buffer storage to conduit transport. Old code archived, not deleted.

### Q: Can I delete the deprecated folder?
**A**: Not yet. Wait until MVP is stable and tested. See `deprecated/README.md` for details.

### Q: What's the goal of this mod?
**A**: Cache factory production rates so factories can run without loading chunks (performance optimization).

### Q: Why no AE2 integration?
**A**: MVP focuses on proving caching works. AE2 is post-MVP (see MVP_SCOPE.md).

### Q: Do I need to understand the old virtual buffer system?
**A**: No! New architecture is simpler. Ignore deprecated code unless curious about history.

### Q: What's the compilation status?
**A**: ⚠️ Code compiles but has deprecated dependencies. Phase 1-2 will fix compilation by implementing new system.

### Q: How long will MVP take?
**A**: Estimated 4-6 weeks (see TODO_NEW.md "Priority Order" section).

---

## 🔗 Quick Links

### Documentation
- [README_ARCHITECTURE.md](README_ARCHITECTURE.md) - High-level overview
- [MVP_SCOPE.md](MVP_SCOPE.md) - Scope definition
- [TODO_NEW.md](TODO_NEW.md) - Implementation roadmap
- [ARCHITECTURE_CONDUIT.md](ARCHITECTURE_CONDUIT.md) - Technical spec
- [CLAUDE.md](CLAUDE.md) - Project guidelines

### Code
- [Source files](../fpscompress-template-1.21.11/src/main/java/com/mukulramesh/fpscompress/) - Active code
- [Deprecated code](../fpscompress-template-1.21.11/deprecated/) - Archived code

### Build
```bash
cd "fpscompress-template-1.21.11"
./gradlew build            # Build mod
./gradlew runClient        # Test in Minecraft
./gradlew compileJava      # Check compilation
```

---

## 📊 Project Status

**Architecture**: ✅ Defined (conduit-based caching)  
**Documentation**: ✅ Complete and organized  
**Codebase**: ⚠️ Partially refactored (deprecated code archived)  
**MVP Phase 1**: 🚧 Not started (face config data structures)  
**MVP Phase 2**: 🚧 Not started (transport logic)  
**MVP Phase 3**: 🚧 Not started (rate measurement)  
**MVP Phase 4**: 🚧 Not started (cached production)  
**MVP Phase 5**: 🚧 Not started (wrench control)  
**MVP Phase 6**: 🚧 Not started (face config GUI)  
**MVP Phase 7**: 🚧 Not started (dynamic capabilities)  

**Estimated MVP completion**: 4-6 weeks (following TODO_NEW.md)

---

## 🎓 Learning Path

**If you're new to the project**:

### Day 1: Understand the Goal
- Read README_ARCHITECTURE.md
- Understand: "Cache rates → Run without chunks → Better TPS"

### Day 2: Understand the Scope
- Read MVP_SCOPE.md
- Understand: "MVP = One PreFab + Face config + Caching, NO integrations"

### Day 3: Understand the Architecture
- Read ARCHITECTURE_CONDUIT.md
- Understand: "Conduits not storage, PULL/PUSH faces, fractional math"

### Day 4: Understand the Implementation Plan
- Read TODO_NEW.md
- Understand: "7 phases, start with face config, test at each phase"

### Day 5: Start Coding
- Create FaceMode.java, ResourceFilter.java, FaceConfig.java
- Follow TODO_NEW.md Phase 1 tasks

---

**Ready to start?** → Read **README_ARCHITECTURE.md** next!
