package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;

/**
 * Teacher: keeps the school open. While the school is staffed, children go there to learn and
 * grow up twice as fast.
 */
public class TeacherGoal extends WorkGoal {
    private int timer;

    public TeacherGoal(CitizenEntity citizen) {
        super(citizen, JobType.TEACHER);
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
            citizen.setStatus("Going to the school");
            return;
        }
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the school to be built");
            return;
        }
        long pupils = level().getEntitiesOfClass(CitizenEntity.class, new AABB(hut).inflate(6), CitizenEntity::isBaby).size();
        citizen.setStatus(pupils == 0 ? "Waiting for pupils" : "Teaching " + pupils + (pupils == 1 ? " pupil" : " pupils"));
        if (pupils > 0 && ++timer % 60 == 0) {
            citizen.swing(InteractionHand.MAIN_HAND);
        }
    }
}
