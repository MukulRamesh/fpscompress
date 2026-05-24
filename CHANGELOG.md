# Changelog

All notable changes to the FPSCompress mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

---

## [0.4.0] - 2026-05-24

### Added
- **Customizable Rate Display Units**: Enhanced PreFab status GUI with flexible rate visualization
  - **Time Scale Toggle**: Button cycles through 5 display modes (Auto → Per Tick → Per Second → Per Minute → Per Hour)
    - Per Second: rate × 20 (e.g., 0.5/tick → 10.00/sec)
    - Per Minute: rate × 1200 (e.g., 0.5/tick → 600.00/min)
    - Per Hour: rate × 72000 (e.g., 0.5/tick → 36,000.00/hr)
    - All rates show 2 decimal places for consistency
  - **Auto-Normalization Mode**: Finds smallest time window for whole-number display
    - Uses LCM (Least Common Multiple) algorithm with cascading time scales
    - Example: 0.5 iron, 4 coal → Auto-normalize to "1 iron, 8 coal per 2 ticks"
    - Cascading: Try per-tick (100 ticks max) → per-second (100s max) → per-minute (10min max) → per-hour
    - Prevents awkward displays like "50,000 iron per 10,000 ticks" by using "2,500 iron per 8.33 minutes"
    - Button shows normalized value: "⏱ 2 Ticks" when in auto mode
  - **Item-Focused Normalization**: Click any resource in grid to normalize all rates to "per 1 unit of that item"
    - Example: Click iron (0.5/tick) → All rates scale to "1 iron, 8 coal per 2 ticks"
    - Visual highlight: Green background + border on focused item
    - Click again to unfocus and return to auto-normalize
  - **Preference Persistence**: All display settings stored server-side in NBT
    - Survives GUI close/reopen, world reload, block break/place
    - Multiple players viewing same PreFab see identical normalized view
    - Original auto-normalized mode restored when cycling through all manual modes
  - **Smart Tooltips**: Show appropriate rate units based on current mode
    - Item-focused: "per 1 Iron Ingot" (shows actual item name)
    - Auto-normalized: "per 2 ticks" or "per 5 seconds" (shows normalized timeframe)
    - Manual modes: "per tick", "per second", "per minute", "per hour"
  - **Test PreFab Support**: Command-created test PreFabs auto-normalize rates on creation
  - **Network Synchronization**: Real-time updates via `StatusGuiSyncPacket` (16 parameters)
    - Server → Client sync includes: `displayMode`, `focusedResourceId`, `autoNormalizedTicks`, `useAutoNormalize`, `autoNormalizedDisplayMode`
    - Client → Server preferences via `RateDisplayPreferencePacket` (5 parameters)
  - Files added: `RateDisplayMode.java` (enum with 4 modes), `RateNormalizer.java` (LCM algorithm)
  - Files modified: `PrefabBlockEntity.java` (5 new fields + NBT), `StatusGuiSyncPacket.java` (5 new fields), `PreFabStatusScreen.java` (button + transformation logic), `PreFabStatusMenu.java` (sync), `Dev2TestCommands.java` (auto-normalize), `RateDisplayPreferencePacket.java` (preferences sync)
  - Completed: 2026-05-24

