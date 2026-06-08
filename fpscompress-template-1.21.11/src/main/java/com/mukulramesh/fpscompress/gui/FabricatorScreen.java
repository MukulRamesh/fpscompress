package com.mukulramesh.fpscompress.gui;

import com.mukulramesh.fpscompress.blueprint.BlueprintData;
import com.mukulramesh.fpscompress.component.FPSDataComponents;
import com.mukulramesh.fpscompress.network.PrintRequestPacket;
import com.mukulramesh.fpscompress.network.ScanRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side GUI screen for the Fabricator block.
 *
 * <p>Layout (176×222):
 * <pre>
 * ┌─────────────────────────────────┐
 * │         Fabricator              │  ← title
 * │  [Input]  →  [Output]           │  ← slots 0, 1 at top
 * │       [ Action Button ]         │  ← single state-aware button
 * │  "X / Y resources satisfied"    │  ← status text
 * │  ┌─── Resource Slots 3×9 ───┐  │  ← bordered box
 * │  │ [  ][  ][  ]...[  ][  ][ ]│  │
 * │  │ [  ][  ][  ]...[  ][  ][ ]│  │
 * │  │ [  ][  ][  ]...[  ][  ][ ]│  │
 * │  └────────────────────────────┘  │
 * │  Player Inventory (3×9 + hotbar) │  ← standard rendering
 * └─────────────────────────────────┘
 * </pre>
 */
public class FabricatorScreen extends AbstractContainerScreen<FabricatorMenu> {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 222;

    private static final int BUTTON_X = 44;
    private static final int BUTTON_Y = 38;
    private static final int BUTTON_WIDTH = 88;
    private static final int BUTTON_HEIGHT = 16;

    // Resource slot area for border rendering
    private static final int RESOURCE_BOX_X = 7;
    private static final int RESOURCE_BOX_Y = 68;
    private static final int RESOURCE_BOX_WIDTH = 162;
    private static final int RESOURCE_BOX_HEIGHT = 56;

    // Slot background colors (vanilla-style beveled)
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF; // top/left edge highlight
    private static final int SLOT_SHADOW = 0xFF555555;    // bottom/right edge shadow
    private static final int SLOT_INNER = 0xFF8B8B8B;     // interior
    private static final int SLOT_INPUT_HIGHLIGHT = 0xFFFFE8A0; // gold-tinted highlight
    private static final int SLOT_INPUT_SHADOW = 0xFF8B6914;    // gold-tinted shadow
    private static final int SLOT_OUTPUT_INNER = 0xFF7A8B7A;    // green-tinted interior
    // Box / border colors
    private static final int BG_COLOR = 0xFFC6C6C6;
    private static final int OUTER_BORDER = 0xFF373737;
    private static final int AREA_BORDER = 0xFF555555;

    private Button actionButton;

    // Tracked state for detecting changes
    private int lastScanState = -1;
    private int lastRequiredCount = -1;
    private int lastAvailableCount = -1;
    private boolean lastPrefabValid = false;
    private boolean lastHasPreFab = false;
    private boolean lastHasBlueprint = false;
    private boolean lastOutputEmpty = true;
    private BlueprintData.ResourceRequirement hoveredRequirement = null;

    public FabricatorScreen(FabricatorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, Component.literal("Fabricator"));
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = GUI_HEIGHT - 94; // 128 — below resource box
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();

        // Single action button — label and active state updated via updateButtonState()
        actionButton = Button.builder(
            Component.literal("Idle"),
            btn -> onActionButtonPressed()
        )
        .bounds(leftPos + BUTTON_X, topPos + BUTTON_Y,
            BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();

        addRenderableWidget(actionButton);
        updateButtonState();
    }

    /**
     * Called every client tick while the screen is open.
     * Ensures button state updates reactively without needing to close/reopen.
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonState();
    }

    /**
     * Handle the action button press.
     * Delegates to scan or print based on current input contents.
     */
    private void onActionButtonPressed() {
        if (menu.hasPreFabInInput()) {
            PacketDistributor.sendToServer(
                new ScanRequestPacket(menu.getFabricatorPos())
            );
        } else if (menu.hasBlueprintInInput()) {
            PacketDistributor.sendToServer(
                new PrintRequestPacket(menu.getFabricatorPos())
            );
        }
    }

