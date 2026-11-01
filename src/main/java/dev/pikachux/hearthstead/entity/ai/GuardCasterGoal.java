package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Guards;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Skill;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.EnumSet;

/**
 * How rangers and druids fight: they keep their distance from the enemy, closing in when it is
 * too far or out of sight and backing off when it comes too close, and meanwhile shoot arrows
 * (rangers, with a bow) or throw harming potions (druids, brewed from nether wart; undead get a
 * healing potion, which hurts them). Without their gear they fight in melee like knights.
 */
public class GuardCasterGoal extends Goal {
    /** Distance a ranger or druid tries to keep from the enemy. */
    public static final double MIN_RANGE = 5;
    public static final double MAX_RANGE = 14;
    /** Potions a druid brews from one nether wart. */
    public static final int POTIONS_PER_WART = 4;

    private final CitizenEntity citizen;
    private int cooldown;
    private int brewed;

    public GuardCasterGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Ticks between a ranger's shots: from 30 down to 15 for a master archer. */
    public static int arrowCooldown(int marksmanship) {
        return Math.max(15, 30 - marksmanship * 3 / 10);
    }

    /** A ranger's arrow damage: 3, plus 1 for every 10 levels of Marksmanship. */
    public static double arrowDamage(int marksmanship) {
        return 3 + marksmanship / 10.0;
    }

    /** Ticks between a druid's potions: from 70 down to 35 with Knowledge. */
    public static int potionCooldown(int knowledge) {
        return Math.max(35, 70 - knowledge * 7 / 10);
    }

    /** Whether this guard fights from a distance right now (has the gear for it). */
    public static boolean fightsAtRange(CitizenEntity citizen) {
        CitizenData data = citizen.data();
        if (data == null || data.job() != JobType.GUARD) {
            return false;
        }
        return switch (Guards.kind(citizen.colony(), data)) {
            case RANGER -> citizen.getMainHandItem().is(Items.BOW) && citizen.inventory().countItem(Items.ARROW) > 0;
            case DRUID -> citizen.inventory().countItem(Items.NETHER_WART) > 0;
            case KNIGHT -> false;
        };
    }

    @Override
    public boolean canUse() {
        LivingEntity target = citizen.getTarget();
        return target != null && target.isAlive() && fightsAtRange(citizen);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = citizen.getTarget();
        CitizenData data = citizen.data();
        if (target == null || data == null || !(citizen.level() instanceof ServerLevel level)) {
            return;
        }
        citizen.getLookControl().setLookAt(target, 30F, 30F);
        double distance = citizen.distanceTo(target);
        boolean visible = citizen.getSensing().hasLineOfSight(target);
        if (distance > MAX_RANGE || !visible) {
            citizen.getNavigation().moveTo(target, 1.0);
        } else if (distance < MIN_RANGE) {
            // Step back, away from the enemy.
            double dx = citizen.getX() - target.getX();
            double dz = citizen.getZ() - target.getZ();
            double length = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            citizen.getNavigation().moveTo(citizen.getX() + dx / length * 4, citizen.getY(), citizen.getZ() + dz / length * 4, 1.2);
        } else {
            citizen.getNavigation().stop();
        }
        if (--cooldown > 0 || !visible || distance > MAX_RANGE + 4) {
            return;
        }
        if (Guards.kind(citizen.colony(), data) == Guards.Kind.RANGER) {
            shoot(level, target, data);
        } else {
            throwPotion(level, target, data);
        }
    }

    private void shoot(ServerLevel level, LivingEntity target, CitizenData data) {
        int marksmanship = data.skill(Skill.MARKSMANSHIP);
        cooldown = arrowCooldown(marksmanship);
        Arrow arrow = new Arrow(level, citizen, new ItemStack(Items.ARROW), citizen.getMainHandItem());
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.setBaseDamage(arrowDamage(marksmanship));
        double dx = target.getX() - citizen.getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - citizen.getZ();
        arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.6F, Math.max(0F, 6F - marksmanship / 8F));
        level.addFreshEntity(arrow);
        citizen.swing(InteractionHand.MAIN_HAND);
        citizen.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (citizen.getRandom().nextFloat() * 0.4F + 0.8F));
        use(Items.ARROW);
        data.addExperience(Skill.MARKSMANSHIP, 0.5);
    }

    private void throwPotion(ServerLevel level, LivingEntity target, CitizenData data) {
        cooldown = potionCooldown(data.skill(Skill.KNOWLEDGE));
        // Instant damage heals the undead and instant health hurts them.
        throwSplash(level, citizen, target, target.isInvertedHealAndHarm() ? Potions.HEALING : Potions.HARMING);
        if (++brewed >= POTIONS_PER_WART) {
            brewed = 0;
            use(Items.NETHER_WART);
        }
        data.addExperience(Skill.KNOWLEDGE, 0.5);
    }

    /** Throws a splash potion from the citizen at an entity. */
    static void throwSplash(ServerLevel level, CitizenEntity citizen, LivingEntity target, Holder<Potion> potion) {
        ItemStack stack = PotionContents.createItemStack(Items.SPLASH_POTION, potion);
        ThrownSplashPotion thrown = new ThrownSplashPotion(level, citizen, stack);
        double dx = target.getX() - citizen.getX();
        double dy = target.getEyeY() - 1.1 - citizen.getEyeY();
        double dz = target.getZ() - citizen.getZ();
        thrown.setXRot(thrown.getXRot() + 20.0F);
        thrown.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 0.75F, 4.0F);
        level.addFreshEntity(thrown);
        citizen.swing(InteractionHand.MAIN_HAND);
        citizen.playSound(SoundEvents.WITCH_THROW, 1.0F, 0.8F + citizen.getRandom().nextFloat() * 0.4F);
    }

    private void use(net.minecraft.world.item.Item item) {
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            if (citizen.inventory().getItem(i).is(item)) {
                citizen.inventory().getItem(i).shrink(1);
                return;
            }
        }
    }
}
