package com.mukulramesh.fpscompress.gui;

import com.mukulramesh.fpscompress.network.SimulationControlPacket;
import com.mukulramesh.fpscompress.portal.MachineState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side GUI for PreFab status and control.
 * Shows current state and provides button to transition states.
 */
public class PreFabStatusScreen extends AbstractContainerScreen<PreFabStatusMenu> {

    private static final int GUI_WIDTH = 220; // Wider for more content
    private static final int GUI_HEIGHT = 220; // Taller for inventory display

    private Button controlButton;
    private MachineState lastKnownState = MachineState.BUILDING;
    private boolean haltedConfirmationPending = false; // Two-click confirmation for HALTED resume

    // Tab system
    private static final int TAB_CONTROL = 0;
    private static final int TAB_RESOURCES = 1;
    private int selectedTab = TAB_CONTROL;
    private Button tabControlButton;
    private Button tabResourcesButton;

    // Scrolling support (for Tab 1 only)
    private int scrollOffset = 0; // Pixels scrolled
    private int maxScrollOffset = 0; // Maximum scroll (calculated during render)

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
    private long syncedSimulationElapsedTicks = 0; // Elapsed ticks in SIMULATING state
    private long syncedSimulationRequiredTicks = 0; // Required ticks from config snapshot

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
    // CHECKSTYLE.OFF: ParameterNumber - Packet data unpacking requires 11 parameters
    public void updateFromServer(MachineState state, long simulationStartTick, long simulationEndTick,
                                 long cachedStateStartTick, long currentTick,
                                 java.util.Map<String, long[]> liveStats,
                                 java.util.Map<String, Double> cachedRates,
                                 java.util.Map<String, Long> cachedProduction,
                                 String lastSimulationResult,
                                 long simulationElapsedTicks,
                                 long simulationRequiredTicks) {
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
        this.syncedSimulationElapsedTicks = simulationElapsedTicks;
        this.syncedSimulationRequiredTicks = simulationRequiredTicks;
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

    /**
     * Format ticks as "Xm XXs" (e.g., "2m 30s").
     *
     * @param ticks Number of ticks (20 ticks = 1 second)
     * @return Formatted time string
     */
    private String formatTime(long ticks) {
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%dm %02ds", minutes, remainingSeconds);
    }

    /**
     * Render tooltip for state button (extracted to reduce renderTooltips() method length).
     */
    private void renderStateButtonTooltip(GuiGraphics graphics, MachineState state, int mouseX, int mouseY) {
        if (state == MachineState.SIMULATING && syncedSimulationRequiredTicks > 0) {
            // SIMULATING with minimum time: Show progress tooltip
            Component tooltip;
            if (syncedSimulationElapsedTicks < syncedSimulationRequiredTicks) {
                long remaining = syncedSimulationRequiredTicks - syncedSimulationElapsedTicks;
                tooltip = Component.literal(
                    String.format("Survival: %s remaining | Creative: Click to finish now",
                        formatTime(remaining))
                );
            } else {
                tooltip = Component.literal("Click to finish simulation and cache rates");
            }
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        } else {
            Component tooltip = switch (state) {
                case BUILDING -> Component.literal("Load chunks and start measuring rates");
                case SIMULATING -> Component.literal("Calculate rates and enter cached mode");
                case CACHED -> Component.literal("Clear rates and return to building mode");
                default -> Component.literal(""); // Should never happen
            };
            if (!tooltip.getString().isEmpty()) {
                graphics.renderTooltip(font, tooltip, mouseX, mouseY);
            }
        }
    }

    /**
     * Draw text with word wrap support.
     * Automatically wraps text that exceeds maxWidth onto multiple lines.
     * Preserves Minecraft color codes (§x) across line breaks.
     *
     * @param graphics The graphics context
     * @param text The text to draw (supports Minecraft color codes)
     * @param x X position
     * @param y Y position
     * @param maxWidth Maximum width before wrapping
     * @param color Text color (only used if no color codes in text)
     * @return Number of lines drawn (for yOffset calculation)
     */
    private int drawWrappedText(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        // Split text by spaces to get words
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        String lastColorCode = ""; // Track last color code for line continuation
        int linesDrawn = 0;
        int currentY = y;

        for (String word : words) {
            // Check if word starts with or contains a color code
            if (word.contains("§") && word.length() >= 2) {
                int colorIndex = word.lastIndexOf("§");
                if (colorIndex < word.length() - 1) {
                    lastColorCode = word.substring(colorIndex, colorIndex + 2);
                }
            }

            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            Component testComponent = Component.literal(testLine);
            int testWidth = font.width(testComponent);

            if (testWidth > maxWidth && !currentLine.isEmpty()) {
                // Current line is full, draw it and start new line
                graphics.drawString(font, Component.literal(currentLine.toString()), x, currentY, color, false);
                currentY += 10; // Line height
                linesDrawn++;
                // Start new line with last color code to preserve formatting
                currentLine = new StringBuilder(lastColorCode + word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }

        // Draw remaining text
        if (!currentLine.isEmpty()) {
            graphics.drawString(font, Component.literal(currentLine.toString()), x, currentY, color, false);
            linesDrawn++;
        }

        return linesDrawn;
    }

    @Override
    protected void init() {
        super.init();

        // Get current state from menu
        lastKnownState = menu.getCurrentState();
        syncedState = lastKnownState;

        // Add tab buttons at the top
        int tabWidth = 80;
        int tabHeight = 20;
        int tabY = topPos + 5;

        tabControlButton = Button.builder(
            Component.literal("Control"),
            button -> switchToTab(TAB_CONTROL)
        )
        .bounds(leftPos + 10, tabY, tabWidth, tabHeight)
        .build();

        tabResourcesButton = Button.builder(
            Component.literal("Resources"),
            button -> switchToTab(TAB_RESOURCES)
        )
        .bounds(leftPos + 95, tabY, tabWidth, tabHeight)
        .build();

        addRenderableWidget(tabControlButton);
        addRenderableWidget(tabResourcesButton);

        // Add control button (only visible in Control tab)
        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonX = leftPos + (imageWidth - buttonWidth) / 2;
        int buttonY = topPos + imageHeight - 35; // Bottom of GUI

        controlButton = Button.builder(
            getButtonLabel(lastKnownState),
            button -> onControlButtonPressed()
        )
        .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
        .build();

        addRenderableWidget(controlButton);

        // Update tab button states
        updateTabButtons();
    }

    /**
     * Switch to a specific tab.
     */
    private void switchToTab(int tab) {
        selectedTab = tab;
        scrollOffset = 0; // Reset scroll when switching tabs
        updateTabButtons();
    }

    /**
     * Update tab button appearance based on selected tab.
     */
    private void updateTabButtons() {
        if (tabControlButton != null) {
            tabControlButton.active = selectedTab != TAB_CONTROL;
        }
        if (tabResourcesButton != null) {
            tabResourcesButton.active = selectedTab != TAB_RESOURCES;
        }

        // Control button only visible in Control tab
        if (controlButton != null) {
            controlButton.visible = selectedTab == TAB_CONTROL;
        }
    }

    /**
     * Get button label based on current state.
     */
    private Component getButtonLabel(MachineState state) {
        // Special case: HALTED with confirmation pending
        if (state == MachineState.HALTED && haltedConfirmationPending) {
            return Component.literal("§cAre you sure?");
        }

        return switch (state) {
            case BUILDING -> Component.literal("Start Simulation");
            case SIMULATING -> {
                // Show progress indication in button label
                if (syncedSimulationRequiredTicks > 0 && syncedSimulationElapsedTicks < syncedSimulationRequiredTicks) {
                    int percentage = (int) ((syncedSimulationElapsedTicks * 100) / syncedSimulationRequiredTicks);
                    yield Component.literal("Simulating... " + percentage + "%");
                } else {
                    yield Component.literal("Finish Simulation");
                }
            }
            case CACHED -> Component.literal("Reset to Building");
            case HALTED -> Component.literal("Reset Cache");
        };
    }

    /**
     * Handle control button press.
     * Sends packet to server to trigger state transition.
     */
    private void onControlButtonPressed() {
        // Special handling for HALTED state: Two-click confirmation
        if (syncedState == MachineState.HALTED && !haltedConfirmationPending) {
            // First click: Show confirmation
            haltedConfirmationPending = true;
            controlButton.setMessage(Component.literal("§cAre you sure?"));
            return; // Don't send packet yet
        }

        // All other states OR second click on HALTED: Send packet
        PacketDistributor.sendToServer(
            new SimulationControlPacket(menu.getPrefabPos())
        );

        // Reset confirmation state
        haltedConfirmationPending = false;

        // Keep GUI open so user can see live updates
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

        // Render content based on selected tab
        if (selectedTab == TAB_CONTROL) {
            renderControlTab(graphics);
        } else if (selectedTab == TAB_RESOURCES) {
            renderResourcesTab(graphics, mouseX, mouseY);
        }

        // Update button label if state changed
        if (syncedState != lastKnownState) {
            lastKnownState = syncedState;
            haltedConfirmationPending = false; // Reset confirmation on state change
            if (controlButton != null) {
                controlButton.setMessage(getButtonLabel(syncedState));
            }
        }
    }

    /**
     * Render Control tab (state info, simulation stats, control button).
     */
    private void renderControlTab(GuiGraphics graphics) {
        int yOffset = 35; // Below tabs

        // Current State
        String stateColor = switch (syncedState) {
            case BUILDING -> "§e";
            case SIMULATING -> "§b";
            case CACHED -> "§a";
            case HALTED -> "§c";
        };
        Component stateText = Component.literal("State: " + stateColor + syncedState.name());
        stateY = yOffset;
        stateHeight = 10;
        graphics.drawString(font, stateText, 10, yOffset, 0xFFFFFF, false);
        yOffset += 15;

        // Room Code
        String roomCode = menu.getRoomCode();
        Component roomText = roomCode != null
            ? Component.literal("Room: §3" + roomCode)
            : Component.literal("Room: §cNot linked");
        graphics.drawString(font, roomText, 10, yOffset, 0xFFFFFF, false);
        yOffset += 20;

        // Last Simulation Result
        if (!syncedLastSimulationResult.isEmpty() && syncedState != MachineState.SIMULATING) {
            String resultColor = syncedLastSimulationResult.contains("Success") ? "§a"
                : syncedLastSimulationResult.contains("Passthrough") ? "§e" : "§c";
            int linesDrawn = drawWrappedText(graphics, resultColor + syncedLastSimulationResult,
                10, yOffset, imageWidth - 20, 0xFFFFFF);
            yOffset += linesDrawn * 10 + 10;
        }

        // State-specific basic stats (simplified for Control tab)
        if (syncedState == MachineState.SIMULATING) {
            // Show minimum time progress if configured
            if (syncedSimulationRequiredTicks > 0) {
                // Time display: "Simulating: 2m 30s / 5m 00s"
                String timeText = String.format("Simulating: %s / %s",
                    formatTime(syncedSimulationElapsedTicks), formatTime(syncedSimulationRequiredTicks));
                graphics.drawString(font, timeText, 10, yOffset, 0xFFFFFF, false);
                yOffset += 15;

                // Progress bar (visual)
                int progressBarWidth = 180;
                int progressBarHeight = 10;
                int progressBarX = 20;
                int progressBarY = yOffset;

                // Background (unfilled)
                graphics.fill(progressBarX, progressBarY,
                    progressBarX + progressBarWidth, progressBarY + progressBarHeight,
                    0xFF404040); // Dark gray

                // Filled portion (green)
                float progress = Math.min(1.0f, (float) syncedSimulationElapsedTicks / syncedSimulationRequiredTicks);
                int filledWidth = (int) (progressBarWidth * progress);
                graphics.fill(progressBarX, progressBarY,
                    progressBarX + filledWidth, progressBarY + progressBarHeight,
                    0xFF00FF00); // Bright green

                // Percentage text (centered on bar)
                int percentage = (int) (progress * 100);
                String percentText = percentage + "%";
                int textWidth = font.width(percentText);
                graphics.drawString(font, percentText,
                    progressBarX + (progressBarWidth - textWidth) / 2,
                    progressBarY + 1, // Slight offset for centering
                    0xFFFFFF, false);

                yOffset += 20;
            } else {
                // No minimum time - show regular elapsed ticks
                long elapsedTicks = syncedCurrentTick - syncedSimulationStartTick;
                Component elapsedText = Component.literal("§7Elapsed: §b" + elapsedTicks + " ticks");
                graphics.drawString(font, elapsedText, 10, yOffset, 0xFFFFFF, false);
                yOffset += 10;
            }

            if (menu.isCreativeMode()) {
                Component deltaText = Component.literal("§7[Delta Accounting Active]");
                graphics.drawString(font, deltaText, 10, yOffset, 0x808080, false);
                yOffset += 10;
            }

            int resourceCount = syncedLiveStats.size();
            Component countText = Component.literal("§7Tracking " + resourceCount + " resource types");
            graphics.drawString(font, countText, 10, yOffset, 0xFFFFFF, false);

        } else if (syncedState == MachineState.CACHED || syncedState == MachineState.HALTED) {
            if (menu.isCreativeMode()) {
                long simulationTime = syncedSimulationEndTick - syncedSimulationStartTick;
                Component simTimeText = Component.literal("§7Simulation: §b" + simulationTime + " ticks");
                graphics.drawString(font, simTimeText, 10, yOffset, 0xFFFFFF, false);
                yOffset += 10;

                long cachedTicks = syncedCurrentTick - syncedCachedStateStartTick;
                Component cachedTicksText = Component.literal("§7Cached: §a" + cachedTicks + " ticks");
                graphics.drawString(font, cachedTicksText, 10, yOffset, 0xFFFFFF, false);
                yOffset += 10;
            }

            int resourceCount = syncedCachedRates.size();
            Component countText = Component.literal("§7Cached " + resourceCount + " resource types");
            graphics.drawString(font, countText, 10, yOffset, 0xFFFFFF, false);
            yOffset += 15;

            Component hintText = Component.literal("§7→ Switch to Resources tab for details");
            graphics.drawString(font, hintText, 10, yOffset, 0x808080, false);
        }
    }

    /**
     * Render Resources tab (inventory-style item grid).
     */
    private void renderResourcesTab(GuiGraphics graphics, int mouseX, int mouseY) {
        int startY = 35; // Below tabs

        // Show which state we're displaying
        String stateColor = switch (syncedState) {
            case BUILDING -> "§e";
            case SIMULATING -> "§b";
            case CACHED -> "§a";
            case HALTED -> "§c";
        };
        Component headerText = Component.literal(stateColor + syncedState.name() + " §7Resources:");
        graphics.drawString(font, headerText, 10, startY, 0xFFFFFF, false);
        startY += 15;

        // Separate resources by consumed (negative rate) vs produced (positive rate)
        java.util.List<ResourceDisplayInfo> consumed = new java.util.ArrayList<>();
        java.util.List<ResourceDisplayInfo> produced = new java.util.ArrayList<>();

        if (syncedState == MachineState.SIMULATING) {
            // SIMULATING: Show based on import/export
            for (java.util.Map.Entry<String, long[]> entry : syncedLiveStats.entrySet()) {
                String resourceId = entry.getKey();
                long imported = entry.getValue()[0];
                long exported = entry.getValue()[1];

                if (imported > 0) {
                    consumed.add(new ResourceDisplayInfo(resourceId, -imported, imported, exported));
                }
                if (exported > 0) {
                    produced.add(new ResourceDisplayInfo(resourceId, exported, imported, exported));
                }
            }
        } else if (syncedState == MachineState.CACHED || syncedState == MachineState.HALTED) {
            // CACHED/HALTED: Show based on rate sign
            for (java.util.Map.Entry<String, Double> entry : syncedCachedRates.entrySet()) {
                String resourceId = entry.getKey();
                double rate = entry.getValue();

                long[] counts = syncedLiveStats.get(resourceId);
                long imported = counts != null ? counts[0] : 0;
                long exported = counts != null ? counts[1] : 0;
                long cachedProdRaw = syncedCachedProduction.getOrDefault(resourceId, 0L);
                long cachedProd = rate < 0 ? -cachedProdRaw : cachedProdRaw;

                if (rate < 0) {
                    consumed.add(new ResourceDisplayInfo(resourceId, cachedProd, imported, exported, rate));
                } else {
                    produced.add(new ResourceDisplayInfo(resourceId, cachedProd, imported, exported, rate));
                }
            }
        }

        // Grid parameters
        int slotSize = 18; // 16px item + 1px padding each side
        int slotsPerRow = 5;
        int columnGap = 10; // Gap between consumed and produced columns

        // Calculate center line
        int centerX = imageWidth / 2;

        // Labels above grids
        Component consumedLabel = Component.literal("§cConsumed");
        int consumedLabelWidth = font.width(consumedLabel);
        graphics.drawString(font, consumedLabel,
            centerX - columnGap / 2 - consumedLabelWidth - 5, startY, 0xFFFFFF, false);

        Component producedLabel = Component.literal("§aProduced");
        graphics.drawString(font, producedLabel, centerX + columnGap / 2 + 5, startY, 0xFFFFFF, false);

        int gridStartY = startY + 12; // Below labels

        // Render consumed items (left side, right-aligned)
        renderItemGrid(graphics, consumed, centerX - columnGap / 2, gridStartY,
            slotsPerRow, slotSize, true, mouseX, mouseY);

        // Render divider line
        int dividerX = centerX;
        int dividerTop = gridStartY;
        int dividerBottom = imageHeight - 40;
        graphics.fill(dividerX - 1, dividerTop, dividerX, dividerBottom, 0xFF444444);

        // Render produced items (right side, left-aligned)
        renderItemGrid(graphics, produced, centerX + columnGap / 2, gridStartY,
            slotsPerRow, slotSize, false, mouseX, mouseY);
    }

    /**
     * Render a grid of items.
     * @param rightAlign If true, grid grows leftward; if false, grows rightward
     */
    // CHECKSTYLE.OFF: ParameterNumber - Grid rendering requires positioning parameters
    private void renderItemGrid(GuiGraphics graphics, java.util.List<ResourceDisplayInfo> items,
                                 int baseX, int baseY, int slotsPerRow, int slotSize,
                                 boolean rightAlign, int mouseX, int mouseY) {
    // CHECKSTYLE.ON: ParameterNumber
        for (int i = 0; i < items.size(); i++) {
            ResourceDisplayInfo info = items.get(i);
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;

            int slotX, slotY;
            if (rightAlign) {
                // Right-aligned: slots grow leftward
                slotX = baseX - (col + 1) * slotSize;
            } else {
                // Left-aligned: slots grow rightward
                slotX = baseX + col * slotSize;
            }
            slotY = baseY + row * slotSize;

            // Draw slot background (renderLabels coordinates are already relative to GUI)
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);

            // Render item/fluid (also uses relative coordinates in renderLabels)
            ItemStack stack = getItemStackForResource(info.getResourceId());
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, slotX, slotY);
            } else {
                // Fallback: draw question mark for unknown resources
                graphics.drawString(font, "?", slotX + 4, slotY + 4, 0xFFFFFF, false);
            }

            // Check if mouse is over this slot for tooltip (convert to screen coords)
            int screenX = leftPos + slotX;
            int screenY = topPos + slotY;
            if (mouseX >= screenX && mouseX < screenX + 16
                    && mouseY >= screenY && mouseY < screenY + 16) {
                // Store for tooltip rendering
                hoveredResource = info;
            }
        }
    }

