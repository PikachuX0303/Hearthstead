package dev.pikachux.hearthstead.client.gui;

import dev.pikachux.hearthstead.blueprint.Styles;
import dev.pikachux.hearthstead.network.BuildingViewPayload;
import dev.pikachux.hearthstead.network.HutActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The hut window: overview, construction progress and, for the town hall,
 * the colony's citizens and settings.
 */
public class BuildingScreen extends ColonyScreen {
    private static final int REFRESH_TICKS = 40;

    private enum Tab {
        OVERVIEW("gui.hearthstead.tab.overview"),
        ORDERS("gui.hearthstead.tab.orders"),
        RECIPES("gui.hearthstead.tab.recipes"),
        CONSTRUCTION("gui.hearthstead.tab.construction"),
        REQUESTS("gui.hearthstead.tab.requests"),
        SETTINGS("gui.hearthstead.tab.settings"),
        RESEARCH("gui.hearthstead.tab.research"),
        CITIZENS("gui.hearthstead.tab.citizens"),
        COLONY("gui.hearthstead.tab.colony");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private BuildingViewPayload view;
    private Tab tab = Tab.OVERVIEW;
    /** Set after the first click on Deconstruct; the second click orders it. */
    private boolean confirmDemolish;
    /** What is typed into the postbox's order fields, kept across refreshes. */
    private String orderItemText = "";
    private String orderCountText = "16";
    private String recipeItemText = "";
    private String recipeStockText = "0";
    private Component orderMessage = Component.empty();
    private static final int ORDER_ROW = 20;
    private EditBox nameField;
    /** Research selected in the research tab, by id. */
    private String selectedResearch;
    private int refreshTimer;

    public BuildingScreen(BuildingViewPayload view) {
        super(buildingName(view.buildingType()), 292, 220);
        this.view = view;
    }

    public BlockPos pos() {
        return view.pos();
    }

    /** Called when the server sends fresh data while the screen is open. */
    public void update(BuildingViewPayload view) {
        this.view = view;
        if ((tab == Tab.ORDERS || tab == Tab.RECIPES) && getFocused() instanceof EditBox) {
            // Keep typing undisturbed; the order list is drawn from the new view anyway.
            return;
        }
        String typed = nameField == null ? null : nameField.getValue();
        boolean focused = nameField != null && nameField.isFocused();
        int oldScroll = scroll;
        rebuildWidgets();
        scroll = oldScroll;
        if (nameField != null && typed != null && focused) {
            nameField.setValue(typed);
            setFocused(nameField);
        }
    }

    private List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<>(List.of(Tab.OVERVIEW, Tab.CONSTRUCTION, Tab.REQUESTS));
        if (view.buildingType().equals(dev.pikachux.hearthstead.colony.BuildingType.POSTBOX.id())) {
            tabs.add(1, Tab.ORDERS);
        }
        if (dev.pikachux.hearthstead.colony.Crafters.TEACHABLE.contains(
                dev.pikachux.hearthstead.colony.BuildingType.byId(view.buildingType()))) {
            tabs.add(1, Tab.RECIPES);
        }
        if (!view.settings().isEmpty()) {
            tabs.add(Tab.SETTINGS);
        }
        if (!view.research().isEmpty()) {
            tabs.add(Tab.RESEARCH);
        }
        if (view.colony().present()) {
            tabs.add(Tab.CITIZENS);
            tabs.add(Tab.COLONY);
        }
        return tabs;
    }

    private void send(HutActionPayload.Action action, String argument) {
        ClientPlayNetworking.send(new HutActionPayload(view.pos(), action, argument));
    }

    @Override
    protected void init() {
        super.init();
        nameField = null;

        // Tabs share the width in proportion to their labels, so long names still fit.
        List<Tab> tabs = tabs();
        int available = panelWidth - 16 - 2 * (tabs.size() - 1);
        int[] widths = new int[tabs.size()];
        int labels = 0;
        for (int i = 0; i < tabs.size(); i++) {
            widths[i] = font.width(Component.translatable(tabs.get(i).key)) + 6;
            labels += widths[i];
        }
        int extra = Math.max(0, available - labels);
        int tabX = left + 8;
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            int width = widths[i] + extra / tabs.size() + (i < extra % tabs.size() ? 1 : 0);
            Button button = ColonyButton.themed(Component.translatable(t.key), b -> {
                tab = t;
                resetList();
                rebuildWidgets();
            }).bounds(tabX, top + 22, width, 18).build().selected(t == tab);
            button.active = t != tab;
            addRenderableWidget(button);
            tabX += width + 2;
        }