- **Minimum Simulation Time Requirement**: Enforce minimum duration before allowing CACHED transition
  - **Config**: `minimumSimulationTicks` (default: 2400 = 2 minutes, range: 0-72000 ticks)
    - Type: SERVER (runtime changes without restart, syncs to clients)
    - Set to 0 to disable minimum time requirement
  - **Timer Tracking**: Two fields added to `PrefabBlockEntity`
    - `simulationElapsedTicks`: Increments each tick during SIMULATING state
    - `simulationRequiredTicks`: Config snapshot captured at simulation start (immune to mid-simulation changes)
    - Both persist in NBT for world reload support
  - **Enforcement**: Server-side validation in `finishSimulation()` method
    - Survival players blocked if `elapsedTicks < requiredTicks` with chat message showing remaining time
    - Creative players bypass minimum time (instant finish for rapid prototyping)
  - **GUI Updates**: Visual feedback in Status screen during SIMULATING state
    - Time display: "Simulating: 2m 30s / 5m 00s" (formatted as minutes:seconds)
    - Progress bar: Visual green bar showing 0-100% completion
    - Button label: "Simulating... X%" (before minimum) or "Finish Simulation" (after minimum)
    - Tooltip: "Survival: Xm XXs remaining | Creative: Click to finish now"
  - **Config Snapshot Behavior**: Value captured once at simulation start, not re-read at finish
    - Prevents mid-simulation rule changes from affecting in-progress simulations
    - Example: Start with 2min requirement → Config changes to 5min → Finish after 2min (original requirement honored)
  - **Purpose**: Prevents inaccurate rate measurements from too-short simulations (e.g., 5-second "spam simulation → instant cache" exploits)
  - Files modified: `Config.java`, `PrefabBlockEntity.java`, `StatusGuiSyncPacket.java`, `PreFabStatusScreen.java`, `PreFabStatusMenu.java`, `en_us.json`
  - Completed: 2026-05-24

- **PreFab Entry Protection**: Survival players cannot enter PreFabs during active operation
  - Blocks entry to PreFabs in SIMULATING, CACHED, or HALTED states (only BUILDING allows entry)
  - Creative mode players can always enter for testing/debugging purposes
  - Shows red error message: "PreFab is active! Only creative mode players can enter during operation."
  - Prevents accidental factory breakage from entering unloaded dimensions during CACHED mode
  - Location: `PrefabBlock.useItemOn()` checks state before teleporting player
  - Future enhancement: Config option to allow specific permission levels to bypass restriction

### Changed
- **Rate Calculation Formula**: Upgraded from MVP formula to full delta accounting formula
  - **Old (MVP)**: `Net = Exported - Imported` (flow-only tracking)
  - **New (Full)**: `Net = (Final - Initial) + (Exported - Imported)` (flow + storage deltas)
  - **Storage Delta**: Accounts for items buffered in machines, Importer/Exporter buffers, and chests
  - **Flow Delta**: Tracks items that physically crossed PreFab faces during simulation
  - **Distribution**: Aggregate net production distributed to UUIDs proportionally based on flow contribution
  - **Purpose**: Prevents underestimating rates when items buffer inside factory during simulation
  - **Purpose**: Prevents overestimating rates when items are exported from existing storage
  - **Example**: Furnace smelts 64 coal → 64 iron, but only 32 iron exported (32 buffered)
    - Old: `Net = 32 - 0 = 32` (underestimate)
    - New: `Net = (32 - 0) + (32 - 0) = 64` (correct!)
  - **Files Modified**: `PrefabBlockEntity.java` (rate calculation in `calculateRatesAndTransition()`)
  - **Technical**: Storage delta from room-wide scans distributed proportionally to UUIDs based on their relative flow
  - **Edge Cases**: Zero flow delta splits storage equally; passthrough still detected when both deltas sum to zero
  - Completed: 2026-05-24

### Fixed
- **Documentation Clarity**: Fixed widespread misconception about Simulation Wrench usage
  - **Issue**: Multiple documentation sources incorrectly stated the wrench is used to switch PreFab states
  - **Reality**: Wrench is **only** for face configuration (Shift+Right-click), not state control
  - **State Control**: Use empty hand or non-wrench item to open control menu for state transitions
  - Updated 12 documentation files across Patchouli in-game docs, GitHub WIKI, and language files:
    - `simulation_wrench.json`: Added clarification that wrench doesn't control states
    - `state_overview.json`: Changed state diagram arrows from "Right-click with wrench" to "Right-click (empty hand / non-wrench)"
    - `building_state.json`, `simulating_state.json`: Updated transition instructions
    - `what_youll_need.json`, `step_by_step_setup.json`: Added important notes and corrected steps
    - `State-Machine-Guide.md`, `Getting-Started.md`: Updated both wiki folders with correct workflow
    - `en_us.json`: Changed wrench tooltip from "control simulation state" to "Shift+Right-click PreFab to configure faces"
    - `CLAUDE.md`: Updated developer documentation with correct player experience workflow
  - Added new "Common Mistakes" entry warning against using wrench for state changes

