# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FPSCompress is a NeoForge 1.21.11 Minecraft mod implementing a factory compression system with virtual dimensions. The mod caches factory production rates to run factories "virtually" without chunk loading, dramatically improving server performance.

**Mod ID**: `fpscompress`
**Package**: `com.mukulramesh.fpscompress`
**Working Directory**: `fpscompress-template-1.21.11/` (relative to repository root)

### Core Concept: PreFabs

**PreFabs** (Prefabricated Factories) are cross-dimensional conduits that enable **factory input/output caching**. The mod's primary purpose is to cache production rates so factories can run "virtually" without chunk loading.

**Three-Block System**:
1. **PreFab Block** (Overworld) - Routes resources, controls state machine
2. **Importer Block** (CM dimension) - Input gates where resources enter factory
3. **Exporter Block** (CM dimension) - Output gates where resources exit factory

**How It Works**:
- Player places Importers/Exporters inside CM factory room
- PreFab faces link to specific Importers/Exporters (UUID-based)
- PULL face: Overworld chest → PreFab → Importer → Factory machines
- PUSH face: Factory machines → Exporter → PreFab → Overworld chest
- During SIMULATING: Measure actual rates while CM chunks loaded
- During CACHED: Simulate production using fractional math, CM chunks **unloaded**

**Player Experience**:
1. Build factory inside a Compact Machine room
2. **Place Importer/Exporter blocks** inside CM room (input/output gates)
3. Right-click CM block with "TPS Upgrade" item → becomes PreFab
4. Shift+Right-click PreFab with Simulation Wrench → Open face config GUI
5. Configure each face:
   - Set mode (PULL/PUSH/DISABLED)
   - Set filter (ITEMS/FLUIDS/ENERGY)
   - **Link to specific Importer/Exporter** (select from dropdown)
6. Connect chests/hoppers to PreFab faces in Overworld
7. Start SIMULATION: PreFab observes actual production rates (CM chunks loaded)
8. Finish SIMULATION: PreFab calculates rates, enters CACHED mode (CM chunks **unload**)
9. CACHED mode: PreFab uses math to simulate production based on cached rates

**Key Design Principles**:
- **PreFabs are conduits, not storage** - resources transport instantly between dimensions
- **Caching is the primary goal** - Everything else exists to enable the caching system
- **Importer/Exporter clarity** - Clear input/output points (no complex coordinate math)
- **Vanilla-only for MVP** - No AE2, no Controller block, no external mod integrations until caching works
- **No internal storage** - Players could cheat (Importer → Chest → Exporter), but that's post-MVP validation

### Current State

**Codebase Status**: Cleaned up, deprecated code archived
- Active source: 19 Java files in `src/main/java/`
- Deprecated code: Moved to `deprecated/` folder (old virtual buffer system)
- Documentation: Complete, organized (see START_HERE.md)

**Implementation Status**: Architecture defined, ready for Phase 1
- Phase 1: Face configuration + adjacent block detection (DE-RISK)
- Phase 2: Importer/Exporter blocks
- Phase 3-6: Transport → Rate measurement → Caching → Wrench control
- Phase 7-8: Enhanced GUI + Dynamic capabilities (optional)

**See TODO.md for complete implementation roadmap.**

---

## Environment Setup

**Java 21 is mandatory** for Minecraft 1.20.5+ and 1.21.11. The project will not build or run with older Java versions.

### Verify Java Version

```bash
java -version  # Should show version 21
```

