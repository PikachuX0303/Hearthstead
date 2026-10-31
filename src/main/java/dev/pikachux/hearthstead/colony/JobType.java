package dev.pikachux.hearthstead.colony;

public enum JobType {
    UNEMPLOYED("unemployed", "Unemployed"),
    LUMBERJACK("lumberjack", "Lumberjack"),
    FARMER("farmer", "Farmer"),
    MINER("miner", "Miner"),
    GUARD("guard", "Guard"),
    BUILDER("builder", "Builder"),
    DELIVERYMAN("deliveryman", "Deliveryman"),
    COOK("cook", "Cook"),
    FISHERMAN("fisherman", "Fisherman"),
    BAKER("baker", "Baker"),
    SMELTER("smelter", "Smelter"),
    SHEPHERD("shepherd", "Shepherd"),
    RESEARCHER("researcher", "Researcher"),
    COMPOSTER("composter", "Composter"),
    SAWMILL("sawmill", "Sawmill Worker"),
    COWHAND("cowhand", "Cowhand"),
    CHICKEN_HERDER("chicken_herder", "Chicken Herder"),
    SWINEHERD("swineherd", "Swineherd"),
    STONEMASON("stonemason", "Stonemason"),
    FLORIST("florist", "Florist"),
    DYER("dyer", "Dyer"),
    BEEKEEPER("beekeeper", "Beekeeper"),
    RABBIT_HERDER("rabbit_herder", "Rabbit Herder"),
    GLASSBLOWER("glassblower", "Glassblower"),
    CONCRETE_MIXER("concrete_mixer", "Concrete Mixer");

    private final String id;
    private final String displayName;

    JobType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static JobType byId(String id) {
        for (JobType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return UNEMPLOYED;
    }
}
