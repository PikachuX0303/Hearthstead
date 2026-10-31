package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;

/**
 * Undertaker: buries the colony's dead at the graveyard, which ends the mourning. Now and then,
 * more often at a higher graveyard level, the departed comes back to life instead.
 */
public class UndertakerGoal extends WorkGoal {
    /** Ticks a burial takes, before the work speed bonus. */
    public static final int BURIAL_TICKS = 400;

    private int timer;

    public UndertakerGoal(CitizenEntity citizen) {
        super(citizen, JobType.UNDERTAKER);
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
            citizen.setStatus("Going to the graveyard");
            return;
        }
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the graveyard to be built");
            return;
        }
        if (colony.dead().isEmpty()) {
            citizen.setStatus("Tending the graves");
            timer = 0;
            return;
        }
        CitizenData next = colony.dead().getFirst();
        citizen.setStatus("Burying " + next.name());
        if (++timer % 20 == 0) {
            citizen.swing(InteractionHand.MAIN_HAND);
        }
        if (timer < breakDelay(BURIAL_TICKS)) {
            return;
        }
        timer = 0;
        CitizenData revived = colony.bury(level(), building);
        level().playSound(null, hut, revived != null ? SoundEvents.TOTEM_USE : SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.6F, 0.8F);
        level().sendParticles(revived != null ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.SOUL, hut.getX() + 0.5,
                hut.getY() + 1, hut.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);
    }
}
