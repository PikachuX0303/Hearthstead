package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.entity.CitizenEntity;

/**
 * Smelter: runs the furnaces in the smeltery, turning ores, raw metals and sand into
 * ingots and glass.
 */
public class SmelterGoal extends FurnaceWorkerGoal {
    public SmelterGoal(CitizenEntity citizen) {
        super(citizen, JobType.SMELTER, Logistics::isSmeltable, Logistics::isCookFuel, "Smelting",
                "Needs ore, raw metal or sand");
    }

    @Override
    protected java.util.function.Predicate<net.minecraft.world.item.ItemStack> input() {
        return HutSettings.smelterInput(building());
    }
}
