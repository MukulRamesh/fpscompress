# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FPSCompress is a NeoForge 1.21.11 Minecraft mod implementing a factory compression system with virtual dimensions. The mod is designed with a modular architecture split across 5 development domains that integrate through defined API contracts.

**Mod ID**: `fpscompress`
**Package**: `com.mukulramesh.fpscompress`
**Working Directory**: `fpscompress-template-1.21.11/` (relative to repository root)

### Current State

The repository currently contains the NeoForge template structure with example code:
- `FPSCompress.java` - Template main mod class with example blocks/items/creative tab
- `FPSCompressClient.java` - Template client-side setup
- `Config.java` - Template configuration with example config values

**The 5-module architecture described below is the planned design from `notes.md` and has not yet been implemented.**

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

## Planned Architecture

The project will follow a modular design with 5 independent but integrated components (see `notes.md` for detailed developer assignments):

### 1. Core Registry & Block Shell (`IPortalRouter`)
- **Purpose**: Physical Overworld blocks with NeoForge 1.21 capabilities
- **Blocks**: `machine_portal` (with BlockEntity), `input_proxy`, `output_proxy`
- **API**: `IPortalRouter` interface for routing resources to dimensions or internal virtual buffers
- **Tech**: `DeferredRegister`, custom Data Components (Codec/StreamCodec), `RegisterCapabilitiesEvent`
- **Capabilities**: `IItemHandler` (27 slots), `IFluidHandler` (50,000 mB), `IEnergyStorage` (1,000,000 FE)

### 2. Client Assets & DataGen
- **Purpose**: Textures and JSON data generation
- **Location**: `src/main/resources/assets/fpscompress/`
- **DataGen Providers**: `BlockStateProvider`, `ItemModelProvider`, `LanguageProvider`, `BlockLootSubProvider`
- **Textures**: 16x16 PNGs in `textures/block/` for machine_portal, input_proxy, output_proxy

### 3. Spatial & Dimension Manager (`ISpaceManager`)
- **Purpose**: Custom void dimension with non-overlapping factory zones
- **API**: `ISpaceManager` interface for spiral grid allocation
- **Mechanics**: Spiral expansion (Right→Down→Left→Up), 1,000-block spacing, `SavedData` persistence
- **Chunk Loading**: 3x3 area force-loading via `TicketHelper` on void dimension's `ServerLevel`

### 4. State Machine & Fractional Logic (`IMachineLogic`)
- **Purpose**: Pure Java state management and fractional production math
- **States**: `BUILDING` → `SIMULATING` → `CACHED` / `HALTED`
- **Math**: Fractional accumulator for sub-tick production rates: `Rate_per_tick = Total_Output / Sim_Time`
- **No Minecraft Dependencies**: Unit-testable pure Java

### 5. Spatial Capability Scanner (`IAntiCheatScanner`)
- **Purpose**: Anti-cheat validation via BlockEntity resource scanning
- **Mechanics**: Scans 15×15×15 volume, compares pre/post simulation snapshots
- **Performance**: Only scans BlockEntities (not all 3,375 blocks), queries capabilities efficiently
- **Config**: NeoForge `ModConfig.Type.SERVER` for block whitelist/blacklist

## Key Integration Points

Each module will expose an interface that other modules consume:
- **Router → Logic**: Portal routes resources based on state machine decisions
- **Space Manager → Router**: Provides dimension coordinates for routing
- **Scanner → Logic**: Validates that factory isn't cheating with hidden batteries
- **DataGen**: Standalone, generates assets for registered blocks

## The Central Integrator: FactoryIntegrator

**CRITICAL**: The `FactoryIntegrator` class is the "central nervous system" of the mod. It holds **NO logic of its own**. Its entire job is to translate and pass data between the APIs of Devs 1, 3, 4, and 5.

### Architecture Pattern

```
┌─────────────────────────────────────────────────┐
│          FactoryIntegrator (Glue Code)          │
│  - Coordinates state transitions                │
│  - Passes data between isolated modules         │
│  - NO business logic of its own                 │
└──────────────────┬──────────────────────────────┘
                   │
      ┌────────────┼────────────┬─────────────┐
      │            │            │             │
      ▼            ▼            ▼             ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│  Dev 1   │ │  Dev 3   │ │  Dev 4   │ │  Dev 5   │
│ IVirtual │ │   ICM    │ │ IMachine │ │ IAnti    │
│ Machine  │ │Interceptor│ │  Logic   │ │  Cheat   │
│  Data    │ │          │ │          │ │ Scanner  │
└──────────┘ └──────────┘ └──────────┘ └──────────┘
```

### Interface Contracts

The `FactoryIntegrator` depends on these four interfaces:

1. **`IVirtualMachineData`** (Dev 1): Virtual buffers in Overworld block
   - `hasTpsUpgrade()`: Check if TPS upgrade installed
   - `addToBuffer()`: Add resources to virtual buffer
   - `extractFromBuffer()`: Remove resources from virtual buffer
   - Location: `com.mukulramesh.fpscompress.portal.IVirtualMachineData`

2. **`ICMInterceptor`** (Dev 3): Chunk loading control in CM dimension
   - `setRoomChunkState()`: Load/unload chunks
   - `setRoutingState()`: Toggle physical vs virtual routing
   - Location: `com.mukulramesh.fpscompress.spatial.ICMInterceptor`

