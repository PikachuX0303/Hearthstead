package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lumberjack: finds trees around the hut, fells them log by log, replants a
 * sapling and brings the wood back to the hut.
 */
public class LumberjackGoal extends WorkGoal {
    private static final int MAX_TREE_SIZE = 96;

    private enum State { PREPARE, SEARCH, CHOP, REPLANT, DEPOSIT, IDLE }

    private State state = State.PREPARE;
    @Nullable
    private BlockPos treeBase;
    private final List<BlockPos> logs = new ArrayList<>();
    private int timer;

    public LumberjackGoal(CitizenEntity citizen) {
        super(citizen, JobType.LUMBERJACK);
    }

    @Override
    public void start() {
        state = State.PREPARE;
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
        switch (state) {
            case PREPARE -> {
                if (!walkTo(hut, 3)) {
                    citizen.setStatus("Going to the hut");
                    return;
                }
                depositAll();
                if (!equipTool(ItemTags.AXES)) {
                    citizen.setStatus("Needs an axe in the hut");
                    state = State.IDLE;
                    timer = 200;
                    return;
                }
                if (HutSettings.isOn(building, HutSettings.REPLANT)) {
                    takeFromHut(s -> s.is(ItemTags.SAPLINGS), 4);
                }
                state = State.SEARCH;
            }
            case SEARCH -> {
                treeBase = findTree(hut, building.workRadius());
                if (treeBase == null) {
                    citizen.setStatus("No trees nearby");
                    state = State.IDLE;
                    timer = 400;
                    return;
                }
                logs.clear();
                logs.addAll(collectTree(treeBase));
                state = State.CHOP;
            }
            case CHOP -> {
                if (treeBase == null || logs.isEmpty()) {
                    state = State.REPLANT;
                    return;
                }
                if (!walkTo(treeBase, 3)) {
                    citizen.setStatus("Walking to a tree");
                    return;
                }
                if (!citizen.getMainHandItem().is(ItemTags.AXES)) {
                    state = State.DEPOSIT;
                    return;
                }
                citizen.setStatus("Chopping a tree");
                BlockPos next = logs.get(logs.size() - 1);
                citizen.getLookControl().setLookAt(next.getCenter());
                if (++timer >= breakDelay(researched(Research.SKILLED_LUMBERJACKS) ? 13 : 20)) {
                    timer = 0;
                    logs.remove(logs.size() - 1);
                    if (level().getBlockState(next).is(BlockTags.LOGS)) {
                        harvest(next);
                    }
                }
            }
            case REPLANT -> {
                if (treeBase != null) {
                    walkTo(treeBase, 3);
                    collectNearbyItems(4);
                    replant(treeBase);
                }
                state = State.DEPOSIT;
            }
            case DEPOSIT -> {
                if (!walkTo(hut, 3)) {
                    citizen.setStatus("Bringing wood home");
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

    private void replant(BlockPos base) {
        if (!HutSettings.isOn(building(), HutSettings.REPLANT)) {
            return;
        }
        if (!level().getBlockState(base).isAir() || !level().getBlockState(base.below()).is(BlockTags.DIRT)) {
            return;
        }
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            ItemStack stack = citizen.inventory().getItem(i);
            if (stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem blockItem) {
                BlockState sapling = blockItem.getBlock().defaultBlockState();
                if (sapling.canSurvive(level(), base)) {
                    level().setBlockAndUpdate(base, sapling);
                    stack.shrink(1);
                    return;
                }
            }
        }
    }

    private boolean insideColonyBuilding(BlockPos pos) {
        Colony colony = citizen.colony();
        if (colony == null) {
            return false;
        }
        for (BuildingData building : colony.buildings()) {
            if (building.pos().closerThan(pos, 24)) {
                BoundingBox box = Blueprints.footprint(level().getServer(), level(), building);
                if (box != null && box.isInside(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private BlockPos findTree(BlockPos hut, int radius) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -6; y <= 10; y++) {
                    pos.set(hut.getX() + x, hut.getY() + y, hut.getZ() + z);
                    if (level().getBlockState(pos).is(BlockTags.LOGS)
                            && level().getBlockState(pos.below()).is(BlockTags.DIRT)
                            && !insideColonyBuilding(pos)
                            && level().getBlockState(pos.above()).is(BlockTags.LOGS)) {
                        candidates.add(pos.immutable());
                    }
                }
            }
        }
        return candidates.stream().min(Comparator.comparingDouble(p -> p.distSqr(citizen.blockPosition()))).orElse(null);
    }

    /** Collects connected logs, ordered so the last entries are the highest. */
    private List<BlockPos> collectTree(BlockPos base) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> result = new ArrayList<>();
        queue.add(base);
        visited.add(base);
        while (!queue.isEmpty() && result.size() < MAX_TREE_SIZE) {
            BlockPos current = queue.poll();
            result.add(current);
            for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, 0, -1), current.offset(1, 1, 1))) {
                if (Math.abs(next.getX() - base.getX()) > 4 || Math.abs(next.getZ() - base.getZ()) > 4) {
                    continue;
                }
                BlockPos immutable = next.immutable();
                if (!visited.contains(immutable) && level().getBlockState(immutable).is(BlockTags.LOGS)
                        && !insideColonyBuilding(immutable)) {
                    visited.add(immutable);
                    queue.add(immutable);
                }
            }
        }
        // Logs are taken from the end of the list, so the tree is felled from the top down.
        result.sort(Comparator.comparingInt(BlockPos::getY));
        return result;
    }
}
