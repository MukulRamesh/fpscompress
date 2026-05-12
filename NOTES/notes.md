Pivoting to a Compact Machines (CM) addon is a massive brain-gain for your timeline. You just deleted an entire developer’s worth of workload.

Because CM already handles the dimension registration, the Overworld blocks, the rendering, and the spatial tracking, Developer 2 (Assets) and Developer 3 (Dimension Manager) from your old plan are gone.

We can now condense your project into **four highly focused developers**. Here is the revised plan, structured specifically for intercepting and upgrading Compact Machines in NeoForge 1.21.

-----

# Developer 1 Assignment: CM Capability Attachment & Upgrade Item (NeoForge 1.21)

## Project Context

You are building the integration layer for a Compact Machines addon. We are not building our own blocks; we are hijacking existing Compact Machines. Your job is to create an upgrade item and attach our custom "Virtual Buffers" to the existing CM BlockEntities so we can store resources when the machine is paused.

## Objectives

1.  **Item Registration:** Use `DeferredRegister` to register a single item: `tps_cache_upgrade`. Create a basic 16x16 texture and item model JSON for it (Mod ID: `fpscompress`).
2.  **Capability Attachment (1.21 Standard):** Use `RegisterCapabilitiesEvent`. When a Compact Machine `BlockEntity` is upgraded with our item, attach a custom capability to it that holds our "Virtual Buffers".
3.  **Buffer Hard Caps:** These virtual buffers must have strict maximums (e.g., exactly 27 item slots, 50,000 mB fluid, 1,000,000 FE). If full, they must reject further inputs.
4.  **Data Components (1.21):** Create a custom Data Component to store the `isCached` boolean state on the CM block's item drop so it retains its upgraded status if broken and moved. Ensure it has a `Codec` and `StreamCodec`.

## API Contract

```java
public interface IVirtualMachineData {
    boolean hasTpsUpgrade();
    void setTpsUpgrade(boolean hasUpgrade);

    /** Returns the internal virtual buffers for the Integrator to manipulate. */
    Object getVirtualBuffer(ResourceType type);

    enum ResourceType { ITEM, FLUID, ENERGY }
}
```

-----

# Developer 2 Assignment: The Interceptor & Chunk Manager (NeoForge 1.21)

## Project Context

You are the traffic controller for this addon. When a Compact Machine is upgraded to be "TPS-Cached," it stops behaving normally. You are responsible for forcefully unloading the room's interior chunks and rerouting resources away from the physical room and into Dev 1's Virtual Buffers.

## Objectives

1.  **Chunk Hijacking:** Write a utility that uses NeoForge's `TicketHelper` (or the CM Room API) to forcefully **unload** the interior chunks of a specific Compact Machine room when the state switches to `CACHED`. Force-load those chunks again when the state switches to `BUILDING` or `SIMULATING`.
2.  **Resource Routing:** Intercept capabilities (`IItemHandler`, `IFluidHandler`, `IEnergyStorage`) interacting with the *outside* of the Compact Machine block in the Overworld.
      * If the machine is `CACHED`, route all inserted resources into the `VirtualBuffer` capability.
      * If it is `BUILDING` or `SIMULATING`, let Compact Machines handle the routing normally.

## API Contract

```java
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public interface ICMInterceptor {
    void setRoomChunkState(ServerLevel cmDimension, String roomCode, boolean forceLoad);

    /** * @param isCached true = intercept and route to virtual buffers; false = default CM behavior.
     */
    void setRoutingState(boolean isCached);
}
```

-----

# Developer 3 Assignment: State Machine & Fractional Logic (Pure Java)

## Project Context

You are building the mathematical brain of the factory. You are writing pure, unit-testable Java. Do not touch Minecraft Blocks, BlockEntities, or NeoForge Capabilities. You only care about states, tick counts, and fractional math.

## Objectives

