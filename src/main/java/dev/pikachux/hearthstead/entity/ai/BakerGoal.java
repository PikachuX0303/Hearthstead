package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Baker: turns wheat from the bakery into bread. Each batch kneads three wheat into a
 * loaf; higher levels bake bigger batches.
 */
public class BakerGoal extends WorkGoal {
    private static final int WHEAT_PER_LOAF = 3;
    private static final int BAKE_TICKS = 160;

    private int timer;

    public BakerGoal(CitizenEntity citizen) {
        super(citizen, JobType.BAKER);
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos hut = hutPos();
        BuildingData building = building();
        if (hut == null || building == null) {
            return;
        }
        if (!walkTo(hut, 3)) {
            citizen.setStatus("Going to the bakery");
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the bakery to be built");
            return;
        }
        List<Container> storage = Storage.containers(level(), building);
        if (Storage.count(storage, s -> s.is(Items.WHEAT)) < WHEAT_PER_LOAF) {
            citizen.setStatus("Needs wheat");
            timer = 0;
            return;
        }
        citizen.setStatus("Baking bread");
        if (++timer % 20 == 0) {
            citizen.swing(InteractionHand.MAIN_HAND);
            level().sendParticles(ParticleTypes.SMOKE, hut.getX() + 0.5, hut.getY() + 1.2, hut.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0);
        }
        if (timer < breakDelay(BAKE_TICKS)) {
            return;
        }
        timer = 0;
        int loaves = Math.min(building.level(), Storage.count(storage, s -> s.is(Items.WHEAT)) / WHEAT_PER_LOAF);
        Storage.extract(storage, s -> s.is(Items.WHEAT), loaves * WHEAT_PER_LOAF);
        ItemStack rest = Storage.insert(storage, new ItemStack(Items.BREAD, loaves));
        if (!rest.isEmpty()) {
            citizen.spawnAtLocation(level(), rest);
        }
    }
}
