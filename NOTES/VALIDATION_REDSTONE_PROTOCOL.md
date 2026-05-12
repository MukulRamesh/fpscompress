# Validation via Redstone Protocol

**Status**: Post-MVP Feature  
**Purpose**: Validate that cached factory rates represent actual production, not hidden storage  
**Date**: 2026-04-28

---

## The Problem

**Cheating with hidden storage**:
```
Player puts 1000 iron ingots in chest inside CM dimension
During SIMULATING: Factory "produces" iron (actually from chest)
Cached rate: 10 iron/second (seems legit!)
During CACHED: PreFab produces 10 iron/second from nothing
Result: Free iron! ❌
```

**Why traditional validation fails**:
- Can't detect which storage is "legitimate" vs "cheating"
- Factories inherently have storage (buffers, intermediate chests)
- No way to distinguish intentional buffer from hidden exploit

**The real question**: "Is factory in same state after full cycle?"
- If yes: Factory is self-sustaining loop (valid)
- If no: Something was consumed (hidden storage)

---

## The Solution: Graceful Shutdown Protocol

### Core Concept

**Bidirectional redstone communication** between PreFab and factory:
1. **PreFab signals**: "Please shut down gracefully"
2. **Factory responds**: Processes in-flight items, returns to idle state
3. **Factory signals**: "I'm now idle and ready for validation"
4. **PreFab validates**: Takes hash snapshot, compares with start state
5. **PreFab signals**: "Resume operation"
6. **Factory resumes**: Returns to normal production

**Key insight**: Factory explicitly enters deterministic idle state when asked.

---

## Protocol Flow

### Phase 1: Start Simulation

```
Player: Right-click with wrench → "Start Simulation"

PreFab:
  1. Send shutdown signal (redstone pulse)
  2. Wait for idle signal from factory
  3. Take hash snapshot (start state)
  4. Send resume signal
  5. Enter SIMULATING state
  
Factory (redstone circuit):
  1. Detect shutdown pulse
  2. Close input gates (stop importing resources)
  3. Process all in-flight items
  4. Wait for machines to idle (furnaces done, hoppers stopped)
  5. Send idle pulse to PreFab
  6. Wait for resume signal
  7. Open input gates, resume production
```

### Phase 2: Simulation Running

```
PreFab:
  - Transport resources normally
  - Count items/fluids/energy transferred
  - Track time elapsed
  
Factory:
  - Runs normally
  - Produces at actual rate
```

### Phase 3: End Simulation (Validation)

```
Player: Right-click with wrench → "Finish Simulation"

PreFab:
  1. Send shutdown signal
  2. Wait for idle signal
  3. Take hash snapshot (end state)
  4. Compare hashes:
     IF start_hash == end_hash:
       → Valid loop! Calculate rates, enter CACHED
       → Send resume signal (optional, chunks unload anyway)
     ELSE:
       → Invalid loop! Show error to player
       → Factory consumed hidden storage
       → Must remove storage and re-simulate
```

---

## Redstone Integration

### New PreFab Face Types

**1. SHUTDOWN_SIGNAL** (PreFab → Factory)
- PreFab outputs redstone signal
- Configured events trigger signal:
  - Simulation start
  - Simulation end
  - Validation checkpoint
- Factory's redstone circuit listens for this

**2. IDLE_SIGNAL** (Factory → PreFab)
- Factory outputs redstone signal
- PreFab face listens for pulse
- Signal means: "I'm idle, ready for validation"
- PreFab responds by taking hash snapshot

**3. RESUME_SIGNAL** (PreFab → Factory)
- PreFab outputs redstone signal
- Tells factory to resume normal operation
- Factory re-enables input gates

### Example Factory Redstone Circuit

**Simple version** (chest-based idle detection):
```
Input Chest → [Comparator] → Detects empty
              ↓
Output Chest → [Comparator] → Detects full
              ↓
       [AND gate] → Both true = Idle
              ↓
       [Idle Detector Block] → Pulse to PreFab
```

**Advanced version** (graceful shutdown):
```
[Shutdown Listener] ← From PreFab
        ↓
   [SR Latch] (shutdown state)
        ↓
  Close input gates (disable importers)
        ↓
  Wait for idle:
    - Input chest empty? ✓
    - Output chest full? ✓
    - All furnaces idle? ✓
    - All hoppers stopped? ✓
        ↓
  [Idle Detector] → Pulse to PreFab
        ↓
  Wait for resume signal
        ↓
  [Resume Listener] ← From PreFab
        ↓
  [SR Latch] (reset)
        ↓
  Open input gates, resume production
```