If Java 21 is not installed:
- Download from [Adoptium (Eclipse Temurin)](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/#java21)
- Set `JAVA_HOME` environment variable to Java 21 installation
- Ensure Java 21 is first in your `PATH`

Gradle is configured to use Java 21 toolchain (see `build.gradle` line 29), but the system Java must also be 21.

---

## Build Commands

All Gradle commands should be run from `fpscompress-template-1.21.11/` directory:

```bash
cd "fpscompress-template-1.21.11"

# Build the mod
./gradlew build

# Clean build artifacts
./gradlew clean

# Refresh dependencies (use when libraries are missing)
./gradlew --refresh-dependencies

# Run data generators (creates block states, models, loot tables, lang files)
./gradlew runData
```

---

## Code Quality & Linting

**IMPORTANT**: Run all three linters after making code changes to catch issues early.

### Quick Lint Check (Run All)
```bash
cd "fpscompress-template-1.21.11"
./gradlew clean compileJava checkstyleMain spotbugsMain
```

### Individual Linters

**1. Java Compiler Warnings** (Strictest - catches compilation errors and warnings)
```bash
./gradlew compileJava --warning-mode all
```
- Enabled flags: `-Xlint:all` with `-Werror` (warnings treated as errors)
- Catches: deprecated API usage, unchecked operations, missing overrides, etc.

**2. Checkstyle** (Style and code conventions)
```bash
./gradlew checkstyleMain
```
- Configuration: `config/checkstyle/checkstyle.xml`
- Checks: naming conventions, import order, whitespace, block structure, etc.
- Report: `build/reports/checkstyle/main.html`

**3. SpotBugs** (Bug detection and potential issues)
```bash
./gradlew spotbugsMain
```
- Detects: null pointer bugs, resource leaks, concurrency issues, security vulnerabilities
- Report: `build/reports/spotbugs/main.html`

### Fixing Common Issues

- **Compilation errors**: Fix immediately - code won't build
- **Checkstyle violations**: Follow Java conventions for consistency
- **SpotBugs warnings**: Review carefully - often indicates real bugs

### IDE Integration

**VS Code**: Java extension shows real-time linting in the "Problems" panel

**IntelliJ IDEA**:
- Checkstyle plugin: Settings → Plugins → Install "CheckStyle-IDEA"
- SpotBugs plugin: Settings → Plugins → Install "SpotBugs"

---

## Running the Mod

**Gradle tasks:**
```bash
./gradlew runClient     # Launch Minecraft client
./gradlew runServer     # Launch dedicated server
./gradlew runGameTestServer  # Run game tests
```

**VSCode launch configurations** (preferred in IDE):
- `Client` - Launch client with mod loaded
- `Data` - Run data generators
- `GameTestServer` - Run game tests
- `Server` - Launch dedicated server

Launch configs are in `fpscompress-template-1.21.11/.vscode/launch.json`.

---

## Architecture: Conduit-Based Caching System

**Primary Goal**: Cache factory input/output rates to run factories without chunk loading.

### Three-Block System

**1. PreFab Block** (Overworld only)
- Upgraded CM block with face configuration
- 6 independently configurable faces (PULL/PUSH/DISABLED per face)
- Routes resources between Overworld and CM dimension via Importers/Exporters
- Controls state machine (BUILDING/SIMULATING/CACHED/HALTED)
- No internal storage - just a router

**2. Importer Block** (CM dimension only)
- Placed inside CM room by player
- Acts as **input gate**: Receives resources from PreFab PULL faces
- Exposes IItemHandler/IFluidHandler/IEnergyStorage to adjacent machines
- Has unique UUID for PreFab linking
- Example: Place Importer next to furnace, furnace pulls from Importer

**3. Exporter Block** (CM dimension only)
- Placed inside CM room by player
- Acts as **output gate**: Sends resources to PreFab PUSH faces
- Queries adjacent machines for resources to extract
- Has unique UUID for PreFab linking
- Example: Place Exporter next to furnace output, Exporter pulls from furnace

**Why Importers/Exporters?**
- ✅ Clear input/output points (player places them)
- ✅ No coordinate mapping math (UUID-based linking)
- ✅ Flexible factory layout (place gates anywhere)
- ✅ Visible to player (can see where resources enter/exit)

### State Machine

**BUILDING**:
- Player configures PreFab faces (which face → which Importer/Exporter)
- Places Importers/Exporters inside CM room
- Factory not running yet

**SIMULATING** (CM chunks LOADED):
- PreFab measures actual resource flow rates
- Counts items/fluids/energy transported per tick
- Records: `Rate = Total_Transported / Time_Elapsed`
- Example: 128 iron ingots in 600 ticks = 0.213 iron/tick

**CACHED** (CM chunks UNLOADED ← Performance gain!):
- PreFab uses fractional math to simulate production
- Accumulates fractional rates: `accumulator += rate`
- When `accumulator >= 1.0`: Transport whole units
- Example: 0.213 iron/tick → every ~4.7 ticks, push 1 iron ingot
- **This is the entire point of the mod!**

**HALTED** (Cache broke):
- Input starved (can't pull from Overworld chest)
- Output blocked (can't push to Overworld chest)
- **CM chunks STAY UNLOADED** (don't reload!)
- Player fixes Overworld side (add inputs, clear outputs)
- Wrench click to resume → back to SIMULATING

### Face Configuration

**Each PreFab face** configures:
- **Mode**: DISABLED, PULL (Overworld→Importer), PUSH (Exporter→Overworld)
- **Filter**: ALL, ITEMS, FLUIDS, ENERGY
- **Target UUID**: Which Importer/Exporter to link to

**Example setup**:
```
Overworld:
  [Coal Chest] → [PreFab NORTH=PULL ITEMS → Importer #1]

CM Dimension:
  [Importer #1] → [Furnace input]
  [Furnace output] → [Exporter #1]
  
Overworld:
  [PreFab SOUTH=PUSH ITEMS ← Exporter #1] → [Iron Chest]
```

---

## Implementation Strategy

### Phase Order (De-risk First!)

**Phase 1**: Face Config + Adjacent Detection (Week 1)
- Prove PreFab can detect adjacent blocks
- Debug command: Right-click PreFab → Shows adjacent blocks and capabilities
- Simple face config GUI (mode/filter, no Importer linking yet)
- **Goal**: Validate core concept before building Importers/Exporters

**Phase 2**: Importer/Exporter Blocks (Week 2)
- Only after Phase 1 proves adjacent detection works
- Create ImporterBlock/ExporterBlock with UUID generation
- Add UUID linking to Phase 1 GUI

**Phase 3**: Basic Transport (Week 2)
- Hardcoded config first (test without GUI)
- PreFab PULL: Extract from Overworld → Insert to Importer
- PreFab PUSH: Extract from Exporter → Insert to Overworld

**Phase 4**: Rate Measurement (Week 3)
- During SIMULATING: Count resources transported
- Calculate rates: `rate = total / ticks`

**Phase 5**: Cached Production (Week 3)
- During CACHED: Accumulate fractional rates
- Transport whole units when accumulator >= 1.0

**Phase 6**: Wrench Control (Week 4)
- State transitions: BUILDING → SIMULATING → CACHED

**Phase 7-8**: Polish (Optional)
- Enhanced GUI, dynamic capabilities

**See TODO.md for complete task breakdown.**

---

## NeoForge 1.21 Specifics

- **Java Version**: Java 21 (shipped with Minecraft 1.21.11)
- **Mappings**: Parchment 2025.12.20 on Minecraft 1.21.11
- **Data Components**: Use `Codec` and `StreamCodec` for custom data on items
- **Capabilities**: Registered via `RegisterCapabilitiesEvent`, not `@CapabilityInject`
- **Config**: Use `ModConfigSpec` with `ModConfig.Type.COMMON` or `.SERVER`

---

## Important Constraints

### MVP Scope (What IS Included)
- ✅ One PreFab block in world
- ✅ Importer/Exporter blocks in CM dimension
- ✅ Face configuration (PULL/PUSH modes, UUID linking)
- ✅ Transport between Overworld and CM dimension
- ✅ Rate measurement (SIMULATING state)
- ✅ Cached production (CACHED state with fractional math)
- ✅ Vanilla blocks only (chests, furnaces, hoppers)

### MVP Scope (What is NOT Included)
- ❌ AE2 integration
- ❌ Refined Storage integration
- ❌ Factory Controller block
- ❌ Multiple PreFab management
- ❌ Any external mod integrations
- ❌ PreFab-as-item portability
- ❌ Advanced filters (item/fluid whitelists)
- ❌ Anti-cheat validation (players can cheat with hidden chests, but that's post-MVP)

### Design Principles
- **No internal storage**: PreFabs are conduits, not chests
- **Faces are independent**: Each face has separate config
- **Caching is the goal**: Everything exists to enable rate-based virtual production
- **MVP first**: Get basic caching working before adding features
- **Performance**: Unload CM chunks during CACHED mode (that's the whole point!)
- **Fractional math**: Production rates < 1.0 item/tick require accumulator pattern

---

## Key Technical Details

### Importer/Exporter Linking (UUID-Based)

**Setup**:
```java
// Importer placed in CM dimension
ImporterBlockEntity importer = new ImporterBlockEntity();
UUID importerUUID = UUID.randomUUID(); // e.g., abc-123
importer.setUUID(importerUUID);

// PreFab face configured
FaceConfig northFace = new FaceConfig();
northFace.mode = FaceMode.PULL;
northFace.resourceType = ResourceFilter.ITEMS;
northFace.targetUUID = importerUUID; // Link to Importer abc-123
```

**Runtime (PULL mode)**:
```java
// PreFab NORTH face = PULL ITEMS → Importer abc-123

// 1. Extract from Overworld
BlockPos overworldPos = prefabPos.relative(Direction.NORTH);
BlockEntity chest = level.getBlockEntity(overworldPos);
IItemHandler chestHandler = chest.getCapability(ItemHandler.BLOCK);
ItemStack extracted = chestHandler.extractItem(0, 64, false);

// 2. Find target Importer by UUID
ServerLevel cmLevel = getCMLevel();
ImporterBlockEntity importer = findImporterByUUID(cmLevel, targetUUID);

// 3. Insert to Importer
ItemStack remainder = importer.insertItem(extracted);
int transferred = extracted.getCount() - remainder.getCount();

// 4. Track for rate measurement (SIMULATING only)
if (state == MachineState.SIMULATING) {
    recordItemTransfer("minecraft:iron_ore", transferred);
}
```

### Fractional Production

```java
// Factory produces 128 iron ingots over 600 ticks
double rate = 128.0 / 600.0; // 0.2133 ingots/tick

// CACHED mode tick logic
ironAccumulator += rate; // Add 0.2133 each tick
if (ironAccumulator >= 1.0) {
    int wholeItems = (int) ironAccumulator;
    ironAccumulator -= wholeItems;
    
    // Push to Overworld via Exporter
    ExporterBlockEntity exporter = findExporterByUUID(cmLevel, exporterUUID);
    ItemStack toPush = new ItemStack(Items.IRON_INGOT, wholeItems);
    BlockPos overworldPos = prefabPos.relative(Direction.SOUTH);
    BlockEntity outputChest = level.getBlockEntity(overworldPos);
    outputChest.getCapability(ItemHandler.BLOCK).insertItem(0, toPush, false);
}
```

### Chunk Loading Control

```java
// Use existing CMInterceptorImpl
CMInterceptorImpl interceptor = CMInterceptorImpl.getInstance();

// Start simulation: Load CM chunks
interceptor.setRoomChunkState(roomCode, true);

// Enter CACHED mode: Unload CM chunks (performance!)
interceptor.setRoomChunkState(roomCode, false);

// HALTED state: Keep chunks unloaded (player fixes Overworld side)
// Don't reload chunks in HALTED!
```

---

## Post-MVP Features (Future)

### Anti-Cheat Validation (v1.0+)
- **Problem**: Players can place chest in CM dimension, factory "produces" from storage
- **Solution**: Bidirectional redstone protocol for graceful shutdown validation
- See VALIDATION_REDSTONE_PROTOCOL.md for complete specification

### AE2 Integration (v1.1+)
- Factory Controller block holds multiple PreFab items
- Controller exposes unified interface to AE2 network
- Automatic resource routing via ME system

### PreFab-as-Item (v1.2+)
- Store all data in item NBT (not BlockEntity)
- Portable factories (carry in inventory, ender chest)
- Trade PreFabs with other players

---

## Documentation Index

**Essential Reading** (start here):
1. **START_HERE.md** - Entry point for new contributors
2. **README_ARCHITECTURE.md** - High-level overview
3. **IMPORTER_EXPORTER_SYSTEM.md** - How the three-block system works
4. **MVP_SCOPE.md** - What's in/out of MVP scope
5. **TODO.md** - Implementation roadmap (7 phases)

**Technical Specs**:
- **ARCHITECTURE_CONDUIT.md** - Complete technical specification
- **ARCHITECTURE_PIVOT.md** - Why we changed from virtual buffers
- **VALIDATION_DELTA_ACCOUNTING.md** - MVP rate measurement via import/export deltas
- **VALIDATION_REDSTONE_PROTOCOL.md** - Post-MVP anti-cheat system
- **CM_API_INTEGRATION.md** - Compact Machines integration details

**Reference**:
- **CLEANUP_SUMMARY.md** - What was archived and why
- **deprecated/** folder - Old virtual buffer system code

---

## Quick Reference

### File Structure (Active Code)
```
src/main/java/com/mukulramesh/fpscompress/
├── portal/
│   ├── PrefabBlock.java              ← Main PreFab block
│   ├── PrefabBlockEntity.java        ← Needs refactor for face configs
│   ├── MachineState.java             ✓ Keep as-is (BUILDING/SIMULATING/CACHED/HALTED)
│   ├── RoomCoordinateCache.java      ✓ Keep for coordinate mapping
│   ├── TpsCacheUpgradeItem.java      ✓ Keep (CM → PreFab upgrade)
│   ├── SimulationWrenchItem.java     ← Needs modify (state transitions)
│   └── ... (other portal files)
├── spatial/
│   ├── CMInterceptorImpl.java        ✓ Keep (chunk loading control)
│   └── ICMInterceptor.java           ✓ Keep (interface)
└── ... (other packages)
```

### Common Commands
```bash
# Compile and check
./gradlew clean compileJava checkstyleMain spotbugsMain

# Run in Minecraft
./gradlew runClient

# Check what to implement next
cat TODO.md | grep "Phase 1"
```

### Getting Help
- Questions about architecture? → See ARCHITECTURE_CONDUIT.md
- Questions about Importers/Exporters? → See IMPORTER_EXPORTER_SYSTEM.md
- Questions about scope? → See MVP_SCOPE.md
- Questions about implementation? → See TODO.md
- Lost? → Read START_HERE.md

---

**Ready to start? Read START_HERE.md, then follow TODO.md Phase 1!**
