# PreFab Conduit Architecture

**Last Updated**: 2026-04-28  
**Status**: New Architecture (Replaces Virtual Buffer System)

---

## Core Concept Change

### Old Architecture (DEPRECATED)
- PreFabs stored items/fluids/energy in virtual buffers
- BlockEntity contained unlimited storage maps
- External mods inserted → stored internally → extracted later
- Complex capability routers with smart extraction logic

### New Architecture (CURRENT)
- **PreFabs are cross-dimensional conduits, not storage**
- Items/fluids/energy are **transported instantly** between dimensions
- No internal storage - purely a transport mechanism
- Configuration GUI defines which faces pull/push what resources

---

## Player Experience

### Setup Flow
1. **Build factory inside Compact Machine room**
   - Place machines, connect them with pipes/cables
   - Set up production chains

2. **Upgrade CM to PreFab** (Right-click with TPS Upgrade item)
   - CM block becomes PreFab block
   - PreFab links to the CM dimension room

3. **Configure PreFab faces** (Right-click with Simulation Wrench or shift+right-click)
   - Opens GUI showing 6 faces (North/South/East/West/Up/Down)
   - For each face, configure:
     - **Mode**: Push, Pull, or Disabled
     - **Resource Type**: Items, Fluids, Energy, or All
     - **Filter**: (optional) Whitelist/blacklist specific items/fluids

4. **Connect external systems** (Overworld side)
   - Connect hoppers, pipes, AE2 interfaces to PreFab faces
   - PreFab automatically transports resources between dimensions

5. **Verify connection** (Right-click PreFab without wrench)
   - Shows status of each configured face
   - Displays what resources are currently being transported
   - Confirms dimension room is accessible

---

## Technical Architecture

### Three Block Types

**1. PreFab Block** (Overworld only)
- Upgraded CM block with face configuration
- 6 independently configurable faces (PULL/PUSH/DISABLED per face)
- Routes resources between Overworld and CM dimension
- Controls state machine (BUILDING/SIMULATING/CACHED/HALTED)
- No internal storage - just a router

**2. Importer Block** (CM dimension only)
- Placed inside CM room by player
- Acts as **input gate**: Receives resources from PreFab PULL faces
- Exposes IItemHandler/IFluidHandler/IEnergyStorage to adjacent machines
- Example: Place Importer next to furnace, furnace pulls from Importer

**3. Exporter Block** (CM dimension only)
- Placed inside CM room by player
- Acts as **output gate**: Sends resources to PreFab PUSH faces
- Queries adjacent machines for resources to extract
- Example: Place Exporter next to furnace output, Exporter pulls from furnace

### Why Importers/Exporters?

**Problem with old design**: PreFab faces mapped directly to CM coordinates → complex coordinate math, unclear where resources go

**Solution with Importers/Exporters**:
- ✅ Clear input/output points (player places them)
- ✅ No coordinate mapping math (Importers/Exporters have fixed positions)
- ✅ Flexible factory layout (multiple Importers/Exporters in one room)
- ✅ Visible to player (can see where resources enter/exit factory)

---

## Face Configuration System

### Data Structure

```java
public enum FaceMode {
    DISABLED,  // No transport
    PULL,      // Extract from Overworld → send to CM dimension
    PUSH       // Extract from CM dimension → send to Overworld
}

public enum ResourceFilter {
    ALL,       // Transport all resource types
    ITEMS,     // Only items
    FLUIDS,    // Only fluids
    ENERGY     // Only energy (Forge Energy)
}

public class FaceConfig {
    FaceMode mode;
    ResourceFilter resourceType;
    @Nullable Set<String> itemWhitelist;  // Optional: specific item IDs
    @Nullable Set<String> fluidWhitelist; // Optional: specific fluid IDs
}

// PreFabBlockEntity stores:
Map<Direction, FaceConfig> faceConfigs = new EnumMap<>(Direction.class);
```

### Default Configuration
When CM → PreFab upgrade happens:
- All faces default to **DISABLED**
- Player must explicitly configure faces
- This prevents accidental resource loops

---

## Transport Logic

### Pull Mode (Overworld → CM Dimension → Importers)

**During SIMULATING or CACHED states**:

1. **Overworld side**: PreFab face configured as PULL ITEMS
   - Query adjacent BlockEntity (e.g., chest north of PreFab)
   - Extract items from chest's IItemHandler capability

