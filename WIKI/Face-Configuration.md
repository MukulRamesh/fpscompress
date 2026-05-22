# Face Configuration Guide

This guide provides a step-by-step walkthrough of the PreFab Face Configuration GUI.

## Opening the GUI

1. Hold **Simulation Wrench** item
2. **Shift + Right-click** the PreFab block
3. Face Configuration GUI opens

## GUI Layout

```
┌──────────────────────────────────────────────────┐
│  PreFab Configuration                            │
├──────────────────────────────────────────────────┤
│                                                  │
│  Current Face: [NORTH ▼]                         │
│                                                  │
│  Mode:   [PULL ▼]                                │
│  Filter: [ITEMS ▼]                               │
│  Target: [Coal Importer ▼]                       │
│                                                  │
│  [Apply] [Cancel]                                │
│                                                  │
└──────────────────────────────────────────────────┘
```

## Configuration Steps

### Step 1: Select Face

**Face directions** (standing in front of PreFab):
- **NORTH**: Side facing north (check F3 screen)
- **SOUTH**: Side facing south
- **EAST**: Side facing east
- **WEST**: Side facing west
- **UP**: Top side
- **DOWN**: Bottom side

💡 **Tip**: Use F3 screen to see which direction you're facing, then orient PreFab accordingly

### Step 2: Set Mode

**Mode options**:

| Mode | Description | Use Case |
|------|-------------|----------|
| **DISABLED** | Face inactive, no transport | Unused faces |
| **PULL** | Extract from Overworld → Transport to CM Importer | Input resources (coal, ore, etc.) |
| **PUSH** | Extract from CM Exporter → Transport to Overworld | Output products (ingots, items, etc.) |

**Choosing PULL vs PUSH**:
- **PULL** when you want to send items FROM Overworld TO factory
- **PUSH** when you want to send items FROM factory TO Overworld

### Step 3: Set Resource Filter

**Filter options**:

| Filter | Description | Status |
|--------|-------------|--------|
| **ALL** | Transfer all resource types | 🔨 Future |
| **ITEMS** | Transfer items only (ItemStacks) | ✅ Works now |
| **FLUIDS** | Transfer fluids only (FluidStacks) | 🔨 Future |
| **ENERGY** | Transfer energy only (FE/RF) | 🔨 Future |

**Current recommendation**: Use **ITEMS** for now (only fully implemented filter)

### Step 4: Select Target

**Target selection rules**:
- **PULL faces** must link to Importers (input gates)
- **PUSH faces** must link to Exporters (output gates)
- Dropdown shows all Importers/Exporters in the CM dimension
- Names come from frequency system (or UUID if not named)

**If dropdown is empty**:
1. No Importers/Exporters placed yet
2. Enter CM dimension, place gates
3. Reopen GUI - dropdown should populate

### Step 5: Apply Configuration

Click **[Apply]** button to save configuration

**What happens**:
- Face config is saved to PreFab's NBT data
- Network packet sent to server
- Face is now active (if mode is PULL or PUSH)

Click **[Cancel]** to discard changes

## Example Configurations

### Example 1: Simple Furnace Setup

**Goal**: Smelt iron ore using coal

**Configuration**:

**NORTH Face**:
- Mode: PULL
- Filter: ITEMS
- Target: Coal Importer
- Connect: Coal chest to NORTH side of PreFab

**EAST Face**:
- Mode: PULL
- Filter: ITEMS
- Target: Iron Ore Importer
- Connect: Ore chest to EAST side of PreFab

**SOUTH Face**:
- Mode: PUSH
- Filter: ITEMS
- Target: Iron Ingot Exporter
- Connect: Output chest to SOUTH side of PreFab

**All other faces** (WEST, UP, DOWN):
- Mode: DISABLED

### Example 2: Complex Processing

**Goal**: Multi-input, multi-output factory

**Configuration**:

```
NORTH: PULL ITEMS → Coal Importer
SOUTH: PUSH ITEMS ← Primary Product Exporter
EAST:  PULL ITEMS → Ore Importer
WEST:  PUSH ITEMS ← Byproduct Exporter
UP:    PULL ITEMS → Catalyst Importer
DOWN:  DISABLED
```

**Overworld setup**:
```
         [Catalyst Chest]
                ↓ (UP face)
[Ore Chest] → [PreFab] → [Byproduct Chest]
        ↑                    ↓
   (EAST face)          (WEST face)
   [Coal Chest]    [Product Chest]
        ↓                ↑
   (NORTH face)    (SOUTH face)
```

### Example 3: Vertical Setup

