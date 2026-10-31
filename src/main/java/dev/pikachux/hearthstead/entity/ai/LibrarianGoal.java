package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/**
 * Librarian: keeps the library open and studies its books. While a staffed library has books,
 * every citizen of the colony gains skill experience faster; the librarian wears out one book
 * every quarter of a day.
 */
public class LibrarianGoal extends WorkGoal {
    /** Ticks of study per book used up. */
    public static final int TICKS_PER_BOOK = 6000;

    private int studied;

    public LibrarianGoal(CitizenEntity citizen) {
        super(citizen, JobType.LIBRARIAN);
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
            citizen.setStatus("Going to the library");
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the library to be built");
            return;
        }
        var storage = Storage.containers(level(), building);
        if (Storage.count(storage, s -> s.is(Items.BOOK)) == 0) {
            citizen.setStatus("Needs books to study");
            return;
        }
        citizen.setStatus("Studying (citizens learn " + Math.round(25 * building.level()) + "% faster)");
        if (++studied % 100 == 0) {
            citizen.swing(InteractionHand.MAIN_HAND);
            level().sendParticles(ParticleTypes.ENCHANT, citizen.getX(), citizen.getY() + 1.8, citizen.getZ(), 6, 0.3, 0.3, 0.3, 0.4);
        }
        if (studied >= TICKS_PER_BOOK) {
            studied = 0;
            Storage.extract(storage, s -> s.is(Items.BOOK), 1);
        }
    }
}
