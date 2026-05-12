# FPSCompress - Architecture Overview

**Mod Purpose**: Cache factory production rates to run factories without chunk loading  
**Status**: Architecture defined, implementation starting  
**Last Updated**: 2026-04-28

---

## 🎯 The Core Idea

**Problem**: Minecraft factories require chunk loading → TPS cost  
**Solution**: Measure production rates while loaded, then simulate mathematically while unloaded

**Example**:
```
Factory produces 128 iron ingots in 600 ticks
→ Rate = 0.213 ingots/tick

During CACHED mode:
- CM dimension chunks UNLOADED (performance!)
- PreFab accumulates: 0.213 + 0.213 + 0.213...
- When accumulator >= 1.0: Push 1 iron ingot to output
- Result: Factory "runs" without loading chunks
```

---

## 📚 Documentation Structure

### Start Here
1. **MVP_SCOPE.md** - What is/isn't in MVP scope (READ THIS FIRST)
2. **TODO.md** - Implementation roadmap (7 phases)
3. **CLAUDE.md** - Project overview and technical guidelines

### Technical Details
4. **ARCHITECTURE_CONDUIT.md** - Complete conduit system specification
5. **ARCHITECTURE_PIVOT.md** - Why we changed from virtual buffers

### Reference
6. **CM_API_INTEGRATION.md** - Compact Machines integration details
7. **TODO.md** - DEPRECATED (old virtual buffer plan)

---

## 🏗️ Architecture Summary

### PreFab Block
**What it is**: Cross-dimensional conduit that caches production rates

**How it works**:
1. Each of 6 faces configured independently (PULL/PUSH/DISABLED)
2. PULL face: Extract from Overworld → Transport to CM dimension
3. PUSH face: Extract from CM dimension → Transport to Overworld
4. No internal storage - resources transport instantly

### State Machine
```
BUILDING (setup)
    │ Player configures faces
    │ [Wrench click]
    ▼
SIMULATING (measuring)
    │ CM chunks LOADED
    │ Count resources transported
    │ Calculate rates
    │ [Wrench click]
    ▼
CACHED (virtual production)
    │ CM chunks UNLOADED ← Performance gain!
    │ Fractional math simulates production
    │ Transport based on cached rates
    │
    │ [If input starved or output blocked]
    ▼
HALTED (needs fix)
    │ CM chunks STAY UNLOADED
    │ Player fixes Overworld input/output
    │ Wrench click to resume → back to SIMULATING
```

### Face Configuration
```
Each face can be configured with:
- Mode: DISABLED / PULL / PUSH
- Filter: ALL / ITEMS / FLUIDS / ENERGY

Example setup:
┌─────────────────────┐
│   PreFab in World   │
├─────────────────────┤
│ North: PULL ITEMS   │ ← Chest with coal
│ South: PULL ITEMS   │ ← Chest with iron ore
│ East:  PUSH ITEMS   │ → Chest (receives iron ingots)
│ West:  DISABLED     │
│ Up:    DISABLED     │
│ Down:  DISABLED     │
└─────────────────────┘
```

---

## 🎮 Player Experience

### Setup
1. Build factory inside Compact Machine room
2. **Place Importer/Exporter blocks** inside CM room (input/output gates)
   - Importers: Place next to machine inputs (e.g., furnace input slot)
   - Exporters: Place next to machine outputs (e.g., furnace output slot)
3. Right-click CM block with "TPS Cache Upgrade" item → becomes PreFab
4. Shift+Right-click PreFab with Simulation Wrench → Open face config GUI
5. Configure each face:
   - Set mode (PULL/PUSH/DISABLED)
   - Set filter (ITEMS/FLUIDS/ENERGY)
   - **Link to specific Importer/Exporter** (select from dropdown)
6. Connect chests/hoppers to PreFab faces in Overworld

### Calibration (SIMULATING)
1. Right-click PreFab with Simulation Wrench → Start simulation
2. Wait 30-60 seconds (measuring production rates)
3. Right-click with wrench again → Finish simulation, enter CACHED mode

### Running (CACHED)
1. CM dimension chunks now UNLOADED (check F3 screen)
2. PreFab "runs" factory using math, no chunk loading
3. Add inputs to Overworld side → Outputs appear based on cached rates
4. Factory produces continuously without TPS cost

### Maintenance
- If inputs run out → PreFab enters HALTED, loads CM chunks
- Add more inputs → PreFab auto-resumes or requires wrench click
- To recalibrate: Wrench click to restart simulation

