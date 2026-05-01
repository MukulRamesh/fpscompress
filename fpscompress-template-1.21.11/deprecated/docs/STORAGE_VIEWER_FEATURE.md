# PreFab Storage Viewer Feature

**Added**: 2026-04-26  
**Purpose**: Visual confirmation of virtual buffer contents without needing a GUI

---

## What It Does

Right-clicking a PreFab block (without holding a Personal Shrinking Device) displays storage statistics in chat.

## Usage

```
1. Place a PreFab block
2. Right-click it (empty hand or any item except PSD)
3. Storage stats appear in chat
```

## Example Output

```
§6=== PreFab Virtual Storage ===
§7Items: §a192 total §7(§b2 types§7)
§7  - cobblestone: §a128
§7  - iron_ingot: §a64
§7Fluids: §a50,000 mB total §7(§b1 types§7)
§7  - water: §a50,000 mB
§7Energy: §a1,000,000 FE
§7Room: §39L87-MNWG-Q6WG
§8(Hold PSD and right-click to enter)
```

## Display Format

### Items
- Shows total count and number of unique types
- Lists up to **5 different item types**
- If more than 5 types: shows "... and X more types"
- Amounts formatted with commas (e.g., "1,000" not "1000")

### Fluids
- Shows total mB and number of unique fluid types  
- Lists up to **3 different fluid types**
- If more than 3 types: shows "... and X more types"
- Amounts formatted with commas

### Energy
- Shows total Forge Energy (FE)
- Single unified energy pool (not per-type)
- Formatted with commas

### Room Code
- Shows linked CM room code (e.g., "9L87-MNWG-Q6WG")
- If not linked: "§cNot linked (upgrade from CM first)"

### Hint Text
- Bottom line reminds: "(Hold PSD and right-click to enter)"

---

## Color Coding

- **§6 Gold**: Section headers
- **§7 Gray**: Labels
- **§a Green**: Counts/amounts (success color)
- **§b Cyan**: Metadata (types, secondary info)
- **§3 Dark Cyan**: Room codes
- **§e Yellow**: "None" states
- **§c Red**: Warnings/errors
- **§8 Dark Gray**: Hints

---

## Technical Details

**Implementation**: `PrefabBlock.useWithoutItem()` → `displayStorageStats()`

**Data Source**:
- Items: `prefab.getItemSnapshot()` → `Map<String, Integer>`
- Fluids: `prefab.getFluidSnapshot()` → `Map<String, Integer>`
- Energy: `prefab.getBufferAmount(ENERGY, "energy")` → `long`
- Room: `prefab.getRoomCode()` → `String`

**Performance**: O(n) where n = number of unique resource types (negligible for typical usage)

---

## Benefits for Testing

### Before (without viewer):
- ❌ No visual confirmation of storage
- ❌ Must check logs to verify transfers
- ❌ Can't tell if hopper worked

### After (with viewer):
- ✅ Instant visual feedback
- ✅ See exact counts and types
- ✅ Confirm transfers worked immediately
- ✅ No need to parse logs

---

## Comparison to /testbuffer Command

| Feature | /testbuffer | Right-Click Viewer |
|---------|-------------|-------------------|
| **Scope** | Tests VirtualBufferStorage class directly | Shows PreFab BlockEntity contents |
| **Usage** | Requires OP permission | Any player can use |
| **Output** | Automated test results (pass/fail) | Current storage state |
| **Purpose** | Validate unlimited storage logic | View actual PreFab contents |
| **Location** | Creates temporary storage instance | Reads from placed block |

Both are useful for different purposes:
- `/testbuffer` validates the storage implementation
- Right-click viewer shows what's actually in a specific PreFab

---

## Future Enhancements (Optional)

### Possible improvements:
1. **Sorting**: Show most-stored items first
2. **Percentages**: Show % of total for each resource
3. **History**: Track recent additions/extractions
4. **Comparison**: Compare two PreFabs
5. **Export**: Generate JSON report of contents
6. **Filters**: `/prefab filter iron` to see only iron-related items

### Full GUI (v2.0):
- Inventory-style display with item icons
- Clickable buttons to extract specific amounts
- Visual progress bars for capacity
- Tabbed interface (Items | Fluids | Energy | Config)

For now, the chat-based viewer is sufficient for testing and provides instant feedback without GUI complexity.

---

## Integration with Other Features

### Works with:
- ✅ Hopper insertion (see counts increase in real-time)
- ✅ Smart extraction (see counts decrease)
- ✅ NBT preservation (view before/after breaking block)
- ✅ Multiple resource types (lists all types)
- ✅ Unlimited storage (shows large numbers correctly)

### Complements:
- Personal Shrinking Device (hold PSD + right-click = enter room)
- Simulation Wrench (different interaction when holding wrench)
- TPS Cache Upgrade (different interaction on CM blocks)

---

## Known Limitations

1. **No scrolling**: If you have 100 item types, only first 5 shown + "... and 95 more"
2. **No icons**: Text-only (no item/fluid sprites)
3. **Chat spam**: Repeated clicks = repeated messages (by design for testing)
4. **No filtering**: Can't search for specific items
5. **Read-only**: Can't extract items from this display (use hoppers)

All of these are acceptable trade-offs for a testing/debugging feature. A full GUI would address these but requires significantly more implementation time.

---

## Testing Checklist

When testing the storage viewer:

- [ ] Empty PreFab shows "None" for all categories
- [ ] Single item type displays correctly
- [ ] Multiple item types display (up to 5)
- [ ] 6+ item types show "... and X more" message
- [ ] Fluids display correctly
- [ ] Energy displays correctly
- [ ] Room code displays (or "Not linked" message)
- [ ] Comma formatting works (1000 → "1,000")
- [ ] Large numbers display (millions/billions)
- [ ] Color codes render correctly in chat
- [ ] Hint text appears at bottom
- [ ] No errors/crashes when viewing

---

**Status**: ✅ Implemented and compiled  
**File Modified**: `PrefabBlock.java` (added `displayStorageStats()` method)  
**Ready for Testing**: Yes
