package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.entity.CitizenEntity;

/**
 * Stone smelter: runs the furnaces of the stone smeltery, turning cobblestone into stone, clay
 * into bricks and terracotta, netherrack into nether bricks and more.
 */
public class StoneSmelterGoal extends FurnaceWorkerGoal {
    public StoneSmelterGoal(CitizenEntity citizen) {
        super(citizen, JobType.STONE_SMELTER, Logistics::isStoneSmeltable, Logistics::isCookFuel, "Smelting stone",
                "Needs cobblestone, clay or netherrack");
    }
}