**Goal**: PreFab above floor, using UP/DOWN faces

**Configuration**:

**DOWN Face**:
- Mode: PULL
- Filter: ITEMS
- Target: Input Importer
- Connect: Hopper below PreFab feeding from chest

**UP Face**:
- Mode: PUSH
- Filter: ITEMS
- Target: Output Exporter
- Connect: Hopper above PreFab feeding to chest

Compact vertical design.

## Advanced Configuration

### Using All 6 Faces

You can configure all 6 faces for maximum throughput:

**Example: 6-Input Factory**
```
NORTH: PULL → Importer #1 (Coal)
SOUTH: PULL → Importer #2 (Iron Ore)
EAST:  PULL → Importer #3 (Gold Ore)
WEST:  PULL → Importer #4 (Copper Ore)
UP:    PULL → Importer #5 (Tin Ore)
DOWN:  PULL → Importer #6 (Catalyst)
```

All faces operate independently.

### Multiple Outputs

**Example: Ore Processing**
```
NORTH: PULL → Ore Importer
SOUTH: PUSH ← Ingot Exporter
EAST:  PUSH ← Dust Exporter
WEST:  PUSH ← Nugget Exporter
```

Separate chests for each product type.

### Bidirectional Faces (NOT POSSIBLE)

**Can a face be both PULL and PUSH?** ❌ No

Each face can only be:
- DISABLED (inactive)
- PULL (Overworld → CM)
- PUSH (CM → Overworld)

## Configuration Persistence

### When Config is Saved

Configuration persists:
- ✅ When PreFab block is broken and replaced
- ✅ When server restarts
- ✅ When chunk unloads/reloads
- ✅ When PreFab is picked up as item (PreFab-as-Item feature)

**Where config is stored**: PreFab's NBT data

### Changing Configuration

You can reconfigure faces at any time:
1. Open GUI (Shift + Right-click with wrench)
2. Change mode/filter/target
3. Click Apply

## GUI Tips and Tricks

### Tip 1: Use Frequency System First

**Before configuring PreFab faces**:
1. Enter CM dimension
2. Name all Importers/Exporters with frequency system
3. Exit CM dimension
4. Configure PreFab faces

**Result**: Dropdown shows clear names like "Coal Importer" instead of "Importer (abc-123)"

### Tip 2: Configure One Face at a Time

Don't try to configure all 6 faces at once:
1. Configure NORTH face → Test (place chest, verify items flow)
2. Configure SOUTH face → Test
3. Continue for other faces

Easier to debug if something goes wrong.

### Tip 3: Check Adjacent Blocks

**Before setting PULL/PUSH**:
- PULL faces need adjacent inventories in Overworld (chests, hoppers)
- PUSH faces need adjacent inventories in Overworld (chests, hoppers)

Place chests before configuring faces.

### Tip 4: Orient PreFab Correctly

PreFab face directions are absolute (based on world directions, not player facing):
- Use F3 screen to see world directions
- Place PreFab with desired orientation
- Configure faces based on absolute directions

### Tip 5: Color-Code Chests

**In Overworld**:
- Blue chests for inputs (PULL faces)
- Red chests for outputs (PUSH faces)

Visual indicator helps remember which side is which.

## Troubleshooting

### Problem: "Target dropdown is empty"

**Cause**: No Importers/Exporters in CM dimension

**Solution**:
1. Enter CM dimension
2. Place at least one Importer or Exporter
3. Exit CM
4. Reopen GUI

### Problem: "Face configuration doesn't save"

**Checklist**:
1. ✅ Did you click [Apply] button?
2. ✅ Server/client sync issue? Relog and check.

### Problem: "Can't PULL from chest"

**Checklist**:
1. ✅ Chest adjacent to PreFab face?
2. ✅ Face mode set to PULL?
3. ✅ Face linked to Importer?
4. ✅ Chest has items?
5. ✅ PreFab in SIMULATING or CACHED state?

### Problem: "Can't PUSH to chest"

**Checklist**:
1. ✅ Chest adjacent to PreFab face?
2. ✅ Face mode set to PUSH?
3. ✅ Face linked to Exporter?
4. ✅ Exporter has items to send?
5. ✅ Chest has space?
6. ✅ PreFab in SIMULATING or CACHED state?

## See Also

- [Getting Started](Getting-Started) - First-time setup guide
- [PreFab System](PreFab-System) - Understanding PreFabs and faces
- [Importer & Exporter Guide](Importer-Exporter-Guide) - Setting up gates
- [State Machine Guide](State-Machine-Guide) - Understanding PreFab states
