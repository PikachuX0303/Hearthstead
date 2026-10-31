package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.Crafters;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

import java.util.function.Predicate;

/**
 * Fletcher: makes bows, crossbows, arrows, fishing rods and leads for the huts that ask for them
 * (the fisherman's rod, for example).
 */
public class FletcherGoal extends RecipeCrafterGoal {
    public FletcherGoal(CitizenEntity citizen) {
        super(citizen, JobType.FLETCHER);
    }

    @Override
    protected Predicate<Item> products() {
        return Crafters.FLETCHING::contains;
    }

    @Override
    protected SoundEvent workSound() {
        return SoundEvents.VILLAGER_WORK_FLETCHER;
    }
}