2. **CM dimension side**: Find linked Importer block
   - PreFab has UUID-based link to specific Importer(s)
   - Importer acts as receiving buffer

3. **Insert to Importer**:
   - Importer has internal capability that adjacent machines can pull from
   - Furnace next to Importer pulls items automatically

4. **Rate measurement** (SIMULATING only):
   - Count items transported: `itemCount += extracted`
   - Track time: `ticksElapsed++`
   - On finish: `rate = itemCount / ticksElapsed`

**Example Setup**:
```
Overworld:
  [Chest] → [PreFab North=PULL ITEMS]

CM Dimension:
  [Importer #1] → [Furnace input]
  
PreFab knows: "North face → Importer #1 (UUID: abc-123)"
```

### Push Mode (CM Dimension → Exporters → Overworld)

**During SIMULATING or CACHED states**:

1. **CM dimension side**: Exporter queries adjacent machine
   - Exporter pulls from furnace output (IItemHandler)
   - Exporter stores extracted items temporarily

2. **PreFab queries linked Exporter**:
   - PreFab face configured as PUSH ITEMS
   - PreFab pulls from linked Exporter's internal buffer

3. **Overworld side**: Insert to adjacent block
   - PreFab inserts into adjacent chest's IItemHandler

4. **Rate measurement** (SIMULATING only):
   - Count items pushed: `itemCount += pushed`
   - Track time: `ticksElapsed++`
   - On finish: `rate = itemCount / ticksElapsed`

**Example Setup**:
```
CM Dimension:
  [Furnace output] → [Exporter #1]

Overworld:
  [PreFab South=PUSH ITEMS] → [Chest]
  
PreFab knows: "South face → Exporter #1 (UUID: def-456)"
```

---

## Capability Integration

### PreFab Capabilities

PreFab block exposes capabilities on each face **dynamically**:

**IItemHandler** (if face configured for ITEMS):
- `insertItem()`: Routes to CM dimension (PULL mode) or rejects (PUSH mode)
- `extractItem()`: Routes from CM dimension (PUSH mode) or rejects (PULL mode)
- `getSlots()`: Returns 1 (single-slot passthrough)
- `getSlotLimit()`: Returns 64 (standard stack size)

**IFluidHandler** (if face configured for FLUIDS):
- `fill()`: Routes to CM dimension (PULL mode)
- `drain()`: Routes from CM dimension (PUSH mode)
- `getTankCapacity()`: Returns Integer.MAX_VALUE (unlimited passthrough)

**IEnergyStorage** (if face configured for ENERGY):
- `receiveEnergy()`: Routes to CM dimension (PULL mode)
- `extractEnergy()`: Routes from CM dimension (PUSH mode)
- `getMaxEnergyStored()`: Returns Integer.MAX_VALUE (unlimited passthrough)

### Why This Works

External mods (pipes, AE2, hoppers) interact with PreFab faces like any other block:
- Hopper above PULL face → items transport to CM dimension
- Mekanism pipe on PUSH face → fluids transport from CM dimension
- AE2 interface on PULL face → items transport to CM dimension for processing

**No storage needed** - resources instantly transported (within same tick).

---

## Configuration GUI

### GUI Layout

```
┌─────────────────────────────────────────────┐
│         PreFab Face Configuration           │
├─────────────────────────────────────────────┤
│                                             │
│   Select Face:  [North] [South] [East]     │
│                 [West]  [Up]    [Down]      │
│                                             │
│   Current Face: NORTH                       │
│   ┌─────────────────────────────────────┐   │
│   │ Mode:      [DISABLED] [PULL] [PUSH] │   │
│   │ Resource:  [ ALL ]                  │   │
│   │            [ITEMS] [FLUIDS] [ENERGY]│   │
│   │                                     │   │
│   │ Filter: [Item Whitelist] [Disabled]│   │
│   │         [Fluid Whitelist]           │   │
│   └─────────────────────────────────────┘   │
│                                             │
│   [Save] [Cancel]                           │
└─────────────────────────────────────────────┘
```

### GUI Trigger
- **Shift + Right-Click** with Simulation Wrench → Opens GUI
- **Right-Click** without wrench → Shows status (no GUI)

### Network Sync
- Client sends configuration changes to server via packet
- Server validates and applies to PreFabBlockEntity
- Server sends confirmation back to client

