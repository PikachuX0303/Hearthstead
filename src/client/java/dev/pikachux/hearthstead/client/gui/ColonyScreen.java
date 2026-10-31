package dev.pikachux.hearthstead.client.gui;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import dev.pikachux.hearthstead.colony.ColonyRequests;
import dev.pikachux.hearthstead.network.BuildingViewPayload;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

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

    // ---------------------------------------------------------------- requests

    private static final int REQUEST_ROW = 22;
    private static final int GIVE_WIDTH = 34;
    private static final Identifier BUTTON = Hearthstead.id("button");
    private static final Identifier BUTTON_HOVER = Hearthstead.id("button_highlighted");

    protected static Component buildingName(String type) {
        return Component.translatable("block.hearthstead.blockhut" + type.replace("_", ""));
    }

    /**
     * Draws a summary line at {@code y} and below it a scrollable list of requests with their status
     * and a Give button where the player carries matching items.
     */
    protected void renderRequestList(GuiGraphics g, int x, int y, int w, int bottom, List<BuildingViewPayload.RequestEntry> requests,
                                     boolean showBuilding, int mouseX, int mouseY) {
        if (requests.isEmpty()) {
            wrapped(g, Component.translatable("gui.hearthstead.requests.none"), x, y, w, TEXT_LIGHT);
            return;
        }
        long needYou = requests.stream().filter(r -> r.status() == ColonyRequests.Status.NEEDS_PLAYER).count();
        text(g, Component.translatable("gui.hearthstead.requests.summary", requests.size(), needYou), x, y, needYou > 0 ? BAD : TEXT);
        int listTop = y + 12;
        renderList(g, x, listTop, w, bottom - listTop, REQUEST_ROW, requests.size(), (gr, i, rx, ry, rw) -> {
            BuildingViewPayload.RequestEntry request = requests.get(i);
            ItemStack icon = new ItemStack(request.icon());
            gr.renderItem(icon, rx, ry + 2);
            int textWidth = rw - 22 - GIVE_WIDTH - 4;
            gr.drawString(font, font.plainSubstrByWidth(request.count() + "x " + request.description(), textWidth),
                    rx + 20, ry, 0xFFFFFFFF, true);
            Component status = Component.translatable("gui.hearthstead.requests.status." + request.status().name().toLowerCase(Locale.ROOT));
            int color = switch (request.status()) {
                case DELIVERING -> 0xFF8BE38B;
                case NEEDS_DELIVERYMAN -> 0xFFFFD27F;
                case NEEDS_PLAYER -> 0xFFFF8A80;
            };
            Component line = showBuilding ? buildingName(request.buildingType()).copy().append(" · ").append(status) : status;
            gr.drawString(font, font.plainSubstrByWidth(line.getString(), textWidth), rx + 20, ry + 10, color, false);
            if (request.playerHas() > 0) {
                int bx = rx + rw - GIVE_WIDTH;
                boolean hover = mouseX >= bx && mouseX < bx + GIVE_WIDTH && mouseY >= ry + 2 && mouseY < ry + 18;
                gr.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? BUTTON_HOVER : BUTTON, bx, ry + 2, GIVE_WIDTH, 16);
                Component give = Component.translatable("gui.hearthstead.requests.give");
                gr.drawString(font, give, bx + GIVE_WIDTH / 2 - font.width(give) / 2, ry + 6, 0xFFFFFFFF, true);
                if (hover) {
                    gr.setTooltipForNextFrame(font, Component.translatable("gui.hearthstead.requests.give.tooltip",
                            Math.min(request.playerHas(), request.count())), mouseX, mouseY);
                }
            }
            if (mouseX >= rx && mouseX < rx + 16 && mouseY >= ry + 2 && mouseY < ry + 18) {
                gr.setTooltipForNextFrame(font, icon.getHoverName(), mouseX, mouseY);
            }
        });
    }

    /** The request whose Give button is at the mouse, for a list drawn by {@link #renderRequestList}. */
    @Nullable
    protected BuildingViewPayload.RequestEntry requestGiveAt(double mouseX, double mouseY, int x, int y, int w, int bottom,
                                                            List<BuildingViewPayload.RequestEntry> requests) {
        int listTop = y + 12;
        if (mouseY < listTop + 3 || mouseY >= bottom - 1) {
            return null;
        }
        int index = scroll + (int) ((mouseY - listTop - 3) / REQUEST_ROW);
        if (index < 0 || index >= requests.size()) {
            return null;
        }
        int ry = listTop + 3 + (index - scroll) * REQUEST_ROW;
        int bx = x + 3 + (w - 10) - GIVE_WIDTH;
        BuildingViewPayload.RequestEntry request = requests.get(index);
        boolean onButton = mouseX >= bx && mouseX < bx + GIVE_WIDTH && mouseY >= ry + 2 && mouseY < ry + 18;
        return request.playerHas() > 0 && onButton ? request : null;
    }

    protected void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
