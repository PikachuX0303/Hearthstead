package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Miner: digs a two-high tunnel out of the front of the miner's building and mines
 * every ore exposed along its walls. Place the hut underground for best results.
 */
public class MinerGoal extends WorkGoal {
    private enum State { PREPARE, DIG, DEPOSIT, IDLE }

    private State state = State.PREPARE;
    @Nullable
    private BlockPos target;
    private int timer;

    public MinerGoal(CitizenEntity citizen) {
        super(citizen, JobType.MINER);
    }

    @Override
    public void start() {
        state = State.PREPARE;
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
    }

    private int tunnelLength(BuildingData building) {
        return 16 * building.level();
    }

    @Override
    public void tick() {
        BlockPos hut = hutPos();
        BuildingData building = building();
        if (hut == null || building == null) {
            return;
        }
        BlockState hutState = level().getBlockState(hut);
        Direction facing = hutState.hasProperty(HutBlock.FACING) ? hutState.getValue(HutBlock.FACING) : Direction.NORTH;
        switch (state) {
            case PREPARE -> {
                if (!walkTo(hut, 3)) {
                    citizen.setStatus(Status.of("going_hut"));
                    return;
                }
                depositAll();
                if (!equipTool(ItemTags.PICKAXES)) {
                    citizen.setStatus(Status.of("needs_pickaxe_hut"));
                    state = State.IDLE;
                    timer = 200;
                    return;
                }
                if (HutSettings.isOn(building, HutSettings.TORCHES)) {
                    takeFromHut(s -> s.is(Items.TORCH), 16);
                }
                state = State.DIG;
            }
            case DIG -> {
                if (target == null) {
                    target = nextBlock(hut, facing, tunnelLength(building), Blueprints.footprint(level().getServer(), level(), building));
                    if (target == null) {
                        citizen.setStatus(Status.of("mine_is_finished_upgrade_hut"));
                        state = inventoryEmpty() ? State.IDLE : State.DEPOSIT;
                        timer = 400;
                        return;
                    }
                }
                if (!walkTo(target, 3)) {
                    citizen.setStatus(Status.of("walking_into_mine"));
                    if (citizen.getNavigation().isDone() && !citizen.blockPosition().closerToCenterThan(target.getCenter(), 6)) {
                        // Cannot reach the target; skip it.
                        target = null;
                    }
                    return;
                }
                if (!citizen.getMainHandItem().is(ItemTags.PICKAXES)) {
                    state = State.DEPOSIT;
                    return;
                }
                citizen.setStatus(Status.of("mining"));
                if (++timer < breakDelay(30)) {
                    return;
                }
                timer = 0;
                BlockState mined = level().getBlockState(target);
                harvest(target);
                if (mined.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.ORES)
                        && researched(Research.PROSPECTORS) && citizen.getRandom().nextFloat() < 0.2F) {
                    bonusDrop(mined, target);
                }
                placeTorch(hut, facing, target);
                target = null;
                if (inventoryFull()) {
                    state = State.DEPOSIT;
                }
            }
            case DEPOSIT -> {
                if (!walkTo(hut, 3)) {
                    citizen.setStatus(Status.of("bringing_ore_home"));
                    return;
                }
                depositAll();
                state = State.PREPARE;
            }
            case IDLE -> {
                if (--timer <= 0) {
                    state = State.PREPARE;
                }
            }
        }
    }

    private static boolean minable(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(Blocks.BEDROCK)
                && state.getBlock().defaultDestroyTime() >= 0 && !(state.getBlock() instanceof HutBlock)
                && !state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH)
                && !(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock);
    }

    /** Next block to break: the tunnel itself first, then ores in its walls, floor and ceiling. */
    @Nullable
    private BlockPos nextBlock(BlockPos hut, Direction facing, int length, @Nullable BoundingBox house) {
        Direction side = facing.getClockWise();
        int start = 1;
        while (house != null && house.isInside(hut.relative(facing, start))) {
            start++;
        }
        for (int i = start; i < start + length; i++) {
            BlockPos floor = hut.relative(facing, i);
            for (BlockPos pos : new BlockPos[]{floor, floor.above()}) {
                if (minable(level().getBlockState(pos))) {
                    return pos;
                }
            }
            for (BlockPos pos : new BlockPos[]{floor.relative(side), floor.relative(side.getOpposite()),
                    floor.above().relative(side), floor.above().relative(side.getOpposite()), floor.below(), floor.above(2)}) {
                if (level().getBlockState(pos).is(ConventionalBlockTags.ORES)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private void placeTorch(BlockPos hut, Direction facing, BlockPos dug) {
        if (!HutSettings.isOn(building(), HutSettings.TORCHES)) {
            return;
        }
        int distance = Math.abs(dug.getX() - hut.getX()) + Math.abs(dug.getZ() - hut.getZ());
        if (distance % 8 != 0 || dug.getY() != hut.getY() + 1) {
            return;
        }
        Direction side = facing.getClockWise();
        BlockPos torchPos = dug;
        BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side.getOpposite());
        if (!level().getBlockState(torchPos).isAir() || !torch.canSurvive(level(), torchPos)) {
            return;
        }
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            ItemStack stack = citizen.inventory().getItem(i);
            if (stack.is(Items.TORCH)) {
                level().setBlockAndUpdate(torchPos, torch);
                stack.shrink(1);
                return;
            }
        }
    }
}
