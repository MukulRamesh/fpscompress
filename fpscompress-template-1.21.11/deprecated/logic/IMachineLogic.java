package com.mukulramesh.fpscompress.logic;

import java.util.Map;

/**
 * Interface for Dev 4: State Machine & Fractional Logic
 *
 * This interface represents the pure Java state machine and fractional production math.
 * It has NO Minecraft dependencies and should be unit-testable.
 *
 * RESPONSIBILITIES:
 * - Manage state transitions: BUILDING → SIMULATING → CACHED / HALTED
 * - Calculate fractional production rates from simulation data
 * - Accumulate fractional outputs over time (sub-tick precision)
 * - Detect input starvation and output blockage
 *
 * STATE FLOW:
 * 1. BUILDING: Player is setting up the factory (chunks loaded, physical mode)
 * 2. SIMULATING: Factory is running to calculate rates (observation phase)
 * 3. CACHED: Factory is running in math-only mode (chunks unloaded, virtual mode)
 * 4. HALTED: Cache broke (ran out of inputs or outputs full, chunks reloaded)
 *
 * FRACTIONAL MATH:
 * - Rate_per_tick = Total_Output / Sim_Time
 * - Accumulator tracks partial items (e.g., 1.5 items/tick → 1 item now, 0.5 saved)
 *
 * @author Dev 4 - State Machine Team
 */
public interface IMachineLogic {

    /**
     * Get the current state of the machine.
     *
     * @return The current state
     */
    State getCurrentState();

    /**
     * Begin the simulation phase.
     *
     * Transitions from BUILDING → SIMULATING.
     * This is called when the player clicks "Start Simulation".
     */
    void startSimulation();

    /**
     * Finish the simulation phase and calculate production rates.
     *
     * Transitions from SIMULATING → CACHED (if valid) or BUILDING (if invalid).
     * This is called when the player clicks "Finish Simulation".
     *
     * @param simTimeTicks The number of ticks the simulation ran
     * @param consumed Map of resource IDs to quantities consumed during simulation
     * @param produced Map of resource IDs to quantities produced during simulation
     */
    void finishSimulation(long simTimeTicks, Map<String, Integer> consumed, Map<String, Integer> produced);

    /**
     * Tick the math-only logic during CACHED mode.
     *
     * This updates fractional accumulators and produces/consumes resources
     * at the calculated rates. Should only be called when in CACHED state.
     */
    void tick();

    /**
     * Push input resources into the logic.
     *
     * This is called during CACHED mode when the integrator feeds resources
     * from Dev 1's virtual buffers.
     *
     * @param resourceId The resource identifier (e.g., "minecraft:iron_ingot")
     * @param amount The amount being pushed
     * @return The amount actually accepted (may be less if logic is full)
     */
    int pushInput(String resourceId, int amount);

    /**
     * Pull output resources from the logic.
     *
     * This is called during CACHED mode when the integrator extracts resources
     * to Dev 1's virtual buffers.
     *
     * @param resourceId The resource identifier (e.g., "minecraft:iron_block")
     * @param maxAmount The maximum amount to pull
     * @return The amount actually pulled (may be less if logic lacks outputs)
     */
    int pullOutput(String resourceId, int maxAmount);

    /**
     * Reset the state machine to BUILDING.
     *
     * This is called when anti-cheat validation fails or the player manually resets.
     */
    void resetState();

    /**
     * Check if the logic has detected input starvation.
     *
     * When true, the state should transition to HALTED.
     *
     * @return true if inputs are starved, false otherwise
     */
    boolean isInputStarved();

    /**
     * Check if the logic has detected output blockage.
     *
     * When true, the state should transition to HALTED.
     *
     * @return true if outputs are blocked, false otherwise
     */
    boolean isOutputBlocked();

    /**
     * State enumeration for the machine.
     */
    enum State {
        /**
         * BUILDING: Player is setting up the factory.
         * - Chunks are loaded in CM dimension
         * - Physical routing is active
         * - Player can enter and modify the factory
         */
        BUILDING,

        /**
         * SIMULATING: Factory is running to calculate rates.
         * - Chunks are loaded in CM dimension
         * - Physical routing is active
         * - IO is being tracked for rate calculation
         */
        SIMULATING,

        /**
         * CACHED: Factory is running in math-only mode.
         * - Chunks are unloaded in CM dimension
         * - Virtual routing is active
         * - Fractional math is producing outputs
         */
        CACHED,

        /**
         * HALTED: Cache broke due to input starvation or output blockage.
         * - Chunks are loaded in CM dimension
         * - Physical routing is active
         * - Waiting for player intervention
         */
        HALTED
    }
}
