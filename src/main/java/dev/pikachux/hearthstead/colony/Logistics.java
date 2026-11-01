package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What each building needs delivered and which of its items may be taken away.
 * Used by the deliveryman and shown in the hut window.
 */
public final class Logistics {
    /** Raw foods the cook knows how to prepare. */
    public static final Set<Item> RAW_FOODS = Set.of(Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON,
            Items.RABBIT, Items.COD, Items.SALMON, Items.POTATO, Items.KELP);
    /** Fuels the cook uses; deliberately excludes wood, which builders need. */
    public static final Set<Item> COOK_FUELS = Set.of(Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK, Items.BLAZE_ROD,
            Items.DRIED_KELP_BLOCK);

    /** Number of items a worker hut may hold before the deliveryman empties it. */
    public static final int PICKUP_THRESHOLD = 16;

    private Logistics() {
    }

    public record Request(BlockPos target, Predicate<ItemStack> matcher, int count, String description) {
    }

    public static boolean isRawFood(ItemStack stack) {
        return RAW_FOODS.contains(stack.getItem());
    }

    public static boolean isCookFuel(ItemStack stack) {
        return COOK_FUELS.contains(stack.getItem());
    }

    /** Ready to eat food, preferring what the cook made over raw ingredients. */
    public static boolean isMeal(ItemStack stack) {
        return stack.has(DataComponents.FOOD) && !isRawFood(stack);
    }

    /** Ores, raw metals and sand: what the smelter works with. */
    public static boolean isSmeltable(ItemStack stack) {
        return stack.is(ConventionalItemTags.RAW_MATERIALS) || stack.is(ConventionalItemTags.ORES)
                || stack.is(ItemTags.SMELTS_TO_GLASS);
    }