---

## Importer/Exporter Linking System

### Problem
PreFab faces need to know **which** Importer/Exporter to route to.

### Solution: UUID-Based Linking

**Setup Phase** (During BUILDING state):

1. **Player places Importer/Exporter blocks inside CM room**
   - Each Importer/Exporter gets a unique UUID on placement
   - Shows UUID in chat/tooltip: "Importer #1 (abc-123)"

2. **Player configures PreFab face**:
   - Shift+Right-click PreFab with wrench → Open GUI
   - Select face (e.g., NORTH)
   - Set mode: PULL
   - Set filter: ITEMS
   - **Set target**: Select from dropdown of available Importers in room
   - GUI shows: "Importer #1 (abc-123) - 3 blocks from center"

3. **PreFab stores link**:
   ```java
   class FaceConfig {
       FaceMode mode;              // PULL
       ResourceFilter filter;      // ITEMS
       UUID targetUUID;            // abc-123 (Importer's UUID)
   }
   ```

**Runtime (SIMULATING/CACHED states)**:

```java
// PreFab NORTH face = PULL ITEMS → Importer abc-123

// 1. Extract from Overworld
BlockPos overworldSource = prefabPos.relative(Direction.NORTH);
BlockEntity overworldBE = level.getBlockEntity(overworldSource);
IItemHandler overworldHandler = overworldBE.getCapability(ItemHandler.BLOCK);
ItemStack extracted = overworldHandler.extractItem(0, 64, false);

// 2. Find target Importer by UUID
ServerLevel cmLevel = getCMLevel();
ImporterBlockEntity importer = findImporterByUUID(cmLevel, targetUUID); // Search loaded chunks

// 3. Insert to Importer
if (importer != null) {
    ItemStack remainder = importer.insertItem(extracted);
    int transferred = extracted.getCount() - remainder.getCount();
    
    // 4. Track for rate measurement
    if (state == SIMULATING) {
        recordItemTransfer("minecraft:iron_ore", transferred);
    }
}
```

**Benefits**:
- ✅ No coordinate math
- ✅ Player has full control over routing
- ✅ Multiple Importers/Exporters supported
- ✅ Clear 1-to-1 mapping (face → Importer/Exporter)

---

## Anti-Cheat Considerations

### Old System (Virtual Buffers)
- Needed snapshot scanning to detect hidden batteries
- Complex validation logic

### New System (Conduit Transport)
**PreFab itself has no storage**, but players can still cheat with hidden storage:

**The Real Risk**: Hidden chests inside factory
- Player places chest with 1000 iron ingots inside CM room
- During SIMULATING: Factory "produces" iron ingots (actually from chest)
- Cached rate includes chest contents, not actual production
- During CACHED: Factory appears to produce iron from nothing
- **This is impossible to detect by checking Importers/Exporters alone**

**Post-MVP Validation Strategy** (Bidirectional Redstone Control):

### The Challenge
**Autonomous factories**: Some factories run continuously without natural "idle" moments
- Example: Cobblestone generator → Crusher → Always producing
- No clear "cycle complete" state
- Can't know when to take snapshot

### The Solution: Graceful Shutdown Protocol

**Step 1: PreFab signals "Please shut down"**
```
PreFab face type: SHUTDOWN_SIGNAL (outputs redstone)
Player configures: "When starting validation, send redstone pulse"

PreFab → [Shutdown Signal Block] → Factory redstone logic
```

**Step 2: Factory shuts down gracefully**
```
Redstone circuit detects shutdown signal:
- Stop importing new inputs
- Finish processing current items
- Wait for all machines to idle
- Return to "clean state" (empty buffers)
```

**Step 3: Factory signals "I'm ready"**
```
Factory redstone logic → [Idle Detector Block] → PreFab

Idle Detector checks:
- All input chests empty? ✓
- All output chests processed? ✓
- All furnaces idle? ✓
- All hoppers stopped? ✓
→ Send pulse to PreFab: "Ready for validation!"
```

**Step 4: PreFab validates**
```
PreFab receives idle signal:
- Take hash snapshot
- Compare with start-of-simulation hash
- If match: Factory is valid loop! ✓
- PreFab sends redstone signal: "Resume operation"
```

