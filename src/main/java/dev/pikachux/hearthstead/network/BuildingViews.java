package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingInfo;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyRequests;
import dev.pikachux.hearthstead.colony.ColonyResearch;
import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.colony.WorkOrder;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the data shown by the client screens.
 */
public final class BuildingViews {
    private BuildingViews() {
    }

    /** Opens the hut screen for the player. */
    public static void sendBuilding(ServerPlayer player, Colony colony, BuildingData building) {
        ServerPlayNetworking.send(player, create(player, colony, building, false));
    }

    /** Refreshes the hut screen if the player still has it open. */
    public static void updateBuilding(ServerPlayer player, Colony colony, BuildingData building) {
        ServerPlayNetworking.send(player, create(player, colony, building, true));
    }

    private static BuildingViewPayload create(ServerPlayer player, Colony colony, BuildingData building, boolean updateOnly) {
        ServerLevel level = player.level();
        BuildingType type = building.type();

        long residents = colony.citizens().stream().filter(c -> building.pos().equals(c.homePos())).count();
        List<BuildingViewPayload.Worker> workers = colony.citizens().stream()
                .filter(c -> building.pos().equals(c.workPos()))
                .map(c -> new BuildingViewPayload.Worker(c.name(), c.job().displayName(), c.status()))
                .toList();

        WorkOrder order = colony.workOrder(building.pos());
        int done = 0;
        int total = 0;
        List<BuildingViewPayload.Material> materials = new ArrayList<>();
        if (order != null) {
            List<Blueprint.Placement> plan = Construction.plan(level, building, order.targetLevel());
            if (plan != null) {
                Construction.Progress progress = Construction.progress(level, plan);
                done = progress.done();
                total = progress.total();
                for (Map.Entry<Item, Integer> entry : progress.remaining().entrySet()) {
                    materials.add(new BuildingViewPayload.Material(entry.getKey(), entry.getValue(),
                            countInBuilderHuts(level, colony, entry.getKey())));
                }
            }
        }

        String blocker = "";
        if (order == null) {
            if (building.level() >= BuildingType.MAX_LEVEL) {
                blocker = "message.hearthstead.max_level";
            } else if (type != BuildingType.TOWN_HALL && building.level() + 1 > colony.townHallLevel()) {
                blocker = "message.hearthstead.townhall_too_low";
            } else if (Blueprints.get(level.getServer(), building, building.level() + 1) == null) {
                blocker = "message.hearthstead.no_blueprint";
            }
        }
        boolean needsBuilder = colony.buildings().stream().noneMatch(b -> b.type() == BuildingType.BUILDER);

        String builderJob = "";
        if (type == BuildingType.BUILDER) {
            builderJob = colony.workOrders().stream()
                    .filter(o -> building.pos().equals(o.builder()))
                    .findFirst()
                    .map(o -> BuildingInfo.describe(colony, o))
                    .orElse("");
        }

        // The town hall shows every open request in the colony, other huts their own.
        List<BuildingViewPayload.RequestEntry> requests = requests(player, level, colony,
                type == BuildingType.TOWN_HALL ? null : building);

        BuildingViewPayload.ColonyInfo info = BuildingViewPayload.ColonyInfo.NONE;
        if (type == BuildingType.TOWN_HALL) {
            List<BuildingViewPayload.Citizen> citizens = colony.citizens().stream()
                    .map(c -> new BuildingViewPayload.Citizen(c.id(), c.name(), c.job().displayName(),
                            (float) c.happiness(), (float) c.saturation(), c.status()))
                    .toList();
            List<BuildingViewPayload.WorkOrderEntry> orders = colony.workOrders().stream()
                    .map(o -> new BuildingViewPayload.WorkOrderEntry(BuildingInfo.describe(colony, o), o.builder() != null))
                    .toList();
            info = new BuildingViewPayload.ColonyInfo(true, colony.ownerName(), colony.citizens().size(),
                    colony.housingCapacity(), colony.buildings().size(), colony.owner().equals(player.getUUID()),
                    citizens, orders, colony.raid().enabled(), colony.raid().remaining(), colony.raid().total(), colony.style(),
                    colony.raid().culture() == dev.pikachux.hearthstead.colony.ColonyRaid.Culture.PIRATES,
                    colony.happenings().festival(level.getGameTime()));
        }

        return new BuildingViewPayload(building.pos(), type.id(), building.level(), BuildingType.MAX_LEVEL, colony.name(),
                (int) residents, colony.housing(building), workers, order != null,
                order == null ? 0 : order.targetLevel(), order != null && order.builder() != null, done, total,
                materials, blocker, needsBuilder, builderJob, building.style(), info, requests,
                type == BuildingType.UNIVERSITY ? research(level, colony, building) : List.of(),
                HutSettings.of(type).stream()
                        .map(s -> new BuildingViewPayload.SettingEntry(s.id(), HutSettings.get(building, s)))
                        .toList(),
                updateOnly);
    }

