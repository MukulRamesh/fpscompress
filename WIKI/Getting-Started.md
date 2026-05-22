# Getting Started with FPSCompress

This guide will walk you through setting up your first factory compression system.

## Prerequisites

Before starting, make sure you have:
- ✅ Compact Machines mod installed
- ✅ A Compact Machine block (any size)
- ✅ Personal Shrinking Device (to teleport into CM)
- ✅ Basic understanding of Compact Machines

## What You'll Need

### Crafting Recipes
1. **PreFab Upgrade Template** - Converts a Compact Machine into a PreFab
2. **Simulation Wrench** - Controls PreFab state and opens configuration GUI
3. **Importer Block** - Input gate for factory (place inside CM)
4. **Exporter Block** - Output gate for factory (place inside CM)

## Step-by-Step Setup

### 1. Build Your Factory Inside a Compact Machine

1. Place a Compact Machine block in the world
2. Right-click with Personal Shrinking Device to enter
3. Build your factory inside (example: furnace with chests)

**Example Simple Factory**:
```
[Chest] → [Furnace] → [Chest]
```

### 2. Place Importer and Exporter Blocks

**Importers** receive resources from the Overworld:
- Actively push items into adjacent inventories

**Exporters** send resources to the Overworld:
- Actively pull items from adjacent inventories

**Example (With Chests - Recommended)**:
```
[Importer #1] → [Chest] → [Furnace input]
[Furnace output] → [Chest] → [Exporter #1]
```
💡 **Best practice**: Use intermediate chests for more reliable systems

💡 **Tip**: Importers/Exporters have unique IDs - you'll see them in the PreFab GUI later

### 3. Upgrade the Compact Machine to a PreFab

1. Exit the CM dimension (right-click Personal Shrinking Device)
2. Hold the PreFab Upgrade Template item
3. Right-click the Compact Machine block
4. The CM becomes a PreFab (block texture changes)

### 4. Configure PreFab Faces

1. Hold the Simulation Wrench
2. Right-click the PreFab
3. The Face Configuration GUI opens

**For each face you want to use**:
1. Select a face (North, South, East, West, Up, Down)
2. Set **Mode**:
   - `PULL` - Extract from Overworld → Send to Importer
   - `PUSH` - Extract from Exporter → Send to Overworld
   - `DISABLED` - Face inactive
3. Set **Resource Filter**:
   - `ITEMS` - Transfer items only
   - `FLUIDS` - Transfer fluids only (future)
   - `ENERGY` - Transfer energy only (future)
   - `ALL` - Transfer all resource types
4. Set **Target**:
   - Select the Importer or Exporter from dropdown
   - The dropdown shows all Importers/Exporters in the CM dimension

**Example configuration**:
```
NORTH face: PULL ITEMS → Importer #1 (coal input)
SOUTH face: PUSH ITEMS ← Exporter #1 (iron output)
All other faces: DISABLED
```

### 5. Connect Overworld Chests

Place chests next to the PreFab faces you configured:
- PULL faces: Chest must have input materials
- PUSH faces: Chest receives output products

**Example**:
```
[Coal Chest] → [PreFab NORTH face]
[PreFab SOUTH face] → [Iron Ingot Chest]
```

### 6. Start Simulation (Calibration)

1. With empty hand or any item (No Simulation Wrench)
2. Right-click PreFab
3. PreFab enters `SIMULATING` state
4. CM chunks load (factory actually runs)
5. PreFab measures production rates

**What's happening**:
- Resources flow from input chest → Importer → Furnace
- Products flow from Furnace → Exporter → Output chest
- PreFab counts how many items/tick are transported
- Wait 30-60 seconds for accurate measurement

### 7. Finish Simulation (Enter CACHED Mode)

1. Right-click PreFab with Simulation Wrench again
2. PreFab calculates production rates
3. PreFab enters `CACHED` state
4. **CM chunks unload** (performance gain)

### 8. Watch It Run

Your factory now runs "virtually":
- Add coal and iron ore to input chests
- Iron ingots appear in output chest
- CM dimension chunks stay unloaded
- Check with F3 screen (chunk count should be lower)

**Rate example**:
- If simulation measured 0.213 iron/tick
- PreFab will push 1 iron every ~4.7 ticks
- This matches real furnace production without loading chunks.

## Your First Factory: Iron Smelting

### Inside CM Dimension:
```
[Importer "Coal"] → [Furnace (fuel slot)]
[Importer "Iron Ore"] → [Furnace (input slot)]
[Furnace (output slot)] → [Exporter "Iron Ingots"]
```

### PreFab Configuration:
```
NORTH: PULL ITEMS → Importer "Coal"
EAST:  PULL ITEMS → Importer "Iron Ore"
SOUTH: PUSH ITEMS ← Exporter "Iron Ingots"
WEST:  DISABLED
UP:    DISABLED
DOWN:  DISABLED
```

### Overworld Setup:
```
            [Coal Chest]
                 ↓
[Ore Chest] → [PreFab]
                 ↓
            [Iron Chest]
```

## Understanding PreFab States

### BUILDING (Initial)
- PreFab is idle, waiting for configuration
- Configure faces and connect chests

### SIMULATING (Measuring)
- CM chunks are LOADED
- PreFab measures actual production
- Wait 30-60 seconds

### CACHED (Running Virtually)
- CM chunks are UNLOADED ← This is the performance gain.
- PreFab simulates production using math
- Add inputs → outputs appear based on cached rates

### HALTED (Problem Detected)
- Input ran out or output blocked
- CM chunks STAY unloaded
- Fix Overworld side (add inputs, clear outputs)
- Back to SIMULATING automatically (usually within 5 seconds)

## Frequency System (Optional)

You can "name" Importers and Exporters for easy organization:

1. Hold any item (e.g., Coal)
2. Right-click an Importer/Exporter
3. That gate is now named after the item

**Example**:
- Right-click Importer with Coal → Shows as "Coal Importer" in GUI
- Right-click Exporter with Iron Ingot → Shows as "Iron Ingot Exporter"

This does not change functionality, just makes it easier to identify gates.

## Next Steps

- Read [Face Configuration Guide](Face-Configuration) for detailed GUI walkthrough
- Learn about [State Machine](State-Machine-Guide) for understanding transitions
- Explore [Cached Production](Cached-Production) to understand the math
- Check [Troubleshooting](Troubleshooting) if something doesn't work

## Common Mistakes

❌ **Forgetting to place Importers/Exporters** - PreFab needs gates to link to
❌ **Not connecting chests** - PULL/PUSH faces need adjacent inventories
❌ **Ending simulation too early** - Wait for accurate rates

## Tips

💡 Use frequency system to name gates before configuring PreFab
💡 Start with a simple factory (single furnace) to learn the system
💡 Monitor chunk count in F3 screen to verify chunks unload
💡 If rates seem wrong, run SIMULATING longer (60+ seconds)
💡 Keep input chests stocked to avoid HALTED state
