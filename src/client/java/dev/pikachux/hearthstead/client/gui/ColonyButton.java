package dev.pikachux.hearthstead.client.gui;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * A button in the mod's wooden theme. Tabs use the selected state, drawn as a
 * parchment tab that blends into the window.
 */
public class ColonyButton extends Button {
    private static final Identifier NORMAL = Hearthstead.id("button");
    private static final Identifier HIGHLIGHTED = Hearthstead.id("button_highlighted");
    private static final Identifier DISABLED = Hearthstead.id("button_disabled");
    private static final Identifier SELECTED = Hearthstead.id("tab_selected");

    private boolean selected;

    protected ColonyButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static Builder themed(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    /** Marks the button as the selected tab. */
    public ColonyButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Identifier sprite = selected ? SELECTED : !active ? DISABLED : isHoveredOrFocused() ? HIGHLIGHTED : NORMAL;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight());
        Font font = Minecraft.getInstance().font;
        Component message = getMessage();
        int color = selected ? 0xFF3B2A1A : active ? 0xFFF6E7C4 : 0xFFA89880;
        // Plain text: inactive buttons would otherwise get the vanilla grey style.
        String text = font.plainSubstrByWidth(message.getString(), getWidth() - 2);
        int x = getX() + (getWidth() - font.width(text)) / 2;
        int y = getY() + (getHeight() - 8) / 2;
        graphics.drawString(font, text, x, y, color, active && !selected);
    }

    /** Mirrors {@link Button.Builder} so screens can switch to the themed button easily. */
    public static class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        @Nullable
        private Tooltip tooltip;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder tooltip(@Nullable Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public ColonyButton build() {
            ColonyButton button = new ColonyButton(x, y, width, height, message, onPress);
            button.setTooltip(tooltip);
            return button;
        }
    }
}
