# HALTED State GUI Display

## Overview
When a PreFab enters HALTED state (input starved or output blocked), the GUI now shows cached rates to help players understand what the factory needs/produces.

---

## Visual Layout

### Survival Mode (All Players)
```
┌─────────────────────────────────────┐
│    PreFab Status & Control          │
├─────────────────────────────────────┤
│ State: HALTED                       │ ← Red color
│ Room: 3K6Q-R3R1-G0QV                │
│                                     │
│ Input starved: 3and (128 needed)   │ ← Error message
│                                     │
│ ┌─────────────────────────────┐    │
│ │      Reset Cache            │    │ ← Button
│ └─────────────────────────────┘    │
│                                     │
│ Factory Requirements (HALTED):      │ ← Header (red)
│   Coal: -0.213/t                   │ ← Red (needs input)
│   Iron Ingot: +0.107/t             │ ← Green (produces output)
│   ...                               │
└─────────────────────────────────────┘
```

### Creative Mode
```
┌─────────────────────────────────────┐
│    PreFab Status & Control          │
├─────────────────────────────────────┤
│ State: HALTED                       │
│ Room: 3K6Q-R3R1-G0QV                │
│                                     │
│ Input starved: 3and (128 needed)   │
│                                     │
│ ┌─────────────────────────────┐    │
│ │      Reset Cache            │    │
│ └─────────────────────────────┘    │
│                                     │
│ Factory Requirements (HALTED):      │
│   Simulation Time: 600 ticks       │ ← Extra stats
│   Cached Ticks: 1234 ticks         │
│                                     │
│   Coal: 128/0 123 (-0.213/t)      │ ← Detailed format
│   Iron Ingot: 0/64 32 (+0.107/t)  │    (imported/exported cached rate)
└─────────────────────────────────────┘
```

---

## Why Show Rates in HALTED?

### Problem
Player enters HALTED state and sees:
- "Input starved: 3and (128 needed)"
- ❓ What is "3and"? (registry ID not user-friendly)
- ❓ What else does the factory need?
- ❓ What does it produce?

### Solution
Show cached rates:
- **Coal: -0.213/t** ← Factory consumes coal (negative = input)
- **Iron Ingot: +0.107/t** ← Factory produces iron (positive = output)

Now player knows:
- ✅ Factory needs coal input (the "3and" is sand, but coal is more critical)
- ✅ Factory produces iron output
- ✅ Rates tell them how fast (0.213 coal/tick = ~4.26 coal/second)

---

## User Flow

### Scenario: Input Starved
1. **PreFab enters HALTED**
   - Player removed coal chest (or chest is empty)
   - GUI shows "Input starved: 3and (128 needed)"

2. **Player opens Status GUI**
   - Sees rates: "Coal: -0.213/t"
   - Realizes: "Oh, it needs coal input!"

3. **Player fixes issue**
   - Places chest with coal on NORTH face

4. **Player clicks "Reset Cache"**
   - PreFab returns to BUILDING
   - Player can reconfigure if needed
   - Clicks "Start Simulation" to re-measure

### Scenario: Output Blocked
1. **PreFab enters HALTED**
   - Output chest is full (can't push more iron)
   - GUI shows "Output blocked: iron_ingot"

2. **Player opens Status GUI**
   - Sees rates: "Iron Ingot: +0.107/t"
   - Realizes: "Oh, it's producing iron but can't output!"

3. **Player fixes issue**
   - Empties iron chest on SOUTH face

4. **Player clicks "Reset Cache"**
   - Returns to BUILDING, re-simulate to continue

---

## Comparison: CACHED vs HALTED

### CACHED State (Normal Operation)
```
State: CACHED                   ← Green
Room: 3K6Q-R3R1-G0QV

┌─────────────────────────┐
│   Reset to Building     │
└─────────────────────────┘

Cached Rates:               ← No "Factory Requirements" header
  Coal: -0.213/t
  Iron Ingot: +0.107/t
```

### HALTED State (Cache Broken)
```
State: HALTED                   ← Red
Room: 3K6Q-R3R1-G0QV

Input starved: 3and (128 needed)

┌─────────────────────────┐
│      Reset Cache        │
└─────────────────────────┘

Factory Requirements (HALTED):  ← Red header, more urgent
  Coal: -0.213/t
  Iron Ingot: +0.107/t
```

**Key difference**: HALTED shows rates + error message, emphasizing urgency.

---

## Technical Details

### Code Location
File: `PreFabStatusScreen.java`

**Line 270-274**: Render stats for both CACHED and HALTED
```java
if (syncedState == MachineState.SIMULATING) {
    renderSimulatingStats(graphics);
} else if (syncedState == MachineState.CACHED || syncedState == MachineState.HALTED) {
    renderCachedStats(graphics);
}
```

**Line 348-354**: Show header for HALTED
```java
if (syncedState == MachineState.HALTED) {
    Component headerText = Component.literal("§cFactory Requirements (HALTED):");
    graphics.drawString(font, headerText, 10, yOffset, 0xFFFFFF, false);
    yOffset += 12;
}
```

### Data Synced to Client
- `syncedCachedRates`: Map<String, Double> (resource ID → rate per tick)
- `syncedState`: MachineState (CACHED or HALTED)
- `syncedLastSimulationResult`: String ("Input starved: 3and (128 needed)")

---

## Benefits

### For Players
✅ **Clarity**: See what factory needs without guessing  
✅ **Troubleshooting**: Quickly identify missing inputs/blocked outputs  
✅ **Decision-making**: Decide if worth fixing or redesigning factory  

### For Developers
✅ **Consistent**: Same `renderCachedStats()` method for CACHED and HALTED  
✅ **Minimal code**: Just added one header line + state check  
✅ **Maintainable**: Future changes to rate display apply to both states  

---

## Future Improvements (Post-MVP)

### v1.1: Enhanced Error Messages
Replace registry IDs with localized names:
- Before: "Input starved: 3and (128 needed)"
- After: "Input starved: Sand (128 needed)"

### v1.2: Visual Indicators
Add icons next to rates:
- ⬇️ Coal: -0.213/t (input indicator)
- ⬆️ Iron Ingot: +0.107/t (output indicator)

### v1.3: Sorting
Show resources in priority order:
1. Most critical inputs (highest negative rate)
2. Most valuable outputs (highest positive rate)

### v1.4: Demand Forecast
Show how long until inputs exhausted:
- Coal: -0.213/t (12 minutes remaining in chest)

---

## Testing Checklist

When testing HALTED state, verify:

✅ Rates are visible in HALTED state  
✅ Header says "Factory Requirements (HALTED):" (red)  
✅ Rates show in both survival and creative modes  
✅ Negative rates (red) indicate inputs needed  
✅ Positive rates (green) indicate outputs produced  
✅ Error message shows above rates ("Input starved: ...")  
✅ Button says "Reset Cache" (not "Resume Simulation")  
✅ Clicking button returns to BUILDING (clears rates)  

---

**Implementation Date**: 2026-05-06  
**Status**: ✅ Complete and tested  
**Build**: `fpscompress-template-1.21.11-1.0.0.jar`
