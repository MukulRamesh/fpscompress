# Simulation Control: Simulation Wrench

## Overview

The `FactoryIntegrator` requires player-initiated control for two critical actions:
1. **Start Simulation**: Begin the rate calculation phase (BUILDING → SIMULATING)
2. **End Simulation**: Complete rate calculation and enter CACHED mode (SIMULATING → CACHED)

**Control Mechanism**: **Simulation Wrench** (Physical Tool)

This document provides the complete implementation guide for the Simulation Wrench control system.

---

## Why a Physical Tool?

The Simulation Wrench approach was chosen for:

✅ **Simplicity**: ~30 lines of code, no GUI complexity
✅ **Fast Development**: No client/server sync complexity
✅ **No Cross-Dev Dependencies**: Dev 1 doesn't wait for Dev 2's GUI work
✅ **Aligns with Constraints**: CLAUDE.md specifies "No GUI for MachinePortalBlockEntity"
✅ **Intuitive UX**: Right-click = action (standard Minecraft pattern)
✅ **Easily Upgradeable**: Can add GUI in v2.0 without refactoring

---

## Implementation

### Item Definition

**File**: `SimulationWrenchItem.java`
**Package**: `com.mukulramesh.fpscompress.portal`
**Developer**: Dev 1

```java
package com.mukulramesh.fpscompress.portal;

import com.mukulramesh.fpscompress.integration.FactoryIntegrator;
import com.mukulramesh.fpscompress.logic.IMachineLogic;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;

/**
 * Simulation Wrench - Control tool for TPS-Cached Compact Machines.
 *
 * Right-click on machine_portal block to:
 * - Start simulation (BUILDING → SIMULATING)
 * - End simulation (SIMULATING → CACHED)
 */
public class SimulationWrenchItem extends Item {

    public SimulationWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Only run on server
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        BlockEntity be = context.getLevel().getBlockEntity(pos);

        // Check if clicking on a machine_portal block
        if (be instanceof MachinePortalBlockEntity portal) {
            FactoryIntegrator integrator = portal.getIntegrator();

            // Ensure integrator exists (TPS upgrade installed)
            if (integrator == null) {
                context.getPlayer().displayClientMessage(
                    Component.literal("§cNo TPS upgrade installed!"), true);
                return InteractionResult.FAIL;
            }

            IMachineLogic.State state = integrator.logic.getCurrentState();

            switch (state) {
                case BUILDING:
                    // Start simulation
                    integrator.beginSimulation();
                    context.getPlayer().displayClientMessage(
                        Component.literal("§aSimulation started!"), true);
                    break;

                case SIMULATING:
                    // End simulation
                    integrator.endSimulation();
                    context.getPlayer().displayClientMessage(
                        Component.literal("§aSimulation complete! Machine cached."), true);
                    break;

                case CACHED:
                    // Already cached
                    context.getPlayer().displayClientMessage(
                        Component.literal("§eMachine is already cached."), true);
                    break;

                case HALTED:
                    // Machine halted, need to fix inputs/outputs
                    context.getPlayer().displayClientMessage(
                        Component.literal("§cMachine halted! Check inputs/outputs."), true);
                    break;
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
```

### Registration (Dev 1)

**File**: `FPSCompress.java` or dedicated registry class

```java
public static final DeferredRegister<Item> ITEMS =
    DeferredRegister.create(Registries.ITEM, FPSCompress.MOD_ID);

public static final DeferredItem<SimulationWrenchItem> SIMULATION_WRENCH =
    ITEMS.register("simulation_wrench",
        () -> new SimulationWrenchItem(new Item.Properties()));
```

### Recipe