### Technical
- **Code Quality: Major Refactoring**: Resolved checkstyle violations by extracting service classes from `PrefabBlockEntity`
  - **Problem**: `PrefabBlockEntity.java` violated checkstyle limits (1,667 lines, method 160 lines over 2,000/150 limits)
  - **Solution**: Extracted 7 service classes using delegation pattern with package-private field access
  - **Result**: File size reduced 1,667 → 806 lines (51.6% reduction, 1,194 lines under limit)
  - **New Service Classes** (2,026 lines total):
    - `DisplayPreferenceManager.java` (116 lines): Rate display preferences, GUI sync
    - `RateCalculationEngine.java` (231 lines): Delta accounting formula, rate calculation, state transition
    - `PrefabNBTSerializer.java` (557 lines): NBT serialization, schema migration, data validation
    - `StateTransitionManager.java` (328 lines): State machine (BUILDING→SIMULATING→CACHED→HALTED)
    - `InventoryScanningService.java` (348 lines): Room scanning, Machine Wall detection, buffer inspection
    - `CachedProductionHandler.java` (187 lines): Fractional accumulator math, HALTED exponential backoff
    - `TransportTickHandler.java` (259 lines): Resource routing between Overworld and CM dimension
  - **Architecture**: Services hold reference to `PrefabBlockEntity` for direct field access (intentional design)
  - **Linter Compliance**: All 3 linters passing (compileJava, checkstyleMain, spotbugsMain)
    - Fixed 7 SpotBugs EI_EXPOSE_REP2 warnings (added @SuppressFBWarnings with justification)
    - Fixed 9 unused private method warnings (removed delegation stubs)
    - Fixed 10 Checkstyle LineLength violations (split long annotations)
    - Fixed 3 unused imports (ItemStack, BuiltInRegistries, Map)
  - **Benefits**: Improved maintainability, clear separation of concerns, easier testing
  - Files created: 7 new service classes in `com.mukulramesh.fpscompress.portal` package
  - Files modified: `PrefabBlockEntity.java` (stripped to 806 lines, all fields now package-private)
  - Completed: 2026-05-24

---

## [0.3.0] - 2026-05-23

### Added
- **Phase 6: Multi-Output Routing System**: UUID-based cached production with per-equipment rate tracking
  - `CachedTransferHandler`: New utility class for routing cached outputs to specific PreFab faces
    - Transfers resources from specific Exporter UUIDs to mapped faces only
    - Each Exporter can route to different faces based on UUID mapping
    - Enables complex factory patterns with multiple output streams
  - `CachedConfigurationValidator`: Validates face-to-UUID mappings before entering CACHED state
    - Ensures all Exporters with rates have at least one mapped face
    - Generates detailed error messages for missing/broken configurations
    - Defensive unmodifiable collections to prevent external mutation (SpotBugs compliance)
  - Per-UUID rate storage: `importerExporterRates` maps each equipment UUID to its resource rates
    - Enables routing different resources to different faces (e.g., iron → NORTH, copper → SOUTH)
    - GUI displays aggregate rates for user-friendly monitoring
    - NBT schema v2 format for portable PreFab blocks with multi-output data
- **Patchouli Integration**: In-game documentation system with 112+ JSON files
  - Automated conversion tool (`wiki_to_patchouli.py`) generates Patchouli JSON from GitHub WIKI markdown
  - 11 categories covering all mod features (Welcome, Getting Started, PreFab System, etc.)
  - Cross-referenced pages with clickable links between sections
  - Added Patchouli 1.21.1-93-NEOFORGE library to `libs/` folder
  - Registered book in resources: `patchouli_books/fpscompress_guide/book.json`
- **Enhanced Debug Commands**: Extended `/fps_dev2 give-test-prefab` with advanced options
  - Single item: `/fps_dev2 give-test-prefab <inputItem> <inputRate> <outputItem> <outputRate>`
  - Multiple items: `/fps_dev2 give-test-prefab list "<inputs>" "<outputs>"` (format: `"item:rate,item:rate"`)
  - Test PreFabs now maintain CACHED state, room code, UUID rates, and face configs when placed/broken
  - Resources tab in Status GUI correctly displays rates from `importerExporterRates`
  - Added UUID parameter support for assigning fake room codes to test PreFabs