1.  **The State Machine:** Manage four states: `BUILDING`, `SIMULATING`, `CACHED`, and `HALTED`.
      * Transitions must be explicit: Calling `startSimulation()` moves the machine from `BUILDING` to `SIMULATING`. Calling `finishSimulation(...)` moves it from `SIMULATING` to `CACHED` (or `HALTED` if parameters fail).
2.  **The Math:** When moving to `CACHED`, calculate the production and consumption rates using the data passed into `finishSimulation`:
      * $Rate\_per\_tick = \frac{Total\_Output}{Sim\_Time}$
      * $Input\_per\_tick = \frac{Total\_Consumed}{Sim\_Time}$
3.  **Fractional Accumulator:** During the `CACHED` state's tick loop, deduct the required $Input\_per\_tick$ from the inputs, and add the $Rate\_per\_tick$ to a hidden floating-point tracker for outputs. When the tracker reaches $\ge 1.0$, subtract $1.0$ and push $1$ integer unit to the output queue.
4.  **Halt Conditions:** If the machine starves for inputs, or if the output queue is full, transition to `HALTED`.

## API Contract

```java
import java.util.Map;

public interface IMachineLogic {
    enum State { BUILDING, SIMULATING, CACHED, HALTED }

    State getCurrentState();

    /** Transitions state from BUILDING to SIMULATING */
    void startSimulation();

    /** * Transitions state from SIMULATING to CACHED.
     * Uses provided maps to calculate fractional consumption and production rates.
     */
    void finishSimulation(long simTimeTicks, Map<String, Integer> consumedInputs, Map<String, Integer> totalOutputs);

    void tick();

    /** Returns the amount actually accepted into the logic buffers */
    int pushInput(String resourceId, int amount);

    /** Extracts calculated fractional outputs */
    int pullOutput(String resourceId, int maxAmount);
}
```

-----

# Developer 4 Assignment: CM Room Capability Scanner (NeoForge 1.21)

## Project Context

You are building an anti-cheat validation utility. You will scan the inside of a Compact Machine room to ensure players aren't using finite batteries to spoof an infinite factory.

## Objectives

1.  **The Snapshot:** Write a system that queries a specific interior room. You will be provided the `ServerLevel` and the `BoundingBox` of the CM room.
      * **Performance Critical:** Do *not* blindly iterate and query every block coordinate in the bounding box. First, gather all `BlockEntity` objects within the bounding box bounds (e.g., via `Level#getBlockEntities`). Only query capabilities (`IItemHandler`, `IFluidHandler`, `IEnergyStorage`) from the valid `BlockEntity` objects found. Sum up the exact total of all stored resources into a data object.
2.  **The Comparator:** Compare a "Pre-Simulation" snapshot with a "Post-Simulation" snapshot.
3.  **Validation Logic:** If the total stored resources in the room *decreased* between snapshots, the machine is draining an internal battery/chest, and validation fails. If totals are identical or increased, validation passes.
4.  **Configuration:** Implement a NeoForge server config (`ModConfig.Type.SERVER`) to whitelist or blacklist specific block IDs from being scanned.

## API Contract

```java
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public interface IAntiCheatScanner {
    Snapshot takeSnapshot(ServerLevel cmDimension, BoundingBox roomBounds);

    /** @return true if post-sim totals >= pre-sim totals. false if resources decreased. */
    boolean validateLoop(Snapshot preSim, Snapshot postSim);

    class Snapshot {
        public final long totalItems;
        public final long totalFluidMb;
        public final long totalEnergyFe;

        public Snapshot(long items, long fluid, long energy) {
            this.totalItems = items;
            this.totalFluidMb = fluid;
            this.totalEnergyFe = energy;
        }
    }
}
```

-----

*One quick note on the current 1.21.1 CM environment:* Compact Machines is currently reworking how their interior tunnels function, but because Dev 2 is intercepting resources directly at the Overworld block shell, your addon is insulated from whatever interior tunnel logic CM ends up using.