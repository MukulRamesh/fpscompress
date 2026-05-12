# FPSCompress

A NeoForge 1.21.11 Minecraft mod that enables **factory compression** through intelligent rate caching. Run your factories without chunk loading overhead by caching production rates and simulating them mathematically.

## What Does It Do?

FPSCompress allows you to build factories inside [Compact Machines](https://www.curseforge.com/minecraft/mc-mods/compact-machines) rooms and run them **virtually** without keeping those chunks loaded. The mod measures your factory's actual production rates during a calibration period, then uses fractional math to simulate production while the factory dimension stays unloaded.

**The Result**: Massive server performance gains for factory-heavy builds.

## Core Concept: PreFabs

**PreFabs** (Prefabricated Factories) are upgraded Compact Machine blocks that act as cross-dimensional conduits between the Overworld and your factory dimension.

### How It Works

1. **Build your factory** inside a Compact Machines room
2. **Place Importer/Exporter blocks** inside the CM room (input/output gates)
3. **Upgrade the CM block** with a TPS Upgrade item → becomes a PreFab
4. **Configure PreFab faces** to link to specific Importers/Exporters
5. **Connect chests/hoppers** to PreFab faces in the Overworld
6. **Start Simulation** mode to measure production rates (CM chunks loaded)
7. **Enter Cached** mode to run factory virtually (CM chunks **unloaded**)

### The Three-Block System

- **PreFab Block** (Overworld) - Routes resources, controls state machine, no internal storage
- **Importer Block** (CM dimension) - Input gate where resources enter factory
- **Exporter Block** (CM dimension) - Output gate where resources exit factory

### State Machine

```
BUILDING    → Player configures faces, places Importers/Exporters
SIMULATING  → PreFab measures actual production rates (CM chunks LOADED)
CACHED      → PreFab simulates production mathematically (CM chunks UNLOADED ← Performance!)
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
  - No chunk loading = Better server TPS!
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
- 🔨 Performance optimizations

## Building

Requires **Java 21** (mandatory for Minecraft 1.21.11).

```bash
cd fpscompress-template-1.21.11
./gradlew build
```

Run in development:
```bash
./gradlew runClient
```

## Documentation

- [START_HERE.md](NOTES/START_HERE.md) - Entry point for contributors
- [CLAUDE.md](NOTES/CLAUDE.md) - Complete project guide
- [ARCHITECTURE_CONDUIT.md](NOTES/ARCHITECTURE_CONDUIT.md) - Technical specification
- [TODO_NEW.md](NOTES/TODO_NEW.md) - Implementation roadmap

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

Built with [NeoForge](https://neoforged.net/) for Minecraft 1.21.11.

Integrates with [Compact Machines](https://www.curseforge.com/minecraft/mc-mods/compact-machines) by [TheRealp455w0rd](https://www.curseforge.com/members/therealp455w0rd).
