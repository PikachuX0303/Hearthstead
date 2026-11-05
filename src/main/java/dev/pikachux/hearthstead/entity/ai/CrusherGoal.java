package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Crafters;
import dev.pikachux.hearthstead.colony.HutSettings;
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
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Crusher: crushes cobblestone into gravel or sand. Makes what other huts are short of first
 * (the glassblower's or concrete mixer's sand, for example), otherwise what the hut's setting
 * asks for, until the warehouse holds a stack of it.
 */
public class CrusherGoal extends WorkGoal {
    private static final int WORK_TICKS = 60;
    private static final int STOCK = 64;

    private int timer;

    public CrusherGoal(CitizenEntity citizen) {
        super(citizen, JobType.CRUSHER);
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
            citizen.setStatus(Status.of("going_crusher"));
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus(Status.of("waiting_hut_be_built"));
            return;
        }
        List<Container> storage = Storage.containers(level(), building);
        if (Storage.count(storage, s -> s.is(Items.COBBLESTONE)) == 0) {
            citizen.setStatus(Status.of("needs_cobblestone"));
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
        Item made = null;
        for (Item wanted : Logistics.shortages(level(), colony, Crafters.products(level(), BuildingType.CRUSHER), storage)) {
            made = wanted;
            break;
        }
        if (made == null) {
            Item chosen = "sand".equals(HutSettings.get(building, HutSettings.CRUSH)) ? Items.SAND : Items.GRAVEL;
            if (warehouseCount(colony, chosen) < STOCK) {
                made = chosen;
            }
        }
        if (made == null) {
            citizen.setStatus(Status.of("nothing_crush_right_now"));
            return;
        }
        Storage.extract(storage, s -> s.is(Items.COBBLESTONE), 1);
        ItemStack rest = Storage.insert(storage, new ItemStack(made));
        if (!rest.isEmpty()) {
            storeInHut(rest);
        }
        level().playSound(null, hut, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6F, 0.8F);
        citizen.setStatus(Status.of("crushing_into", made.getDefaultInstance().getHoverName()));
    }

    private int warehouseCount(Colony colony, Item item) {
        List<Container> warehouse = new ArrayList<>();
        for (BuildingData building : colony.buildings()) {
            if (building.type() == BuildingType.WAREHOUSE) {
                warehouse.addAll(Storage.containers(level(), building));
            }
        }
        return Storage.count(warehouse, s -> s.is(item));
    }
}
