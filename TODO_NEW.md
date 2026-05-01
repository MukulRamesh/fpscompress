# FPSCompress TODO List - Conduit Architecture

**Last Updated**: 2026-04-28  
**Architecture**: Conduit-based caching system (see ARCHITECTURE_CONDUIT.md)

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
**Status**: Not Started  
**Goal**: Prove PreFab can detect adjacent blocks and faces work correctly

**Why this first**: De-risk the core concept before building Importers/Exporters

**Tasks - Part A: Data Structures**:
- [ ] Create `portal/FaceMode.java` enum (DISABLED, PULL, PUSH)
- [ ] Create `portal/ResourceFilter.java` enum (ALL, ITEMS, FLUIDS, ENERGY)
- [ ] Create `portal/FaceConfig.java` data class:
  ```java
  public class FaceConfig {
      FaceMode mode = FaceMode.DISABLED;
      ResourceFilter resourceType = ResourceFilter.ALL;
      UUID targetUUID = null;  // Will link to Importer/Exporter (Phase 2)
      
      // NBT serialization methods
  }
  ```
- [ ] Modify `portal/PreFabBlockEntity.java`:
  - Add `Map<Direction, FaceConfig> faceConfigs` field
  - Initialize all 6 faces to DISABLED in constructor
  - Implement NBT `saveAdditional()` and `loadAdditional()`

**Tasks - Part B: Adjacent Block Detection (Debug)**:
- [ ] Add debug method to `PreFabBlockEntity`:
  ```java
  public void debugAdjacentBlocks(Player player) {
      for (Direction dir : Direction.values()) {
          BlockPos adjacentPos = this.getBlockPos().relative(dir);
          BlockEntity be = this.level.getBlockEntity(adjacentPos);
          
          if (be != null) {
              // Try to get capabilities
              IItemHandler itemHandler = be.getCapability(Capabilities.ItemHandler.BLOCK, dir.getOpposite());
              IFluidHandler fluidHandler = be.getCapability(Capabilities.FluidHandler.BLOCK, dir.getOpposite());
              IEnergyStorage energyStorage = be.getCapability(Capabilities.EnergyStorage.BLOCK, dir.getOpposite());
              
              player.displayClientMessage(Component.literal(
                  String.format("§6%s: §7%s §a[Items:%s Fluids:%s Energy:%s]",
                      dir.name(),
                      be.getBlockState().getBlock().getName().getString(),
                      itemHandler != null ? "✓" : "✗",
                      fluidHandler != null ? "✓" : "✗",
                      energyStorage != null ? "✓" : "✗"
                  )
              ), false);
          } else {
              player.displayClientMessage(Component.literal(
                  String.format("§6%s: §8No block", dir.name())
              ), false);
          }
      }
  }
  ```
- [ ] Modify `PrefabBlock.useWithoutItem()`:
  - Right-click PreFab without item → Call `debugAdjacentBlocks(player)`
  - Shows what blocks are adjacent and what capabilities they have

**Tasks - Part C: Simple Face Config GUI**:
- [ ] Create minimal GUI (no Importer/Exporter linking yet):
  ```
  ┌─────────────────────────────┐
  │   Face: NORTH               │
  ├─────────────────────────────┤
  │ Mode:   [DISABLED] [PULL] [PUSH]
  │ Filter: [ALL] [ITEMS] [FLUIDS] [ENERGY]
  │ [Save]                      │
  └─────────────────────────────┘
  ```
- [ ] Trigger: Shift+Right-click PreFab with wrench
- [ ] Network packet to sync config to server
- [ ] Test: Configure faces, break PreFab, verify configs preserved in NBT

**De-risk Validation**:
- ✅ Can detect adjacent chests/furnaces/hoppers
- ✅ Can query their capabilities (Items/Fluids/Energy)
- ✅ Face configs save/load correctly
- ✅ GUI opens and works

**If this phase fails**: Core concept broken, rethink architecture  
**If this phase succeeds**: Confident to proceed with Importers/Exporters

---

### Phase 2: Importer/Exporter Blocks
**Status**: Not Started  
**Goal**: Create input/output gate blocks for CM dimension

**Prerequisites**: Phase 1 complete (face config working, adjacent detection proven)

**Tasks**:
- [ ] Create `portal/ImporterBlock.java` and `ImporterBlockEntity.java`:
  - Acts as input gate in CM dimension
  - Exposes IItemHandler/IFluidHandler/IEnergyStorage
  - Has UUID for identification
  - Internal buffer (small, just for passthrough)
- [ ] Create `portal/ExporterBlock.java` and `ExporterBlockEntity.java`:
  - Acts as output gate in CM dimension
  - Queries adjacent machines for resources
  - Has UUID for identification
  - Internal buffer (small, just for passthrough)
