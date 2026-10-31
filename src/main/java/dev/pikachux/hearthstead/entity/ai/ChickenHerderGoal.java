package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Chicken herder: breeds the chickens around the hut with seeds and gathers the eggs and
 * feathers they leave lying around.
 */
public class ChickenHerderGoal extends HerderGoal<Chicken> {
    public ChickenHerderGoal(CitizenEntity citizen) {
        super(citizen, JobType.CHICKEN_HERDER, Chicken.class);
    }

    @Override
    protected boolean prepare() {
        return true;
    }

    @Override
    protected boolean isBreedingFood(ItemStack stack) {
        return stack.is(ItemTags.CHICKEN_FOOD);
    }

    @Override
    protected boolean isLoot(ItemStack stack) {
        return stack.is(Items.EGG) || stack.is(Items.BROWN_EGG) || stack.is(Items.BLUE_EGG) || stack.is(Items.FEATHER);
    }

    /** Chickens need no tending beyond feeding; their produce is gathered from the ground. */
    @Override
    protected boolean canTend(Chicken chicken) {
        return false;
    }

    @Override
    protected boolean tend(Chicken chicken) {
        return false;
    }

    @Override
    protected String workStatus() {
        return "Gathering eggs";
    }

    @Override
    protected String missingToolStatus() {
        return "Tending the chickens";
    }
}
