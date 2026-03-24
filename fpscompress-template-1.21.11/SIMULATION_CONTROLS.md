# Simulation Control Strategy

## Overview

The `FactoryIntegrator` requires player-initiated control for two critical actions:
1. **Start Simulation**: Begin the rate calculation phase (BUILDING → SIMULATING)
2. **End Simulation**: Complete rate calculation and enter CACHED mode (SIMULATING → CACHED)

This document outlines two implementation approaches and provides a recommendation.

## Option 1: Physical Tool (Simulation Wrench)

### Concept

A handheld tool (e.g., "TPS Calibrator" or "Simulation Wrench") that players right-click on the `machine_portal` block to trigger simulation control.

### Implementation

```java
public class SimulationWrenchItem extends Item {
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;

        BlockPos pos = context.getClickedPos();
        BlockEntity be = context.getLevel().getBlockEntity(pos);

        if (be instanceof MachinePortalBlockEntity portal) {
            FactoryIntegrator integrator = portal.getIntegrator();
            IMachineLogic.State state = integrator.logic.getCurrentState();

            if (state == IMachineLogic.State.BUILDING) {
                integrator.beginSimulation();
                context.getPlayer().displayClientMessage(
                    Component.literal("Simulation started!"), true);
            } else if (state == IMachineLogic.State.SIMULATING) {
                integrator.endSimulation();
                context.getPlayer().displayClientMessage(
                    Component.literal("Simulation complete!"), true);
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
```

### Pros

✅ **No GUI programming required** - Pure item interaction
✅ **Simple implementation** - ~30 lines of code
✅ **No client/server sync complexity** - `useOn()` runs on server
✅ **Intuitive UX** - Right-click = action
✅ **Can be gated behind crafting** - Requires resources to obtain tool
✅ **Works with modpacks** - Easily configured in recipes

### Cons

❌ **No visual feedback** - Player can't see current state without clicking
❌ **Requires tool in inventory** - Extra step for players
❌ **Limited information display** - Action bar messages only
❌ **No progress tracking** - Can't show simulation time elapsed

### Assignment

- **Dev 1**: Register the `SimulationWrenchItem` in `DeferredRegister<Item>`
- **Dev 2**: Create 16x16 PNG texture for wrench (e.g., gold wrench icon)
- **Integration**: Wire `useOn()` to call `integrator.beginSimulation()` / `endSimulation()`

---

## Option 2: Simple GUI Screen

### Concept

Right-clicking the `machine_portal` block opens a minimal GUI with state display and buttons.

### Implementation

**Server-side (Dev 1):**
```java
public class MachinePortalBlock extends Block {
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachinePortalBlockEntity portal) {
                player.openMenu(portal);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

public class MachinePortalBlockEntity extends BlockEntity implements MenuProvider {
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SimulationControlMenu(id, inv, this.getBlockPos());
    }
}
```

**Client-side (Dev 2):**
```java
public class SimulationControlScreen extends Screen {
    private Button startButton;
    private Button endButton;

    @Override
    protected void init() {
        // Button: "Start Simulation" (enabled only in BUILDING state)
        this.startButton = Button.builder(
            Component.literal("Start Simulation"),
            btn -> sendPacketToServer(SimulationAction.START)
        ).bounds(this.width / 2 - 50, this.height / 2 - 10, 100, 20).build();

        // Button: "End Simulation" (enabled only in SIMULATING state)
        this.endButton = Button.builder(
            Component.literal("End Simulation"),
            btn -> sendPacketToServer(SimulationAction.END)
        ).bounds(this.width / 2 - 50, this.height / 2 + 15, 100, 20).build();

        this.addRenderableWidget(startButton);
        this.addRenderableWidget(endButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Display current state
        String stateText = "State: " + getCurrentState().name();
        graphics.drawString(this.font, stateText, 10, 10, 0xFFFFFF);

        // Display simulation time if SIMULATING
        if (getCurrentState() == IMachineLogic.State.SIMULATING) {
            long elapsed = getSimulationElapsedTicks();
            String timeText = "Time: " + (elapsed / 20) + "s";
            graphics.drawString(this.font, timeText, 10, 25, 0xFFFFFF);
        }
    }
}
```

### Pros

✅ **Visual feedback** - Player sees current state, simulation time, etc.
✅ **Professional UX** - Dedicated screen for controls
✅ **No extra items needed** - Right-click block directly
✅ **Can display detailed info** - Production rates, warnings, etc.
✅ **Extensible** - Easy to add more controls later

### Cons

❌ **Requires GUI programming** - ~150-200 lines of code
❌ **Client/server sync** - Need custom packets for button clicks
❌ **More complex** - Menu + Screen + Packet handling
❌ **Dev 2 dependency** - Client-side rendering work required

### Assignment

