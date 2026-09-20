package com.eboac.terracraft.client.screen;

import com.eboac.terracraft.menu.CraftBrowserMenu;
import com.eboac.terracraft.net.BrowserActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The browser window.
 *
 * <p>Drawn procedurally with filled rectangles rather than a texture, so the mod ships without
 * any image assets and the panel can be any size we like.
 *
 * <p>This class decides nothing. Scrolling, filtering and searching are sent to the server, which
 * sends back the new contents. That round trip is what keeps a modified client from inventing
 * recipes it cannot afford.
 */
public class CraftBrowserScreen extends AbstractContainerScreen<CraftBrowserMenu> {

    private static final int WIDTH = 194;
    private static final int HEIGHT = 222;

    private static final int GRID_X = 8;
    private static final int GRID_Y = 32;
    private static final int SLOT = 18;

    private static final int SCROLLBAR_X = 174;
    private static final int SCROLLBAR_Y = 32;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HEIGHT = CraftBrowserMenu.VISIBLE_ROWS * SLOT;
    private static final int HANDLE_HEIGHT = 15;

    private static final int FILTER_X = CraftBrowserMenu.FILTER_X;
    private static final int FILTER_Y = CraftBrowserMenu.FILTER_Y;

    private static final int TOGGLE_X = 174;
    private static final int TOGGLE_Y = 14;
    private static final int TOGGLE_SIZE = 12;

    // Vanilla's GUI palette, so the panel does not look out of place.
    private static final int COLOUR_PANEL = 0xFFC6C6C6;
    private static final int COLOUR_SHADOW = 0xFF555555;
    private static final int COLOUR_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COLOUR_SLOT = 0xFF8B8B8B;
    private static final int COLOUR_SLOT_EDGE = 0xFF373737;
    private static final int COLOUR_UNCRAFTABLE = 0xA0101010;
    private static final int COLOUR_CHAINED = 0xFF5FD068;
    private static final int COLOUR_TOGGLE_ON = 0xFF4CAF50;
    private static final int COLOUR_TOGGLE_OFF = 0xFF7A7A7A;

    private EditBox searchBox;
    private boolean draggingScrollbar;