    /**
     * Convert resource ID to ItemStack for rendering.
     */
    private ItemStack getItemStackForResource(String resourceId) {
        try {
            ResourceLocation rl = ResourceLocation.parse(resourceId);

            // Try as item first
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != Items.AIR) {
                return new ItemStack(item);
            }

            // Try as fluid
            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            if (fluid != Fluids.EMPTY) {
                // Get bucket item for fluid
                Item bucketItem = fluid.getBucket();
                if (bucketItem != Items.AIR) {
                    return new ItemStack(bucketItem);
                }
            }

            // Fallback: empty stack
            return ItemStack.EMPTY;

        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    // Tooltip tracking
    private ResourceDisplayInfo hoveredResource = null;

    /**
     * Resource display information for inventory grid.
     */
    private static final class ResourceDisplayInfo {
        private final String resourceId;
        private final long displayCount; // Main count to show (negative for consumed)
        private final long imported;
        private final long exported;
        private final Double rate; // Nullable, only for CACHED states

        ResourceDisplayInfo(String resourceId, long displayCount, long imported, long exported) {
            this(resourceId, displayCount, imported, exported, null);
        }

        ResourceDisplayInfo(String resourceId, long displayCount,
                           long imported, long exported, Double rate) {
            this.resourceId = resourceId;
            this.displayCount = displayCount;
            this.imported = imported;
            this.exported = exported;
            this.rate = rate;
        }

        public String getResourceId() {
            return resourceId;
        }

        public long getDisplayCount() {
            return displayCount;
        }

        public long getImported() {
            return imported;
        }

        public long getExported() {
            return exported;
        }

        public Double getRate() {
            return rate;
        }
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll up = positive, scroll down = negative
        int scrollAmount = (int) (scrollY * 10); // 10 pixels per scroll notch
        scrollOffset -= scrollAmount; // Invert: scroll down increases offset

        // Clamp to valid range
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Reset hovered resource each frame
        hoveredResource = null;

        // Render background first
        super.render(graphics, mouseX, mouseY, partialTick);

        // Check if mouse is actually over the button (not just focused)
        boolean mouseOverButton = controlButton != null
            && mouseX >= controlButton.getX()
            && mouseX <= controlButton.getX() + controlButton.getWidth()
            && mouseY >= controlButton.getY()
            && mouseY <= controlButton.getY() + controlButton.getHeight();

        // Reset confirmation if mouse moved away from button
        if (controlButton != null && haltedConfirmationPending && !mouseOverButton) {
            haltedConfirmationPending = false;
            controlButton.setMessage(getButtonLabel(syncedState));
        }

        // Render tooltips
        renderTooltips(graphics, mouseX, mouseY, mouseOverButton);

        // Render resource tooltip if hovering over an item
        if (hoveredResource != null) {
            renderResourceTooltip(graphics, mouseX, mouseY);
        }
    }

    /**
     * Render tooltip for hovered resource in inventory grid.
     */
    private void renderResourceTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        java.util.List<Component> tooltip = new java.util.ArrayList<>();

        // Item name
        String localizedName = getLocalizedName(hoveredResource.getResourceId());
        tooltip.add(Component.literal("§f" + localizedName));

        // Add stats based on state
        if (syncedState == MachineState.SIMULATING) {
            tooltip.add(Component.literal("§7Simulation Stats:"));
            if (hoveredResource.getImported() > 0) {
                tooltip.add(Component.literal("§c  Imported: "
                    + hoveredResource.getImported()));
            }
            if (hoveredResource.getExported() > 0) {
                tooltip.add(Component.literal("§a  Exported: "
                    + hoveredResource.getExported()));
            }
        } else if (syncedState == MachineState.CACHED || syncedState == MachineState.HALTED) {
            if (hoveredResource.getRate() != null) {
                double rate = hoveredResource.getRate();
                String rateColor = rate > 0 ? "§a" : "§c";
                String rateStr = String.format("%.3f", rate);
                tooltip.add(Component.literal(rateColor + "Rate: " + rateStr + "/tick"));
            }

            if (menu.isCreativeMode()) {
                tooltip.add(Component.literal("§7Simulation: §e"
                    + hoveredResource.getImported() + "/" + hoveredResource.getExported()));

                String displaySign = hoveredResource.getDisplayCount() >= 0 ? "+" : "";
                tooltip.add(Component.literal("§dCached: " + displaySign
                    + hoveredResource.getDisplayCount()));
            }
        }

        // Render tooltip
        graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
    }

