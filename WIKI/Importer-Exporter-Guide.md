# Importer & Exporter Guide

Importers and Exporters are **input/output gates** that connect your Compact Machine factory to PreFab faces. They're essential for the three-block system that makes FPSCompress work.

## Overview

### What Are Importers?

**Importers** are blocks placed **inside the CM dimension** that act as input gates:
- Receive resources from PreFab PULL faces (from Overworld)
- Have small internal buffer (9 item slots)
- **Actively push items** into adjacent inventories
- Each Importer has a unique UUID for linking

**Think of Importers as**: "Where resources from the Overworld enter your factory"

### What Are Exporters?

**Exporters** are blocks placed **inside the CM dimension** that act as output gates:
- Send resources to PreFab PUSH faces (to Overworld)
- Have small internal buffer (9 item slots)
- Actively pull from adjacent machines every tick
- PreFab extracts from Exporters to send to Overworld
- Each Exporter has a unique UUID for linking

**Think of Exporters as**: "Where resources from your factory exit to the Overworld"

## Placement and Setup

### Placing Importers

**Step 1**: Enter CM dimension (right-click CM with Personal Shrinking Device)

**Step 2**: Identify where resources need to enter
- Next to furnace input slots
- Next to machine input sides

**Step 3**: Place Importer block

**Step 4** (Optional): Name the Importer using frequency system
- Hold an item (e.g., Coal)
- Right-click the Importer
- Importer now shows as "Coal Importer" in GUIs

**Example (With Intermediate Chest - Recommended)**:
```
[Importer "Coal"] → [Chest] → [Hopper / Pipe] → [Furnace fuel slot]
[Importer "Iron Ore"] → [Chest] → [Hopper / Pipe] → [Furnace input slot]
```
*Using intermediate chests + hoppers/pipes creates more reliable, better-behaved systems.*

### Placing Exporters

**Step 1**: Enter CM dimension

**Step 2**: Identify where products exit
- Next to furnace output slots
- Next to machine output sides

**Step 3**: Place Exporter block next to the output

**Step 4** (Optional): Name the Exporter using frequency system
- Hold an item (e.g., Iron Ingot)
- Right-click the Exporter
- Exporter now shows as "Iron Ingot Exporter" in GUIs

**Example (With Intermediate Chest - Recommended)**:
```
[Furnace output] → [Hopper / Pipe] → [Chest] → [Exporter "Iron Ingots"]
[Crusher output] → [Hopper / Pipe] → [Chest] → [Exporter "Crushed Ore"]
```
*Using hoppers/pipes + intermediate chests creates more reliable, better-behaved systems.*

## How Importers Work

### Internal Buffer

Importers have a **9-slot internal buffer**:
- PreFab inserts items into buffer
- Importer **actively pushes items** into adjacent inventories
- Buffer prevents item loss if target inventory is full

**Buffer states**:
- Empty: PreFab can insert up to 9 stacks
- Partial: Some space available
- Full: PreFab can't insert more (may trigger HALTED state)

### Active Pushing

Importers **actively push** items into adjacent inventories:
```
[Importer] → (pushes via IItemHandler) → [Chest/Machine]
```

This means Importers work proactively, not passively.

### Tick Behavior

Importers **actively push** items every tick:
- Receive items from PreFab
- Push items into adjacent inventories
- Act as an active middleman/buffer

## How Exporters Work

### Internal Buffer

Exporters have a **9-slot internal buffer**:
- Exporter pulls items from adjacent machines into buffer
- PreFab extracts items from buffer
- Buffer allows for timing differences between machine output and PreFab extraction

### Active Pulling

Exporters **actively pull** from adjacent machines every tick:
```java
// Every tick:
1. Check all 6 adjacent blocks
2. If block has IItemHandler capability:
3.   Try to extract items (up to 64 per slot)
4.   Insert extracted items into Exporter's buffer
```

This means Exporters work with machines that do not push (like furnaces).

### Tick Behavior

**Every game tick** (20 times per second):
1. Query all adjacent blocks for `IItemHandler`
2. Extract items from those blocks
3. Store in internal buffer
4. PreFab extracts from buffer when ready

## Frequency System

The **frequency system** lets you "name" Importers/Exporters for easy identification.

### How to Set Frequency

1. Hold any item (e.g., Coal)
2. Right-click Importer or Exporter
3. The gate is now "named" after that item

**Example**:
- Right-click Importer with Coal → "Coal Importer"
- Right-click Importer with Iron Ore → "Iron Ore Importer"
- Right-click Exporter with Iron Ingot → "Iron Ingot Exporter"

### Display in GUI

When you open PreFab configuration, the dropdown shows:
```
Without frequency:
- Unnamed Importer
- Unnamed Importer

With frequency:
- Coal Importer
- Iron Ore Importer
```

