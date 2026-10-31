package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.item.ItemStack;

/**
 * Rabbit herder: breeds the rabbits around the hutch with carrots or dandelions, and once
 * the warren is full takes an adult rabbit for meat and hide with a sword or axe.
 */
public class RabbitHerderGoal extends HerderGoal<Rabbit> {
    public RabbitHerderGoal(CitizenEntity citizen) {
        super(citizen, JobType.RABBIT_HERDER, Rabbit.class);
    }

    @Override
    protected boolean prepare() {
        return equipTool(Logistics::isButcherTool);
    }

    @Override
    protected boolean isBreedingFood(ItemStack stack) {
        return Logistics.isRabbitFood(stack);
    }

    @Override
    protected boolean canTend(Rabbit rabbit) {
        BuildingData building = building();
        return building != null && herdCount >= herdSize(building) && Logistics.isButcherTool(citizen.getMainHandItem());
    }

    @Override
    protected boolean tend(Rabbit rabbit) {
        rabbit.hurtServer(level(), level().damageSources().mobAttack(citizen), Float.MAX_VALUE);
        citizen.getMainHandItem().hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
        herdCount--;
        return true;
    }

    @Override
    protected String workStatus() {
        return "Culling the warren";
    }

    @Override
    protected String missingToolStatus() {
        return "Needs a sword or axe in the hut";
    }
}
