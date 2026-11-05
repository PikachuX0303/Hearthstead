package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * Visitors stay around the tavern and let everyone know what it would take for them to join.
 */
public class VisitorGoal extends Goal {
    private final CitizenEntity citizen;
    private int timer;

    public VisitorGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        CitizenData data = citizen.data();
        return data != null && data.visitor() && data.homePos() != null;
    }

    @Override
    public void tick() {
        CitizenData data = citizen.data();
        if (data == null || data.homePos() == null) {
            return;
        }
        ItemStack cost = data.recruitCost();
        data.setStatus(Status.of("visiting", cost.getCount(), cost.getHoverName()));
        BlockPos tavern = data.homePos();
        if (--timer <= 0) {
            timer = 100 + citizen.getRandom().nextInt(100);
            BlockPos target = tavern.offset(citizen.getRandom().nextInt(11) - 5, 0, citizen.getRandom().nextInt(11) - 5);
            citizen.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.5);
        }
    }
}