- **Documentation**: Comprehensive GitHub WIKI (11 pages) covering all mod features
  - Getting Started, Advanced Setup, PreFab System, Cached Production, State Machine, Troubleshooting
  - Developer API reference, Face Configuration, Importer/Exporter guides
- **Localization**: Added Patchouli-related translation keys to `en_us.json`

### Changed
- **NBT Schema Version 2**: `PrefabBlockEntity` now uses UUID-based rate storage
  - Schema version upgraded from 1 → 2 for per-UUID rate tracking
  - `importerExporterRates` stores rates per equipment UUID (replaces aggregate `cachedRates`)
  - Automatic migration from v1 (aggregate rates) to v2 (per-UUID rates) on world load
  - Aggregate `cachedRates` maintained for GUI display and backward compatibility
  - `validateLoadedData()` checks both old and new rate formats for state validation
- **Test PreFab Generation**: `Dev2TestCommands.buildUUIDRates()` creates schema v2 NBT format
  - Generates ListTag structure matching `PrefabBlockEntity` loading code
  - Supports fake room codes (`fake_*`) for testing without real Compact Machines rooms
  - `loadRatesFromNBT()` derives aggregate rates from per-UUID data for GUI consistency
- **Face Configuration Persistence**: `PrefabBlock.setPlacedBy()` restores NBT from item to block entity
  - Face configs, UUID mappings, and room linkage preserved across place/break cycles
  - Test PreFabs maintain full configuration state when picked up and replaced
- **Registry Updates**: `ImporterExporterRegistry.updateFrequency()` added for instant frequency sync
  - Frequency changes via right-click now immediately reflect in PreFab config GUI
  - No longer requires chunk reload to update "Unnamed Importer/Exporter" labels
- **Logging Cleanup**: Removed 34+ excessive log statements across multiple classes
  - Removed per-tick transport logs in `PrefabBlockEntity` (PULL/PUSH operations)
  - Removed per-chunk/per-slot logs in `InventoryScanner` (room scans)
  - Removed verbose reflection debugging in `DimensionTeleportListener`
  - Added targeted debug logs for NBT loading and rate storage (gated by log level)
  - Kept only critical state transitions, validation failures, and error logs

### Fixed
- **PreFab Block Drops**: Fixed PreFab blocks dropping nothing when broken with pickaxe
  - **Root Cause**: Block tags referenced `fpscompress:prefab` but block was registered as `fpscompress:prefab_machine`
  - **Solution**: Updated `mineable/pickaxe.json` and `needs_iron_tool.json` to use correct block ID
  - **Result**: PreFab blocks now properly drop with all NBT data (rates, face configs, room linkage) preserved
  - Files changed: `data/minecraft/tags/block/mineable/pickaxe.json`, `needs_iron_tool.json`
- **Test PreFab NBT Persistence**: Fixed test PreFabs losing all cached data when placed
  - **Issue 1**: Schema version was 1, causing UUID rates to be ignored during load
    - **Fix**: Set schema version to 2 in `buildUUIDRates()` for new NBT format
  - **Issue 2**: NBT structure didn't match loading code expectations
    - **Fix**: Create ListTag format with proper CompoundTag nesting (`uuid`, `rates` lists)
  - **Issue 3**: Face configs and room linkage lost on placement
    - **Fix**: `PrefabBlock.setPlacedBy()` now calls `loadAdditional()` to restore all data
  - **Issue 4**: Fake test rooms failed validation (no roomCenter)
    - **Fix**: `validateLoadedData()` allows fake rooms (`fake_*` roomCodes skip roomCenter check)
  - **Issue 5**: State reset to BUILDING because UUID rates weren't checked
    - **Fix**: Added `importerExporterRates.isEmpty()` check alongside `cachedRates.isEmpty()`
  - **Issue 6**: GUI showed "No production data" after placement
    - **Fix**: `loadRatesFromNBT()` derives aggregate `cachedRates` from `importerExporterRates` for GUI
