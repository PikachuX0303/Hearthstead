package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Crafters;
import dev.pikachux.hearthstead.colony.Crafting;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Mechanic: crafts redstone parts, rails, iron doors and bars, lanterns, torches, buckets and
 * shears with the game's crafting recipes, for whatever the colony's huts are short of (the
 * miners' torches, the cowhand's buckets, a builder's lanterns, ...).
 */
public class MechanicGoal extends WorkGoal {
    private static final int WORK_TICKS = 80;

    private int timer;

    public MechanicGoal(CitizenEntity citizen) {
        super(citizen, JobType.MECHANIC);
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos hut = hutPos();
        BuildingData building = building();
        Colony colony = citizen.colony();
        if (hut == null || building == null || colony == null) {
            return;
        }
        if (!walkTo(hut, 3)) {
            citizen.setStatus("Going to the workshop");
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the workshop to be built");
            return;
        }
        if (++timer < breakDelay(WORK_TICKS)) {
            if (timer % 10 == 0) {
                citizen.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        timer = 0;
        List<Container> storage = Storage.containers(level(), building);
        for (Item wanted : Logistics.shortages(level(), colony, Crafters.MECHANISMS::contains, storage)) {
            ItemStack made = Crafting.craftFromStorage(level(), storage, wanted);
            if (!made.isEmpty()) {
                level().playSound(null, hut, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6F, 1.0F);
                citizen.setStatus("Making " + made.getHoverName().getString());
                return;
            }
        }
        citizen.setStatus("Nothing to make right now");
    }
}
