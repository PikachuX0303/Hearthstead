package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.entity.CitizenEntity;

/**
 * Cook: runs the smokers in the kitchen with raw food and fuel from the hut and stores
 * the meals in the hut, where citizens can eat them.
 */
public class CookGoal extends FurnaceWorkerGoal {
    public CookGoal(CitizenEntity citizen) {
        super(citizen, JobType.COOK, Logistics::isRawFood, Logistics::isCookFuel, Status.of("cooking"), Status.of("needs_raw_food"));
    }

    @Override
    protected java.util.function.Predicate<net.minecraft.world.item.ItemStack> input() {
        return HutSettings.cookInput(building());
    }
}
