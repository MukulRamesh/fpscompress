# Developer API Guide

Guide for mod developers integrating with FPSCompress or building add-ons.

## Overview

FPSCompress exposes a simple API based on standard Minecraft/NeoForge capabilities. Most integrations can be achieved using existing capability systems without special APIs.

**Mod ID**: `fpscompress`
**Package**: `com.mukulramesh.fpscompress`
**Minecraft Version**: 1.21.1
**NeoForge Version**: 21.1.221+

---

## Core Concepts

### Three-Block System

FPSCompress uses three main block types:

1. **PreFabBlock** (`fpscompress:prefab_machine`)
   - Routes resources between Overworld and CM dimension
   - Manages state machine (BUILDING/SIMULATING/CACHED/HALTED)
   - No internal storage (pure conduit)

2. **ImporterBlock** (`fpscompress:importer`)
   - Input gate inside CM dimension
   - Exposes `IItemHandler` capability
   - Has 9-slot internal buffer

3. **ExporterBlock** (`fpscompress:exporter`)
   - Output gate inside CM dimension
   - Exposes `IItemHandler` capability
   - Actively pulls from adjacent machines

### Capability-Based Transport

PreFabs interact with blocks via standard NeoForge capabilities:
- `IItemHandler` - Items (current)
- `IFluidHandler` - Fluids (future)
- `IEnergyStorage` - Energy (future)

**No special API needed** - if your block exposes these capabilities, PreFabs can interact with it.

---

## Making Your Blocks Compatible

### Items (IItemHandler)

**Requirement**: Your block must expose `IItemHandler` capability

**Example** (block entity):
```java
public class MyMachineBlockEntity extends BlockEntity {
    private final ItemStackHandler inventory = new ItemStackHandler(9);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return LazyOptional.of(() -> inventory).cast();
        }
        return super.getCapability(cap, side);
    }
}
```

**PreFab interaction**:
- **PULL face**: PreFab calls `extractItem()` from your block
- **PUSH face**: PreFab calls `insertItem()` into your block

**Testing**: Place chest next to your machine - if hoppers can interact, PreFabs can too.

### Fluids (IFluidHandler) - Future

**Status**: Not yet implemented (planned for post-MVP)

**Expected API**:
```java
@Override
public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
    if (cap == ForgeCapabilities.FLUID_HANDLER) {
        return LazyOptional.of(() -> fluidTank).cast();
    }
    return super.getCapability(cap, side);
}
```

### Energy (IEnergyStorage) - Future

**Status**: Not yet implemented (planned for post-MVP)

**Expected API**:
```java
@Override
public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
    if (cap == ForgeCapabilities.ENERGY) {
        return LazyOptional.of(() -> energyStorage).cast();
    }
    return super.getCapability(cap, side);
}
```

---

## Accessing PreFab Data

### Checking if Block is a PreFab

```java
import com.mukulramesh.fpscompress.portal.PrefabBlock;
import com.mukulramesh.fpscompress.portal.PrefabBlockEntity;

// Check block type
if (level.getBlockState(pos).getBlock() instanceof PrefabBlock) {
    // It's a PreFab.
}

// Get block entity
BlockEntity be = level.getBlockEntity(pos);
if (be instanceof PrefabBlockEntity prefab) {
    // Access PreFab data
}
```

### Reading PreFab State

```java
import com.mukulramesh.fpscompress.portal.MachineState;

PrefabBlockEntity prefab = (PrefabBlockEntity) blockEntity;

// Get current state
MachineState state = prefab.getState();

switch (state) {
    case BUILDING -> {
        // PreFab is in setup mode
    }
    case SIMULATING -> {
        // PreFab is measuring rates (chunks loaded)
    }
    case CACHED -> {
        // PreFab is running virtually (chunks unloaded)
    }
    case HALTED -> {
        // PreFab has stopped due to issue
    }
}
```

### Reading Face Configuration

```java
import com.mukulramesh.fpscompress.portal.FaceConfig;
import com.mukulramesh.fpscompress.portal.FaceMode;
import com.mukulramesh.fpscompress.portal.ResourceFilter;
import net.minecraft.core.Direction;

PrefabBlockEntity prefab = (PrefabBlockEntity) blockEntity;

// Get configuration for a specific face
FaceConfig northConfig = prefab.getFaceConfig(Direction.NORTH);

if (northConfig != null) {
    FaceMode mode = northConfig.mode; // DISABLED, PULL, PUSH
    ResourceFilter filter = northConfig.resourceType; // ITEMS, FLUIDS, ENERGY, ALL
    UUID targetUUID = northConfig.targetUUID; // Linked Importer/Exporter UUID
}
```