    public CraftBrowserScreen(CraftBrowserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.inventoryLabelY = HEIGHT - 94;
        this.titleLabelY = 5;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(this.font, this.leftPos + 8, this.topPos + 16, 132, 12,
                Component.translatable("gui.terracraft.search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        // Colours here are ARGB. 0x404040 has an alpha of zero, which draws perfectly
        // transparent text -- the typing works, you just cannot see any of it.
        searchBox.setTextColor(0xFF404040);
        searchBox.setTextColorUneditable(0xFF707070);
        searchBox.setHint(Component.translatable("gui.terracraft.search")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        searchBox.setValue(this.menu.search());
        searchBox.setResponder(text -> sendState(this.menu.scrollRow()));
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        // The server builds the list while constructing the menu, which happens before the client
        // has been told to open this screen -- so that first state packet arrives with no menu to
        // apply it to and is dropped. Asking again now that the screen exists is what makes the
        // list appear at all.
        sendState(this.menu.scrollRow());
    }

    // ------------------------------------------------------------------
    // Talking to the server
    // ------------------------------------------------------------------

    private void sendState(int scrollRow) {
        ClientPlayNetworking.send(new BrowserActionPayload(
                scrollRow,
                this.menu.showUncraftable(),
                searchBox == null ? "" : searchBox.getValue()));
    }

    private void sendToggle() {
        ClientPlayNetworking.send(new BrowserActionPayload(
                this.menu.scrollRow(),
                !this.menu.showUncraftable(),
                searchBox == null ? "" : searchBox.getValue()));
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (isOver(mouseX, mouseY, TOGGLE_X, TOGGLE_Y, TOGGLE_SIZE, TOGGLE_SIZE)) {
            sendToggle();
            return true;
        }

        if (isOver(mouseX, mouseY, SCROLLBAR_X, SCROLLBAR_Y, SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT)) {
            draggingScrollbar = true;
            scrollTo(mouseY);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = this.menu.maxScrollRow();
        if (max > 0) {
            int target = Math.clamp(this.menu.scrollRow() - (int) Math.signum(scrollY), 0, max);
            if (target != this.menu.scrollRow()) {
                sendState(target);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void scrollTo(double mouseY) {
        int max = this.menu.maxScrollRow();
        if (max <= 0) {
            return;
        }
        double relative = (mouseY - (this.topPos + SCROLLBAR_Y) - HANDLE_HEIGHT / 2.0)
                / (SCROLLBAR_HEIGHT - HANDLE_HEIGHT);
        int target = Math.clamp((int) Math.round(relative * max), 0, max);
        if (target != this.menu.scrollRow()) {
            sendState(target);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox != null && searchBox.isFocused() && event.key() != InputConstants.KEY_ESCAPE) {
            searchBox.keyPressed(event);
            // Consume everything except Escape. Falling through to super would let vanilla treat
            // a typed "e" as the close-inventory key and the digits as hotbar swaps.
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        // Without this, clicking the scrollbar or toggle would be treated as "clicked outside"
        // and throw the item on your cursor onto the floor.
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop)
                && !isOver(mouseX, mouseY, SCROLLBAR_X, SCROLLBAR_Y, SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT)
                && !isOver(mouseX, mouseY, TOGGLE_X, TOGGLE_Y, TOGGLE_SIZE, TOGGLE_SIZE);
    }

    private boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        double localX = mouseX - this.leftPos;
        double localY = mouseY - this.topPos;
        return localX >= x && localX < x + width && localY >= y && localY < y + height;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int x = this.leftPos;
        int y = this.topPos;

        panel(graphics, x, y, WIDTH, HEIGHT);

        // search field well
        sunken(graphics, x + 6, y + 14, 136, 16);

        // ingredient filter slot: drop an item here to see what it can make
        slot(graphics, x + FILTER_X, y + FILTER_Y);

        // recipe grid
        for (int row = 0; row < CraftBrowserMenu.VISIBLE_ROWS; row++) {
            for (int column = 0; column < CraftBrowserMenu.COLUMNS; column++) {
                slot(graphics, x + GRID_X + column * SLOT, y + GRID_Y + row * SLOT);
            }
        }

        // player inventory
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, x + 8 + column * SLOT, y + 140 + row * SLOT);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(graphics, x + 8 + column * SLOT, y + 198);
        }

        drawScrollbar(graphics, x, y);
        drawToggle(graphics, x, y);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int y) {
        sunken(graphics, x + SCROLLBAR_X, y + SCROLLBAR_Y, SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT);

        int max = this.menu.maxScrollRow();
        int travel = SCROLLBAR_HEIGHT - HANDLE_HEIGHT;
        int offset = max == 0 ? 0 : (int) ((long) travel * this.menu.scrollRow() / max);
        int handleY = y + SCROLLBAR_Y + offset;

        graphics.fill(x + SCROLLBAR_X + 1, handleY, x + SCROLLBAR_X + SCROLLBAR_WIDTH - 1,
                handleY + HANDLE_HEIGHT, max == 0 ? COLOUR_TOGGLE_OFF : COLOUR_PANEL);
        graphics.fill(x + SCROLLBAR_X + 1, handleY, x + SCROLLBAR_X + SCROLLBAR_WIDTH - 2,
                handleY + 1, COLOUR_HIGHLIGHT);
    }

    private void drawToggle(GuiGraphicsExtractor graphics, int x, int y) {
        sunken(graphics, x + TOGGLE_X, y + TOGGLE_Y, TOGGLE_SIZE, TOGGLE_SIZE);
        graphics.fill(x + TOGGLE_X + 2, y + TOGGLE_Y + 2,
                x + TOGGLE_X + TOGGLE_SIZE - 2, y + TOGGLE_Y + TOGGLE_SIZE - 2,
                this.menu.showUncraftable() ? COLOUR_TOGGLE_OFF : COLOUR_TOGGLE_ON);
    }

    private void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOUR_PANEL);
        graphics.fill(x, y, x + width, y + 1, COLOUR_HIGHLIGHT);
        graphics.fill(x, y, x + 1, y + height, COLOUR_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOUR_SHADOW);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOUR_SHADOW);
    }

    private void sunken(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOUR_SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + width, y + height, COLOUR_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, COLOUR_SLOT);
    }

    private void slot(GuiGraphicsExtractor graphics, int x, int y) {
        sunken(graphics, x - 1, y - 1, SLOT, SLOT);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        super.extractSlot(graphics, slot, mouseX, mouseY);

        int index = this.menu.slots.indexOf(slot);
        if (index < 0 || index >= CraftBrowserMenu.DISPLAY_SLOTS || slot.getItem().isEmpty()) {
            return;
        }

        if (!this.menu.isDisplaySlotObtainable(index)) {
            // Out of reach entirely -- dim it, the way the recipe book does.
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, COLOUR_UNCRAFTABLE);
        } else if (this.menu.isDisplaySlotChained(index)) {
            // Reachable, but only by crafting the parts first. Drawn at full brightness with a
            // small corner mark, so "I can get this" reads instantly and "it costs extra steps"
            // is there if you look.
            graphics.fill(slot.x, slot.y, slot.x + 3, slot.y + 1, COLOUR_CHAINED);
            graphics.fill(slot.x, slot.y, slot.x + 1, slot.y + 3, COLOUR_CHAINED);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        if (this.menu.totalEntries() == 0 && this.menu.slots.getFirst().getItem().isEmpty()) {
            boolean filtering = searchBox != null && !searchBox.getValue().isEmpty();
            graphics.text(this.font,
                    Component.translatable(filtering ? "gui.terracraft.no_match" : "gui.terracraft.nothing"),
                    GRID_X + 4, GRID_Y + 36, 0xFF808080);
        }
    }
}
