package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.Crafters;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

import java.util.function.Predicate;

/**
 * Mechanic: crafts redstone parts, rails, iron doors and bars, lanterns, torches, buckets and
 * shears with the game's crafting recipes, for whatever the colony's huts are short of (the
 * miners' torches, the cowhand's buckets, a builder's lanterns, ...).
 */
public class MechanicGoal extends RecipeCrafterGoal {
    public MechanicGoal(CitizenEntity citizen) {
        super(citizen, JobType.MECHANIC);
    }

    @Override
    protected Predicate<Item> products() {
        return Crafters.MECHANISMS::contains;
    }

    @Override
    protected SoundEvent workSound() {
        return SoundEvents.SMITHING_TABLE_USE;
    }
}
