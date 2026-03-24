package com.mukulramesh.fpscompress.integration;

import com.mukulramesh.fpscompress.portal.IVirtualMachineData;
import com.mukulramesh.fpscompress.spatial.ICMInterceptor;
import com.mukulramesh.fpscompress.logic.IMachineLogic;
import com.mukulramesh.fpscompress.scanner.IAntiCheatScanner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Map;
import java.util.HashMap;

/**
 * The central controller for a single TPS-Cached Compact Machine.
 * This class wires together the isolated modules from Devs 1, 3, 4, and 5.
 *
 * ARCHITECTURE NOTE:
 * This is the "central nervous system" of the mod. It holds NO logic of its own.
 * Its entire job is to translate and pass data between the APIs of the isolated modules:
 *
 * - Dev 1 (IVirtualMachineData): Physical Overworld block with virtual buffers
 * - Dev 3 (ICMInterceptor): Chunk loading and routing control in CM dimension
 * - Dev 4 (IMachineLogic): Pure Java state machine with fractional production math
 * - Dev 5 (IAntiCheatScanner): Anti-cheat validation via capability scanning
 *
 * Dev 2 (Client Assets) does not appear here as they handle client-side rendering and data generation.
 *
 * BLAME ASSIGNMENT:
 * - Chunk loading crashes? → Dev 3 (ICMInterceptor)
 * - Anti-cheat false positives? → Dev 5 (IAntiCheatScanner)
 * - Math outputting wrong rates? → Dev 4 (IMachineLogic)
 * - Virtual buffer routing issues? → Dev 1 (IVirtualMachineData)
 *
 * @author FPSCompress Integration Team
 */
public class FactoryIntegrator {

    // Injected Dependencies from our isolated developers
    private final IVirtualMachineData virtualData;   // Dev 1: Virtual buffers in Overworld block
    private final ICMInterceptor chunkManager;       // Dev 3: CM dimension chunk loading
    private final IMachineLogic logic;               // Dev 4: Pure Java state machine
    private final IAntiCheatScanner scanner;         // Dev 5: Anti-cheat validation

    // Machine Context
    private final ServerLevel cmDimension;
    private final String roomCode;
    private final BoundingBox roomBounds;

    // Simulation Tracking
    private IAntiCheatScanner.Snapshot preSimSnapshot;
    private long simStartTime;
    private final Map<String, Integer> ioTracker = new HashMap<>(); // Tracks IO during simulation

    /**
     * Constructor for FactoryIntegrator.
     *
     * @param dev1 Virtual machine data handler (Dev 1)
     * @param dev3 Chunk manager and interceptor (Dev 3)
     * @param dev4 State machine logic (Dev 4)
     * @param dev5 Anti-cheat scanner (Dev 5)
     * @param level The Compact Machines dimension ServerLevel
     * @param room The room code for this factory
     * @param bounds The bounding box of the factory room
     */
    public FactoryIntegrator(
            IVirtualMachineData dev1,
            ICMInterceptor dev3,
            IMachineLogic dev4,
            IAntiCheatScanner dev5,
            ServerLevel level,
            String room,
            BoundingBox bounds) {
        this.virtualData = dev1;
        this.chunkManager = dev3;
        this.logic = dev4;
        this.scanner = dev5;

        this.cmDimension = level;
        this.roomCode = room;
        this.roomBounds = bounds;
    }

