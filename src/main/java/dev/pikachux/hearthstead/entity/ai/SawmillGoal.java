package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.Crafters;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.colony.Woodworking;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import dev.pikachux.hearthstead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Sawmill worker: saws logs from the sawmill into the wooden parts builders are waiting
 * for (planks, sticks, slabs, stairs, doors, trapdoors, fences, gates). With nothing
 * requested it keeps the warehouse stocked with planks.
 */
public class SawmillGoal extends WorkGoal {
    private static final int WORK_TICKS = 60;

    private int timer;

    public SawmillGoal(CitizenEntity citizen) {
        super(citizen, JobType.SAWMILL);
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
            citizen.setStatus(Status.of("going_sawmill"));
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus(Status.of("waiting_sawmill_be_built"));
            return;
        }
        List<Container> storage = Storage.containers(level(), building);
        if (Storage.count(storage, s -> s.is(ItemTags.LOGS)) == 0 && building.recipes().isEmpty()) {
            citizen.setStatus(Status.of("needs_logs"));
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
        ItemStack made = ItemStack.EMPTY;
        java.util.function.Predicate<Item> taught = Crafters.taught(building);
        for (Item wanted : Logistics.shortages(level(), colony, ((java.util.function.Predicate<Item>) Crafters::isWoodwork).or(taught), storage)) {
            made = Crafters.isWoodwork(wanted) ? Woodworking.craft(level(), storage, wanted)
                    : dev.pikachux.hearthstead.colony.Crafting.craftFromStorage(level(), storage, wanted);
            if (!made.isEmpty()) {
                break;
            }
        }
        // Taught recipes with a stock to keep.
        for (var recipe : building.recipes().entrySet()) {
            Item item = Logistics.postboxItem(recipe.getKey());
            if (made.isEmpty() && item != null && Logistics.belowStock(level(), colony, building, item, recipe.getValue())) {
                made = Crafters.isWoodwork(item) ? Woodworking.craft(level(), storage, item)
                        : dev.pikachux.hearthstead.colony.Crafting.craftFromStorage(level(), storage, item);
            }
        }
        if (made.isEmpty() && warehousePlanks(colony) < dev.pikachux.hearthstead.colony.HutSettings.sparePlanks(building)) {
            for (Container container : storage) {
                for (int i = 0; i < container.getContainerSize() && made.isEmpty(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (stack.is(ItemTags.LOGS)) {
                        made = Woodworking.craft(level(), storage, Woodworking.planksFrom(level(), stack.getItem()).getItem());
                    }
                }
            }
        }
        if (made.isEmpty()) {
            citizen.setStatus(Status.of("nothing_saw_right_now"));
            return;
        }
        citizen.setStatus(Status.of("sawing", made.getHoverName()));
        level().playSound(null, hut, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    private int warehousePlanks(Colony colony) {
        int planks = 0;
        for (BuildingData building : colony.buildings()) {
            if (building.type() == BuildingType.WAREHOUSE) {
                planks += Storage.count(Storage.containers(level(), building), s -> s.is(ItemTags.PLANKS));
            }
        }
        return planks;
    }
}
