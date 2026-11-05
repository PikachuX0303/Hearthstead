package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.Crafters;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Glasswork;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Glassblower: smelts sand into glass in the furnaces of its workshop, and makes the
 * glass panes and stained glass builders are waiting for.
 */
public class GlassblowerGoal extends FurnaceWorkerGoal {
    public GlassblowerGoal(CitizenEntity citizen) {
        super(citizen, JobType.GLASSBLOWER, Logistics::isSand, Logistics::isCookFuel, Status.of("blowing_glass"), Status.of("needs_sand"));
    }

    @Override
    protected void workAtHut(List<Container> storage) {
        Colony colony = citizen.colony();
        if (colony == null || !dev.pikachux.hearthstead.colony.HutSettings.isOn(building(), dev.pikachux.hearthstead.colony.HutSettings.GLASSWORK)) {
            return;
        }
        for (Item wanted : Logistics.shortages(level(), colony, Crafters::isGlassProduct, storage)) {
            ItemStack made = Glasswork.craft(level(), storage, wanted);
            if (!made.isEmpty()) {
                citizen.setStatus(Status.of("making", made.getHoverName()));
                return;
            }
        }
    }
}
