package com.mukulramesh.fpscompress.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mukulramesh.fpscompress.portal.VirtualBufferStorage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Debug command to test VirtualBufferStorage unlimited storage.
 *
 * Usage: /testbuffer
 *
 * This command tests that unlimited storage works correctly (no capacity limits).
 */
public class BufferTestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("testbuffer")
                .requires(source -> source.hasPermission(2)) // Requires OP
                .executes(BufferTestCommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        VirtualBufferStorage storage = new VirtualBufferStorage();

        source.sendSuccess(() -> Component.literal("§e=== Testing Virtual Buffer Unlimited Storage ==="), false);

        // Test 1: Unlimited item storage
        testUnlimitedItems(source, storage);

        // Test 2: Unlimited fluid storage
        storage.clear();
        testUnlimitedFluids(source, storage);

        // Test 3: Unlimited energy storage
        storage.clear();
        testUnlimitedEnergy(source, storage);

        source.sendSuccess(() -> Component.literal("§a=== All unlimited storage tests complete! ==="), false);
        return 1;
    }

    private static void testUnlimitedItems(CommandSourceStack source, VirtualBufferStorage storage) {
        source.sendSuccess(() -> Component.literal("§b[TEST 1] Unlimited Item Storage"), false);

        // Add way beyond old limit (old limit was 1,728)
        int added1 = storage.addItem("minecraft:stone", 10_000);
        if (added1 == 10_000) {
            source.sendSuccess(() -> Component.literal("  §a✓ Added 10,000 items (old limit: 1,728)"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Only added " + added1 + " items"));
        }

        // Add even more - should NOT reject
        int added2 = storage.addItem("minecraft:stone", 100_000);
        if (added2 == 100_000) {
            source.sendSuccess(() -> Component.literal("  §a✓ Added 100,000 more items (total: 110,000)"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Expected 100,000, got " + added2));
        }

        // Verify total
        int total = storage.getTotalItemCount();
        if (total == 110_000) {
            source.sendSuccess(() -> Component.literal("  §a✓ Total items: 110,000 (unlimited!)"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Expected 110,000 total, got " + total));
        }

        // Test multiple item types
        storage.addItem("minecraft:iron_ingot", 50_000);
        storage.addItem("minecraft:gold_ingot", 30_000);
        storage.addItem("minecraft:diamond", 20_000);
        int multiTypeTotal = storage.getTotalItemCount();
        if (multiTypeTotal == 210_000) {
            source.sendSuccess(() ->
                Component.literal("  §a✓ Multiple item types: 210,000 total items"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Expected 210,000, got " + multiTypeTotal));
        }
    }

    private static void testUnlimitedFluids(CommandSourceStack source, VirtualBufferStorage storage) {
        source.sendSuccess(() -> Component.literal("§b[TEST 2] Unlimited Fluid Storage"), false);

        // Add way beyond old limit (old limit was 50,000 mB)
        int added1 = storage.addFluid("minecraft:water", 1_000_000);
        if (added1 == 1_000_000) {
            source.sendSuccess(() -> Component.literal("  §a✓ Added 1,000,000 mB water (old limit: 50,000)"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Only added " + added1 + " mB"));
        }

        // Add even more - should NOT reject
        int added2 = storage.addFluid("minecraft:water", 5_000_000);
        if (added2 == 5_000_000) {
            source.sendSuccess(() -> Component.literal("  §a✓ Added 5,000,000 more mB (total: 6,000,000)"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Expected 5,000,000, got " + added2));
        }

        // Verify total
        int total = storage.getTotalFluidAmount();
        if (total == 6_000_000) {
            source.sendSuccess(() -> Component.literal("  §a✓ Total fluid: 6,000,000 mB (unlimited!)"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Expected 6,000,000 total, got " + total));
        }

        // Test multiple fluid types
        storage.addFluid("minecraft:lava", 2_000_000);
        int multiTypeTotal = storage.getTotalFluidAmount();
        if (multiTypeTotal == 8_000_000) {
            source.sendSuccess(() ->
                Component.literal("  §a✓ Multiple fluid types: 8,000,000 mB total"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Expected 8,000,000, got " + multiTypeTotal));
        }
    }

    private static void testUnlimitedEnergy(CommandSourceStack source, VirtualBufferStorage storage) {
        source.sendSuccess(() -> Component.literal("§b[TEST 3] Unlimited Energy Storage"), false);

        // Add way beyond old limit (old limit was 1,000,000 FE)
        long added1 = storage.addEnergy(10_000_000L);
        if (added1 == 10_000_000L) {
            source.sendSuccess(() -> Component.literal("  §a✓ Added 10,000,000 FE (old limit: 1,000,000)"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Only added " + added1 + " FE"));
        }

        // Add even more - should NOT reject
        long added2 = storage.addEnergy(100_000_000L);
        if (added2 == 100_000_000L) {
            source.sendSuccess(() -> Component.literal("  §a✓ Added 100,000,000 more FE (total: 110,000,000)"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Expected 100,000,000, got " + added2));
        }

        // Verify total
        long total = storage.getEnergyAmount();
        if (total == 110_000_000L) {
            source.sendSuccess(() -> Component.literal("  §a✓ Total energy: 110,000,000 FE (unlimited!)"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Expected 110,000,000 total, got " + total));
        }

        // Test extreme value
        storage.clear();
        long extreme = storage.addEnergy(1_000_000_000L); // 1 billion FE
        if (extreme == 1_000_000_000L) {
            source.sendSuccess(() ->
                Component.literal("  §a✓ Stored 1,000,000,000 FE (1 billion!)"), false);
        } else {
            source.sendFailure(Component.literal("  §c✗ FAILED: Expected 1,000,000,000, got " + extreme));
        }
    }
}
