# Advanced Setup Guide

Advanced techniques for complex factory setups with FPSCompress.

## Multi-PreFab Factories

### Overview

You can use multiple PreFabs to create complex, modular production chains.

**Current status**: ✅ Multiple PreFabs work independently  
**Future feature**: 🔨 Factory Controller for coordinated management

### Independent PreFabs

**Setup**:
1. Create multiple Compact Machines (different rooms)
2. Upgrade each CM to PreFab (separate PreFab Upgrade Templates)
3. Configure each PreFab independently
4. Connect chests to each PreFab

**Example: Modular Iron Production**
```
PreFab A: Ore Processing
  - NORTH: PULL → Iron Ore Importer
  - SOUTH: PUSH ← Iron Dust Exporter

PreFab B: Smelting
  - NORTH: PULL → Iron Dust Importer
  - EAST:  PULL → Coal Importer
  - SOUTH: PUSH ← Iron Ingot Exporter

Overworld Connection:
[Ore Chest] → [PreFab A] → [Dust Chest] → [PreFab B] → [Ingot Chest]
                                               ↑
                                         [Coal Chest]
```

**Benefits**:
- Modular design (independent factories)
- Easy to debug (isolate issues to specific PreFab)
- Scalable (add more PreFabs as needed)

### Chained PreFabs

**Pattern**: Output from PreFab A → Input to PreFab B

**Setup**:
```
[PreFab A SOUTH face] → [Intermediate Chest] → [PreFab B NORTH face]
```

**Example**:
```
PreFab A (Crushing):
  - Produces crushed ore

Intermediate Chest:
  - Holds crushed ore temporarily

PreFab B (Smelting):
  - Consumes crushed ore
  - Produces ingots
```

**Advantages**:
- Clear separation of concerns
- Each PreFab can be in CACHED independently
- Easy to expand (add PreFab C, D, etc.)

### Shared Resources

**Pattern**: Multiple PreFabs pulling from same chest

**Setup**:
```
        [Coal Chest]
        /     |     \
   PreFab A  PreFab B  PreFab C
(All PULL from same chest)
```

**Use case**: Shared fuel/power supply for multiple factories

**Considerations**:
- Ensure chest has enough supply for all PreFabs
- If chest empties, all PreFabs enter HALTED
- Use large chests (barrels, storage drawers)

---

## Organization Strategies

### Frequency System for Organization

**Best practice**: Name Importers/Exporters by function, not just resource

**Examples**:

**By Resource Type**:
- Coal Importer
- Iron Ore Importer
- Iron Ingot Exporter

**By Function**:
- Primary Fuel Input
- Raw Material Input
- Finished Product Output
- Byproduct Output

**By Stage**:
- Stage 1 Input (Ore)
- Stage 2 Input (Dust)
- Final Output (Ingot)

**Tip**: Use item that represents function (e.g., Hopper for "Input", Chest for "Output")

### Color-Coding in Overworld

**Pattern**: Use different blocks for visual organization

**Example**:
- Blue Wool/Concrete: Input chests (PULL faces)
- Red Wool/Concrete: Output chests (PUSH faces)
- Green Wool/Concrete: Shared resource chests
- Yellow Wool/Concrete: Intermediate chests (between PreFabs)

**Layout**:
```
[Blue Wool]  [PreFab]  [Red Wool]
[Input Chest] ← → [Output Chest]
```

### Labeling with Signs

**Recommended**:
- Place signs on chests indicating purpose
- "Coal Input for PreFab A"
- "Iron Ingot Output from PreFab B"
- "Shared Fuel Supply"

**Benefits**:
- Easy to remember setup
- Helps when returning after long break
- Useful for multiplayer (others can understand your setup)

---

## Complex Factory Patterns

### Pattern 1: Fan-In (Multiple Inputs → One Factory)

**Setup**:
```
[Coal Chest] → [PreFab NORTH]
[Ore Chest]  → [PreFab EAST]
[Flux Chest] → [PreFab WEST]
                 |
                 ↓
           [Output Chest] ← [PreFab SOUTH]
```

**Use case**: Factory requires multiple input types

**Inside CM**:
```
[Coal Importer] → [Machine Input 1]
[Ore Importer]  → [Machine Input 2]
[Flux Importer] → [Machine Input 3]
      ↓
[Machine Output] → [Product Exporter]
```

### Pattern 2: Fan-Out (One Input → Multiple Outputs)

**Setup**:
```
         [Input Chest] → [PreFab NORTH]
                           |
         ┌─────────────────┼─────────────┐
         ↓                 ↓             ↓
[PreFab SOUTH]    [PreFab EAST]    [PreFab WEST]
        ↓                 ↓             ↓
[Primary Output]  [Byproduct 1]  [Byproduct 2]
```

**Use case**: Machine produces multiple output types

**Inside CM**:
```
[Input Importer] → [Processing Machine] → [Primary Exporter]
                                        → [Byproduct Exporter 1]
                                        → [Byproduct Exporter 2]
```

### Pattern 3: Recycling Loop

**Setup**:
```
[Raw Input] → [PreFab A NORTH]
                  |
                  ↓ (main output)
           [PreFab A SOUTH] → [Product Chest]
                  |
                  ↓ (byproduct)
           [PreFab A EAST] → [Recycling Chest] → [PreFab B]
                                                     |
                                                     ↓
                                              [Raw Input]
                                              (loops back)
```

**Use case**: Byproduct recycling (e.g., slag → more ore)

