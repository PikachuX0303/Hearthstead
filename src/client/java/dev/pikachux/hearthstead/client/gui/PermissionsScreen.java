package dev.pikachux.hearthstead.client.gui;

import dev.pikachux.hearthstead.network.PermissionActionPayload;
import dev.pikachux.hearthstead.network.PermissionsViewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The colony's permissions, opened from the town hall: members with their ranks on the left,
 * and on the right what friends and visitors may do. Only the owner can change anything.
 */
public class PermissionsScreen extends ColonyScreen {
    /** The actions in display order, matching ColonyPermissions.Action ids. */
    private static final List<String> ACTIONS = List.of("break", "build", "use_blocks", "containers", "hurt", "hut_windows");
    private static final int ROW = 11;
    private static final int RULE_ROW = 22;
    private static final int LIST_WIDTH = 120;
    private static final int TOGGLE_WIDTH = 36;

    private PermissionsViewPayload view;
    private EditBox nameField;

    public PermissionsScreen(PermissionsViewPayload view) {
        super(Component.translatable("gui.hearthstead.permissions.title"), 292, 220);
        this.view = view;
    }

    public void update(PermissionsViewPayload view) {
        this.view = view;
        String typed = nameField == null ? "" : nameField.getValue();
        rebuildWidgets();
        if (nameField != null) {
            nameField.setValue(typed);
        }
    }

    private void send(PermissionActionPayload.Action action, String first, String second) {
        ClientPlayNetworking.send(new PermissionActionPayload(action, first, second));
    }

    private int listTop() {
        return top + 34;
    }

    private int listBottom() {
        return top + 136;
    }