Much easier to identify which gate is which.

### Frequency Item Storage

The frequency item is stored in the Importer/Exporter's NBT data:
- Doesn't consume the item (you keep it)
- Persists when gate is broken and replaced
- Can be changed by right-clicking with different item

### When to Use Frequency

💡 **Best practice**: Set frequency BEFORE configuring PreFab faces

**Recommended workflow**:
1. Place all Importers/Exporters
2. Name each one with frequency system (Coal Importer, Iron Ore Importer, etc.)
3. Exit CM dimension
4. Configure PreFab faces (dropdown shows clear names)

## Resource Flow Examples

### Example 1: Simple Furnace

**Inside CM**:
```
[Coal Importer] → [Furnace fuel]
[Ore Importer] → [Furnace input]
[Furnace output] → [Ingot Exporter]
```

**PreFab Config**:
```
NORTH: PULL ITEMS → Coal Importer
EAST:  PULL ITEMS → Ore Importer
SOUTH: PUSH ITEMS ← Ingot Exporter
```

**Overworld**:
```
[Coal Chest] → [PreFab NORTH]
[Ore Chest] → [PreFab EAST]
[PreFab SOUTH] → [Ingot Chest]
```

### Example 2: Multi-Stage Processing

**Inside CM**:
```
[Ore Importer] → [Crusher] → [Dust] → [Furnace] → [Ingot Exporter]
```

**PreFab Config**:
```
NORTH: PULL ITEMS → Ore Importer
SOUTH: PUSH ITEMS ← Ingot Exporter
```

**Overworld**:
```
[Ore Chest] → [PreFab NORTH]
[PreFab SOUTH] → [Ingot Chest]
```

Intermediate products (dust) stay inside CM - only inputs/outputs cross dimensions.

### Example 3: Multiple Outputs

**Inside CM**:
```
[Ore Importer] → [Processing Machine] → [Primary Exporter]
                                      → [Byproduct Exporter]
```

**PreFab Config**:
```
NORTH: PULL ITEMS → Ore Importer
SOUTH: PUSH ITEMS ← Primary Exporter
WEST:  PUSH ITEMS ← Byproduct Exporter
```

**Overworld**:
```
[Ore Chest] → [PreFab NORTH]
[PreFab SOUTH] → [Primary Chest]
[PreFab WEST] → [Byproduct Chest]
```

## Advanced Topics

### Exporter Not Pulling

**Common causes**:
1. Adjacent machine doesn't have items to extract
2. Adjacent machine doesn't expose `IItemHandler` capability
3. Exporter buffer is full (PreFab isn't extracting fast enough)

**Debug**:
- Check machine output (is it producing items?)
- Try placing chest next to Exporter (test if Exporter pulls from chest)
- Check Exporter buffer (is it full? Items stuck?)

## Limitations

### Current Limitations (MVP)

- ✅ Items only (fluids/energy not yet supported)
- ✅ 9-slot buffer (fixed size, no upgrades)
- ❌ No filtering (Exporters pull ALL items from adjacent machines)
- ❌ No priority (can't specify "extract from slot X first")

### Future Features

- 🔨 Fluid support (tanks, fluid pipes)
- 🔨 Energy support (batteries, cables)
- 🔨 Filtered Importers/Exporters (whitelist/blacklist items)
- 🔨 Larger buffers via upgrades
- 🔨 Priority settings (extract specific slots first)
- 🔨 Visual indicators (particles showing transfer)

## Troubleshooting

### Problem: "Can't find Importer/Exporter in dropdown"

**Cause**: No Importers/Exporters placed in CM dimension

**Solution**:
1. Enter CM dimension
2. Place at least one Importer or Exporter
3. Exit CM dimension
4. Reopen PreFab GUI (dropdown should now show the gate)

### Problem: "Resources not entering factory"

**Checklist**:
1. ✅ Importer placed inside CM?
2. ✅ PreFab face set to PULL mode?
3. ✅ PreFab face linked to correct Importer UUID?
4. ✅ Chest connected to PreFab face in Overworld?
5. ✅ Chest has items?
6. ✅ PreFab in SIMULATING or CACHED state?

### Problem: "Resources not exiting factory"

**Checklist**:
1. ✅ Exporter placed next to machine output?
2. ✅ Machine producing items?
3. ✅ PreFab face set to PUSH mode?
4. ✅ PreFab face linked to correct Exporter UUID?
5. ✅ Chest connected to PreFab face in Overworld?
6. ✅ Chest has space for items?
7. ✅ PreFab in SIMULATING or CACHED state?



## See Also

- [Getting Started](Getting-Started) - First-time setup
- [PreFab System](PreFab-System) - Understanding PreFabs
- [Face Configuration](Face-Configuration) - Configuring PreFab faces
- [Troubleshooting](Troubleshooting) - Common issues and fixes
