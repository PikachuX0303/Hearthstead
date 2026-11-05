package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Planter: looks after the sugar cane, cactus and bamboo growing around the hut. It cuts every
 * plant taller than one block down to its base (so it grows back), and spreads each kind to free
 * spots right next to plants of the same kind, up to a number of plants that grows with the hut
 * level. It keeps a few cuttings in its pocket for planting and brings the rest to the hut.
 */
public class PlanterGoal extends WorkGoal {
    private enum State { PREPARE, HARVEST, PLANT, IDLE }

    /** Plant blocks and the item that plants them. */
    public static final Map<Block, Item> PLANTS = Map.of(
            Blocks.SUGAR_CANE, Items.SUGAR_CANE,
            Blocks.CACTUS, Items.CACTUS,
            Blocks.BAMBOO, Items.BAMBOO);
    private static final int CUTTINGS_KEPT = 4;
    private static final int WORK_TICKS = 20;

    private State state = State.PREPARE;
    @Nullable
    private BlockPos target;
    private int timer;

    public PlanterGoal(CitizenEntity citizen) {
        super(citizen, JobType.PLANTER);
    }

    /** Most plants (counted by their base block) the planter keeps for a hut level. */
    public static int maxPlants(int hutLevel) {
        return 4 + 4 * hutLevel;
    }