**Step 5: Factory resumes**
```
Factory detects "resume" signal:
- Re-enable input importers
- Start processing again
- Return to normal operation
```

### Bidirectional Control Flow
```
SIMULATION START:
  PreFab → "Shutdown" signal → Factory
  Factory processes...
  Factory → "Idle" signal → PreFab
  PreFab takes hash (start state)
  PreFab → "Resume" signal → Factory

SIMULATION END (after configured time):
  PreFab → "Shutdown" signal → Factory
  Factory processes...
  Factory → "Idle" signal → PreFab
  PreFab takes hash (end state)
  
  Compare hashes:
    Match? → Valid loop! ✓
    Differ? → Hidden storage detected ✗
```

### Example: Autonomous Cobblestone Factory

**Without graceful shutdown** (broken):
- Factory always running
- Can't determine idle state
- Hash changes every tick (items in-flight)
- Validation impossible ❌

**With graceful shutdown** (works!):
```
Redstone circuit:
  IF shutdown signal:
    - Close input gate (stop cobble generator)
    - Wait for all items to finish processing
    - Wait for all chests to stabilize
    - When idle: Send "ready" pulse
  
  IF resume signal:
    - Open input gate
    - Resume production
```

**Result**: Factory can enter deterministic idle state on demand ✓

### Why This Works
- ✅ **Handles autonomous factories**: Can shut down on command
- ✅ **Player has full control**: Designs shutdown logic
- ✅ **Deterministic**: Same idle state every time
- ✅ **Real-world pattern**: Industrial "safe shutdown" protocol
- ✅ **Can't be cheated**: Hash comparison catches storage changes

### Implementation Details

**New PreFab face types** (Post-MVP):
1. `SHUTDOWN_SIGNAL` - PreFab outputs redstone (tells factory to shut down)
2. `IDLE_SIGNAL` - PreFab inputs redstone (factory says "I'm idle")
3. `RESUME_SIGNAL` - PreFab outputs redstone (tells factory to resume)

**New blocks** (Post-MVP):
1. `IdleDetectorBlock` - In CM dimension, sends pulse when idle
2. `ShutdownListenerBlock` - In CM dimension, receives shutdown signal
3. `ResumeListenerBlock` - In CM dimension, receives resume signal

**Face configuration** (Post-MVP GUI):
```
┌─────────────────────────────────────┐
│   Face: UP                          │
├─────────────────────────────────────┤
│ Mode:   [SHUTDOWN_SIGNAL]           │
│                                     │
│ Sends redstone when:                │
│   [●] Simulation starts             │
│   [ ] Simulation ends               │
│   [●] Validation requested          │
└─────────────────────────────────────┘
```

### Alternative: Simple Timeout (MVP)

**For MVP**: Skip redstone control entirely
- Use fixed timeout (configurable: 30s, 60s, 120s)
- Take snapshot at start, snapshot after timeout
- Compare hashes
- **Works for 80% of factories** (those with natural cycles)

**Post-MVP**: Add redstone control for advanced factories

**For MVP**: Skip anti-cheat validation (trust players)
- Focus on proving caching works
- Add validation post-MVP once core system is stable
- **This is fundamentally impossible to implement perfectly** (outside MVP scope)

**Remaining Validation** (MVP scope):
- Ensure face configurations are valid (no contradictory settings)
- Check that CM dimension room still exists
- Verify Importer/Exporter UUIDs are valid

---

## Performance Implications

### Advantages Over Virtual Buffers
✅ **Less memory usage** - No Map<String, Integer> storage  
✅ **Simpler serialization** - Only face configs in NBT, not resource counts  
✅ **No extraction ambiguity** - No "which item to extract?" problem  
✅ **Real-time feedback** - Player sees resources move instantly  

### Performance Costs
⚠️ **Tick-based transport** - Every enabled face queries capabilities each tick  
⚠️ **Cross-dimensional access** - Requires ServerLevel lookups  
⚠️ **Chunk loading dependency** - CM dimension chunks must stay loaded  

**Optimization Strategy**:
- Only tick faces with mode != DISABLED
- Cache capability lookups (invalidate on neighbor change)
- Batch multiple resource types in single tick
- Skip ticking if no adjacent capabilities detected

---

## State Machine Integration

### Old System
- BUILDING → SIMULATING → CACHED
- CACHED mode used fractional math with virtual buffers
- Complex state transitions

