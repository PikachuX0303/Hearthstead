package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.config.HearthsteadConfig;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;

/**
 * Researcher: studies at the university, adding research points to the colony's
 * active research (about one point per second, faster when happy).
 */
public class ResearcherGoal extends WorkGoal {
    private int timer;

    public ResearcherGoal(CitizenEntity citizen) {
        super(citizen, JobType.RESEARCHER);
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
            citizen.setStatus("Going to the university");
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the university to be built");
            return;
        }
        Research active = colony.research().active();
        if (active == null) {
            citizen.setStatus("Waiting for a research to be started");
            return;
        }
        citizen.setStatus("Researching " + Component.translatable(active.nameKey()).getString());
        if (++timer % 20 != 0) {
            return;
        }
        if (timer % 100 == 0) {
            level().sendParticles(ParticleTypes.ENCHANT, citizen.getX(), citizen.getY() + 2, citizen.getZ(), 6, 0.4, 0.3, 0.4, 0.5);
        }
        colony.research().addProgress(level().getServer(), colony,
                citizen.workSpeed() * HearthsteadConfig.RESEARCH_SPEED.get());
    }
}
