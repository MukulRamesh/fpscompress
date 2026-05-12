# HALTED State Recovery Optimizations

**Status**: Post-MVP Enhancement (v1.1+)  
**Current Implementation**: Exponential Backoff (MVP)  
**Related**: CACHED mode performance, chunk loading avoidance

---

## Problem Statement

When a PreFab enters **HALTED state** (input starved or output blocked), the system needs to detect when resources become available again to resume CACHED operation. The challenge is balancing **responsiveness** (quick recovery) with **performance** (avoiding expensive inventory checks).

### Current MVP Implementation: Exponential Backoff

**How it works:**
- After entering HALTED, retry transfer attempts at increasing intervals
- Intervals: 1 tick → 2 ticks → 4 ticks → 8 ticks → ... → max 100 ticks (5 seconds)
- On successful recovery, reset to 1 tick for next failure

**Performance:**
- First retry: Immediate (1 tick = 50ms)
- After 6 failures: 64 tick interval (3.2 seconds between checks)
- After 7+ failures: 100 tick interval (5 seconds between checks)
- **99% reduction** in inventory checks compared to every-tick polling

**Trade-offs:**
- ✅ Simple implementation (~30 lines of code)
- ✅ Works with all mods (no capability dependencies)
- ✅ Huge performance gain for persistent failures
- ✅ Still responsive for transient failures (1-2 second recovery)
- ⚠️ Max 5 second delay for recovery (acceptable for error state)

---

## Post-MVP Optimization Strategies

### Option 1: Inventory Change Listeners (v1.1)

**Concept**: Subscribe to inventory change events from adjacent blocks instead of polling.

**Implementation:**
```java
// Listen for inventory changes on adjacent blocks
private final Map<BlockPos, INotifyingItemHandler> inventoryListeners = new HashMap<>();

private void enterHaltedState() {
    // Register listeners for all adjacent inventory blocks
    for (Direction face : Direction.values()) {
        BlockPos adjacentPos = getBlockPos().relative(face);
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, 
            adjacentPos, face.getOpposite());
        
        if (handler instanceof INotifyingItemHandler notifier) {
            notifier.addListener(this::onInventoryChanged);
            inventoryListeners.put(adjacentPos, notifier);
        }
    }
}

private void onInventoryChanged(BlockPos pos, int slot, ItemStack stack) {
    // Inventory changed - attempt recovery immediately
    if (currentState == MachineState.HALTED) {
        tickCachedProduction(); // Immediate retry
    }
}

private void exitHaltedState() {
    // Clean up listeners
    for (var entry : inventoryListeners.entrySet()) {
        entry.getValue().removeListener(this::onInventoryChanged);
    }
    inventoryListeners.clear();
}
```

**Performance:**
- **Zero overhead** when inventories are static (no polling)
- **Instant recovery** when items added/removed
- **Best-case scenario**: No checks until inventory actually changes

**Challenges:**
- Not all mod inventories implement listener interfaces
- Need careful lifecycle management (chunk unload, block removal)
- Fallback to backoff required for non-notifying inventories
- Event spam protection (some mods fire excessive events)

**Recommendation**: 
- Use hybrid approach: listeners where available + backoff as fallback
- Detect listener support during face configuration
- Track `listenersAvailable` flag per face

---

### Option 2: Smart Selective Checking (v1.1)

**Concept**: Only check the specific resource and faces that caused the HALT.

**Implementation:**
```java
// Track which resource caused HALT
private String haltedResourceId = null;
private boolean haltedIsInput = false; // true = input starved, false = output blocked
private final Set<Direction> relevantFaces = new HashSet<>();

private void tickCachedProduction() {
    if (currentState == MachineState.HALTED) {
        // Backoff logic...
        if (ticksSinceLastRetry < haltedRetryInterval) {
            return;
        }
        ticksSinceLastRetry = 0;
        
        // Only check the failed resource on relevant faces
        if (haltedResourceId != null) {
            boolean success = haltedIsInput 
                ? retryFailedInput(haltedResourceId, relevantFaces)
                : retryFailedOutput(haltedResourceId, relevantFaces);
            
            if (success) {
                // Recovery successful - return to full processing
                haltedResourceId = null;
                relevantFaces.clear();
                setCurrentState(MachineState.CACHED);
                return;
            }
        }
    } else {
        // Normal CACHED mode: process all resources
        // ... existing logic ...
    }
}

private boolean retryFailedInput(String resourceId, Set<Direction> faces) {
    // Only check specified PULL faces for this specific item
    // Much faster than scanning all resources on all faces
}
```

