The QA Pipeline (Sanity Checks):
As each developer completes their phase, you must pull their branch and run the following tests in a dedicated testing world:

    Post-Dev 1 (Capabilities): Launch the game. Give yourself the tps_cache_upgrade item. Verify the game doesn't crash when attaching the custom capability to a Compact Machine block. Use a debugger or console print to confirm the virtual buffers reject items beyond their hard caps.

    Post-Dev 2 (Interceptor): Place a Compact Machine. Trigger Dev 2's "force unload" method and verify the server thread doesn't hang or crash. Attempt to pipe items into the physical block using a vanilla Hopper; verify that Dev 2's routing successfully redirects the items based on the boolean state.

    Post-Dev 3 (Math Logic): Dev 3 is writing pure Java. You do not need to launch Minecraft. Run standard JUnit tests to verify their state machine transitions correctly (BUILDING -> SIMULATING -> CACHED) and that the fractional accumulator properly outputs exactly 1 integer unit when the float threshold is reached.

    Post-Dev 4 (Scanner): Build a Compact Machine room. Place a chest inside with exactly 64 Cobblestone. Run Dev 4's scanner method and verify it outputs exactly 64. Add a fluid tank and battery, and verify it reads all three capabilities perfectly without causing a TPS lag spike.