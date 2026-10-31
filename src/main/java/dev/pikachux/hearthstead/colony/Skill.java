package dev.pikachux.hearthstead.colony;

import java.util.Locale;

/**
 * A citizen's skills. Each job trains one of them while the citizen works, and a higher level in
 * it makes the citizen work faster. Guards train Combat and Marksmanship at the Combat Academy and
 * the Archery.
 */
public enum Skill {
    STRENGTH, DEXTERITY, AGILITY, KNOWLEDGE, ENDURANCE, COMBAT, MARKSMANSHIP;

    /** Highest level a skill can reach. */
    public static final int MAX_LEVEL = 50;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Experience needed to go from {@code level} to the next. */
    public static double experienceFor(int level) {
        return 40 + level * 8;
    }

    /** The skill a job trains and depends on, or null for the unemployed. */
    public static Skill primary(JobType job) {
        return switch (job) {
            case LUMBERJACK, MINER, BUILDER, QUARRIER, CRUSHER, NETHERWORKER -> STRENGTH;
            case SAWMILL, STONEMASON, DYER, GLASSBLOWER, CONCRETE_MIXER, MECHANIC, BLACKSMITH, FLETCHER, COOK, BAKER -> DEXTERITY;
            case DELIVERYMAN, FISHERMAN, SHEPHERD, COWHAND, CHICKEN_HERDER, SWINEHERD, RABBIT_HERDER, BEEKEEPER -> AGILITY;
            case RESEARCHER, ENCHANTER, ALCHEMIST, HEALER, LIBRARIAN -> KNOWLEDGE;
            case FARMER, PLANTER, COMPOSTER, FLORIST, SIFTER, SMELTER, STONE_SMELTER -> ENDURANCE;
            case GUARD -> COMBAT;
            case UNEMPLOYED -> null;
        };
    }
}
