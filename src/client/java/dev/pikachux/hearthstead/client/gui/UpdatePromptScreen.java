package dev.pikachux.hearthstead.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Asks once whether Hearthstead may update itself from GitHub releases.
 * Closing it with Escape leaves the question for the next start.
 */
public class UpdatePromptScreen extends ColonyScreen {
    private final @Nullable Screen parent;
    private final Consumer<Boolean> answer;

    public UpdatePromptScreen(@Nullable Screen parent, Consumer<Boolean> answer) {
        super(Component.translatable("gui.hearthstead.updates.title"), 260, 132);
        this.parent = parent;
        this.answer = answer;
    }

    @Override
    protected void init() {
        super.init();
        int buttonY = top + panelHeight - 28;
        addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.updates.enable"), b -> choose(true))
                .bounds(left + panelWidth / 2 - 104, buttonY, 100, 20).build());
        addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.updates.disable"), b -> choose(false))
                .bounds(left + panelWidth / 2 + 4, buttonY, 100, 20).build());
    }

    private void choose(boolean enabled) {
        answer.accept(enabled);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        centered(g, title, left + panelWidth / 2, top + 9, HEADER);
        int y = wrapped(g, Component.translatable("gui.hearthstead.updates.question"), left + 12, top + 26, panelWidth - 24, TEXT);
        wrapped(g, Component.translatable("gui.hearthstead.updates.change"), left + 12, y + 5, panelWidth - 24, TEXT_LIGHT);
    }
}
