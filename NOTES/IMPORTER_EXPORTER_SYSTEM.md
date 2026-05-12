# Importer/Exporter System

**Date**: 2026-04-28  
**Purpose**: Clarify how resources enter and exit the CM dimension factory

---

## Problem with Original Design

**Original idea**: PreFab faces map directly to CM coordinates
- North face → Resource appears at south side of CM room
- Complex coordinate math: `cmPos = roomCenter.relative(oppositeFace, roomRadius)`
- Unclear to player: "Where do my resources actually go?"
- Inflexible: Fixed positions, can't adjust factory layout

**Result**: Over-engineered and confusing

---

## Solution: Importer/Exporter Blocks

### Core Concept

**Importers** = Input gates (CM dimension only)
- Player places inside CM room
- Receives resources from PreFab PULL faces
- Adjacent machines pull from Importer

**Exporters** = Output gates (CM dimension only)
- Player places inside CM room
- Pulls resources from adjacent machines
- PreFab PUSH faces pull from Exporter

**PreFab** = Router (Overworld only)
- Each face links to specific Importer/Exporter (by UUID)
- Transports resources between Overworld and linked gate

---

## How It Works

### Setup Phase

**Step 1: Build factory in CM room**
```
[Chest] → [Furnace] → [Chest]
```

**Step 2: Add Importer/Exporter blocks**
```
[Importer #1] → [Furnace input]
[Furnace output] → [Exporter #1]
```

Each Importer/Exporter gets a unique UUID on placement:
- Importer #1: UUID `abc-123`
- Exporter #1: UUID `def-456`

**Step 3: Configure PreFab faces**

Shift+Right-click PreFab with wrench → Opens GUI:
```
┌─────────────────────────────────────┐
│   Face: NORTH                       │
├─────────────────────────────────────┤
│ Mode:   [PULL]                      │
│ Filter: [ITEMS]                     │
│ Target: [Importer #1 (abc-123)] ▼  │
│         [Importer #2 (ghi-789)]     │
│         [Importer #3 (jkl-012)]     │
└─────────────────────────────────────┘
```

Player selects Importer #1 from dropdown.

**Result**: NORTH face linked to Importer #1

---

## Runtime Behavior

### PULL Mode (Overworld → Importer)

**Every tick during SIMULATING or CACHED**:

1. **Extract from Overworld**:
   ```java
   BlockPos overworldPos = prefabPos.relative(Direction.NORTH);
   BlockEntity overworldBE = level.getBlockEntity(overworldPos);
   IItemHandler handler = overworldBE.getCapability(ItemHandler.BLOCK);
   ItemStack extracted = handler.extractItem(0, 64, false);
   ```

2. **Find linked Importer**:
   ```java
   UUID targetUUID = faceConfig.targetUUID; // abc-123
   ImporterBlockEntity importer = findImporterByUUID(cmLevel, targetUUID);
   ```

3. **Insert to Importer**:
   ```java
   ItemStack remainder = importer.insertItem(extracted);
   int transferred = extracted.getCount() - remainder.getCount();
   ```

4. **Furnace pulls from Importer** (vanilla Minecraft behavior):
   - Furnace queries Importer's IItemHandler capability
   - Furnace extracts fuel/input automatically

### PUSH Mode (Exporter → Overworld)

**Every tick during SIMULATING or CACHED**:

1. **Exporter pulls from furnace**:
   ```java
   // Exporter ticks independently
   BlockEntity adjacent = level.getBlockEntity(exporterPos.relative(Direction.WEST));
   IItemHandler furnaceHandler = adjacent.getCapability(ItemHandler.BLOCK);
   ItemStack extracted = furnaceHandler.extractItem(0, 64, false);
   
   // Store in Exporter's internal buffer
   exporterBuffer.add(extracted);
   ```

2. **PreFab pulls from Exporter**:
   ```java
   UUID targetUUID = faceConfig.targetUUID; // def-456
   ExporterBlockEntity exporter = findExporterByUUID(cmLevel, targetUUID);
   ItemStack extracted = exporter.extractFromBuffer(64);
   ```

