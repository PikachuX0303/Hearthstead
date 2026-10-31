package dev.pikachux.hearthstead.entity.raider;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Barbarian archer that shoots at citizens and players from a distance.
 */
public class ArcherBarbarianEntity extends AbstractBarbarian implements RangedAttackMob {
    public ArcherBarbarianEntity(EntityType<? extends AbstractBarbarian> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerAttackGoals() {
        goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0, 30, 15.0F));
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        setDropChance(EquipmentSlot.MAINHAND, 0.05F);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack bow = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack ammo = getProjectile(bow);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, power, bow);
        double dx = target.getX() - getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (level() instanceof ServerLevel serverLevel) {
            Projectile.spawnProjectileUsingShoot(arrow, serverLevel, ammo, dx, dy + horizontal * 0.2, dz, 1.6F,
                    10 - serverLevel.getDifficulty().getId() * 3);
        }
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
    }
}
