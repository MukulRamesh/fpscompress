# FPSCompress TODO List - Conduit Architecture

**Last Updated**: 2026-05-12 (Restructured: Uncompleted items at top)
**Architecture**: Conduit-based caching system (see ARCHITECTURE_CONDUIT.md)
**Current Phase**: MVP COMPLETE ✅ + Post-MVP Room Filtering ✅

**Primary Goal**: Cache factory input/output rates to run factories without chunk loading.

---

## 📋 Document Organization

This TODO list is organized with **uncompleted items at the top** for quick reference, followed by completed phases and documentation in the same order below. Jump to:
- [Pending Tasks](#-pending-tasks) - What's left to do
- [MVP Implementation](#-mvp-implementation-focus-here-first) - Completed phases
- [Post-MVP Features](#-post-mvp-features-implement-after-mvp-works) - Future enhancements
- [Testing Plan](#-testing-plan-mvp) - Test scenarios
- [Reference Documents](#-reference-documents) - Related docs

---

## 🔲 Pending Tasks

**Organization Note**: Tasks are roughly organized by priority and dependencies. Higher-priority or prerequisite tasks appear earlier in the list.

### Core System Improvements

#### UUID-Based Rate Storage & Face Reconfiguration
- [ ] Refactor rate storage from aggregate to per-UUID (BREAKING CHANGE)
  - **Root Problem**: Multi-output factories require sidedness control
  - **Example scenario** (why aggregate storage fails):
    - Factory produces: Iron (+5/tick) AND Copper (+3/tick) [multiple outputs]
    - Player wants: NORTH → iron chest, SOUTH → copper chest [sorted routing]
    - Current aggregate storage: `{iron: +5.0, copper: +3.0}` (no face association)
    - During CACHED: Both resources try ALL PUSH faces → first available chest wins
    - **Result**: Iron and copper MIX in same chest (no sidedness control!)

  - **Solution: Store rates per Importer/Exporter UUID**
    - Change: `Map<String, Double> cachedRates` → `Map<UUID, Map<String, Double>> importerExporterRates`
    - Rationale: Internal factory layout (which Importer/Exporter handles which resources) IS the routing specification
    - Example: `{ExporterUUID-A: {iron: +5.0}, ExporterUUID-B: {copper: +3.0}}`
    - Face config links: NORTH → ExporterUUID-A, SOUTH → ExporterUUID-B
    - **Benefit**: Player designs routing via internal factory layout (Importer/Exporter placement)

- [ ] Enable face reconfiguration without re-simulation
  - **Current limitation**: Aggregate rates can't distinguish which Exporter produced which resource
  - **New capability**: UUID-based rates preserve routing through internal factory design
  - **Example workflow**:
    1. Build factory: Importer-A (coal input) → Furnace → Exporter-B (iron output)
    2. Configure faces: NORTH → Importer-A, SOUTH → Exporter-B
    3. Simulate: System learns "UUID-A handles coal, UUID-B handles iron"
    4. Player rotates PreFab block (flips faces)
    5. Reconfigure: NORTH → Exporter-B, SOUTH → Importer-A (NO re-simulation!)
    6. **Result**: NORTH now outputs iron, SOUTH now inputs coal (routing preserved via UUIDs)

- [ ] Implementation subtasks:
  - [ ] Update `ResourceDeltaTracker` to track imports/exports per UUID
    - Add UUID parameter to `recordImport()` and `recordExport()`
    - Group deltas by UUID during tracking
  - [ ] Update `calculateRatesAndTransition()` to compute per-UUID rates
    - Loop through tracked UUIDs, calculate rates for each
    - Store in `importerExporterRates` map
  - [ ] Add validation: `validateCachedConfiguration()`
    - Check 1: All UUIDs with rates MUST have at least one face mapped
      - Failure → HALTED: "Unmapped outputs: iron_ingot (reconnect Exporter to face)"
    - Check 2: Warn if faces map to UUIDs with no rates (idle equipment, not fatal)
    - Check 3: Validate during `setFaceConfig()` to prevent unmapped UUIDs during CACHED
  - [ ] Update `transferCachedOutput()` to filter by UUID
    - Find source UUID for resource (which Exporter produced it)
    - Only try faces mapped to that UUID
    - Supports multi-face load balancing (multiple faces → same UUID)
  - [ ] Update `transferCachedInput()` to filter by UUID
    - Find target UUID for resource (which Importer needs it)
    - Only try faces mapped to that UUID
  - [ ] Differentiate error messages: Configuration vs. Runtime failures
    - "OUTPUT NOT ROUTED" (no face mapped to UUID) vs. "OUTPUT BLOCKED" (downstream full)
    - "INPUT NOT ROUTED" (no face mapped to UUID) vs. "INPUT STARVED" (upstream empty)
    - Configuration errors: No exponential backoff (requires player action)
    - Runtime errors: Exponential backoff (transient conditions)
  - [ ] Update NBT serialization:
    - Save: `Map<UUID, Map<String, Double>>` structure
    - Migration: Convert old `Map<String, Double>` to UUID-based (assign to first Exporter or clear)
    - Schema version bump to handle migration
  - [ ] Add GUI warnings for face reconfiguration during CACHED
    - "⚠ Changing faces may break cached production"
    - Validate in real-time which UUIDs would become unmapped
    - Highlight problem faces in red

- [ ] Edge cases to handle:
  - **Unmapped UUID**: UUID has rates but no face mapped → HALTED (configuration error)
  - **Idle UUID**: Face maps to UUID with no rates → Allowed (face does nothing)
  - **Multi-face → UUID**: Multiple faces to same UUID → Load balancing (first available wins)
  - **UUID mismatch**: Equipment broken/moved → HALTED (runtime error)
  - **Face reconfigured during CACHED**: Validate config remains valid → HALTED if unmapped UUIDs created

- [ ] Add unit tests:
  - Multi-output factory with sorted routing (iron to NORTH, copper to SOUTH)
  - Face reconfiguration without re-simulation (swap faces, verify routing preserved)
  - Unmapped UUID detection (configuration validation)
  - Error message differentiation (configuration vs. runtime failures)

#### CACHED State Entry Prevention (Survival Mode)
- [ ] Prevent survival players from entering PreFab during CACHED state
  - **Problem**: CM chunks unload during CACHED mode (performance optimization)
  - **Risk**: Player enters PreFab while CACHED → breaks Importer/Exporter → UUID links broken → transport fails → HALTED state
  - **Current behavior**: Nothing prevents entry (player can break factory setup while chunks unloaded)
- [ ] Implementation tasks:
  - [ ] Hook `PrefabBlock.useWithoutItem()` to check state before teleport
  - [ ] If state == CACHED and player is not creative → Cancel teleport, show chat message
  - [ ] Localize message: "fpscompress.prefab.cached_entry_blocked" → "Factory is running in CACHED mode. Reset to Building to enter."
  - [ ] Add tooltip to PreFab item explaining CACHED entry restriction
  - [ ] Test: Survival player right-clicks CACHED PreFab → Entry blocked
  - [ ] Test: Creative player right-clicks CACHED PreFab → Entry allowed (for debugging)

#### Minimum Simulation Time Requirement (Survival Mode)
- [ ] Enforce minimum simulation duration before allowing transition to CACHED state
  - **Goal**: Prevent inaccurate rate measurements from too-short simulations
  - **Problem**: Player starts simulation, waits 5 seconds, finishes → Rates calculated from tiny sample size → Unreliable
  - **Current behavior**: Player can finish simulation immediately (no time limit)
  - **Default**: 2 minutes (2400 ticks) minimum simulation time for survival players
  - **Creative bypass**: Creative players have no minimum (instant finish for testing)
- [ ] Implementation tasks:
  - [ ] Add config option in `FPSCompressConfig.java`:
    - [ ] `minimumSimulationTicks` (default: 2400 = 2 minutes)
    - [ ] Config type: SERVER (server owner controls, syncs to clients)
    - [ ] Range: 600 to 72000 (30 seconds to 1 hour)
    - [ ] Description: "Minimum ticks required in SIMULATING state before survival players can cache rates"
  - [ ] Track elapsed simulation time in `PreFabBlockEntity`:
    - [ ] Add `long simulationElapsedTicks` field (increments each tick in SIMULATING state)
    - [ ] Add `long simulationRequiredTicks` field (captured once at simulation start)
    - [ ] Reset `simulationElapsedTicks` to 0 when entering SIMULATING state
    - [ ] Capture `simulationRequiredTicks = config.minimumSimulationTicks` when entering SIMULATING state
    - [ ] Persist both fields in NBT (survives world reload mid-simulation)
  - [ ] Modify `startSimulation()` method:
    - [ ] Read current config value: `config.minimumSimulationTicks`
    - [ ] Store in `simulationRequiredTicks` field (snapshot at start)
    - [ ] This prevents mid-simulation config changes from affecting in-progress simulations
  - [ ] Modify `finishSimulation()` method:
    - [ ] Check if player is creative mode → Allow immediate finish
    - [ ] Check if `simulationElapsedTicks >= simulationRequiredTicks` → Allow finish (uses cached value, no config lookup)
    - [ ] Otherwise → Cancel transition, show chat message: "Simulation incomplete. Minimum time: X minutes (Y minutes remaining)"
  - [ ] Update Status GUI to show progress:
    - [ ] Display elapsed time: "Simulating: 1m 30s / 2m 00s"
    - [ ] Progress bar: ████████░░░░░░ 75%
    - [ ] Disable "Finish Simulation" button until minimum time reached (survival only)
    - [ ] Button tooltip: "Minimum simulation time not reached (30s remaining)"
  - [ ] Add localization strings:
    - [ ] "fpscompress.simulation.minimum_time_not_reached" → "Simulation incomplete. Minimum time: %d minutes (%d minutes remaining)"
    - [ ] "fpscompress.gui.simulation.elapsed_time" → "Simulating: %s / %s"
    - [ ] "fpscompress.gui.simulation.progress" → "Progress: %d%%"
    - [ ] "fpscompress.config.minimum_simulation_ticks" → "Minimum Simulation Time (ticks)"
  - [ ] Test cases:
    - [ ] Survival player tries to finish after 1 minute → Blocked, message shown
    - [ ] Survival player finishes after 2 minutes → Success
    - [ ] Creative player tries to finish immediately → Success (no minimum)
    - [ ] Player reloads world mid-simulation → Elapsed time AND required time persist
    - [ ] PreFab starts simulation with 2min requirement → Config changes to 5min mid-simulation → PreFab STILL only needs 2min (captured at start)
    - [ ] PreFab starts simulation with 5min requirement → Config changes to 2min mid-simulation → PreFab STILL needs 5min (captured at start)
    - [ ] New PreFab starts simulation after config change → Uses NEW config value (5min)

  **Why 2 minutes default**:
  - Enough time for factory to stabilize (hoppers fill, machines warm up)
  - Long enough to observe multiple production cycles
  - Not so long that it's tedious for testing
  - Prevents "spam simulation → instant cache" exploits

  **Config justification**:
  - Modpack authors may want longer times (5-10 min) for complex factories
  - Server owners may want shorter times (1 min) for casual gameplay
  - Creative players need no restriction for rapid prototyping

  **Config snapshot behavior**:
  - Config value captured ONCE when simulation starts (stored in `simulationRequiredTicks`)
  - In-progress simulations unaffected by config changes (prevents mid-simulation rule changes)
  - New simulations always use current config value
  - Performance: No config lookups during `finishSimulation()` (uses cached `simulationRequiredTicks`)
  - Fairness: Player knows exact time requirement when they start (doesn't change mid-simulation)
  - Example: Start simulation with 2min requirement → Config changes to 5min → Finish after 2min (original requirement honored)

  **Priority**: MEDIUM-HIGH (improves rate measurement accuracy, prevents exploit)
  **Estimated effort**: 3-5 days (config system, GUI updates, timer tracking)

#### Customizable Rate Units (Status GUI Enhancement)
- [ ] Add configurable rate display modes to PreFab status GUI
  - **Goal**: Allow players to view rates in different time scales and normalize to specific items
  - **Current behavior**: Rates always shown per tick (e.g., "0.5 iron/tick", "4 coal/tick")

  **Feature 1: Time Scale Toggle Button**
  - [ ] Add cycle button: "Per Tick" → "Per Second" → "Per Minute" → "Per Hour"
  - [ ] Convert rates based on 20 ticks/second, truncate to 2 decimal places:
    - Per Second: rate × 20 (e.g., 0.5/tick → 10.00/sec, 0.333.../tick → 6.67/sec)
    - Per Minute: rate × 1200 (e.g., 0.5/tick → 600.00/min, 0.0083/tick → 10.00/min)
    - Per Hour: rate × 72000 (e.g., 0.5/tick → 36000.00/hr, 0.333.../tick → 23998.40/hr)
  - [ ] Always display 2 decimal places for consistency (e.g., "10.00" not "10")
  - [ ] Persist selected time scale in BlockEntity NBT (survives GUI close/reopen)
  - [ ] Display time scale in GUI header: "Production Rates (Per Second)"

  **Feature 2: Normalize to Specific Item (Click to Focus)**
  - [ ] Make resource rate lines clickable in status GUI
  - [ ] On click: Normalize all rates to show "per 1 unit of clicked item"
  - [ ] Example: Original rates: 0.5 iron/tick, 4 coal/tick
    - Click iron → Show: "1 iron per 2 ticks, 8 coal per 2 ticks"
    - Click coal → Show: "0.125 iron per 0.25 ticks, 1 coal per 0.25 ticks"
  - [ ] Calculation: `normalizedTime = 1.0 / clickedItemRate`, then `normalizedRate = originalRate × normalizedTime`
  - [ ] Display focused item with highlight (green background or bold text)
  - [ ] Add "Reset" button to return to default display (per tick, no normalization)

  **Feature 3: Auto-Normalize to Whole Numbers (Cascading LCM)**
  - [ ] Find smallest time window where all rates are whole numbers
  - [ ] Algorithm: Calculate LCM of rate denominators with cascading time scales
    - Example: 0.5 iron (1/2), 4 coal (4/1) → LCM(2, 1) = 2 ticks → "1 iron, 8 coal per 2 ticks"
    - Example: 0.25 iron (1/4), 0.1 coal (1/10) → LCM(4, 10) = 20 ticks → "5 iron, 2 coal per 20 ticks"
  - [ ] Apply auto-normalization by default when GUI opens
  - [ ] **Cascading time scale strategy** (prevents showing awkwardly large tick counts):
    1. Try LCM within 100 ticks (5 seconds) → Display as ticks if found
    2. If LCM > 100 ticks: Convert rates to per-second, try LCM within 100 seconds
    3. If LCM > 100 seconds: Convert to per-minute, try LCM within 10 minutes (600 seconds)
    4. If LCM > 10 minutes: Convert to per-hour, try LCM within 1 hour
    5. If LCM > 1 hour: Default to per-hour rates, truncate to 2 decimal places
       - Example: 0.333... iron/tick → 23,998.4 iron/hr → Display "23,998.40 iron per hour"
       - Example: π/100 coal/tick → 2,261.95 coal/hr → Display "2,261.95 coal per hour"
  - [ ] **Why cascading**: Avoids showing "50,000 iron per 10,000 ticks" when "2,500 iron per 8.33 minutes" is clearer
  - [ ] **Why truncate at 2 decimals**: Balances precision vs. readability (72,000 ticks = 1 hour provides ~0.0014% precision)
  - [ ] Display normalized time in GUI: "Production Rates (Per 20 Ticks - Auto)" or "Production Rates (Per Hour - Rounded)"

  **Implementation Tasks**:
  - [ ] Add `RateDisplayMode` enum: `PER_TICK`, `PER_SECOND`, `PER_MINUTE`, `PER_HOUR`
  - [ ] Add `RateNormalization` class:
    - [ ] `calculateLCM(List<Double> rates)` - Find smallest whole-number time window
    - [ ] `normalizeToItem(Map<String, Double> rates, String focusedItem)` - Scale all rates to 1 unit of focused item
    - [ ] `convertTimeScale(double rate, RateDisplayMode mode)` - Convert tick rates to seconds/minutes/hours
  - [ ] Extend `PreFabStatusScreen.java`:
    - [ ] Add time scale cycle button (top-right corner)
    - [ ] Make resource rate lines clickable (add mouse click detection)
    - [ ] Add focused item highlight rendering
    - [ ] Add "Reset" button to clear normalization
    - [ ] Display normalized time window in header (e.g., "Per 20 Ticks")
  - [ ] Extend `PreFabBlockEntity.java`:
    - [ ] Add `RateDisplayMode currentDisplayMode` field (default: `PER_TICK`)
    - [ ] Add `String focusedItemId` field (null if no focus)
    - [ ] Add `int autoNormalizedTicks` field (calculated LCM, default: 1)
    - [ ] Persist display preferences in NBT
  - [ ] Update `StatusGuiSyncPacket.java`:
    - [ ] Add display mode, focused item, and normalized ticks to sync packet
  - [ ] Add localization strings:
    - [ ] "fpscompress.gui.rates.per_tick" → "Per Tick"
    - [ ] "fpscompress.gui.rates.per_second" → "Per Second"
    - [ ] "fpscompress.gui.rates.per_minute" → "Per Minute"
    - [ ] "fpscompress.gui.rates.per_hour" → "Per Hour"
    - [ ] "fpscompress.gui.rates.reset" → "Reset View"
    - [ ] "fpscompress.gui.rates.auto_normalized" → "Auto-Normalized"
  - [ ] Test cases:
    - [ ] Simple rates (0.5 iron, 4 coal) → Auto-normalize to 2 ticks
    - [ ] Complex rates (0.25 iron, 0.1 coal) → Auto-normalize to 20 ticks
    - [ ] Large LCM (0.333... iron, 0.1 coal) → Show fractional (LCM > 100)
    - [ ] Click item → All rates scale correctly
    - [ ] Cycle time scale → Rates convert accurately (20 ticks = 1 second)
    - [ ] Reset button → Returns to default view
    - [ ] Preferences persist across GUI close/reopen

  **UI Mockup**:
  ```
  ┌─────────────────────────────────────────────┐
  │ Production Rates (Per 2 Ticks - Auto)  [⏱] │ ← Time scale button
  ├─────────────────────────────────────────────┤
  │ ▶ 1.0 Iron Ingot                           │ ← Clickable (focused = bold/green)
  │   8.0 Coal                                  │ ← Clickable
  │   0.5 Diamond                               │ ← Clickable
  │                                             │
  │ [Reset View]                                │ ← Reset button
  └─────────────────────────────────────────────┘
  ```

  **Priority**: MEDIUM (significant UX improvement, helps players understand complex rates)
  **Estimated effort**: 1-2 weeks (GUI changes, math algorithms, NBT persistence)

#### Crafting Recipes
- [x] Add crafting recipe for PreFab Upgrade Template
- [x] Add crafting recipe for Simulation Wrench
- [x] Add crafting recipe for Importer block
- [x] Add crafting recipe for Exporter block
- [x] Ensure recipes are balanced (not too cheap/expensive)
- [x] Test recipes in survival mode

#### Documentation
- [ ] Add Patchouli support (in-game wiki/guidebook)
  - [ ] Add Patchouli dependency to build.gradle
  - [ ] Create book JSON (book name, landing text, model)
  - [ ] Create categories (Getting Started, PreFab System, Importer/Exporter, etc.)
  - [ ] Write entry pages for each major feature
  - [ ] Add crafting recipes to relevant pages
  - [ ] Test book in-game (verify all pages load, images render)
- [ ] Update GitHub wiki with current architecture
  - [ ] Document conduit-based system
  - [ ] Add visual diagrams (PreFab faces, Importer/Exporter linking)
  - [ ] Document state machine (BUILDING/SIMULATING/CACHED/HALTED)
  - [ ] Add setup tutorials (basic factory, frequency system)
  - [ ] Keep wiki in sync with Patchouli book content

### PreFab Blueprint System (Scanner & Printer)
**Status**: Not started
**Goal**: Scan PreFab factories to create blueprints, then print copies with resource costs

**Block: PreFab Scanner/Printer** (dual-mode operation)

**Mode 1: Scanning (PreFab → Blueprint)**
- Input: PreFab item (with cached rates and room linkage)
- Player clicks "Scan" button in GUI
- Scans CM room for:
  - All blocks (type, position, blockstate, NBT data)
  - All items in inventories (using existing InventoryScanner)
  - Room dimensions (sizeX, sizeY, sizeZ)
- Stores scan data in new item: **PreFab Blueprint**
- Blueprint contains:
  - Cached rates (copied from input PreFab)
  - Full block list (resource ID → count)
  - Full item list (resource ID → count)
  - Room dimensions
  - Optional: Structure NBT (for exact reconstruction)
- Output: PreFab Blueprint item (input PreFab consumed)

**Mode 2: Printing (Blueprint + Resources → PreFab)**
- Input: PreFab Blueprint item
- GUI displays required resources:
  - All blocks from scan (e.g., 200x Stone, 50x Glass, 10x Chest)
  - All items from scan (e.g., 64x Coal, 32x Iron Ore)
  - Constant costs (configurable): 1x PreFab Upgrade Template, 8x Diamond, etc.
- Player inserts resources into block's inventory (9x9 grid or larger)
- When all resources present, "Print" button becomes enabled
- Player clicks "Print" → Block consumes resources and produces:
  - **Output PreFab** (item, not placed block):
    - Has cached rates from blueprint
    - Has NO room linkage (roomCode = null, roomCenter = null)
    - State = CACHED (ready to use immediately)
    - Can be placed anywhere or inserted into Fractal Factory
- Blueprint is NOT consumed (reusable template)

**Implementation**:
- [ ] Create `PreFabScannerBlock` and `PreFabScannerBlockEntity`
- [ ] Add inventory slots:
  - [ ] 1 input slot (accepts PreFab OR Blueprint)
  - [ ] 1 output slot (outputs Blueprint OR PreFab)
  - [ ] 27+ resource slots (for printing materials)
- [ ] Create new item: `PreFabBlueprintItem`
  - [ ] Custom item with NBT data storage
  - [ ] Tooltip shows required resources summary
  - [ ] Tooltip shows cached rates preview
  - [ ] Optional: Fancy texture (rolled-up blueprint aesthetic)
- [ ] Scanning logic:
  - [ ] Detect PreFab in input slot
  - [ ] GUI shows "Scan" button (manual trigger to avoid lag)
  - [ ] On button click:
    - [ ] Read roomCode from PreFab
    - [ ] Load CM dimension and locate room
    - [ ] Perform async scan (blocks + items, reuse InventoryScanner)
    - [ ] Generate PreFab Blueprint item with scan data
    - [ ] Place blueprint in output slot
    - [ ] Consume input PreFab
    - [ ] Chat feedback: "Blueprint created: X blocks, Y items"
- [ ] Printing logic:
  - [ ] Detect Blueprint in input slot
  - [ ] Read required resources from blueprint NBT
  - [ ] Add constant costs from config (PreFab Upgrade Template, etc.)
  - [ ] GUI shows resource checklist:
    - [ ] Green checkmark if resource present in inventory
    - [ ] Red X if missing (shows needed vs. available)
    - [ ] Progress bar: "15/20 resources satisfied"
  - [ ] Enable "Print" button when all resources present
  - [ ] On button click:
    - [ ] Consume all required resources from inventory
    - [ ] Create new PreFab item with:
      - [ ] Cached rates from blueprint
      - [ ] No room linkage (roomCode = null)
      - [ ] State = CACHED
      - [ ] Schema version = 1
    - [ ] Place PreFab in output slot
    - [ ] Keep blueprint in input slot (reusable)
    - [ ] Chat feedback: "PreFab printed successfully"
- [ ] Configuration:
  - [ ] Add config option for constant costs (default: 1x PreFab Upgrade Template)
  - [ ] Add config option for resource multiplier (e.g., 0.5x = half resources needed)
  - [ ] Add config option to enable/disable blueprint reusability
- [ ] Visual/Audio feedback:
  - [ ] Scanning: Progress bar with spinning animation
  - [ ] Printing: Crafting animation with particle effects
  - [ ] Sound effects for scan/print completion
- [ ] NBT serialization:
  - [ ] Blueprint item stores all scan data
  - [ ] Scanner block stores current mode and progress
- [ ] Test cases:
  - [ ] Scan simple PreFab (5x5x5 room) → Creates blueprint
  - [ ] Print from blueprint with sufficient resources → Creates PreFab copy
  - [ ] Print with insufficient resources → Button disabled
  - [ ] Printed PreFab has identical rates to original
  - [ ] Printed PreFab has no room linkage (can't enter CM room)
  - [ ] Insert printed PreFab into Fractal Factory → Works normally
  - [ ] Break scanner block mid-scan → Cancels cleanly

**Use Cases**:
- **Mass production**: Scan one PreFab, print many copies for Fractal Factories
- **Trading**: Share blueprints with other players (blueprint is tradeable)
- **Backups**: Create blueprints of valuable factories before modifying
- **Resource sink**: Printing requires rebuilding the factory (balances automation)
- **Fractional Factory fuel**: Printed PreFabs can be "eaten" by Fractal Factories

**Integration with Fractal Factory**:
1. Build factory in CM room, configure PreFab
2. Run simulation, get CACHED rates
3. Scan PreFab → Creates blueprint
4. Print blueprint multiple times (consumes resources each time)
5. Insert printed PreFabs into Fractal Factory
6. Fractal Factory absorbs rates, produces autonomously

**Design Rationale**:
- **Why separate scan/print**: Scanning is expensive (async), printing is cheap (local inventory check)
- **Why consume PreFab on scan**: Prevents scanning same PreFab repeatedly (blueprint is the reusable copy)
- **Why require full resources**: Balances automation power (can't duplicate factories for free)
- **Why no room linkage on printed PreFabs**: Prevents CM room conflicts (multiple PreFabs can't link to same room)
- **Why CACHED state on printed PreFabs**: Ready to use immediately (no re-simulation needed)
- **Why reusable blueprints**: Encourages blueprint trading/sharing

**Estimated effort**: 2-3 weeks
**Priority**: MEDIUM-HIGH (enables Fractal Factory mass production, good progression system)

### PreFab Face Visualization
**Status**: Not started
**Goal**: Show which PreFab faces are configured to PULL/PUSH at a glance

**Implementation**:
- [ ] Keybind to toggle visualization mode (e.g., "V" key)
- [ ] Visual indicators for each face:
  - [ ] PULL faces: Blue glowing overlay/particles
  - [ ] PUSH faces: Green glowing overlay/particles
  - [ ] DISABLED faces: No indicator
  - [ ] Render as see-through (not occluded by blocks) using GL depth test tricks
- [ ] Show active transfer state:
  - [ ] Brighter glow when actively transferring resources
  - [ ] Dim glow when idle (waiting for resources)
  - [ ] Pulsing animation for HALTED state faces
- [ ] Performance:
  - [ ] Only render when player within 32 blocks of PreFab
  - [ ] Client-side only (no server packets needed)
  - [ ] Cache rendering state, update only on config change
- [ ] Test: PreFab with all 6 faces configured differently

**Use Cases**:
- Quick visual check of face configuration (no need to open GUI)
- Identify which side to connect chests/pipes to
- Debug misconfigured faces (forgot to set a face to PUSH)

**Estimated effort**: 3-5 days
**Priority**: MEDIUM (significant UX improvement)

### Fractal Factory Block
**Status**: Not started
**Goal**: Simple single-slot block that "eats" PreFabs and produces their outputs autonomously

**Concept**:
- 1-slot inventory (only accepts PreFab items)
- When PreFab inserted: Block "consumes" it and absorbs its cached rates
- **Accumulation**: Can eat multiple PreFabs - rates are summed together
- **Active state**: If all total rates >= 0, block is active and produces
- **Inactive state**: If any total rate < 0, block becomes inactive (net negative inputs)
- Runs absorbed production rates automatically (no chunk loading needed)
- Pushes outputs to adjacent inventories (like a hopper)

**Implementation**:
- [ ] Create `FractalFactoryBlock` and `FractalFactoryBlockEntity`
- [ ] Add 1-slot inventory (only accepts PreFab items)
- [ ] Rate accumulation system:
  - [ ] Store `Map<String, Double> totalRates` (sum of all absorbed PreFab rates)
  - [ ] Store `List<String> absorbedPreFabNames` (for GUI display)
- [ ] On PreFab insertion:
  - [ ] Read cached rates from PreFab item NBT
  - [ ] Add rates to `totalRates` (accumulate across multiple PreFabs)
  - [ ] Recalculate active state: Check if any `totalRates` entry is negative
  - [ ] If any negative: Set inactive state (display warning in GUI)
  - [ ] If all non-negative: Set active state
  - [ ] Consume PreFab item (add name to list)
  - [ ] Send chat feedback: "Absorbed [PreFab Name] (Total: X PreFabs, State: Active/Inactive)"
- [ ] Tick logic:
  - [ ] Only tick if in active state (all totalRates >= 0)
  - [ ] Use same fractional accumulator pattern as PreFabBlockEntity
  - [ ] Accumulate production for each total rate
  - [ ] When accumulator >= 1.0: Push whole items to adjacent inventories
  - [ ] If output blocked: Pause accumulation (don't lose progress)
- [ ] Visual indicator:
  - [ ] Inactive state (negative rates): Red glow, no particles
  - [ ] Idle state (active, no output): Dim green glow
  - [ ] Active production: Bright green glow with particle effects
  - [ ] Output blocked: Yellow pulsing glow
- [ ] Status GUI:
  - [ ] Show number of absorbed PreFabs
  - [ ] Show list of absorbed PreFab names (if naming implemented)
  - [ ] Show total rates per resource (sum across all PreFabs)
  - [ ] Highlight negative rates in red (causing inactive state)
  - [ ] Show accumulated fractional values (if active)
  - [ ] Show time running / total produced
- [ ] NBT serialization:
  - [ ] Save absorbed rates
  - [ ] Save accumulators
  - [ ] Save production statistics
- [ ] Textures:
  - [ ] Unique block texture (fractal/recursive theme)
  - [ ] Animated texture when producing
- [ ] Test cases:
  - [ ] Insert single PreFab with all positive rates → Active, produces
  - [ ] Insert PreFab with negative rates → Inactive (red glow)
  - [ ] Insert second PreFab that cancels out negatives → Becomes active
  - [ ] Example: PreFab A (coal: -1.0/tick), PreFab B (coal: +2.0/tick) → Total: +1.0/tick (active)
  - [ ] Output blocked → Yellow glow, pauses cleanly
  - [ ] Break block → Drops nothing (PreFabs consumed permanently)
  - [ ] Reset button in GUI → Clears all absorbed PreFabs and rates

**Use Cases**:
- **Combine multiple PreFabs**: Stack complementary factories (one produces coal, one consumes coal)
- **Self-sufficient loops**: PreFab A outputs iron, PreFab B needs iron input → Net positive output
- **Compact end-game production**: No need for Controller inventory management
- **"Fire and forget" factories**: Insert PreFabs, walk away, collect outputs
- **Balancing act**: Player must carefully balance inputs/outputs to stay active

**Design Rationale**:
- **Why allow negative rates**: Enables combining complementary PreFabs (producer + consumer)
- **Why inactive on net negative**: Can't run without external inputs (logically consistent)
- **Why consume PreFab**: Prevents item duplication exploits, encourages commitment
- **Why single-slot**: Keeps block simple (insert one at a time, rate accumulation happens automatically)
- **Why "Fractal"**: Recursive factory-within-factory absorption (multiple PreFabs → one block)

**Estimated effort**: 1 week
**Priority**: MEDIUM (simpler alternative to Factory Controller, good for early-game)

### Factory Controller Block
- [ ] Factory Controller block:
  - Inventory that accepts PreFab items
  - Each PreFab item in inventory runs its cached production
  - Controller manages chunk loading for all PreFabs
  - Controller exposes unified AE2 interface
- [ ] Benefits:
  - Portable factories (carry in inventory, ender chest, etc.)
  - Multiple factories in one Controller
  - Trade PreFabs with other players
  - Store inactive factories compactly

### External Mod Integrations
- [ ] AE2 integration (Applied Energistics 2)
- [ ] Refined Storage integration
- [ ] Mekanism pipes/conduits
- [ ] Create mod integration
- [ ] Any other mod-specific features

### HALTED Recovery Optimizations

**v1.1 - Inventory Change Listeners**:
- [ ] Implement `INotifyingItemHandler` listener registration
- [ ] Subscribe to inventory change events from adjacent blocks
- [ ] Zero overhead when inventories static, instant recovery on change
- [ ] Fallback to backoff for non-notifying inventories
- [ ] Expected gain: 50-90% reduction over backoff alone

**v1.1 - Smart Selective Checking**:
- [ ] Track which specific resource caused HALT (`haltedResourceId`)
- [ ] Only check relevant faces (PULL for inputs, PUSH for outputs)
- [ ] Reduces checks from N resources × 6 faces to 1 resource × 1-2 faces
- [ ] Combine with exponential backoff for best results

**v1.1 - Periodic Full Scan (Safety Net)**:
- [ ] Full scan every 12000 ticks (10 minutes) regardless of backoff
- [ ] Prevents edge cases where optimizations fail
- [ ] Negligible overhead, pure safety measure

**v1.2 - Player Proximity Detection**:
- [ ] Adjust max backoff based on player distance
- [ ] Fast recovery (20 ticks) when player within 16 blocks
- [ ] Slow recovery (200 ticks) when no players nearby
- [ ] 10x better performance for AFK scenarios

**v1.3 - Redstone Signal Trigger**:
- [ ] Apply redstone signal to PreFab → forces immediate retry
- [ ] Perfect for automation: item detector → redstone → PreFab
- [ ] Add debounce (max 1 trigger per second)
- [ ] Optional advanced feature, documented in tooltip

**Estimated effort**: 2-3 weeks total (1 week per version)
**Priority**: MEDIUM (MVP backoff sufficient, these are polish)
**Performance gain**: Current 99% → Post-MVP 99.9%+

### Per-Face Resource Filters
**Status**: Not started
**Goal**: Allow PreFab faces to filter specific items/fluids instead of accepting ALL

**Implementation**:
- [ ] Add `Set<ResourceLocation> allowedResources` to `FaceConfig.java`
- [ ] Add `Set<ResourceLocation> blockedResources` to `FaceConfig.java`
- [ ] Extend face config GUI:
  - [ ] Add "Configure Filter" button when face mode is PULL/PUSH
  - [ ] Item selection screen (show all registered items)
  - [ ] Toggle between whitelist mode (only allowed) and blacklist mode (block specific)
  - [ ] Display active filters in face config GUI (e.g., "PULL ITEMS: Iron, Gold, Diamond")
- [ ] Update transport logic:
  - [ ] Check whitelist/blacklist before transferring items
  - [ ] Skip transport if resource blocked by filter
  - [ ] Log filtered resources in debug mode
- [ ] NBT serialization for filter lists (save/load with face configs)
- [ ] Test: Whitelist (only iron/gold), Blacklist (no cobblestone)

**Use Cases**:
- Dedicated iron-only input face
- Prevent trash items from entering factory
- Separate fluid types across different faces

**Estimated effort**: 1 week
**Priority**: MEDIUM (nice-to-have, not required for basic caching)

### Face Priority System
**Status**: Not started
**Goal**: Control which PreFab face is checked first when multiple faces can handle a resource

**Problem**:
- Factory produces iron ingots
- NORTH face = PUSH to storage chest
- SOUTH face = PUSH to processing line
- Both can accept iron - which gets priority?

**Implementation**:
- [ ] Add `int priority` field to `FaceConfig.java` (default: 0, higher = checked first)
- [ ] Add priority spinner to face config GUI (+/- buttons, range 0-100)
- [ ] Modify `tickCachedProduction()` to sort faces by priority before attempting transfer
- [ ] Update status GUI to show face priority (e.g., "NORTH PUSH (Priority: 50)")
- [ ] NBT serialization for priority values
- [ ] Test: High-priority face fills first, low-priority face only if high blocked

**Use Cases**:
- Fill primary storage before overflow storage
- Prioritize critical processing over secondary outputs
- Control resource routing without external pipes

**Estimated effort**: 3-4 days
**Priority**: LOW (workaround: configure only one PUSH face per resource)

### Anti-Cheat Validation
**Status**: Not started
**Goal**: Detect hidden batteries/storage during SIMULATING phase

**Problem**:
- Player places chest inside CM factory (hidden storage)
- During SIMULATING: Factory "produces" from chest (not real production)
- PreFab caches inflated rate
- During CACHED: PreFab produces infinite resources (exploit)

**Implementation** (see VALIDATION_REDSTONE_PROTOCOL.md for full spec):
- [ ] Add bidirectional redstone protocol:
  - [ ] PreFab sends "shutdown request" signal to all Importers/Exporters
  - [ ] Importers/Exporters propagate redstone signal through factory
  - [ ] Machines gracefully finish current operations
  - [ ] Exporters send "shutdown complete" signal back to PreFab
- [ ] Snapshot initial state (scan all Importer/Exporter buffers)
- [ ] Wait for factory to fully drain (no items in-flight)
- [ ] Snapshot final state (scan buffers again)
- [ ] Calculate net production: `Net = (Final - Initial) + (Exported - Imported)`
- [ ] If `Net > 0` for inputs OR `Net < 0` for outputs → INVALID (hidden storage detected)
- [ ] Display warning in GUI: "Invalid simulation - hidden storage detected"

**Estimated effort**: 2-3 weeks
**Priority**: LOW (trust players for now, address exploits post-release if needed)

### Factory Controller Flow Graph (Requires Factory Controller Block)
**Status**: Not started (depends on Factory Controller implementation)
**Goal**: Interactive UI showing resource flow between PreFabs in a Controller

**Implementation**:
- [ ] **Prerequisites**:
  - [ ] Factory Controller block implemented
  - [ ] PreFab naming system implemented (not yet done)
- [ ] Graph UI screen:
  - [ ] Draggable canvas (click-and-drag to pan)
  - [ ] Zoomable (mouse wheel to zoom in/out, range: 0.5x to 2.0x)
  - [ ] Grid background for reference
- [ ] Node rendering:
  - [ ] PreFab nodes: Black square with PreFab name overlay (text)
  - [ ] Input nodes: Importer top texture (frequency item visible)
  - [ ] Output nodes: Exporter top texture (frequency item visible)
  - [ ] Node size scales with zoom level
- [ ] Edge rendering:
  - [ ] Lines connecting PreFabs to Input/Output nodes
  - [ ] Color-coded by resource type (iron = orange, coal = black, etc.)
  - [ ] Line thickness = transfer rate (thicker = more items/tick)
  - [ ] Animated flow direction (particles moving along edges)
- [ ] Interactivity:
  - [ ] Click node to highlight connected edges
  - [ ] Hover node to show tooltip (resource rates, state, etc.)
  - [ ] Right-click node to open PreFab status GUI
- [ ] Auto-layout algorithm:
  - [ ] Initial layout: Force-directed graph (nodes repel, edges attract)
  - [ ] Manual override: Drag nodes to custom positions (saved to NBT)
  - [ ] Reset button: Recalculate auto-layout
- [ ] Test: Controller with 5+ PreFabs, multiple resource types

**Use Cases**:
- Visualize complex factory networks (many PreFabs, shared resources)
- Identify bottlenecks (which PreFab is HALTED, which edge is slow)
- Tutorial/documentation tool (screenshot graph for guides)
- Design factory layouts before building in-game

**Estimated effort**: 2-3 weeks
**Priority**: LOW (Factory Controller not implemented yet, focus on that first)

### Statistics Tracking
**Status**: Partially implemented
**Goal**: Track cumulative statistics for each PreFab (total resources transported, uptime, etc.)

**Already Implemented** (see [PrefabBlockEntity.java:79](../fpscompress-template-1.21.11/src/main/java/com/mukulramesh/fpscompress/portal/PrefabBlockEntity.java#L79)):
- [x] `Map<String, Long> cachedProduction` - Tracks produced/consumed during CACHED state
- [x] Production counter increments in `tickCachedProduction()` ([line 1785](../fpscompress-template-1.21.11/src/main/java/com/mukulramesh/fpscompress/portal/PrefabBlockEntity.java#L1785))
- [x] NBT serialization for cachedProduction ([lines 595-606](../fpscompress-template-1.21.11/src/main/java/com/mukulramesh/fpscompress/portal/PrefabBlockEntity.java#L595-L606))
- [x] GUI access via `getCachedProduction()` method

**Not Yet Implemented**:
- [ ] Add lifetime statistics fields to `PreFabBlockEntity`:
  - [ ] `Map<String, Long> totalImported` (cumulative across all simulations)
  - [ ] `Map<String, Long> totalExported` (cumulative across all simulations)
  - [ ] `long ticksInCachedMode` (total time running cached)
  - [ ] `long ticksInSimulatingMode` (total time simulating)
  - [ ] `long ticksInHaltedMode` (total time halted)
  - [ ] `int haltCount` (number of times entered HALTED state)
  - [ ] `int simulationCount` (number of simulations completed)
- [ ] Update statistics during operation:
  - [ ] Track state time in `tick()` method (increment appropriate counter)
  - [ ] Track state transitions in `setCurrentState()` (increment haltCount, simulationCount)
  - [ ] Accumulate import/export in `handlePullFace()` and `handlePushFace()`
- [ ] Add "Statistics" tab to status GUI:
  - [ ] Display top 10 imported/exported resources (lifetime)
  - [ ] Show uptime breakdown (% time in each state)
  - [ ] Show efficiency metrics (halt rate, average cache duration)
  - [ ] Export button: Save stats to JSON file (for spreadsheet analysis)
- [ ] NBT serialization for lifetime statistics
- [ ] Optional: Reset button (clear all stats, prompt for confirmation)

**Use Cases**:
- Monitor factory efficiency over time
- Identify frequently halted PreFabs (optimize input/output)
- Compare production rates across multiple factories
- Generate reports for server admins (most-used PreFabs)

**Estimated effort**: 1 week
**Priority**: LOW (QoL feature, not gameplay-critical)

---

## 🎯 MVP Implementation (Focus Here First)

**MVP Scope**: Get ONE PreFab block to cache production rates correctly.

**Explicitly OUT of scope for MVP**:
- ❌ AE2 integration
- ❌ Factory Controller block
- ❌ Multiple PreFab management
- ❌ Refined Storage integration
- ❌ Any other mod integrations
- ❌ PreFab-as-item portability system
- ❌ Advanced filters (item/fluid whitelists)
- ❌ Anti-cheat validation (trust players for MVP)

**MVP Delivers**:
- ✅ One PreFab block in the world
- ✅ Configure its 6 faces (PULL/PUSH/DISABLED)
- ✅ Transport resources between Overworld and CM dimension
- ✅ Measure production rates (SIMULATING state)
- ✅ Run cached production using math (CACHED state, chunks unloaded)
- ✅ Vanilla compatibility only (hoppers, chests, furnaces)

---

## 🎯 MVP Implementation (Focus Here First)

### Phase 1: Face Configuration + Adjacent Block Detection (De-risk)
**Status**: ✅ **COMPLETE** (2026-05-02)
**Goal**: Prove PreFab can detect adjacent blocks and faces work correctly

**Why this first**: De-risk the core concept before building Importers/Exporters

**Tasks - Part A: Data Structures**:
- [x] Create `portal/FaceMode.java` enum (DISABLED, PULL, PUSH)
- [x] Create `portal/ResourceFilter.java` enum (ALL, ITEMS, FLUIDS, ENERGY)
- [x] Create `portal/FaceConfig.java` data class with NBT serialization
- [x] Modify `portal/PreFabBlockEntity.java`:
  - Added `Map<Direction, FaceConfig> faceConfigs` field
  - Initialize all 6 faces to DISABLED in constructor
  - Implemented NBT `saveAdditional()` and `loadAdditional()`
  - Added client sync methods (`getUpdatePacket()`, `getUpdateTag()`, `handleUpdateTag()`)

**Tasks - Part B: Adjacent Block Detection (Debug)**:
- [x] Add `debugAdjacentBlocks(Player player)` method to `PreFabBlockEntity`
  - Queries IItemHandler, IFluidHandler, IEnergyStorage capabilities
  - Displays results in chat with color-coded symbols
- [x] Modify `PrefabBlock.useWithoutItem()`:
  - Right-click PreFab without wrench → Calls `debugAdjacentBlocks(player)`
  - Checks for Simulation Wrench and returns PASS if held (allows wrench interaction)

**Tasks - Part C: Simple Face Config GUI**:
- [x] Create `gui/PreFabConfigScreen.java`:
  - 6 face selection buttons (NORTH/SOUTH/EAST/WEST/UP/DOWN)
  - Mode buttons (DISABLED/PULL/PUSH) with green highlighting for active selection
  - Filter buttons (ALL/ITEMS/FLUIDS/ENERGY) with green highlighting
  - Save button (also auto-saves on ESC/GUI close)
  - Defaults to clicked face when opened
- [x] Create `gui/PreFabConfigMenu.java`:
  - Server-side container menu
  - Loads configs from BlockEntity on open
  - Creates defensive copies to prevent direct BlockEntity modification
- [x] Create `network/FaceConfigPacket.java`:
  - Client → Server sync packet
  - Uses CustomPacketPayload with StreamCodec
  - Defensive copying to prevent internal representation exposure (spotbugs compliance)
- [x] Modify `portal/SimulationWrenchItem.java`:
  - Right-click → Opens face config GUI (sends BlockPos + Direction to client)
  - Shift+right-click → Breaks PreFab block and drops as item
  - Custom buffer writer for network sync (`buf.writeBlockPos()`, `buf.writeByte()`)
- [x] Register client-side screen in `FPSCompressClient.java`
- [x] Register network packet in `FPSCompress.java`
- [x] Add `"id": "fpscompress:prefab"` to BlockEntity NBT for item serialization
- [x] Test: Configure faces, close/reopen GUI → Configs persist ✓
- [x] Test: Break PreFab → Drops with NBT data ✓
- [x] Test: Quit to menu → No crashes ✓

**De-risk Validation**:
- ✅ Can detect adjacent chests/furnaces/hoppers
- ✅ Can query their capabilities (Items/Fluids/Energy)
- ✅ Face configs save/load correctly (NBT persistence)
- ✅ Face configs sync client ↔ server correctly (BlockEntity sync)
- ✅ GUI opens and works (network packet properly formatted)
- ✅ Auto-save on ESC implemented
- ✅ All code quality checks passing (checkstyle, spotbugs)

**Bugs Fixed**:
- Fixed crash on save: BlockEntity NBT missing "id" field
- Fixed wrench not opening GUI: Block interaction consuming event before item
- Fixed network protocol error: Menu constructor expected BlockPos + Direction, only received BlockPos
- Fixed configs not persisting: Client BlockEntity not syncing from server
- Added `getUpdatePacket()`, `getUpdateTag()`, `handleUpdateTag()` for proper client sync

**Result**: ✅ Core concept validated - proceeding to Phase 2 (Importers/Exporters)

---

### Phase 2: Importer/Exporter Blocks
**Status**: ✅ **COMPLETE** (2026-05-02)
**Goal**: Create input/output gate blocks for CM dimension

**Prerequisites**: Phase 1 complete (face config working, adjacent detection proven)

**Tasks**:
- [x] Create `portal/ImporterBlock.java` and `ImporterBlockEntity.java`:
  - Acts as input gate in CM dimension
  - Right-click with item to set filter → "Apple Importer" display name
  - Has UUID for identification (unique per block)
  - Internal buffer (9 slots for passthrough)
  - Debug display shows UUID, filter, buffer contents
- [x] Create `portal/ExporterBlock.java` and `ExporterBlockEntity.java`:
  - Acts as output gate in CM dimension
  - Actively pulls from adjacent machines every tick
  - Has UUID for identification
  - Internal buffer (9 slots for passthrough)
  - Debug display shows UUID, filter, buffer, adjacent capabilities
- [x] Create `portal/ImporterExporterRegistry.java`:
  - Global registry tracking all Importers/Exporters
  - Caches display names to avoid chunk loading issues
  - Thread-safe ConcurrentHashMap implementation
- [x] Register blocks in `FPSCompress.java`:
  - Add IMPORTER_BLOCK and EXPORTER_BLOCK to DeferredRegister
  - Add IMPORTER_BE and EXPORTER_BE BlockEntity types
  - Add IMPORTER_ITEM and EXPORTER_ITEM to creative tab
- [x] Test: Place Importer/Exporter in CM dimension, verify UUID generation ✓
- [x] Test: Place chest next to Exporter, verify active pulling works ✓
- [x] Test: Right-click Importer with apple, verify "Apple Importer" display ✓
- [x] Add `findImporterByUUID(UUID)` and `findExporterByUUID(UUID)` to PreFabBlockEntity
  - Implemented with O(1) caching for repeated lookups
- [x] Update Phase 1 GUI to include Importer/Exporter linking:
  - Link button cycles through available gates
  - Shows display names instead of UUIDs/coordinates
  - Client/server sync via packet buffer
  - Cross-dimensional visibility working (registry caching)

**Bugs Fixed**:
- Fixed UUID duplication from creative mode middle-click
- Fixed empty dropdown (client couldn't see registry)
- Fixed chunk loading issues (display names now cached)
- Fixed hardcoded stack size in Exporter pulling logic

**Result**: ✅ Complete three-block system working - PreFab + Importer + Exporter with UUID linking

---

### Phase 3: Basic Transport Logic
**Status**: ✅ **COMPLETE** (2026-05-02)
**Goal**: Move resources between Overworld and CM dimension via Importer/Exporter gates

**Prerequisites**: Phase 2 complete (Importers/Exporters working, UUID linking functional)

**Implementation Strategy**:
- PULL mode: Overworld chest → PreFab → find Importer by UUID → insert to Importer buffer
- PUSH mode: find Exporter by UUID → extract from Exporter buffer → PreFab → Overworld chest
- Exporters actively pull from adjacent machines (already implemented in Phase 2)
- Importers actively push to adjacent machines (implemented in Phase 3)

**Tasks**:
- [x] Add ticker to `PrefabBlock.java`: Register server-side BlockEntityTicker
- [x] Implement `tick()` in `PrefabBlockEntity.java`: Process all configured faces
- [x] Implement PULL logic: Overworld → Importer
- [x] Implement PUSH logic: Exporter → Overworld
- [x] Add `getCMLevel()` helper method to PrefabBlockEntity
- [x] Add `insertItem()` to ExporterBlockEntity (for remainder handling)
- [x] Add `pushToAdjacentMachines()` to ImporterBlockEntity
- [x] Add ticker to ImporterBlock for active pushing
- [x] Test PULL mode: Overworld chest → Importer buffer ✓
- [x] Test PUSH mode: Exporter buffer → Overworld chest ✓
- [x] Test roundtrip: Full item flow working ✓
- [x] Fix registry persistence bugs (moved to Block.onRemove())

**Bugs Fixed**:
- Registry unregistering on chunk unload (moved to Block.onRemove())
- Infinite loop from getBlockState() loading chunks
- Importer not pushing to adjacent machines (added active pushing)
- ExporterBlockEntity missing public insertItem() method

**Result**: ✅ Complete item flow working:
1. PreFab PULL: Overworld chest → Importer buffer
2. Importer PUSH: Buffer → Adjacent machine (furnace, etc.)
3. Exporter PULL: Adjacent machine → Exporter buffer
4. PreFab PUSH: Exporter buffer → Overworld chest

**Known Limitations** (will address in Phase 4-5):
- CM chunks must stay loaded for transport to work
- No rate measurement yet
- No cached production yet
- Limit items moved per tick (64 max for MVP)

---

### Phase 4: Rate Measurement (SIMULATING State)
**Status**: ✅ **COMPLETE** (2026-05-03)
**Goal**: Record actual transport rates while CM chunks are loaded

**Implementation Approach**: Use delta accounting (see VALIDATION_DELTA_ACCOUNTING.md)
- Track four quantities: Imported, Exported, Initial State, Final State
- Calculate net production: `Net = (Final - Initial) + (Exported - Imported)`
- Positive = produced, Negative = consumed, Zero = passthrough

**Tasks**:
- [x] Create `portal/ResourceDeltaTracker.java`:
  - Track totalImported, totalExported per resource type (MVP simplified - no inventory scanning)
  - Methods: `recordImport()`, `recordExport()`, `calculateNet()`, `getAllTrackedResources()`
  - NBT serialization for crash recovery
- [x] Add delta tracker to PreFabBlockEntity with timing fields:
  - `ResourceDeltaTracker deltaTracker`
  - `long simulationStartTick` (when SIMULATING started)
  - `long simulationEndTick` (when SIMULATING ended)
  - `long cachedStateStartTick` (when CACHED started)
  - `Map<String, Long> cachedProduction` (accumulated during CACHED)
  - `String lastSimulationResult` (GUI display of last result)
- [x] Restrict transport to SIMULATING state only (keep items visible in Overworld until simulation starts)
- [x] Hook delta tracking into transport logic:
  - Call `deltaTracker.recordImport()` after successful PULL transport
  - Call `deltaTracker.recordExport()` after successful PUSH transport
- [x] Implement state transition methods in PreFabBlockEntity:
  - `startSimulation()`: BUILDING → SIMULATING (loads CM chunks, resets tracker)
  - `finishSimulation()`: SIMULATING → CACHED/HALTED/BUILDING (calculates rates, unloads chunks)
  - `resetToBuilding()`: CACHED → BUILDING (clears rates, keeps chunks UNLOADED)
  - `resumeSimulation()`: HALTED → SIMULATING (resume measurement)
- [x] Calculate rates using MVP formula: `Net = Exported - Imported`
  - Positive = Factory produced (output)
  - Negative = Factory consumed (input)
  - Zero = Passthrough (no production/consumption)
- [x] Handle edge cases:
  - No activity (no imports AND no exports) → HALTED state
  - Passthrough (imports = exports, net = 0) → BUILDING state (reset and reconfigure)
  - Production detected (net ≠ 0) → CACHED state (success)
- [x] Create Status GUI system:
  - `gui/PreFabStatusScreen.java` - Client-side GUI with live updates
  - `gui/PreFabStatusMenu.java` - Server-side container menu
  - `network/StatusGuiSyncPacket.java` - Server → Client sync every tick (9 fields)
  - `network/SimulationControlPacket.java` - Client → Server state transition trigger
- [x] Implement Status GUI features:
  - Right-click PreFab without wrench → Opens status/control GUI
  - Live display during SIMULATING: Elapsed ticks, imported/exported counts (all players)
  - Live display during CACHED (creative): Simulation Time, Cached Ticks, production counts, rates
  - Live display during CACHED (survival): Rates only (no timing/count info)
  - Localized item names using BuiltInRegistries
  - Comprehensive tooltips explaining each field
  - Last simulation result display (Success/Passthrough/No activity)
- [x] Update face config GUI:
  - Remove Mode and Filter buttons (moved to linking phase)
  - Keep face selection + link button + save only
- [x] NBT serialization for all new fields (survives world reload)
- [x] All linters passing (checkstyle, spotbugs, compileJava)

---

### Phase 5: Cached Production (Fractional Math)
**Status**: ✅ **COMPLETE** (2026-05-03)
**Goal**: Simulate production using cached rates without loading CM chunks

**Tasks**:
- [x] Add fractional accumulator field: `Map<String, Double> itemAccumulators`
- [x] Modify `tick()` to handle CACHED state separately
- [x] Implement `tickCachedProduction()` method:
  - Accumulate fractional rates: `accum += rate`
  - When `|accum| >= 1.0`: Extract whole units and transfer
  - Handle positive rates (outputs) and negative rates (inputs)
- [x] Implement `transferCachedOutput()`:
  - Find PUSH faces that can accept the resource
  - Insert into Overworld adjacent blocks via IItemHandler
  - Track production counts for GUI display
  - Return false if output blocked (triggers HALTED)
- [x] Implement `transferCachedInput()`:
  - Find PULL faces that can provide the resource
  - Extract from Overworld adjacent blocks via IItemHandler
  - Return false if input starved (triggers HALTED)
- [x] Implement cache breaking:
  - If transfer fails → Put items back in accumulator (don't lose progress)
  - Enter HALTED state with message: "Cache broke - check inputs/outputs"
  - **CM chunks stay UNLOADED** (no setRoomChunkState call)
- [x] NBT serialization for itemAccumulators (survives world reload)
- [x] Clear accumulators in `resetToBuilding()`
- [x] All linters passing (checkstyle, spotbugs, compileJava)

**Result**: ✅ Core caching system working - factories run virtually without CM chunks loaded!

**Post-MVP Enhancement** (2026-05-08): ✅ Importer/Exporter Buffer Scanning
- [x] Added `scanImporterExporterBuffers()` method to scan buffer contents
- [x] Added `scanImporterBuffer()` to read Importer's 9-slot inventory
- [x] Added `scanExporterBuffer()` to read Exporter's 9-slot inventory
- [x] Modified `startSimulation()` to include buffer contents in initial scan
- [x] Modified `finishSimulation()` to include buffer contents in final scan
- **Why**: Prevents items sitting in buffers from inflating rate calculations
- **Example**: 64 iron in Exporter buffer during BUILDING → now counted in initial state, not as production

**Result**: ✅ Core caching system working - factories run virtually without CM chunks loaded!

---

## 📋 Completed MVP Phases

### Phase 6: Simulation Wrench Control
**Status**: ✅ **COMPLETE** (Implemented in Phase 4)
**Goal**: Let player control state transitions

**Completed in Phase 4**:
- [x] Created Status GUI with control button (replaces wrench for state transitions)
- [x] Implemented `SimulationControlPacket` for Client → Server state control
- [x] State transition methods in PreFabBlockEntity:
  - `startSimulation()`: BUILDING → SIMULATING (loads CM chunks, resets tracker)
  - `finishSimulation()`: SIMULATING → CACHED/HALTED/BUILDING (calculates rates, unloads chunks)
  - `resetToBuilding()`: CACHED → BUILDING (clears rates, keeps chunks unloaded)
  - `resumeSimulation()`: HALTED → SIMULATING (resumes measurement)
- [x] Control button changes based on state:
  - BUILDING: "Start Simulation" (green)
  - SIMULATING: "Finish Simulation" (yellow)
  - CACHED: "Reset to Building" (red)
  - HALTED: "Resume Simulation" (orange)
- [x] Chat feedback messages for each transition
- [x] Wrench kept for face configuration GUI only (Shift+Right-click)

**Note**: This phase merged with Phase 4 (Status GUI implementation)

---

### Phase 7: Enhanced Face Configuration GUI
**Status**: ✅ **COMPLETE** (Phase 1)
**Goal**: Let player configure faces visually

**Completed in Phase 1**:
- [x] Created `gui/PreFabConfigScreen.java` - Client-side GUI
- [x] Created `gui/PreFabConfigMenu.java` - Server-side container
- [x] Created `network/FaceConfigPacket.java` - Client → Server sync
- [x] GUI with 6 face buttons (North/South/East/West/Up/Down)
- [x] Mode buttons: [DISABLED] [PULL] [PUSH] with green highlighting
- [x] Filter buttons: [ALL] [ITEMS] [FLUIDS] [ENERGY] with green highlighting
- [x] Link button: Cycles through available Importers/Exporters
- [x] Trigger: Shift+Right-click PreFab with Simulation Wrench
- [x] Auto-save on ESC/close

**Note**: This phase was implemented early (Phase 1) to de-risk the GUI system.

---

### Phase 8: Dynamic Capabilities (Optional for MVP)
**Status**: Deferred - Not needed for MVP
**Goal**: Expose IItemHandler capabilities on PreFab faces

**Why deferred**:
- PreFab already uses active transport (tick-based pushing/pulling)
- Hoppers and pipes can interact with Importers/Exporters directly (Phase 9)
- Capability exposure on PreFab faces adds complexity without immediate benefit
- Can be added post-MVP if players want hopper integration on PreFab itself

**If implemented later**:
- Register capabilities with context-aware logic (per-face)
- Create FaceItemHandler that respects PULL/PUSH/DISABLED modes
- Similar for FaceFluidHandler and FaceEnergyStorage

---

## 📋 Post-MVP Features (Implement After MVP Works)

**All of these are explicitly OUT OF SCOPE for MVP. Only implement after core caching works perfectly.**

### 1. PreFab-as-Item System (Major Feature - Post-MVP)
**Status**: ✅ **FOUNDATION COMPLETE** (2026-05-08)
**Vision**: PreFabs as portable items that store complete factory state

**Phase 1: Item-Centric Data Storage** ✅ COMPLETE:
- [x] Refactor PreFabBlockEntity data to be item-centric:
  - [x] Added schema versioning (`schemaVersion = 1`) for future migrations
  - [x] Store face configs in item NBT (always persist)
  - [x] Store cached rates in item NBT (only if CACHED state)
  - [x] Store room linkage in item NBT (always persist)
  - [x] Store accumulators in item NBT (only if CACHED state)
- [x] When PreFab block breaks → All data goes into item (via existing `getDrops()`)
- [x] When PreFab item placed → Data loads from item into BlockEntity
- [x] Added migration rules for state transitions:
  - SIMULATING → BUILDING (partial measurement invalid after chunk unload)
  - HALTED → BUILDING (location-specific condition no longer applies)
  - CACHED → preserved (portable production data!)
- [x] Added validation for corrupt/inconsistent NBT data
- [x] Reduced PreFab hardness from 5.0 to 3.75 (25% faster to break)

**Phase 2: Factory Controller Block** (NOT YET IMPLEMENTED):
- [ ] Factory Controller block:
  - Inventory that accepts PreFab items
  - Each PreFab item in inventory runs its cached production
  - Controller manages chunk loading for all PreFabs
  - Controller exposes unified AE2 interface
- [ ] Benefits:
  - Portable factories (carry in inventory, ender chest, etc.)
  - Multiple factories in one Controller
  - Trade PreFabs with other players
  - Store inactive factories compactly

**Why Post-MVP**:
- MVP focuses on getting caching to work for ONE PreFab block
- Item-based system adds complexity (item ticking, Controller inventory logic)
- Can validate caching math works first, then add portability

### 2. Room-Based Filtering
**Status**: ✅ **COMPLETE** (2026-05-11)
**See**: [ROOM_FILTERING.md](ROOM_FILTERING.md) for complete implementation plan

**Problem**: PreFab GUI shows ALL Importers/Exporters across all CM rooms, causing clutter in large factories.

**Solution**: Filter Importers/Exporters by room using player context stack (FILO):
- Track which CM room each player is in using per-player stack
- When player enters CM room → push roomCode onto stack
- When player places Importer/Exporter → peek stack, store roomCode in block
- PreFab only shows Importers/Exporters in its linked room

**Actual effort**: 9 hours (5 phases)
**Complexity**: MEDIUM
**Priority**: HIGH (improves UX significantly for multi-room factories)

**Implementation**:
- [x] Create `PlayerRoomContext.java` registry (FILO stack per player UUID)
- [x] Hook teleportation events to push/pop room codes
- [x] Store `roomCode` field in Importer/ExporterBlockEntity
- [x] Update `ImporterExporterRegistry.Entry` to include roomCode
- [x] **Added O(1) secondary index** for room-based filtering (performance optimization)
- [x] Filter GUI dropdown by PreFab's linked room
- [x] Handle edge cases (disconnects, nested PreFabs, /tp commands)
- [x] Add debug commands (`/fpscompress room stack`, `/fpscompress room clear`)
- [x] Display "(Legacy)" suffix for blocks without roomCode

**Performance**: O(1) filtering via HashMap secondary index - scales to thousands of gates with no degradation

### 3. Completed Polish & UX
**Status**: ✅ **COMPLETE** (2026-05-12)
- [x] Add status display (Right-click PreFab without wrench):
  - Show current state (BUILDING/SIMULATING/CACHED/HALTED)
  - Show configured faces and their modes
  - Show current rates (items/tick, fluids/tick, FE/tick)
  - Show accumulated fractional values during CACHED mode
- [x] Add PreFab item tooltip:
  - Show state
  - Show room code
  - Show number of configured faces
- [x] Create texture for PreFab block (replaced purple/black checkerboard with custom textures)
  - Added [prefab_front.png](../fpscompress-template-1.21.11/src/main/resources/assets/fpscompress/textures/block/prefab_front.png)
  - Added [prefab_side.png](../fpscompress-template-1.21.11/src/main/resources/assets/fpscompress/textures/block/prefab_side.png)
  - Added [prefab_top.png](../fpscompress-template-1.21.11/src/main/resources/assets/fpscompress/textures/block/prefab_top.png)
- [x] Add localization entries ([en_us.json](../fpscompress-template-1.21.11/src/main/resources/assets/fpscompress/lang/en_us.json))
  - Block and item names
  - Tooltips for items
  - UI messages (upgrade installed, simulation states, etc.)
  - State names (Building, Simulating, Cached, Halted)

### 4. Importer/Exporter Frequency System
**Status**: ✅ **COMPLETE** (2026-05-16)
**Goal**: Improved terminology and visual identification system for Importer/Exporter blocks

**Terminology Refactoring**:
- [x] Rename "filter" to "frequency" across codebase:
  - [x] Update `ImporterBlockEntity` and `ExporterBlockEntity` field names
  - [x] Update GUI labels and tooltips
  - [x] Update localization entries in `en_us.json`
  - [x] Update documentation (CLAUDE.md, IMPORTER_EXPORTER_SYSTEM.md, etc.)
  - [x] Update NBT tags: `FilterItem` → `FrequencyItem`

**Visual Frequency Indicator**:
- [x] Render frequency item on all 6 sides of Importer/Exporter blocks
- [x] Style: 3D item rendering similar to item frames with full brightness
- [x] Use block entity renderer (FrequencyIndicatorRenderer)
- [x] Update when frequency item changes (right-click with new item)
- [x] Client-server synchronization for instant updates
- [x] Test: Verify visibility from all angles in-game

**Additional Improvements**:
- [x] Fixed registry sync bug: Frequency changes now immediately update registry
- [x] Added CM dimension placement restriction (survival mode only)
- [x] Renderer uses `LightTexture.FULL_BRIGHT` for consistent visibility

**Why "frequency" instead of "filter"**:
- More intuitive metaphor: Importers/Exporters "tune" to specific item frequencies
- Aligns with radio/signal terminology (fits conduit architecture)
- "Filter" implies blocking unwanted items; "frequency" implies selective transport
- Visual indicator reinforces the concept (item appears on block faces)

**Breaking Changes**:
- NBT tag renamed: `FilterItem` → `FrequencyItem` (old saves lose frequency settings)
- Acceptable for pre-release (0.2.0-alpha) stage

---

## 🗑️ Deprecated Code (Keep for Reference)

### Deleted Files (Old Virtual Buffer Architecture)
**Status**: ✅ **COMPLETE** - All deprecated files removed from project (preserved in git history)

**Java Files Deleted**:
- [x] `portal/VirtualBufferStorage.java` - Was storing items/fluids/energy
- [x] `capabilities/VirtualItemHandler.java` - Replaced by direct transport
- [x] `capabilities/VirtualFluidHandler.java` - Replaced by direct transport
- [x] `capabilities/VirtualEnergyStorage.java` - Replaced by direct transport
- [x] `spatial/CapabilityRouter.java` - Complex routing no longer needed
- [x] `debug/BufferTestCommand.java` - Was testing virtual buffers

**Documentation Files Deleted**:
- [x] `TESTING_CAPABILITY_REGISTRATION.md` - Outdated testing guide
- [x] `TESTING_QUICK_START.md` - Outdated quickstart
- [x] `STORAGE_VIEWER_FEATURE.md` - No storage to view
- [x] `TEST_BUFFER_CAPACITY.md` - No capacity limits to test

**Note**: All files are preserved in git history if ever needed for reference.

**Files to Keep (Reuse)**:
- ✅ `portal/PrefabBlock.java` - Reuse for GUI trigger
- ✅ `portal/PrefabBlockEntity.java` - Modify for face configs and transport logic
- ✅ `portal/RoomCoordinateCache.java` - Reuse for coordinate mapping
- ✅ `spatial/CMInterceptorImpl.java` - Reuse for chunk loading control
- ✅ `portal/TpsCacheUpgradeItem.java` - Keep for CM → PreFab conversion
- ✅ `portal/SimulationWrenchItem.java` - Keep for state control

---

## 🧪 Testing Plan (MVP)

### Test 1: Face Config Persistence
1. Place PreFab block
2. Break PreFab, verify it drops as item
3. Place PreFab elsewhere
4. Verify face configs preserved (when GUI implemented, check configs match)

### Test 2: Basic Transport (Hardcoded Config)
1. Hardcode NORTH face = PULL ITEMS in PreFabBlockEntity constructor
2. Place PreFab in Overworld
3. Place vanilla chest north of PreFab with items
4. Verify items disappear from chest
5. Check CM dimension - items should be in chest/furnace at mapped position
6. **Use only vanilla blocks for MVP testing** (chest, furnace, hopper)

### Test 3: Rate Measurement
1. Configure PreFab faces (hardcoded or via GUI)
2. Use wrench to start simulation (BUILDING → SIMULATING)
3. Wait 600 ticks (~30 seconds)
4. Use wrench to finish simulation (SIMULATING → CACHED)
5. Check logs for calculated rates (e.g., "Iron rate: 0.213 items/tick")

### Test 4: Cached Production
1. Continue from Test 3 (PreFab now in CACHED state)
2. Verify CM chunks are unloaded (check F3 debug screen)
3. Add input items to Overworld side
4. Verify output items appear on Overworld side based on cached rate
5. Check fractional accumulator logs (e.g., "Accumulator: 0.85 -> 1.05, pushing 1 item")

### Test 5: Cache Breaking and HALTED State
1. Continue from Test 4 (CACHED mode)
2. Stop providing input items (remove input chest)
3. Wait for input starvation (PreFab tries to pull but fails)
4. Verify PreFab enters HALTED state (logs or status display)
5. **Verify CM chunks STAY UNLOADED** (F3 debug screen - chunks should still be unloaded!)
6. Add input items back (place chest with items)
7. Use wrench to resume (HALTED → SIMULATING)
8. Verify production resumes

### Test 6: Face Configuration GUI
1. Shift+Right-click PreFab with wrench
2. Select NORTH face
3. Set mode to PULL, filter to ITEMS
4. Save configuration
5. Verify configuration persists after breaking/placing PreFab

---

## 📊 Success Criteria

**MVP is complete when**:
✅ Face configs save/load from NBT correctly
✅ Basic transport works (PULL/PUSH modes)
✅ Rate measurement works during SIMULATING
✅ Cached production works using fractional math
✅ CM chunks unload during CACHED mode (performance gain!)
✅ Cache breaking triggers HALTED state
✅ Player can control states with wrench

**Nice to have** (post-MVP):
⭐ Face configuration GUI
⭐ Status display (right-click without wrench)
⭐ Item/fluid filters
⭐ Multiple PreFab support

---

## 🚀 Priority Order

**Week 1**: Phase 1 (Face config + adjacent detection + simple GUI) - **DE-RISK FIRST**
**Week 2**: Phase 2 (Importer/Exporter blocks) + Phase 3 (Basic transport)
**Week 3**: Phase 4 (Rate measurement) + Phase 5 (Cached production)
**Week 4**: Phase 6 (Wrench control) + Testing
**Week 5**: Phase 7 (Enhanced GUI - optional) + Phase 8 (Dynamic capabilities - optional)
**Week 6**: Polish, optimization, documentation

**Key change**: Start with adjacent block detection and minimal GUI to prove concept works before building Importers/Exporters.

---

## 📚 Reference Documents

- **ARCHITECTURE_CONDUIT.md** - Full architectural spec for conduit system
- **CLAUDE.md** - Updated with conduit architecture and caching focus
- **CM_API_INTEGRATION.md** - Reflection-based CM integration (still valid)
- **DEV2_IMPLEMENTATION.md** - Chunk loading system (reuse CMInterceptorImpl)

---

**Questions?** See ARCHITECTURE_CONDUIT.md for detailed transport logic and face mapping examples.