- [ ] Register blocks in `FPSCompress.java`:
  - Add IMPORTER_BLOCK and EXPORTER_BLOCK to DeferredRegister
  - Add IMPORTER_BE and EXPORTER_BE BlockEntity types
- [ ] Test: Place Importer/Exporter in CM dimension, verify UUID generation
- [ ] Test: Place chest next to Importer, verify chest can insert items
- [ ] Add `findImporterByUUID(UUID)` and `findExporterByUUID(UUID)` to PreFabBlockEntity
- [ ] Update Phase 1 GUI to include Importer/Exporter dropdown (select target UUID)

---

### Phase 3: Basic Transport Logic (Hardcoded Config)
**Status**: Not Started  
**Goal**: Move resources between Overworld and CM dimension based on hardcoded face config

**Tasks**:
- [ ] Create `portal/ResourceTransporter.java`:
  - `transportItems(from, to, maxAmount)` - Move items between BlockPos
  - `transportFluids(from, to, maxAmount)` - Move fluids
  - `transportEnergy(from, to, maxAmount)` - Move energy
- [ ] Modify `portal/PreFabBlockEntity.java`:
  - Override `tick()` method
  - For each enabled face, call ResourceTransporter
  - Query adjacent capabilities in Overworld
  - Map to CM dimension coordinates (use RoomCoordinateCache)
  - Insert/extract based on face mode (PULL/PUSH)
- [ ] Test with hardcoded config:
  - Set NORTH face = PULL ITEMS in code
  - Place chest north of PreFab, add items
  - Verify items transport to CM dimension

---

### Phase 4: Rate Measurement (SIMULATING State)
**Status**: Not Started  
**Goal**: Record actual transport rates while CM chunks are loaded

**Tasks**:
- [ ] Add `MachineState` field to PreFabBlockEntity (BUILDING/SIMULATING/CACHED/HALTED)
- [ ] Add rate tracking fields:
  ```java
  Map<String, Double> itemRates = new HashMap<>(); // resource_id -> items/tick
  Map<String, Double> fluidRates = new HashMap<>(); // fluid_id -> mB/tick
  double energyRate = 0.0; // FE/tick
  int simulationTicks = 0;
  ```
- [ ] During `tick()` when state == SIMULATING:
  - Count all resources transported
  - Increment simulationTicks
- [ ] On transition SIMULATING → CACHED:
  - Calculate rates: `rate = totalTransported / simulationTicks`
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

### Phase 7: Enhanced Face Configuration GUI (Optional)
**Status**: Not Started  
**Goal**: Let player configure faces visually

**Files to Create**:
- `gui/PreFabConfigScreen.java` - Client-side GUI (extends Screen)
- `gui/PreFabConfigMenu.java` - Server-side container (extends AbstractContainerMenu)
- `network/FaceConfigPacket.java` - Client → Server sync

**Tasks**:
- [ ] Create GUI with 6 face buttons (North/South/East/West/Up/Down)
- [ ] For selected face, show:
  - Mode buttons: [DISABLED] [PULL] [PUSH]
  - Filter buttons: [ALL] [ITEMS] [FLUIDS] [ENERGY]
- [ ] On button click:
  - Send packet to server with new config
  - Server validates and updates PreFabBlockEntity
  - Server sends confirmation back to client
- [ ] Trigger GUI:
  - Modify `PrefabBlock.useItemOn()` to detect Shift+Right-click with wrench
  - Open GUI on client side

---

### Phase 8: Dynamic Capabilities (Optional for MVP)
**Status**: Not Started (CapabilityRegistration exists but needs rewrite)  
**Goal**: Expose capabilities based on face configuration

**Files to Modify**:
- `portal/CapabilityRegistration.java`

**Tasks**:
- [ ] Register capabilities with context-aware logic:
  ```java
  event.registerBlockEntity(
      Capabilities.ItemHandler.BLOCK,
      FPSCompress.PREFAB_BE.get(),
      (blockEntity, context) -> {
          if (blockEntity instanceof PrefabBlockEntity prefab) {
              Direction face = context; // Which side is being queried
              FaceConfig config = prefab.getFaceConfig(face);
              
              if (config.resourceType.allowsItems()) {
                  return new FaceItemHandler(prefab, face, config.mode);
              }
          }
          return null;
      }
  );
  ```
- [ ] Create `capabilities/FaceItemHandler.java`:
  - If mode == PULL: Accept insertItem(), reject extractItem()
  - If mode == PUSH: Accept extractItem(), reject insertItem()
  - If mode == DISABLED: Reject all operations
- [ ] Similar for FaceFluidHandler and FaceEnergyStorage

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

### 4. Advanced Features (Post-MVP)
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
