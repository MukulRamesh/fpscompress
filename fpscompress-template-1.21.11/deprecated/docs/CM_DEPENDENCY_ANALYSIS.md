# Compact Machines Dependency Analysis

**Date**: 2026-03-24
**CM Version**: 7.0.81 (NeoForge 1.21.1)
**JAR Location**: `fpscompress-template-1.21.11/libs/compactmachines-neoforge-7.0.81.jar`

---

## Key Findings

### 1. BlockEntity Structure

**Main Target**: `dev.compactmods.machines.machine.block.BoundCompactMachineBlockEntity`

```java
public class BoundCompactMachineBlockEntity extends BlockEntity {
    protected UUID owner;           // Machine owner
    private String roomCode;        // Room identifier (CRITICAL!)
    private Component customName;   // Custom display name

    // ... methods
}
```

**Key Fields for FPSCompress**:
- `roomCode` (String) - The unique identifier for this machine's interior dimension space
- This is what Dev 2 (ICMInterceptor) will use to control chunk loading

**Block Classes**:
- `BoundCompactMachineBlock` - The physical block in Overworld (what we attach capabilities to)
- `UnboundCompactMachineBlock` / `UnboundCompactMachineEntity` - Unbound state (before placement)
- `CompactMachineBlock` - Base class

### 2. Data Components (NeoForge 1.21 Pattern)

CM uses modern Data Components instead of NBT:

```java
// From CMDataComponents (referenced but not in JAR)
BOUND_ROOM_CODE    // DeferredHolder<DataComponentType<String>>
MACHINE_COLOR      // DeferredHolder<DataComponentType<MachineColor>>
```

**Important**: CM's API classes are **not included** in the main JAR. The classes reference:
- `dev.compactmods.machines.api.machine.block.IBoundCompactMachineBlockEntity`
- `dev.compactmods.machines.api.component.CMDataComponents`
- `dev.compactmods.machines.api.attachment.CMDataAttachments`

But these are **not present** in the JAR we have. This means:
- CM's API might be in a separate artifact (not needed for our use case)
- We can work directly with the concrete `BoundCompactMachineBlockEntity` class
- No API compatibility constraints

### 3. Room System

**Relevant Classes**:
- `dev.compactmods.machines.room.RoomHelper` - Utility for room operations
- `dev.compactmods.machines.room.RoomRegistrarData` - Room registration/tracking
- `dev.compactmods.machines.dimension.Dimension` - CM's void dimension

**For Dev 2 (Chunk Manager)**:
- The `roomCode` field in BlockEntity is the key to identify which room to load/unload
- CM dimension management is in `dev.compactmods.machines.dimension.*`

### 4. Package Structure

```
dev/compactmods/machines/
├── command/          # Commands for room management
├── dimension/        # Dimension handling (void dimension, teleportation)
├── feature/          # Feature flags and packs
├── i18n/             # Translations
├── machine/          # Machine blocks and items
│   ├── block/        # BoundCompactMachineBlock(Entity)
│   └── item/         # BoundCompactMachineItem, UnboundCompactMachineItem
├── mixin/            # Mixins for Minecraft integration
├── player/           # Player tracking when entering/exiting machines
└── room/             # Room management, upgrades, UI
```

---

## Implementation Strategy for Dev 1

### Target BlockEntity for Capability Attachment

```java
// In RegisterCapabilitiesEvent
event.registerBlockEntity(
    // Capability types (IItemHandler, IFluidHandler, IEnergyStorage)
    CapabilityType.ITEM,
    // Target: CM's BoundCompactMachineBlockEntity
    Machines.BlockEntities.MACHINE.get(),  // or get the type reference
    // Provider: Our virtual buffer implementation
    (blockEntity, context) -> {
        if (blockEntity instanceof BoundCompactMachineBlockEntity cmBE) {
            // Check if TPS upgrade is installed
            // Return our virtual buffer capability
        }
        return null;
    }
);
```

### Identifying Upgraded Machines

