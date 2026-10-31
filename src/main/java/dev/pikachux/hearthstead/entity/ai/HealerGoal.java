package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Healer: works at the hospital and tends to hurt citizens around it. Badly hurt citizens come
 * to the hospital on their own ({@link SeekHealingGoal}).
 */
public class HealerGoal extends WorkGoal {
    private static final int TREAT_TICKS = 40;

    private int timer;

    public HealerGoal(CitizenEntity citizen) {
        super(citizen, JobType.HEALER);
    }

    /** Health restored per treatment: 2, plus 1 for every two hut levels. */
    public static float healing(int hutLevel) {
        return 2 + hutLevel / 2;
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
            citizen.setStatus("Going to the hospital");
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the hospital to be built");
            return;
        }
        if (++timer < breakDelay(TREAT_TICKS)) {
            return;
        }
        timer = 0;
        List<CitizenEntity> patients = level().getEntitiesOfClass(CitizenEntity.class,
                new AABB(hut).inflate(building.workRadius()),
                other -> other != citizen && other.isAlive() && other.getHealth() < other.getMaxHealth() && other.colony() == colony);
        if (patients.isEmpty()) {
            citizen.setStatus("No one needs care");
            return;
        }
        for (CitizenEntity patient : patients) {
            patient.heal(healing(building.level()));
            level().sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + 2, patient.getZ(), 2, 0.3, 0.2, 0.3, 0);
        }
        citizen.swing(InteractionHand.MAIN_HAND);
        citizen.setStatus("Tending to " + patients.size() + (patients.size() == 1 ? " patient" : " patients"));
    }
}
