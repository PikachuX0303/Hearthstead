package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Guards;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Skill;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

/**
 * Guards spend every other stretch of a calm day training: Combat at the Combat Academy and
 * Marksmanship at the Archery. A higher training ground level trains faster.
 */
public class GuardTrainingGoal extends WorkGoal {
    /** Length of one stretch of training or patrolling. */
    public static final int SHIFT_TICKS = 1200;

    @Nullable
    private BuildingData ground;
    private int timer;

    public GuardTrainingGoal(CitizenEntity citizen) {
        super(citizen, JobType.GUARD);
    }

    @Override
    public boolean canUse() {
        CitizenData data = citizen.data();
        Colony colony = citizen.colony();
        if (data == null || colony == null || data.job() != JobType.GUARD || citizen.getTarget() != null
                || data.saturation() <= 2 || !citizen.level().isBrightOutside()) {
            return false;
        }
        long shift = citizen.level().getGameTime() / SHIFT_TICKS + data.id();
        if (shift % 2 != 0) {
            return false;
        }
        // Guards on hold or following someone stay on duty; druids learn by brewing instead.
        Guards.Order order = Guards.order(colony, data);
        Guards.Kind kind = Guards.kind(colony, data);
        if (order == Guards.Order.HOLD || order == Guards.Order.FOLLOW || kind == Guards.Kind.DRUID) {
            return false;
        }
        BuildingData academy = colony.trainingGround(BuildingType.COMBAT_ACADEMY);
        BuildingData archery = colony.trainingGround(BuildingType.ARCHERY);
        if (kind == Guards.Kind.RANGER) {
            // Rangers train at the archery whenever there is one.
            ground = archery != null ? archery : academy;
        } else {
            // With both grounds, knights alternate between them from one training stretch to the next.
            ground = academy != null && archery != null ? (shift / 2 % 2 == 0 ? academy : archery) : academy != null ? academy : archery;
        }
        return ground != null;
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
        ground = null;
    }

    @Override
    public void tick() {
        CitizenData data = citizen.data();
        Colony colony = citizen.colony();
        if (ground == null || data == null || colony == null) {
            return;
        }
        boolean archery = ground.type() == BuildingType.ARCHERY;
        if (!walkTo(ground.pos(), 4)) {
            citizen.setStatus(archery ? "Going to the archery" : "Going to the combat academy");
            return;
        }
        citizen.setStatus(archery ? "Training at the archery" : "Training at the combat academy");
        if (++timer % 20 != 0) {
            return;
        }
        citizen.swing(InteractionHand.MAIN_HAND);
        if (timer % 60 == 0) {
            citizen.level().playSound(null, citizen.blockPosition(), archery ? SoundEvents.ARROW_SHOOT : SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.NEUTRAL, 0.5F, 1.0F);
        }
        data.addExperience(archery ? Skill.MARKSMANSHIP : Skill.COMBAT, (1 + ground.level()) * colony.skillGainMultiplier());
    }
}
