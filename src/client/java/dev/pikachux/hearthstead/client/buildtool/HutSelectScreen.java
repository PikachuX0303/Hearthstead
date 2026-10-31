package dev.pikachux.hearthstead.client.buildtool;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.client.gui.ColonyButton;
import dev.pikachux.hearthstead.client.gui.ColonyScreen;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.network.BlueprintRequestPayload;
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
 * (every hut in creative mode), each shown with its job sign.
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
    }

    private int rows() {
        return (available.size() + COLUMNS - 1) / COLUMNS;
    }

    private static Identifier sign(BuildingType type) {
        return Hearthstead.id("textures/block/hut_sign_" + type.blockName().substring("blockhut".length()) + ".png");
    }

    /** The hut under the mouse, or null. */
    private BuildingType hutAt(double mouseX, double mouseY) {
        int gridX = left + 10;
        int gridY = top + GRID_TOP;
        if (mouseX < gridX || mouseY < gridY || mouseY >= gridY + VISIBLE_ROWS * TILE_HEIGHT) {
            return null;
        }
        int column = (int) (mouseX - gridX) / TILE_WIDTH;
        int index = (row + (int) (mouseY - gridY) / TILE_HEIGHT) * COLUMNS + column;
        return column < COLUMNS && index < available.size() ? available.get(index) : null;
    }

    private void choose(BuildingType type) {
        PendingPlacement.set(target, facing);
        ClientPlayNetworking.send(new BlueprintRequestPayload(type.id(), Optional.empty(), ""));
        onClose();
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        text(g, getTitle(), left + 8, top + 8, HEADER);
        text(g, Component.translatable("gui.hearthstead.build_tool.choose"), left + 8, top + 19, TEXT_LIGHT);
        if (available.isEmpty()) {
            wrapped(g, Component.translatable("gui.hearthstead.build_tool.no_huts"), left + 12, top + 36, panelWidth - 24, BAD);
            return;
        }
        BuildingType hovered = hutAt(mouseX, mouseY);
        for (int i = row * COLUMNS; i < Math.min(available.size(), (row + VISIBLE_ROWS) * COLUMNS); i++) {
            BuildingType type = available.get(i);
            int x = left + 10 + (i % COLUMNS) * TILE_WIDTH;
            int y = top + GRID_TOP + (i / COLUMNS - row) * TILE_HEIGHT;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, type == hovered ? TILE_HOVER : TILE, x, y, TILE_WIDTH - 4, TILE_HEIGHT - 4);
            g.blit(RenderPipelines.GUI_TEXTURED, sign(type), x + (TILE_WIDTH - 4 - 32) / 2, y + 4, 0, 0, 32, 32, 32, 32);
            Component name = Component.translatable(ModBlocks.HUTS.get(type).getDescriptionId());
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
        BuildingType type = hutAt(event.x(), event.y());
        if (type != null && event.button() == 0) {
            choose(type);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
