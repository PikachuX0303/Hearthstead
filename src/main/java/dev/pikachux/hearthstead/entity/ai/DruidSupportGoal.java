package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Guards;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Skill;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * Druids throw regeneration potions at hurt colonists (themselves included) nearby. Runs
 * alongside everything else; it does not steer the druid.
 */
public class DruidSupportGoal extends Goal {
    /** How far a druid looks for hurt colonists. */
    public static final double RANGE = 10;
    /** Colonists below this share of their health get a potion. */
    public static final float HURT_SHARE = 0.6F;
    public static final int COOLDOWN = 100;

    private final CitizenEntity citizen;
    private int cooldown;

    public DruidSupportGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        CitizenData data = citizen.data();
        return data != null && data.job() == JobType.GUARD && Guards.kind(citizen.colony(), data) == Guards.Kind.DRUID
                && citizen.inventory().countItem(Items.NETHER_WART) > 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (--cooldown > 0 || !(citizen.level() instanceof ServerLevel level)) {
            return;
        }
        cooldown = 10;
        CitizenData data = citizen.data();
        CitizenEntity hurt = level.getEntitiesOfClass(CitizenEntity.class, citizen.getBoundingBox().inflate(RANGE),
                        other -> other.isAlive() && other.colony() == citizen.colony()
                                && other.getHealth() < other.getMaxHealth() * HURT_SHARE && citizen.hasLineOfSight(other))
                .stream()
                .min(Comparator.comparingDouble(other -> other.getHealth() / other.getMaxHealth()))
                .orElse(null);
        if (hurt == null || data == null) {
            return;
        }
        cooldown = COOLDOWN;
        GuardCasterGoal.throwSplash(level, citizen, hurt, Potions.REGENERATION);
        data.addExperience(Skill.KNOWLEDGE, 0.5);
    }
}