We need a way to mark that a CM has our TPS upgrade installed. Options:

**Option A: Custom Data Component on the BlockEntity**
- Create `FPSCompressDataComponents.TPS_UPGRADE_INSTALLED` (boolean)
- Attach when player uses `tps_cache_upgrade` item
- Persists when broken and moved

**Option B: Data Attachment (NeoForge 1.21)**
- Use `BlockEntity.setData()` / `getData()` (seen in CM's code)
- Cleaner than components if we don't need item persistence

**Recommended**: Use Data Component so upgraded status persists on the item drop.

### Room Code Access

```java
// Get room code from CM BlockEntity
BoundCompactMachineBlockEntity cmBE = ...;
String roomCode = cmBE.roomCode;  // Direct field access (protected)
// OR use getter if available (need to check)
```

**Important**: `roomCode` is `private` in BoundCompactMachineBlockEntity. We may need:
- Reflection (not ideal)
- Access Transformer (better)
- Or find a public getter method

---

## Critical Notes for Integration

### For Dev 1 (Capability Attachment)
- **Target**: `BoundCompactMachineBlockEntity` class
- **Method**: `RegisterCapabilitiesEvent` to attach virtual buffers
- **Storage**: Use Data Components to mark TPS upgrade status

### For Dev 2 (Chunk Manager)
- **Key Data**: `roomCode` field from BlockEntity
- **CM Dimension**: Access via `Dimension` class or `ServerLevel` for CM dimension
- **Challenge**: Need to access private `roomCode` field (AT or reflection)

### For Dev 4 (Anti-Cheat Scanner)
- **Room Bounds**: Will need to determine BoundingBox from `roomCode`
- **Dimension**: CM's void dimension is where room interiors exist

---

## Build Configuration

**Already Configured** in `build.gradle:134`:
```gradle
compileOnly files("libs/compactmachines-neoforge-7.0.81.jar")
```

**For Runtime Testing** (uncomment if needed):
```gradle
// localRuntime files("libs/compactmachines-neoforge-7.0.81.jar")
```

---

## Next Steps

1. **Access Transformer (if needed)**:
   - Create `src/main/resources/META-INF/accesstransformer.cfg`
   - Make `roomCode` field accessible:
     ```
     public-f dev.compactmods.machines.machine.block.BoundCompactMachineBlockEntity roomCode
     ```

2. **Create Data Component**:
   - `FPSCompressDataComponents.TPS_UPGRADE_INSTALLED`
   - Use `Codec.BOOL` and `ByteBufCodecs.BOOL` for serialization

3. **Register Capabilities**:
   - Listen to `RegisterCapabilitiesEvent`
   - Attach `IItemHandler`, `IFluidHandler`, `IEnergyStorage` to upgraded CMs

4. **Create TPS Upgrade Item**:
   - `tps_cache_upgrade` item with custom use logic
   - Right-click on CM block to install upgrade
   - Mark BlockEntity with data component

---

## Questions & Risks

### ❓ Open Questions
1. Is there a public getter for `roomCode`? (Need to check methods)
2. How does CM handle chunk loading? (May need to investigate `RoomHelper`)
3. Are there events we can hook for room creation/destruction?

### ⚠️ Risks
1. **Private Field Access**: `roomCode` is private - may need AT
2. **API Changes**: CM is in beta (7.0.81) - API may change
3. **Room System Complexity**: CM's room management might have edge cases

---

## Summary

✅ **Ready to Implement**:
- We identified the target BlockEntity: `BoundCompactMachineBlockEntity`
- We understand CM's Data Component pattern
- We know where the `roomCode` field is (critical for chunk management)

🛠️ **Tools Needed**:
- Access Transformer (if `roomCode` lacks public getter)
- Custom Data Component for upgrade tracking
- Capability registration for virtual buffers

🎯 **Dev 1 Can Now Proceed** with:
1. Creating `tps_cache_upgrade` item
2. Registering data components
3. Attaching capabilities to CM BlockEntities
