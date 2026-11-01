package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.blueprint.Blueprints;
import net.minecraft.server.level.ServerLevel;

/**
 * Requests to build or upgrade a hut.
 */
public final class BuildingInfo {
    private BuildingInfo() {
    }

    /** Queues an upgrade. Returns null on success or a translation key describing the problem. */
    public static String requestUpgrade(ServerLevel level, Colony colony, BuildingData building) {
        if (colony.workOrder(building.pos()) != null) {
            return "message.hearthstead.already_ordered";
        }
        int target = building.level() + 1;
        if (target > BuildingType.MAX_LEVEL) {
            return "message.hearthstead.max_level";
        }
        if (building.type() != BuildingType.TOWN_HALL && target > colony.townHallLevel()) {
            return "message.hearthstead.townhall_too_low";
        }
        if (Blueprints.get(level.getServer(), building, target) == null) {
            return "message.hearthstead.no_blueprint";
        }
        colony.addWorkOrder(new WorkOrder(building.pos(), target));
        return null;
    }

    /** Queues a repair of the hut's current level. Returns null on success or a translation key. */
    public static String requestRepair(ServerLevel level, Colony colony, BuildingData building) {
        if (colony.workOrder(building.pos()) != null) {
            return "message.hearthstead.already_ordered";
        }
        if (building.level() == 0) {
            return "message.hearthstead.nothing_to_repair";
        }
        java.util.List<dev.pikachux.hearthstead.blueprint.Blueprint.Placement> plan =
                dev.pikachux.hearthstead.blueprint.Construction.plan(level, building, building.level());
        if (plan == null) {
            return "message.hearthstead.no_blueprint";
        }
        var progress = dev.pikachux.hearthstead.blueprint.Construction.progress(level, plan);
        if (progress.done() >= progress.total()) {
            return "message.hearthstead.nothing_to_repair";
        }
        colony.addWorkOrder(WorkOrder.repair(building.pos(), building.level()));
        return null;
    }

    /** Queues taking the hut down. Returns null on success or a translation key. */
    public static String requestDemolish(ServerLevel level, Colony colony, BuildingData building) {
        if (building.type() == BuildingType.TOWN_HALL) {
            return "message.hearthstead.cannot_demolish_townhall";
        }
        WorkOrder existing = colony.workOrder(building.pos());
        if (existing != null) {
            if (existing.builder() != null) {
                return "message.hearthstead.already_ordered";
            }
            // Taking a hut down replaces an upgrade or repair nobody has started.
            colony.removeWorkOrder(existing);
        }
        colony.addWorkOrder(WorkOrder.removal(building.pos(), building.type(), building.style(), building.level(),
                Blueprints.facing(level, building.pos()), true));
        return null;
    }

    public static String describe(Colony colony, WorkOrder order) {
        if (order.isDecoration()) {
            return Decoration.displayName(order.decoration()) + " (decoration) at " + order.buildingPos().toShortString();
        }
        if (order.kind() == WorkOrder.Kind.REMOVE) {
            String name = order.hutType() == null ? "?" : order.hutType().displayName();
            return (order.demolish() ? "Take down " : "Clear old site of ") + name + " at " + order.buildingPos().toShortString();
        }
        if (order.kind() == WorkOrder.Kind.REPAIR) {
            BuildingData target = colony.building(order.buildingPos());
            return "Repair " + (target == null ? "?" : target.type().displayName()) + " at " + order.buildingPos().toShortString();
        }
        BuildingData target = colony.building(order.buildingPos());
        String name = target == null ? "?" : target.type().displayName();
        return name + " level " + order.targetLevel() + " at " + order.buildingPos().toShortString();
    }
}
