package dev.pikachux.hearthstead.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Persistent data of a colonist. The entity in the world is only a view on this data:
 * if it dies or goes missing the colony can respawn it.
 */
public class CitizenData {
    private static final String[] FIRST_NAMES = {
            "Ada", "Bram", "Cora", "Dain", "Elin", "Finn", "Greta", "Hugo", "Ines", "Jory",
            "Kara", "Lars", "Mira", "Nils", "Orla", "Piet", "Rhea", "Sven", "Tilda", "Ulf",
            "Vera", "Wim", "Yara", "Zeno"
    };
    private static final String[] LAST_NAMES = {
            "Ashdown", "Birch", "Cooper", "Dale", "Fletcher", "Glenn", "Hollow", "Miller",
            "Thatcher", "Stone", "Brook", "Wheeler", "Smith", "Fairweather", "Marsh", "Hayward"
    };

    public static final double MAX_SATURATION = 20.0;

    private final int id;
    private String name;
    private boolean female;
    private int skin;
    @Nullable
    private UUID entityUuid;
    private JobType job = JobType.UNEMPLOYED;
    @Nullable
    private BlockPos workPos;
    @Nullable
    private BlockPos homePos;
    private double saturation = MAX_SATURATION;
    private double happiness = 5.0;
    private net.minecraft.network.chat.Component status = dev.pikachux.hearthstead.entity.Status.IDLE;
    /** Children grow up after {@link #GROW_UP_SECONDS}; they do not work. */
    private boolean child;
    private int ageSeconds;
    /** Visitors stay at the tavern until recruited for {@link #recruitCost()} or until they leave. */
    private boolean visitor;
    @org.jetbrains.annotations.Nullable
    private Illness illness;
    private long illnessDay;
    /** Game time a netherworker's trip ends, or 0 when at home. */
    private long awayUntil;
    /** Day a visitor comes back from a tavern expedition, or 0 when not on one. */
    private long expeditionReturnDay;
    private boolean expeditionArmed;
    private long arrivalDay;
    private String recruitItem = "";
    private int recruitCount;
    private final int[] skillLevels = new int[Skill.values().length];
    private final double[] skillExperience = new double[Skill.values().length];
    /** Game time at which the entity last reported in. */
    private transient long lastSeen;

    public CitizenData(int id) {
        this.id = id;
    }

