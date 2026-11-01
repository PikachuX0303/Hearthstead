package dev.pikachux.hearthstead.blueprint;

import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.WorkOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for building a blueprint in the world, shared by the builder AI,
 * the hut info screen and the instant build command.
 */
public final class Construction {
    private Construction() {
    }

    @Nullable
    public static List<Blueprint.Placement> plan(ServerLevel level, BuildingData building, int targetLevel) {
        Blueprint blueprint = Blueprints.get(level.getServer(), building, targetLevel);
        if (blueprint == null) {
            return null;
        }
        return blueprint.placements(building.pos(), Blueprints.facing(level, building.pos()));
    }

    /**
     * What a builder has to do for a work order: the blocks of a hut level or a decoration to
     * build, or for a removal the blocks to take down (each with the block that should be there).
     */
    @Nullable
    public static List<Blueprint.Placement> plan(ServerLevel level, Colony colony, WorkOrder order) {
        if (order.kind() == WorkOrder.Kind.REMOVE) {
            return removalPlan(level, order);
        }
        if (order.isDecoration()) {
            Blueprint blueprint = Blueprints.decoration(level.getServer(), order.style(), order.decoration());
            return blueprint == null ? null : blueprint.placements(order.buildingPos(), order.facing());
        }
        BuildingData target = colony.building(order.buildingPos());
        return target == null ? null : plan(level, target, order.targetLevel());
    }

    /**
     * The blocks a removal takes down, top to bottom: everything of the hut's blueprint from the
     * hut's own layer upwards (the floor and foundation stay), but never the hut block itself.
     */
    @Nullable
    public static List<Blueprint.Placement> removalPlan(ServerLevel level, WorkOrder order) {
        if (order.hutType() == null) {
            return null;
        }
        BuildingData ghost = new BuildingData(order.buildingPos(), order.hutType(), order.targetLevel());
        ghost.setStyle(order.style());
        Blueprint blueprint = Blueprints.get(level.getServer(), ghost, Math.max(1, order.targetLevel()));
        if (blueprint == null) {
            return null;
        }
        return blueprint.placements(order.buildingPos(), order.facing()).stream()
                .filter(p -> !p.state().isAir() && p.state().getFluidState().isEmpty() && p.pos().getY() >= order.buildingPos().getY())
                .sorted(java.util.Comparator.comparingInt((Blueprint.Placement p) -> -p.pos().getY()))
                .toList();
    }

    /** For a removal: true once the blueprint's block is gone from this spot (or something else stands there). */
    public static boolean isRemoved(ServerLevel level, Blueprint.Placement placement) {
        return level.getBlockState(placement.pos()).getBlock() != placement.state().getBlock();
    }

    /** True if the world already matches this placement closely enough. */
    public static boolean isDone(ServerLevel level, Blueprint.Placement placement) {
        BlockState current = level.getBlockState(placement.pos());
        if (current.getBlock() instanceof HutBlock) {
            return true;
        }
        if (placement.state().isAir()) {
            return current.isAir() || current.canBeReplaced() && current.getFluidState().isEmpty();
        }
        // Compare blocks only: shapes and connections are recalculated after placing.
        return current.getBlock() == placement.state().getBlock();
    }

    /** The item a builder consumes to place this block, or null if it is free. */
    @Nullable
    public static Item requiredItem(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return null;
        }
        // Ground that cannot be picked up as it is: builders lay dirt and shape it.
        if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND) || state.is(Blocks.GRASS_BLOCK)) {
            return Items.DIRT;
        }
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : item;
    }

    /** Places a block together with the other half of doors and beds. */
    public static void place(ServerLevel level, Blueprint.Placement placement) {
        BlockState state = placement.state();
        BlockPos partner = Blueprint.partnerOf(placement);
        // The first half never re-checks its own shape, so it survives until the second half arrives.
        level.setBlock(placement.pos(), state, Block.UPDATE_ALL);
        if (partner != null) {
            level.setBlock(partner, Blueprint.partnerState(state), Block.UPDATE_ALL);
        }
    }

    public record Progress(int done, int total, Map<Item, Integer> remaining) {
    }

    /** How far a removal has come. */
    public static Progress removalProgress(ServerLevel level, List<Blueprint.Placement> plan) {
        int done = (int) plan.stream().filter(p -> isRemoved(level, p)).count();
        return new Progress(done, plan.size(), new LinkedHashMap<>());
    }

    /** How far a work order has come, and for building which materials are still needed. */
    public static Progress progress(ServerLevel level, WorkOrder order, List<Blueprint.Placement> plan) {
        return order.kind() == WorkOrder.Kind.REMOVE ? removalProgress(level, plan) : progress(level, plan);
    }

    /** How far construction has come and which materials are still needed. */
    public static Progress progress(ServerLevel level, List<Blueprint.Placement> plan) {
        int done = 0;
        Map<Item, Integer> remaining = new LinkedHashMap<>();
        for (Blueprint.Placement placement : plan) {
            if (isDone(level, placement)) {
                done++;
                continue;
            }
            Item item = requiredItem(placement.state());
            if (item != null) {
                remaining.merge(item, 1, Integer::sum);
            }
        }
        return new Progress(done, plan.size(), remaining);
    }

    /** Builds the whole plan immediately, without consuming materials. */
    public static void buildInstantly(ServerLevel level, List<Blueprint.Placement> plan) {
        for (Blueprint.Placement placement : plan) {
            if (!isDone(level, placement)) {
                if (placement.state().isAir()) {
                    level.setBlock(placement.pos(), placement.state(), Block.UPDATE_ALL);
                } else {
                    place(level, placement);
                }
            }
        }
    }
}
