# Changelog

All notable changes to the FPSCompress mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Frequency System**: Importer/Exporter blocks now use "frequency" terminology instead of "filter"
  - Right-click with any item to set the frequency (e.g., "Apple Importer", "Iron Exporter")
  - Visual frequency indicators render the frequency item on all 6 sides of the block
  - 3D item rendering with full brightness, similar to item frames
  - Placement restricted to Compact Machines dimension in survival mode (creative mode can place anywhere)
- **Localization**: Added frequency-related translation keys to `en_us.json`

### Changed
- **Terminology**: Renamed "filter" to "frequency" across the entire codebase
  - `filterItem` → `frequencyItem` (field names)
  - `getFilterItem()` → `getFrequencyItem()` (methods)
  - `FilterItem` → `FrequencyItem` (NBT tags)
- **Debug Messages**: Updated left-click debug output to show "Frequency:" instead of "Filter:"
- **Renderer**: FrequencyIndicatorRenderer uses `LightTexture.FULL_BRIGHT` for consistent visibility
- **Logging**: Removed 34 excessive log statements to eliminate log spam
  - Removed per-tick transport logs in PrefabBlockEntity (PULL/PUSH operations)
  - Removed per-chunk/per-slot logs in InventoryScanner (room scans)
  - Removed verbose reflection debugging in DimensionTeleportListener
  - Kept only critical state transitions and error logs

### Fixed
- **Registry Sync Bug**: Frequency changes now immediately update the ImporterExporterRegistry
  - Previously, the PreFab config GUI would show "Unnamed Importer/Exporter" until chunks reloaded
  - Now updates instantly when frequency is set via right-click
- **Performance**: Significantly reduced console output and logging overhead during normal operations

### Breaking Changes
- **NBT Format Change**: Existing Importer/Exporter blocks will lose their frequency settings when loading old saves
  - Old NBT tag: `"FilterItem"`
  - New NBT tag: `"FrequencyItem"`
  - This is acceptable for pre-release (0.1.0-alpha) stage

---

## [0.2.0] - 2026-05-16

### Added
- **Crafting Recipes**: Added recipes for all mod items using data generation
  - PreFab Upgrade Template: Gold ingots, copper ingots, and stone in cross pattern
  - Simulation Wrench: Copper ingots in wrench shape
  - Importer: Iron ingots, stone, copper ingot, and chest
  - Exporter: Iron ingots, stone, copper ingot, and chest (different pattern from Importer)
  - Shapeless conversion recipes: Importer ↔ Exporter (allows converting between types)
- **Recipe Data Generation**: ModRecipeProvider using ShapedRecipeBuilder/ShapelessRecipeBuilder
  - Recipes now generated via `./gradlew runData` instead of manual JSON files
  - Advancements automatically created for recipe unlocking
- **JEI Support**: Added Just Enough Items (JEI) dependency for recipe viewing in-game
  - Version 19.27.0.336 for Minecraft 1.21.1

### Changed
- **Build Configuration**: Fixed data generation task (clientData → data in build.gradle)
- **Version Management**: Bumped version from 0.1.0-alpha to 0.2.0

### Technical Details
- Deleted manual recipe JSON files (now auto-generated)
- Updated .gitignore to include JEI JAR in libs/ folder
- Registered datagen in FPSCompress main class via GatherDataEvent

---

## [0.1.0-alpha] - 2026-05-16

### Initial Alpha Release
- Importer/Exporter blocks for CM dimension resource gates
- PreFab block for factory compression (work in progress)
- UUID-based linking between PreFab faces and Importers/Exporters
- Room-based filtering system
- Basic registry tracking

[Unreleased]: https://github.com/mukulramesh/fpscompress/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/mukulramesh/fpscompress/compare/v0.1.0-alpha...v0.2.0
[0.1.0-alpha]: https://github.com/mukulramesh/fpscompress/releases/tag/v0.1.0-alpha
