package dev.pikachux.hearthstead.entity.ai;

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
    private static final int PLANKS_STOCK = 64;
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
            citizen.setStatus("Going to the sawmill");
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the sawmill to be built");
            return;
        }
        List<Container> storage = Storage.containers(level(), building);
        if (Storage.count(storage, s -> s.is(ItemTags.LOGS)) == 0) {
            citizen.setStatus("Needs logs");
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
        for (Item wanted : Logistics.builderShortages(level(), colony,
                item -> item.getDefaultInstance().is(ItemTags.PLANKS) || isWooden(item))) {
            made = Woodworking.craft(level(), storage, wanted);
            if (!made.isEmpty()) {
                break;
            }
        }
        if (made.isEmpty() && warehousePlanks(colony) < PLANKS_STOCK) {
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
            citizen.setStatus("Nothing to saw right now");
            return;
        }
        citizen.setStatus("Sawing " + made.getHoverName().getString());
        level().playSound(null, hut, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    private static boolean isWooden(Item item) {
        ItemStack stack = item.getDefaultInstance();
        return stack.is(ItemTags.WOODEN_STAIRS) || stack.is(ItemTags.WOODEN_SLABS) || stack.is(ItemTags.WOODEN_DOORS)
                || stack.is(ItemTags.WOODEN_TRAPDOORS) || stack.is(ItemTags.WOODEN_FENCES) || stack.is(ItemTags.FENCE_GATES)
                || stack.is(net.minecraft.world.item.Items.STICK) || stack.is(ModBlocks.ROPE_FENCE.asItem());
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