---

## 🔧 Technical Implementation

### Key Components

**1. FaceConfig System**
- `FaceMode` enum: DISABLED, PULL, PUSH
- `ResourceFilter` enum: ALL, ITEMS, FLUIDS, ENERGY
- Stored in PreFabBlockEntity, serialized to NBT

**2. ResourceTransporter**
- Core transport logic
- Maps Overworld coordinates to CM dimension positions
- Queries capabilities on both sides
- Extracts from source, inserts to target

**3. Rate Measurement**
- During SIMULATING: Count every resource transported
- Track total amount and time elapsed
- On transition to CACHED: Calculate `rate = total / ticks`

**4. Fractional Accumulator**
- During CACHED: `accumulator += rate` each tick
- When `accumulator >= 1.0`: Transport whole units
- Handles sub-tick production (e.g., 0.213 items/tick)

**5. Chunk Loading Control**
- SIMULATING: Load CM chunks (via CMInterceptorImpl)
- CACHED: Unload CM chunks ← **This is the performance gain!**
- HALTED: Load CM chunks (for player to fix issues)

---

## 📦 MVP Scope

**What's in MVP**:
- ✅ One PreFab block in world
- ✅ Face configuration (6 faces, PULL/PUSH modes)
- ✅ Transport items between dimensions
- ✅ Rate measurement (SIMULATING state)
- ✅ Cached production (CACHED state with fractional math)
- ✅ Vanilla blocks only (chests, furnaces, hoppers)

**What's NOT in MVP** (post-MVP features):
- ❌ AE2 integration
- ❌ Factory Controller block
- ❌ Multiple PreFab management
- ❌ Any external mod integrations
- ❌ PreFab-as-item portability
- ❌ Advanced filters (whitelist/blacklist)

**See MVP_SCOPE.md for complete list.**

---

## 📂 File Structure

### Source Code (Current)
```
src/main/java/com/mukulramesh/fpscompress/
├── FPSCompress.java                    # Main mod class
├── portal/
│   ├── PrefabBlock.java                # PreFab block (reuse, modify)
│   ├── PrefabBlockEntity.java          # PreFab tile entity (reuse, modify)
│   ├── TpsCacheUpgradeItem.java        # CM → PreFab upgrade (keep)
│   ├── RoomCoordinateCache.java        # Coordinate mapping (keep)
│   ├── MachineState.java               # State enum (keep)
│   └── ... (other files to keep)
├── spatial/
│   └── CMInterceptorImpl.java          # Chunk loading control (keep)
└── ... (other packages)
```

### Source Code (To Create for MVP)
```
├── portal/
│   ├── FaceMode.java                   # DISABLED/PULL/PUSH enum
│   ├── ResourceFilter.java             # ALL/ITEMS/FLUIDS/ENERGY enum
│   ├── FaceConfig.java                 # Face configuration data class
│   └── ResourceTransporter.java        # Core transport logic
├── gui/                                # (Optional for MVP)
│   ├── PreFabConfigScreen.java         # Face config GUI
│   ├── PreFabConfigMenu.java           # Server-side container
│   └── FaceConfigPacket.java           # Network sync
└── capabilities/                       # (Phase 7)
    ├── FaceItemHandler.java            # Per-face IItemHandler
    ├── FaceFluidHandler.java           # Per-face IFluidHandler
    └── FaceEnergyStorage.java          # Per-face IEnergyStorage
```

### Deleted Files (Old Architecture)
**Note**: All files deleted from project (preserved in git history - commit `11737a0`)

Previously removed:
- VirtualBufferStorage.java - Old unlimited storage system
- VirtualItemHandler.java - Old smart extraction
- VirtualFluidHandler.java - Old fluid storage
- VirtualEnergyStorage.java - Old energy storage
- CapabilityRouter.java - Old complex routing
- BufferTestCommand.java - Old buffer tests
- And 18+ other deprecated files

---

## 🚀 Getting Started

### For Developers

1. **Read the docs in order**:
   ```
   MVP_SCOPE.md          → Understand scope
   TODO.md           → See implementation phases
   ARCHITECTURE_CONDUIT  → Get technical details
   CLAUDE.md             → Learn project guidelines
   ```