### Reading Cached Rates (Future Feature)

```java
// Future API (not yet implemented)
Map<String, Double> rates = prefab.getCachedRates();

// Example rates:
// "minecraft:iron_ingot" -> 0.213 (produces 0.213 iron/tick)
// "minecraft:coal" -> -1.0 (consumes 1.0 coal/tick)
```

---

## Accessing Importer/Exporter Data

### Finding Importers/Exporters

```java
import com.mukulramesh.fpscompress.portal.ImporterBlockEntity;
import com.mukulramesh.fpscompress.portal.ExporterBlockEntity;

// Check if block is an Importer
if (blockEntity instanceof ImporterBlockEntity importer) {
    UUID uuid = importer.getUUID();
    ItemStack frequencyItem = importer.getFrequencyItem();
    String displayName = importer.getDisplayName(); // e.g., "Coal Importer"
}

// Check if block is an Exporter
if (blockEntity instanceof ExporterBlockEntity exporter) {
    UUID uuid = exporter.getUUID();
    ItemStack frequencyItem = exporter.getFrequencyItem();
    String displayName = exporter.getDisplayName();
}
```

### Setting Frequency Item

```java
ImporterBlockEntity importer = (ImporterBlockEntity) blockEntity;

// Set frequency (e.g., "Coal Importer")
ItemStack frequencyItem = new ItemStack(Items.COAL);
importer.setFrequencyItem(frequencyItem);

// Get frequency
ItemStack currentFrequency = importer.getFrequencyItem();
```

---

## Custom Importer/Exporter Types

**Status**: Not officially supported yet (API may change)

**Concept**: Create custom gate blocks that extend Importer/Exporter

**Potential use cases**:
- Filtered Importers (whitelist specific items)
- Priority Exporters (extract from specific slots first)
- Wireless gates (link without PreFab faces)

**Current recommendation**: Wait for stable API in post-MVP

---

## Integration Examples

### Example 1: AE2-Style Interface

**Goal**: Create block that acts as ME interface for PreFab

**Approach**:
```java
public class PreFabMEInterface extends BlockEntity {
    private final IItemHandler fakeInventory = new PreFabItemHandler();

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            // Return handler that proxies to nearby PreFab
            return LazyOptional.of(() -> fakeInventory).cast();
        }
        return super.getCapability(cap, side);
    }

    class PreFabItemHandler implements IItemHandler {
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Find adjacent PreFab
            // Extract from PreFab's linked Exporter
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // Find adjacent PreFab
            // Insert to PreFab's linked Importer
            return stack;
        }
    }
}
```

**Place between AE2 ME Interface and PreFab**:
```
[ME Interface] → [PreFab ME Interface Block] → [PreFab]
```

### Example 2: Monitoring System

**Goal**: Display PreFab status on screen

**Approach**:
```java
public class PreFabMonitor extends BlockEntity {
    public void tick() {
        // Find nearby PreFabs
        for (BlockPos pos : BlockPos.betweenClosed(this.worldPosition.offset(-5, -5, -5),
                                                     this.worldPosition.offset(5, 5, 5))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PrefabBlockEntity prefab) {
                // Read state
                MachineState state = prefab.getState();

                // Read rates (future)
                // Map<String, Double> rates = prefab.getCachedRates();

                // Display on screen (custom GUI)
                updateDisplay(prefab, state);
            }
        }
    }
}
```

### Example 3: Automation Trigger

**Goal**: Detect when PreFab enters HALTED state and send alert

**Approach**:
```java
public class PreFabWatchdog extends BlockEntity {
    private Map<BlockPos, MachineState> lastStates = new HashMap<>();

    public void tick() {
        for (BlockPos pos : trackedPreFabs) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PrefabBlockEntity prefab) {
                MachineState currentState = prefab.getState();
                MachineState lastState = lastStates.get(pos);

                if (lastState != MachineState.HALTED && currentState == MachineState.HALTED) {
                    // PreFab just entered HALTED.
                    sendAlert(pos);
                }

                lastStates.put(pos, currentState);
            }
        }
    }

    private void sendAlert(BlockPos pos) {
        // Send chat message, activate redstone, etc.
    }
}
```

---

## Events (Future API)

**Status**: Not yet implemented

