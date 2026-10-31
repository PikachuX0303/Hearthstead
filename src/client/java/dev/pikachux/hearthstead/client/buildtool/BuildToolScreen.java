package dev.pikachux.hearthstead.client.buildtool;

import dev.pikachux.hearthstead.blueprint.Styles;
import dev.pikachux.hearthstead.client.gui.ColonyButton;
import dev.pikachux.hearthstead.client.gui.ColonyScreen;
import dev.pikachux.hearthstead.network.BlueprintRequestPayload;
import dev.pikachux.hearthstead.network.HutActionPayload;
import dev.pikachux.hearthstead.network.PlaceHutPayload;
import dev.pikachux.hearthstead.registry.ModBlocks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/**
 * Controls for moving, rotating and confirming the build tool preview. The panel
 * sits at the bottom of the screen and leaves the world visible.
 */
public class BuildToolScreen extends ColonyScreen {
    private static final int BUTTON = 22;

    public BuildToolScreen() {
        super(Component.translatable("gui.hearthstead.build_tool.title"), 336, 126);
    }

    private static BuildPreview preview() {
        return BuildPreview.current();
    }

    @Override
    protected void init() {
        super.init();
        top = height - panelHeight - 6;
        BuildPreview preview = preview();
        if (preview == null) {
            return;
        }
        boolean movable = !preview.isUpgrade();
        int x = left + 8;
        int y = top + 40;
        add(Component.literal("⟲"), "gui.hearthstead.build_tool.rotate_left", x, y, movable, () -> preview.rotate(false));
        add(Component.literal("▲"), "gui.hearthstead.build_tool.forward", x + BUTTON + 2, y, movable, () -> move(0));
        add(Component.literal("⟳"), "gui.hearthstead.build_tool.rotate_right", x + 2 * (BUTTON + 2), y, movable, () -> preview.rotate(true));
        add(Component.literal("◀"), "gui.hearthstead.build_tool.left", x, y + BUTTON + 2, movable, () -> move(3));
        add(Component.literal("▼"), "gui.hearthstead.build_tool.back", x + BUTTON + 2, y + BUTTON + 2, movable, () -> move(2));
        add(Component.literal("▶"), "gui.hearthstead.build_tool.right", x + 2 * (BUTTON + 2), y + BUTTON + 2, movable, () -> move(1));
        add(Component.literal("⇧"), "gui.hearthstead.build_tool.up", x + 3 * (BUTTON + 2) + 6, y, movable, () -> preview.move(Direction.UP, 1));
        add(Component.literal("⇩"), "gui.hearthstead.build_tool.down", x + 3 * (BUTTON + 2) + 6, y + BUTTON + 2, movable,
                () -> preview.move(Direction.DOWN, 1));

        Component confirm = preview.isUpgrade() ? Component.translatable("gui.hearthstead.build_tool.order_upgrade")
                : Component.translatable("gui.hearthstead.build_tool.place");
        addRenderableWidget(ColonyButton.themed(confirm, b -> confirm()).bounds(left + panelWidth - 98, y, 90, 20).build());

        // Style switcher: asks the server for the same building in another style.
        int styleY = y + 2 * (BUTTON + 2) + 2;
        Button previous = ColonyButton.themed(Component.literal("◀"), b -> changeStyle(-1)).bounds(x, styleY, 20, 20).build();
        Button next = ColonyButton.themed(Component.literal("▶"), b -> changeStyle(1)).bounds(x + 168, styleY, 20, 20).build();
        previous.active = next.active = preview.styles().size() > 1;
        addRenderableWidget(previous);
        addRenderableWidget(next);
        addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.build_tool.cancel"), b -> cancel())
                .bounds(left + panelWidth - 98, y + BUTTON + 2, 90, 20).build());
    }

    private void add(Component label, String tooltipKey, int x, int y, boolean active, Runnable action) {
        Button button = ColonyButton.themed(label, b -> action.run()).bounds(x, y, BUTTON, BUTTON)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(tooltipKey))).build();
        button.active = active;
        addRenderableWidget(button);
    }

    /** Moves relative to where the player is looking: 0 forward, 1 right, 2 back, 3 left. */
    private void move(int quarterTurns) {
        BuildPreview preview = preview();
        if (preview == null || minecraft.player == null) {
            return;
        }
        Direction direction = minecraft.player.getDirection();
        for (int i = 0; i < quarterTurns; i++) {
            direction = direction.getClockWise();
        }
        preview.move(direction, 1);
    }

    private void changeStyle(int direction) {
        BuildPreview preview = preview();
        if (preview == null) {
            return;
        }
        String style = Styles.next(preview.styles(), preview.style(), direction);
        if (!preview.isUpgrade()) {
            PendingPlacement.set(preview.pos(), preview.facing());
        }
        ClientPlayNetworking.send(new BlueprintRequestPayload(preview.type().id(),
                Optional.ofNullable(preview.existingHut()), style));
    }

    private void confirm() {
        BuildPreview preview = preview();
        if (preview == null) {
            return;
        }
        if (preview.isUpgrade()) {
            ClientPlayNetworking.send(new HutActionPayload(preview.existingHut(), HutActionPayload.Action.UPGRADE, preview.style()));
        } else {
            ClientPlayNetworking.send(new PlaceHutPayload(preview.type().id(), preview.pos(), preview.facing(), preview.style()));
        }
        BuildPreview.set(null);
        onClose();
    }

    private void cancel() {
        BuildPreview.set(null);
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        BuildPreview preview = preview();
        if (preview != null && !preview.isUpgrade()) {
            switch (event.key()) {
                case GLFW.GLFW_KEY_UP -> move(0);
                case GLFW.GLFW_KEY_RIGHT -> move(1);
                case GLFW.GLFW_KEY_DOWN -> move(2);
                case GLFW.GLFW_KEY_LEFT -> move(3);
                case GLFW.GLFW_KEY_PAGE_UP -> preview.move(Direction.UP, 1);
                case GLFW.GLFW_KEY_PAGE_DOWN -> preview.move(Direction.DOWN, 1);
                case GLFW.GLFW_KEY_R -> preview.rotate(true);
                case GLFW.GLFW_KEY_ENTER -> confirm();
                default -> {
                    return super.keyPressed(event);
                }
            }
            return true;
        }
        if (preview != null && event.key() == GLFW.GLFW_KEY_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world visible behind the controls.
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        BuildPreview preview = preview();
        if (preview == null) {
            return;
        }
        Component name = Component.translatable(ModBlocks.HUTS.get(preview.type()).getDescriptionId());
        text(g, Component.translatable("gui.hearthstead.build_tool.previewing", name, preview.level()), left + 8, top + 8, HEADER);
        if (preview.isUpgrade()) {
            text(g, Component.translatable("gui.hearthstead.build_tool.hint_upgrade"), left + 8, top + 19, TEXT_LIGHT);
        } else {
            text(g, Component.translatable("gui.hearthstead.build_tool.hint"), left + 8, top + 19, TEXT_LIGHT);
            text(g, Component.translatable("gui.hearthstead.build_tool.hint2"), left + 8, top + 29, TEXT_LIGHT);
        }
        int styleY = top + 40 + 2 * (BUTTON + 2) + 2;
        centered(g, Component.translatable("gui.hearthstead.style", Styles.displayName(preview.style())), left + 8 + 94, styleY + 6, TEXT);
    }
}
