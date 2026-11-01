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

    public static String describe(Colony colony, WorkOrder order) {
        if (order.isDecoration()) {
            return Decoration.displayName(order.decoration()) + " (decoration) at " + order.buildingPos().toShortString();
        }
        BuildingData target = colony.building(order.buildingPos());
        String name = target == null ? "?" : target.type().displayName();
        return name + " level " + order.targetLevel() + " at " + order.buildingPos().toShortString();
    }
}