**Performance:**
- Reduces checks from **N resources × 6 faces** to **1 resource × 1-2 faces**
- Example: 3 resources → 83% reduction in checks
- Still benefits from exponential backoff

**Challenges:**
- Edge case: Multiple resources could fail simultaneously (need to track all)
- Complexity: Need to properly track failure context
- Benefit diminishes if all resources fail (rare in practice)

**Recommendation**: 
- Combine with exponential backoff for best results
- Track up to 3 failed resources (beyond that, revert to full checking)

---

### Option 3: Player Proximity Detection (v1.2)

**Concept**: Adjust backoff max interval based on player proximity.

**Implementation:**
```java
private int getMaxBackoffInterval() {
    // Check if any player within 16 blocks
    boolean playerNearby = level.players().stream()
        .anyMatch(p -> p.distanceToSqr(Vec3.atCenterOf(getBlockPos())) < 16*16);
    
    // Players nearby: Fast recovery (1 second)
    // No players: Slow recovery (10 seconds)
    return playerNearby ? 20 : 200;
}

private void tickCachedProduction() {
    if (currentState == MachineState.HALTED) {
        ticksSinceLastRetry++;
        if (ticksSinceLastRetry < haltedRetryInterval) {
            return;
        }
        ticksSinceLastRetry = 0;
        // ... existing logic ...
        
        // Dynamic backoff cap
        haltedRetryInterval = Math.min(haltedRetryInterval * 2, getMaxBackoffInterval());
    }
}
```

**Performance:**
- **10x better** when players are far away (AFK scenarios)
- **Responsive** when players are actively working nearby
- Minimal overhead (player distance check every 1-10 seconds)

**Challenges:**
- Doesn't help with automated systems (hoppers, pipes)
- Players passing by trigger fast recovery (maybe good?)
- Distance threshold needs tuning (16 blocks? 32 blocks?)

**Recommendation**: 
- Add in v1.2 after gathering gameplay data
- Make distance threshold configurable
- Consider "player intent" detection (standing still = AFK = slow recovery)

---

### Option 4: Redstone Signal Trigger (v1.3)

**Concept**: Allow player to force immediate retry via redstone signal.

**Implementation:**
```java
// In PrefabBlock.java
@Override
public void neighborChanged(BlockState state, Level level, BlockPos pos, 
                           Block block, BlockPos fromPos, boolean isMoving) {
    if (!level.isClientSide()) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PrefabBlockEntity prefab) {
            boolean receivingPower = level.hasNeighborSignal(pos);
            
            if (receivingPower && prefab.getCurrentState() == MachineState.HALTED) {
                // Redstone signal forces immediate retry
                prefab.forceRetryNow();
            }
        }
    }
}

// In PrefabBlockEntity.java
public void forceRetryNow() {
    if (currentState == MachineState.HALTED) {
        ticksSinceLastRetry = haltedRetryInterval; // Trigger retry on next tick
        FPSCompress.LOGGER.info("PreFab at {} forced retry via redstone", getBlockPos());
    }
}
```

**Performance:**
- **Zero overhead** when no redstone wiring
- **Perfect for automation**: Item detector → redstone → PreFab
- **Player control**: Manual button press to force retry

**Challenges:**
- Requires player knowledge/setup
- Not automatic (needs extra building)
- Redstone spam could cause performance issues (debounce needed)

**Recommendation**: 
- Add in v1.3 as optional advanced feature
- Document in tooltip: "Apply redstone signal to force retry"
- Add debounce: Only trigger once per second max

---

### Option 5: Periodic Full Scan (Safety Net)

**Concept**: Regardless of optimization, do a full scan every 10 minutes as safety net.

**Implementation:**
```java
private long lastFullScanTick = 0;

private void tickCachedProduction() {
    // Safety net: Full scan every 12000 ticks (10 minutes)
    long currentTick = level.getGameTime();
    boolean forcedFullScan = (currentTick - lastFullScanTick) > 12000;
    
    if (forcedFullScan) {
        lastFullScanTick = currentTick;
        // Force retry regardless of backoff
        ticksSinceLastRetry = haltedRetryInterval;
    }
    
    // ... existing backoff logic ...
}
```

**Performance:**
- Negligible overhead (1 check per 10 minutes)
- Prevents edge cases where optimizations fail

**Challenges:**
- None - pure safety net

**Recommendation**: 
- Add in v1.1 as safety measure
- Make interval configurable (default 10 minutes)

---

## Recommended Implementation Roadmap

### Phase 1 (v1.0 - MVP): Exponential Backoff ✅
- **Status**: Implemented
- **Performance**: 99% reduction vs every-tick
- **UX**: 1-5 second recovery time

