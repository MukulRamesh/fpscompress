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
3. Right-click CM block with "PreFab Upgrade Template" item → becomes PreFab
4. **Shift+Right-click PreFab with Simulation Wrench** → Open face config GUI
5. Configure each face:
   - Set mode (PULL/PUSH/DISABLED)
   - Set filter (ITEMS/FLUIDS/ENERGY)
   - **Link to specific Importer/Exporter** (select from dropdown)
6. Connect chests/hoppers to PreFab faces in Overworld
7. **Right-click PreFab with empty hand** → Open control menu → Start SIMULATION
8. PreFab observes actual production rates (CM chunks loaded)
9. **Right-click PreFab with empty hand** → Stop simulation → Enters CACHED mode (CM chunks **unload**)
10. CACHED mode: PreFab uses math to simulate production based on cached rates

**Important:** The Simulation Wrench is **only** for face configuration (Shift+Right-click). State changes (BUILDING→SIMULATING→CACHED) are done via the control menu (right-click with empty hand or non-wrench item).

**Key Design Principles**:
- **PreFabs are conduits, not storage** - resources transport instantly between dimensions
- **Caching is the primary goal** - Everything else exists to enable the caching system
- **Importer/Exporter clarity** - Clear input/output points (no complex coordinate math)
- **Vanilla-first approach** - Core system works with vanilla, mod integration as optional enhancements

### Current State

**Codebase Status**: Core system complete, active development on enhancements
- Active source: 19+ Java files in `src/main/java/`
- Core Features: ✅ Face configuration, Importer/Exporter system, rate measurement, cached production
- Documentation: Complete, organized (see START_HERE.md)

**Implementation Status**: v0.2.0+ (Core caching system functional)
- ✅ Phase 1-6: Face config, Importers/Exporters, transport, rate measurement, caching, state control
- ✅ Enhanced features: Frequency system, customizable rate units, blueprint scanning
- 🔄 Current focus: Polish, performance optimization, advanced features (see TODO.md)

**See TODO.md for current development roadmap and CHANGELOG.md for completed features.**

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

## Version Management

Use `bump-version.sh` to update the mod version in `gradle.properties`. The script automatically creates a git commit and tag.

**Usage**:
```bash
cd fpscompress-template-1.21.11
./bump-version.sh [major|minor|patch|alpha|beta|release]
```

**Version progression**:
- `major/minor/patch` — Bumps version number and removes pre-release tag (e.g., `0.1.0-alpha` → `0.2.0`)
- `alpha` — Sets pre-release to `-alpha` (e.g., `0.1.0` → `0.1.0-alpha`)
- `beta` — Sets pre-release to `-beta` (e.g., `0.1.0-alpha` → `0.1.0-beta`)
- `release` — Removes pre-release tag (e.g., `0.1.0-beta` → `0.1.0`)

**Example workflow**:
```bash
./bump-version.sh minor   # 0.0.0 → 0.1.0
./bump-version.sh alpha   # 0.1.0 → 0.1.0-alpha (development)
./bump-version.sh beta    # 0.1.0-alpha → 0.1.0-beta (testing)
./bump-version.sh release # 0.1.0-beta → 0.1.0 (stable)
./bump-version.sh patch   # 0.1.0 → 0.1.1 (hotfix)
```

The script creates a commit and tag (e.g., `v0.1.0-alpha`). Push with:
```bash
git push && git push --tags
```

### Changelog Management

**IMPORTANT**: Always update `CHANGELOG.md` when making significant changes.

**Location**: `CHANGELOG.md` in the repository root

**Format**: Follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) with semantic versioning

**Structure**:
```markdown
## [Unreleased]
### Added / Changed / Fixed / Breaking Changes
- Document work in progress for next release

## [X.Y.Z] - YYYY-MM-DD
### Added / Changed / Fixed
- Document completed features for this version
```

**When to update**:
- Before committing significant features (add to `[Unreleased]`)
- When bumping version (move `[Unreleased]` to versioned section)
- After fixing bugs (document in `### Fixed`)

**Example workflow**:
1. Implement feature → Update `[Unreleased]` section
2. Run `./bump-version.sh minor` → Move changes to `[X.Y.Z]` section
3. Commit: "Update CHANGELOG for vX.Y.Z release"

