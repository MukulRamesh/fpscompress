package com.mukulramesh.fpscompress.gui;

import com.mukulramesh.fpscompress.network.SimulationControlPacket;
import com.mukulramesh.fpscompress.portal.MachineState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side GUI for PreFab status and control.
 * Shows current state and provides button to transition states.
 */
public class PreFabStatusScreen extends AbstractContainerScreen<PreFabStatusMenu> {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    private Button controlButton;
    private MachineState lastKnownState = MachineState.BUILDING;

    // Synced data from server
    private MachineState syncedState = MachineState.BUILDING;
    private long syncedSimulationStartTick = 0;
    private long syncedSimulationEndTick = 0;
    private long syncedCachedStateStartTick = 0;
    private long syncedCurrentTick = 0;
    private java.util.Map<String, long[]> syncedLiveStats = java.util.Collections.emptyMap();
    private java.util.Map<String, Double> syncedCachedRates = java.util.Collections.emptyMap();
    private java.util.Map<String, Long> syncedCachedProduction = java.util.Collections.emptyMap();
    private String syncedLastSimulationResult = "";

    // Tooltip hover areas (set during rendering)
    private int stateY = 0;
    private int stateHeight = 0;
    private int simulationTimeY = 0;
    private int simulationTimeHeight = 0;
    private int cachedTicksY = 0;
    private int cachedTicksHeight = 0;
    private int itemStatsY = 0;
    private int itemStatsHeight = 0;

    public PreFabStatusScreen(PreFabStatusMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, Component.literal("PreFab Status & Control"));
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    /**
     * Update screen with fresh data from server.
     * Called by StatusGuiSyncPacket handler.
     */
    // CHECKSTYLE.OFF: ParameterNumber - Packet data unpacking requires 9 parameters
    public void updateFromServer(MachineState state, long simulationStartTick, long simulationEndTick,
                                 long cachedStateStartTick, long currentTick,
                                 java.util.Map<String, long[]> liveStats,
                                 java.util.Map<String, Double> cachedRates,
                                 java.util.Map<String, Long> cachedProduction,
                                 String lastSimulationResult) {
    // CHECKSTYLE.ON: ParameterNumber
        this.syncedState = state;
        this.syncedSimulationStartTick = simulationStartTick;
        this.syncedSimulationEndTick = simulationEndTick;
        this.syncedCachedStateStartTick = cachedStateStartTick;
        this.syncedCurrentTick = currentTick;
        this.syncedLiveStats = liveStats;
        this.syncedCachedRates = cachedRates;
        this.syncedCachedProduction = cachedProduction;
        this.syncedLastSimulationResult = lastSimulationResult;
    }

