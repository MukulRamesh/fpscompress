# Architecture Pivot: From Virtual Buffers to Conduit Caching

**Date**: 2026-04-28  
**Decision**: Pivot from virtual buffer storage to conduit-based caching system

---

## Why the Pivot?

### Original Design (Virtual Buffers)
**Concept**: PreFabs store items/fluids/energy in unlimited internal maps
- PreFabBlockEntity contained `Map<String, Integer>` for items, fluids, energy
- External mods inserted → stored internally → extracted later
- Complex capability routers with "smart extraction" logic
- NBT grew large with millions of stored items

**Problems**:
1. **Not aligned with mod goal**: Caching rates, not storing resources
2. **Over-engineered**: Unlimited storage not needed for rate measurement
3. **NBT bloat**: Storing millions of items creates huge save files
4. **Extraction ambiguity**: "Which item to extract when multiple types stored?"
5. **Complex code**: Smart extraction logic, capacity management (even unlimited)

### New Design (Conduit Caching)
**Concept**: PreFabs are cross-dimensional pipes that cache production rates
- PreFabs transport resources instantly (no storage)
- Measure rates during SIMULATING (chunks loaded)
- Simulate production during CACHED using math (chunks unloaded)
- Face configuration determines what each side pulls/pushes

**Benefits**:
1. ✅ **Aligned with goal**: Focus on caching, not storage
2. ✅ **Simpler code**: Transport logic < Storage + Routing logic
3. ✅ **Small NBT**: Only store face configs and rates, not resource counts
4. ✅ **No extraction ambiguity**: Resources flow through, not stored
5. ✅ **Performance**: Chunks unloaded = TPS savings (the whole point!)

---

## Architectural Comparison

### Data Structures

| Old (Virtual Buffers) | New (Conduit Caching) |
|----------------------|----------------------|
| `Map<String, Integer> itemBuffer` | `Map<Direction, FaceConfig> faceConfigs` |
| `Map<String, Integer> fluidBuffer` | `Map<String, Double> itemRates` |
| `long energyBuffer` | `Map<String, Double> fluidRates` |
| `MAX_CAPACITY` constants | `double energyRate` |
| Smart extraction logic | Fractional accumulators |

**NBT Size Example**:
- Old: Storing 1M iron ingots = "minecraft:iron_ingot" + count (1000000) = ~40 bytes per resource type × types
- New: Face config = mode (1 byte) + filter (1 byte) × 6 faces = 12 bytes + rates (~8 bytes per resource type)

### Capability Behavior

| Old (Virtual Buffers) | New (Conduit) |
|----------------------|--------------|
| `insertItem()` → add to map | `insertItem()` → transport to CM dimension |
| `extractItem()` → smart extraction from map | `extractItem()` → transport from CM dimension |
| Check if single type before extracting | No ambiguity - face mode determines direction |
| Return unlimited capacity | Return passthrough capacity |

### State Machine

| Old | New |
|-----|-----|
| BUILDING → SIMULATING → CACHED | Same, but simpler! |
| CACHED: Extract from virtual → push to math → pull from math → insert to virtual | CACHED: Accumulate fractional rates → transport when >= 1.0 |
| HALTED: Virtual buffer issues | HALTED: Input starved or output blocked |

---

## Code Migration Guide

### Files to Delete
```
portal/VirtualBufferStorage.java        # Was: Unlimited storage maps
capabilities/VirtualItemHandler.java    # Was: Smart extraction wrapper
capabilities/VirtualFluidHandler.java   # Was: Fluid storage wrapper
capabilities/VirtualEnergyStorage.java  # Was: Energy storage wrapper
spatial/CapabilityRouter.java           # Was: Complex physical/virtual routing

# Testing files for old system
debug/BufferTestCommand.java
TESTING_CAPABILITY_REGISTRATION.md
TESTING_QUICK_START.md
STORAGE_VIEWER_FEATURE.md
TEST_BUFFER_CAPACITY.md
```

### Files to Modify

