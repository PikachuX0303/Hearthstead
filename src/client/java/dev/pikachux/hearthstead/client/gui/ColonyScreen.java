package dev.pikachux.hearthstead.client.gui;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Base for the mod's screens: a vanilla-styled panel with helpers for text,
 * bars, slots and a scrollable list.
 */
public abstract class ColonyScreen extends Screen {
    public static final int TEXT = 0xFF3B2A1A;
    public static final int TEXT_LIGHT = 0xFF7A6248;
    public static final int GOOD = 0xFF2E6B1F;
    public static final int BAD = 0xFF9E1B1B;
    public static final int HEADER = 0xFF6B2E0E;

    private static final Identifier WINDOW = Hearthstead.id("window");
    private static final Identifier INSET = Hearthstead.id("inset");
    private static final Identifier SLOT = Hearthstead.id("slot");

    protected final int panelWidth;
    protected final int panelHeight;
    protected int left;
    protected int top;
    /** Scroll offset of the list drawn with {@link #renderList}, in rows. */
    protected int scroll;
    private int listX, listY, listWidth, listHeight, listRowHeight, listRows;

    protected ColonyScreen(Component title, int panelWidth, int panelHeight) {
        super(title);
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
    }

    @Override
    protected void init() {
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawPanel(graphics, left, top, panelWidth, panelHeight);
        renderContents(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    protected abstract void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    // ---------------------------------------------------------------- drawing helpers

    /** The window: parchment in a dark wooden frame. */
    protected static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, WINDOW, x, y, w, h);
    }

    /** A sunken leather area, used behind lists. */
    protected static void drawInset(GuiGraphics g, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, INSET, x, y, w, h);
    }

    protected static void drawSlot(GuiGraphics g, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 18, 18);
    }

    protected void text(GuiGraphics g, Component component, int x, int y, int color) {
        g.drawString(font, component, x, y, color, false);
    }

    protected void text(GuiGraphics g, String string, int x, int y, int color) {
        g.drawString(font, string, x, y, color, false);
    }

    /** Draws wrapped text and returns the y coordinate below it. */
    protected int wrapped(GuiGraphics g, Component component, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = font.split(component, maxWidth);
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    protected void centered(GuiGraphics g, Component component, int centerX, int y, int color) {
        g.drawString(font, component, centerX - font.width(component) / 2, y, color, false);
    }

    /** A labelled bar showing {@code value / max}. */
    protected void bar(GuiGraphics g, int x, int y, int w, int h, float value, float max, int color) {
        float fraction = max <= 0 ? 0 : Math.max(0, Math.min(1, value / max));
        g.fill(x, y, x + w, y + h, 0xFF2A1C12);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF5A4634);
        g.fill(x + 1, y + 1, x + 1 + Math.round((w - 2) * fraction), y + h - 1, color);
    }

    protected void item(GuiGraphics g, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            return;
        }
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            g.setTooltipForNextFrame(font, stack.getHoverName(), mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- scrollable list

    public interface RowRenderer {
        void render(GuiGraphics graphics, int index, int x, int y, int width);
    }

    /** Draws a clipped, scrollable list of rows inside a sunken box. */
    protected void renderList(GuiGraphics g, int x, int y, int w, int h, int rowHeight, int rows, RowRenderer renderer) {
        listX = x;
        listY = y;
        listWidth = w;
        listHeight = h;
        listRowHeight = rowHeight;
        listRows = rows;
        int visible = Math.max(1, (h - 4) / rowHeight);
        scroll = Math.max(0, Math.min(scroll, rows - visible));
        drawInset(g, x, y, w, h);
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        for (int i = scroll; i < Math.min(rows, scroll + visible + 1); i++) {
            renderer.render(g, i, x + 3, y + 3 + (i - scroll) * rowHeight, w - 10);
        }
        g.disableScissor();
        if (rows > visible) {
            int trackHeight = h - 2;
            int thumbHeight = Math.max(8, trackHeight * visible / rows);
            int thumbY = y + 1 + (trackHeight - thumbHeight) * scroll / Math.max(1, rows - visible);
            g.fill(x + w - 5, y + 1, x + w - 1, y + h - 1, 0xFF2A1C12);
            g.fill(x + w - 5, thumbY, x + w - 1, thumbY + thumbHeight, 0xFFC8A46A);
        }
    }

    protected void resetList() {
        listRows = 0;
        scroll = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (listRows > 0 && mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
            scroll -= (int) Math.signum(scrollY);
            int visible = Math.max(1, (listHeight - 4) / listRowHeight);
            scroll = Math.max(0, Math.min(scroll, listRows - visible));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
