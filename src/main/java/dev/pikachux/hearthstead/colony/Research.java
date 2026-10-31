package dev.pikachux.hearthstead.colony;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The research tree. Each research belongs to a branch, needs its parent research and
 * a university of at least its tier, costs materials to start and gives the whole
 * colony a lasting bonus once finished.
 */
public enum Research {
    // Civilian: comfort and growth
    COMMUNITY_SPIRIT(Branch.CIVILIAN, 1, null, 300, cost(Items.BREAD, 16)),
    COMFORTABLE_HOMES(Branch.CIVILIAN, 2, COMMUNITY_SPIRIT, 900, cost(Items.WHITE_WOOL, 16), cost(Items.BOOK, 4)),
    FEAST_DAYS(Branch.CIVILIAN, 3, COMFORTABLE_HOMES, 2400, cost(Items.BREAD, 32), cost(Items.CAKE, 1), cost(Items.BOOK, 8)),
    SPACIOUS_HOMES(Branch.CIVILIAN, 4, FEAST_DAYS, 4800, cost(Items.OAK_PLANKS, 64), cost(Items.WHITE_BED, 4), cost(Items.BOOK, 12)),
    GOLDEN_AGE(Branch.CIVILIAN, 5, SPACIOUS_HOMES, 9000, cost(Items.GOLD_BLOCK, 2), cost(Items.EMERALD, 8), cost(Items.BOOK, 16)),
    HERBAL_REMEDIES(Branch.CIVILIAN, 2, COMMUNITY_SPIRIT, 900, cost(Items.POPPY, 8), cost(Items.SWEET_BERRIES, 16), cost(Items.BOOK, 4)),
    WELCOME_WAGONS(Branch.CIVILIAN, 3, HERBAL_REMEDIES, 2400, cost(Items.OAK_BOAT, 1), cost(Items.CHEST, 4), cost(Items.BOOK, 8)),
    MARKET_DAYS(Branch.CIVILIAN, 4, WELCOME_WAGONS, 4800, cost(Items.EMERALD, 4), cost(Items.BARREL, 4), cost(Items.BOOK, 12)),
    // Technology: work and tools
    APPRENTICESHIP(Branch.TECHNOLOGY, 1, null, 300, cost(Items.PAPER, 8)),
    SWIFT_CONSTRUCTION(Branch.TECHNOLOGY, 2, APPRENTICESHIP, 900, cost(Items.IRON_INGOT, 16), cost(Items.BOOK, 4)),
    MASTER_CRAFTSMEN(Branch.TECHNOLOGY, 3, SWIFT_CONSTRUCTION, 2400, cost(Items.GOLD_INGOT, 8), cost(Items.DIAMOND, 1), cost(Items.BOOK, 8)),
    DELIVERY_CARTS(Branch.TECHNOLOGY, 4, MASTER_CRAFTSMEN, 4800, cost(Items.MINECART, 2), cost(Items.SADDLE, 1), cost(Items.BOOK, 12)),
    MASTERWORK(Branch.TECHNOLOGY, 5, DELIVERY_CARTS, 9000, cost(Items.DIAMOND, 4), cost(Items.ANVIL, 1), cost(Items.BOOK, 16)),
    BOUNTIFUL_HARVEST(Branch.TECHNOLOGY, 2, APPRENTICESHIP, 900, cost(Items.BONE_MEAL, 32), cost(Items.BOOK, 4)),
    SKILLED_LUMBERJACKS(Branch.TECHNOLOGY, 3, BOUNTIFUL_HARVEST, 2400, cost(Items.IRON_AXE, 2), cost(Items.BOOK, 8)),
    PROSPECTORS(Branch.TECHNOLOGY, 4, SKILLED_LUMBERJACKS, 4800, cost(Items.IRON_PICKAXE, 2), cost(Items.MAP, 1), cost(Items.BOOK, 12)),
    // Combat: guards and defences
    MILITIA_DRILLS(Branch.COMBAT, 1, null, 300, cost(Items.IRON_INGOT, 8)),
    CHAINMAIL_TRAINING(Branch.COMBAT, 2, MILITIA_DRILLS, 900, cost(Items.IRON_INGOT, 24), cost(Items.BOOK, 4)),
    STONE_WALLS(Branch.COMBAT, 3, CHAINMAIL_TRAINING, 2400, cost(Items.STONE_BRICKS, 64), cost(Items.BOOK, 8)),
    PLATE_ARMOR(Branch.COMBAT, 4, STONE_WALLS, 4800, cost(Items.IRON_BLOCK, 4), cost(Items.BOOK, 12)),
    FORTIFIED_COLONY(Branch.COMBAT, 5, PLATE_ARMOR, 9000, cost(Items.OBSIDIAN, 16), cost(Items.IRON_BLOCK, 4), cost(Items.BOOK, 16)),
    FIRST_AID(Branch.COMBAT, 2, MILITIA_DRILLS, 900, cost(Items.GOLDEN_APPLE, 1), cost(Items.PAPER, 16), cost(Items.BOOK, 4)),
    SWIFT_PATROLS(Branch.COMBAT, 3, FIRST_AID, 2400, cost(Items.LEATHER_BOOTS, 4), cost(Items.SUGAR, 32), cost(Items.BOOK, 8)),
    VETERAN_GUARDS(Branch.COMBAT, 4, SWIFT_PATROLS, 4800, cost(Items.IRON_SWORD, 4), cost(Items.SHIELD, 2), cost(Items.BOOK, 12));

    public enum Branch {
        CIVILIAN, TECHNOLOGY, COMBAT
    }

    private final Branch branch;
    private final int tier;
    @Nullable
    private final Research parent;
    private final int points;
    private final List<ItemStack> cost;

    Research(Branch branch, int tier, @Nullable Research parent, int points, ItemStack... cost) {
        this.branch = branch;
        this.tier = tier;
        this.parent = parent;
        this.points = points;
        this.cost = List.of(cost);
    }

    private static ItemStack cost(Item item, int count) {
        return new ItemStack(item, count);
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String nameKey() {
        return "research.hearthstead." + id();
    }

    public String descriptionKey() {
        return nameKey() + ".desc";
    }

    public Branch branch() {
        return branch;
    }

    /** Also the university level needed to start this research. */
    public int tier() {
        return tier;
    }

    @Nullable
    public Research parent() {
        return parent;
    }

    /** Research points needed to finish; researchers earn about one point per second. */
    public int points() {
        return points;
    }

    public List<ItemStack> cost() {
        return cost;
    }

    @Nullable
    public static Research byId(String id) {
        for (Research research : values()) {
            if (research.id().equals(id)) {
                return research;
            }
        }
        return null;
    }
}
