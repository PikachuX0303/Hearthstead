package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;

/**
 * Swineherd: breeds the pigs around the hut with carrots, potatoes or beetroot, and once
 * the herd is full butchers an adult pig for pork with a sword or axe from the hut.
 */
public class SwineherdGoal extends HerderGoal<Pig> {
    public SwineherdGoal(CitizenEntity citizen) {
        super(citizen, JobType.SWINEHERD, Pig.class);
    }

    @Override
    protected boolean prepare() {
        return equipTool(Logistics::isButcherTool);
    }

    @Override
    protected boolean isBreedingFood(ItemStack stack) {
        return stack.is(ItemTags.PIG_FOOD);
    }

    @Override
    protected boolean canTend(Pig pig) {
        BuildingData building = building();
        return building != null && herdCount >= herdSize(building) && Logistics.isButcherTool(citizen.getMainHandItem())
                && !pig.isVehicle();
    }

    @Override
    protected boolean tend(Pig pig) {
        pig.hurtServer(level(), level().damageSources().mobAttack(citizen), Float.MAX_VALUE);
        citizen.getMainHandItem().hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
        herdCount--;
        return true;
    }

    @Override
    protected String workStatus() {
        return "Butchering a pig";
    }

    @Override
    protected String missingToolStatus() {
        return "Needs a sword or axe in the hut";
    }
}