    /**
     * Called every server tick for this specific machine.
     *
     * This method coordinates the state transitions and data flow between all modules.
     * The behavior changes based on the current state from Dev 4's state machine.
     */
    public void tick() {
        if (!virtualData.hasTpsUpgrade()) return;

        IMachineLogic.State currentState = logic.getCurrentState();

        switch (currentState) {
            case BUILDING:
                // Normal Compact Machine behavior. Chunks are loaded, physical routing is active.
                // The player can interact with the factory interior normally.
                chunkManager.setRoomChunkState(cmDimension, roomCode, true);
                chunkManager.setRoutingState(false);
                break;

            case SIMULATING:
                // The machine is actively running to calculate its rates.
                // Dev 3's interceptor observes IO on the physical Overworld block during this time.
                // ioTracker would be populated by the interceptor's observations.
                break;

            case CACHED:
                // Virtual mode: Chunks unloaded, math-only simulation running.

                // 1. Let Dev 4's pure math logic tick
                logic.tick();

                // 2. Feed inputs from Dev 1's Virtual Buffers into Dev 4's Math Logic
                // Example: Loop through virtualData.getVirtualBuffer(ITEM)
                // This is pseudo-code - actual implementation depends on Dev 1's API
                int itemsAccepted = logic.pushInput("minecraft:iron_ingot", 1);
                // Then remove 'itemsAccepted' from Dev 1's buffer
                // virtualData.extractFromBuffer(ResourceType.ITEM, "minecraft:iron_ingot", itemsAccepted);

                // 3. Extract outputs from Dev 4's Math Logic into Dev 1's Virtual Buffers
                int itemsProduced = logic.pullOutput("minecraft:iron_block", 64);
                // Then add 'itemsProduced' to Dev 1's buffer
                // virtualData.addToBuffer(ResourceType.ITEM, "minecraft:iron_block", itemsProduced);

                // 4. If the math logic starves or fills up, it handles its own transition to HALTED.
                //    Dev 4 is responsible for detecting input starvation or output blockage.
                break;

            case HALTED:
                // The machine broke the cache loop (ran out of inputs or output is full).
                // Wake the physical chunks back up so the player can fix it.
                chunkManager.setRoomChunkState(cmDimension, roomCode, true);
                chunkManager.setRoutingState(false);
                break;
        }
    }

    /**
     * Triggered by the player (e.g., clicking a "Start Simulation" button on the physical block).
     *
     * This begins the calibration phase where the factory runs physically to calculate its rates.
     */
    public void beginSimulation() {
        if (logic.getCurrentState() != IMachineLogic.State.BUILDING) return;

        // 1. Use Dev 5 to snapshot the room before the simulation starts
        this.preSimSnapshot = scanner.takeSnapshot(cmDimension, roomBounds);
        this.simStartTime = cmDimension.getGameTime();
        this.ioTracker.clear();

        // 2. Tell Dev 4 to transition states
        logic.startSimulation();
    }

    /**
     * Triggered by the player (e.g., clicking "Finish Simulation" after letting it run for a while).
     *
     * This completes the calibration phase and transitions to cached mode if validation passes.
     */
    public void endSimulation() {
        if (logic.getCurrentState() != IMachineLogic.State.SIMULATING) return;

        // 1. Use Dev 5 to snapshot the room after the simulation
        IAntiCheatScanner.Snapshot postSimSnapshot = scanner.takeSnapshot(cmDimension, roomBounds);

        // 2. Ask Dev 5 if the loop is valid (no hidden batteries drained)
        boolean isValid = scanner.validateLoop(preSimSnapshot, postSimSnapshot);

        if (!isValid) {
            // Anti-cheat failed. Force the machine back to BUILDING.
            // Dev 4 should provide a reset() method in the IMachineLogic contract for this.
            logic.resetState();
            return;
        }

        // 3. Calculate time passed during simulation
        long simTimeTicks = cmDimension.getGameTime() - this.simStartTime;

        // 4. Pass the simulation data into Dev 4's math brain
        // Assuming ioTracker mapped "consumed" and "produced" resources from the Overworld block
        Map<String, Integer> consumed = getConsumedFromTracker(ioTracker);
        Map<String, Integer> produced = getProducedFromTracker(ioTracker);

        logic.finishSimulation(simTimeTicks, consumed, produced);

        // 5. If successful, Dev 4 is now in CACHED state.
        // Tell Dev 3 to unload the chunks and hijack the routing!
        chunkManager.setRoomChunkState(cmDimension, roomCode, false);
        chunkManager.setRoutingState(true);
    }

    /**
     * Helper method to extract consumed resources from ioTracker.
     *
     * @param tracker The IO tracker map populated during simulation
     * @return Map of consumed resource IDs to quantities
     */
    private Map<String, Integer> getConsumedFromTracker(Map<String, Integer> tracker) {
        // TODO: Implement actual logic to differentiate consumed vs produced
        // This might involve prefixing keys with "consumed_" or "produced_"
        return new HashMap<>();
    }

    /**
     * Helper method to extract produced resources from ioTracker.
     *
     * @param tracker The IO tracker map populated during simulation
     * @return Map of produced resource IDs to quantities
     */
    private Map<String, Integer> getProducedFromTracker(Map<String, Integer> tracker) {
        // TODO: Implement actual logic to differentiate consumed vs produced
        return new HashMap<>();
    }
}
