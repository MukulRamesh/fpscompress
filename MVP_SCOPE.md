# MVP Scope Definition

**Last Updated**: 2026-04-28  
**Goal**: Get ONE PreFab block to cache production rates correctly

---

## ✅ What IS in MVP Scope

### Core Caching System
1. **Three block types**:
   - **PreFab block** (Overworld) - Routes resources, controls state machine
   - **Importer blocks** (CM dimension) - Input gates for factory
   - **Exporter blocks** (CM dimension) - Output gates from factory
2. **Face configuration**:
   - Configure each of 6 PreFab faces independently
   - Modes: DISABLED, PULL (Overworld→Importer), PUSH (Exporter→Overworld)
   - Filters: ALL, ITEMS, FLUIDS, ENERGY
   - **Link to specific Importer/Exporter** (UUID-based)
3. **Resource transport**:
   - Move items between Overworld and CM dimension
   - Move fluids between dimensions (if time permits)
   - Move energy between dimensions (if time permits)
4. **Rate measurement** (SIMULATING state):
   - Load CM dimension chunks
   - Count resources transported over time
   - Calculate average rates (items/tick, fluids/tick, FE/tick)
5. **Cached production** (CACHED state):
   - Unload CM dimension chunks (**THIS IS THE PERFORMANCE GAIN**)
   - Use fractional math to simulate production
   - Accumulate fractional values, transport when >= 1.0
6. **State control**:
   - Simulation Wrench to toggle states (BUILDING → SIMULATING → CACHED)
   - Manual player control (no automation)
7. **NBT preservation**:
   - Face configs save when PreFab broken
   - Cached rates save when PreFab broken
   - Room linkage preserved

### Vanilla Minecraft Only
- ✅ Chests (input/output)
- ✅ Furnaces (processing)
- ✅ Hoppers (automation)
- ✅ Droppers/Dispensers (automation)
- ✅ Standard IItemHandler/IFluidHandler/IEnergyStorage capabilities

### Minimal GUI
- ✅ Face configuration screen (Shift+Right-click with wrench)
  - Select face
  - Set mode (DISABLED/PULL/PUSH)
  - Set filter (ALL/ITEMS/FLUIDS/ENERGY)
  - Save button
- ✅ Status display (Right-click without wrench)
  - Current state
  - Configured faces
  - Current rates (if in CACHED mode)

---

## ❌ What is NOT in MVP Scope

### External Mod Integrations
- ❌ **Applied Energistics 2 (AE2)** - No ME network integration
- ❌ **Refined Storage** - No storage network integration
- ❌ **Mekanism** - No special pipe support
- ❌ **Create** - No mechanical integration
- ❌ **Pipez** - Works via standard capabilities, but no special support
- ❌ **Any other tech mod** - Standard capabilities only

### Advanced Features
- ❌ **Factory Controller block** - Only single PreFab blocks for MVP
- ❌ **Multiple PreFab management** - One PreFab at a time
- ❌ **PreFab-as-item portability** - PreFab is just a block for MVP
- ❌ **Item/fluid filters** (whitelist/blacklist) - ALL resources for MVP
- ❌ **Priority system** - No prioritization between faces
- ❌ **Anti-cheat scanning** - Trust players for MVP
- ❌ **Statistics tracking** - No fancy metrics
- ❌ **Network visualization** - No GUI diagrams

### Complex Transport
- ❌ **Multi-hop routing** - Direct Overworld ↔ CM only
- ❌ **Smart routing** - Face config is manual
- ❌ **Auto-detection** - Player configures everything
- ❌ **NBT preservation for items** - Items transport without NBT for MVP

### Polish Features
- ❌ **Custom textures** - Purple/black checkerboard is fine for MVP
- ❌ **Fancy GUIs** - Minimal functional GUI only
- ❌ **Localization** - English only for MVP
- ❌ **Tooltips** - Basic or none
- ❌ **Sounds** - No custom sounds
- ❌ **Particles** - No visual effects

---

## 🎯 MVP Success Criteria

**The MVP is complete when:**

1. ✅ **Face config works**: Can configure faces, configs save to NBT
2. ✅ **Transport works**: Resources move Overworld ↔ CM dimension
3. ✅ **Rate measurement works**: SIMULATING state records accurate rates
4. ✅ **Cached production works**: CACHED state simulates using fractional math
5. ✅ **Chunks unload**: CM dimension chunks unload during CACHED (verify in F3 screen)
6. ✅ **No crashes**: System is stable for 10+ minute test
7. ✅ **Player control works**: Wrench toggles states correctly

**Specific Test Case**:
```
Setup:
- Build furnace array in CM room (5 furnaces)
- Place Importer #1 next to furnace coal input
- Place Importer #2 next to furnace iron ore input
- Place Exporter #1 next to furnace iron ingot output
- Configure PreFab faces:
  - NORTH face = PULL ITEMS → Importer #1 (coal)
  - SOUTH face = PULL ITEMS → Importer #2 (iron ore)
  - EAST face = PUSH ITEMS ← Exporter #1 (iron ingots)
- Place chests in Overworld:
  - North: Coal chest
  - South: Iron ore chest
  - East: Empty output chest

Test:
1. Start SIMULATING (wrench click)
2. Wait 600 ticks (~30 seconds)
3. Finish SIMULATING (wrench click)
4. Verify CM chunks unload (F3 screen)
5. Add more coal and iron ore to input chests
6. Verify iron ingots appear in output chest at correct rate
7. Check logs: Fractional accumulator working correctly

Success:
- Iron ingots produced at cached rate
- CM dimension chunks NOT loaded
- No crashes or errors
```