**portal/PreFabBlockEntity.java**:
```java
// OLD:
private VirtualBufferStorage storage;
public int addToBuffer(ResourceType type, String resourceId, int amount) {
    return storage.addItem(resourceId, amount);
}
public Map<String, Integer> getItemSnapshot() {
    return storage.getItemSnapshot();
}

// NEW:
private Map<Direction, FaceConfig> faceConfigs = new EnumMap<>(Direction.class);
private Map<String, Double> itemRates = new HashMap<>();
private Map<String, Double> itemAccumulators = new HashMap<>();

@Override
public void tick(Level level, BlockPos pos, BlockState state, PreFabBlockEntity be) {
    if (this.state == MachineState.CACHED) {
        // Fractional production
        for (Map.Entry<String, Double> entry : itemRates.entrySet()) {
            String itemId = entry.getKey();
            double rate = entry.getValue();
            
            double accum = itemAccumulators.getOrDefault(itemId, 0.0);
            accum += rate;
            
            if (accum >= 1.0) {
                int whole = (int) accum;
                accum -= whole;
                
                // Push to Overworld
                ResourceTransporter.transportItems(this, itemId, whole);
            }
            
            itemAccumulators.put(itemId, accum);
        }
    } else if (this.state == MachineState.SIMULATING) {
        // Measure actual transport
        // (handled in ResourceTransporter callbacks)
    }
}
```

**portal/IVirtualMachineData.java** → **portal/IPreFabData.java**:
```java
// OLD:
public interface IVirtualMachineData {
    int addToBuffer(ResourceType type, String resourceId, int amount);
    int extractFromBuffer(ResourceType type, String resourceId, int amount);
    int getBufferAmount(ResourceType type, String resourceId);
    Map<String, Integer> getItemSnapshot();
}

// NEW:
public interface IPreFabData {
    FaceConfig getFaceConfig(Direction face);
    void setFaceConfig(Direction face, FaceConfig config);
    MachineState getState();
    void setState(MachineState state);
    Map<String, Double> getRates(ResourceType type);
}
```

**portal/CapabilityRegistration.java**:
```java
// OLD:
event.registerBlockEntity(
    Capabilities.ItemHandler.BLOCK,
    FPSCompress.PREFAB_BE.get(),
    (blockEntity, context) -> {
        CMInterceptorImpl interceptor = new CMInterceptorImpl();
        interceptor.setRoutingState(true);
        return new CapabilityRouter.ItemHandlerRouter(null, prefab, interceptor);
    }
);

// NEW:
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

### New Files to Create

**portal/FaceConfig.java**:
```java
public class FaceConfig {
    private FaceMode mode = FaceMode.DISABLED;
    private ResourceFilter resourceType = ResourceFilter.ALL;
    
    public void saveToNBT(CompoundTag tag) {
        tag.putString("mode", mode.name());
        tag.putString("resource", resourceType.name());
    }
    