3. **Insert to Overworld**:
   ```java
   BlockPos overworldPos = prefabPos.relative(Direction.SOUTH);
   BlockEntity overworldBE = level.getBlockEntity(overworldPos);
   IItemHandler handler = overworldBE.getCapability(ItemHandler.BLOCK);
   handler.insertItem(0, extracted, false);
   ```

---

## Benefits of This Design

### For Players

✅ **Clear input/output points**: "Put Importer here, Exporter there"  
✅ **Visible gates**: Can see where resources enter/exit  
✅ **Flexible layout**: Place multiple Importers/Exporters anywhere  
✅ **Intuitive**: Physical blocks = physical gates  
✅ **Discoverable**: Break Importer → items drop (if any in buffer)  

### For Developers

✅ **No coordinate math**: UUID lookup instead of position calculation  
✅ **No room size queries**: Don't care about CM room dimensions  
✅ **Simple linking**: Face → UUID → Gate block  
✅ **Testable**: Can test Importer/Exporter independently  
✅ **Extensible**: Easy to add Importer variants (filtered, prioritized, etc.)  

### For Caching System

✅ **Rate measurement**: Count items flowing through gates  
✅ **Cache independence**: Gates work same in SIMULATING and CACHED  
✅ **No chunk loading needed**: PreFab queries gate by UUID, doesn't need to load surrounding chunks  

---

## Technical Details

### Importer Block

**ImporterBlock.java**:
- Block that can only be placed in CM dimension
- Has BlockEntity (`ImporterBlockEntity`)
- Texture: Blue border with inward arrow

**ImporterBlockEntity.java**:
```java
public class ImporterBlockEntity extends BlockEntity {
    private UUID uuid;                    // Unique identifier
    private ItemStackHandler inventory;   // Small buffer (9 slots)
    private FluidTank fluidTank;          // Small buffer (10,000 mB)
    private EnergyStorage energyStorage;  // Small buffer (100,000 FE)
    
    // Expose capabilities to adjacent blocks
    public IItemHandler getItemHandler() { return inventory; }
    public IFluidHandler getFluidHandler() { return fluidTank; }
    public IEnergyStorage getEnergyStorage() { return energyStorage; }
    
    // For PreFab to insert
    public ItemStack insertItem(ItemStack stack) {
        return inventory.insertItem(0, stack, false);
    }
}
```

**Why buffer needed?**
- Prevents instant item loss if furnace input is full
- Allows for slight timing mismatches
- Small buffer (9 slots) keeps it simple

### Exporter Block

**ExporterBlock.java**:
- Block that can only be placed in CM dimension
- Has BlockEntity (`ExporterBlockEntity`)
- Texture: Red border with outward arrow

**ExporterBlockEntity.java**:
```java
public class ExporterBlockEntity extends BlockEntity implements ITickable {
    private UUID uuid;
    private ItemStackHandler inventory;   // Small buffer (9 slots)
    private FluidTank fluidTank;
    private EnergyStorage energyStorage;
    
    @Override
    public void tick() {
        // Pull from adjacent machines
        for (Direction dir : Direction.values()) {
            BlockEntity adjacent = level.getBlockEntity(pos.relative(dir));
            if (adjacent != null) {
                IItemHandler handler = adjacent.getCapability(ItemHandler.BLOCK, dir.getOpposite());
                if (handler != null) {
                    // Extract items and store in buffer
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        ItemStack extracted = handler.extractItem(slot, 64, false);
                        if (!extracted.isEmpty()) {
                            inventory.insertItem(0, extracted, false);
                        }
                    }
                }
            }
        }
    }
    
    // For PreFab to extract
    public ItemStack extractFromBuffer(int maxAmount) {
        return inventory.extractItem(0, maxAmount, false);
    }
}
```

**Why active pulling?**
- Exporters actively query adjacent machines (not passive)
- Works with machines that don't push (like furnaces)
- Predictable behavior: "Exporter pulls, PreFab pulls from Exporter"

### UUID Lookup System