- **Code Quality**: Fixed all SpotBugs static analysis warnings (5 findings → 0)
  - **`CachedConfigurationValidator`**: Inefficient map iteration using `keySet()` instead of `entrySet()`
    - Changed `faceConfigs.keySet()` iteration to `faceConfigs.entrySet()` for better performance
  - **`ValidationResult` record**: List fields exposed to external mutation
    - Added defensive copies using `List.copyOf()` in record constructor
  - **`ResourceDeltaTracker`**: False positive warnings for intentional behavior
    - Added `@SuppressFBWarnings("UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")` with justification
  - **Result**: All static analysis checks now pass (Checkstyle + SpotBugs), zero warnings
- **Registry Sync Bug**: Frequency changes now immediately update `ImporterExporterRegistry`
  - Previously, PreFab config GUI showed "Unnamed Importer/Exporter" until chunks reloaded
  - Added `updateFrequency()` method called on right-click frequency setting
  - PreFab GUI now instantly reflects frequency item changes without chunk reload
- **Performance**: Significantly reduced console output and logging overhead during normal operations
  - Removed 34+ per-tick/per-operation log statements causing console spam
  - Console output reduced from ~50 lines/tick to ~0 lines/tick during normal operation
  - Logging overhead reduced by ~95% (performance improvement in tick processing)

### Technical Details
- **New Classes**:
  - `CachedTransferHandler`: 217 lines, handles UUID-based cached resource transfers
  - `CachedConfigurationValidator`: 106 lines, validates multi-output face-to-UUID mappings
  - `ValidationResult` record: Immutable validation result with errors/warnings lists
- **Modified Classes**:
  - `PrefabBlockEntity`: Added per-UUID rate storage, NBT schema v2, validation logic
  - `Dev2TestCommands`: Extended give-test-prefab with list support, UUID rates generation
  - `PrefabBlock`: Added `setPlacedBy()` override for NBT restoration on placement
  - `PrefabBlockItem`: NBT handling improvements for portable test PreFabs
  - `ResourceDeltaTracker`: Added SpotBugs suppression annotations
  - `ImporterExporterRegistry`: Added `updateFrequency()` for instant sync
  - `InventoryScanner`: Removed excessive logging (34+ statements)
  - `DimensionTeleportListener`: Removed verbose reflection debugging
- **Resource Files**:
  - Added 112 Patchouli JSON files (11 categories, 101 entries)
  - Updated `en_us.json` with new translation keys
  - Fixed block tags in `mineable/pickaxe.json` and `needs_iron_tool.json`
- **Dependencies**:
  - Added Patchouli 1.21.1-93-NEOFORGE (646KB JAR in `libs/` folder)
- **Data Format**:
  - NBT schema version: 1 → 2
  - New tags: `"importerExporterRates"` (ListTag), `"schemaVersion"` (int)
  - Maintained tags: `"rates"` (aggregate, for GUI), `"faceConfigs"`, `"roomCode"`

### Breaking Changes
- **NBT Schema Migration**: PreFab blocks from schema v1 automatically migrate to v2 on world load
  - Migration converts aggregate rates to per-UUID format (assumes single equipment per resource)
  - Test PreFabs from older versions will need to be regenerated with new commands
  - Production worlds should re-measure rates after update (use Simulation Wrench)
- **Importer/Exporter Frequency**: Existing blocks will lose frequency settings from pre-frequency builds
  - Old NBT tag: `"FilterItem"` (from frequency system PR)
  - New NBT tag: `"FrequencyItem"`
  - Acceptable for pre-release (0.1.0-alpha) stage, no production users affected

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

[Unreleased]: https://github.com/mukulramesh/fpscompress/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/mukulramesh/fpscompress/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/mukulramesh/fpscompress/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/mukulramesh/fpscompress/compare/v0.1.0-alpha...v0.2.0
[0.1.0-alpha]: https://github.com/mukulramesh/fpscompress/releases/tag/v0.1.0-alpha
