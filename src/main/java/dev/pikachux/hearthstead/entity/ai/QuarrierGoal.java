package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Quarrier: digs an open pit behind the quarry, one layer at a time from the top down. A higher
 * hut level makes the pit wider and deeper. Needs a pickaxe; brings what it digs to the hut.
 */
public class QuarrierGoal extends WorkGoal {
    private static final int DIG_TICKS = 30;

    @Nullable
    private BlockPos target;
    private int timer;

    public QuarrierGoal(CitizenEntity citizen) {
        super(citizen, JobType.QUARRIER);
    }

    /**
     * The pit for a quarry: a square of 3 + 2 per level blocks behind the hut, clear of the
     * largest building, reaching 4 + 4 per level blocks down from the ground.
     */
    public static BoundingBox pit(BlockPos hut, Direction facing, int level) {
        int half = 1 + level;
        int depth = 4 + 4 * level;
        BlockPos center = hut.relative(facing.getOpposite(), 9 + half);
        return new BoundingBox(center.getX() - half, hut.getY() - depth, center.getZ() - half,
                center.getX() + half, hut.getY() - 1, center.getZ() + half);
    }

    @Nullable
    private BoundingBox pit(BuildingData building) {
        BlockState hutState = level().getBlockState(building.pos());
        if (!hutState.hasProperty(HutBlock.FACING)) {
            return null;
        }
        return pit(building.pos(), hutState.getValue(HutBlock.FACING), dev.pikachux.hearthstead.colony.HutSettings.pitLevel(building));
    }

    /** Whether the quarrier digs this block: anything solid that is not unbreakable, a hut or a container. */
    public static boolean diggable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && level.getFluidState(pos).isEmpty() && state.getDestroySpeed(level, pos) >= 0
                && level.getBlockEntity(pos) == null && !(state.getBlock() instanceof HutBlock);
    }

    /** The next block to dig: the highest layer first. */
    @Nullable
    public static BlockPos nextBlock(ServerLevel level, BoundingBox pit) {
        for (int y = pit.maxY(); y >= Math.max(pit.minY(), level.getMinY() + 1); y--) {
            for (int x = pit.minX(); x <= pit.maxX(); x++) {
                for (int z = pit.minZ(); z <= pit.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (diggable(level, pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
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
        if (building.level() == 0) {
            citizen.setStatus(Status.of("waiting_quarry_be_built"));
            return;
        }
        if (inventoryFull() || !citizen.getMainHandItem().is(ItemTags.PICKAXES)) {
            if (!walkTo(hut, 3)) {
                citizen.setStatus(inventoryFull() ? Status.of("bringing_stone_quarry") : Status.of("fetching_pickaxe"));
                return;
            }
            depositAll();
            if (!equipTool(ItemTags.PICKAXES)) {
                citizen.setStatus(Status.of("needs_pickaxe_hut"));
                return;
            }
        }
        BoundingBox pit = pit(building);
        if (pit == null) {
            return;
        }
        if (target == null || !diggable(level(), target)) {
            target = nextBlock(level(), pit);
            timer = 0;
        }
        if (target == null) {
            citizen.setStatus(Status.of("pit_is_dug_out_upgrade_quarry"));
            return;
        }
        if (!walkTo(target, 4)) {
            citizen.setStatus(Status.of("walking_into_pit"));
            return;
        }
        citizen.setStatus(Status.of("quarrying"));
        citizen.getLookControl().setLookAt(target.getCenter());
        if (++timer < breakDelay(DIG_TICKS)) {
            return;
        }
        timer = 0;
        BlockState mined = level().getBlockState(target);
        harvest(target);
        if (mined.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.ORES)
                && researched(Research.PROSPECTORS) && citizen.getRandom().nextFloat() < 0.2F) {
            bonusDrop(mined, target);
        }
        target = null;
    }
}
