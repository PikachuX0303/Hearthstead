package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shepherd: shears the sheep around the hut with shears from the hut and breeds them
 * with wheat while the flock is smaller than the hut allows.
 */
public class ShepherdGoal extends HerderGoal<Sheep> {
    public ShepherdGoal(CitizenEntity citizen) {
        super(citizen, JobType.SHEPHERD, Sheep.class);
    }

    @Override
    protected boolean prepare() {
        return equipTool(s -> s.is(Items.SHEARS));
    }

    @Override
    protected boolean isLoot(ItemStack stack) {
        return stack.is(ItemTags.WOOL);
    }

    @Override
    protected boolean canTend(Sheep sheep) {
        return sheep.readyForShearing() && citizen.getMainHandItem().is(Items.SHEARS);
    }

    @Override
    protected boolean tend(Sheep sheep) {
        sheep.shear(level(), SoundSource.NEUTRAL, citizen.getMainHandItem());
        citizen.getMainHandItem().hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    protected String workStatus() {
        return "Shearing sheep";
    }

    @Override
    protected String missingToolStatus() {
        return "Needs shears in the hut";
    }
}