    public static CitizenData create(int id, RandomSource random) {
        CitizenData data = new CitizenData(id);
        data.name = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + " " + LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        data.female = random.nextBoolean();
        data.skin = random.nextInt(9);
        // Settlers arrive with a little of everything; Marksmanship is only learned at the Archery.
        for (Skill skill : Skill.values()) {
            data.skillLevels[skill.ordinal()] = skill == Skill.MARKSMANSHIP ? 1 : 1 + random.nextInt(10);
        }
        return data;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean female() {
        return female;
    }

    public int skin() {
        return skin;
    }

    @Nullable
    public UUID entityUuid() {
        return entityUuid;
    }

    public void setEntityUuid(@Nullable UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public JobType job() {
        return job;
    }

    @Nullable
    public BlockPos workPos() {
        return workPos;
    }

    public void assignJob(JobType job, @Nullable BlockPos workPos) {
        this.job = job;
        this.workPos = workPos == null ? null : workPos.immutable();
    }

    @Nullable
    public BlockPos homePos() {
        return homePos;
    }

    public void setHomePos(@Nullable BlockPos homePos) {
        this.homePos = homePos == null ? null : homePos.immutable();
    }

    public double saturation() {
        return saturation;
    }

    public void setSaturation(double saturation) {
        this.saturation = Math.max(0, Math.min(MAX_SATURATION, saturation));
    }

    public double happiness() {
        return happiness;
    }

    public void setHappiness(double happiness) {
        this.happiness = Math.max(0, Math.min(10, happiness));
    }

    /** The status line in the server's language (English on a dedicated server); for logs and tests. */
    public String status() {
        return status.getString();
    }

    /** The status line, translated by each player's game. */
    public net.minecraft.network.chat.Component statusComponent() {
        return status;
    }

    public void setStatus(net.minecraft.network.chat.Component status) {
        this.status = status;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isHungry() {
        return saturation < MAX_SATURATION / 2;
    }

    /** Seconds a child needs to grow up: one in-game day (half with a school). */
    public static final int GROW_UP_SECONDS = 1200;

    /** A newborn of the colony, taking the family name from a parent. */
    public static CitizenData createChild(int id, RandomSource random, String familyName) {
        CitizenData data = create(id, random);
        data.name = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + " " + familyName;
        data.child = true;
        for (Skill skill : Skill.values()) {
            data.skillLevels[skill.ordinal()] = 1;
        }
        return data;
    }

    /** A traveller who stays at the tavern: more skilled than settlers, but asks for something to join. */
    public static CitizenData createVisitor(int id, RandomSource random, long day) {
        CitizenData data = create(id, random);
        data.visitor = true;
        data.arrivalDay = day;
        int best = 0;
        for (Skill skill : Skill.values()) {
            int level = skill == Skill.MARKSMANSHIP ? 1 + random.nextInt(5) : 5 + random.nextInt(16);
            data.skillLevels[skill.ordinal()] = level;
            best = Math.max(best, level);
        }
        // The better the visitor, the more they ask for.
        if (best < 12) {
            data.recruitItem = "minecraft:bread";
            data.recruitCount = 8 + random.nextInt(9);
        } else if (best < 17) {
            data.recruitItem = "minecraft:iron_ingot";
            data.recruitCount = 3 + random.nextInt(4);
        } else {
            data.recruitItem = "minecraft:emerald";
            data.recruitCount = 1 + random.nextInt(3);
        }
        return data;
    }

    public String familyName() {
        int space = name.lastIndexOf(' ');
        return space < 0 ? name : name.substring(space + 1);
    }

    public boolean child() {
        return child;
    }

    public int ageSeconds() {
        return ageSeconds;
    }

    /** Ages a child; returns true when it has just grown up. */
    public boolean grow(int seconds) {
        if (!child) {
            return false;
        }
        ageSeconds += seconds;
        if (ageSeconds >= GROW_UP_SECONDS) {
            child = false;
            return true;
        }
        return false;
    }

    public boolean visitor() {
        return visitor;
    }

    /** The illness the citizen has, or null when healthy. */
    @org.jetbrains.annotations.Nullable
    public Illness illness() {
        return illness;
    }

    public boolean sick() {
        return illness != null;
    }

    /** The day the citizen fell ill. */
    public long illnessDay() {
        return illnessDay;
    }

    public void fallIll(Illness illness, long day) {
        this.illness = illness;
        this.illnessDay = day;
    }

    public void cure() {
        illness = null;
    }

    public long awayUntil() {
        return awayUntil;
    }

    public void setAwayUntil(long gameTime) {
        this.awayUntil = gameTime;
    }

    /** Whether the citizen is away on a tavern expedition (and so not in the world). */
    public boolean onExpedition() {
        return expeditionReturnDay > 0;
    }

    public long expeditionReturnDay() {
        return expeditionReturnDay;
    }

    public boolean expeditionArmed() {
        return expeditionArmed;
    }

    public void startExpedition(long returnDay, boolean armed) {
        this.expeditionReturnDay = returnDay;
        this.expeditionArmed = armed;
    }

    public void endExpedition() {
        expeditionReturnDay = 0;
        expeditionArmed = false;
    }

    public long arrivalDay() {
        return arrivalDay;
    }

    public void setArrivalDay(long day) {
        this.arrivalDay = day;
    }

    /** What the visitor asks for to join: an item id and count. */
    public net.minecraft.world.item.ItemStack recruitCost() {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(
                recruitItem.isEmpty() ? "minecraft:bread" : recruitItem));
        return new net.minecraft.world.item.ItemStack(item, Math.max(1, recruitCount));
    }

    /** The visitor joins the colony as an ordinary citizen. */
    public void recruit() {
        visitor = false;
        homePos = null;
    }

    public int skill(Skill skill) {
        return Math.max(1, skillLevels[skill.ordinal()]);
    }

    public void setSkill(Skill skill, int level) {
        skillLevels[skill.ordinal()] = Math.max(1, Math.min(Skill.MAX_LEVEL, level));
        skillExperience[skill.ordinal()] = 0;
    }

    /** Experience towards the next level of a skill, from 0 to 1. */
    public double skillProgress(Skill skill) {
        int level = skill(skill);
        return level >= Skill.MAX_LEVEL ? 1 : skillExperience[skill.ordinal()] / Skill.experienceFor(level);
    }

    /** Adds experience to a skill; returns true if it went up a level. */
    public boolean addExperience(Skill skill, double amount) {
        int index = skill.ordinal();
        int before = skill(skill);
        skillLevels[index] = before;
        skillExperience[index] += amount;
        while (skillLevels[index] < Skill.MAX_LEVEL && skillExperience[index] >= Skill.experienceFor(skillLevels[index])) {
            skillExperience[index] -= Skill.experienceFor(skillLevels[index]);
            skillLevels[index]++;
        }
        if (skillLevels[index] >= Skill.MAX_LEVEL) {
            skillExperience[index] = 0;
        }
        return skillLevels[index] > before;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("name", name);
        tag.putBoolean("female", female);
        tag.putInt("skin", skin);
        if (entityUuid != null) {
            tag.putString("entity", entityUuid.toString());
        }
        tag.putString("job", job.id());
        if (workPos != null) {
            tag.putLong("work", workPos.asLong());
        }
        if (homePos != null) {
            tag.putLong("home", homePos.asLong());
        }
        tag.putDouble("saturation", saturation);
        tag.putDouble("happiness", happiness);
        CompoundTag skills = new CompoundTag();
        for (Skill skill : Skill.values()) {
            skills.putInt(skill.id(), skill(skill));
            skills.putDouble(skill.id() + "_xp", skillExperience[skill.ordinal()]);
        }
        tag.put("skills", skills);
        if (child) {
            tag.putBoolean("child", true);
            tag.putInt("age", ageSeconds);
        }
        if (awayUntil > 0) {
            tag.putLong("awayUntil", awayUntil);
        }
        if (expeditionReturnDay > 0) {
            tag.putLong("expeditionReturnDay", expeditionReturnDay);
            tag.putBoolean("expeditionArmed", expeditionArmed);
        }
        if (illness != null) {
            tag.putString("illness", illness.id());
            tag.putLong("illnessDay", illnessDay);
        }
        if (visitor) {
            tag.putBoolean("visitor", true);
            tag.putLong("arrivalDay", arrivalDay);
            tag.putString("recruitItem", recruitItem);
            tag.putInt("recruitCount", recruitCount);
        }
        return tag;
    }

    public static CitizenData load(CompoundTag tag) {
        CitizenData data = new CitizenData(tag.getIntOr("id", 0));
        data.name = tag.getStringOr("name", "Citizen");
        data.female = tag.getBooleanOr("female", false);
        data.skin = tag.getIntOr("skin", 0);
        tag.getString("entity").ifPresent(s -> data.entityUuid = UUID.fromString(s));
        data.job = JobType.byId(tag.getStringOr("job", ""));
        tag.getLong("work").ifPresent(l -> data.workPos = BlockPos.of(l));
        tag.getLong("home").ifPresent(l -> data.homePos = BlockPos.of(l));
        data.saturation = tag.getDoubleOr("saturation", MAX_SATURATION);
        data.happiness = tag.getDoubleOr("happiness", 5.0);
        // Citizens from before skills existed start at a middling 5 in everything.
        CompoundTag skills = tag.getCompoundOrEmpty("skills");
        for (Skill skill : Skill.values()) {
            data.skillLevels[skill.ordinal()] = skills.getIntOr(skill.id(), skill == Skill.MARKSMANSHIP ? 1 : 5);
            data.skillExperience[skill.ordinal()] = skills.getDoubleOr(skill.id() + "_xp", 0);
        }
        data.child = tag.getBooleanOr("child", false);
        data.ageSeconds = tag.getIntOr("age", 0);
        data.visitor = tag.getBooleanOr("visitor", false);
        data.illness = Illness.byId(tag.getStringOr("illness", ""));
        data.awayUntil = tag.getLongOr("awayUntil", 0);
        data.expeditionReturnDay = tag.getLongOr("expeditionReturnDay", 0);
        data.expeditionArmed = tag.getBooleanOr("expeditionArmed", false);
        data.illnessDay = tag.getLongOr("illnessDay", 0);
        data.arrivalDay = tag.getLongOr("arrivalDay", 0);
        data.recruitItem = tag.getStringOr("recruitItem", "");
        data.recruitCount = tag.getIntOr("recruitCount", 0);
        return data;
    }
}
