package dev.pikachux.hearthstead.colony;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Per-hut settings shown in the hut window's Settings tab. Each setting cycles through a fixed
 * list of values; the first one is the default. Labels are translated from
 * {@code setting.hearthstead.<id>} and {@code setting.hearthstead.<id>.<value>}.
 */
public final class HutSettings {
    public record Setting(String id, List<String> values) {
        public String defaultValue() {
            return values.getFirst();
        }
    }

    public static final Setting CROP = new Setting("crop", List.of("any", "wheat", "carrots", "potatoes", "beetroot"));
    public static final Setting REPLANT = new Setting("replant", List.of("on", "off"));
    public static final Setting TORCHES = new Setting("torches", List.of("on", "off"));
    public static final Setting SMELT = new Setting("smelt", List.of("all", "ores", "sand"));
    public static final Setting COOK = new Setting("cook", List.of("all", "meat", "fish", "vegetables"));
    public static final Setting HERD_SIZE = new Setting("herd_size", List.of("auto", "2", "4", "6", "8", "12", "16", "24"));
    public static final Setting BREED = new Setting("breed", List.of("on", "off"));

    private HutSettings() {
    }

    /** The settings a building of this type has, in display order. */
    public static List<Setting> of(BuildingType type) {
        return switch (type) {
            case FARMER -> List.of(CROP);
            case LUMBERJACK -> List.of(REPLANT);
            case MINER -> List.of(TORCHES);
            case SMELTER -> List.of(SMELT);
            case COOK -> List.of(COOK);
            case SHEPHERD, COWHAND, CHICKEN_HERDER, SWINEHERD, RABBIT_HERDER -> List.of(HERD_SIZE, BREED);
            default -> List.of();
        };
    }

    @Nullable
    public static Setting byId(BuildingType type, String id) {
        return of(type).stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }

    /** The building's value for the setting, or its default. */
    public static String get(@Nullable BuildingData building, Setting setting) {
        if (building == null) {
            return setting.defaultValue();
        }
        String value = building.setting(setting.id());
        return value != null && setting.values().contains(value) ? value : setting.defaultValue();
    }

    public static boolean isOn(@Nullable BuildingData building, Setting setting) {
        return "on".equals(get(building, setting));
    }

    /** Moves the setting to its next value and returns it. */
    public static String cycle(BuildingData building, Setting setting) {
        List<String> values = setting.values();
        String next = values.get((values.indexOf(get(building, setting)) + 1) % values.size());
        building.setSetting(setting.id(), next.equals(setting.defaultValue()) ? null : next);
        return next;
    }

    // ---------------------------------------------------------------- what the settings mean

    /** Seeds the farmer plants. */
    public static Predicate<ItemStack> farmerSeeds(@Nullable BuildingData building) {
        return switch (get(building, CROP)) {
            case "wheat" -> s -> s.is(Items.WHEAT_SEEDS);
            case "carrots" -> s -> s.is(Items.CARROT);
            case "potatoes" -> s -> s.is(Items.POTATO);
            case "beetroot" -> s -> s.is(Items.BEETROOT_SEEDS);
            default -> s -> s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS);
        };
    }

    public static String farmerSeedsName(@Nullable BuildingData building) {
        return switch (get(building, CROP)) {
            case "wheat" -> "Wheat Seeds";
            case "carrots" -> "Carrots";
            case "potatoes" -> "Potatoes";
            case "beetroot" -> "Beetroot Seeds";
            default -> "Seeds";
        };
    }

    /** What the smelter puts in its furnaces. */
    public static Predicate<ItemStack> smelterInput(@Nullable BuildingData building) {
        return switch (get(building, SMELT)) {
            case "ores" -> s -> Logistics.isSmeltable(s) && !Logistics.isSand(s);
            case "sand" -> Logistics::isSand;
            default -> Logistics::isSmeltable;
        };
    }

    public static String smelterInputName(@Nullable BuildingData building) {
        return switch (get(building, SMELT)) {
            case "ores" -> "Ore or raw metal";
            case "sand" -> "Sand";
            default -> "Ore, raw metal or sand";
        };
    }

    /** What the cook prepares. */
    public static Predicate<ItemStack> cookInput(@Nullable BuildingData building) {
        return switch (get(building, COOK)) {
            case "meat" -> s -> s.is(Items.BEEF) || s.is(Items.PORKCHOP) || s.is(Items.CHICKEN) || s.is(Items.MUTTON)
                    || s.is(Items.RABBIT);
            case "fish" -> s -> s.is(Items.COD) || s.is(Items.SALMON);
            case "vegetables" -> s -> s.is(Items.POTATO) || s.is(Items.KELP);
            default -> Logistics::isRawFood;
        };
    }

    public static String cookInputName(@Nullable BuildingData building) {
        return switch (get(building, COOK)) {
            case "meat" -> "Raw meat";
            case "fish" -> "Raw fish";
            case "vegetables" -> "Potatoes or kelp";
            default -> "Raw food";
        };
    }

    /** Adult animals the herder keeps, or -1 to follow the hut level. */
    public static int herdSize(@Nullable BuildingData building) {
        String value = get(building, HERD_SIZE);
        return "auto".equals(value) ? -1 : Integer.parseInt(value);
    }
}
