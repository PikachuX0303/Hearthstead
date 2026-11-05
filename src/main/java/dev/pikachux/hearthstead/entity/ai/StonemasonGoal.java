package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.Crafters;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.colony.Masonry;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Stonemason: cuts stone from the hut into the stone parts builders are waiting for and
 * the warehouse cannot supply.
 */
public class StonemasonGoal extends WorkGoal {
    private static final int WORK_TICKS = 60;

    private int timer;

    public StonemasonGoal(CitizenEntity citizen) {
        super(citizen, JobType.STONEMASON);
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
            citizen.setStatus(Status.of("going_workshop"));
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus(Status.of("waiting_workshop_be_built"));
            return;
        }
        List<Container> storage = Storage.containers(level(), building);
        if (Storage.count(storage, Logistics::isMasonry) == 0) {
            citizen.setStatus(Status.of("needs_stone_or_cobblestone"));
            timer = 0;
            return;
        }
        if (++timer < breakDelay(WORK_TICKS)) {
            if (timer % 10 == 0) {
                citizen.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        timer = 0;
        for (Item wanted : Logistics.shortages(level(), colony, Crafters.masonry(level())::contains, storage)) {
            ItemStack made = Masonry.craft(level(), storage, wanted);
            if (!made.isEmpty()) {
                citizen.setStatus(Status.of("cutting", made.getHoverName()));
                level().playSound(null, hut, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.6F, 0.8F);
                return;
            }
        }
        // Spare stone bricks for the warehouse, if the hut is set to keep some.
        int spare = dev.pikachux.hearthstead.colony.HutSettings.spareBricks(building);
        if (spare > 0 && warehouseCount(colony, net.minecraft.world.item.Items.STONE_BRICKS) < spare) {
            ItemStack made = Masonry.craft(level(), storage, net.minecraft.world.item.Items.STONE_BRICKS);
            if (!made.isEmpty()) {
                citizen.setStatus(Status.of("cutting", made.getHoverName()));
                level().playSound(null, hut, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.6F, 0.8F);
                return;
            }
        }
        citizen.setStatus(Status.of("nothing_cut_right_now"));
    }

    private int warehouseCount(Colony colony, net.minecraft.world.item.Item item) {
        int count = 0;
        for (BuildingData building : colony.buildings()) {
            if (building.type() == dev.pikachux.hearthstead.colony.BuildingType.WAREHOUSE) {
                count += Storage.count(Storage.containers(level(), building), s -> s.is(item));
            }
        }
        return count;
    }
}