    /**
     * Update the single action button's label and active state
     * based on the current menu state.
     */
    private void updateButtonState() {
        int scanState = menu.getScanState();
        boolean hasPreFab = menu.hasPreFabInInput();
        boolean hasBlueprint = menu.hasBlueprintInInput();
        boolean outputEmpty = menu.isOutputSlotEmpty();
        int requiredCount = menu.getRequiredResourceCount();
        int availableCount = menu.getAvailableResourceCount();

        boolean prefabValid = menu.isPrefabValid();

        // Skip if nothing changed
        if (scanState == lastScanState && requiredCount == lastRequiredCount
                && availableCount == lastAvailableCount && hasPreFab == lastHasPreFab
                && hasBlueprint == lastHasBlueprint && outputEmpty == lastOutputEmpty
                && prefabValid == lastPrefabValid) {
            return;
        }
        lastScanState = scanState;
        lastRequiredCount = requiredCount;
        lastAvailableCount = availableCount;
        lastHasPreFab = hasPreFab;
        lastHasBlueprint = hasBlueprint;
        lastOutputEmpty = outputEmpty;
        lastPrefabValid = prefabValid;

        String label;
        boolean active;

        if (hasPreFab) {
            // PreFab in input slot — check server validation
            boolean valid = menu.isPrefabValid();
            if (!outputEmpty) {
                label = "Output occupied";
                active = false;
            } else if (!valid) {
                label = "Invalid PreFab";
                active = false;
            } else if (scanState == 2) {
                label = "Scanning...";
                active = false;
            } else {
                label = "Scan";
                active = true;
            }
        } else if (hasBlueprint && scanState == 3) {
            // Blueprint in input slot — server validated
            if (!outputEmpty) {
                label = "Output occupied";
                active = false;
            } else if (availableCount >= requiredCount && requiredCount > 0) {
                label = "Print";
                active = true;
            } else {
                label = "Resource Starved";
                active = false;
            }
        } else {
            // Nothing in input
            label = "Idle";
            active = false;
        }

        // Apply Minecraft color formatting
        String formattedLabel;
        if (active) {
            formattedLabel = "§a" + label;
        } else {
            formattedLabel = "§7" + label;
        }

        actionButton.setMessage(Component.literal(formattedLabel));
        actionButton.active = active;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int lx = leftPos;
        int ty = topPos;

        // Main background — vanilla container gray
        graphics.fill(lx, ty, lx + imageWidth, ty + imageHeight, BG_COLOR);

        // Outer border — dark frame
        graphics.fill(lx, ty, lx + imageWidth, ty + 2, OUTER_BORDER);
        graphics.fill(lx, ty + imageHeight - 2, lx + imageWidth, ty + imageHeight, OUTER_BORDER);
        graphics.fill(lx, ty, lx + 2, ty + imageHeight, OUTER_BORDER);
        graphics.fill(lx + imageWidth - 2, ty, lx + imageWidth, ty + imageHeight, OUTER_BORDER);

        // Slot 0: Input — gold-tinted highlight
        drawSlotBg(graphics, lx + 44, ty + 20,
            SLOT_INPUT_HIGHLIGHT, SLOT_INPUT_SHADOW, SLOT_INNER);
        // Slot 1: Output — green-tinted interior to distinguish
        drawSlotBg(graphics, lx + 116, ty + 20,
            SLOT_HIGHLIGHT, SLOT_SHADOW, SLOT_OUTPUT_INNER);

        // Arrow between input and output
        graphics.drawString(font, "→", lx + 80, ty + 22, 0xFF404040, false);

        // Slots 2-28: Resource slots (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = lx + 8 + col * 18;
                int sy = ty + 70 + row * 18;
                drawSlotBg(graphics, sx, sy, SLOT_HIGHLIGHT, SLOT_SHADOW, SLOT_INNER);
            }
        }

        // Resource slots area border
        drawBox(graphics, lx + RESOURCE_BOX_X, ty + RESOURCE_BOX_Y,
            RESOURCE_BOX_WIDTH, RESOURCE_BOX_HEIGHT, AREA_BORDER);

        // Player inventory slot backgrounds (3 rows of 9 main + 1 row of 9 hotbar)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = lx + 8 + col * 18;
                int sy = ty + 138 + row * 18;
                drawSlotBg(graphics, sx, sy, SLOT_HIGHLIGHT, SLOT_SHADOW, SLOT_INNER);
            }
        }
        for (int col = 0; col < 9; col++) {
            int sx = lx + 8 + col * 18;
            int sy = ty + 196;
            drawSlotBg(graphics, sx, sy, SLOT_HIGHLIGHT, SLOT_SHADOW, SLOT_INNER);
        }

        // Player inventory area border
        int playerBoxY = ty + 136;
        int playerBoxH = 78;
        drawBox(graphics, lx + RESOURCE_BOX_X, playerBoxY,
            RESOURCE_BOX_WIDTH, playerBoxH, AREA_BORDER);
    }

    /**
     * Draw a vanilla-style beveled slot background with highlight/shadow edges.
     * The interior exactly matches the 16×16 vanilla slot area so the hover highlight aligns.
     *
     * @param graphics The graphics context
     * @param x Left edge screen X (slot position)
     * @param y Top edge screen Y (slot position)
     * @param highlightColor ARGB for top/left raised edge
     * @param shadowColor ARGB for bottom/right lowered edge
     * @param innerColor ARGB for the 16×16 interior
     */
    private static void drawSlotBg(GuiGraphics graphics, int x, int y,
                                   int highlightColor, int shadowColor, int innerColor) {
        // 1px highlight on top and left (raised edge)
        graphics.fill(x - 1, y - 1, x + 17, y, highlightColor);
        graphics.fill(x - 1, y, x, y + 16, highlightColor);
        // 1px shadow on bottom and right (lowered edge)
        graphics.fill(x - 1, y + 16, x + 17, y + 17, shadowColor);
        graphics.fill(x + 16, y, x + 17, y + 16, shadowColor);
        // 16×16 interior
        graphics.fill(x, y, x + 16, y + 16, innerColor);
    }

    /**
     * Draw a rectangular border (1px thick outline, no fill).
     *
     * @param graphics The graphics context
     * @param x Left edge X
     * @param y Top edge Y
     * @param width Box width
     * @param height Box height
     * @param color ARGB border color
     */
    private static void drawBox(GuiGraphics graphics, int x, int y,
                                int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Title — dark gray like vanilla "Inventory"
        graphics.drawString(font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);

        // Player inventory label
        graphics.drawString(font, this.playerInventoryTitle,
            this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        // Dynamic "Blueprint Resources" label
        int scanState = menu.getScanState();
        int requiredCount = menu.getRequiredResourceCount();
        int availableCount = menu.getAvailableResourceCount();

        if (scanState == 3 && requiredCount > 0) {
            // Blueprint loaded: show count with color
            boolean allSatisfied = availableCount >= requiredCount;
            String label = "Blueprint Resources: " + availableCount + " / " + requiredCount;
            int color = allSatisfied ? 0x00AA00 : 0xFF5555;
            graphics.drawString(font, label, 8, 58, color, false);
        } else {
            // Idle/scanning: plain gray label
            graphics.drawString(font, "Blueprint Resources",
                8, 58, 0x404040, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (menu.hasBlueprintInInput()) {
            renderSlotStatusIndicators(graphics, mouseX, mouseY);
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Render slot status indicators when a blueprint is loaded:
     * - Empty (0 items): red blink + ghost item
     * - Partial (&lt; requirement): yellow blink
     * - Satisfied (≥ requirement): solid green background
     */
    private void renderSlotStatusIndicators(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack blueprintStack = menu.slots.get(0).getItem();
        if (blueprintStack.isEmpty()) {
            return;
        }
        CompoundTag nbt = blueprintStack.get(FPSDataComponents.BLUEPRINT_DATA.get());
        if (nbt == null) {
            return;
        }
        BlueprintData blueprint = BlueprintData.fromNBT(nbt);
        if (blueprint.isEmpty()) {
            return;
        }

        List<BlueprintData.ResourceRequirement> allReqs = new ArrayList<>();
        allReqs.addAll(blueprint.getBlockResources());
        allReqs.addAll(blueprint.getItemResources());

        // Blink alpha: ~2.5s sine cycle
        float raw = (float) Math.sin(System.currentTimeMillis() / 800.0);
        int blinkAlpha = (int) ((raw * 0.5f + 0.5f) * 60 + 35);
        int redBlink = (blinkAlpha << 24) | 0x00CC4444;
        int yellowBlink = (blinkAlpha << 24) | 0x00FFCC44;
        int solidGreen = 0x4400AA00;

        int slot = 2;
        int reqIndex = 0;
        for (BlueprintData.ResourceRequirement req : allReqs) {
            if (slot >= 29) {
                break;
            }

            int col = (slot - 2) % 9;
            int row = (slot - 2) / 9;
            int sx = leftPos + 8 + col * 18;
            int sy = topPos + 70 + row * 18;

            ItemStack realStack = menu.slots.get(slot).getItem();
            int count = realStack.isEmpty() ? 0 : realStack.getCount();
            long required = req.count();

            boolean hasNbtRequirement = req.nbt() != null && !req.nbt().isEmpty();

            if (hasNbtRequirement) {
                // Use server-side validation bitmask for NBT-required items
                if (menu.isSlotSatisfied(reqIndex)) {
                    graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, solidGreen);
                } else if (count > 0) {
                    graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, yellowBlink);
                } else {
                    graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, redBlink);
                    renderGhostItem(graphics, req, sx, sy);
                }
            } else if (count >= required) {
                // Solid green — satisfied (no NBT to validate)
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, solidGreen);
            } else if (count > 0) {
                // Yellow blink — partial
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, yellowBlink);
                renderGhostItem(graphics, req, sx, sy);
            } else {
                // Red blink — empty, show ghost item
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, redBlink);
                renderGhostItem(graphics, req, sx, sy);
            }

            // Track hovered ghost for tooltip — only when slot actually needs items
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16
                    && count < required) {
                hoveredRequirement = req;
            }

            slot++;
            reqIndex++;
        }

        // Render tooltip for hovered ghost item
        if (hoveredRequirement != null) {
            List<Component> tooltip = new ArrayList<>();
            String shortName = reqShortName(hoveredRequirement.id());
            tooltip.add(Component.literal("Need " + hoveredRequirement.count()
                + " x " + shortName));
            CompoundTag reqNbt = hoveredRequirement.nbt();
            if (reqNbt != null && !reqNbt.isEmpty()) {
                tooltip.add(Component.literal("§cRequires NBT:")
                    .withStyle(net.minecraft.ChatFormatting.RED));
                for (String key : reqNbt.getAllKeys()) {
                    tooltip.add(Component.literal("  §7" + key));
                }
            }
            graphics.renderTooltip(font, tooltip,
                java.util.Optional.empty(), mouseX, mouseY);
        }
        hoveredRequirement = null;
    }

    /**
     * Render a single ghost item icon showing what's needed in this slot.
     */
    private static String reqShortName(String resourceId) {
        return resourceId.contains(":") ? resourceId.split(":")[1] : resourceId;
    }

    private static void renderGhostItem(GuiGraphics graphics,
                                         BlueprintData.ResourceRequirement req,
                                         int sx, int sy) {
        try {
            ResourceLocation rl = ResourceLocation.parse(req.id());
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != net.minecraft.world.item.Items.AIR) {
                ItemStack ghost = new ItemStack(item,
                    (int) Math.min(req.count(), 64));
                graphics.renderItem(ghost, sx, sy);
            }
        } catch (net.minecraft.ResourceLocationException e) {
            // Skip invalid IDs
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