    @Override
    protected void init() {
        super.init();
        nameField = null;
        if (view.canEdit()) {
            nameField = new EditBox(font, left + 10, top + 142, LIST_WIDTH, 16, Component.translatable("gui.hearthstead.permissions.player_name"));
            nameField.setMaxLength(16);
            nameField.setHint(Component.translatable("gui.hearthstead.permissions.player_name"));
            addRenderableWidget(nameField);
            addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.permissions.add_friend"),
                    b -> send(PermissionActionPayload.Action.ADD_PLAYER, nameField.getValue(), "friend"))
                    .bounds(left + 10, top + 162, 58, 18).build());
            addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.permissions.add_officer"),
                    b -> send(PermissionActionPayload.Action.ADD_PLAYER, nameField.getValue(), "officer"))
                    .bounds(left + 72, top + 162, 58, 18).build());
        }
        for (int i = 0; i < ACTIONS.size(); i++) {
            String action = ACTIONS.get(i);
            int y = top + 40 + i * RULE_ROW;
            addRenderableWidget(toggle("friend", view.friendRules(), action, friendColumn(), y));
            addRenderableWidget(toggle("visitor", view.visitorRules(), action, visitorColumn(), y));
        }
        addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.close"), b -> onClose())
                .bounds(left + panelWidth - 78, top + panelHeight - 28, 70, 20).build());
    }

    private int visitorColumn() {
        return left + panelWidth - 8 - TOGGLE_WIDTH;
    }

    private int friendColumn() {
        return visitorColumn() - 6 - TOGGLE_WIDTH;
    }

    private Button toggle(String rank, List<String> rules, String action, int x, int y) {
        boolean allowed = rules.contains(action);
        Button button = ColonyButton.themed(Component.translatable(allowed ? "gui.hearthstead.permissions.yes" : "gui.hearthstead.permissions.no"),
                        b -> send(PermissionActionPayload.Action.TOGGLE, rank, action))
                .bounds(x, y, TOGGLE_WIDTH, 16).build();
        button.active = view.canEdit();
        button.setTooltip(Tooltip.create(Component.translatable("gui.hearthstead.permissions.toggle_tooltip",
                Component.translatable("gui.hearthstead.permissions.rank." + rank),
                Component.translatable("gui.hearthstead.permissions.action." + action + ".desc"))));
        return button;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        text(g, getTitle(), left + 8, top + 8, HEADER);
        Component colony = Component.literal(view.colonyName());
        text(g, colony, left + panelWidth - 8 - font.width(colony), top + 8, TEXT_LIGHT);

        // Members
        text(g, Component.translatable("gui.hearthstead.permissions.members"), left + 10, top + 24, HEADER);
        List<PermissionsViewPayload.Member> members = view.members();
        renderList(g, left + 10, listTop(), LIST_WIDTH, listBottom() - listTop(), ROW, members.size(), (gr, i, rx, ry, rw) -> {
            PermissionsViewPayload.Member member = members.get(i);
            Component rank = Component.translatable("gui.hearthstead.permissions.rank." + member.rank());
            int nameWidth = rw - font.width(rank) - 6 - (editable(member) ? 20 : 0);
            gr.drawString(font, font.plainSubstrByWidth(member.name(), nameWidth), rx, ry + 1, 0xFFFFFFFF, true);
            int rankX = rx + rw - font.width(rank) - (editable(member) ? 20 : 0);
            gr.drawString(font, rank, rankX, ry + 1, rankColor(member.rank()), true);
            if (editable(member)) {
                boolean hoverCycle = mouseX >= rx + rw - 18 && mouseX < rx + rw - 9 && mouseY >= ry && mouseY < ry + ROW;
                boolean hoverRemove = mouseX >= rx + rw - 8 && mouseX < rx + rw && mouseY >= ry && mouseY < ry + ROW;
                gr.drawString(font, "⇅", rx + rw - 17, ry + 1, hoverCycle ? 0xFFFFE08A : 0xFFC8A46A, true);
                gr.drawString(font, "✕", rx + rw - 7, ry + 1, hoverRemove ? 0xFFFF8A80 : 0xFFC8A46A, true);
                if (hoverCycle) {
                    gr.setTooltipForNextFrame(font, Component.translatable(member.rank().equals("officer")
                            ? "gui.hearthstead.permissions.make_friend" : "gui.hearthstead.permissions.make_officer"), mouseX, mouseY);
                } else if (hoverRemove) {
                    gr.setTooltipForNextFrame(font, Component.translatable("gui.hearthstead.permissions.remove"), mouseX, mouseY);
                }
            }
        });

        // Rules
        int x = left + LIST_WIDTH + 18;
        text(g, Component.translatable("gui.hearthstead.permissions.allowed"), x, top + 24, HEADER);
        Component friend = Component.translatable("gui.hearthstead.permissions.rank.friend");
        Component visitor = Component.translatable("gui.hearthstead.permissions.rank.visitor");
        int toggleX = friendColumn();
        fittedCentered(g, friend, friendColumn() + TOGGLE_WIDTH / 2, top + 30, TOGGLE_WIDTH + 4, TEXT_LIGHT, false);
        fittedCentered(g, visitor, visitorColumn() + TOGGLE_WIDTH / 2, top + 30, TOGGLE_WIDTH + 4, TEXT_LIGHT, false);
        for (int i = 0; i < ACTIONS.size(); i++) {
            int y = top + 40 + i * RULE_ROW;
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.translatable("gui.hearthstead.permissions.action." + ACTIONS.get(i)), toggleX - x - 4);
            int lineY = y + (lines.size() > 1 ? 0 : 4);
            for (net.minecraft.util.FormattedCharSequence line : lines.subList(0, Math.min(2, lines.size()))) {
                g.drawString(font, line, x, lineY, TEXT, false);
                lineY += 9;
            }
        }
        if (!view.canEdit()) {
            wrapped(g, Component.translatable("gui.hearthstead.permissions.owner_only"), left + 10, top + 142, LIST_WIDTH, TEXT_LIGHT);
        }
    }

    private boolean editable(PermissionsViewPayload.Member member) {
        return view.canEdit() && !member.rank().equals("owner");
    }

    private static int rankColor(String rank) {
        return switch (rank) {
            case "owner" -> 0xFFFFE08A;
            case "officer" -> 0xFF8AD8FF;
            default -> 0xFF8BE38B;
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        PermissionsViewPayload.Member member = memberAt(event.y());
        if (event.button() == 0 && member != null && editable(member)) {
            int rx = left + 10 + 3;
            int rw = LIST_WIDTH - 10;
            if (event.x() >= rx + rw - 18 && event.x() < rx + rw - 9) {
                playClick();
                send(PermissionActionPayload.Action.SET_RANK, member.uuid().toString(), member.rank().equals("officer") ? "friend" : "officer");
                return true;
            }
            if (event.x() >= rx + rw - 8 && event.x() < rx + rw) {
                playClick();
                send(PermissionActionPayload.Action.SET_RANK, member.uuid().toString(), "visitor");
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Nullable
    private PermissionsViewPayload.Member memberAt(double mouseY) {
        if (mouseY < listTop() + 3 || mouseY >= listBottom() - 1) {
            return null;
        }
        int index = scroll + (int) ((mouseY - listTop() - 3) / ROW);
        return index >= 0 && index < view.members().size() ? view.members().get(index) : null;
    }
}