    public static FaceConfig loadFromNBT(CompoundTag tag) {
        FaceConfig config = new FaceConfig();
        config.mode = FaceMode.valueOf(tag.getString("mode"));
        config.resourceType = ResourceFilter.valueOf(tag.getString("resource"));
        return config;
    }
}
```

**portal/ResourceTransporter.java**:
```java
public class ResourceTransporter {
    public static int transportItems(PreFabBlockEntity prefab, Direction face, int maxAmount) {
        FaceConfig config = prefab.getFaceConfig(face);
        
        if (config.mode == FaceMode.PULL) {
            // Extract from Overworld → Insert to CM
            BlockPos overworldPos = prefab.getBlockPos().relative(face);
            BlockEntity overworldBE = prefab.getLevel().getBlockEntity(overworldPos);
            IItemHandler overworldHandler = overworldBE.getCapability(Capabilities.ItemHandler.BLOCK);
            
            ItemStack extracted = overworldHandler.extractItem(0, maxAmount, false);
            if (!extracted.isEmpty()) {
                BlockPos cmTarget = mapOverworldPosToCM(prefab, face);
                ServerLevel cmLevel = getCMLevel(prefab);
                BlockEntity cmBE = cmLevel.getBlockEntity(cmTarget);
                IItemHandler cmHandler = cmBE.getCapability(Capabilities.ItemHandler.BLOCK);
                
                ItemStack remainder = cmHandler.insertItem(0, extracted, false);
                int transferred = extracted.getCount() - remainder.getCount();
                
                // Track for rate measurement
                if (prefab.getState() == MachineState.SIMULATING) {
                    prefab.recordItemTransfer(extracted.getItem().toString(), transferred);
                }
                
                return transferred;
            }
        } else if (config.mode == FaceMode.PUSH) {
            // Extract from CM → Insert to Overworld
            // (inverse of above)
        }
        
        return 0;
    }
}
```

---

## Migration Steps

### Step 1: Document Review (Done)
- ✅ Created ARCHITECTURE_CONDUIT.md
- ✅ Updated CLAUDE.md
- ✅ Created TODO.md
- ✅ Created ARCHITECTURE_PIVOT.md (this file)

### Step 2: Clean Up Old Code
**Status**: ✅ **COMPLETE** - All deprecated files deleted from project (2026-05-12)

Previously archived (2026-04-28), now deleted (preserved in git history):
- VirtualBufferStorage.java
- Virtual*.java (ItemHandler, FluidHandler, EnergyStorage)
- CapabilityRouter.java
- BufferTestCommand.java
- All TESTING_*.md docs
- All deprecated documentation (24 files total)

See commit `11737a0` in git history to restore if needed.

### Step 3: Implement New Architecture
Follow TODO.md phases:
1. Phase 1: Face config data structures
2. Phase 2: Basic transport (hardcoded config)
3. Phase 3: Rate measurement
4. Phase 4: Cached production
5. Phase 5: Wrench control
6. Phase 6: Face config GUI
7. Phase 7: Dynamic capabilities

### Step 4: Testing
Follow testing plan in TODO.md:
- Test 1: Face config persistence
- Test 2: Basic transport
- Test 3: Rate measurement
- Test 4: Cached production
- Test 5: Cache breaking
- Test 6: Face config GUI

---

## FAQ

### Q: What happens to existing PreFab blocks in worlds?
**A**: They'll need migration. Options:
1. Add migration code in PreFabBlockEntity.load() to detect old NBT format
2. Tell players to break and replace old PreFabs (loses data)
3. Provide migration command: `/fps_migrate`

Recommend option 3 - migration command that:
- Finds all PreFab blocks in loaded chunks
- Clears old virtual buffer data from NBT
- Sets all faces to DISABLED (player reconfigures)
- Preserves room code linkage

### Q: Can I still use hoppers with PreFabs?
**A**: Yes! Hoppers interact with PreFab faces just like any other block:
- Hopper above PULL-ITEMS face → items transport to CM dimension
- Hopper below PUSH-ITEMS face → items transport from CM dimension to hopper

### Q: What about AE2 integration?
**A**: Post-MVP feature. For MVP, PreFabs act as standalone conduits.
Later, create Factory Controller block that:
- Holds multiple PreFab items in inventory
- Exposes unified interface to AE2
- Routes resources to/from correct PreFab based on requests

### Q: Is the caching system still the same?
**A**: Yes! The caching logic is identical:
1. SIMULATING: Measure actual rates while chunks loaded
2. CACHED: Use fractional math to simulate production while chunks unloaded
3. Performance gain: Unloaded chunks = faster server

The only change is **where resources come from**:
- Old: Virtual buffers (internal storage)
- New: Adjacent blocks (direct transport)

### Q: What if I have multiple resource types?
**A**: Each face can handle multiple types independently:
- Face 1: PULL ITEMS (all items)
- Face 2: PULL FLUIDS (all fluids)
- Face 3: PUSH ITEMS (all items)
- Face 4: PUSH ENERGY

During SIMULATING, PreFab measures rates for each resource type separately.
During CACHED, fractional accumulators run in parallel for each resource.

---

## Summary

**What Changed**:
- ❌ Virtual buffer storage (unlimited maps)
- ❌ Smart extraction logic
- ❌ Buffer capacity management
- ✅ Face configuration system
- ✅ Direct cross-dimensional transport
- ✅ Fractional rate accumulators

**What Stayed the Same**:
- ✅ State machine (BUILDING/SIMULATING/CACHED/HALTED)
- ✅ Rate measurement concept
- ✅ Fractional math for sub-tick production
- ✅ Chunk loading/unloading control
- ✅ CM dimension integration

**Why This is Better**:
1. Simpler code (less logic)
2. Smaller NBT (face configs vs resource counts)
3. Better aligned with mod goal (caching, not storage)
4. No extraction ambiguity
5. Easier to understand (pipes concept familiar to players)

**Migration Path**:
1. ✅ Delete old virtual buffer code (preserved in git history)
2. ✅ Implement face config system
3. ✅ Implement transport logic
4. ✅ Implement rate measurement + caching (unchanged)
5. ✅ Implement GUI (last step)

---

**Next Steps**: See TODO.md for implementation roadmap.
