package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Florist: keeps a flower garden on the grass around the florist's building. Uses bone
 * meal from the hut on the grass so flowers grow, and picks the flowers.
 */
public class FloristGoal extends WorkGoal {
    private enum State { PREPARE, WORK, DEPOSIT, IDLE }

    private State state = State.PREPARE;
    @Nullable
    private BlockPos target;
    private boolean picking;
    private int timer;

    public FloristGoal(CitizenEntity citizen) {
        super(citizen, JobType.FLORIST);
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
                depositAll();
                takeFromHut(s -> s.is(Items.BONE_MEAL), 16);
                findTask(hut, building);
                if (target == null) {
                    citizen.setStatus(citizen.inventory().countItem(Items.BONE_MEAL) == 0
                            ? Status.of("needs_bone_meal") : Status.of("garden_in_bloom"));
                    timer = 200;
                    state = State.IDLE;
                    return;
                }
                state = State.WORK;
            }
            case WORK -> {
                if (target == null) {
                    state = State.PREPARE;
                    return;
                }
                if (!walkTo(target, 2.5)) {
                    citizen.setStatus(picking ? Status.of("picking_flowers") : Status.of("tending_garden"));
                    return;
                }
                if (++timer < breakDelay(15)) {
                    return;
                }
                timer = 0;
                BlockState state = level().getBlockState(target);
                if (picking && state.is(BlockTags.SMALL_FLOWERS)) {
                    harvest(target);
                } else if (!picking && state.is(Blocks.GRASS_BLOCK)) {
                    fertilize(target);
                }
                target = null;
                this.state = inventoryFull() ? State.DEPOSIT : State.PREPARE;
            }
            case DEPOSIT -> {
                if (!walkTo(hut, 3)) {
                    citizen.setStatus(Status.of("bringing_flowers_home"));
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

    private void fertilize(BlockPos grass) {
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            ItemStack stack = citizen.inventory().getItem(i);
            if (stack.is(Items.BONE_MEAL)) {
                if (BoneMealItem.growCrop(stack, level(), grass)) {
                    level().levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, grass.above(), 15);
                    citizen.swing(InteractionHand.MAIN_HAND);
                }
                return;
            }
        }
    }

    /** Flowers to pick first; otherwise bare grass to fertilise. */
    private void findTask(BlockPos hut, BuildingData building) {
        BoundingBox house = Blueprints.footprint(level().getServer(), level(), building);
        if (house == null) {
            house = new BoundingBox(hut);
        }
        int width = 2 + building.level();
        boolean hasBoneMeal = citizen.inventory().countItem(Items.BONE_MEAL) > 0;
        boolean pickFlowers = dev.pikachux.hearthstead.colony.HutSettings.isOn(building, dev.pikachux.hearthstead.colony.HutSettings.PICK_FLOWERS);
        BlockPos grass = null;
        target = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = house.minX() - width; x <= house.maxX() + width; x++) {
            for (int z = house.minZ() - width; z <= house.maxZ() + width; z++) {
                if (x >= house.minX() && x <= house.maxX() && z >= house.minZ() && z <= house.maxZ()) {
                    continue;
                }
                pos.set(x, hut.getY(), z);
                if (pickFlowers && level().getBlockState(pos).is(BlockTags.SMALL_FLOWERS)) {
                    target = pos.immutable();
                    picking = true;
                    return;
                }
                if (hasBoneMeal && grass == null && level().getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)
                        && level().getBlockState(pos).isAir()) {
                    grass = pos.below().immutable();
                }
            }
        }
        if (grass != null) {
            target = grass;
            picking = false;
        }
    }
}
