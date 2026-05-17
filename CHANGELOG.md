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

### Fixed
- **Registry Sync Bug**: Frequency changes now immediately update the ImporterExporterRegistry
  - Previously, the PreFab config GUI would show "Unnamed Importer/Exporter" until chunks reloaded
  - Now updates instantly when frequency is set via right-click

### Breaking Changes
- **NBT Format Change**: Existing Importer/Exporter blocks will lose their frequency settings when loading old saves
  - Old NBT tag: `"FilterItem"`
  - New NBT tag: `"FrequencyItem"`
  - This is acceptable for pre-release (0.1.0-alpha) stage

---

## [0.1.0-alpha] - 2026-05-16

### Initial Alpha Release
- Importer/Exporter blocks for CM dimension resource gates
- PreFab block for factory compression (work in progress)
- UUID-based linking between PreFab faces and Importers/Exporters
- Room-based filtering system
- Basic registry tracking

[Unreleased]: https://github.com/mukulramesh/fpscompress/compare/v0.1.0-alpha...HEAD
[0.1.0-alpha]: https://github.com/mukulramesh/fpscompress/releases/tag/v0.1.0-alpha
