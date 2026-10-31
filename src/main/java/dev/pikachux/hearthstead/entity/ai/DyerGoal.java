package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Dyeing;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
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
 * Dyer: makes the dyes, coloured wool and beds builders are waiting for. With nothing
 * requested, it turns the flowers in the hut into dyes.
 */
public class DyerGoal extends WorkGoal {
    private static final int WORK_TICKS = 60;

    private int timer;

    public DyerGoal(CitizenEntity citizen) {
        super(citizen, JobType.DYER);
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
        ItemStack made = ItemStack.EMPTY;
        for (Item wanted : Logistics.builderShortages(level(), colony, DyerGoal::isDyerProduct)) {
            made = Dyeing.craft(level(), storage, wanted);
            if (!made.isEmpty()) {
                break;
            }
        }
        if (made.isEmpty()) {
            for (Container container : storage) {
                for (int i = 0; i < container.getContainerSize() && made.isEmpty(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (Logistics.isFlower(stack)) {
                        made = Dyeing.craft(level(), storage, Dyeing.dyeFrom(level(), stack.getItem()).getItem());
                    }
                }
            }
        }
        if (made.isEmpty()) {
            citizen.setStatus("Nothing to dye right now");
            return;
        }
        citizen.setStatus("Making " + made.getHoverName().getString());
        level().playSound(null, hut, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    private static boolean isDyerProduct(Item item) {
        ItemStack stack = item.getDefaultInstance();
        return stack.is(ItemTags.BEDS) || stack.is(ItemTags.WOOL)
                || stack.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags.DYES);
    }
}