**Current versions**:
- `[Unreleased]` → v0.3.0 (Frequency system)
- `[0.2.0]` → Crafting recipes & JEI support
- `[0.1.0-alpha]` → Initial alpha release

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

## Debug Commands

Debug commands for testing and development (requires OP level 2):

### `/fps_dev2` Commands

**Give Test PreFab** - Create PreFab items with pre-configured cached rates for testing:

```bash
# Default: 1 dirt/tick → 1 diamond/tick
/fps_dev2 give-test-prefab

# Custom single input/output
/fps_dev2 give-test-prefab <inputItem> <inputRate> <outputItem> <outputRate>
/fps_dev2 give-test-prefab minecraft:iron_ore 2.5 minecraft:iron_ingot 5.0

# Multiple inputs/outputs (use quotes!)
/fps_dev2 give-test-prefab list "<inputList>" "<outputList>"
/fps_dev2 give-test-prefab list "minecraft:dirt:1.0,minecraft:cobblestone:2.0" "minecraft:diamond:0.5"
/fps_dev2 give-test-prefab list "minecraft:oak_log:1.0,minecraft:birch_log:1.0,minecraft:spruce_log:1.0" "minecraft:charcoal:4.0"
```

**Format for lists**: `"item:rate,item:rate,..."` (comma-separated, namespaced item IDs required)

**Test PreFab Behavior**:
- Each test PreFab gets a **unique fake roomCode** (format: `fake_<uuid>`) to isolate equipment
- Fake Importers/Exporters are registered in `ImporterExporterRegistry` with proper display names
- Display names derived from frequency items (e.g., "Diamond Exporter" instead of "Unnamed Exporter")
- Equipment can be iterated per-PreFab using the unique roomCode
- Test PreFabs function in CACHED mode and will actually transfer items when connected to chests
- **NBT preservation**: PreFabs preserve all cached data when placed/broken:
  - `PrefabBlock.setPlacedBy()` restores NBT from item to block entity
  - `PrefabBlockEntity.validateLoadedData()` allows fake rooms (no roomCenter required for `fake_*` roomCodes)
  - Schema version 2 used for UUID-based rate storage (`importerExporterRates`)
- **Status GUI**: Aggregate `cachedRates` derived from `importerExporterRates` in `loadRatesFromNBT()` for GUI display

**Other Dev2 Commands**:
```bash
/fps_dev2 chunks <roomCode> <true|false>       # Test chunk loading/unloading
/fps_dev2 routing <true|false>                 # Test virtual/physical routing
/fps_dev2 diagnostics                          # Show interceptor state
/fps_dev2 test-room <roomCode>                 # Run comprehensive room test
/fps_dev2 debug-reflection <roomCode>          # Debug CM room lookup
/fps_dev2 cleanup                              # Clean up all chunk tickets
```

**Implementation**: `src/main/java/com/mukulramesh/fpscompress/debug/Dev2TestCommands.java`

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

## Core Features (Implemented)

### Current System Capabilities
- ✅ PreFab block with face configuration (PULL/PUSH/DISABLED modes)
- ✅ Importer/Exporter blocks with UUID-based linking
- ✅ Frequency system for visual organization and rate display
- ✅ Transport between Overworld and CM dimension
- ✅ Rate measurement with delta accounting (SIMULATING state)
- ✅ Cached production with fractional math (CACHED state)
- ✅ Customizable rate display units (per tick/second/minute/hour)
- ✅ Blueprint system for factory scanning and printing
- ✅ Vanilla blocks support (chests, furnaces, hoppers, etc.)

### Future Enhancements (Planned)
- 🔄 AE2 integration (optional enhancement)
- 🔄 Refined Storage integration (optional enhancement)
- 🔄 Factory Controller block (multi-PreFab management)
- 🔄 Advanced filters (item/fluid whitelists)
- 🔄 Anti-cheat validation (bidirectional redstone protocol)

### Design Principles
- **No internal storage**: PreFabs are conduits, not chests
- **Faces are independent**: Each face has separate config
- **Caching is the goal**: Everything exists to enable rate-based virtual production
- **Vanilla-first**: Core system works without external mods
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

## Future Enhancements

