# FPSCompress TODO List - Conduit Architecture

**Last Updated**: 2026-05-02  
**Architecture**: Conduit-based caching system (see ARCHITECTURE_CONDUIT.md)  
**Current Phase**: Phase 3 Complete ✅ → Ready for Phase 4

**Primary Goal**: Cache factory input/output rates to run factories without chunk loading.

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
  ```java
  // 1. Get face config
  FaceConfig config = getFaceConfig(face);
  if (config.getMode() != FaceMode.PULL) return;
  
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
**Status**: Not Started  
**Goal**: Record actual transport rates while CM chunks are loaded

**Implementation Approach**: Use delta accounting (see VALIDATION_DELTA_ACCOUNTING.md)
- Track four quantities: Imported, Exported, Initial State, Final State
- Calculate net production: `Net = (Final - Initial) + (Exported - Imported)`
- Positive = produced, Negative = consumed, Zero = passthrough

**Tasks**:
- [ ] Add `MachineState` field to PreFabBlockEntity (BUILDING/SIMULATING/CACHED/HALTED)
- [ ] Create `portal/ResourceDeltaTracker.java`:
  - Track totalImported, totalExported per resource type
  - Methods: `recordImport()`, `recordExport()`, `captureInitialState()`, `captureFinalState()`
  - Method: `calculateNet(resource)` returns net production
- [ ] Add delta tracker to PreFabBlockEntity:
  ```java
  ResourceDeltaTracker deltaTracker = new ResourceDeltaTracker();
  long simulationStartTick = 0;
  long simulationEndTick = 0;
  ```
- [ ] On transition BUILDING → SIMULATING:
  - Scan all machine inventories in CM dimension (see VALIDATION_DELTA_ACCOUNTING.md)
  - Call `deltaTracker.captureInitialState(inventory)`
  - Record simulationStartTick
- [ ] During `tick()` when state == SIMULATING:
  - Call `deltaTracker.recordImport()` after each successful pull
  - Call `deltaTracker.recordExport()` after each successful push
- [ ] On transition SIMULATING → CACHED:
  - Scan all machine inventories again
  - Call `deltaTracker.captureFinalState(inventory)`
  - Record simulationEndTick
  - Calculate rates: `rate = calculateNet(resource) / (simulationEndTick - simulationStartTick)`
  - Store rates in PreFabBlockEntity
  - Serialize rates to NBT

---

### Phase 5: Cached Production (Fractional Math)
**Status**: Not Started  
**Goal**: Simulate production using cached rates without loading CM chunks

**Tasks**:
- [ ] Add fractional accumulator fields:
  ```java
  Map<String, Double> itemAccumulators = new HashMap<>();
  Map<String, Double> fluidAccumulators = new HashMap<>();
  double energyAccumulator = 0.0;
  ```
- [ ] During `tick()` when state == CACHED:
  - For each cached rate, accumulate: `accum += rate`
  - When `accum >= 1.0`:
    - Extract whole units: `int whole = (int) accum`
    - Subtract from accumulator: `accum -= whole`
    - Transport whole units via ResourceTransporter
- [ ] Implement cache breaking:
  - If PULL face can't extract from Overworld (input starved) → HALTED
  - If PUSH face can't insert to Overworld (output blocked) → HALTED
  - **IMPORTANT**: Don't reload CM chunks, just enter HALTED state
- [ ] On transition to HALTED:
  - **CM chunks stay UNLOADED** (don't call setRoomChunkState)
  - Show message to player: "Cache broke - fix inputs/outputs in Overworld"
  - Player must fix Overworld side, then wrench-click to resume (→ SIMULATING)

---

### Phase 6: Simulation Wrench Control
**Status**: Partially implemented (exists but needs state machine integration)  
**Goal**: Let player control state transitions

**Files to Modify**:
- `portal/SimulationWrenchItem.java`

**Tasks**:
- [ ] Update `useOn()` to handle PreFab blocks:
  ```java
  if (blockEntity instanceof PrefabBlockEntity prefab) {
      MachineState current = prefab.getState();
      if (current == BUILDING) {
          prefab.startSimulation(); // → SIMULATING
          player.displayClientMessage("Started simulation - measuring rates...");
      } else if (current == SIMULATING) {
          prefab.finishSimulation(); // → CACHED
          player.displayClientMessage("Caching complete - running virtually!");
      } else if (current == CACHED) {
          prefab.resetToBuilding(); // → BUILDING
          player.displayClientMessage("Reset to building mode");
      } else if (current == HALTED) {
          prefab.resumeSimulation(); // → SIMULATING
          player.displayClientMessage("Resumed simulation");
      }
  }
  ```
- [ ] Implement state transition methods in PreFabBlockEntity:
  - `startSimulation()`: Reset counters, load CM chunks
  - `finishSimulation()`: Calculate rates, unload CM chunks, enter CACHED
  - `resetToBuilding()`: Clear rates, load CM chunks
  - `resumeSimulation()`: Keep rates, load CM chunks, continue measurement

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
**Vision**: PreFabs as portable items that store complete factory state
- [ ] Refactor PreFabBlockEntity data to be item-centric:
  - Store face configs in item NBT (not BlockEntity NBT)
  - Store cached rates in item NBT
  - Store room linkage in item NBT
- [ ] When PreFab block breaks → All data goes into item
- [ ] When PreFab item placed → Data loads from item into BlockEntity
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

### Polish & UX
- [ ] Add status display (Right-click PreFab without wrench):
  - Show current state (BUILDING/SIMULATING/CACHED/HALTED)
  - Show configured faces and their modes
  - Show current rates (items/tick, fluids/tick, FE/tick)
  - Show accumulated fractional values during CACHED mode
- [ ] Add PreFab item tooltip:
  - Show state
  - Show room code
  - Show number of configured faces
- [ ] Create texture for PreFab block (currently purple/black checkerboard)
- [ ] Add localization entries

### 2. External Mod Integrations (Post-MVP)
**All external mod integration is OUT OF SCOPE for MVP**
- [ ] AE2 integration (Applied Energistics 2)
- [ ] Refined Storage integration
- [ ] Mekanism pipes/conduits
- [ ] Create mod integration
- [ ] Any other mod-specific features

### 3. Factory Controller Block (Post-MVP)
- [ ] Multi-PreFab inventory management
- [ ] Unified capability interface
- [ ] Centralized chunk loading
- [ ] GUI for managing multiple factories

### 4. Room-Based Filtering (Post-MVP)
**See**: [ROOM_FILTERING.md](ROOM_FILTERING.md) for complete implementation plan

**Problem**: PreFab GUI shows ALL Importers/Exporters across all CM rooms, causing clutter in large factories.

**Solution**: Filter Importers/Exporters by room using player context stack (FILO):
- Track which CM room each player is in using per-player stack
- When player enters CM room → push roomCode onto stack
- When player places Importer/Exporter → peek stack, store roomCode in block
- PreFab only shows Importers/Exporters in its linked room

**Estimated effort**: 2-4 hours  
**Complexity**: MEDIUM  
**Priority**: HIGH (improves UX significantly for multi-room factories)

**Implementation**:
- [ ] Create `PlayerRoomContext.java` registry (FILO stack per player UUID)
- [ ] Hook teleportation events to push/pop room codes
- [ ] Store `roomCode` field in Importer/ExporterBlockEntity
- [ ] Update `ImporterExporterRegistry.Entry` to include roomCode
- [ ] Filter GUI dropdown by PreFab's linked room
- [ ] Handle edge cases (disconnects, nested PreFabs, /tp commands)

### 5. Advanced Features (Post-MVP)
- [ ] Item/fluid whitelist filters (per-face)
- [ ] Blacklist filters
- [ ] Priority system (which face to prioritize)
- [ ] Anti-cheat validation (detect hidden batteries during SIMULATING)
- [ ] Network view (visualize resource flow)
- [ ] Statistics tracking (total resources transported)

---

## 🗑️ Files to Remove (Old Virtual Buffer Architecture)

**Deprecated - No Longer Needed**:
- [ ] `portal/VirtualBufferStorage.java` - Was storing items/fluids/energy, now we transport directly
- [ ] `capabilities/VirtualItemHandler.java` - Replaced by FaceItemHandler
- [ ] `capabilities/VirtualFluidHandler.java` - Replaced by FaceFluidHandler
- [ ] `capabilities/VirtualEnergyStorage.java` - Replaced by FaceEnergyStorage
- [ ] `spatial/CapabilityRouter.java` - Complex routing no longer needed
- [ ] `debug/BufferTestCommand.java` - Was testing virtual buffers
- [ ] `TESTING_CAPABILITY_REGISTRATION.md` - Outdated testing guide
- [ ] `TESTING_QUICK_START.md` - Outdated
- [ ] `STORAGE_VIEWER_FEATURE.md` - No storage to view
- [ ] `TEST_BUFFER_CAPACITY.md` - No capacity limits to test

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