**Planned events**:
- `PreFabStateChangeEvent` - Fired when PreFab changes state
- `PreFabRateMeasuredEvent` - Fired when simulation completes
- `PreFabHaltedEvent` - Fired when PreFab enters HALTED

**Example usage** (future):
```java
@SubscribeEvent
public void onPreFabStateChange(PreFabStateChangeEvent event) {
    PrefabBlockEntity prefab = event.getPrefab();
    MachineState oldState = event.getOldState();
    MachineState newState = event.getNewState();

    if (newState == MachineState.CACHED) {
        // PreFab entered CACHED mode - chunks unloaded.
    }
}
```

---

## NBT Data Format

### PreFab NBT Structure

```nbt
{
    "state": "CACHED",
    "roomCode": "compact_machine_room_12345",
    "roomCenter": {x: 100, y: 64, z: 200},
    "faces": {
        "NORTH": {
            "mode": "PULL",
            "resourceType": "ITEMS",
            "targetUUID": "abc-123-def-456"
        },
        "SOUTH": {
            "mode": "PUSH",
            "resourceType": "ITEMS",
            "targetUUID": "ghi-789-jkl-012"
        }
    },
    "cachedRates": {
        "minecraft:iron_ingot": 0.213,
        "minecraft:coal": -1.0
    },
    "accumulators": {
        "minecraft:iron_ingot": 0.85,
        "minecraft:coal": -0.42
    }
}
```

**Warning**: NBT structure may change between versions - use API methods instead of direct NBT access.

### Importer/Exporter NBT

```nbt
{
    "uuid": "abc-123-def-456",
    "frequencyItem": {id: "minecraft:coal", Count: 1},
    "inventory": {
        // Standard ItemStackHandler NBT
    }
}
```

---

## Building Add-Ons

### Add-On Ideas

**Monitoring Tools**:
- GUI showing all PreFabs and their states
- Rate display overlay
- Performance metrics (TPS saved)

**Automation**:
- Automatic restocking system
- HALTED state recovery (auto-refill chests)
- Multi-PreFab coordinator

**Visualizations**:
- Particle effects showing resource flow
- Holographic displays of rates
- 3D render of factory inside CM

### Add-On Best Practices

✅ **Use capability APIs** - Don't modify FPSCompress internals
✅ **Handle CACHED state** - Chunks may be unloaded
✅ **Respect state machine** - Don't force invalid transitions
✅ **Test with PreFab-as-Item** - Ensure NBT compatibility

❌ Don't modify PreFab states directly (use Simulation Wrench mechanics)
❌ Don't assume chunks are loaded (especially in CACHED)
❌ Don't break Importer/Exporter UUID linking

---

## FAQ for Developers

### Q: Can I access PreFab from another dimension?

**A**: Yes, but be careful:
- PreFab is in Overworld
- Importers/Exporters are in CM dimension
- Use `ServerLevel` to access different dimensions

### Q: Can I programmatically start/stop simulation?

**A**: Not officially supported yet (use Simulation Wrench item for now)

**Future API** (planned):
```java
prefab.startSimulation();
prefab.finishSimulation();
```

### Q: How do I make my machine work inside CM?

**A**: Just expose `IItemHandler` capability - no special code needed.

### Q: Can I create custom state machine states?

**A**: No - states are hardcoded (BUILDING/SIMULATING/CACHED/HALTED)

### Q: Is there a way to detect if chunks are loaded?

**A**: Check `ServerLevel.isPositionEntityTicking(BlockPos)` for CM dimension

---

## Contributing to FPSCompress

**Repository**: Check mod page for GitHub link

**Areas where contributions are welcome**:
- Bug fixes
- API improvements
- Documentation
- Testing

**Development setup**:
1. Clone repository
2. Import Gradle project
3. Run `./gradlew build`
4. See CLAUDE.md for development guidelines

---

## Version Compatibility

**Current API version**: v0.2.0
**Stability**: Alpha (API may change)

**Breaking changes planned**:
- Post-MVP: Fluid/energy capability support
- v1.0: Stable API freeze
- v2.0: Potential API redesign

**Recommendation**: Pin to specific FPSCompress version for production add-ons

---

## See Also

- [Getting Started](Getting-Started) - User guide
- [PreFab System](PreFab-System) - Core mechanics
- [GitHub Repository](https://github.com/...) - Source code (check mod page)

---

**Note**: This API is in active development. Check GitHub for latest API documentation and examples.