        int buttonY = top + panelHeight - 28;
        addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.inventory"),
                b -> send(HutActionPayload.Action.OPEN_INVENTORY, "")).bounds(left + 8, buttonY, 70, 20).build());

        Button order;
        if (view.hasOrder()) {
            order = ColonyButton.themed(Component.translatable("gui.hearthstead.cancel_order"),
                    b -> send(HutActionPayload.Action.CANCEL_ORDER, "")).bounds(left + 82, buttonY, 96, 20).build();
        } else {
            Component label = view.level() == 0 ? Component.translatable("gui.hearthstead.build")
                    : Component.translatable("gui.hearthstead.upgrade", view.level() + 1);
            order = ColonyButton.themed(label, b -> send(HutActionPayload.Action.UPGRADE, ""))
                    .bounds(left + 82, buttonY, 96, 20).build();
            if (!view.upgradeBlocker().isEmpty()) {
                order.active = false;
                order.setTooltip(Tooltip.create(Component.translatable(view.upgradeBlocker())));
            }
        }
        addRenderableWidget(order);
        addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.close"), b -> onClose())
                .bounds(left + panelWidth - 78, buttonY, 70, 20).build());

        if (tab == Tab.RESEARCH) {
            BuildingViewPayload.ResearchEntry selected = selectedResearch();
            if (selected != null && selected.state() == BuildingViewPayload.ResearchEntry.State.AVAILABLE) {
                Button start = ColonyButton.themed(Component.translatable("gui.hearthstead.research.start"),
                        b -> send(HutActionPayload.Action.START_RESEARCH, selected.id()))
                        .bounds(left + panelWidth - 98, top + 46 + RESEARCH_LIST_HEIGHT + 4, 88, 18).build();
                if (!selected.blocker().isEmpty()) {
                    start.active = false;
                    start.setTooltip(Tooltip.create(Component.translatable(selected.blocker())));
                }
                addRenderableWidget(start);
            }
        }

        if (tab == Tab.SETTINGS) {
            for (int i = 0; i < view.settings().size(); i++) {
                BuildingViewPayload.SettingEntry setting = view.settings().get(i);
                addRenderableWidget(ColonyButton.themed(settingValue(setting),
                                b -> send(HutActionPayload.Action.CHANGE_SETTING, setting.id()))
                        .bounds(left + panelWidth - 98, top + 46 + i * SETTING_ROW, 88, 20).build());
            }
        }

        if (tab == Tab.OVERVIEW && view.buildingType().equals(dev.pikachux.hearthstead.colony.BuildingType.TAVERN.id())
                && view.level() > 0) {
            addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.expedition"),
                            b -> send(HutActionPayload.Action.SEND_EXPEDITION, ""))
                    .tooltip(Tooltip.create(Component.translatable("gui.hearthstead.expedition.tooltip",
                            dev.pikachux.hearthstead.colony.Colony.EXPEDITION_FOOD)))
                    .bounds(left + panelWidth - 118, top + panelHeight - 52, 110, 18).build());
        }

        if (tab == Tab.RECIPES) {
            EditBox item = new EditBox(font, left + 10, top + 60, 170, 16, Component.translatable("gui.hearthstead.recipes.item"));
            item.setMaxLength(64);
            item.setValue(recipeItemText);
            item.setHint(Component.translatable("gui.hearthstead.recipes.item"));
            item.setResponder(text -> recipeItemText = text);
            addRenderableWidget(item);
            EditBox stock = new EditBox(font, left + 184, top + 60, 36, 16, Component.translatable("gui.hearthstead.recipes.stock"));
            stock.setMaxLength(4);
            stock.setFilter(text -> text.chars().allMatch(Character::isDigit));
            stock.setValue(recipeStockText);
            stock.setResponder(text -> recipeStockText = text);
            stock.setTooltip(Tooltip.create(Component.translatable("gui.hearthstead.recipes.stock.tooltip")));
            addRenderableWidget(stock);
            addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.recipes.teach"), b -> teachRecipe())
                    .bounds(left + 224, top + 59, 58, 18).build());
            for (int i = 0; i < view.recipes().size(); i++) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(view.recipes().get(i).item()).toString();
                addRenderableWidget(ColonyButton.themed(Component.literal("✕"), b -> send(HutActionPayload.Action.FORGET_RECIPE, id))
                        .tooltip(Tooltip.create(Component.translatable("gui.hearthstead.recipes.forget")))
                        .bounds(entryX(i) + ENTRY_WIDTH - 20, entryY(i), 18, 18).build());
            }
        }

        if (tab == Tab.ORDERS) {
            EditBox item = new EditBox(font, left + 10, top + 60, 170, 16, Component.translatable("gui.hearthstead.postbox.item"));
            item.setMaxLength(64);
            item.setValue(orderItemText);
            item.setHint(Component.translatable("gui.hearthstead.postbox.item"));
            item.setResponder(text -> orderItemText = text);
            addRenderableWidget(item);
            EditBox count = new EditBox(font, left + 184, top + 60, 36, 16, Component.translatable("gui.hearthstead.postbox.count"));
            count.setMaxLength(4);
            count.setFilter(text -> text.chars().allMatch(Character::isDigit));
            count.setValue(orderCountText);
            count.setResponder(text -> orderCountText = text);
            addRenderableWidget(count);
            addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.postbox.order"), b -> placeOrder())
                    .bounds(left + 224, top + 59, 58, 18).build());
            for (int i = 0; i < view.postboxOrders().size(); i++) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(view.postboxOrders().get(i).item()).toString();
                addRenderableWidget(ColonyButton.themed(Component.literal("✕"), b -> send(HutActionPayload.Action.POSTBOX_CANCEL, id))
                        .tooltip(Tooltip.create(Component.translatable("gui.hearthstead.postbox.cancel")))
                        .bounds(entryX(i) + ENTRY_WIDTH - 20, entryY(i), 18, 18).build());
            }
        }

        if (tab == Tab.CONSTRUCTION && !view.hasOrder() && view.level() > 0) {
            int rowY = top + panelHeight - 52;
            addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.repair"),
                            b -> send(HutActionPayload.Action.REPAIR, ""))
                    .tooltip(Tooltip.create(Component.translatable("gui.hearthstead.repair.tooltip")))
                    .bounds(left + 82, rowY, 96, 18).build());
            if (!view.buildingType().equals(dev.pikachux.hearthstead.colony.BuildingType.TOWN_HALL.id())) {
                addRenderableWidget(ColonyButton.themed(Component.translatable(confirmDemolish ? "gui.hearthstead.demolish.confirm"
                                : "gui.hearthstead.demolish"), b -> {
                            if (confirmDemolish) {
                                confirmDemolish = false;
                                send(HutActionPayload.Action.DEMOLISH, "");
                            } else {
                                confirmDemolish = true;
                                rebuildWidgets();
                            }
                        })
                        .tooltip(Tooltip.create(Component.translatable("gui.hearthstead.demolish.tooltip")))
                        .bounds(left + 182, rowY, 120, 18).build());
            }
        }

        if (tab == Tab.COLONY) {
            addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.permissions.open"),
                            b -> send(HutActionPayload.Action.OPEN_PERMISSIONS, ""))
                    .bounds(left + panelWidth - 98, top + 88, 90, 18).build());
        }

        if (tab == Tab.COLONY && view.colony().isOwner()) {
            nameField = new EditBox(font, left + 10, top + 108, panelWidth - 92, 18, Component.translatable("gui.hearthstead.colony_name"));
            nameField.setMaxLength(32);
            nameField.setValue(view.colonyName());
            addRenderableWidget(nameField);
            addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.rename"),
                    b -> send(HutActionPayload.Action.RENAME_COLONY, nameField.getValue()))
                    .bounds(left + panelWidth - 78, top + 107, 70, 20).build());
            addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.change_style"),
                    b -> send(HutActionPayload.Action.CYCLE_STYLE, ""))
                    .bounds(left + panelWidth - 98, top + 68, 90, 20).build());
            addRenderableWidget(ColonyButton.themed(Component.translatable("gui.hearthstead.toggle_raids"),
                    b -> send(HutActionPayload.Action.TOGGLE_RAIDS, ""))
                    .bounds(left + panelWidth - 98, top + 46, 90, 20).build());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTimer >= REFRESH_TICKS) {
            refreshTimer = 0;
            send(HutActionPayload.Action.REFRESH, "");
        }
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        text(g, getTitle(), left + 8, top + 8, HEADER);
        Component level = Component.translatable("gui.hearthstead.level", view.level(), view.maxLevel());
        text(g, level, left + panelWidth - 8 - font.width(level), top + 8, TEXT);

        int x = left + 10;
        int y = top + 46;
        int w = panelWidth - 20;
        int listBottom = top + panelHeight - 34;
        switch (tab) {
            case OVERVIEW -> renderOverview(g, x, y, w, listBottom);
            case ORDERS -> renderOrders(g, x, y, w);
            case RECIPES -> renderRecipes(g, x, y, w);
            case CONSTRUCTION -> renderConstruction(g, x, y, w, listBottom, mouseX, mouseY);
            case REQUESTS -> renderRequests(g, x, y, w, listBottom, mouseX, mouseY);
            case SETTINGS -> renderSettings(g, x, y, w);
            case CITIZENS -> renderCitizens(g, x, y, w, listBottom);
            case COLONY -> renderColony(g, x, y, w, listBottom);
            case RESEARCH -> renderResearch(g, x, y, w, mouseX, mouseY);
        }
    }

    private void renderOverview(GuiGraphics g, int x, int y, int w, int bottom) {
        text(g, Component.translatable("gui.hearthstead.colony", view.colonyName()), x, y, TEXT);
        Component style = Component.translatable("gui.hearthstead.style", Styles.displayName(view.style()));
        text(g, style, x + w - font.width(style), y, TEXT_LIGHT);
        y += 12;
        if (view.level() == 0) {
            y = wrapped(g, Component.translatable("gui.hearthstead.not_built"), x, y, w, TEXT_LIGHT);
            if (view.needsBuilder()) {
                y = wrapped(g, Component.translatable("message.hearthstead.need_builder"), x, y, w, BAD);
            }
            y += 2;
        }
        if (view.hasOrder()) {
            Component order = orderText();
            text(g, order, x, y, view.orderAssigned() ? GOOD : TEXT_LIGHT);
            y += 12;
        }
        if (view.housing() > 0) {
            text(g, Component.translatable("gui.hearthstead.residents", view.residents(), view.housing()), x, y, TEXT);
            y += 12;
        }
        if (view.colony().present()) {
            BuildingViewPayload.ColonyInfo colony = view.colony();
            if (colony.raidersLeft() > 0) {
                text(g, Component.literal("⚔ ").append(Component.translatable(colony.pirates() ? "gui.hearthstead.raid_active_pirates"
                        : "gui.hearthstead.raid_active", colony.raidersLeft(), colony.raidSize())), x, y, BAD);
                y += 12;
            }
            if (colony.festival()) {
                text(g, Component.literal("✿ ").append(Component.translatable("gui.hearthstead.festival")), x, y, GOOD);
                y += 12;
            }
            text(g, Component.translatable("gui.hearthstead.citizen_count", colony.citizenCount(), colony.housing()), x, y, TEXT);
            y += 12;
            text(g, Component.translatable("gui.hearthstead.open_work_orders", colony.workOrders().size()), x, y, TEXT);
            y += 12;
            if (!colony.citizens().isEmpty()) {
                float happiness = (float) colony.citizens().stream().mapToDouble(BuildingViewPayload.Citizen::happiness).average().orElse(0);
                Component label = Component.translatable("gui.hearthstead.average_happiness", String.format("%.1f", happiness));
                text(g, label, x, y, TEXT);
                bar(g, x + font.width(label) + 6, y + 1, 80, 7, happiness, 10, CitizenScreen.happinessColor(happiness));
                y += 12;
            }
        }
        List<String> waiting = view.requests().stream()
                .filter(r -> r.target().equals(view.pos()))
                .map(r -> r.count() + "x " + r.description())
                .toList();
        if (!waiting.isEmpty()) {
            y = wrapped(g, Component.translatable("gui.hearthstead.waiting_for", String.join(", ", waiting)), x, y, w, BAD);
        }
        if (!view.builderJob().getString().isEmpty()) {
            y = wrapped(g, Component.translatable("gui.hearthstead.current_job", view.builderJob()), x, y, w, TEXT);
        }
        if (!view.workers().isEmpty()) {
            text(g, Component.translatable("gui.hearthstead.workers"), x, y + 2, HEADER);
            y += 14;
            int top = y;
            renderList(g, x, top, w, bottom - top, 22, view.workers().size(), (gr, i, rx, ry, rw) -> {
                BuildingViewPayload.Worker worker = view.workers().get(i);
                gr.drawString(font, Component.literal(worker.name() + " (").append(Component.translatable(worker.job())).append(")"),
                        rx, ry, 0xFFFFFFFF, true);
                gr.drawString(font, font.plainSubstrByWidth(worker.status(), rw), rx, ry + 10, 0xFFE0E0E0, false);
            });
        }
    }

    // ---------------------------------------------------------------- item lists (orders, recipes)

    /** Orders and recipes are listed in two columns of five rows. */
    private static final int ENTRY_WIDTH = 134;

    private int entryX(int i) {
        return left + 10 + (i % 2) * (ENTRY_WIDTH + 4);
    }

    private int entryY(int i) {
        return top + 86 + (i / 2) * ORDER_ROW;
    }

    /** One entry: the item, its name and a short count, next to its ✕ button. */
    private void entry(GuiGraphics g, int i, ItemStack stack, Component count, int countColor) {
        int x = entryX(i);
        int y = entryY(i);
        g.renderItem(stack, x, y + 1);
        int countX = x + ENTRY_WIDTH - 24 - font.width(count);
        g.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(), countX - x - 22), x + 18, y + 5, 0xFFFFFFFF, true);
        g.drawString(font, count, countX, y + 5, countColor, true);
    }

    // ---------------------------------------------------------------- recipes

    private void teachRecipe() {
        String text = recipeItemText.strip();
        net.minecraft.world.item.Item item = findItem(text);
        if (item == null) {
            orderMessage = Component.translatable("gui.hearthstead.postbox.unknown", text);
            return;
        }
        orderMessage = Component.empty();
        recipeItemText = "";
        send(HutActionPayload.Action.TEACH_RECIPE, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item) + "|"
                + (recipeStockText.isEmpty() ? "0" : recipeStockText));
    }

    private void renderRecipes(GuiGraphics g, int x, int y, int w) {
        Component title = Component.translatable("gui.hearthstead.recipes.title");
        text(g, title, x, y, HEADER);
        Component count = Component.translatable("gui.hearthstead.recipes.count", view.recipes().size(),
                dev.pikachux.hearthstead.colony.Crafters.recipeSlots(view.level()));
        text(g, count, x + w - font.width(count), y, TEXT_LIGHT);
        if (!orderMessage.getString().isEmpty()) {
            text(g, orderMessage, x, y + 32, BAD);
        }
        if (view.recipes().isEmpty()) {
            wrapped(g, Component.translatable("gui.hearthstead.recipes.none"), x, y + 44, w, TEXT_LIGHT);
            return;
        }
        for (int i = 0; i < view.recipes().size(); i++) {
            BuildingViewPayload.Cost recipe = view.recipes().get(i);
            Component keep = recipe.count() > 0
                    ? Component.literal(Math.min(recipe.have(), recipe.count()) + "/" + recipe.count())
                    : Component.translatable("gui.hearthstead.recipes.on_request");
            entry(g, i, new ItemStack(recipe.item()), keep, recipe.count() > 0 && recipe.have() < recipe.count() ? TEXT_LIGHT : GOOD);
        }
    }

    // ---------------------------------------------------------------- postbox

    /** Finds the typed item by id ("bread", "minecraft:bread") or by its name ("Bread") and orders it. */
    private void placeOrder() {
        String text = orderItemText.strip();
        int count;
        try {
            count = Integer.parseInt(orderCountText.isEmpty() ? "1" : orderCountText);
        } catch (NumberFormatException e) {
            count = 1;
        }
        net.minecraft.world.item.Item item = findItem(text);
        if (item == null) {
            orderMessage = Component.translatable("gui.hearthstead.postbox.unknown", text);
            return;
        }
        orderMessage = Component.empty();
        orderItemText = "";
        send(HutActionPayload.Action.POSTBOX_ORDER,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item) + "|" + Math.max(1, count));
    }

    @org.jetbrains.annotations.Nullable
    private static net.minecraft.world.item.Item findItem(String text) {
        if (text.isEmpty()) {
            return null;
        }
        String id = text.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(id);
        if (key != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key);
            return item == net.minecraft.world.item.Items.AIR ? null : item;
        }
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item != net.minecraft.world.item.Items.AIR && item.getDefaultInstance().getHoverName().getString().equalsIgnoreCase(text)) {
                return item;
            }
        }
        return null;
    }

    private void renderOrders(GuiGraphics g, int x, int y, int w) {
        text(g, Component.translatable("gui.hearthstead.postbox.title"), x, y, HEADER);
        if (!orderMessage.getString().isEmpty()) {
            text(g, orderMessage, x, y + 32, BAD);
        }
        if (view.postboxOrders().isEmpty()) {
            wrapped(g, Component.translatable("gui.hearthstead.postbox.none"), x, y + 44, w, TEXT_LIGHT);
            return;
        }
        for (int i = 0; i < view.postboxOrders().size(); i++) {
            BuildingViewPayload.Cost order = view.postboxOrders().get(i);
            entry(g, i, new ItemStack(order.item()), Component.literal(Math.min(order.have(), order.count()) + "/" + order.count()),
                    order.have() >= order.count() ? GOOD : TEXT_LIGHT);
        }
    }

    /** What the hut's work order is doing, for the overview and construction tabs. */
    private Component orderText() {
        String kind = switch (view.orderKind()) {
            case "repair" -> "repair";
            case "remove" -> "remove";
            default -> "order";
        };
        return Component.translatable("gui.hearthstead." + kind + (view.orderAssigned() ? "_in_progress" : "_waiting"), view.orderLevel());
    }

    private void renderConstruction(GuiGraphics g, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        if (!view.hasOrder()) {
            wrapped(g, Component.translatable("gui.hearthstead.no_construction"), x, y, w, TEXT_LIGHT);
            return;
        }
        text(g, orderText(), x, y, view.orderAssigned() ? GOOD : TEXT_LIGHT);
        y += 12;
        bar(g, x, y, w, 10, view.progressDone(), view.progressTotal(), 0xFF4CAF50);
        Component progress = Component.translatable("gui.hearthstead.progress", view.progressDone(), view.progressTotal());
        g.drawString(font, progress, x + w / 2 - font.width(progress) / 2, y + 1, 0xFFFFFFFF, true);
        y += 16;
        if (view.materials().isEmpty()) {
            wrapped(g, Component.translatable("gui.hearthstead.no_materials_needed"), x, y, w, TEXT_LIGHT);
            return;
        }
        text(g, Component.translatable("gui.hearthstead.materials"), x, y, HEADER);
        y += 11;
        int listTop = y;
        renderList(g, x, listTop, w, bottom - listTop, 18, view.materials().size(), (gr, i, rx, ry, rw) -> {
            BuildingViewPayload.Material material = view.materials().get(i);
            ItemStack stack = new ItemStack(material.item());
            gr.renderItem(stack, rx, ry);
            gr.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(), rw - 80), rx + 20, ry + 4, 0xFFFFFFFF, true);
            String count = Math.min(material.available(), material.needed()) + " / " + material.needed();
            int color = material.available() >= material.needed() ? 0xFF8BE38B : 0xFFFF8A80;
            gr.drawString(font, count, rx + rw - font.width(count), ry + 4, color, true);
        });
    }

    private void renderCitizens(GuiGraphics g, int x, int y, int w, int bottom) {
        BuildingViewPayload.ColonyInfo colony = view.colony();
        text(g, Component.translatable("gui.hearthstead.citizen_count", colony.citizenCount(), colony.housing()), x, y, TEXT);
        y += 12;
        int listTop = y;
        renderList(g, x, listTop, w, bottom - listTop, 22, colony.citizens().size(), (gr, i, rx, ry, rw) -> {
            BuildingViewPayload.Citizen citizen = colony.citizens().get(i);
            gr.drawString(font, citizen.name(), rx, ry, 0xFFFFFFFF, true);
            Component job = Component.translatable(citizen.job());
            gr.drawString(font, job, rx + rw - 54 - font.width(job), ry, 0xFFE0E0E0, false);
            bar(gr, rx + rw - 50, ry + 1, 50, 6, citizen.happiness(), 10, CitizenScreen.happinessColor(citizen.happiness()));
            gr.drawString(font, font.plainSubstrByWidth(citizen.status(), rw), rx, ry + 10, 0xFFD0D0D0, false);
        });
    }

    private void renderColony(GuiGraphics g, int x, int y, int w, int bottom) {
        cancelButtons.clear();
        BuildingViewPayload.ColonyInfo colony = view.colony();
        // Everything left of the buttons on the right; two short values share a line.
        int columnWidth = left + panelWidth - 98 - 6 - x;
        int half = columnWidth / 2;
        fitted(g, Component.translatable("gui.hearthstead.colony", view.colonyName()), x, y, columnWidth, TEXT);
        fitted(g, Component.translatable("gui.hearthstead.owner", colony.owner()), x, y + 10, columnWidth, TEXT);
        fitted(g, Component.translatable("gui.hearthstead.citizen_count", colony.citizenCount(), colony.housing()), x, y + 20,
                half - 4, TEXT);
        fitted(g, Component.translatable("gui.hearthstead.building_count", colony.buildingCount()), x + half, y + 20, half, TEXT);
        // The raids line is short and sits on the right; the style takes the rest of the line.
        Component raids = Component.translatable("gui.hearthstead.raids",
                Component.translatable(colony.raidsEnabled() ? "gui.hearthstead.raids_on" : "gui.hearthstead.raids_off"));
        int raidsWidth = Math.min(half, font.width(raids));
        fitted(g, raids, x + columnWidth - raidsWidth, y + 30, raidsWidth, colony.raidsEnabled() ? BAD : GOOD);
        fitted(g, Component.translatable("gui.hearthstead.default_style", Styles.displayName(colony.style())), x, y + 30,
                columnWidth - raidsWidth - 6, TEXT);
        Component decorations = colony.decorations() == 0
                ? Component.translatable("gui.hearthstead.decorations_none")
                : Component.translatable("gui.hearthstead.decorations", colony.intactDecorations(),
                        String.format("%.2f", colony.decorationHappiness()));
        fitted(g, decorations, x, y + 40, columnWidth, colony.decorations() == 0 ? TEXT_LIGHT : GOOD);
        if (colony.isOwner()) {
            text(g, Component.translatable("gui.hearthstead.colony_name"), x, y + 51, TEXT_LIGHT);
        }
        y += 86;
        text(g, Component.translatable("gui.hearthstead.work_orders"), x, y, HEADER);
        y += 11;
        if (colony.workOrders().isEmpty()) {
            text(g, Component.translatable("gui.hearthstead.no_work_orders"), x, y + 2, TEXT_LIGHT);
            return;
        }
        int listTop = y;
        renderList(g, x, listTop, w, bottom - listTop, 11, colony.workOrders().size(), (gr, i, rx, ry, rw) -> {
            BuildingViewPayload.WorkOrderEntry order = colony.workOrders().get(i);
            int textWidth = order.decoration().isPresent() ? rw - 10 : rw;
            gr.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(order.description(), textWidth)), rx, ry,
                    order.assigned() ? 0xFF8BE38B : 0xFFFFFFFF, true);
            if (order.decoration().isPresent() && ry + ROW_CANCEL <= bottom) {
                gr.drawString(font, "✕", rx + rw - 7, ry, 0xFFFF8A80, true);
                cancelButtons.add(new CancelButton(rx + rw - 8, ry - 1, order.decoration().get()));
            }
        });
    }

    private static final int ROW_CANCEL = 9;

    /** Where a decoration order's cancel mark was drawn this frame. */
    private record CancelButton(int x, int y, net.minecraft.core.BlockPos decoration) {
    }

    private final List<CancelButton> cancelButtons = new java.util.ArrayList<>();

    private boolean clickCancel(double mouseX, double mouseY) {
        for (CancelButton button : cancelButtons) {
            if (mouseX >= button.x() && mouseX < button.x() + 9 && mouseY >= button.y() && mouseY < button.y() + 10) {
                playClick();
                ClientPlayNetworking.send(new HutActionPayload(view.pos(), HutActionPayload.Action.CANCEL_DECORATION,
                        Long.toString(button.decoration().asLong())));
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- settings tab

    private static final int SETTING_ROW = 40;

    private static Component settingValue(BuildingViewPayload.SettingEntry setting) {
        return Component.translatableWithFallback("setting.hearthstead." + setting.id() + "." + setting.value(), setting.value());
    }

    private void renderSettings(GuiGraphics g, int x, int y, int w) {
        for (int i = 0; i < view.settings().size(); i++) {
            BuildingViewPayload.SettingEntry setting = view.settings().get(i);
            int rowY = y + i * SETTING_ROW;
            text(g, Component.translatable("setting.hearthstead." + setting.id()), x, rowY + 1, HEADER);
            wrapped(g, Component.translatable("setting.hearthstead." + setting.id() + ".desc"), x, rowY + 12, w - 96, TEXT_LIGHT);
        }
    }

    // ---------------------------------------------------------------- requests tab

    private void renderRequests(GuiGraphics g, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        renderRequestList(g, x, y, w, bottom, view.requests(), view.colony().present(), mouseX, mouseY);
    }

    /** Handles a click on a Give button in the requests list. */
    private boolean clickRequest(double mouseX, double mouseY) {
        BuildingViewPayload.RequestEntry request = requestGiveAt(mouseX, mouseY, left + 10, top + 46, panelWidth - 20,
                top + panelHeight - 34, view.requests());
        if (request == null) {
            return false;
        }
        playClick();
        send(HutActionPayload.Action.FULFILL_REQUEST, HutActionPayload.requestArgument(request.target(), request.description()));
        return true;
    }

    // ---------------------------------------------------------------- research tab

    private static final int RESEARCH_ROW = 12;
    private static final int RESEARCH_LIST_HEIGHT = 64;

    /** Rows of the research list: a branch header (null entry) followed by its research. */
    private List<BuildingViewPayload.ResearchEntry> researchRows() {
        List<BuildingViewPayload.ResearchEntry> rows = new ArrayList<>();
        int branch = -1;
        for (BuildingViewPayload.ResearchEntry entry : view.research()) {
            if (entry.branch() != branch) {
                branch = entry.branch();
                rows.add(null);
            }
            rows.add(entry);
        }
        return rows;
    }

    private BuildingViewPayload.ResearchEntry selectedResearch() {
        return view.research().stream().filter(r -> r.id().equals(selectedResearch)).findFirst().orElse(null);
    }

    private static String branchKey(int branch) {
        return "research.hearthstead.branch." + switch (branch) {
            case 0 -> "civilian";
            case 1 -> "technology";
            default -> "combat";
        };
    }

    private void renderResearch(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        List<BuildingViewPayload.ResearchEntry> rows = researchRows();
        renderList(g, x, y, w, RESEARCH_LIST_HEIGHT, RESEARCH_ROW, rows.size(), (gr, i, rx, ry, rw) -> {
            BuildingViewPayload.ResearchEntry entry = rows.get(i);
            if (entry == null) {
                int branch = rows.get(i + 1).branch();
                gr.drawString(font, Component.translatable(branchKey(branch)).withStyle(net.minecraft.ChatFormatting.BOLD),
                        rx, ry + 1, 0xFFFFE08A, true);
                return;
            }
            boolean selected = entry.id().equals(selectedResearch);
            if (selected) {
                gr.fill(rx - 2, ry - 1, rx + rw + 2, ry + RESEARCH_ROW - 1, 0x60FFFFFF);
            }
            int color = switch (entry.state()) {
                case DONE -> 0xFF8BE38B;
                case ACTIVE -> 0xFF8AD8FF;
                case AVAILABLE -> 0xFFFFFFFF;
                case LOCKED -> 0xFFB0B0B0;
            };
            String prefix = "  ".repeat(entry.tier() - 1) + (entry.state() == BuildingViewPayload.ResearchEntry.State.DONE ? "✔ " : "• ");
            gr.drawString(font, prefix + Component.translatable("research.hearthstead." + entry.id()).getString(), rx + 4, ry + 1, color, true);
            Component state = switch (entry.state()) {
                case DONE -> Component.translatable("gui.hearthstead.research.state.done");
                case ACTIVE -> Component.translatable("gui.hearthstead.research.state.active", Math.round(entry.progress() * 100));
                case AVAILABLE -> Component.translatable("gui.hearthstead.research.state.available");
                case LOCKED -> Component.translatable("gui.hearthstead.research.state.locked");
            };
            gr.drawString(font, state, rx + rw - font.width(state), ry + 1, color, true);
        });

        int detailY = y + RESEARCH_LIST_HEIGHT + 4;
        BuildingViewPayload.ResearchEntry selected = selectedResearch();
        if (selected == null) {
            BuildingViewPayload.ResearchEntry active = view.research().stream()
                    .filter(r -> r.state() == BuildingViewPayload.ResearchEntry.State.ACTIVE).findFirst().orElse(null);
            if (active != null) {
                text(g, Component.translatable("gui.hearthstead.research.active",
                        Component.translatable("research.hearthstead." + active.id())), x, detailY + 2, HEADER);
                bar(g, x, detailY + 14, w, 8, active.progress(), 1, 0xFF7E57C2);
            }
            wrapped(g, Component.translatable("gui.hearthstead.research.select"), x, detailY + 28, w, TEXT_LIGHT);
            return;
        }
        text(g, Component.translatable("research.hearthstead." + selected.id()), x, detailY + 2, HEADER);
        int textY = wrapped(g, Component.translatable("research.hearthstead." + selected.id() + ".desc"), x, detailY + 13, w, TEXT);
        if (selected.state() == BuildingViewPayload.ResearchEntry.State.ACTIVE) {
            bar(g, x, textY + 1, w, 8, selected.progress(), 1, 0xFF7E57C2);
            return;
        }
        if (selected.state() == BuildingViewPayload.ResearchEntry.State.DONE) {
            return;
        }
        if (selected.state() == BuildingViewPayload.ResearchEntry.State.LOCKED) {
            Component argument = selected.blocker().endsWith("needs_parent")
                    ? Component.translatable(selected.blockerArgument()) : Component.literal(selected.blockerArgument());
            text(g, Component.translatable(selected.blocker(), argument), x, textY + 1, BAD);
            textY += 11;
        }
        int costX = x;
        for (BuildingViewPayload.Cost cost : selected.cost()) {
            ItemStack stack = new ItemStack(cost.item());
            g.renderItem(stack, costX, textY + 1);
            if (mouseX >= costX && mouseX < costX + 16 && mouseY >= textY + 1 && mouseY < textY + 17) {
                g.setTooltipForNextFrame(font, stack.getHoverName(), mouseX, mouseY);
            }
            String count = Math.min(cost.have(), cost.count()) + "/" + cost.count();
            g.drawString(font, count, costX + 18, textY + 5, cost.have() >= cost.count() ? GOOD : BAD, false);
            costX += 22 + font.width(count) + 6;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (tab == Tab.REQUESTS && event.button() == 0 && clickRequest(event.x(), event.y())) {
            return true;
        }
        if (tab == Tab.COLONY && event.button() == 0 && clickCancel(event.x(), event.y())) {
            return true;
        }
        if (tab == Tab.RESEARCH) {
            int x = left + 10;
            int y = top + 46;
            int w = panelWidth - 20;
            if (event.x() >= x && event.x() < x + w - 6 && event.y() >= y + 3 && event.y() < y + RESEARCH_LIST_HEIGHT - 1) {
                int index = scroll + (int) ((event.y() - y - 3) / RESEARCH_ROW);
                List<BuildingViewPayload.ResearchEntry> rows = researchRows();
                if (index >= 0 && index < rows.size() && rows.get(index) != null) {
                    selectedResearch = rows.get(index).id();
                    int keep = scroll;
                    rebuildWidgets();
                    scroll = keep;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