    /** Plant waste for the composter; leaves out food, seeds and wheat, which other jobs need. */
    public static boolean isCompostable(ItemStack stack) {
        return ComposterBlock.COMPOSTABLES.containsKey(stack.getItem()) && !stack.has(DataComponents.FOOD)
                && !stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) && !stack.is(Items.WHEAT) && !stack.is(Items.HAY_BLOCK)
                && !stack.is(Items.CAKE);
    }

    /** Stone the stonemason can cut into building parts. */
    public static final Set<Item> MASONRY = Set.of(Items.STONE, Items.COBBLESTONE, Items.STONE_BRICKS, Items.SANDSTONE,
            Items.SMOOTH_SANDSTONE, Items.RED_SANDSTONE, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.DEEPSLATE,
            Items.COBBLED_DEEPSLATE, Items.BRICKS, Items.MUD_BRICKS, Items.TUFF, Items.BLACKSTONE, Items.SMOOTH_STONE);

    public static boolean isMasonry(ItemStack stack) {
        return MASONRY.contains(stack.getItem());
    }

    public static boolean isFlower(ItemStack stack) {
        return stack.is(ItemTags.SMALL_FLOWERS);
    }

    /** What the dyer works with: flowers, white wool and planks for beds. */
    public static boolean isDyerMaterial(ItemStack stack) {
        return isFlower(stack) || stack.is(Items.WHITE_WOOL) || stack.is(ItemTags.PLANKS);
    }

    public static boolean isSand(ItemStack stack) {
        return stack.is(ItemTags.SMELTS_TO_GLASS);
    }

    public static boolean isDye(ItemStack stack) {
        return stack.is(ConventionalItemTags.DYES);
    }

    public static boolean isConcretePowder(ItemStack stack) {
        return stack.is(ConventionalItemTags.CONCRETE_POWDERS);
    }

    public static boolean isRabbitFood(ItemStack stack) {
        return stack.is(Items.CARROT) || stack.is(Items.GOLDEN_CARROT) || stack.is(Items.DANDELION);
    }

    public static boolean isButcherTool(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
    }

    /** Ingredients a brewing stand accepts (the alchemist finishes awkward potions with them). */
    public static final Set<Item> BREWING_INGREDIENTS = Set.of(Items.SUGAR, Items.RABBIT_FOOT, Items.GLISTERING_MELON_SLICE,
            Items.SPIDER_EYE, Items.PUFFERFISH, Items.MAGMA_CREAM, Items.GOLDEN_CARROT, Items.BLAZE_POWDER, Items.GHAST_TEAR,
            Items.TURTLE_HELMET, Items.PHANTOM_MEMBRANE, Items.FERMENTED_SPIDER_EYE, Items.BREEZE_ROD, Items.SLIME_BLOCK,
            Items.COBWEB, Items.STONE, Items.NETHER_WART);

    public static boolean isBrewingIngredient(ItemStack stack) {
        return BREWING_INGREDIENTS.contains(stack.getItem());
    }

    /** What the stone smeltery puts in its furnaces (not stone itself, which is what it makes). */
    public static final Set<Item> STONE_SMELTABLES = Set.of(Items.COBBLESTONE, Items.CLAY_BALL, Items.CLAY, Items.NETHERRACK,
            Items.COBBLED_DEEPSLATE, Items.SANDSTONE, Items.RED_SANDSTONE, Items.QUARTZ_BLOCK, Items.BASALT);

    public static boolean isStoneSmeltable(ItemStack stack) {
        return STONE_SMELTABLES.contains(stack.getItem());
    }

    public static boolean isFishingRod(ItemStack stack) {
        return stack.is(Items.FISHING_ROD);
    }

    /** Items a building keeps; anything else in a worker hut is its output. */
    public static Predicate<ItemStack> keeps(BuildingType type) {
        return switch (type) {
            case LUMBERJACK -> s -> s.is(ItemTags.AXES) || s.is(ItemTags.SAPLINGS);
            case FARMER -> s -> s.is(ItemTags.HOES) || s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || s.is(Items.BONE_MEAL);
            case MINER -> s -> s.is(ItemTags.PICKAXES) || s.is(Items.TORCH);
            case GUARD_TOWER, BARRACKS -> s -> s.is(ItemTags.SWORDS) || s.is(Items.ARROW) || s.is(Items.SHIELD)
                    || s.is(Items.BOW) || s.is(Items.NETHER_WART);
            case LIBRARY -> s -> s.is(Items.BOOK);
            case QUARRY -> s -> s.is(ItemTags.PICKAXES);
            case STASH -> s -> false;
            case FISHERMAN -> Logistics::isFishingRod;
            case COOK -> s -> isRawFood(s) || isCookFuel(s);
            case SMELTER -> s -> isSmeltable(s) || isCookFuel(s);
            case BAKER -> s -> s.is(Items.WHEAT);
            case SHEPHERD -> s -> s.is(Items.SHEARS) || s.is(Items.WHEAT);
            case COWHAND -> s -> s.is(Items.BUCKET) || s.is(Items.WHEAT);
            case CHICKEN_HERDER -> s -> s.is(ItemTags.CHICKEN_FOOD);
            case SWINEHERD -> s -> s.is(ItemTags.PIG_FOOD) || isButcherTool(s);
            case STONEMASON -> Logistics::isMasonry;
            case FLORIST -> s -> s.is(Items.BONE_MEAL);
            case DYER -> Logistics::isDyerMaterial;
            case BEEKEEPER -> s -> s.is(Items.SHEARS) || s.is(Items.GLASS_BOTTLE);
            case RABBIT_HERDER -> s -> isRabbitFood(s) || isButcherTool(s);
            case GLASSBLOWER -> s -> isSand(s) || isCookFuel(s) || isDye(s) || s.is(Items.GLASS);
            case CONCRETE_MIXER -> s -> isSand(s) || s.is(Items.GRAVEL) || isDye(s) || isConcretePowder(s);
            case COMPOSTER -> Logistics::isCompostable;
            case SAWMILL -> s -> s.is(ItemTags.LOGS) || s.is(Items.STRING);
            case PLANTER -> s -> false;
            case ALCHEMIST -> dev.pikachux.hearthstead.entity.ai.AlchemistGoal::isMaterial;
            case ENCHANTER -> s -> s.is(Items.BOOK) || s.is(Items.LAPIS_LAZULI);
            case CRUSHER -> s -> s.is(Items.COBBLESTONE);
            case SIFTER -> s -> s.is(Items.GRAVEL) || s.is(Items.SAND) || s.is(Items.DIRT);
            case MECHANIC -> s -> !Crafters.MECHANISMS.contains(s.getItem());
            case NETHERWORKER -> s -> isMeal(s) || s.is(ItemTags.SWORDS);
            case BLACKSMITH -> s -> !Crafters.isSmithing(s.getItem());
            case FLETCHER -> s -> !Crafters.FLETCHING.contains(s.getItem());
            case STONE_SMELTERY -> s -> isStoneSmeltable(s) || isCookFuel(s);
            default -> s -> true;
        };
    }

    /** Whether the deliveryman should empty this building into the warehouse. */
    public static boolean producesOutput(BuildingType type) {
        return switch (type) {
            case LUMBERJACK, FARMER, MINER, FISHERMAN, COOK, SMELTER, BAKER, SHEPHERD, COWHAND, COMPOSTER, SAWMILL,
                 CHICKEN_HERDER, SWINEHERD, STONEMASON, FLORIST, DYER, BEEKEEPER,
                 RABBIT_HERDER, GLASSBLOWER, CONCRETE_MIXER, PLANTER, ALCHEMIST, ENCHANTER,
                 CRUSHER, SIFTER, MECHANIC, NETHERWORKER, BLACKSMITH, FLETCHER, STONE_SMELTERY,
                 QUARRY, STASH -> true;
            default -> false;
        };
    }

    /**
     * Items the colony's huts are waiting for that neither the warehouse nor {@code ownStock} (the
     * crafter's own storage, holding what it already made) can supply, limited to the candidates a
     * crafting job can make. Builders' needs come first.
     */
    public static List<Item> shortages(ServerLevel level, Colony colony, Predicate<Item> candidates, List<Container> ownStock) {
        List<Container> available = new ArrayList<>(ownStock);
        for (BuildingData building : colony.buildings()) {
            if (building.type() == BuildingType.WAREHOUSE) {
                available.addAll(Storage.containers(level, building));
            }
        }
        List<BuildingData> buildings = new ArrayList<>(colony.buildings());
        buildings.sort(java.util.Comparator.comparing(b -> b.type() != BuildingType.BUILDER));
        List<Item> wanted = new ArrayList<>();
        for (BuildingData building : buildings) {
            for (Request request : requests(level, colony, building)) {
                if (Storage.count(available, request.matcher()) > 0) {
                    continue;
                }
                for (Item item : BuiltInRegistries.ITEM) {
                    if (!wanted.contains(item) && candidates.test(item) && request.matcher().test(new ItemStack(item))) {
                        wanted.add(item);
                    }
                }
            }
        }
        return wanted;
    }

    /** Everything the colony's buildings are currently waiting for. */
    public static List<Request> requests(ServerLevel level, Colony colony) {
        List<Request> requests = new ArrayList<>();
        for (BuildingData building : colony.buildings()) {
            requests.addAll(requests(level, colony, building));
        }
        return requests;
    }

    public static List<Request> requests(ServerLevel level, Colony colony, BuildingData building) {
        List<Request> requests = new ArrayList<>();
        List<Container> storage = Storage.containers(level, building);
        BlockPos pos = building.pos();
        switch (building.type()) {
            case BUILDER -> builderRequests(level, colony, building, storage, requests);
            case LUMBERJACK -> tool(level, colony, building, storage, s -> s.is(ItemTags.AXES), "Axe", requests);
            case FARMER -> {
                tool(level, colony, building, storage, s -> s.is(ItemTags.HOES), "Hoe", requests);
                restock(building, storage, HutSettings.farmerSeeds(building), 16, 32, HutSettings.farmerSeedsName(building), requests);
                restock(building, storage, s -> s.is(Items.BONE_MEAL), 4, 16, "Bone Meal", requests);
            }
            case MINER -> {
                tool(level, colony, building, storage, s -> s.is(ItemTags.PICKAXES), "Pickaxe", requests);
                if (HutSettings.isOn(building, HutSettings.TORCHES)) {
                    restock(building, storage, s -> s.is(Items.TORCH), 16, 32, "Torches", requests);
                }
            }
            case GUARD_TOWER, BARRACKS -> {
                switch (Guards.kind(building)) {
                    case KNIGHT -> {
                        tool(level, colony, building, storage, s -> s.is(ItemTags.SWORDS), "Sword", requests);
                        offhandTool(level, colony, building, storage, s -> s.is(Items.SHIELD), "Shield", requests);
                        // Knights trained at an Archery also shoot arrows from their tower's stock.
                        if (building.level() > 0 && colony.trainingGround(BuildingType.ARCHERY) != null) {
                            restock(building, storage, s -> s.is(Items.ARROW), 8, 32, "Arrows", requests);
                        }
                    }
                    case RANGER -> {
                        tool(level, colony, building, storage, s -> s.is(Items.BOW), "Bow", requests);
                        restock(building, storage, s -> s.is(Items.ARROW), 16, 64, "Arrows", requests);
                    }
                    case DRUID -> {
                        // Druids brew their potions from nether wart.
                        restock(building, storage, s -> s.is(Items.NETHER_WART), 4, 16, "Nether Wart", requests);
                    }
                }
            }
            case HOSPITAL -> {
                // One cure for every sick citizen, by illness.
                if (building.level() > 0) {
                    for (Illness illness : Illness.values()) {
                        long sick = colony.citizens().stream().filter(c -> c.illness() == illness).count();
                        if (sick > 0) {
                            restock(building, storage, s -> s.is(illness.cure()), (int) sick, (int) sick,
                                    illness.cure().getDefaultInstance().getHoverName().getString(), requests);
                        }
                    }
                }
            }
            case LIBRARY -> {
                if (building.level() > 0) {
                    restock(building, storage, s -> s.is(Items.BOOK), 2, 8, "Books", requests);
                }
            }
            case QUARRY -> tool(level, colony, building, storage, s -> s.is(ItemTags.PICKAXES), "Pickaxe", requests);
            case FISHERMAN -> tool(level, colony, building, storage, Logistics::isFishingRod, "Fishing Rod", requests);
            case COOK -> {
                if (building.level() > 0) {
                    restock(building, storage, HutSettings.cookInput(building), 16, 32, HutSettings.cookInputName(building), requests);
                    restock(building, storage, Logistics::isCookFuel, 8, 16, "Coal or charcoal", requests);
                }
            }
            case SMELTER -> {
                if (building.level() > 0) {
                    restock(building, storage, HutSettings.smelterInput(building), 16, 32, HutSettings.smelterInputName(building), requests);
                    restock(building, storage, Logistics::isCookFuel, 8, 16, "Coal or charcoal", requests);
                }
            }
            case BAKER -> {
                if (building.level() > 0) {
                    restock(building, storage, s -> s.is(Items.WHEAT), 15, 33, "Wheat", requests);
                }
            }
            case SHEPHERD -> {
                tool(level, colony, building, storage, s -> s.is(Items.SHEARS), "Shears", requests);
                if (HutSettings.isOn(building, HutSettings.BREED)) {
                    restock(building, storage, s -> s.is(Items.WHEAT), 8, 16, "Wheat", requests);
                }
            }
            case COWHAND -> {
                restock(building, storage, s -> s.is(Items.BUCKET), 2, 4, "Buckets", requests);
                if (HutSettings.isOn(building, HutSettings.BREED)) {
                    restock(building, storage, s -> s.is(Items.WHEAT), 8, 16, "Wheat", requests);
                }
            }
            case COMPOSTER -> {
                if (building.level() > 0) {
                    restock(building, storage, Logistics::isCompostable, 16, 64, "Plant waste (leaves, saplings, ...)", requests);
                }
            }
            case SAWMILL -> {
                if (building.level() > 0) {
                    restock(building, storage, s -> s.is(ItemTags.LOGS), 8, 32, "Logs", requests);
                    restock(building, storage, s -> s.is(Items.STRING), 2, 8, "String", requests);
                }
            }
            case CHICKEN_HERDER -> {
                if (HutSettings.isOn(building, HutSettings.BREED)) {
                    restock(building, storage, s -> s.is(ItemTags.CHICKEN_FOOD), 8, 16, "Seeds", requests);
                }
            }
            case SWINEHERD -> {
                tool(level, colony, building, storage, Logistics::isButcherTool, "Sword or Axe", requests);
                if (HutSettings.isOn(building, HutSettings.BREED)) {
                    restock(building, storage, s -> s.is(ItemTags.PIG_FOOD), 8, 16, "Carrots, potatoes or beetroot", requests);
                }
            }
            case STONEMASON -> {
                if (building.level() > 0) {
                    restock(building, storage, Logistics::isMasonry, 16, 64, "Stone or cobblestone", requests);
                }
            }
            case FLORIST -> restock(building, storage, s -> s.is(Items.BONE_MEAL), 4, 16, "Bone Meal", requests);
            case DYER -> {
                if (building.level() > 0) {
                    restock(building, storage, Logistics::isFlower, 4, 16, "Flowers", requests);
                    restock(building, storage, s -> s.is(Items.WHITE_WOOL), 3, 9, "White Wool", requests);
                    restock(building, storage, s -> s.is(ItemTags.PLANKS), 3, 9, "Planks", requests);
                }
            }
            case BEEKEEPER -> tool(level, colony, building, storage, s -> s.is(Items.SHEARS), "Shears", requests);
            case RABBIT_HERDER -> {
                tool(level, colony, building, storage, Logistics::isButcherTool, "Sword or Axe", requests);
                if (HutSettings.isOn(building, HutSettings.BREED)) {
                    restock(building, storage, Logistics::isRabbitFood, 8, 16, "Carrots or dandelions", requests);
                }
            }
            case GLASSBLOWER -> {
                if (building.level() > 0) {
                    restock(building, storage, Logistics::isSand, 16, 64, "Sand", requests);
                    restock(building, storage, Logistics::isCookFuel, 8, 16, "Coal or charcoal", requests);
                }
            }
            case CONCRETE_MIXER -> {
                if (building.level() > 0) {
                    restock(building, storage, Logistics::isSand, 8, 32, "Sand", requests);
                    restock(building, storage, s -> s.is(Items.GRAVEL), 8, 32, "Gravel", requests);
                    restock(building, storage, Logistics::isDye, 2, 8, "Dyes", requests);
                }
            }
            case ALCHEMIST -> {
                if (building.level() > 0) {
                    // Only ask for more bottles once the water and awkward potions are used up.
                    if (Storage.count(storage, dev.pikachux.hearthstead.entity.ai.AlchemistGoal::isBasePotion) < 3) {
                        restock(building, storage, s -> s.is(Items.GLASS_BOTTLE), 3, 9, "Glass Bottles", requests);
                    }
                    restock(building, storage, s -> s.is(Items.NETHER_WART), 3, 9, "Nether Wart", requests);
                    restock(building, storage, s -> s.is(Items.BLAZE_POWDER), 1, 2, "Blaze Powder", requests);
                }
            }
            case ENCHANTER -> {
                if (building.level() > 0) {
                    int lapis = dev.pikachux.hearthstead.entity.ai.EnchanterGoal.lapisCost(building.level());
                    restock(building, storage, s -> s.is(Items.BOOK), 1, 4, "Books", requests);
                    restock(building, storage, s -> s.is(Items.LAPIS_LAZULI), lapis, lapis * 4, "Lapis Lazuli", requests);
                }
            }
            case CRUSHER -> {
                if (building.level() > 0) {
                    restock(building, storage, s -> s.is(Items.COBBLESTONE), 16, 64, "Cobblestone", requests);
                }
            }
            case SIFTER -> {
                if (building.level() > 0) {
                    Item input = HutSettings.sifterInput(building);
                    restock(building, storage, s -> s.is(input), 16, 64, input.getDefaultInstance().getHoverName().getString(), requests);
                }
            }
            case MECHANIC -> {
                if (building.level() > 0) {
                    restock(building, storage, s -> s.is(Items.IRON_INGOT), 4, 16, "Iron Ingots", requests);
                    restock(building, storage, s -> s.is(Items.REDSTONE), 4, 16, "Redstone", requests);
                    restock(building, storage, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), 4, 16, "Coal or charcoal", requests);
                    restock(building, storage, s -> s.is(Items.STICK), 4, 16, "Sticks", requests);
                    restock(building, storage, s -> s.is(Items.COBBLESTONE), 8, 32, "Cobblestone", requests);
                }
            }
            case BLACKSMITH -> {
                if (building.level() > 0) {
                    restock(building, storage, s -> s.is(Items.IRON_INGOT), 6, 24, "Iron Ingots", requests);
                    restock(building, storage, s -> s.is(Items.STICK), 4, 16, "Sticks", requests);
                }
            }
            case FLETCHER -> {
                if (building.level() > 0) {
                    restock(building, storage, s -> s.is(Items.STICK), 4, 16, "Sticks", requests);
                    restock(building, storage, s -> s.is(Items.STRING), 4, 16, "String", requests);
                    restock(building, storage, s -> s.is(Items.FLINT), 2, 8, "Flint", requests);
                    restock(building, storage, s -> s.is(Items.FEATHER), 2, 8, "Feathers", requests);
                }
            }
            case STONE_SMELTERY -> {
                if (building.level() > 0) {
                    restock(building, storage, s -> s.is(Items.COBBLESTONE), 16, 64, "Cobblestone", requests);
                    restock(building, storage, Logistics::isCookFuel, 8, 16, "Coal or charcoal", requests);
                }
            }
            case NETHERWORKER -> {
                tool(level, colony, building, storage, s -> s.is(ItemTags.SWORDS), "Sword", requests);
                if (building.level() > 0) {
                    restock(building, storage, Logistics::isMeal, 8, 16, "Food for the expedition", requests);
                }
            }
            case TOWN_HALL, RESIDENCE -> {
                long residents = colony.citizens().stream().filter(c -> pos.equals(c.homePos())).count();
                if (residents > 0) {
                    restock(building, storage, Logistics::isMeal, (int) residents * 2, (int) residents * 4, "Food", requests);
                }
            }
            default -> {
            }
        }
        return requests;
    }

    private static void restock(BuildingData building, List<Container> storage, Predicate<ItemStack> matcher,
                                int minimum, int target, String description, List<Request> requests) {
        int have = Storage.count(storage, matcher);
        if (have < minimum) {
            requests.add(new Request(building.pos(), matcher, target - have, description));
        }
    }

    /** Requests a tool unless the hut or one of its workers already has one. */
    private static void tool(ServerLevel level, Colony colony, BuildingData building, List<Container> storage,
                             Predicate<ItemStack> matcher, String description, List<Request> requests) {
        if (Storage.count(storage, matcher) > 0) {
            return;
        }
        for (CitizenData citizen : colony.citizens()) {
            if (building.pos().equals(citizen.workPos()) && citizen.entityUuid() != null
                    && level.getEntity(citizen.entityUuid()) instanceof CitizenEntity entity
                    && matcher.test(entity.getMainHandItem())) {
                return;
            }
        }
        requests.add(new Request(building.pos(), matcher, 1, description));
    }

    /** Like {@link #tool}, for something the hut's workers carry in their off hand (a knight's shield). */
    private static void offhandTool(ServerLevel level, Colony colony, BuildingData building, List<Container> storage,
                                    Predicate<ItemStack> matcher, String description, List<Request> requests) {
        if (Storage.count(storage, matcher) > 0) {
            return;
        }
        for (CitizenData citizen : colony.citizens()) {
            if (building.pos().equals(citizen.workPos()) && citizen.entityUuid() != null
                    && level.getEntity(citizen.entityUuid()) instanceof CitizenEntity entity
                    && matcher.test(entity.getOffhandItem())) {
                return;
            }
        }
        requests.add(new Request(building.pos(), matcher, 1, description));
    }

    /** Materials the builder's current work order still needs beyond what it already has. */
    private static void builderRequests(ServerLevel level, Colony colony, BuildingData builderHut,
                                        List<Container> storage, List<Request> requests) {
        WorkOrder order = colony.workOrders().stream()
                .filter(o -> builderHut.pos().equals(o.builder()))
                .findFirst()
                .orElse(null);
        if (order == null || order.kind() == WorkOrder.Kind.REMOVE) {
            return;
        }
        List<Blueprint.Placement> plan = Construction.plan(level, colony, order);
        if (plan == null) {
            return;
        }
        List<Container> carried = new ArrayList<>(storage);
        for (CitizenData citizen : colony.citizens()) {
            if (builderHut.pos().equals(citizen.workPos()) && citizen.entityUuid() != null
                    && level.getEntity(citizen.entityUuid()) instanceof CitizenEntity entity) {
                carried.add(entity.inventory());
            }
        }
        for (Map.Entry<Item, Integer> entry : Construction.progress(level, plan).remaining().entrySet()) {
            Item item = entry.getKey();
            int missing = entry.getValue() - Storage.count(carried, s -> s.is(item));
            if (missing > 0) {
                requests.add(new Request(builderHut.pos(), s -> s.is(item), Math.min(missing, item.getDefaultMaxStackSize()),
                        item.getDefaultInstance().getHoverName().getString()));
            }
        }
    }
}
