# FPSCompress - Start Here

**Welcome!** This is the entry point for understanding the FPSCompress mod architecture.

**Last Updated**: 2026-05-11  
**Status**: Core MVP systems complete, polish and advanced features in progress

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

3. **TODO.md** (15 min read)
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
- ✅ **TODO.md** - Implementation roadmap
- ✅ **ARCHITECTURE_CONDUIT.md** - Technical specification

### Reference Documentation
- 📘 **CLAUDE.md** - Project guidelines and build commands
- 📘 **CM_API_INTEGRATION.md** - Compact Machines integration details
- 📘 **ARCHITECTURE_PIVOT.md** - Why we changed architectures (history)
- 📘 **CLEANUP_SUMMARY.md** - What was archived (history)

### Deprecated Documentation
- 📦 **TODO.md** - OLD roadmap (points to TODO.md)
- 📦 **fpscompress-template-1.21.11/deprecated/** - Archived code and docs

### Meta Documentation
- 📄 **notes.md** - Original design notes
- 📄 **START_HERE.md** - This file

---

## 🏗️ Codebase Status

### Current State
- ✅ **Core systems implemented** - Face config, Importer/Exporter, transport, rate measurement, caching
- ✅ **PreFab-as-Item system** - Portable factories with cached rates
- ✅ **Enhanced GUI** - Live status display with resource tracking
- 🔨 **In progress** - Advanced features, integrations, and polish

### Active Source Files (19 files)
Located in `fpscompress-template-1.21.11/src/main/java/com/mukulramesh/fpscompress/`

**Core mod files** (3):
- `FPSCompress.java`
- `FPSCompressClient.java`
- `Config.java`

**Portal system**:
- `PrefabBlock.java` ✅ Implemented
- `PrefabBlockEntity.java` ✅ Implemented with face configs
- `PrefabBlockItem.java` ✅ PreFab-as-Item system
- `ImporterBlock.java` / `ImporterBlockEntity.java` ✅ Implemented
- `ExporterBlock.java` / `ExporterBlockEntity.java` ✅ Implemented
- `SimulationWrenchItem.java` ✅ State control
- `TpsCacheUpgradeItem.java` ✅ CM → PreFab upgrade
- `MachineState.java` ✅ State machine
- `RoomCoordinateCache.java` ✅ Coordinate mapping
- `DimensionTeleportListener.java` ✅ Teleport handling
- `PSDExitListener.java` ✅ Exit handling
- `PlayerPositionCache.java` ✅ Position tracking

**Spatial system** (2):
- `CMInterceptorImpl.java` ✅ Keep as-is (chunk loading)
- `ICMInterceptor.java` ✅ Keep as-is (interface)

**GUI system**:
- `gui/PreFabConfigScreen.java` ✅ Face configuration GUI
- `gui/PreFabConfigMenu.java` ✅ Menu handler
- `network/FaceConfigPacket.java` ✅ Network sync
- `network/SimulationControlPacket.java` ✅ State control sync

**Data & Components**:
- `component/FPSDataComponents.java` ✅ PreFab item data
- `portal/FPSDataAttachments.java` ✅ Block entity data
- `debug/Dev2TestCommands.java` ✅ Debug commands

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

### For Developers (Contributing)

**Core systems are complete!** Consider working on:

1. **Advanced Features** (see README.md "In progress" section)
   - AE2/Refined Storage integration
   - Factory Controller block for multiple PreFabs
   - Advanced filtering (item/fluid whitelists)
   - Anti-cheat validation system

2. **Polish & Bug Fixes**
   - Edge case handling
   - Performance optimizations
   - User experience improvements
   - Documentation updates

3. **Testing**
   - Test with modded machines (Create, Mekanism, etc.)
   - Stress test with large factories
   - Multi-PreFab scenarios
   - Edge cases (chunk boundaries, dimension transitions)

### For Testers (Testing Current Build)

**Core systems are functional!** Test:
1. Build a factory in a Compact Machine
2. Place Importers/Exporters inside the CM
3. Upgrade CM to PreFab with TPS Upgrade item
4. Configure faces with Simulation Wrench
5. Start simulation, verify rate measurement
6. Finish simulation, verify cached production works
7. Break PreFab, verify it becomes portable item with preserved state

### For Architects (Understanding System)

**Read in this order**:
1. ARCHITECTURE_CONDUIT.md - Technical spec
2. ARCHITECTURE_PIVOT.md - Why we changed
3. CLAUDE.md - Project constraints
4. CM_API_INTEGRATION.md - How we integrate with Compact Machines

---

## ❓ Common Questions

### Q: Where do I start coding?
**A**: Read MVP_SCOPE.md, then follow TODO.md Phase 1 (Face Config Data Structures).

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
**A**: Estimated 4-6 weeks (see TODO.md "Priority Order" section).

---

## 🔗 Quick Links

### Documentation
- [README_ARCHITECTURE.md](README_ARCHITECTURE.md) - High-level overview
- [MVP_SCOPE.md](MVP_SCOPE.md) - Scope definition
- [TODO.md](TODO.md) - Implementation roadmap
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

**Architecture**: ✅ Defined and implemented (conduit-based caching)  
**Documentation**: ✅ Complete and organized  
**Codebase**: ✅ Core systems complete  
**Phase 1**: ✅ Complete (face config data structures)  
**Phase 2**: ✅ Complete (Importer/Exporter blocks)  
**Phase 3**: ✅ Complete (transport logic)  
**Phase 4**: ✅ Complete (rate measurement)  
**Phase 5**: ✅ Complete (cached production)  
**Phase 6**: ✅ Complete (wrench control + GUI)  
**Phase 7+**: ✅ Complete (PreFab-as-Item, enhanced GUI, room filtering)  

**Current Focus**: Advanced features, integrations, and polish

---

## 🎓 Learning Path

**If you're new to the project**:

### Day 1: Understand the Goal
- Read README_ARCHITECTURE.md
- Understand: "Cache rates → Run without chunks → Better TPS"

### Day 2: Understand the Implementation
- Read ARCHITECTURE_CONDUIT.md
- Understand: "Conduits not storage, PULL/PUSH faces, fractional math"
- Core system is complete, review existing code

### Day 3: Review Current Code
- Browse source files in `../fpscompress-template-1.21.11/src/main/java/`
- Key files: PrefabBlockEntity.java, ImporterBlock.java, ExporterBlock.java
- Understand how face configs, transport, and caching work

### Day 4: Understand Next Steps
- Read TODO.md to see what's left
- Read MVP_SCOPE.md "Post-MVP Features" section
- Identify areas where you can contribute

### Day 5: Start Contributing
- Pick a feature from "In progress" section of README.md
- Or improve existing features (better GUI, performance, etc.)
- Or add test coverage and edge case handling

---

**Ready to start?** → Read **README_ARCHITECTURE.md** next!