---

## New Blocks (Post-MVP)

### 1. Shutdown Listener Block (CM dimension only)

**Purpose**: Receives shutdown signal from PreFab

**Behavior**:
- Placed inside CM room
- Outputs redstone when PreFab sends shutdown signal
- Player connects to factory's shutdown logic

**BlockEntity data**:
```java
public class ShutdownListenerBlockEntity extends BlockEntity {
    private UUID linkedPreFabUUID;  // Which PreFab controls this
    private boolean isShutdownSignalActive;
    
    public void receiveShutdownSignal() {
        isShutdownSignalActive = true;
        updateNeighbors(); // Power adjacent redstone
    }
}
```

### 2. Idle Detector Block (CM dimension only)

**Purpose**: Sends idle signal to PreFab

**Behavior**:
- Placed inside CM room
- Receives redstone input from factory
- On redstone pulse: Notifies PreFab "factory is idle"

**BlockEntity data**:
```java
public class IdleDetectorBlockEntity extends BlockEntity {
    private UUID linkedPreFabUUID;
    
    @Override
    public void neighborChanged() {
        if (detectRedstonePulse()) {
            notifyPreFab(); // Tell PreFab we're idle
        }
    }
}
```

### 3. Resume Listener Block (CM dimension only)

**Purpose**: Receives resume signal from PreFab

**Behavior**:
- Placed inside CM room
- Outputs redstone when PreFab sends resume signal
- Player connects to factory's resume logic

---

## Face Configuration (GUI)

### Shutdown Signal Face
```
┌─────────────────────────────────────┐
│   Face: UP                          │
├─────────────────────────────────────┤
│ Mode:   [SHUTDOWN_SIGNAL]           │
│ Target: [Shutdown Listener #1] ▼   │
│                                     │
│ Send signal when:                   │
│   [✓] Simulation starts             │
│   [✓] Simulation ends               │
│   [ ] Manual trigger (wrench)       │
│                                     │
│ Signal type:                        │
│   ( ) Pulse (1 redstone tick)       │
│   (●) Sustained (until idle)        │
└─────────────────────────────────────┘
```

### Idle Signal Face
```
┌─────────────────────────────────────┐
│   Face: DOWN                        │
├─────────────────────────────────────┤
│ Mode:   [IDLE_SIGNAL]               │
│ Source: [Idle Detector #1] ▼       │
│                                     │
│ Listen for:                         │
│   (●) Rising edge (pulse)           │
│   ( ) High signal (sustained)       │
│                                     │
│ On signal received:                 │
│   [✓] Take hash snapshot            │
│   [✓] Compare with start hash       │
│   [✓] Log validation result         │
└─────────────────────────────────────┘
```

---

## Validation Logic

### Hash Computation (Async)

```java
// On main thread (fast):
Map<BlockPos, CompoundTag> snapshot = collectBlockEntityData(cmRoom);

// Off main thread (slow, doesn't block):
CompletableFuture<String> hashFuture = CompletableFuture.supplyAsync(() -> {
    // Normalize NBT data
    Map<BlockPos, CompoundTag> normalized = normalizeNBT(snapshot);
    
    // Compute hash
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    for (Map.Entry<BlockPos, CompoundTag> entry : normalized.entrySet()) {
        byte[] nbtBytes = serializeNBT(entry.getValue());
        digest.update(nbtBytes);
    }
    
    return bytesToHex(digest.digest());
});

// When hash ready:
hashFuture.thenAccept(endHash -> {
    if (endHash.equals(startHash)) {
        player.sendMessage("§a✓ Factory validated! Loop confirmed.");
        enterCachedMode();
    } else {
        player.sendMessage("§c✗ Validation failed! Hidden storage detected.");
        player.sendMessage("§7Remove all storage and re-simulate.");
        enterHaltedMode();
    }
});
```

### NBT Normalization (Critical!)

**Problem**: Same contents, different NBT → different hash
```
Chest A: [Slot 0: iron, Slot 5: coal]
Chest B: [Slot 2: iron, Slot 8: coal]
Same items, different slots!
```

