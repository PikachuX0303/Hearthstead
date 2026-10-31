package dev.pikachux.hearthstead.colony;

import org.jetbrains.annotations.Nullable;

/**
 * Every hut block that can be placed in a colony.
 */
public enum BuildingType {
    TOWN_HALL("town_hall", "Town Hall", null, 4),
    RESIDENCE("residence", "Residence", null, 2),
    LUMBERJACK("lumberjack", "Lumberjack's Hut", JobType.LUMBERJACK, 0),
    FARMER("farmer", "Farmer's Hut", JobType.FARMER, 0),
    MINER("miner", "Miner's Hut", JobType.MINER, 0),
    GUARD_TOWER("guard_tower", "Guard Tower", JobType.GUARD, 0),
    WAREHOUSE("warehouse", "Warehouse", null, 0),
    BUILDER("builder", "Builder's Hut", JobType.BUILDER, 0),
    DELIVERYMAN("deliveryman", "Deliveryman's Hut", JobType.DELIVERYMAN, 0),
    COOK("cook", "Cook's Hut", JobType.COOK, 0),
    FISHERMAN("fisherman", "Fisherman's Hut", JobType.FISHERMAN, 0),
    BAKER("baker", "Bakery", JobType.BAKER, 0),
    SMELTER("smelter", "Smeltery", JobType.SMELTER, 0),
    SHEPHERD("shepherd", "Shepherd's Hut", JobType.SHEPHERD, 0),
    UNIVERSITY("university", "University", JobType.RESEARCHER, 0),
    COMPOSTER("composter", "Composter's Hut", JobType.COMPOSTER, 0),
    SAWMILL("sawmill", "Sawmill", JobType.SAWMILL, 0),
    COWHAND("cowhand", "Cowhand's Hut", JobType.COWHAND, 0),
    CHICKEN_HERDER("chickenherder", "Chicken Herder's Hut", JobType.CHICKEN_HERDER, 0),
    SWINEHERD("swineherd", "Swineherd's Hut", JobType.SWINEHERD, 0),
    STONEMASON("stonemason", "Stonemason's Hut", JobType.STONEMASON, 0),
    FLORIST("florist", "Florist's Hut", JobType.FLORIST, 0),
    DYER("dyer", "Dyer's Hut", JobType.DYER, 0),
    BEEKEEPER("beekeeper", "Apiary", JobType.BEEKEEPER, 0),
    RABBIT_HERDER("rabbitherder", "Rabbit Hutch", JobType.RABBIT_HERDER, 0),
    GLASSBLOWER("glassblower", "Glassblower's Hut", JobType.GLASSBLOWER, 0),
    CONCRETE_MIXER("concretemixer", "Concrete Mixer's Hut", JobType.CONCRETE_MIXER, 0),
    PLANTER("planter", "Planter's Hut", JobType.PLANTER, 0),
    ALCHEMIST("alchemist", "Alchemist's Hut", JobType.ALCHEMIST, 0),
    ENCHANTER("enchanter", "Enchanter's Hut", JobType.ENCHANTER, 0),
    CRUSHER("crusher", "Crusher's Hut", JobType.CRUSHER, 0),
    SIFTER("sifter", "Sifter's Hut", JobType.SIFTER, 0),
    MECHANIC("mechanic", "Mechanic's Workshop", JobType.MECHANIC, 0),
    NETHERWORKER("netherworker", "Nether Mine", JobType.NETHERWORKER, 0),
    BLACKSMITH("blacksmith", "Blacksmith's Hut", JobType.BLACKSMITH, 0),
    FLETCHER("fletcher", "Fletcher's Hut", JobType.FLETCHER, 0),
    STONE_SMELTERY("stonesmeltery", "Stone Smeltery", JobType.STONE_SMELTER, 0),
    BARRACKS("barracks", "Barracks", JobType.GUARD, 0),
    HOSPITAL("hospital", "Hospital", JobType.HEALER, 0),
    QUARRY("quarry", "Quarry", JobType.QUARRIER, 0),
    STASH("stash", "Stash", null, 0);

    public static final int MAX_LEVEL = 5;

    private final String id;
    private final String displayName;
    @Nullable
    private final JobType job;
    private final int housingPerLevel;

    BuildingType(String id, String displayName, @Nullable JobType job, int housingPerLevel) {
        this.id = id;
        this.displayName = displayName;
        this.job = job;
        this.housingPerLevel = housingPerLevel;
    }

    public String id() {
        return id;
    }

    public String blockName() {
        return "blockhut" + id.replace("_", "");
    }

    public String displayName() {
        return displayName;
    }

    @Nullable
    public JobType job() {
        return job;
    }

    /** Number of citizens this building houses at the given level. */
    public int housing(int level) {
        // The town hall houses the first settlers even before it is built.
        return housingPerLevel * (this == TOWN_HALL ? Math.max(1, level) : level);
    }

    /** Number of workers this building employs at the given level. */
    public int workers(int level) {
        if (job == null) {
            return 0;
        }
        if (job == JobType.BUILDER) {
            // A builder is needed to build the builder's hut itself.
            return 1;
        }
        if (level == 0) {
            return 0;
        }
        if (this == BARRACKS) {
            return 2 * level;
        }
        return job == JobType.GUARD || job == JobType.RESEARCHER ? level : 1;
    }

    public static BuildingType byId(String id) {
        for (BuildingType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return RESIDENCE;
    }
}
