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
    private String status = "Idle";
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

    public String status() {
        return status;
    }

    public void setStatus(String status) {
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
        return data;
    }
}