    /**
     * Get localized item name from resource ID.
     */
    private String getLocalizedName(String resourceId) {
        try {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.parse(resourceId));
            return item.getName(new net.minecraft.world.item.ItemStack(item)).getString();
        } catch (Exception e) {
            // Fallback to short ID if item lookup fails
            return resourceId.contains(":") ? resourceId.substring(resourceId.indexOf(':') + 1) : resourceId;
        }
    }

    @Override
    protected void init() {
        super.init();

        // Get current state from menu
        lastKnownState = menu.getCurrentState();
        syncedState = lastKnownState;

        // Add control button (centered horizontally, mid-screen vertically)
        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonX = leftPos + (imageWidth - buttonWidth) / 2;
        int buttonY = topPos + 80;

        controlButton = Button.builder(
            getButtonLabel(lastKnownState),
            button -> onControlButtonPressed()
        )
        .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
        .build();

        addRenderableWidget(controlButton);
    }

    /**
     * Get button label based on current state.
     */
    private Component getButtonLabel(MachineState state) {
        return switch (state) {
            case BUILDING -> Component.literal("Start Simulation");
            case SIMULATING -> Component.literal("Finish Simulation");
            case CACHED -> Component.literal("Reset to Building");
            case HALTED -> Component.literal("Resume Simulation");
        };
    }

    /**
     * Handle control button press.
     * Sends packet to server to trigger state transition.
     */
    private void onControlButtonPressed() {
        PacketDistributor.sendToServer(
            new SimulationControlPacket(menu.getPrefabPos())
        );

        // Close GUI after button press
        this.onClose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                           int mouseX, int mouseY) {
        // Draw background
        int bgColor = 0xC0101010; // Semi-transparent dark gray
        graphics.fill(leftPos, topPos, leftPos + imageWidth,
            topPos + imageHeight, bgColor);

        // Draw border
        int borderColor = 0xFF8B8B8B;
        // Top
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, borderColor);
        // Bottom
        graphics.fill(leftPos, topPos + imageHeight - 2,
            leftPos + imageWidth, topPos + imageHeight, borderColor);
        // Left
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight, borderColor);
        // Right
        graphics.fill(leftPos + imageWidth - 2, topPos,
            leftPos + imageWidth, topPos + imageHeight, borderColor);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Don't call super - we'll draw our own labels

        // Title
        Component titleText = Component.literal("PreFab Status & Control");
        int titleWidth = font.width(titleText);
        graphics.drawString(font, titleText, (imageWidth - titleWidth) / 2, 10, 0xFFFFFF, false);

        // Current State
        String stateColor = switch (syncedState) {
            case BUILDING -> "§e";
            case SIMULATING -> "§b";
            case CACHED -> "§a";
            case HALTED -> "§c";
        };
        Component stateText = Component.literal("State: " + stateColor + syncedState.name());
        stateY = 30; // Track for tooltip
        stateHeight = 10;
        graphics.drawString(font, stateText, 10, 30, 0xFFFFFF, false);

        // Room Code
        String roomCode = menu.getRoomCode();
        Component roomText = roomCode != null
            ? Component.literal("Room: §3" + roomCode)
            : Component.literal("Room: §cNot linked");
        graphics.drawString(font, roomText, 10, 45, 0xFFFFFF, false);

        // Last Simulation Result (only show if not empty and not currently simulating)
        if (!syncedLastSimulationResult.isEmpty() && syncedState != MachineState.SIMULATING) {
            String resultColor = syncedLastSimulationResult.contains("Success") ? "§a"
                : syncedLastSimulationResult.contains("Passthrough") ? "§e" : "§c";
            Component resultText = Component.literal(resultColor + syncedLastSimulationResult);
            graphics.drawString(font, resultText, 10, 60, 0xFFFFFF, false);
        }

        // State-specific stats
        if (syncedState == MachineState.SIMULATING) {
            renderSimulatingStats(graphics);
        } else if (syncedState == MachineState.CACHED) {
            renderCachedStats(graphics);
        }

        // Update button label if state changed
        if (syncedState != lastKnownState) {
            lastKnownState = syncedState;
            if (controlButton != null) {
                controlButton.setMessage(getButtonLabel(syncedState));
            }
        }
    }

    /**
     * Render simulation stats (SIMULATING state).
     */
    private void renderSimulatingStats(GuiGraphics graphics) {
        long elapsedTicks = syncedCurrentTick - syncedSimulationStartTick;

        // Elapsed time
        Component elapsedText = Component.literal("§7Elapsed: §b" + elapsedTicks + " ticks");
        graphics.drawString(font, elapsedText, 10, 60, 0xFFFFFF, false);

        // Resource stats
        int yOffset = 110;
        if (syncedLiveStats.isEmpty()) {
            Component noDataText = Component.literal("§7No resources tracked yet...");
            graphics.drawString(font, noDataText, 10, yOffset, 0xFFFFFF, false);
        } else {
            Component statsHeaderText = Component.literal("§7Live Stats:");
            graphics.drawString(font, statsHeaderText, 10, yOffset, 0xFFFFFF, false);
            yOffset += 12;

            int resourceCount = 0;
            for (java.util.Map.Entry<String, long[]> entry : syncedLiveStats.entrySet()) {
                String resourceId = entry.getKey();
                long imported = entry.getValue()[0];
                long exported = entry.getValue()[1];

                String localizedName = getLocalizedName(resourceId);

                // SIMULATING: Always show counts (both creative and survival)
                Component resourceText = Component.literal(
                    "§3" + localizedName + "§7: §c↓" + imported + " §a↑" + exported
                );
                graphics.drawString(font, resourceText, 10, yOffset, 0xFFFFFF, false);
                yOffset += 10;

                resourceCount++;
                if (resourceCount >= 3) {
                    Component moreText = Component.literal("§7...");
                    graphics.drawString(font, moreText, 10, yOffset, 0xFFFFFF, false);
                    break;
                }
            }
        }
    }

    /**
     * Render cached stats (CACHED state).
     */
    private void renderCachedStats(GuiGraphics graphics) {
        int yOffset = 110;

        if (syncedCachedRates.isEmpty()) {
            Component noRatesText = Component.literal("§cNo rates cached");
            graphics.drawString(font, noRatesText, 10, yOffset, 0xFFFFFF, false);
        } else {
            // Creative mode: Show simulation time, cached ticks, and production counts
            if (menu.isCreativeMode()) {
                // Show simulation time (static - used to determine rate)
                long simulationTime = syncedSimulationEndTick - syncedSimulationStartTick;
                Component simTimeText = Component.literal(
                    "§7Simulation Time: §b" + simulationTime + " ticks"
                );
                simulationTimeY = yOffset; // Track for tooltip
                simulationTimeHeight = 10;
                graphics.drawString(font, simTimeText, 10, yOffset, 0xFFFFFF, false);
                yOffset += 10;

                // Show cached ticks (dynamic - time spent in CACHED state)
                long cachedTicks = syncedCurrentTick - syncedCachedStateStartTick;
                Component cachedTicksText = Component.literal(
                    "§7Cached Ticks: §a" + cachedTicks + " ticks"
                );
                cachedTicksY = yOffset; // Track for tooltip hover detection
                cachedTicksHeight = 10;
                graphics.drawString(font, cachedTicksText, 10, yOffset, 0xFFFFFF, false);
                yOffset += 12;

                // Show resources with counts + rates + production
                itemStatsY = yOffset; // Start of item stats area for tooltip
                int resourceCount = 0;
                for (java.util.Map.Entry<String, Double> entry : syncedCachedRates.entrySet()) {
                    String resourceId = entry.getKey();
                    double rate = entry.getValue();
                    String localizedName = getLocalizedName(resourceId);

                    // Get counts from liveStats (simulation-time counts)
                    long[] counts = syncedLiveStats.get(resourceId);
                    long imported = counts != null ? counts[0] : 0;
                    long exported = counts != null ? counts[1] : 0;

                    // Get cached production (accumulated during CACHED)
                    long cachedProd = syncedCachedProduction.getOrDefault(resourceId, 0L);

                    // Format rate
                    String rateColor = rate > 0 ? "§a" : "§c";
                    String rateStr = String.format("%.3f", rate);

                    // Show: resource: SimIn/Out CachedProd (rate/t)
                    Component resourceText = Component.literal(
                        "§3" + localizedName + "§7: §e" + imported + "/" + exported
                        + " §d" + cachedProd + " §7(" + rateColor + rateStr + "/t§7)"
                    );
                    graphics.drawString(font, resourceText, 10, yOffset, 0xFFFFFF, false);
                    yOffset += 10;

                    resourceCount++;
                    if (resourceCount >= 2) {
                        Component moreText = Component.literal("§7...");
                        graphics.drawString(font, moreText, 10, yOffset, 0xFFFFFF, false);
                        break;
                    }
                }
                itemStatsHeight = yOffset - itemStatsY; // Track total height for tooltip
            } else {
                // Survival mode: Show only rates
                Component ratesHeaderText = Component.literal("§7Cached Rates:");
                graphics.drawString(font, ratesHeaderText, 10, yOffset, 0xFFFFFF, false);
                yOffset += 12;

                int resourceCount = 0;
                for (java.util.Map.Entry<String, Double> entry : syncedCachedRates.entrySet()) {
                    String resourceId = entry.getKey();
                    double rate = entry.getValue();
                    String localizedName = getLocalizedName(resourceId);

                    String rateColor = rate > 0 ? "§a" : "§c";
                    String rateStr = String.format("%.3f", rate);

                    Component resourceText = Component.literal(
                        "§3" + localizedName + "§7: " + rateColor + rateStr + "/t"
                    );
                    graphics.drawString(font, resourceText, 10, yOffset, 0xFFFFFF, false);
                    yOffset += 10;

                    resourceCount++;
                    if (resourceCount >= 3) {
                        Component moreText = Component.literal("§7...");
                        graphics.drawString(font, moreText, 10, yOffset, 0xFFFFFF, false);
                        break;
                    }
                    }
                }
            }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render background first
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render tooltip if hovering over button
        if (controlButton != null && controlButton.isHoveredOrFocused()) {
            MachineState state = menu.getCurrentState();
            Component tooltip = switch (state) {
                case BUILDING -> Component.literal("Load CM chunks and start measuring rates");
                case SIMULATING -> Component.literal("Calculate rates and enter cached mode");
                case CACHED -> Component.literal("Clear rates and return to building mode");
                case HALTED -> Component.literal("Resume simulation after fixing inputs/outputs");
            };
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }

        // Render tooltip for "State"
        if (stateHeight > 0) {
            int textX = leftPos + 10;
            int textY = topPos + stateY;
            int textWidth = imageWidth - 20;

            if (mouseX >= textX && mouseX <= textX + textWidth
                    && mouseY >= textY && mouseY <= textY + stateHeight) {
                java.util.List<Component> tooltipComponents = switch (syncedState) {
                    case BUILDING -> java.util.List.of(
                        Component.literal("§eBUILDING"),
                        Component.literal("§fConfigure faces and place"),
                        Component.literal("§fImporters/Exporters inside room")
                    );
                    case SIMULATING -> java.util.List.of(
                        Component.literal("§bSIMULATING"),
                        Component.literal("§fMeasuring actual production rates"),
                        Component.literal("§fCM chunks are loaded and ticking")
                    );
                    case CACHED -> java.util.List.of(
                        Component.literal("§aCACHED"),
                        Component.literal("§fRunning virtually using cached rates"),
                        Component.literal("§fCM chunks are §cunloaded§f (TPS saved!)")
                    );
                    case HALTED -> java.util.List.of(
                        Component.literal("§cHALTED"),
                        Component.literal("§fSimulation failed or cache invalid"),
                        Component.literal("§fFix inputs/outputs and resume")
                    );
                };
                renderTooltipFromComponents(graphics, tooltipComponents, mouseX, mouseY);
            }
        }

        // Render tooltip for "Simulation Time" (creative + CACHED only)
        if (menu.isCreativeMode() && syncedState == MachineState.CACHED && simulationTimeHeight > 0) {
            int textX = leftPos + 10;
            int textY = topPos + simulationTimeY;
            int textWidth = imageWidth - 20;

            if (mouseX >= textX && mouseX <= textX + textWidth
                    && mouseY >= textY && mouseY <= textY + simulationTimeHeight) {
                java.util.List<Component> tooltipComponents = java.util.List.of(
                    Component.literal("§7Simulation Time"),
                    Component.literal("§fTime used to measure rates"),
                    Component.literal("§7"),
                    Component.literal("§fStatic value: §bStart §7to §bEnd §fof"),
                    Component.literal("§fSIMULATING state")
                );
                renderTooltipFromComponents(graphics, tooltipComponents, mouseX, mouseY);
            }
        }

        // Render tooltip for "Cached Ticks" (creative + CACHED only)
        if (menu.isCreativeMode() && syncedState == MachineState.CACHED && cachedTicksHeight > 0) {
            int textX = leftPos + 10;
            int textY = topPos + cachedTicksY;
            int textWidth = imageWidth - 20;

            if (mouseX >= textX && mouseX <= textX + textWidth
                    && mouseY >= textY && mouseY <= textY + cachedTicksHeight) {
                java.util.List<Component> tooltipComponents = java.util.List.of(
                    Component.literal("§7Cached Ticks"),
                    Component.literal("§fTime spent in CACHED state"),
                    Component.literal("§7"),
                    Component.literal("§eNote: §fIncludes time skipped from"),
                    Component.literal("§fsleeping or /time commands")
                );
                renderTooltipFromComponents(graphics, tooltipComponents, mouseX, mouseY);
            }
        }

        // Render tooltip for item stats (creative + CACHED only)
        if (menu.isCreativeMode() && syncedState == MachineState.CACHED && itemStatsHeight > 0) {
            int textX = leftPos + 10;
            int textY = topPos + itemStatsY;
            int textWidth = imageWidth - 20;

            if (mouseX >= textX && mouseX <= textX + textWidth
                    && mouseY >= textY && mouseY <= textY + itemStatsHeight) {
                java.util.List<Component> tooltipComponents = java.util.List.of(
                    Component.literal("§7Item Stats Format"),
                    Component.literal("§fItem: §eIn/Out §dCached §7(§aRate§7)"),
                    Component.literal("§7"),
                    Component.literal("§eYellow (In/Out)§7: Items imported/"),
                    Component.literal("  exported during SIMULATING"),
                    Component.literal("§dPurple (Cached)§7: Total produced"),
                    Component.literal("  while in CACHED state"),
                    Component.literal("§aGreen/§cRed (Rate)§7: Items per tick"),
                    Component.literal("  (§aPositive§7=output, §cNegative§7=input)")
                );
                renderTooltipFromComponents(graphics, tooltipComponents, mouseX, mouseY);
            }
        }
    }

    /**
     * Helper to render tooltip from Component list.
     */
    private void renderTooltipFromComponents(GuiGraphics graphics,
                                            java.util.List<Component> components,
                                            int mouseX, int mouseY) {
        java.util.List<net.minecraft.util.FormattedCharSequence> tooltipLines =
            components.stream()
                .map(Component::getVisualOrderText)
                .toList();
        graphics.renderTooltip(font, tooltipLines, mouseX, mouseY);
    }
}