3. **`IMachineLogic`** (Dev 4): Pure Java state machine with fractional math
   - `getCurrentState()`: Get current state (BUILDING/SIMULATING/CACHED/HALTED)
   - `startSimulation()`: Begin rate calculation
   - `finishSimulation()`: Complete rate calculation and enter CACHED mode
   - `tick()`: Update fractional production during CACHED mode
   - `pushInput()/pullOutput()`: Feed resources to/from math logic
   - Location: `com.mukulramesh.fpscompress.logic.IMachineLogic`

4. **`IAntiCheatScanner`** (Dev 5): Anti-cheat validation
   - `takeSnapshot()`: Capture BlockEntity capabilities state
   - `validateLoop()`: Compare snapshots to detect cheating
   - Location: `com.mukulramesh.fpscompress.scanner.IAntiCheatScanner`

### State Flow Through Integrator

```
BUILDING (player sets up)
    │
    │ [Player clicks "Start Simulation"]
    │ → takeSnapshot() [Dev 5]
    │ → startSimulation() [Dev 4]
    ▼
SIMULATING (observing rates)
    │
    │ [Player clicks "Finish Simulation"]
    │ → takeSnapshot() [Dev 5]
    │ → validateLoop() [Dev 5]
    │ → finishSimulation() [Dev 4]
    │ → setRoomChunkState(false) [Dev 3]
    │ → setRoutingState(true) [Dev 3]
    ▼
CACHED (math-only mode)
    │
    │ [Every tick]
    │ → tick() [Dev 4]
    │ → extractFromBuffer() [Dev 1] → pushInput() [Dev 4]
    │ → pullOutput() [Dev 4] → addToBuffer() [Dev 1]
    │
    │ [If starved or blocked]
    │ → setRoomChunkState(true) [Dev 3]
    │ → setRoutingState(false) [Dev 3]
    ▼
HALTED (cache broke, needs player fix)
```

### Blame Assignment (Debugging)

When issues occur, the integrator's simple design makes debugging trivial:

| Problem | Responsible Developer |
|---------|----------------------|
| Chunk loading crashes | Dev 3 (ICMInterceptor) |
| Anti-cheat false positives | Dev 5 (IAntiCheatScanner) |
| Wrong production rates | Dev 4 (IMachineLogic) |
| Virtual buffer routing issues | Dev 1 (IVirtualMachineData) |
| Integration logic errors | Integration Team (FactoryIntegrator) |

No developer steps on anyone else's toes. Each module is isolated and testable.

### Player Control Mechanism: Simulation Wrench

**Control Tool**: `SimulationWrenchItem` (Physical tool, no GUI required)

Players use a handheld "Simulation Wrench" to control simulation phases:
- **Right-click machine_portal in BUILDING state**: Start simulation
- **Right-click machine_portal in SIMULATING state**: End simulation and enter CACHED mode

**Implementation** (See `SIMULATION_CONTROLS.md` for complete guide):
- Dev 1: Create `SimulationWrenchItem` with `useOn()` logic
- Dev 2: Create 16x16 wrench texture and localization
- Recipe: 2 Gold Ingots + 2 Sticks → Simulation Wrench

**Benefits**:
- No GUI programming required (~30 lines of code)
- No client/server sync complexity
- Intuitive UX (right-click = action)
- Can be upgraded to full GUI in v2.0 if desired

## Implementation Strategy

When implementing the architecture:

1. **Replace Template Code**: Remove example_block, example_item, and example_tab from FPSCompress.java
2. **Module Independence**: Each of the 5 modules should be implementable independently and tested in isolation
3. **Interface-First Design**: Define and commit API interfaces before implementing concrete classes
4. **Package Organization**: Consider creating subpackages: `portal`, `spatial`, `logic`, `scanner`, `datagen`
5. **Testing**: Module 4 (State Machine) should have unit tests since it's pure Java with no Minecraft dependencies

## Current File Structure

```
fpscompress-template-1.21.11/
├── src/main/java/com/mukulramesh/fpscompress/
│   ├── FPSCompress.java       # Main mod class with example DeferredRegisters
│   │                          # (Contains example_block, example_item, example_tab)
│   ├── FPSCompressClient.java # Client-only initialization
│   └── Config.java            # Example ModConfigSpec with sample config values
├── src/main/resources/
│   └── assets/fpscompress/
│       └── lang/en_us.json    # Localization (currently empty template)
├── src/main/templates/META-INF/
│   └── neoforge.mods.toml     # Mod metadata template (uses gradle.properties variables)
├── build.gradle               # NeoForge ModDevGradle build configuration
├── gradle.properties          # Mod metadata: version=1.0.0, minecraft=1.21.11, neo=21.11.38-beta
└── settings.gradle
```

**Note**: Template example code (example_block, example_item, etc.) should be replaced with actual mod implementation.

## NeoForge 1.21 Specifics

- **Java Version**: Java 21 (shipped with Minecraft 1.21.11)
- **Mappings**: Parchment 2025.12.20 on Minecraft 1.21.11
- **Data Components**: Use `Codec` and `StreamCodec` for custom data on items
- **Capabilities**: Registered via `RegisterCapabilitiesEvent`, not `@CapabilityInject`
- **Config**: Use `ModConfigSpec` with `ModConfig.Type.COMMON` or `.SERVER`

## Important Constraints

- **No GUI for MachinePortalBlockEntity**: Internal storage acts like a headless chest
- **No directional blocks**: All blocks use standard cube mapping (no facing)
- **Performance**: Scanner must NOT iterate all block positions—only query existing BlockEntities
- **State Transitions**: Must be explicit in IMachineLogic (no automatic transitions)
- **Security**: Ensure validation prevents infinite resource loops via hidden batteries
