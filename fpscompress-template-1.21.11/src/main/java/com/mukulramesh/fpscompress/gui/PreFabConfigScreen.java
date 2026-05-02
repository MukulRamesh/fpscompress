package com.mukulramesh.fpscompress.gui;

import com.mukulramesh.fpscompress.portal.FaceConfig;
import com.mukulramesh.fpscompress.portal.FaceMode;
import com.mukulramesh.fpscompress.portal.ImporterExporterRegistry;
import com.mukulramesh.fpscompress.portal.ResourceFilter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * Client-side GUI screen for configuring PreFab faces.
 *
 * Phase 1 Part C - Simple face configuration:
 * - Select face direction
 * - Set mode (DISABLED/PULL/PUSH)
 * - Set filter (ALL/ITEMS/FLUIDS/ENERGY)
 * - Save changes (sends packet to server)
 *
 * TODO: GUI Structural Improvements (Post-Phase 1)
 * This GUI needs a proper inventory-based implementation:
 * 1. Create proper container GUI with inventory slots (not AbstractContainerScreen hack)
 * 2. Mode/Filter should be labels, NOT clickable buttons - buttons are for actions only
 * 3. Add proper texture background instead of transparent overlay
 * 4. Consider using toggle buttons or radio button groups for mode/filter selection
 * 5. Separate face selection from configuration - possibly use tabs or pages
 * 6. Add visual feedback for which Importer/Exporter each face links to (Phase 2)
 *
 * Current implementation is functional but not architecturally sound.
 */
public class PreFabConfigScreen extends AbstractContainerScreen<PreFabConfigMenu> {
    private Direction selectedFace;

    // Button references for updating states
    private Button[] modeButtons = new Button[3];
    private Button[] filterButtons = new Button[4];
    private Button linkButton;

    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 5;

    public PreFabConfigScreen(PreFabConfigMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 200;  // Taller to fit our buttons
        this.inventoryLabelY = 1000;  // Hide inventory label (push off screen)
        this.titleLabelY = 1000;  // Hide title label (push off screen)
        this.selectedFace = menu.getDefaultFace();  // Set default to clicked face
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 80;

        // Face selection buttons (6 directions) - store references for highlighting
        Button[] faceButtons = new Button[6];
        int faceButtonY = startY;
        int idx = 0;
        for (Direction dir : Direction.values()) {
            int buttonX = centerX - 180 + (idx * 60);
            final Direction capturedDir = dir;
            final int capturedIdx = idx;
            faceButtons[idx] = addRenderableWidget(Button.builder(
                Component.literal(dir.getName().toUpperCase(Locale.ROOT)),
                btn -> {
                    selectFace(capturedDir);
                    updateFaceButtonHighlights(faceButtons, capturedIdx);
                }
            ).bounds(buttonX, faceButtonY, 55, BUTTON_HEIGHT).build());
            idx++;
        }

        // Set initial selection highlight based on default face
        int defaultIndex = selectedFace.ordinal();
        updateFaceButtonHighlights(faceButtons, defaultIndex);

        // Mode buttons
        int modeY = startY + 40;
        addRenderableWidget(Button.builder(
            Component.literal("Mode:"),
            btn -> { }
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
            btn -> { }
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

        // Link button (Phase 2 - cycle through available Importers/Exporters)
        int linkY = startY + 100;
        linkButton = addRenderableWidget(Button.builder(
            Component.literal("Link: None"),
            btn -> cycleLink()
        ).bounds(centerX - 110, linkY, 220, BUTTON_HEIGHT).build());

        // Save button
        addRenderableWidget(Button.builder(
            Component.literal("Save"),
            btn -> save()
        ).bounds(centerX - 40, startY + 130, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // Update button states for currently selected face
        updateButtonStates();
    }

    private void selectFace(Direction face) {
        this.selectedFace = face;
        updateButtonStates();
    }

    private void updateFaceButtonHighlights(Button[] faceButtons, int selectedIndex) {
        for (int i = 0; i < faceButtons.length; i++) {
            Button btn = faceButtons[i];
            Direction dir = Direction.values()[i];
            boolean isSelected = (i == selectedIndex);
            btn.setMessage(Component.literal(
                (isSelected ? "§a" : "§7") + dir.getName().toUpperCase(Locale.ROOT)
            ));
        }
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

    private void cycleLink() {
        FaceConfig config = menu.getFaceConfig(selectedFace);
        FaceMode mode = config.getMode();

        if (mode == FaceMode.DISABLED) {
            return; // No linking for disabled faces
        }

        // Get available Importers or Exporters based on mode
        List<ImporterExporterRegistry.Entry> available = mode == FaceMode.PULL
            ? menu.getAvailableImporters()
            : menu.getAvailableExporters();

        if (available.isEmpty()) {
            config.setTargetUUID(null);
            updateButtonStates();
            return;
        }

        // Find current index
        int currentIndex = -1;
        if (config.getTargetUUID() != null) {
            for (int i = 0; i < available.size(); i++) {
                if (available.get(i).uuid().equals(config.getTargetUUID())) {
                    currentIndex = i;
                    break;
                }
            }
        }

        // Cycle to next (or first if none selected)
        int nextIndex = (currentIndex + 1) % available.size();
        config.setTargetUUID(available.get(nextIndex).uuid());

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

        // Update link button based on mode and current selection
        FaceMode mode = config.getMode();
        if (mode == FaceMode.DISABLED) {
            linkButton.active = false;
            linkButton.setMessage(Component.literal("§7Link: (Disabled)"));
        } else {
            linkButton.active = true;
            String linkType = mode == FaceMode.PULL ? "Importer" : "Exporter";

            if (config.getTargetUUID() == null) {
                linkButton.setMessage(Component.literal("§cLink: No " + linkType + " selected"));
            } else {
                // Show display name (e.g., "Apple Importer")
                ImporterExporterRegistry.Entry entry = findEntry(config.getTargetUUID(), mode);
                if (entry != null) {
                    linkButton.setMessage(Component.literal(
                        String.format("§aLink: %s", entry.displayName())
                    ));
                } else {
                    linkButton.setMessage(Component.literal("§cLink: " + linkType + " not found"));
                }
            }
        }
    }

    private ImporterExporterRegistry.Entry findEntry(java.util.UUID uuid, FaceMode mode) {
        List<ImporterExporterRegistry.Entry> available = mode == FaceMode.PULL
            ? menu.getAvailableImporters()
            : menu.getAvailableExporters();

        for (ImporterExporterRegistry.Entry entry : available) {
            if (entry.uuid().equals(uuid)) {
                return entry;
            }
        }
        return null;
    }

    private void save() {
        menu.saveToServer();
        this.onClose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Render background with dark overlay
        renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render default background
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render custom title at top
        graphics.drawCenteredString(this.font, "PreFab Configuration",
            this.width / 2, this.height / 2 - 95, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // Auto-save when GUI closes (ESC key or any other way)
        menu.saveToServer();
        super.removed();
    }
}