**Solution**: Normalize before hashing
```java
private CompoundTag normalizeNBT(CompoundTag tag) {
    CompoundTag normalized = new CompoundTag();
    
    // Sort item lists by item ID, ignore slots
    if (tag.contains("Items")) {
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        List<CompoundTag> sortedItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            sortedItems.add(items.getCompound(i));
        }
        sortedItems.sort(Comparator.comparing(item -> item.getString("id")));
        
        ListTag normalizedItems = new ListTag();
        for (CompoundTag item : sortedItems) {
            CompoundTag normalized = new CompoundTag();
            normalized.putString("id", item.getString("id"));
            normalized.putInt("Count", item.getInt("Count"));
            // Ignore Slot, ignore NBT data (for simplicity)
            normalizedItems.add(normalized);
        }
        normalized.put("Items", normalizedItems);
    }
    
    // Ignore dynamic fields
    // - burnTime (constantly changing)
    // - cookTime (constantly changing)
    // - cooldown (hopper transfer timer)
    
    return normalized;
}
```

---

## Edge Cases

### Case 1: Factory Never Reaches Idle

**Problem**: Autonomous factory runs forever, never signals idle

**Solution**: Timeout
```java
// Wait for idle signal, but with timeout
if (waitingForIdleSignal && ticksElapsed > MAX_WAIT_TICKS) {
    player.sendMessage("§cFactory failed to reach idle state!");
    player.sendMessage("§7Check redstone circuit or increase timeout.");
    abortValidation();
}
```

**Configurable timeout** (per-PreFab):
- Default: 60 seconds
- Player can set: 30s - 600s
- For complex factories with long cycles

### Case 2: Player Interference

**Problem**: Player enters CM dimension, adds items during validation

**Solution**: Lock factory during validation
```java
@SubscribeEvent
public void onPlayerEnterDimension(PlayerEvent.ChangeDimension event) {
    if (event.getTo().equals(CM_DIMENSION) && isValidating) {
        event.setCanceled(true);
        player.sendMessage("§cFactory locked during validation!");
    }
}
```

**Alternative**: Detect interference, re-validate
```java
if (playerEnteredRoom || chunkUnloaded || serverCrashed) {
    player.sendMessage("§eValidation interrupted, restarting...");
    restartValidation();
}
```

### Case 3: Multi-Cycle Loops

**Problem**: Factory has 2-cycle pattern (A → B → A)

**Solution**: Multiple validation checkpoints
```java
// Take hash every N seconds
List<String> hashes = new ArrayList<>();
hashes.add(takeHash()); // t=0
hashes.add(takeHash()); // t=60s
hashes.add(takeHash()); // t=120s
hashes.add(takeHash()); // t=180s

// Check if any hash repeats (indicates loop)
if (hashes.get(0).equals(hashes.get(2)) || 
    hashes.get(0).equals(hashes.get(3))) {
    // Factory returned to start state!
    validLoop();
}
```

**Or**: Let player configure cycle length
```
Player: "My factory has a 3-cycle loop"
PreFab: Takes hash every 60s, validates after 180s
```

---

## Fallback: Simple Timeout (MVP Approach)

**For factories without redstone control**:

```java
// No shutdown/idle signals, just fixed timing
configuredCycleTime = 60; // seconds, player-configurable

// Start simulation:
startHash = takeHash();

// After configured time:
if (ticksElapsed >= configuredCycleTime * 20) {
    endHash = takeHash();
    
    if (startHash.equals(endHash)) {
        validLoop();
    } else {
        invalidLoop();
    }
}
```

**Works for**:
- ✅ Simple factories with natural cycles (furnace array)
- ✅ Factories that idle naturally (chest-based buffers)

**Doesn't work for**:
- ❌ Autonomous factories (never idle)
- ❌ Complex multi-cycle patterns
- ❌ Variable timing factories

**Recommendation**: Implement timeout first (MVP), add redstone control later (v1.1+)

---

## Benefits Summary

**Why this approach works**:

✅ **Handles any factory complexity**: Player designs shutdown logic  
✅ **Deterministic**: Same idle state every time  
✅ **Can't be cheated**: Hash comparison catches changes  
✅ **Player has control**: Configures when/how to idle  
✅ **Real-world pattern**: Industrial "safe shutdown" protocol  
✅ **Progressive enhancement**: Works without redstone (timeout), better with redstone  
✅ **Performance**: Async hashing doesn't block main thread  

---

## Implementation Priority

**MVP**: No validation (trust players)
- Focus on core caching functionality
- Add validation later once caching proven

**v1.0**: Simple timeout validation
- Fixed cycle time (60s configurable)
- Hash before/after, compare
- Works for 80% of factories

**v1.1**: Bidirectional redstone protocol
- Shutdown/Idle/Resume signals
- Handles autonomous factories
- Advanced users can optimize

**v1.2**: Performance optimization
- Async hashing
- NBT normalization
- Multi-checkpoint validation

---

**See ARCHITECTURE_CONDUIT.md for how this integrates with overall caching system.**