- **Dev 1**: Create `SimulationControlMenu` (MenuProvider, packet handling)
- **Dev 2**: Create `SimulationControlScreen` (rendering, button layout)
- **Integration**: Wire button packets to call `integrator.beginSimulation()` / `endSimulation()`

---

## Option 3: Hybrid Approach (RECOMMENDED)

### Concept

Use a **physical tool** for simplicity, but add a **"TPS Monitor" block** (optional) that displays state visually.

### Implementation

**Core Mechanic**: Simulation Wrench (Option 1)
**Optional Enhancement**: TPS Monitor block with simple overlay text

```java
// TPS Monitor Block - displays state when looked at
public class TpsMonitorBlock extends Block {
    // No BlockEntity needed - just a visual indicator
}

// Client-side overlay (Dev 2)
@SubscribeEvent
public static void onRenderGameOverlay(RenderGuiOverlayEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    HitResult hit = mc.hitResult;

    if (hit.getType() == HitResult.Type.BLOCK) {
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (state.getBlock() instanceof TpsMonitorBlock) {
            // Look for nearby machine_portal and display its state
            // ...
        }
    }
}
```

### Pros

✅ **Best of both worlds** - Simple tool + optional visual feedback
✅ **Progressive complexity** - Core works without GUI, enhancement is optional
✅ **Flexible UX** - Players choose their preference
✅ **Minimal GUI work** - Just overlay text, no menus/buttons

### Cons

❌ **Requires two blocks/items** - Slightly more content to implement

### Assignment

- **Dev 1**: Simulation Wrench item + TPS Monitor block registration
- **Dev 2**: Wrench texture + Monitor texture + overlay rendering
- **Integration**: Wire tool to `FactoryIntegrator`

---

## Final Recommendation

### **Use Option 1: Physical Tool (Simulation Wrench)**

**Reasoning:**

1. **Fastest to implement**: Get the mod functional ASAP
2. **No GUI dependencies**: Avoids cross-dev blocking (Dev 1 doesn't wait for Dev 2)
3. **Aligns with "no GUI" constraint**: CLAUDE.md states "No GUI for MachinePortalBlockEntity"
4. **Easy to upgrade later**: Can always add GUI in v2.0 if needed

### Implementation Checklist

- [ ] **Dev 1**: Create `SimulationWrenchItem` class
- [ ] **Dev 1**: Register item in `DeferredRegister<Item>`
- [ ] **Dev 1**: Wire `useOn()` to `FactoryIntegrator.beginSimulation()` / `endSimulation()`
- [ ] **Dev 1**: Add access method `getIntegrator()` to `MachinePortalBlockEntity`
- [ ] **Dev 2**: Create `simulation_wrench.png` texture (16x16)
- [ ] **Dev 2**: Add lang entry: `"item.fpscompress.simulation_wrench": "Simulation Wrench"`
- [ ] **Integration**: Test state transitions with wrench

### Example Recipe (Optional)

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

## Future Enhancements (v2.0+)

Once the core system is stable, consider adding:

1. **GUI overlay** - Show state/time when holding wrench
2. **Particle effects** - Visual feedback on simulation start/end
3. **Sound effects** - Audio cues for state transitions
4. **TPS Monitor block** - Optional visual state indicator
5. **Full GUI screen** - Detailed simulation statistics and controls

---

## Developer Notes

### For Dev 1 (Core Registry)

The `MachinePortalBlockEntity` needs to expose the `FactoryIntegrator`:

```java
public class MachinePortalBlockEntity extends BlockEntity {
    private FactoryIntegrator integrator;

    public FactoryIntegrator getIntegrator() {
        return this.integrator;
    }

    // Initialize integrator when TPS upgrade is installed
    public void installTpsUpgrade() {
        this.integrator = new FactoryIntegrator(
            this,              // IVirtualMachineData
            chunkManager,      // ICMInterceptor (from Dev 3)
            machineLogic,      // IMachineLogic (from Dev 4)
            scanner,           // IAntiCheatScanner (from Dev 5)
            cmDimension,
            roomCode,
            roomBounds
        );
    }
}
```

### For Dev 2 (Client Assets)

Simple wrench texture example:
- Base: Gray/silver metal
- Handle: Brown wood or black rubber grip
- Accent: Gold/yellow highlight to indicate "TPS" theme
- Style: Minecraft vanilla aesthetic (16x16 pixel art)

### For Integration Team

Test scenarios:
1. Right-click with wrench in BUILDING state → should start simulation
2. Right-click with wrench in SIMULATING state → should end simulation
3. Right-click with wrench in CACHED state → should show "Already cached" message
4. Right-click with wrench in HALTED state → should show "Factory halted" message
5. Right-click on non-portal block → should do nothing

---

## Conclusion

The **Simulation Wrench** approach balances simplicity, development speed, and user experience. It allows parallel development without GUI dependencies and can be enhanced later if needed.

**Start simple, iterate based on feedback.**
