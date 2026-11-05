package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Farmer: works the field surrounding the farmer's building, at the level of its floor, and the
 * fields marked with scarecrows that the colony gives this hut (see {@link Colony#fieldsOf}).
 * Tills dirt, sows seeds, harvests ripe crops and uses bone meal on growing ones.
 */
public class FarmerGoal extends WorkGoal {
    private enum Task { TILL, PLANT, HARVEST, FERTILIZE }

    private enum State { PREPARE, WORK, DEPOSIT, IDLE }

    private State state = State.PREPARE;
    @Nullable
    private BlockPos target;
    private Task task = Task.HARVEST;
    private int timer;

    public FarmerGoal(CitizenEntity citizen) {
        super(citizen, JobType.FARMER);
    }

    @Override
    public void start() {
        state = State.PREPARE;
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
    }

    /** Width of the field band that surrounds the farmer's building. */
    private int fieldWidth(BuildingData building) {
        return 2 + building.level();
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
                if (!equipTool(ItemTags.HOES)) {
                    citizen.setStatus(Status.of("needs_hoe_hut"));
                    state = State.IDLE;
                    timer = 200;
                    return;
                }
                takeFromHut(HutSettings.farmerSeeds(building), 32);
                takeFromHut(s -> s.is(Items.BONE_MEAL), 16);
                state = State.WORK;
            }
            case WORK -> {
                if (target == null) {
                    findTask(hut, building);
                    if (target == null) {
                        state = inventoryEmpty() ? State.IDLE : State.DEPOSIT;
                        citizen.setStatus(Status.of("field_is_tended"));
                        timer = 200;
                        return;
                    }
                }
                if (!walkTo(target, 2.5)) {
                    citizen.setStatus(Status.of("walking_field"));
                    return;
                }
                citizen.setStatus(Status.of("farming"));
                if (++timer < breakDelay(10)) {
                    return;
                }
                timer = 0;
                perform(target);
                target = null;
                if (inventoryFull()) {
                    state = State.DEPOSIT;
                }
            }
            case DEPOSIT -> {
                if (!walkTo(hut, 3)) {
                    citizen.setStatus(Status.of("bringing_harvest_home"));
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

    private void findTask(BlockPos hut, BuildingData building) {
        BoundingBox house = Blueprints.footprint(level().getServer(), level(), building);
        if (house == null) {
            house = new BoundingBox(hut);
        }
        // The ground of the band around the hut, then of the fields the hut works.
        List<BlockPos> ground = new ArrayList<>();
        int width = fieldWidth(building);
        for (int x = house.minX() - width; x <= house.maxX() + width; x++) {
            for (int z = house.minZ() - width; z <= house.maxZ() + width; z++) {
                if (x < house.minX() || x > house.maxX() || z < house.minZ() || z > house.maxZ()) {
                    ground.add(new BlockPos(x, hut.getY() - 1, z));
                }
            }
        }
        Colony colony = citizen.colony();
        if (colony != null) {
            for (BlockPos scarecrow : colony.fieldsOf(building)) {
                for (int dx = -Colony.FIELD_RADIUS; dx <= Colony.FIELD_RADIUS; dx++) {
                    for (int dz = -Colony.FIELD_RADIUS; dz <= Colony.FIELD_RADIUS; dz++) {
                        if (dx != 0 || dz != 0) {
                            ground.add(scarecrow.offset(dx, -1, dz));
                        }
                    }
                }
            }
        }
        boolean hasSeeds = seedSlot() >= 0;
        boolean hasBoneMeal = citizen.inventory().countItem(Items.BONE_MEAL) > 0;
        BlockPos growing = null;
        for (BlockPos pos : ground) {
            BlockState soil = level().getBlockState(pos);
            BlockState above = level().getBlockState(pos.above());
            if (above.getBlock() instanceof CropBlock crop && crop.isMaxAge(above)) {
                target = pos.above();
                task = Task.HARVEST;
                return;
            }
            if (hasSeeds && soil.is(Blocks.FARMLAND) && above.isAir()) {
                target = pos.above();
                task = Task.PLANT;
                return;
            }
            if ((soil.is(Blocks.DIRT) || soil.is(Blocks.GRASS_BLOCK)) && above.isAir()) {
                target = pos;
                task = Task.TILL;
                return;
            }
            if (hasBoneMeal && growing == null && above.getBlock() instanceof CropBlock) {
                growing = pos.above();
            }
        }
        // With nothing else to do, speed up a growing crop with bone meal.
        if (growing != null) {
            target = growing;
            task = Task.FERTILIZE;
        }
    }

    /** A slot holding seeds of the crop chosen in the hut's settings. */
    private int seedSlot() {
        var seeds = HutSettings.farmerSeeds(building());
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            ItemStack stack = citizen.inventory().getItem(i);
            if (seeds.test(stack) && stack.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    private void perform(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        switch (task) {
            case HARVEST -> {
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                    harvest(pos);
                    if (researched(Research.BOUNTIFUL_HARVEST) && citizen.getRandom().nextFloat() < 0.25F) {
                        bonusDrop(state, pos);
                    }
                    task = Task.PLANT;
                    perform(pos);
                }
            }
            case PLANT -> {
                int slot = seedSlot();
                if (slot >= 0 && state.isAir() && level().getBlockState(pos.below()).is(Blocks.FARMLAND)) {
                    ItemStack seeds = citizen.inventory().getItem(slot);
                    BlockState crop = ((BlockItem) seeds.getItem()).getBlock().defaultBlockState();
                    if (crop.canSurvive(level(), pos)) {
                        level().setBlockAndUpdate(pos, crop);
                        seeds.shrink(1);
                    }
                }
            }
            case FERTILIZE -> {
                for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
                    ItemStack stack = citizen.inventory().getItem(i);
                    if (stack.is(Items.BONE_MEAL) && state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                        if (BoneMealItem.growCrop(stack, level(), pos)) {
                            level().levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
                            citizen.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        }
                        break;
                    }
                }
            }
            case TILL -> {
                if ((state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)) && level().getBlockState(pos.above()).isAir()) {
                    level().setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
                    citizen.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    citizen.getMainHandItem().hurtAndBreak(1, citizen, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                }
            }
        }
    }
}
