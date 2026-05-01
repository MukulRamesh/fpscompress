# Testing Virtual Buffer Capacity Limits

This guide shows how to manually test that virtual buffers reject items/fluids/energy beyond their hard caps.

## What Was Added

Console logging was added to `VirtualBufferStorage.java` that prints:
- **WARNING** messages when capacity is exceeded and items are rejected
- **DEBUG** messages for successful additions

Look for log messages in the console like:
```
[WARN] Virtual buffer REJECTED minecraft:iron_ingot x100 (buffer full: 1728/1728 items). Only added 0.
[WARN] Virtual buffer REJECTED minecraft:water 10000mB (buffer full: 50000/50000 mB). Only added 0 mB.
[WARN] Virtual buffer REJECTED 500000 FE (buffer full: 1000000/1000000 FE). Only added 0 FE.
```

## Manual Test: In-Game

### Test 1: Item Capacity (1,728 items max)

1. Launch the game: `./gradlew runClient`
2. Create a Machine Portal block (from FPSCompress mod)
3. Access the virtual buffer (via code or debug command)
4. Try adding items:
   ```java
   // In your test code or via command
   storage.addItem("minecraft:iron_ingot", 1728);  // Should succeed
   storage.addItem("minecraft:iron_ingot", 1);     // Should REJECT (buffer full)
   ```
5. **Check console** - you should see:
   ```
   [WARN] Virtual buffer REJECTED minecraft:iron_ingot x1 (buffer full: 1728/1728 items)
   ```

### Test 2: Fluid Capacity (50,000 mB max)

```java
storage.addFluid("minecraft:water", 50000);  // Should succeed
storage.addFluid("minecraft:water", 1000);   // Should REJECT 1000 mB
```

Expected console output:
```
[WARN] Virtual buffer REJECTED minecraft:water 1000mB (buffer full: 50000/50000 mB)
```

### Test 3: Energy Capacity (1,000,000 FE max)

```java
storage.addEnergy(1_000_000L);  // Should succeed
storage.addEnergy(100_000L);    // Should REJECT 100,000 FE
```

Expected console output:
```
[WARN] Virtual buffer REJECTED 100000 FE (buffer full: 1000000/1000000 FE)
```

## Method 2: Using a Debugger (IDE)

### VS Code Setup

1. Open [`VirtualBufferStorage.java`](fpscompress-template-1.21.11/src/main/java/com/mukulramesh/fpscompress/portal/VirtualBufferStorage.java)
2. Set breakpoints on these lines:
   - Line 89 in `addItem()` - where it returns `toAdd`
   - Line 177 in `addFluid()` - where it returns `toAdd`
   - Line 261 in `addEnergy()` - where it returns `toAdd`

3. Launch the game in debug mode:
   - Press `F5` or click "Run and Debug"
   - Select the "Client" launch configuration

4. When the code hits your breakpoint, inspect:
   - `amount` (requested amount)
   - `toAdd` (actually added amount)
   - `spaceLeft` (remaining capacity)
   - If `toAdd < amount`, the buffer rejected excess items ✓

### IntelliJ IDEA Setup

1. Set breakpoints in `VirtualBufferStorage.java`:
   - Right-click line numbers 89, 177, 261
   - Click "Toggle Breakpoint"

2. Run in debug mode:
   - Click the bug icon next to "runClient" Gradle task
   - Or: Right-click `FPSCompress.java` → "Debug"

3. When breakpoint hits, use the "Variables" panel to inspect values

## Automated Unit Test (Recommended)

Create this JUnit test to verify capacity enforcement without launching the game:

```java
package com.mukulramesh.fpscompress.portal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VirtualBufferStorageTest {

    @Test
    void testItemCapacityEnforcement() {
        VirtualBufferStorage storage = new VirtualBufferStorage();

        // Fill to capacity
        int added = storage.addItem("minecraft:stone", 1728);
        assertEquals(1728, added, "Should add all 1728 items");
        assertEquals(1728, storage.getTotalItemCount());

        // Try to exceed capacity
        int rejected = storage.addItem("minecraft:stone", 100);
        assertEquals(0, rejected, "Should reject all items when full");
        assertEquals(1728, storage.getTotalItemCount(), "Count shouldn't increase");
    }

    @Test
    void testFluidCapacityEnforcement() {
        VirtualBufferStorage storage = new VirtualBufferStorage();

        // Fill to capacity
        int added = storage.addFluid("minecraft:water", 50000);
        assertEquals(50000, added);

        // Try to exceed capacity
        int rejected = storage.addFluid("minecraft:water", 1000);
        assertEquals(0, rejected, "Should reject all fluid when full");
        assertEquals(50000, storage.getTotalFluidAmount());
    }

    @Test
    void testEnergyCapacityEnforcement() {
        VirtualBufferStorage storage = new VirtualBufferStorage();

        // Fill to capacity
        long added = storage.addEnergy(1_000_000L);
        assertEquals(1_000_000L, added);

        // Try to exceed capacity
        long rejected = storage.addEnergy(100_000L);
        assertEquals(0L, rejected, "Should reject all energy when full");
        assertEquals(1_000_000L, storage.getEnergyAmount());
    }

    @Test
    void testPartialRejection() {
        VirtualBufferStorage storage = new VirtualBufferStorage();

        // Add 1700 items
        storage.addItem("minecraft:iron_ingot", 1700);

        // Try to add 100 more (only 28 should fit)
        int partiallyAdded = storage.addItem("minecraft:iron_ingot", 100);
        assertEquals(28, partiallyAdded, "Should only add remaining 28 items");
        assertEquals(1728, storage.getTotalItemCount());
    }
}
```

Save this as `VirtualBufferStorageTest.java` in `src/test/java/com/mukulramesh/fpscompress/portal/`

Run tests:
```bash
cd fpscompress-template-1.21.11
./gradlew test
```

## Expected Results

✅ **Capacity limits work correctly if:**
- Console shows WARN messages when items/fluids/energy are rejected
- `toAdd < amount` when buffer is full (visible in debugger)
- Unit tests pass

❌ **Bug found if:**
- Buffer accepts more than 1,728 items
- Buffer accepts more than 50,000 mB fluid
- Buffer accepts more than 1,000,000 FE energy
- No WARNING logs appear when capacity exceeded
