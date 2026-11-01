package dev.pikachux.hearthstead.client.buildtool;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.client.gui.ColonyButton;
import dev.pikachux.hearthstead.client.gui.ColonyScreen;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.network.BlueprintRequestPayload;
import dev.pikachux.hearthstead.network.DecorationsPayload;
import dev.pikachux.hearthstead.registry.ModBlocks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * First step of the build tool: a scrollable grid of the huts in the player's inventory
 * (every hut in creative mode), each shown with its job sign, or of the decorations the
 * colony's builders can build, each shown with an item.
 */
public class HutSelectScreen extends ColonyScreen {
    private static final int COLUMNS = 4;
    private static final int TILE_WIDTH = 80;
    private static final int TILE_HEIGHT = 60;
    private static final int GRID_TOP = 32;
    private static final int VISIBLE_ROWS = 2;
    private static final Identifier TILE = Hearthstead.id("inset");
    private static final Identifier TILE_HOVER = Hearthstead.id("button_highlighted");

    private final BlockPos target;
    private final Direction facing;
    private final List<BuildingType> available = new ArrayList<>();
    /** Whether the decorations are shown instead of the huts; remembered between openings. */
    private static boolean decorationsTab;
    private int row;

    public HutSelectScreen(BlockPos target, Direction facing) {
        super(Component.translatable("gui.hearthstead.build_tool.title"), 20 + COLUMNS * TILE_WIDTH + 6,
                GRID_TOP + VISIBLE_ROWS * TILE_HEIGHT + 40);
        this.target = target;
        this.facing = facing;
    }

    @Override
    protected void init() {
        super.init();
        available.clear();
        for (BuildingType type : BuildingType.values()) {
            if (minecraft.player.getAbilities().instabuild
                    || minecraft.player.getInventory().contains(new ItemStack(ModBlocks.HUTS.get(type)))) {
                available.add(type);
            }
        }
        addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.close"), b -> onClose())
                .bounds(left + panelWidth - 78, top + panelHeight - 28, 70, 20).build());
        var huts = ColonyButton.themed(Component.translatable("gui.hearthstead.build_tool.huts"), b -> switchTab(false))
                .bounds(left + panelWidth - 172, top + 4, 76, 14).build();
        var decorations = ColonyButton.themed(Component.translatable("gui.hearthstead.build_tool.decorations"), b -> switchTab(true))
                .bounds(left + panelWidth - 92, top + 4, 84, 14).build();
        huts.active = decorationsTab;
        decorations.active = !decorationsTab;
        addRenderableWidget(huts);
        addRenderableWidget(decorations);
    }

    private void switchTab(boolean decorations) {
        decorationsTab = decorations;
        row = 0;
        rebuildWidgets();
    }

    /** Opens the screen on the decorations (true) or the huts (false). */
    public static void showDecorations(boolean decorations) {
        decorationsTab = decorations;
    }

    private int entries() {
        return decorationsTab ? ClientDecorations.all().size() : available.size();
    }

    private int rows() {
        return (entries() + COLUMNS - 1) / COLUMNS;
    }

    private static Identifier sign(BuildingType type) {
        return Hearthstead.id("textures/block/hut_sign_" + type.blockName().substring("blockhut".length()) + ".png");
    }

    /** Index of the tile under the mouse, or -1. */
    private int indexAt(double mouseX, double mouseY) {
        int gridX = left + 10;
        int gridY = top + GRID_TOP;
        if (mouseX < gridX || mouseY < gridY || mouseY >= gridY + VISIBLE_ROWS * TILE_HEIGHT) {
            return -1;
        }
        int column = (int) (mouseX - gridX) / TILE_WIDTH;
        int index = (row + (int) (mouseY - gridY) / TILE_HEIGHT) * COLUMNS + column;
        return column < COLUMNS && index < entries() ? index : -1;
    }

    private void chooseDecoration(String name) {
        PendingPlacement.set(target, facing);
        ClientPlayNetworking.send(BlueprintRequestPayload.decoration(name, ""));
        onClose();
    }

    private void choose(BuildingType type) {
        PendingPlacement.set(target, facing);
        ClientPlayNetworking.send(new BlueprintRequestPayload(type.id(), Optional.empty(), ""));
        onClose();
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        text(g, getTitle(), left + 8, top + 8, HEADER);
        text(g, Component.translatable(decorationsTab ? "gui.hearthstead.build_tool.choose_decoration"
                : "gui.hearthstead.build_tool.choose"), left + 8, top + 19, TEXT_LIGHT);
        if (entries() == 0) {
            wrapped(g, Component.translatable(decorationsTab ? "gui.hearthstead.build_tool.no_decorations"
                    : "gui.hearthstead.build_tool.no_huts"), left + 12, top + 36, panelWidth - 24, BAD);
            return;
        }
        int hovered = indexAt(mouseX, mouseY);
        for (int i = row * COLUMNS; i < Math.min(entries(), (row + VISIBLE_ROWS) * COLUMNS); i++) {
            int x = left + 10 + (i % COLUMNS) * TILE_WIDTH;
            int y = top + GRID_TOP + (i / COLUMNS - row) * TILE_HEIGHT;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, i == hovered ? TILE_HOVER : TILE, x, y, TILE_WIDTH - 4, TILE_HEIGHT - 4);
            Component name;
            if (decorationsTab) {
                DecorationsPayload.Entry decoration = ClientDecorations.all().get(i);
                g.pose().pushMatrix();
                g.pose().translate(x + (TILE_WIDTH - 4 - 32) / 2F, y + 4);
                g.pose().scale(2F, 2F);
                g.renderItem(new ItemStack(decoration.icon()), 0, 0);
                g.pose().popMatrix();
                name = ClientDecorations.displayName(decoration.name());
            } else {
                BuildingType type = available.get(i);
                g.blit(RenderPipelines.GUI_TEXTURED, sign(type), x + (TILE_WIDTH - 4 - 32) / 2, y + 4, 0, 0, 32, 32, 32, 32);
                name = Component.translatable(ModBlocks.HUTS.get(type).getDescriptionId());
            }
            List<FormattedCharSequence> lines = font.split(name, TILE_WIDTH - 8);
            for (int line = 0; line < Math.min(2, lines.size()); line++) {
                FormattedCharSequence text = lines.get(line);
                g.drawString(font, text, x + (TILE_WIDTH - 4 - font.width(text)) / 2, y + 38 + line * 9, 0xFFF6E7C4, true);
            }
        }
        // Scroll bar
        int rows = rows();
        if (rows > VISIBLE_ROWS) {
            int trackX = left + panelWidth - 14;
            int trackY = top + GRID_TOP;
            int trackHeight = VISIBLE_ROWS * TILE_HEIGHT - 4;
            int thumbHeight = Math.max(10, trackHeight * VISIBLE_ROWS / rows);
            int thumbY = trackY + (trackHeight - thumbHeight) * row / (rows - VISIBLE_ROWS);
            g.fill(trackX, trackY, trackX + 4, trackY + trackHeight, 0xFF2A1C12);
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, 0xFFC8A46A);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        row = Math.max(0, Math.min(Math.max(0, rows() - VISIBLE_ROWS), row - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = indexAt(event.x(), event.y());
        if (index >= 0 && event.button() == 0) {
            playClick();
            if (decorationsTab) {
                chooseDecoration(ClientDecorations.all().get(index).name());
            } else {
                choose(available.get(index));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