**Considerations**:
- Ensure loop doesn't deadlock (always have external input)
- Monitor recycling chest (shouldn't overflow)

---

## Nested PreFabs (PreFab Inside PreFab)

### Concept

**Idea**: Place a PreFab inside a Compact Machine dimension

**Status**: 🔨 Experimental (may have issues)

**Setup**:
```
Overworld:
  [PreFab A]

CM Dimension (inside PreFab A):
  [PreFab B] (another PreFab)

CM Dimension (inside PreFab B):
  [Actual factory]
```

**Theoretical use case**: Ultra-compact setups (one block in Overworld contains 2+ factories)

**Known issues**:
- Chunk loading may be complex (nested CMs)
- State management unclear (if PreFab A is CACHED, can PreFab B simulate?)
- Not officially supported (test at your own risk)

---

## Integration with Other Mods

### Vanilla+ Mods

**Hoppers**:
- ✅ Work great with PreFabs
- Place hopper between chest and PreFab face
- Faster item transfer than direct chest

**Storage Drawers**:
- ✅ Compatible (exposes IItemHandler)
- Use for large storage capacity
- Good for high-throughput factories

**Ender Chests**:
- ✅ Can pull/push to Ender Chests
- Use for wireless item transport
- Connect multiple PreFabs via shared Ender Chest

### Modded Pipes (Item Transport)

**Examples**: Thermal Dynamics, Mekanism Pipes, Create Funnels

**Setup**:
```
[Chest] → [Pipe] → [PreFab Face]
```

**Compatibility**: Should work if pipe acts as inventory

**Note**: PreFab extracts from adjacent block (pipe must be adjacent to PreFab)

### AE2 / Refined Storage (Future)

**Status**: 🔨 Not yet implemented

**Planned**:
- Factory Controller block
- Direct ME interface connection
- Autocrafting integration

**Current workaround**: Use chests + ME import/export buses

---

## Performance Optimization

### Minimizing HALTED States

**Problem**: PreFab enters HALTED frequently

**Solutions**:

**1. Larger buffers**:
- Use Barrels instead of chests (64 stacks vs. 27 stacks)
- Use Storage Drawers (massive capacity)

**2. Balanced rates**:
- Match input supply rate to factory consumption
- Example: Factory uses 1 coal/tick → Supply chest needs 1200 coal/minute

**3. Monitoring systems**:
- Use comparators to detect low chest levels
- Automate restocking (hoppers, pipes)

### Reducing Tick Lag

**PreFab tick cost**:
- CACHED mode: ~0.01ms per PreFab (negligible)
- SIMULATING mode: Same as running real factory (high)

**Optimization**:
- Keep PreFabs in CACHED as much as possible
- Don't re-simulate unless necessary (rates don't change often)
- Avoid having many PreFabs in SIMULATING simultaneously

### Chunk Loading Management

**Strategy**:
- Group PreFabs in same chunks (reduces total chunks loaded)
- Use chunk loaders sparingly (PreFabs in CACHED do not need loaded chunks)

---

## Advanced Face Configuration

### Using All 6 Faces

**Maximum throughput**: Configure all 6 faces

**Example Setup**:
```
NORTH: PULL → Coal Importer
SOUTH: PULL → Iron Ore Importer
EAST:  PULL → Gold Ore Importer
WEST:  PULL → Copper Ore Importer
UP:    PUSH ← Alloy Exporter
DOWN:  PUSH ← Slag Exporter
```

**Layout**:
```
      [Alloy Chest]
           ↑ (UP face)
[Coal] → [PreFab] ← [Gold]
           ↓ (DOWN face)
      [Slag Chest]

(Plus SOUTH and WEST faces with more inputs)
```

### Directional Optimization

**Pattern**: Place chests based on access

**Vertical factory** (use UP/DOWN faces):
```
[Chest]
   ↓ (hopper)
[PreFab UP face]
   ↓ (hopper)
[PreFab DOWN face]
   ↓ (hopper)
[Chest]
```

**Horizontal factory** (use NORTH/SOUTH/EAST/WEST):
```
[Chest] → [PreFab] → [Chest]
```

---

## Debugging Complex Setups

### Troubleshooting Multi-PreFab Systems

**Problem**: "One PreFab works, others don't"

**Debug steps**:
1. Test each PreFab individually
2. Disconnect chained PreFabs (test in isolation)
3. Check for resource conflicts (multiple PreFabs pulling same chest)

**Problem**: "Chained PreFabs get out of sync"

**Cause**: PreFab A produces faster than PreFab B consumes

**Solution**:
- Use larger intermediate chests
- Balance rates (adjust machine counts)
- Add buffer storage

### Monitoring Tools

**F3 Debug Screen**:
- Check chunk loading (C: value)
- Verify PreFabs are in CACHED

**Chat Messages** (current):
- Watch for state transition messages
- "Entering HALTED" → Investigate that PreFab

**GUI Inspection**:
- Open each PreFab GUI
- Check current state (BUILDING/SIMULATING/CACHED/HALTED)

---

## Future Features

### Factory Controller Block (Planned)

**Concept**: Centralized management for multiple PreFabs

**Features**:
- Hold multiple PreFabs as items
- Unified ME interface
- Cross-PreFab dependencies
- Coordinated state management

**Release**: Post-MVP (v1.1+)

### Advanced Filters (Planned)

**Concept**: Whitelist/blacklist items per face

**Use case**:
- PULL only coal (ignore other items in chest)
- PUSH only iron ingots (keep byproducts inside)

**Release**: Post-MVP (v1.2+)

### Wireless Linking (Planned)

**Concept**: Link PreFab faces without physical chests

**Use case**:
- PreFab A output → PreFab B input (no intermediate chest)
- Directly transfer between dimensions

**Release**: Post-MVP (v1.3+)

---

## See Also

- [Getting Started](Getting-Started) - Basic setup
- [PreFab System](PreFab-System) - Understanding PreFabs
- [Face Configuration](Face-Configuration) - Configuring faces
- [Troubleshooting](Troubleshooting) - Common issues
