package dev.pikachux.hearthstead.client.gui;

import dev.pikachux.hearthstead.network.CitizenViewPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shows a citizen's needs, status, home, workplace and inventory.
 */
public class CitizenScreen extends ColonyScreen {
    private final CitizenViewPayload view;

    public CitizenScreen(CitizenViewPayload view) {
        super(Component.literal(view.name()), 230, 236);
        this.view = view;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.close"), b -> onClose())
                .bounds(left + panelWidth - 68, top + panelHeight - 26, 60, 20).build());
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        text(g, Component.literal(view.name()), left + 8, top + 8, HEADER);
        text(g, Component.translatable(view.job()), left + 8, top + 19, TEXT_LIGHT);

        // Model preview
        drawInset(g, left + 8, top + 32, 52, 72);
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(view.entityId());
        if (entity instanceof LivingEntity living) {
            // Hide the floating name tag in the preview; the name is already in the title.
            boolean nameVisible = living.isCustomNameVisible();
            living.setCustomNameVisible(false);
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 9, top + 33, left + 59, top + 103, 30, 0.0625F,
                        mouseX, mouseY, living);
            } finally {
                living.setCustomNameVisible(nameVisible);
            }
        }

        int x = left + 68;
        int y = top + 34;
        int barWidth = panelWidth - 76;
        text(g, Component.translatable("gui.hearthstead.health", Math.round(view.health()), Math.round(view.maxHealth())), x, y, TEXT);
        bar(g, x, y + 10, barWidth, 6, view.health(), view.maxHealth(), 0xFFD32F2F);
        y += 21;
        text(g, Component.translatable("gui.hearthstead.saturation", String.format("%.1f", view.saturation()),
                Math.round(view.maxSaturation())), x, y, TEXT);
        bar(g, x, y + 10, barWidth, 6, view.saturation(), view.maxSaturation(), 0xFFEF8F2B);
        y += 21;
        text(g, Component.translatable("gui.hearthstead.happiness", String.format("%.1f", view.happiness())), x, y, TEXT);
        bar(g, x, y + 10, barWidth, 6, view.happiness(), 10, happinessColor(view.happiness()));

        renderSkills(g, left + 8, top + 110, mouseX, mouseY);

        y = top + 136;
        y = wrapped(g, Component.translatable("gui.hearthstead.status", view.status()), left + 8, y, panelWidth - 16, TEXT);
        text(g, Component.translatable("gui.hearthstead.home",
                view.home().getString().isEmpty() ? Component.translatable("gui.hearthstead.homeless") : view.home()),
                left + 8, y + 1, TEXT);
        text(g, Component.translatable("gui.hearthstead.workplace",
                view.work().getString().isEmpty() ? Component.translatable("gui.hearthstead.none") : view.work()),
                left + 8, y + 12, TEXT);

        int slotY = top + panelHeight - 48;
        text(g, Component.translatable("gui.hearthstead.carrying"), left + 8, slotY - 10, TEXT_LIGHT);
        drawSlot(g, left + 8, slotY);
        item(g, view.heldItem(), left + 9, slotY + 1, mouseX, mouseY);
        for (int i = 0; i < view.inventory().size(); i++) {
            int sx = left + 30 + i * 18;
            drawSlot(g, sx, slotY);
            item(g, view.inventory().get(i), sx + 1, slotY + 1, mouseX, mouseY);
        }
    }

    private static final String[] SKILLS = {"strength", "dexterity", "agility", "knowledge", "endurance", "combat", "marksmanship"};

    /** Skills in two rows of four short labels; the job's skill is gold, full names on hover. */
    private void renderSkills(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int column = (panelWidth - 16) / 4;
        for (int i = 0; i < view.skills().size() && i < SKILLS.length; i++) {
            int cx = x + (i % 4) * column;
            int cy = y + (i / 4) * 11;
            Component label = Component.translatable("skill.hearthstead." + SKILLS[i] + ".short")
                    .append(" " + view.skills().get(i));
            text(g, label, cx, cy, i == view.primarySkill() ? HEADER : TEXT);
            if (mouseX >= cx && mouseX < cx + column - 2 && mouseY >= cy && mouseY < cy + 10) {
                g.setTooltipForNextFrame(font, Component.translatable("skill.hearthstead." + SKILLS[i] + ".desc",
                        Component.translatable("skill.hearthstead." + SKILLS[i]), view.skills().get(i)), mouseX, mouseY);
            }
        }
    }

    static int happinessColor(float happiness) {
        return happiness >= 7 ? 0xFF43A047 : happiness >= 4 ? 0xFFF9A825 : 0xFFE53935;
    }
}
