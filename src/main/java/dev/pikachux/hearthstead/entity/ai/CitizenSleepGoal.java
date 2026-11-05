package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * At night citizens (except guards) return home and stay there until morning.
 */
public class CitizenSleepGoal extends Goal {
    private final CitizenEntity citizen;
    private int pathCooldown;

    public CitizenSleepGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private BlockPos home() {
        CitizenData data = citizen.data();
        if (data == null) {
            return null;
        }
        if (data.homePos() != null) {
            return data.homePos();
        }
        return citizen.colony() == null ? null : citizen.colony().center();
    }

    @Override
    public boolean canUse() {
        CitizenData data = citizen.data();
        return data != null && data.job() != JobType.GUARD && citizen.level().isDarkOutside()
                && citizen.getTarget() == null && home() != null;
    }

    @Override
    public void tick() {
        BlockPos home = home();
        if (home == null) {
            return;
        }
        if (citizen.blockPosition().closerToCenterThan(home.getCenter(), 3)) {
            citizen.getNavigation().stop();
            citizen.setStatus(Status.of("sleeping"));
            return;
        }
        citizen.setStatus(Status.of("going_home_sleep"));
        if (--pathCooldown <= 0) {
            pathCooldown = 40;
            citizen.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.7);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
