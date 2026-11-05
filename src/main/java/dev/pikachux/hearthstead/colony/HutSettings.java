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
    public static final Setting CRUSH = new Setting("crush", List.of("gravel", "sand"));
    public static final Setting SIFT = new Setting("sift", List.of("gravel", "sand", "dirt"));
    public static final Setting GUARD_CLASS = new Setting("guard_class", List.of("knight", "ranger", "druid"));
    public static final Setting GUARD_ORDER = new Setting("guard_order", List.of("patrol", "colony", "hold", "follow"));
    public static final Setting BUILDER_JOBS = new Setting("builder_jobs", List.of("all", "build", "maintain"));
    public static final Setting COLLECT = new Setting("collect", List.of("early", "full"));
    public static final Setting CATCH = new Setting("catch", List.of("all", "fish"));
    public static final Setting BAKE = new Setting("bake", List.of("bread", "cookies", "pie"));
    public static final Setting NEXT_RESEARCH = new Setting("next_research", List.of("manual", "auto"));
    public static final Setting COMPOST = new Setting("compost", List.of("all", "leaves"));
    public static final Setting SPARE_PLANKS = new Setting("spare_planks", List.of("64", "0", "32", "128"));
    public static final Setting SPARE_BRICKS = new Setting("spare_bricks", List.of("0", "32", "64", "128"));
    public static final Setting PICK_FLOWERS = new Setting("pick_flowers", List.of("on", "off"));
    public static final Setting IDLE_DYES = new Setting("idle_dyes", List.of("on", "off"));
    public static final Setting HONEY = new Setting("honey", List.of("bottles", "comb"));
    public static final Setting GLASSWORK = new Setting("glasswork", List.of("on", "off"));
    public static final Setting SET_CONCRETE = new Setting("set_concrete", List.of("on", "off"));
    public static final Setting SPREAD = new Setting("spread", List.of("all", "sugar_cane", "cactus", "bamboo"));
    public static final Setting SPLASH = new Setting("splash", List.of("off", "on"));
    public static final Setting ENCHANT_POWER = new Setting("enchant_power", List.of("strong", "cheap"));
    public static final Setting OWN_TRADE = new Setting("own_trade", List.of("on", "off"));
    public static final Setting SMITH_MATERIAL = new Setting("smith_material", List.of("best", "no_diamond", "stone"));
    public static final Setting EXPEDITIONS = new Setting("expeditions", List.of("on", "off"));
    public static final Setting STONE_SMELT = new Setting("stone_smelt", List.of("all", "stone", "clay", "nether"));
    public static final Setting TREAT = new Setting("treat", List.of("all", "cures", "wounds"));
    public static final Setting PIT_SIZE = new Setting("pit_size", List.of("auto", "small", "medium"));
    public static final Setting BOOK_USE = new Setting("book_use", List.of("normal", "thrifty"));
    public static final Setting GROW_FASTER = new Setting("grow_faster", List.of("on", "off"));
    public static final Setting REVIVE = new Setting("revive", List.of("on", "off"));
    public static final Setting VISITORS = new Setting("visitors", List.of("on", "off"));
    public static final Setting TRAINING = new Setting("training", List.of("on", "off"));

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
            case CRUSHER -> List.of(CRUSH);
            case SIFTER -> List.of(SIFT);
            case GUARD_TOWER, BARRACKS -> List.of(GUARD_CLASS, GUARD_ORDER);
            case BUILDER -> List.of(BUILDER_JOBS);
            case DELIVERYMAN -> List.of(COLLECT);
            case FISHERMAN -> List.of(CATCH);
            case BAKER -> List.of(BAKE);
            case UNIVERSITY -> List.of(NEXT_RESEARCH);
            case COMPOSTER -> List.of(COMPOST);
            case SAWMILL -> List.of(SPARE_PLANKS);
            case STONEMASON -> List.of(SPARE_BRICKS);
            case FLORIST -> List.of(PICK_FLOWERS);
            case DYER -> List.of(IDLE_DYES);
            case BEEKEEPER -> List.of(HONEY);
            case GLASSBLOWER -> List.of(GLASSWORK);
            case CONCRETE_MIXER -> List.of(SET_CONCRETE);
            case PLANTER -> List.of(SPREAD);
            case ALCHEMIST -> List.of(SPLASH);
            case ENCHANTER -> List.of(ENCHANT_POWER);
            case MECHANIC, FLETCHER -> List.of(OWN_TRADE);
            case BLACKSMITH -> List.of(OWN_TRADE, SMITH_MATERIAL);
            case NETHERWORKER -> List.of(EXPEDITIONS);
            case STONE_SMELTERY -> List.of(STONE_SMELT);
            case HOSPITAL -> List.of(TREAT);
            case QUARRY -> List.of(PIT_SIZE);
            case LIBRARY -> List.of(BOOK_USE);
            case SCHOOL -> List.of(GROW_FASTER);
            case GRAVEYARD -> List.of(REVIVE);
            case TAVERN -> List.of(VISITORS);
            case COMBAT_ACADEMY, ARCHERY -> List.of(TRAINING);
            // The town hall, residences, warehouse, stash, postbox and mystical site have no worker to steer.
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

    /** The block the sifter sifts. */
    public static net.minecraft.world.item.Item sifterInput(@Nullable BuildingData building) {
        return switch (get(building, SIFT)) {
            case "sand" -> Items.SAND;
            case "dirt" -> Items.DIRT;
            default -> Items.GRAVEL;
        };
    }

    /** Whether the builder takes this kind of work order. */
    public static boolean builderTakes(@Nullable BuildingData builderHut, WorkOrder order) {
        return switch (get(builderHut, BUILDER_JOBS)) {
            case "build" -> order.kind() == WorkOrder.Kind.BUILD;
            case "maintain" -> order.kind() != WorkOrder.Kind.BUILD;
            default -> true;
        };
    }

    /** Planks the sawmill keeps in the warehouse when nothing else is wanted. */
    public static int sparePlanks(@Nullable BuildingData building) {
        return Integer.parseInt(get(building, SPARE_PLANKS));
    }

    /** Stone bricks the stonemason keeps in the warehouse when nothing else is wanted. */
    public static int spareBricks(@Nullable BuildingData building) {
        return Integer.parseInt(get(building, SPARE_BRICKS));
    }

    /** What the composter takes: anything compostable, or only leaves and saplings. */
    public static Predicate<ItemStack> compostInput(@Nullable BuildingData building) {
        if ("leaves".equals(get(building, COMPOST))) {
            return s -> Logistics.isCompostable(s) && (s.is(ItemTags.LEAVES) || s.is(ItemTags.SAPLINGS));
        }
        return Logistics::isCompostable;
    }

    public static String compostInputName(@Nullable BuildingData building) {
        return "leaves".equals(get(building, COMPOST)) ? "Leaves or saplings" : "Plant waste (leaves, saplings, ...)";
    }

    /** What the stone smeltery smelts. */
    public static Predicate<ItemStack> stoneSmelterInput(@Nullable BuildingData building) {
        return switch (get(building, STONE_SMELT)) {
            case "stone" -> s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE);
            case "clay" -> s -> s.is(Items.CLAY_BALL) || s.is(Items.CLAY);
            case "nether" -> s -> s.is(Items.NETHERRACK);
            default -> Logistics::isStoneSmeltable;
        };
    }

    /** What the stone smeltery asks for: the main input of the chosen kind. */
    public static net.minecraft.world.item.Item stoneSmelterRequest(@Nullable BuildingData building) {
        return switch (get(building, STONE_SMELT)) {
            case "clay" -> Items.CLAY_BALL;
            case "nether" -> Items.NETHERRACK;
            default -> Items.COBBLESTONE;
        };
    }

    /** The hut level whose pit the quarrier digs: the quarry's own, or a smaller one. */
    public static int pitLevel(BuildingData building) {
        return switch (get(building, PIT_SIZE)) {
            case "small" -> Math.min(building.level(), 1);
            case "medium" -> Math.min(building.level(), 3);
            default -> building.level();
        };
    }

    /** Adult animals the herder keeps, or -1 to follow the hut level. */
    public static int herdSize(@Nullable BuildingData building) {
        String value = get(building, HERD_SIZE);
        return "auto".equals(value) ? -1 : Integer.parseInt(value);
    }
}
