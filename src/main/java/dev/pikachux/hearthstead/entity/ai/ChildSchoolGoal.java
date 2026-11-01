package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Skill;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import org.jetbrains.annotations.Nullable;


/**
 * Children go to a staffed school during the day and learn there, picking up a little of every
 * skill (a school also makes them grow up twice as fast).
 */
public class ChildSchoolGoal extends WorkGoal {
    @Nullable
    private BuildingData school;
    private int timer;

    public ChildSchoolGoal(CitizenEntity citizen) {
        // Uses the job goals' walking (with its way around obstacles), but is no job.
        super(citizen, dev.pikachux.hearthstead.colony.JobType.UNEMPLOYED);
    }

    @Override
    public boolean canUse() {
        CitizenData data = citizen.data();
        Colony colony = citizen.colony();
        if (data == null || colony == null || !data.child() || data.sick() || !citizen.level().isBrightOutside()) {
            return false;
        }
        school = null;
        for (BuildingData building : colony.buildings()) {
            if (building.type() == BuildingType.SCHOOL && building.level() > 0
                    && colony.citizens().stream().anyMatch(c -> building.pos().equals(c.workPos()))) {
                school = building;
                break;
            }
        }
        return school != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
        school = null;
    }

    @Override
    public void tick() {
        CitizenData data = citizen.data();
        Colony colony = citizen.colony();
        if (school == null || data == null || colony == null) {
            return;
        }
        if (!walkTo(school.pos(), 4)) {
            data.setStatus("Going to school");
            return;
        }
        data.setStatus("At school");
        if (++timer % 20 == 0) {
            Skill[] skills = Skill.values();
            Skill skill = skills[citizen.getRandom().nextInt(skills.length - 1)]; // anything but Marksmanship
            data.addExperience(skill, (1 + school.level()) * colony.skillGainMultiplier());
        }
    }
}
