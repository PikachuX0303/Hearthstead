package dev.pikachux.hearthstead.client.gui;

import dev.pikachux.hearthstead.network.BuildingViewPayload;
import dev.pikachux.hearthstead.network.ClipboardActionPayload;
import dev.pikachux.hearthstead.network.HutActionPayload;
import dev.pikachux.hearthstead.network.RequestsViewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The clipboard: every open request of the player's colony, wherever the player is.
 */
public class ClipboardScreen extends ColonyScreen {
    private static final int REFRESH_TICKS = 40;

    private RequestsViewPayload view;
    private int refreshTimer;

    public ClipboardScreen(RequestsViewPayload view) {
        super(Component.translatable("gui.hearthstead.clipboard.title"), 260, 220);
        this.view = view;
    }

    /** Called when the server sends fresh data while the screen is open. */
    public void update(RequestsViewPayload view) {
        this.view = view;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.close"), b -> onClose())
                .bounds(left + panelWidth - 78, top + panelHeight - 28, 70, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTimer >= REFRESH_TICKS) {
            refreshTimer = 0;
            ClientPlayNetworking.send(new ClipboardActionPayload(ClipboardActionPayload.Action.REFRESH, ""));
        }
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        text(g, getTitle(), left + 8, top + 8, HEADER);
        Component colony = Component.literal(view.colonyName());
        text(g, colony, left + panelWidth - 8 - font.width(colony), top + 8, TEXT_LIGHT);
        renderRequestList(g, left + 10, top + 24, panelWidth - 20, top + panelHeight - 34, view.requests(), true, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            BuildingViewPayload.RequestEntry request = requestGiveAt(event.x(), event.y(), left + 10, top + 24, panelWidth - 20,
                    top + panelHeight - 34, view.requests());
            if (request != null) {
                playClick();
                ClientPlayNetworking.send(new ClipboardActionPayload(ClipboardActionPayload.Action.FULFILL_REQUEST,
                        HutActionPayload.requestArgument(request.target(), request.description())));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
