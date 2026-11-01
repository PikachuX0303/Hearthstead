package dev.pikachux.hearthstead.colony;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Guard classes and orders, chosen per guard building in its Settings tab and shared by every
 * guard working there.
 */
public final class Guards {
    private Guards() {
    }

    /** How a guard fights. */
    public enum Kind {
        /** Sword and shield: tough, fights up close. */
        KNIGHT,
        /** Bow and arrows: keeps its distance and shoots. */
        RANGER,
        /** Throws potions: harms enemies and heals hurt colonists. */
        DRUID;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String displayName() {
            return switch (this) {
                case KNIGHT -> "Knight";
                case RANGER -> "Ranger";
                case DRUID -> "Druid";
            };
        }
    }

    /** What a guard does when there is nothing to fight. */
    public enum Order {
        /** Walk around the guard building. */
        PATROL,
        /** Walk from hut to hut across the whole colony. */
        COLONY,
        /** Stay at the guard building. */
        HOLD,
        /** Stay with the nearest colony member (owner or officer) and defend them. */
        FOLLOW;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static boolean isGuardBuilding(@Nullable BuildingData building) {
        return building != null && (building.type() == BuildingType.GUARD_TOWER || building.type() == BuildingType.BARRACKS);
    }

    public static Kind kind(@Nullable BuildingData building) {
        return switch (HutSettings.get(building, HutSettings.GUARD_CLASS)) {
            case "ranger" -> Kind.RANGER;
            case "druid" -> Kind.DRUID;
            default -> Kind.KNIGHT;
        };
    }

    public static Order order(@Nullable BuildingData building) {
        return switch (HutSettings.get(building, HutSettings.GUARD_ORDER)) {
            case "colony" -> Order.COLONY;
            case "hold" -> Order.HOLD;
            case "follow" -> Order.FOLLOW;
            default -> Order.PATROL;
        };
    }

    /** The guard building a citizen works at, or null when they are not a guard. */
    @Nullable
    public static BuildingData post(@Nullable Colony colony, @Nullable CitizenData citizen) {
        if (colony == null || citizen == null || citizen.job() != JobType.GUARD || citizen.workPos() == null) {
            return null;
        }
        BuildingData building = colony.building(citizen.workPos());
        return isGuardBuilding(building) ? building : null;
    }

    public static Kind kind(@Nullable Colony colony, @Nullable CitizenData citizen) {
        return kind(post(colony, citizen));
    }

    public static Order order(@Nullable Colony colony, @Nullable CitizenData citizen) {
        return order(post(colony, citizen));
    }
}
