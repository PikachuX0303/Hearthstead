package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Illness;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A badly hurt citizen (below half health) leaves work and goes to a hospital with a healer,
 * staying there until mostly recovered. A sick citizen goes to the hospital to be cured, or
 * rests at home when there is none, until they are well again. Guards in a fight keep fighting.
 */
public class SeekHealingGoal extends Goal {
    private final CitizenEntity citizen;
    @Nullable
    private BlockPos destination;
    private boolean hospital;
    private int pathTimer;

    public SeekHealingGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean sick() {
        CitizenData data = citizen.data();
        return data != null && data.sick();
    }

    @Override
    public boolean canUse() {
        boolean hurt = citizen.getHealth() < citizen.getMaxHealth() * 0.5F && citizen.job() != JobType.HEALER;
        if (citizen.getTarget() != null || !hurt && !sick()) {
            return false;
        }
        BuildingData staffed = staffedHospital();
        hospital = staffed != null;
        CitizenData data = citizen.data();
        // Without a hospital the sick rest at home by day and sleep in their beds at night.
        destination = staffed != null ? staffed.pos()
                : sick() && data != null && citizen.level().isBrightOutside() ? data.homePos() : null;
        return destination != null;
    }

    @Override
    public boolean canContinueToUse() {
        return citizen.getTarget() == null && destination != null && (hospital || citizen.level().isBrightOutside())
                && (sick() || hospital && citizen.getHealth() < citizen.getMaxHealth() * 0.9F);
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
        destination = null;
    }

    @Override
    public void tick() {
        if (destination == null) {
            return;
        }
        CitizenData data = citizen.data();
        Illness illness = data == null ? null : data.illness();
        if (illness == null) {
            citizen.setStatus("Recovering at the hospital");
        } else {
            citizen.setStatus("Ill with " + illness.displayName().toLowerCase(java.util.Locale.ROOT)
                    + (hospital ? ", at the hospital (cure: " + illness.cure().getDefaultInstance().getHoverName().getString() + ")"
                    : ", resting at home"));
        }
        if (!citizen.blockPosition().closerToCenterThan(destination.getCenter(), hospital ? 4 : 2) && --pathTimer <= 0) {
            pathTimer = 20;
            citizen.getNavigation().moveTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, 0.7);
        }
    }
}
