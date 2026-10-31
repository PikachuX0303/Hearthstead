package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;

/**
 * Guard: equips a sword from the tower and patrols around it. Fighting itself is
 * handled by the citizen's melee and targeting goals. Guards work day and night.
 */
public class GuardPatrolGoal extends WorkGoal {
    private BlockPos patrolTarget;
    private int timer;

    public GuardPatrolGoal(CitizenEntity citizen) {
        super(citizen, JobType.GUARD);
    }

    @Override
    public boolean canUse() {
        CitizenData data = citizen.data();
        return data != null && data.job() == JobType.GUARD && data.workPos() != null
                && data.saturation() > 2 && citizen.getTarget() == null;
    }

    @Override
    public void tick() {
        BlockPos tower = hutPos();
        if (tower == null) {
            return;
        }
        if (!citizen.getMainHandItem().is(ItemTags.SWORDS)) {
            if (walkTo(tower, 3)) {
                if (!equipTool(ItemTags.SWORDS)) {
                    citizen.setStatus("Guarding (a sword in the tower would help)");
                }
            }
            if (!citizen.getMainHandItem().is(ItemTags.SWORDS) && citizen.blockPosition().closerToCenterThan(tower.getCenter(), 3)) {
                patrol(tower);
            }
            return;
        }
        patrol(tower);
    }

    /** Guards who can shoot keep a quiver of arrows from their tower. */
    private void restockArrows(BlockPos tower) {
        var data = citizen.data();
        if (data != null && data.skill(dev.pikachux.hearthstead.colony.Skill.MARKSMANSHIP) >= GuardRangedGoal.MIN_MARKSMANSHIP
                && citizen.inventory().countItem(net.minecraft.world.item.Items.ARROW) < 8
                && citizen.blockPosition().closerToCenterThan(tower.getCenter(), 4)) {
            takeFromHut(s -> s.is(net.minecraft.world.item.Items.ARROW), 16);
        }
    }

    private void patrol(BlockPos tower) {
        restockArrows(tower);
        citizen.setStatus("Patrolling");
        if (patrolTarget == null || walkTo(patrolTarget, 2) || --timer <= 0) {
            int radius = 6 + (building() == null ? 0 : building().level() * 3);
            patrolTarget = tower.offset(citizen.getRandom().nextInt(radius * 2 + 1) - radius, 0,
                    citizen.getRandom().nextInt(radius * 2 + 1) - radius);
            timer = 200;
        }
    }
}
