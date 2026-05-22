# FPSCompress Wiki

Welcome to the FPSCompress documentation. This mod enables **factory compression** through intelligent rate caching, allowing you to run factories without chunk loading overhead.

## What is FPSCompress?

FPSCompress allows you to build factories inside [Compact Machines](https://www.curseforge.com/minecraft/mc-mods/compact-machines) rooms and run them **virtually** without keeping those chunks loaded. The mod measures your factory's actual production rates during a calibration period, then uses fractional math to simulate production while the factory dimension stays unloaded.

**The Result**: Massive server performance gains for factory-heavy builds.

## Quick Links

### Getting Started
- [Getting Started Guide](Getting-Started) - First-time setup and basic usage
- [PreFab System Overview](PreFab-System) - Understanding PreFab blocks and how they work
- [Importer & Exporter Guide](Importer-Exporter-Guide) - Setting up input/output gates

### Core Features
- [Face Configuration](Face-Configuration) - Configuring PreFab faces step-by-step
- [State Machine Guide](State-Machine-Guide) - Understanding BUILDING/SIMULATING/CACHED/HALTED states
- [Cached Production](Cached-Production) - How virtual production works

### Advanced Topics
- [Advanced Setup](Advanced-Setup) - Multi-PreFab factories and organization
- [Troubleshooting](Troubleshooting) - Common issues and solutions
- [Developer API](Developer-API) - For modders integrating with FPSCompress

## Installation

### Requirements
- **Minecraft**: 1.21.1
- **Mod Loader**: NeoForge 21.1.221+
- **Java**: Java 21 (mandatory)
- **Dependencies**: 
  - [Compact Machines](https://www.curseforge.com/minecraft/mc-mods/compact-machines) (required)
  - [Patchouli](https://www.curseforge.com/minecraft/mc-mods/patchouli) (required) - In-game documentation

### Installation Steps
1. Install NeoForge for Minecraft 1.21.1
2. Download Compact Machines and place in `mods/` folder
3. Download Patchouli and place in `mods/` folder
4. Download FPSCompress and place in `mods/` folder
5. Launch Minecraft

## Core Concept: PreFabs

**PreFabs** (Prefabricated Factories) are upgraded Compact Machine blocks that act as cross-dimensional conduits between the Overworld and your factory dimension.

### The Three-Block System

1. **PreFab Block** (Overworld) - Routes resources, controls state machine, no internal storage
2. **Importer Block** (CM dimension) - Input gate, actively pushes items into adjacent inventories
3. **Exporter Block** (CM dimension) - Output gate, actively pulls items from adjacent inventories

💡 **Best Practice**: Use intermediate chests between Importers/Exporters and machines for more reliable systems

### State Machine

```
BUILDING    → Player configures faces, places Importers/Exporters
SIMULATING  → PreFab measures actual production rates (CM chunks LOADED)
CACHED      → PreFab simulates production mathematically (CM chunks UNLOADED ← Performance)
HALTED      → Input starved or output blocked (CM stays unloaded, player fixes Overworld side)
```

## Example Setup

```
Overworld:
  [Coal Chest] → [PreFab NORTH face: PULL ITEMS → Importer #1]

CM Dimension (while SIMULATING):
  [Importer #1] → [Furnace] → [Exporter #1]
  (PreFab measures: 0.213 iron ingots/tick)

Overworld:
  [PreFab SOUTH face: PUSH ITEMS ← Exporter #1] → [Iron Chest]

During CACHED mode:
  - CM dimension chunks unload
  - PreFab accumulates fractional production (0.213/tick)
  - Every ~5 ticks, PreFab pushes 1 iron ingot to output chest
  - No chunk loading = Better server TPS.
```

## Current Status

**⚠️ This mod is in active development.**

Core systems implemented:
- ✅ Face configuration system with GUI
- ✅ Importer/Exporter blocks with UUID linking
- ✅ Resource transport (PULL/PUSH modes)
- ✅ Rate measurement with delta accounting
- ✅ Cached production with fractional math
- ✅ PreFab-as-Item portability system
- ✅ Enhanced GUI with live status and resource display
- ✅ HALTED state recovery with preserved rates
- ✅ Vanilla block support (chests, furnaces, hoppers)

In progress:
- 🔨 AE2/Refined Storage integration
- 🔨 Factory Controller block
- 🔨 Multiple PreFab management
- 🔨 Advanced filters
- 🔨 Anti-cheat validation
- 🔨 Polish and bug fixes

## License

This project is licensed under the MIT License.

## Credits

Built with [NeoForge](https://neoforged.net/) for Minecraft 1.21.1.

Integrates with [Compact Machines](https://www.curseforge.com/minecraft/mc-mods/compact-machines) by TheRealp455w0rd.