**PreFabBlockEntity.java**:
```java
private ImporterBlockEntity findImporterByUUID(ServerLevel cmLevel, UUID targetUUID) {
    // Strategy 1: Cache known positions (fast path)
    BlockPos cachedPos = importerPositionCache.get(targetUUID);
    if (cachedPos != null) {
        BlockEntity be = cmLevel.getBlockEntity(cachedPos);
        if (be instanceof ImporterBlockEntity importer && importer.getUUID().equals(targetUUID)) {
            return importer;
        }
    }
    
    // Strategy 2: Search loaded chunks (slow path)
    for (BlockEntity be : cmLevel.blockEntityList()) {
        if (be instanceof ImporterBlockEntity importer && importer.getUUID().equals(targetUUID)) {
            importerPositionCache.put(targetUUID, be.getBlockPos()); // Cache for next time
            return importer;
        }
    }
    
    return null; // Not found (Importer was broken?)
}
```

**Performance**: O(1) cached, O(n) on cache miss (n = loaded block entities, typically ~100-1000)

---

## Edge Cases

### What if Importer is broken during CACHED mode?

**Scenario**: Player enters CM dimension, breaks Importer #1

**Behavior**:
- PreFab can't find Importer by UUID
- Transport fails silently (or logs warning)
- PreFab enters HALTED state (input blocked)
- Player must place new Importer and reconfigure PreFab face

**Alternative design**: Don't allow breaking Importers/Exporters while PreFab is in CACHED mode (block break event)

### What if multiple PreFabs link to same Importer?

**Scenario**: Two PreFabs both have PULL faces → Importer #1

**Behavior**:
- Both PreFabs insert to same Importer
- Importer buffer might fill up (9 slots)
- Insertion fails → PreFab enters HALTED
- This is valid: Multiple input sources for one factory

**No special handling needed** - just works!

### What if Importer buffer is full?

**Scenario**: Furnace not pulling fast enough, Importer buffer fills up

**Behavior**:
- `importer.insertItem()` returns remainder
- PreFab counts: `transferred = 0` (nothing inserted)
- If this happens repeatedly → Cache breaks → HALTED
- Player must fix bottleneck (add more furnaces, speed up processing)

**This is correct behavior** - cache reflects reality!

---

## Comparison to Other Approaches

### Approach 1: Direct Coordinate Mapping (REJECTED)
```
PreFab NORTH face → (cmRoomCenter + (0, 0, -radius))
```
❌ Complex math  
❌ Inflexible (fixed positions)  
❌ Unclear to player  
❌ Room size dependency  

### Approach 2: Wireless Links (REJECTED)
```
Right-click Importer with "Linker" item → Right-click PreFab face
```
❌ Extra tool required  
❌ Non-obvious linking process  
❌ Hard to see what's linked to what  
✅ No GUI needed (but GUI is better)  

### Approach 3: Importer/Exporter with UUID Linking (CHOSEN)
```
Place Importer → Configure PreFab face GUI → Select from dropdown
```
✅ Simple implementation  
✅ Clear to player  
✅ Flexible layout  
✅ GUI shows available gates  
✅ Survives PreFab break/place  

---

## MVP Scope

**In MVP**:
- ✅ Importer block (items only for MVP, fluids/energy post-MVP)
- ✅ Exporter block (items only for MVP)
- ✅ UUID generation on placement
- ✅ Face → UUID linking in GUI
- ✅ Basic transport logic
- ✅ Small buffer (9 slots)

**Post-MVP**:
- ⭐ Fluid support in Importers/Exporters
- ⭐ Energy support
- ⭐ Filtered Importers (whitelist/blacklist)
- ⭐ Prioritized Exporters (extract from slot X first)
- ⭐ Importer/Exporter upgrades (larger buffer, faster transfer)
- ⭐ Visual indicators (particles showing transfer)

---

## Summary

**Three-block system**:
1. **PreFab** (Overworld) - Routes resources
2. **Importer** (CM dimension) - Input gates
3. **Exporter** (CM dimension) - Output gates

**Linking**: PreFab faces link to Importers/Exporters by UUID (configured in GUI)

**Benefits**: Clear, flexible, simple to implement, intuitive for players

**HALTED state**: CM chunks stay unloaded, player fixes Overworld side, then resumes

---

**See ARCHITECTURE_CONDUIT.md for complete technical specification.**
