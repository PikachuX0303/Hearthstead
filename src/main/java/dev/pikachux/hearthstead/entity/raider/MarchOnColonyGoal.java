package dev.pikachux.hearthstead.entity.raider;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Moves a raider towards the town hall of its colony in short legs, so the path
 * finder never has to plan the whole way at once.
 */
public class MarchOnColonyGoal extends Goal {
    private static final int LEG_LENGTH = 16;

    private final AbstractBarbarian raider;
    private int cooldown;

    public MarchOnColonyGoal(AbstractBarbarian raider) {
        this.raider = raider;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        BlockPos target = raider.target();
        return target != null && raider.getTarget() == null
                && !raider.blockPosition().closerToCenterThan(target.getCenter(), 6);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (--cooldown > 0 && !raider.getNavigation().isDone()) {
            return;
        }
        cooldown = 40;
        BlockPos target = raider.target();
        if (target == null) {
            return;
        }
        Vec3 goal = Vec3.atBottomCenterOf(target);
        Vec3 position = raider.position();
        if (position.distanceTo(goal) > LEG_LENGTH) {
            Vec3 step = position.add(goal.subtract(position).normalize().scale(LEG_LENGTH));
            if (!raider.getNavigation().moveTo(step.x, step.y, step.z, 1.0)) {
                Vec3 detour = DefaultRandomPos.getPosTowards(raider, LEG_LENGTH, 7, goal, Math.PI / 2);
                if (detour != null) {
                    raider.getNavigation().moveTo(detour.x, detour.y, detour.z, 1.0);
                }
            }
        } else {
            raider.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
        }
    }
}