### New System (Simplified)
- **BUILDING**: Player configures faces, places Importers/Exporters in CM dimension
- **SIMULATING**: CM chunks loaded, measure actual resource flow rates
- **CACHED**: CM chunks unloaded, simulate production using fractional math
- **HALTED**: Cache broke (input starved or output blocked), CM stays unloaded

**State Transitions**:
- BUILDING → SIMULATING: Load CM chunks, start rate measurement
- SIMULATING → CACHED: Calculate rates, unload CM chunks
- CACHED → HALTED: Input starved or output blocked, **CM stays unloaded** (player fixes Overworld side)
- HALTED → SIMULATING: Player fixes issue, wrench click to resume

**Key**: HALTED doesn't reload chunks - player fixes input/output in Overworld, then resumes.

---

## Implementation Plan

### Phase 1: Face Configuration System
**Files to Create**:
- `portal/FaceConfig.java` - Data class for face settings
- `portal/FaceMode.java` - Enum (DISABLED/PULL/PUSH)
- `portal/ResourceFilter.java` - Enum (ALL/ITEMS/FLUIDS/ENERGY)

**Files to Modify**:
- `portal/PreFabBlockEntity.java`:
  - Add `Map<Direction, FaceConfig> faceConfigs`
  - Serialize to/from NBT
  - Add `getFaceConfig(Direction)` and `setFaceConfig(Direction, FaceConfig)`

### Phase 2: Transport Logic
**Files to Create**:
- `portal/ResourceTransporter.java` - Core transport logic
  - `transportItems(ServerLevel, BlockPos, BlockPos, int maxAmount)`
  - `transportFluids(ServerLevel, BlockPos, BlockPos, int maxAmount)`
  - `transportEnergy(ServerLevel, BlockPos, BlockPos, int maxAmount)`

**Files to Modify**:
- `portal/PreFabBlockEntity.java`:
  - Override `tick()` method
  - For each enabled face, call ResourceTransporter methods

### Phase 3: Configuration GUI
**Files to Create**:
- `gui/PreFabConfigScreen.java` - Client-side GUI
- `gui/PreFabConfigMenu.java` - Server-side container
- `network/FaceConfigPacket.java` - Client → Server sync

**Files to Modify**:
- `portal/PrefabBlock.java`:
  - Shift+Right-click with wrench → Open GUI
  - Right-click without wrench → Show status

### Phase 4: Dynamic Capabilities
**Files to Modify**:
- `portal/CapabilityRegistration.java`:
  - Register capabilities with context-aware logic
  - Check face configuration before exposing capability
  - Route based on face mode (PULL/PUSH)

---

## Migration from Virtual Buffer System

### Files to Remove/Deprecate
- ❌ `portal/VirtualBufferStorage.java` (no longer needed)
- ❌ `capabilities/VirtualItemHandler.java` (replace with conduit logic)
- ❌ `capabilities/VirtualFluidHandler.java` (replace with conduit logic)
- ❌ `capabilities/VirtualEnergyStorage.java` (replace with conduit logic)
- ❌ `spatial/CapabilityRouter.java` (replace with simpler face-based routing)

### Files to Keep (Reuse)
- ✅ `portal/IVirtualMachineData.java` (rename to `IPreFabData`, remove buffer methods)
- ✅ `portal/PrefabBlock.java` (modify for GUI trigger)
- ✅ `portal/PrefabBlockEntity.java` (replace storage with face configs)
- ✅ `portal/RoomCoordinateCache.java` (reuse for transport coordinate mapping)
- ✅ `spatial/CMInterceptorImpl.java` (reuse for chunk loading control)

### Testing Commands to Update
- ❌ `/testbuffer` - Remove (no buffers to test)
- ✅ `/testconduit` - New command:
  - Test face configuration
  - Verify transport between dimensions
  - Show current transport rates (items/tick, fluids/tick, FE/tick)

---

## Post-MVP: External Integrations

**ALL EXTERNAL MOD INTEGRATIONS ARE OUT OF SCOPE FOR MVP**

The following features are explicitly deferred until after core caching works:

### 1. AE2 Integration (Post-MVP)
- Factory Controller block with AE2 IGridHost interface
- Automatic resource routing via ME network
- Pattern provider support
- Crafting CPU integration

