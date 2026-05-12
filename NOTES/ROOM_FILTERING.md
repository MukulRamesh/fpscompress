# Room-Based Importer/Exporter Filtering

**Status**: Post-MVP Feature (Deferred from Phase 2)
**Priority**: HIGH 🔥
**Complexity**: MEDIUM (2-4 hours)

---

## Problem Statement

**Current Behavior:**
- PreFab GUI shows ALL Importers/Exporters in the entire CM dimension
- Player in Overworld with PreFab linked to Room A sees Importers from Room B, C, D, etc.
- No way to know which Importer belongs to which factory

**Desired Behavior:**
- PreFab should only see Importers/Exporters in ITS linked room
- PreFab in Overworld linked to Room A → sees only Room A's gates
- PreFab in CM Room B → sees only Room B's gates

**User Impact:**
- Large factories with many rooms become cluttered in GUI
- Easy to accidentally link to wrong room's Importer
- UX confusion: "Why am I seeing these unrelated gates?"

---

## Why Scanning Won't Work

**Initial Idea (WRONG)**: Scan for nearby CM machine block when placing Importer/Exporter

**Why it fails:**
- ❌ CM machine blocks are placed in the **Overworld**, not the CM dimension
- ❌ CM dimension contains only the factory room itself (walls, machines, player-placed blocks)
- ❌ No CM block metadata exists inside the dimension to query

---

## Proposed Solution: Player Context Stack (FILO)

### Core Concept

Track which CM room each player is currently in using a **per-player stack**:
- When player enters CM room → **push** roomCode onto their stack
- When player places Importer/Exporter → **peek** top of stack, store roomCode
- When player exits CM room → **pop** from stack

**Why FILO (stack)?**
- Handles **nested PreFabs** (PreFab inside a PreFab inside a PreFab)
- Player enters Room A → stack: `["roomA"]`
- Player enters Room B (nested in A) → stack: `["roomA", "roomB"]`
- Player places Importer → reads `"roomB"` (top of stack)
- Player exits to Room A → stack: `["roomA"]`
- Player exits to Overworld → stack: `[]`

---

## Implementation Plan

### Step 1: Create Player Room Context Registry

**File**: `PlayerRoomContext.java` (new)

```java
public class PlayerRoomContext {
    // Per-player room stack: UUID → Stack<String>
    private static final Map<UUID, Deque<String>> PLAYER_STACKS = new ConcurrentHashMap<>();

    /**
     * Player entered a CM room - push roomCode onto their stack.
     */
    public static void enterRoom(UUID playerUUID, String roomCode) {
        PLAYER_STACKS.computeIfAbsent(playerUUID, k -> new ArrayDeque<>()).push(roomCode);
        LOGGER.info("Player {} entered room {} (stack depth: {})",
            playerUUID, roomCode, PLAYER_STACKS.get(playerUUID).size());
    }

    /**
     * Player exited a CM room - pop from their stack.
     */
    public static void exitRoom(UUID playerUUID) {
        Deque<String> stack = PLAYER_STACKS.get(playerUUID);
        if (stack != null && !stack.isEmpty()) {
            String roomCode = stack.pop();
            LOGGER.info("Player {} exited room {} (stack depth: {})",
                playerUUID, roomCode, stack.size());
            if (stack.isEmpty()) {
                PLAYER_STACKS.remove(playerUUID);
            }
        }
    }

    /**
     * Get the current room the player is in (top of stack).
     * Returns null if player is not in any CM room.
     */
    @Nullable
    public static String getCurrentRoom(UUID playerUUID) {
        Deque<String> stack = PLAYER_STACKS.get(playerUUID);
        return (stack != null && !stack.isEmpty()) ? stack.peek() : null;
    }

    /**
     * Clear all stacks (called on server stop).
     */
    public static void clearAll() {
        PLAYER_STACKS.clear();
    }
}
```

---

### Step 2: Hook into Teleportation Events

**Detect Room Entry:**

**File**: `DimensionTeleportListener.java` (existing) or `PrefabBlock.java` teleport methods

When player teleports **INTO** CM dimension:
```java
// After successful teleport to CM dimension
PlayerRoomContext.enterRoom(player.getUUID(), roomCode);
```

**Locations to hook:**
- `PrefabBlock.teleportPlayerToCMRoom()` - when using PSD on PreFab
- `DimensionTeleportListener` - when entering via CM machine block
- Any other teleport entry points

**Detect Room Exit:**

When player teleports **OUT OF** CM dimension:
```java
// When player leaves CM dimension (any method)
if (fromDimension == CM_DIMENSION && toDimension != CM_DIMENSION) {
    PlayerRoomContext.exitRoom(player.getUUID());
}
```

**Use existing teleport listener** (`DimensionTeleportListener.onPlayerChangedDimension`) to detect exits.

---

### Step 3: Store Room Code in Importers/Exporters

**Add field to ImporterBlockEntity/ExporterBlockEntity:**