---

## 📏 MVP Boundaries

### Minimum Viable (Absolute Core)
If time is extremely limited, implement ONLY:
1. Face config data structures
2. Transport logic (items only, hardcoded PULL/PUSH for testing)
3. Rate measurement
4. Cached production with fractional math
5. Wrench state control

**This proves the concept works!** Everything else is polish.

### Ideal MVP (Recommended Scope)
Add to minimum viable:
1. Face configuration GUI (makes it usable)
2. Fluids transport (proves generality)
3. Energy transport (proves generality)
4. Status display (debugging aid)

**This makes it actually usable by players.**

### MVP+ (Nice to Have, But Not Required)
Add to ideal MVP:
1. Better textures (not purple/black)
2. Tooltips on PreFab item
3. Better error messages
4. Command to dump PreFab state (`/fps_dump`)

**This makes it polished, but not essential.**

---

## 🚫 Common Scope Creep to Avoid

**Don't implement these during MVP** (even if they seem easy):

### "Just One More Feature"
- ❌ "Let me add AE2 support, it's just one class" → NO, post-MVP
- ❌ "Item filtering is easy to add" → NO, test without filters first
- ❌ "Let me add the Controller block now" → NO, prove single PreFab works first
- ❌ "Network visualization would help debugging" → NO, use logs/F3 for MVP

### "Better UX"
- ❌ Custom GUI widgets → Plain buttons are fine
- ❌ Fancy tooltips → Basic text is fine
- ❌ Custom textures → Purple checkerboard is fine
- ❌ Sound effects → Silence is fine
- ❌ Particle effects → No visuals needed

### "Edge Cases"
- ❌ What if player breaks PreFab during SIMULATING? → Document it, don't fix
- ❌ What if CM dimension is deleted? → Error message, don't recover
- ❌ What if two PreFabs link to same room? → Don't support it yet
- ❌ What if player has 100 PreFabs? → Test with 1-2 only

### "Integration Testing"
- ❌ Test with AE2 installed → Not needed for MVP
- ❌ Test with Create mod → Not needed
- ❌ Test with shader mods → Not needed
- ❌ Test on servers → Single player is fine for MVP

---

## 🎓 Learning from MVP

**After MVP is complete, evaluate:**

1. **Does caching actually improve performance?**
   - Measure TPS before/after unloading chunks
   - If no improvement, reevaluate entire concept

2. **Is fractional math accurate?**
   - Compare cached production to actual production
   - If rates diverge, fix math before adding features

3. **Do players understand face configuration?**
   - Is GUI intuitive?
   - Do they configure faces correctly?
   - If not, improve UX before adding complexity

4. **What breaks first?**
   - Coordinate mapping?
   - NBT serialization?
   - Chunk loading/unloading?
   - Fix brittle areas before expanding

**Only proceed to post-MVP features if:**
- ✅ MVP is rock-solid stable
- ✅ Performance gains are measurable
- ✅ Math is accurate
- ✅ No known critical bugs

---

## 📋 MVP Checklist

Use this to stay focused:

### Phase 1: Data Structures
- [ ] `FaceMode` enum created
- [ ] `ResourceFilter` enum created
- [ ] `FaceConfig` class created
- [ ] PreFabBlockEntity stores `Map<Direction, FaceConfig>`
- [ ] NBT serialization works
- [ ] Test: Break/place PreFab, configs preserved

### Phase 2: Transport (Hardcoded Config)
- [ ] `ResourceTransporter` class created
- [ ] `transportItems()` method implemented
- [ ] PreFabBlockEntity ticks faces
- [ ] Coordinate mapping works (Overworld ↔ CM)
- [ ] Test: Items move from Overworld chest to CM chest

### Phase 3: Rate Measurement
- [ ] `MachineState` enum added to PreFabBlockEntity
- [ ] Rate tracking fields added (itemRates, fluidRates, energyRate)
- [ ] SIMULATING state counts resources
- [ ] Transition SIMULATING → CACHED calculates rates
- [ ] Test: Rates logged correctly after simulation

### Phase 4: Cached Production
- [ ] Fractional accumulator fields added
- [ ] CACHED state accumulates rates
- [ ] Transport when accumulator >= 1.0
- [ ] Cache breaking detection (input starved / output blocked)
- [ ] Test: Production continues while CM chunks unloaded

### Phase 5: Wrench Control
- [ ] SimulationWrenchItem detects PreFab blocks
- [ ] State transitions implemented (BUILDING → SIMULATING → CACHED)
- [ ] Player messages shown
- [ ] Chunk loading/unloading triggered
- [ ] Test: Wrench toggles states correctly

### Phase 6: Face Config GUI (Optional for MVP)
- [ ] PreFabConfigScreen created
- [ ] PreFabConfigMenu created
- [ ] FaceConfigPacket created
- [ ] GUI opens on Shift+Right-click
- [ ] Configs sync to server
- [ ] Test: Can configure faces via GUI

### Phase 7: Status Display (Optional for MVP)
- [ ] Right-click without wrench shows status
- [ ] Display current state
- [ ] Display configured faces
- [ ] Display current rates
- [ ] Test: Status accurate

---

## 🎯 Absolute Minimum for Proof of Concept

**If you need to cut scope to the bone:**

1. ✅ Hardcode face config (skip GUI entirely)
2. ✅ Implement transport
3. ✅ Implement rate measurement
4. ✅ Implement cached production
5. ✅ Verify chunks unload

**This proves the caching concept works!** Everything else can be added later.

---

**Remember**: The goal is **proof that caching works**, not a polished product. Resist scope creep!
