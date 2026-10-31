package dev.pikachux.hearthstead.entity.raider;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The pirate captain: leads a pirate raid, as tough as a barbarian chief.
 */
public class PirateCaptainEntity extends ChiefBarbarianEntity {
    public PirateCaptainEntity(EntityType<? extends AbstractBarbarian> type, Level level) {
        super(type, level);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.1F);
    }
}