```java
// Room code this Importer belongs to (null if placed outside CM or before feature)
@Nullable
private String roomCode;
```

**Set room code on placement:**

```java
@Override
public void onLoad() {
    super.onLoad();

    // Detect room code on first load (if not already set)
    if (roomCode == null && level != null && !level.isClientSide()) {
        // Get the placer's UUID from context (need to pass during placement)
        // OR: Use last player in chunk as heuristic
        // OR: Use PlayerInteractEvent to capture placer UUID

        // For now, use a simpler approach: scan PlayerRoomContext for any player
        // This works if only one player is in the room during placement
        UUID placerUUID = getPlacerUUID(); // Need to implement
        if (placerUUID != null) {
            this.roomCode = PlayerRoomContext.getCurrentRoom(placerUUID);
        }
    }

    // Register with room code
    ImporterExporterRegistry.registerImporter(importerUUID, getBlockPos(), getDisplayName(), roomCode);
}
```

**Challenge**: Getting placer UUID during `onLoad()` is tricky. Better approach:

**Alternative: Capture on placement event**

Listen to `BlockEntityEvent.Load` or use `BlockItem.place()` override:
```java
// In ImporterBlock or custom BlockItem
@Override
protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    if (!level.isClientSide() && !movedByPiston) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ImporterBlockEntity importer) {
            // Get placer UUID from thread-local context or scan nearby players
            ServerPlayer nearestPlayer = getNearestPlayer(level, pos);
            if (nearestPlayer != null) {
                String currentRoom = PlayerRoomContext.getCurrentRoom(nearestPlayer.getUUID());
                importer.setRoomCode(currentRoom);
            }
        }
    }
    super.onPlace(state, level, pos, oldState, movedByPiston);
}
```

---

### Step 4: Update Registry to Track Room

**ImporterExporterRegistry changes:**

```java
// Entry now includes roomCode
public record Entry(UUID uuid, BlockPos pos, String displayName, @Nullable String roomCode) { }

// Register with room
public static void registerImporter(UUID uuid, BlockPos pos, String displayName, @Nullable String roomCode) {
    IMPORTERS.put(uuid, new Entry(uuid, pos, displayName, roomCode));
}

// Filter by room
public static List<Entry> getAllImporters(MinecraftServer server, @Nullable String roomCodeFilter) {
    if (roomCodeFilter == null) {
        return new ArrayList<>(IMPORTERS.values()); // No filter
    }
    return IMPORTERS.values().stream()
        .filter(entry -> roomCodeFilter.equals(entry.roomCode()))
        .collect(Collectors.toList());
}
```

---

### Step 5: Update GUI to Filter by PreFab's Room

**SimulationWrenchItem packet changes:**

When opening GUI, send PreFab's roomCode:
```java
serverPlayer.openMenu(prefab, buf -> {
    buf.writeBlockPos(context.getClickedPos());
    buf.writeByte(context.getClickedFace().get3DDataValue());

    // Get PreFab's room code
    String prefabRoomCode = prefab.getRoomCode();
    buf.writeBoolean(prefabRoomCode != null);
    if (prefabRoomCode != null) {
        buf.writeUtf(prefabRoomCode);
    }

    // Query registry with room filter
    List<Entry> importers = ImporterExporterRegistry.getAllImporters(server, prefabRoomCode);
    List<Entry> exporters = ImporterExporterRegistry.getAllExporters(server, prefabRoomCode);

    // ... write lists as before
});
```

**PreFabConfigMenu reads room filter:**
```java
public PreFabConfigMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
    BlockPos pos = extraData.readBlockPos();
    Direction face = Direction.from3DDataValue(extraData.readByte());

    // Read PreFab's room code
    String roomCodeFilter = extraData.readBoolean() ? extraData.readUtf() : null;

    // Read filtered lists
    ImportExportLists lists = readImporterExporterLists(extraData);

    this(containerId, playerInventory, pos, face, lists);
}
```

---

## Edge Cases & Considerations

### 1. **Player Disconnects While in CM Room**

**Problem**: Stack not popped if player disconnects/crashes while in CM dimension

**Solution**: Listen to `PlayerLoggedOutEvent` and clear their stack:
```java
@SubscribeEvent
public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
    PlayerRoomContext.clearPlayer(event.getEntity().getUUID());
}
```

### 2. **Importers Placed Before Feature Implemented**

**Problem**: Old Importers have `roomCode = null`

**Solution**: Show them in ALL PreFabs with a warning:
```java
if (entry.roomCode() == null) {
    // Legacy Importer - show with "(Legacy)" suffix
    displayName = entry.displayName() + " §7(Legacy)";
}
```

Or: Migration command to scan and assign room codes retroactively.

### 3. **Player Teleports via `/tp` Command**

**Problem**: Bypasses normal teleport hooks, stack gets desynced

**Solution**:
- Listen to `EntityTeleportEvent.TeleportCommand`
- Compare from/to dimensions and update stack accordingly
- Add `/fpscompress:fixstack` debug command to reset player's stack