### Anti-Cheat Validation
- **Problem**: Players can place chest in CM dimension, factory "produces" from storage
- **Solution**: Bidirectional redstone protocol for graceful shutdown validation
- **Status**: Design complete (see VALIDATION_REDSTONE_PROTOCOL.md)
- **Priority**: Medium (server admins can use existing tools for now)

### AE2 Integration
- Factory Controller block holds multiple PreFab items
- Controller exposes unified interface to AE2 network
- Automatic resource routing via ME system
- **Status**: Planned for future release
- **Priority**: High community request

### Multi-PreFab Management
- Store all data in item NBT (not BlockEntity)
- Portable factories (carry in inventory, ender chest)
- Trade PreFabs with other players
- **Status**: Foundation complete (NBT preservation implemented)
- **Priority**: Medium (current single-PreFab system works well)

---

## Documentation Conversion

### WIKI to Patchouli In-Game Documentation

The repository contains a Python script that converts the markdown files in `WIKI/` to Patchouli JSON format for in-game documentation.

**Script**: `wiki_to_patchouli.py` (in repository root)

**Purpose**: Automatically generate Patchouli in-game guide books from WIKI markdown files

**How it works**:
- Parses all `.md` files in `WIKI/` directory (except README.md and Developer-API.md)
- Converts markdown formatting to Patchouli format codes:
  - Bold (`**text**`) → `$(bold)text$()`
  - Italic (`*text*`) → `$(italic)text$()`
  - Lists (bullets and numbered) → `$(li)`
  - Links (`[text](page)`) → `$(l:category/entry)text$()`
  - Tables → Formatted bulleted lists
  - Code blocks → Plain text (preserves spacing)
- Maps structure:
  - WIKI .md files → Patchouli categories
  - H2 headers (##) → Patchouli entries/chapters
  - H3 headers (###) → Pages within entries
- Generates 9 categories, 90 entries, 227 pages with 99 cross-references

**Output location**:
```
fpscompress-template-1.21.11/src/main/resources/assets/fpscompress/patchouli_books/fpscompress_guide/en_us/
├── categories/  (9 JSON files)
└── entries/     (90 JSON files organized by category)
```

**Usage** (when updating WIKI content):
```bash
# 1. Edit WIKI markdown files (e.g., WIKI/Getting-Started.md)
# 2. Re-run the conversion script
python wiki_to_patchouli.py

# 3. Test in-game to verify formatting
./gradlew runClient
```

**Source of Truth**: The `WIKI/` markdown files are the source of truth. Edit these files, then regenerate Patchouli JSON files using the script. Do not manually edit the generated JSON files directly.

---

## Documentation Index

**Essential Reading** (start here):
1. **START_HERE.md** - Entry point for new contributors
2. **README_ARCHITECTURE.md** - High-level overview
3. **IMPORTER_EXPORTER_SYSTEM.md** - How the three-block system works
4. **TODO.md** - Current development roadmap
5. **CHANGELOG.md** - Completed features and release history

**Technical Specs**:
- **ARCHITECTURE_CONDUIT.md** - Complete technical specification
- **ARCHITECTURE_PIVOT.md** - Why we changed from virtual buffers (historical)
- **VALIDATION_DELTA_ACCOUNTING.md** - Rate measurement via delta accounting
- **VALIDATION_REDSTONE_PROTOCOL.md** - Anti-cheat system design (future)
- **CM_API_INTEGRATION.md** - Compact Machines integration details

**Reference**:
- **MVP_SCOPE.md** - Historical reference (initial implementation scope)
- **CLEANUP_SUMMARY.md** - What was deleted and why (old code in git history)

**User Documentation**:
- **WIKI/** - External user-facing documentation (markdown)
- **wiki_to_patchouli.py** - Script to convert WIKI to in-game Patchouli format

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

# Check what's next
cat TODO.md | grep "Pending Tasks"
```

### Getting Help
- Questions about architecture? → See ARCHITECTURE_CONDUIT.md
- Questions about Importers/Exporters? → See IMPORTER_EXPORTER_SYSTEM.md
- Questions about current features? → See CHANGELOG.md
- Questions about next steps? → See TODO.md
- Lost? → Read START_HERE.md

---

**Ready to start? Read START_HERE.md for an overview, then check TODO.md for current priorities!**