### Phase 2 (v1.1): Inventory Listeners + Selective Checking
- **Primary**: Inventory change listeners (where available)
- **Secondary**: Smart selective checking (fallback)
- **Safety**: Periodic full scan every 10 minutes
- **Expected gain**: 50-90% reduction over backoff alone
- **UX**: Instant recovery for vanilla/common mod inventories

### Phase 3 (v1.2): Player Proximity
- **Feature**: Dynamic backoff based on player distance
- **Expected gain**: 10x improvement for AFK scenarios
- **UX**: No visible change (just better performance)

### Phase 4 (v1.3): Redstone Trigger
- **Feature**: Manual retry via redstone signal
- **Expected gain**: Perfect control for automation
- **UX**: Optional advanced feature (documented in tooltip)

---

## Compatibility Matrix

| Optimization | Vanilla | Forge Mods | NeoForge Mods | AE2/RS | Notes |
|-------------|---------|-----------|---------------|--------|-------|
| Exponential Backoff | ✅ | ✅ | ✅ | ✅ | Universal |
| Inventory Listeners | ✅ | ⚠️ | ⚠️ | ✅ | Varies by mod |
| Selective Checking | ✅ | ✅ | ✅ | ✅ | Universal |
| Player Proximity | ✅ | ✅ | ✅ | ✅ | Universal |
| Redstone Trigger | ✅ | ✅ | ✅ | ✅ | Universal |
| Periodic Scan | ✅ | ✅ | ✅ | ✅ | Universal |

Legend:
- ✅ Full support
- ⚠️ Partial support (depends on mod implementation)

---

## Configuration Options (Post-MVP)

**Suggested config file structure:**
```toml
[halted_recovery]
# Exponential backoff settings
initial_retry_interval = 1  # ticks (50ms)
max_retry_interval = 100    # ticks (5 seconds)
backoff_multiplier = 2.0    # exponential growth rate

# Player proximity settings
enable_proximity_detection = true
player_proximity_threshold = 16  # blocks
fast_recovery_max_interval = 20  # ticks (1 second)
slow_recovery_max_interval = 200 # ticks (10 seconds)

# Safety net
enable_periodic_full_scan = true
full_scan_interval = 12000  # ticks (10 minutes)

# Redstone trigger
enable_redstone_trigger = true
redstone_trigger_debounce = 20  # ticks (1 second)
```

---

## Performance Benchmarks (Theoretical)

**Scenario**: PreFab in HALTED state with persistent failure (empty input chest).

| Optimization | Checks/Second | Checks/Minute | Improvement |
|-------------|---------------|---------------|-------------|
| Every-tick (original) | 20 | 1200 | Baseline |
| Exponential Backoff (MVP) | 0.2 | 12 | **99% better** |
| + Inventory Listeners | 0 | 0 | **100% better** |
| + Player Proximity (AFK) | 0.1 | 6 | **99.5% better** |

**Real-world scenario**: 100 PreFabs in HALTED state on a server.
- Original: 2000 checks/second (TPS impact: ~5%)
- Exponential Backoff: 20 checks/second (TPS impact: ~0.05%)
- + Listeners: 0 checks/second (TPS impact: ~0%)

---

## Testing Recommendations

**Unit tests:**
- Exponential backoff intervals (1 → 2 → 4 → 8... → 100)
- NBT persistence of backoff state
- Recovery resets backoff to 1

**Integration tests:**
- Empty chest → HALTED → add items → CACHED
- Full chest → HALTED → remove items → CACHED
- Player proximity changes backoff interval
- Redstone signal forces immediate retry

**Performance tests:**
- 100 PreFabs in HALTED: measure TPS impact
- Compare backoff vs every-tick (expect 99% improvement)
- Memory usage (backoff adds 2 ints per PreFab = 8 bytes)

---

## Future Research

### Machine Learning Prediction (v2.0+)
- Learn factory production patterns over time
- Predict when inputs/outputs likely to change
- Pre-emptively check before items run out
- **Complexity**: High (requires ML library)
- **Benefit**: Potentially perfect prediction

### Distributed Recovery Coordination (v2.0+)
- Multiple PreFabs share recovery polling schedule
- Stagger checks to avoid TPS spikes
- Example: 100 PreFabs → each checks every 100 ticks at different offsets
- **Complexity**: Medium (requires global coordinator)
- **Benefit**: Smoother TPS (no spikes)

---

**Document Version**: 1.0  
**Last Updated**: 2026-05-03  
**Author**: FPSCompress Development Team