    public static boolean isPlantItem(ItemStack stack) {
        return PLANTS.containsValue(stack.getItem());
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
                    citizen.setStatus(Status.of("going_hut"));
                    return;
                }
                depositExceptCuttings();
                for (Item item : PLANTS.values()) {
                    takeFromHut(s -> s.is(item), Math.max(0, CUTTINGS_KEPT - count(item)));
                }
                target = findTallPlant(hut, building.workRadius());
                if (target != null) {
                    state = State.HARVEST;
                    return;
                }
                target = findPlantingSpot(hut, building);
                if (target != null) {
                    state = State.PLANT;
                    return;
                }
                citizen.setStatus(countPlants(hut, building.workRadius()) == 0
                        ? Status.of("plant_near_hut") : Status.of("waiting_for_plants"));
                timer = 200;
                state = State.IDLE;
            }
            case HARVEST -> {
                citizen.setStatus(Status.of("harvesting"));
                if (target == null || !isTall(target)) {
                    state = State.PREPARE;
                    return;
                }
                if (!walkTo(target, 3)) {
                    return;
                }
                if (++timer < breakDelay(WORK_TICKS)) {
                    return;
                }
                timer = 0;
                // Cut from the top down so every piece drops into the planter's hands.
                BlockPos top = target;
                while (PLANTS.containsKey(level().getBlockState(top.above()).getBlock())) {
                    top = top.above();
                }
                for (BlockPos pos = top; pos.getY() > target.getY(); pos = pos.below()) {
                    harvest(pos);
                }
                collectNearbyItems(3);
                state = State.PREPARE;
            }
            case PLANT -> {
                citizen.setStatus(Status.of("planting"));
                if (target == null) {
                    state = State.PREPARE;
                    return;
                }
                if (!walkTo(target, 3)) {
                    return;
                }
                plant(target);
                state = State.PREPARE;
            }
            case IDLE -> {
                if (--timer <= 0) {
                    state = State.PREPARE;
                }
            }
        }
    }

    /** The base of a plant that has grown more than one block tall, nearest first. */
    @Nullable
    private BlockPos findTallPlant(BlockPos hut, int radius) {
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(hut.offset(-radius, -3, -radius), hut.offset(radius, 3, radius))) {
            if (isBase(pos) && isTall(pos) && (best == null || pos.distSqr(citizen.blockPosition()) < best.distSqr(citizen.blockPosition()))) {
                best = pos.immutable();
            }
        }
        return best;
    }

    /** The bottom block of a plant column. */
    private boolean isBase(BlockPos pos) {
        Block block = level().getBlockState(pos).getBlock();
        return PLANTS.containsKey(block) && !level().getBlockState(pos.below()).is(block);
    }

    private boolean isTall(BlockPos base) {
        return level().getBlockState(base.above()).is(level().getBlockState(base).getBlock());
    }

    private int countPlants(BlockPos hut, int radius) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(hut.offset(-radius, -3, -radius), hut.offset(radius, 3, radius))) {
            if (isBase(pos)) {
                count++;
            }
        }
        return count;
    }

    /** A free spot next to an existing plant where the planter has a cutting of that kind to plant. */
    @Nullable
    private BlockPos findPlantingSpot(BlockPos hut, BuildingData building) {
        int radius = building.workRadius();
        if (countPlants(hut, radius) >= maxPlants(building.level())) {
            return null;
        }
        for (BlockPos pos : BlockPos.betweenClosed(hut.offset(-radius, -3, -radius), hut.offset(radius, 3, radius))) {
            if (!isBase(pos)) {
                continue;
            }
            Block block = level().getBlockState(pos).getBlock();
            if (count(PLANTS.get(block)) == 0) {
                continue;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int distance = 1; distance <= 2; distance++) {
                    BlockPos spot = pos.relative(side, distance);
                    if (canPlant(block, spot)) {
                        return spot.immutable();
                    }
                }
            }
        }
        return null;
    }

    private boolean canPlant(Block block, BlockPos spot) {
        return level().getBlockState(spot).isAir() && level().getBlockState(spot.above()).isAir()
                && block.defaultBlockState().canSurvive(level(), spot);
    }

    /** Whether the hut's setting lets the planter spread this kind of plant. */
    private static boolean spreads(String setting, Block block) {
        return switch (setting) {
            case "sugar_cane" -> block == Blocks.SUGAR_CANE;
            case "cactus" -> block == Blocks.CACTUS;
            case "bamboo" -> block == Blocks.BAMBOO;
            default -> true;
        };
    }

    private void plant(BlockPos spot) {
        String spread = dev.pikachux.hearthstead.colony.HutSettings.get(building(), dev.pikachux.hearthstead.colony.HutSettings.SPREAD);
        for (Map.Entry<Block, Item> plant : PLANTS.entrySet()) {
            if (count(plant.getValue()) == 0 || !spreads(spread, plant.getKey())) {
                continue;
            }
            boolean nextToSameKind = false;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int distance = 1; distance <= 2; distance++) {
                    if (level().getBlockState(spot.relative(side, distance)).is(plant.getKey())) {
                        nextToSameKind = true;
                    }
                }
            }
            if (!nextToSameKind || !canPlant(plant.getKey(), spot)) {
                continue;
            }
            BlockState state = plant.getKey() == Blocks.BAMBOO ? Blocks.BAMBOO_SAPLING.defaultBlockState() : plant.getKey().defaultBlockState();
            level().setBlockAndUpdate(spot, state);
            remove(plant.getValue());
            citizen.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }
    }

    private int count(Item item) {
        return citizen.inventory().countItem(item);
    }

    private void remove(Item item) {
        SimpleContainer inventory = citizen.inventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                inventory.getItem(i).shrink(1);
                return;
            }
        }
    }

    /** Brings the harvest to the hut but keeps a few cuttings of each kind for planting. */
    private void depositExceptCuttings() {
        SimpleContainer inventory = citizen.inventory();
        Map<Item, Integer> kept = new java.util.HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (isPlantItem(stack)) {
                int keep = Math.min(stack.getCount(), CUTTINGS_KEPT - kept.getOrDefault(stack.getItem(), 0));
                kept.merge(stack.getItem(), keep, Integer::sum);
                if (keep >= stack.getCount()) {
                    continue;
                }
                storeInHut(stack.split(stack.getCount() - keep));
                continue;
            }
            storeInHut(inventory.removeItemNoUpdate(i));
        }
    }
}
