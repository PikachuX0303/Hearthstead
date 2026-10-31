package dev.pikachux.hearthstead.entity.raider;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;

/**
 * Axe-wielding barbarian, the backbone of every raid.
 */
public class BarbarianEntity extends AbstractBarbarian {
    public BarbarianEntity(EntityType<? extends AbstractBarbarian> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerAttackGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextInt(4) == 0 ? Items.IRON_AXE : Items.STONE_AXE));
        setDropChance(EquipmentSlot.MAINHAND, 0.05F);
    }
}
