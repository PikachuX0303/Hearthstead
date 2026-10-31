package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A badly hurt citizen (below half health) leaves work and goes to a hospital with a healer,
 * staying there until mostly recovered. Guards in a fight keep fighting.
 */
public class SeekHealingGoal extends Goal {
    private final CitizenEntity citizen;
    @Nullable
    private BuildingData hospital;
    private int pathTimer;

    public SeekHealingGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (citizen.getTarget() != null || citizen.job() == JobType.HEALER
                || citizen.getHealth() >= citizen.getMaxHealth() * 0.5F) {
            return false;
        }
        hospital = staffedHospital();
        return hospital != null;
    }

    @Override
    public boolean canContinueToUse() {
        return citizen.getTarget() == null && citizen.getHealth() < citizen.getMaxHealth() * 0.9F && hospital != null;
    }

    @Nullable
    private BuildingData staffedHospital() {
        Colony colony = citizen.colony();
        if (colony == null) {
            return null;
        }
        BuildingData nearest = null;
        for (BuildingData building : colony.buildings()) {
            if (building.type() != BuildingType.HOSPITAL || building.level() == 0
                    || colony.citizens().stream().noneMatch(c -> building.pos().equals(c.workPos()))) {
                continue;
            }
            if (nearest == null || building.pos().distSqr(citizen.blockPosition()) < nearest.pos().distSqr(citizen.blockPosition())) {
                nearest = building;
            }
        }
        return nearest;
    }

    @Override
    public void start() {
        pathTimer = 0;
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
        hospital = null;
    }

    @Override
    public void tick() {
        if (hospital == null) {
            return;
        }
        citizen.setStatus("Recovering at the hospital");
        if (!citizen.blockPosition().closerToCenterThan(hospital.pos().getCenter(), 4) && --pathTimer <= 0) {
            pathTimer = 20;
            citizen.getNavigation().moveTo(hospital.pos().getX() + 0.5, hospital.pos().getY(), hospital.pos().getZ() + 0.5, 0.7);
        }
    }
}
