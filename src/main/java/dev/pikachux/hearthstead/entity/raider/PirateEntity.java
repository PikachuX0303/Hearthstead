package dev.pikachux.hearthstead.entity.raider;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A pirate with a cutlass: lands with the boats when a colony near water is raided.
 */
public class PirateEntity extends BarbarianEntity {
    public PirateEntity(EntityType<? extends AbstractBarbarian> type, Level level) {
        super(type, level);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextInt(4) == 0 ? Items.IRON_SWORD : Items.STONE_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.05F);
    }
}