2. **Start with Phase 1** (Face Config Data Structures):
   - Create `FaceMode.java`
   - Create `ResourceFilter.java`
   - Create `FaceConfig.java`
   - Modify `PreFabBlockEntity.java` to store face configs

3. **Follow TODO.md phases sequentially**:
   - Phase 1: Data structures
   - Phase 2: Transport (hardcoded config)
   - Phase 3: Rate measurement
   - Phase 4: Cached production
   - Phase 5: Wrench control
   - Phase 6: GUI (optional)
   - Phase 7: Dynamic capabilities

4. **Test at each phase**:
   - See testing plan in TODO.md
   - Use vanilla blocks only (chests, furnaces, hoppers)
   - Verify chunks unload during CACHED (F3 screen)

### For Testers

**MVP Test Case**:
```
Setup:
1. Build furnace setup in CM room
2. Upgrade CM to PreFab
3. Configure faces (coal input, iron ore input, iron ingot output)
4. Start simulation, wait 30 seconds, finish simulation

Test:
1. Verify CM chunks unload (F3 screen)
2. Add coal and iron ore to input chests
3. Verify iron ingots appear in output chest
4. Check rate matches expected production

Success Criteria:
- Iron ingots produced at correct rate
- CM dimension chunks NOT loaded
- No crashes or errors in logs
```

---

## 🎓 Key Design Decisions

### Why Conduit Instead of Storage?
**Reason**: Mod goal is caching rates, not storing resources
- Simpler code (transport < storage + routing)
- Smaller NBT (configs < resource counts)
- No extraction ambiguity (no "which item to extract?")
- Better aligned with caching goal

### Why Fractional Math?
**Reason**: Production rates often < 1 item/tick
- Example: 1 iron ingot every 5 ticks = 0.2 items/tick
- Accumulator pattern: `0.2 + 0.2 + 0.2 + 0.2 + 0.2 = 1.0` → push 1 item
- Accurate simulation of real production

### Why Face Configuration?
**Reason**: Player control over resource routing
- Each face independent (different modes/filters)
- No auto-detection (explicit configuration)
- Clear mapping: "This side pulls coal, that side pushes iron"

### Why No AE2 in MVP?
**Reason**: Prove caching works before adding complexity
- MVP validates core concept (rate caching)
- External integrations add dependencies and edge cases
- Can add AE2 later once caching is stable

---

## 📊 Success Metrics

**MVP is successful if**:
1. ✅ Chunks unload during CACHED mode (verify in F3)
2. ✅ Production continues while unloaded (outputs appear)
3. ✅ Rates are accurate (matches expected production)
4. ✅ No crashes or errors (stable for 10+ minutes)
5. ✅ Player can control states with wrench

**Measure performance gain**:
- TPS before: Factory running in loaded CM dimension
- TPS after: Factory cached (CM chunks unloaded)
- Expected improvement: Varies by factory complexity

---

## 🔮 Post-MVP Roadmap

**After MVP works**:
1. Polish GUI (better face config interface)
2. Add fluid/energy transport (if not in MVP)
3. Add status display (right-click shows rates)
4. PreFab-as-item system (portability)
5. Factory Controller block (multi-PreFab management)
6. AE2 integration
7. Other mod integrations

**See TODO.md "Post-MVP Features" section for details.**

---

## ❓ FAQ

### Q: Why not just use AE2?
**A**: AE2 doesn't unload chunks - factories still consume TPS. FPSCompress caches rates so chunks can be unloaded.

### Q: What if rates change?
**A**: Player must re-simulate. Factory upgrades (more furnaces, faster machines) require recalibration.

### Q: Does this work with modded machines?
**A**: Yes! As long as they expose standard capabilities (IItemHandler, etc.). But only vanilla for MVP.

### Q: Can I cache multiple factories?
**A**: Not in MVP (single PreFab only). Post-MVP: Factory Controller manages multiple PreFabs.

### Q: What happens if I edit factory during CACHED?
**A**: Cached rates become invalid. Must re-simulate to recalibrate.

### Q: Is this cheating?
**A**: No more than chunk loaders. Player must build real factory and measure real rates. Anti-cheat validation planned for post-MVP.

---

## 📞 Questions?

- **Scope questions**: See MVP_SCOPE.md
- **Implementation questions**: See TODO.md
- **Technical questions**: See ARCHITECTURE_CONDUIT.md
- **CM integration questions**: See CM_API_INTEGRATION.md

---

**Ready to start? Read MVP_SCOPE.md and TODO.md!**