### 2. Refined Storage Integration (Post-MVP)
- External Storage on Controller
- Import/Export bus support
- Crafting grid integration

### 3. Other Mod Integrations (Post-MVP)
- Mekanism pipes/logistics
- Create mod integration
- Pipez
- Any other tech mods

**MVP uses ONLY vanilla Minecraft blocks**: chests, furnaces, hoppers, droppers, dispensers.

---

## Post-MVP: PreFab-as-Item System

### Vision
**Goal**: Make PreFabs truly portable by storing all state in item NBT, not BlockEntity.

**Current (MVP)**: PreFab is a block
- Face configs stored in PreFabBlockEntity
- Rates stored in PreFabBlockEntity
- Breaking block preserves data via `getCloneItemStack()` (standard Minecraft pattern)

**Future**: PreFab is primarily an item
- Face configs stored in item NBT
- Rates stored in item NBT
- Room linkage stored in item NBT
- When placed: Item data loads into BlockEntity (temporary runtime state)
- When broken: BlockEntity data already in item (no transfer needed)

### Factory Controller Integration
**Inventory-Based Management**:
- Factory Controller has 9-slot inventory
- Each slot accepts one PreFab item
- Controller ticks all PreFab items in inventory (runs cached production)
- Controller manages chunk loading for all linked CM rooms
- Controller exposes unified capabilities to AE2

**Player Workflow**:
1. Build factory in CM room
2. Configure PreFab faces, run simulation
3. Break PreFab → get item with cached rates
4. Place PreFab item in Factory Controller
5. Controller runs cached production for all PreFabs
6. Connect Controller to AE2 → automatic resource routing

### Why This is Powerful
✅ **Portability**: Carry factories in inventory, ender chests  
✅ **Scalability**: One Controller manages multiple factories  
✅ **Trading**: Give/sell PreFab items to other players  
✅ **Compact Storage**: Store inactive factories as items  
✅ **AE2 Integration**: Single connection point for all factories  

### Why Post-MVP
⚠️ **Complexity**: Item ticking, inventory management, Controller block  
⚠️ **Dependency**: Need caching to work perfectly first  
⚠️ **Testing**: Much harder to debug when data is in items  

**Recommendation**: Get MVP working with PreFab blocks first, then add item portability.

---

## Benefits of New Architecture

### For Players
✅ **Intuitive**: PreFabs act like pipes/conduits (familiar concept)  
✅ **Visual feedback**: Resources move in real-time (no hidden storage)  
✅ **Flexible**: Configure each face independently  
✅ **No capacity limits**: No "buffer full" errors  

### For Developers
✅ **Simpler code**: No virtual buffer maps, no smart extraction logic  
✅ **Easier debugging**: Transport happens in one place (ResourceTransporter)  
✅ **Better performance**: No large NBT serialization for millions of items  
✅ **Modular**: Face configuration separate from transport logic  

### For Mod Compatibility
✅ **Standard capabilities**: External mods just see IItemHandler/IFluidHandler/IEnergyStorage  
✅ **No special cases**: No "smart extraction" workarounds  
✅ **Real-time**: No sync delays between storage queries  

---

## Known Limitations

### 1. No Buffering
- If CM dimension is full (output blocked), Overworld side won't accept more resources
- Solution: Player must fix output blockage or disable face

### 2. Chunk Loading Dependency
- CM dimension chunks must stay loaded for transport to work
- Solution: Force-load CM chunks when any face is enabled (reuse CMInterceptorImpl)

### 3. No Smart Routing
- Face configuration is manual - PreFab doesn't auto-detect what to transport
- Solution: This is by design - player has full control

### 4. Single-Face Limitation
- Each face can only have one mode (not both PULL and PUSH)
- Solution: Use multiple PreFabs for bidirectional transport on same side

---

## Next Steps

1. **Update CLAUDE.md** - Replace virtual buffer sections with conduit architecture
2. **Update TODO.md** - Mark Phase 2 (virtual buffers) as deprecated, add new phases
3. **Create implementation plan** - Detailed step-by-step for face config GUI
4. **Remove old code** - Clean up VirtualBufferStorage and related classes
5. **Implement Phase 1** - Face configuration data structures
6. **Test in Minecraft** - Verify face config saves/loads correctly

---

**Questions?** See CLAUDE.md (updated) or ask for clarification on specific transport logic.