    private static List<BuildingViewPayload.ResearchEntry> research(ServerLevel level, Colony colony, BuildingData university) {
        ColonyResearch research = colony.research();
        var storage = Storage.containers(level, university);
        List<BuildingViewPayload.ResearchEntry> entries = new ArrayList<>();
        for (Research node : Research.values()) {
            List<BuildingViewPayload.Cost> cost = node.cost().stream()
                    .map(c -> new BuildingViewPayload.Cost(c.getItem(), c.getCount(), Storage.count(storage, s -> s.is(c.getItem()))))
                    .toList();
            BuildingViewPayload.ResearchEntry.State state;
            String blocker = "";
            String argument = "";
            if (research.has(node)) {
                state = BuildingViewPayload.ResearchEntry.State.DONE;
            } else if (research.active() == node) {
                state = BuildingViewPayload.ResearchEntry.State.ACTIVE;
            } else if (node.parent() != null && !research.has(node.parent())) {
                state = BuildingViewPayload.ResearchEntry.State.LOCKED;
                blocker = "gui.hearthstead.research.needs_parent";
                argument = node.parent().nameKey();
            } else if (ColonyResearch.universityLevel(colony) < node.tier()) {
                state = BuildingViewPayload.ResearchEntry.State.LOCKED;
                blocker = "gui.hearthstead.research.needs_university";
                argument = String.valueOf(node.tier());
            } else {
                state = BuildingViewPayload.ResearchEntry.State.AVAILABLE;
                if (research.active() != null) {
                    blocker = "gui.hearthstead.research.busy";
                } else if (cost.stream().anyMatch(c -> c.have() < c.count())) {
                    blocker = "gui.hearthstead.research.missing_items";
                }
            }
            float progress = state == BuildingViewPayload.ResearchEntry.State.ACTIVE
                    ? (float) (research.progress() / node.points()) : 0;
            entries.add(new BuildingViewPayload.ResearchEntry(node.id(), node.branch().ordinal(), node.tier(), state,
                    progress, cost, blocker, argument));
        }
        return entries;
    }

    private static int countInBuilderHuts(ServerLevel level, Colony colony, Item item) {
        int count = 0;
        for (BuildingData building : colony.buildings()) {
            if (building.type() == BuildingType.BUILDER && level.getBlockEntity(building.pos()) instanceof HutBlockEntity hut) {
                for (int i = 0; i < hut.getContainerSize(); i++) {
                    ItemStack stack = hut.getItem(i);
                    if (stack.is(item)) {
                        count += stack.getCount();
                    }
                }
            }
        }
        return count;
    }

    /** Open requests of one building (or the whole colony when {@code only} is null), as seen by the player. */
    static List<BuildingViewPayload.RequestEntry> requests(ServerPlayer player, ServerLevel level, Colony colony,
                                                           @org.jetbrains.annotations.Nullable BuildingData only) {
        var playerItems = player.getInventory().getNonEquipmentItems();
        return ColonyRequests.list(level, colony, only).stream()
                .map(e -> new BuildingViewPayload.RequestEntry(e.building().pos(), e.building().type().id(), e.icon(),
                        e.request().count(), e.request().description(), e.status(),
                        playerItems.stream().filter(s -> !s.isEmpty() && e.request().matcher().test(s))
                                .mapToInt(ItemStack::getCount).sum()))
                .toList();
    }

    /** Opens (or refreshes) the clipboard's list of the colony's requests. */
    public static void sendRequests(ServerPlayer player, Colony colony, boolean updateOnly) {
        ServerPlayNetworking.send(player, new RequestsViewPayload(colony.name(),
                requests(player, player.level(), colony, null), updateOnly));
    }

    public static void sendCitizen(ServerPlayer player, CitizenEntity entity, CitizenData data) {
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < entity.inventory().getContainerSize(); i++) {
            inventory.add(entity.inventory().getItem(i).copy());
        }
        ServerPlayNetworking.send(player, new CitizenViewPayload(entity.getId(), data.name(), data.job().displayName(),
                entity.getHealth(), entity.getMaxHealth(), (float) data.saturation(), (float) CitizenData.MAX_SATURATION,
                (float) data.happiness(), data.status(), describe(entity.colony(), data.homePos()),
                describe(entity.colony(), data.workPos()), entity.getMainHandItem().copy(), inventory));
    }

    private static String describe(Colony colony, BlockPos pos) {
        if (colony == null || pos == null) {
            return "";
        }
        BuildingData building = colony.building(pos);
        return (building == null ? "?" : building.type().displayName()) + " (" + pos.toShortString() + ")";
    }
}
