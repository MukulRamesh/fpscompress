package com.mukulramesh.fpscompress.gui;

import com.mukulramesh.fpscompress.portal.FaceConfig;
import com.mukulramesh.fpscompress.portal.FaceMode;
import com.mukulramesh.fpscompress.portal.ResourceFilter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client-side GUI screen for configuring PreFab faces.
 *
 * Phase 1 Part C - Simple face configuration:
 * - Select face direction
 * - Set mode (DISABLED/PULL/PUSH)
 * - Set filter (ALL/ITEMS/FLUIDS/ENERGY)
 * - Save changes (sends packet to server)
 */
public class PreFabConfigScreen extends AbstractContainerScreen<PreFabConfigMenu> {
    private Direction selectedFace = Direction.NORTH;

    // Button references for updating states
    private Button[] modeButtons = new Button[3];
    private Button[] filterButtons = new Button[4];

    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 5;

    public PreFabConfigScreen(PreFabConfigMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 80;

        // Face selection buttons (6 directions)
        int faceButtonY = startY;
        for (Direction dir : Direction.values()) {
            int buttonX = centerX - 180 + (dir.ordinal() * 60);
            addRenderableWidget(Button.builder(
                Component.literal(dir.getName().toUpperCase()),
                btn -> selectFace(dir)
            ).bounds(buttonX, faceButtonY, 55, BUTTON_HEIGHT).build());
        }

        // Mode buttons
        int modeY = startY + 40;
        addRenderableWidget(Button.builder(
            Component.literal("Mode:"),
            btn -> {}
        ).bounds(centerX - 180, modeY, 60, BUTTON_HEIGHT).build());

        modeButtons[0] = addRenderableWidget(Button.builder(
            Component.literal("DISABLED"),
            btn -> setMode(FaceMode.DISABLED)
        ).bounds(centerX - 110, modeY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        modeButtons[1] = addRenderableWidget(Button.builder(
            Component.literal("PULL"),
            btn -> setMode(FaceMode.PULL)
        ).bounds(centerX - 20, modeY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        modeButtons[2] = addRenderableWidget(Button.builder(
            Component.literal("PUSH"),
            btn -> setMode(FaceMode.PUSH)
        ).bounds(centerX + 70, modeY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // Filter buttons
        int filterY = startY + 70;
        addRenderableWidget(Button.builder(
            Component.literal("Filter:"),
            btn -> {}
        ).bounds(centerX - 180, filterY, 60, BUTTON_HEIGHT).build());

        filterButtons[0] = addRenderableWidget(Button.builder(
            Component.literal("ALL"),
            btn -> setFilter(ResourceFilter.ALL)
        ).bounds(centerX - 110, filterY, 60, BUTTON_HEIGHT).build());

        filterButtons[1] = addRenderableWidget(Button.builder(
            Component.literal("ITEMS"),
            btn -> setFilter(ResourceFilter.ITEMS)
        ).bounds(centerX - 40, filterY, 60, BUTTON_HEIGHT).build());

        filterButtons[2] = addRenderableWidget(Button.builder(
            Component.literal("FLUIDS"),
            btn -> setFilter(ResourceFilter.FLUIDS)
        ).bounds(centerX + 30, filterY, 60, BUTTON_HEIGHT).build());

        filterButtons[3] = addRenderableWidget(Button.builder(
            Component.literal("ENERGY"),
            btn -> setFilter(ResourceFilter.ENERGY)
        ).bounds(centerX + 100, filterY, 60, BUTTON_HEIGHT).build());

        // Save button
        addRenderableWidget(Button.builder(
            Component.literal("Save"),
            btn -> save()
        ).bounds(centerX - 40, startY + 110, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // Update button states for currently selected face
        updateButtonStates();
    }

    private void selectFace(Direction face) {
        this.selectedFace = face;
        updateButtonStates();
    }

    private void setMode(FaceMode mode) {
        FaceConfig config = menu.getFaceConfig(selectedFace);
        config.setMode(mode);
        updateButtonStates();
    }

    private void setFilter(ResourceFilter filter) {
        FaceConfig config = menu.getFaceConfig(selectedFace);
        config.setResourceType(filter);
        updateButtonStates();
    }

    private void updateButtonStates() {
        FaceConfig config = menu.getFaceConfig(selectedFace);

        // Update mode button appearance (active = different message)
        for (int i = 0; i < modeButtons.length; i++) {
            FaceMode mode = FaceMode.values()[i];
            boolean active = config.getMode() == mode;
            modeButtons[i].setMessage(Component.literal(
                (active ? "§a[" : "§7") + mode.name() + (active ? "]" : "")
            ));
        }

        // Update filter button appearance
        for (int i = 0; i < filterButtons.length; i++) {
            ResourceFilter filter = ResourceFilter.values()[i];
            boolean active = config.getResourceType() == filter;
            filterButtons[i].setMessage(Component.literal(
                (active ? "§a[" : "§7") + filter.name() + (active ? "]" : "")
            ));
        }
    }

    private void save() {
        menu.saveToServer();
        this.onClose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Render background - can be empty for now
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render default background
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Render selected face indicator
        graphics.drawString(this.font,
            "Configuring: §6" + selectedFace.getName().toUpperCase(),
            this.width / 2 - 80, this.height / 2 - 100, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
