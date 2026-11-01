package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Skill;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Guards who have trained at the Archery shoot arrows from their pocket at enemies that are not
 * yet in sword reach. Runs alongside the melee attack: it does not steer the guard.
 */
public class GuardRangedGoal extends Goal {
    /** Marksmanship a guard needs before shooting. */
    public static final int MIN_MARKSMANSHIP = 5;

    private final CitizenEntity citizen;
    private int cooldown;

    public GuardRangedGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    /** Ticks between shots: from 45 down to 20 for a master archer. */
    public static int cooldown(int marksmanship) {
        return Math.max(20, 45 - marksmanship / 2);
    }

    /** Arrow damage: 2, plus 1 for every 10 levels of Marksmanship. */
    public static double damage(int marksmanship) {
        return 2 + marksmanship / 10.0;
    }

    @Override
    public boolean canUse() {
        CitizenData data = citizen.data();
        LivingEntity target = citizen.getTarget();
        // Knights who trained at the Archery; rangers and druids have their own goal.
        return data != null && data.job() == JobType.GUARD && data.skill(Skill.MARKSMANSHIP) >= MIN_MARKSMANSHIP
                && dev.pikachux.hearthstead.colony.Guards.kind(citizen.colony(), data) == dev.pikachux.hearthstead.colony.Guards.Kind.KNIGHT
                && target != null && target.isAlive() && citizen.inventory().countItem(Items.ARROW) > 0
                && citizen.distanceToSqr(target) > 16 && citizen.distanceToSqr(target) < 256;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = citizen.getTarget();
        CitizenData data = citizen.data();
        if (target == null || data == null || --cooldown > 0 || !citizen.hasLineOfSight(target)
                || !(citizen.level() instanceof ServerLevel level)) {
            return;
        }
        int marksmanship = data.skill(Skill.MARKSMANSHIP);
        cooldown = cooldown(marksmanship);
        Arrow arrow = new Arrow(level, citizen, new ItemStack(Items.ARROW), null);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.setBaseDamage(damage(marksmanship));
        double dx = target.getX() - citizen.getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - citizen.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.6F, Math.max(0F, 8F - marksmanship / 6F));
        level.addFreshEntity(arrow);
        citizen.getLookControl().setLookAt(target);
        citizen.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        citizen.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (citizen.getRandom().nextFloat() * 0.4F + 0.8F));
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            if (citizen.inventory().getItem(i).is(Items.ARROW)) {
                citizen.inventory().getItem(i).shrink(1);
                break;
            }
        }
        data.addExperience(Skill.MARKSMANSHIP, 0.5);
    }
}
