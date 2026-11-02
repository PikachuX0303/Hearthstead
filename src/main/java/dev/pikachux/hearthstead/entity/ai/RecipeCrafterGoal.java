package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Crafting;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/**
 * A crafting job that makes what the colony's huts are short of with the game's crafting recipes,
 * from the materials in its own hut (mechanic, blacksmith, fletcher).
 */
public abstract class RecipeCrafterGoal extends WorkGoal {
    private static final int WORK_TICKS = 80;

    private int timer;

    protected RecipeCrafterGoal(CitizenEntity citizen, JobType job) {
        super(citizen, job);
    }

    /** What this job makes. */
    protected abstract Predicate<Item> products();

    protected abstract SoundEvent workSound();

    /** The items worth making, in the order to try them; by default as the shortages list them. */
    protected List<Item> order(List<Item> wanted, BuildingData building) {
        return wanted;
    }

    private boolean make(BlockPos hut, List<Container> storage, Item wanted) {
        ItemStack made = Crafting.craftFromStorage(level(), storage, wanted);
        if (made.isEmpty()) {
            return false;
        }
        level().playSound(null, hut, workSound(), SoundSource.BLOCKS, 0.6F, 1.0F);
        citizen.setStatus("Making " + made.getHoverName().getString());
        return true;
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
        Predicate<Item> makes = products().or(dev.pikachux.hearthstead.colony.Crafters.taught(building));
        for (Item wanted : order(Logistics.shortages(level(), colony, makes, storage), building)) {
            if (make(hut, storage, wanted)) {
                return;
            }
        }
        // Taught recipes with a stock to keep.
        for (var recipe : building.recipes().entrySet()) {
            Item item = Logistics.postboxItem(recipe.getKey());
            if (item != null && Logistics.belowStock(level(), colony, building, item, recipe.getValue()) && make(hut, storage, item)) {
                return;
            }
        }
        citizen.setStatus("Nothing to make right now");
    }
}
