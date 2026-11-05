package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.entity.CitizenEntity;

/**
 * Stone smelter: runs the furnaces of the stone smeltery, turning cobblestone into stone, clay
 * into bricks and terracotta, netherrack into nether bricks and more.
 */
public class StoneSmelterGoal extends FurnaceWorkerGoal {
    public StoneSmelterGoal(CitizenEntity citizen) {
        super(citizen, JobType.STONE_SMELTER, Logistics::isStoneSmeltable, Logistics::isCookFuel, Status.of("smelting_stone"),
                Status.of("needs_stone_smelting_input"));
    }

    @Override
    protected java.util.function.Predicate<net.minecraft.world.item.ItemStack> input() {
        return dev.pikachux.hearthstead.colony.HutSettings.stoneSmelterInput(building());
    }
}