### 4. **PreFab Inside PreFab Inside PreFab (Deep Nesting)**

**Problem**: Stack could grow very deep if player builds nested factories

**Solution**:
- Stack naturally handles this (that's why we use FILO!)
- Add safety check: max stack depth = 10 (warn if exceeded)

### 5. **Server Restart While Player in CM**

**Problem**: Stacks are in-memory, lost on restart

**Solution**:
- On player login, detect if they're in CM dimension
- Query their position to determine room (fallback to position calculation. if position calculation is not possible, then try shunting the player back to their spawn? also consider graceful shutdown of server; maybe we could store the data in a file)
- Initialize stack with current room

---

## Testing Plan

### Test 1: Basic Room Filtering
1. Create 2 PreFabs (Room A, Room B) in Overworld
2. Enter Room A, place 2 Importers (Apple, Iron)
3. Enter Room B, place 2 Importers (Gold, Diamond)
4. Open PreFab A GUI → should see only Apple + Iron Importers
5. Open PreFab B GUI → should see only Gold + Diamond Importers

### Test 2: Nested PreFabs
1. Place PreFab A in Overworld
2. Enter Room A, place PreFab B inside
3. Enter Room B (nested), place Importer
4. Open PreFab B GUI → should see only Room B's Importer
5. Exit to Room A, open PreFab A GUI → should see Room A's Importers (not B's)

### Test 3: Stack Pop on Exit
1. Enter Room A
2. Place Importer in Room A
3. Exit to Overworld
4. Re-enter Room A
5. Importer should still be assigned to Room A

### Test 4: Legacy Importers
1. Before implementing feature, place Importer (roomCode = null)
2. Implement feature
3. Open PreFab GUI → should show "(Legacy)" Importer in all rooms

---

## Performance Considerations

**Memory Usage:**
- One `Deque<String>` per online player
- Average: 16 bytes per stack entry
- 100 players in nested rooms (depth 3) = ~5KB total
- **Negligible**

**Lookup Performance:**
- Registry filtering: O(n) where n = total Importers
- With 1000 Importers across 100 rooms: ~10ms worst case
- **Acceptable** (happens only on GUI open, not per-tick)

**Optimization (if needed):**
- Index registry by room: `Map<String, List<Entry>>`
- Reduces filtering to O(1) lookup

---

## Migration Path (Post-MVP)

### Phase 1: Stack Infrastructure
- Implement `PlayerRoomContext` registry
- Hook teleportation events
- Add logging to verify stack push/pop works

### Phase 2: Importer/Exporter Room Tracking
- Add `roomCode` field to block entities
- Capture placer UUID and current room on placement
- Update registry to store room codes

### Phase 3: GUI Filtering
- Pass PreFab's roomCode in packet
- Filter registry queries by room
- Update GUI to show room filter status

### Phase 4: Polish & Edge Cases
- Handle disconnects, restarts, teleport commands
- Add "(Legacy)" labels for old Importers
- Add debug commands for stack inspection

---

## Alternative Approaches (Rejected)

### Approach 1: Calculate Room from Position
**Idea**: Reverse-engineer CM's room grid layout to calculate roomCode from coordinates

**Pros**: No need to track player context

**Cons**:
- Requires understanding CM's internal room layout algorithm
- Brittle (breaks if CM changes layout)
- Doesn't work if CM uses non-grid layouts

### Approach 2: Store Room in Block NBT Only
**Idea**: Don't use player stack, just prompt player for room when placing

**Pros**: Simple, explicit

**Cons**:
- Terrible UX (player must type room code every time)
- Doesn't handle nested PreFabs
- Player may not know room code

---

## Conclusion

**Recommended Approach**: Player Context Stack (FILO)

**Estimated Effort**: 2-4 hours for complete implementation + testing

**Risk Level**: LOW
- No breaking changes to existing data
- Gracefully handles legacy Importers
- Easy to rollback (just remove filtering)

**User Benefit**: HIGH
- Much cleaner GUI experience
- Prevents accidental wrong-room linking
- Scales well to large factories

**When to Implement**: After Phase 3 (basic transport) is validated and working.

---

## Related Files

**New Files:**
- `PlayerRoomContext.java` - Player room stack registry

**Modified Files:**
- `ImporterBlockEntity.java` - Add roomCode field
- `ExporterBlockEntity.java` - Add roomCode field
- `ImporterExporterRegistry.java` - Track roomCode in Entry, filter by room
- `ImporterBlock.java` / `ExporterBlock.java` - Capture placer UUID on placement
- `SimulationWrenchItem.java` - Send PreFab roomCode in packet
- `PreFabConfigMenu.java` - Read roomCode filter, pass to registry queries
- `DimensionTeleportListener.java` - Push/pop stack on teleports
- `PrefabBlock.java` - Push stack on PSD teleport

---

**Questions? See:** TODO_NEW.md Phase 2+ enhancements