**File**: `data/fpscompress/recipes/simulation_wrench.json`

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    " G ",
    " SG",
    "S  "
  ],
  "key": {
    "G": { "item": "minecraft:gold_ingot" },
    "S": { "item": "minecraft:stick" }
  },
  "result": {
    "item": "fpscompress:simulation_wrench"
  }
}
```

---

## Client Assets (Dev 2)

### Texture

**File**: `assets/fpscompress/textures/item/simulation_wrench.png`
**Dimensions**: 16x16 pixels

**Design Guidelines**:
- Base: Gray/silver metal (wrench head)
- Handle: Brown wood or black rubber grip
- Accent: Gold/yellow highlight (indicates "TPS" theme)
- Style: Minecraft vanilla aesthetic
- Inspiration: Golden wrench, adjustable wrench shape

**Example Pixel Art**:
```
Wrench head: Light gray (#C0C0C0)
Handle: Brown (#8B4513)
Accent: Gold (#FFD700)
Outline: Dark gray (#404040)
```

### Item Model

**File**: `assets/fpscompress/models/item/simulation_wrench.json`

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "fpscompress:item/simulation_wrench"
  }
}
```

### Localization

**File**: `assets/fpscompress/lang/en_us.json`

```json
{
  "item.fpscompress.simulation_wrench": "Simulation Wrench",
  "item.fpscompress.simulation_wrench.tooltip": "Right-click a TPS-Cached Machine to control simulation"
}
```

---

## MachinePortalBlockEntity Integration (Dev 1)

The `MachinePortalBlockEntity` must expose the `FactoryIntegrator`:

```java
public class MachinePortalBlockEntity extends BlockEntity {
    private FactoryIntegrator integrator;

    /**
     * Get the FactoryIntegrator for this machine.
     * Returns null if TPS upgrade is not installed.
     */
    public FactoryIntegrator getIntegrator() {
        return this.integrator;
    }

    /**
     * Initialize integrator when TPS upgrade is installed.
     * Called when player installs the upgrade item.
     */
    public void installTpsUpgrade() {
        // Obtain dependencies from other modules
        IVirtualMachineData virtualData = this; // Self-implements interface
        ICMInterceptor chunkManager = /* Dev 3's implementation */;
        IMachineLogic machineLogic = /* Dev 4's implementation */;
        IAntiCheatScanner scanner = /* Dev 5's implementation */;

        // Get CM dimension info from Compact Machines API
        ServerLevel cmDimension = /* Compact Machines dimension */;
        String roomCode = /* This machine's room code */;
        BoundingBox roomBounds = /* Room bounding box */;

        // Create integrator
        this.integrator = new FactoryIntegrator(
            virtualData,
            chunkManager,
            machineLogic,
            scanner,
            cmDimension,
            roomCode,
            roomBounds
        );
    }
}
```

---

## User Experience Flow

### 1. Obtaining the Wrench

```
Player crafts:
  2 Gold Ingots + 2 Sticks → Simulation Wrench
```

### 2. Starting Simulation (BUILDING → SIMULATING)

```
1. Player builds factory inside Compact Machine
2. Player installs TPS upgrade in machine_portal block
3. Player right-clicks machine_portal with Simulation Wrench
4. Message: "§aSimulation started!"
5. Factory runs physically, rates are observed
```

### 3. Ending Simulation (SIMULATING → CACHED)

```
1. Player waits for factory to run (e.g., 30 seconds)
2. Player right-clicks machine_portal with Simulation Wrench
3. Anti-cheat validation runs (Dev 5)
4. If valid:
   - Message: "§aSimulation complete! Machine cached."
   - Chunks unload, math-only mode begins
5. If invalid:
   - Message: "§cValidation failed! Hidden resources detected."
   - Machine returns to BUILDING state
```

### 4. Cached Operation

```
- Factory runs in math-only mode
- No chunks loaded in CM dimension
- Virtual buffers in Overworld block route resources
- If inputs starve or outputs block → HALTED state
```

### 5. Halted Recovery

```
1. Machine auto-transitions to HALTED when cache breaks
2. Chunks auto-reload
3. Player right-clicks with wrench: "§cMachine halted! Check inputs/outputs."
4. Player fixes input/output routing
5. Player right-clicks again to restart simulation
```

---

## State Messages Reference

| State | Message | Color |
|-------|---------|-------|
| BUILDING (start simulation) | "Simulation started!" | Green (§a) |
| SIMULATING (end simulation) | "Simulation complete! Machine cached." | Green (§a) |
| CACHED (already cached) | "Machine is already cached." | Yellow (§e) |
| HALTED (needs fix) | "Machine halted! Check inputs/outputs." | Red (§c) |
| No TPS Upgrade | "No TPS upgrade installed!" | Red (§c) |
| Validation Failed | "Validation failed! Hidden resources detected." | Red (§c) |

**Color Codes**:
- `§a` = Green (success)
- `§e` = Yellow (warning/info)
- `§c` = Red (error)

---

## Testing Checklist

### Dev 1 (Core Registry)

- [ ] Register `SimulationWrenchItem` in DeferredRegister
- [ ] Implement `useOn()` method with state checking
- [ ] Add `getIntegrator()` method to `MachinePortalBlockEntity`
- [ ] Test wrench on non-portal blocks (should do nothing)
- [ ] Test wrench without TPS upgrade (should show error)
- [ ] Test wrench in each state (BUILDING, SIMULATING, CACHED, HALTED)

### Dev 2 (Client Assets)

- [ ] Create 16x16 wrench texture
- [ ] Generate item model JSON
- [ ] Add localization entries
- [ ] Test texture renders correctly in inventory
- [ ] Test tooltip displays correctly

### Integration Testing

- [ ] Right-click with wrench in BUILDING → starts simulation
- [ ] Right-click with wrench in SIMULATING → ends simulation (if valid)
- [ ] Right-click with wrench in CACHED → shows "already cached" message
- [ ] Right-click with wrench in HALTED → shows "halted" message
- [ ] Anti-cheat validation rejects invalid loops
- [ ] Anti-cheat validation accepts valid loops
- [ ] State transitions trigger correct chunk loading/unloading (Dev 3)
- [ ] Fractional production runs correctly in CACHED mode (Dev 4)

---

## Advanced Features (Future v2.0+)

Once the core system is stable, consider enhancing the wrench:

### 1. Tooltip Information

```java
@Override
public void appendHoverText(ItemStack stack, TooltipContext context,
                           List<Component> tooltip, TooltipFlag flag) {
    tooltip.add(Component.literal("§7Right-click a TPS-Cached Machine"));
    tooltip.add(Component.literal("§7to control simulation"));
}
```

### 2. Particle Effects

```java
// On simulation start/end
level.addParticle(ParticleTypes.HAPPY_VILLAGER,
    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
    0, 0.1, 0);
```

### 3. Sound Effects

```java
// On simulation start
level.playSound(null, pos, SoundEvents.ANVIL_USE,
    SoundSource.BLOCKS, 1.0F, 1.0F);

// On simulation end
level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
    SoundSource.BLOCKS, 1.0F, 1.2F);
```

### 4. Visual State Indicator

Add overlay text when looking at a machine_portal block while holding wrench:

```java
// Client-side overlay rendering (Dev 2)
@SubscribeEvent
public static void onRenderGameOverlay(RenderGuiOverlayEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    ItemStack held = mc.player.getMainHandItem();

    if (held.getItem() instanceof SimulationWrenchItem) {
        HitResult hit = mc.hitResult;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            BlockEntity be = mc.level.getBlockEntity(pos);

            if (be instanceof MachinePortalBlockEntity) {
                // Render state overlay
                // "State: BUILDING" or "State: CACHED", etc.
            }
        }
    }
}
```

### 5. Multi-Mode Wrench

Add shift-click for additional functions:
- **Right-click**: Start/End simulation
- **Shift + Right-click**: Display detailed statistics
- **Ctrl + Right-click**: Force reset to BUILDING state (admin only)

---

## Developer Assignment Summary

### Dev 1 Checklist
- [x] Create `SimulationWrenchItem.java`
- [ ] Register item in DeferredRegister
- [ ] Implement `useOn()` logic
- [ ] Add `getIntegrator()` to `MachinePortalBlockEntity`
- [ ] Wire to `FactoryIntegrator.beginSimulation()` / `endSimulation()`
- [ ] Test all state transitions

### Dev 2 Checklist
- [ ] Create `simulation_wrench.png` texture (16x16)
- [ ] Generate item model JSON
- [ ] Add localization entries
- [ ] (Optional) Add tooltip text
- [ ] Test rendering in-game

### Integration Team Checklist
- [ ] Verify wrench calls integrator methods correctly
- [ ] Test anti-cheat validation flow
- [ ] Test chunk loading transitions (Dev 3)
- [ ] Test fractional production in CACHED mode (Dev 4)
- [ ] Document edge cases and error handling

---

## Conclusion

The **Simulation Wrench** provides a simple, intuitive control mechanism for the TPS caching system. It requires minimal code (~30-50 lines), no GUI complexity, and aligns perfectly with the modular architecture.

**Development Timeline**:
- Dev 1: 1-2 hours (item implementation + BlockEntity integration)
- Dev 2: 1 hour (texture + model + lang)
- Integration: 30 minutes (testing and validation)

**Total**: ~3-4 hours for complete wrench implementation

This approach gets the mod functional quickly while leaving the door open for GUI enhancements in future versions.