    /**
     * Render all tooltips (button and text area tooltips).
     */
    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY, boolean mouseOverButton) {
        // Check what the mouse is hovering over (priority: button > text areas)
        boolean renderingTooltip = false;

        // Priority 1: Button tooltip (if actually hovering, not just focused)
        if (mouseOverButton) {
            MachineState state = menu.getCurrentState();

            // Don't show tooltip during confirmation state (user already knows what they're doing)
            if (state == MachineState.HALTED && !haltedConfirmationPending) {
                // HALTED state: Show warning about cache invalidation (first click only)
                java.util.List<FormattedCharSequence> haltedTooltip =
                    java.util.List.of(
                        Component.literal("§cWarning: Will invalidate current cache!"),
                        Component.literal("§fResume simulation to re-measure rates"),
                        Component.literal("§7(Click twice to confirm)")
                    ).stream()
                    .map(Component::getVisualOrderText)
                    .toList();
                graphics.renderTooltip(font, haltedTooltip, mouseX, mouseY);
                renderingTooltip = true;
            } else if (state != MachineState.HALTED || !haltedConfirmationPending) {
                // Other states: Simple tooltip (but not during confirmation)
                renderStateButtonTooltip(graphics, state, mouseX, mouseY);
                renderingTooltip = true;
            }
        }

        // Priority 2: Text area tooltips (only if not already rendering button tooltip)
        if (!renderingTooltip && stateHeight > 0) {
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
                        Component.literal("§fFactory chunks are loaded and ticking")
                    );
                    case CACHED -> java.util.List.of(
                        Component.literal("§aCACHED"),
                        Component.literal("§fRunning virtually using cached rates"),
                        Component.literal("§fFactory chunks are §cunloaded§f (TPS saved!)")
                    );
                    case HALTED -> java.util.List.of(
                        Component.literal("§cHALTED"),
                        Component.literal("§fCache broken (input starved or output blocked)"),
                        Component.literal("§fClick 'Reset Cache' to return to BUILDING")
                    );
                };
                renderTooltipFromComponents(graphics, tooltipComponents, mouseX, mouseY);
                renderingTooltip = true;
            }
        }

        // Render tooltip for "Simulation Time" (creative + CACHED only)
        if (!renderingTooltip && menu.isCreativeMode() && syncedState == MachineState.CACHED
                && simulationTimeHeight > 0) {
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
                renderingTooltip = true;
            }
        }

        // Render tooltip for "Cached Ticks" (creative + CACHED only)
        if (!renderingTooltip && menu.isCreativeMode() && syncedState == MachineState.CACHED
                && cachedTicksHeight > 0) {
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
                renderingTooltip = true;
            }
        }

        // Render tooltip for item stats (creative + CACHED only)
        if (!renderingTooltip && menu.isCreativeMode() && syncedState == MachineState.CACHED
                && itemStatsHeight > 0) {
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
