package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Composter: fills the composters in the building with plant waste from the hut and
 * collects the bone meal, which the farmer uses to make crops grow faster.
 */
public class ComposterGoal extends WorkGoal {
    private enum State { PREPARE, TEND, IDLE }

    private State state = State.PREPARE;
    private final List<BlockPos> composters = new ArrayList<>();
    private int index;
    private int timer;

    public ComposterGoal(CitizenEntity citizen) {
        super(citizen, JobType.COMPOSTER);
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
                if (building.level() == 0) {
                    citizen.setStatus(Status.of("waiting_hut_be_built"));
                    idle(200);
                    return;
                }
                findComposters(building);
                if (composters.isEmpty()) {
                    citizen.setStatus(Status.of("needs_composters_building"));
                    idle(200);
                    return;
                }
                takeFromHut(dev.pikachux.hearthstead.colony.HutSettings.compostInput(building()), 64);
                index = 0;
                state = State.TEND;
            }
            case TEND -> {
                if (index >= composters.size()) {
                    if (!walkTo(hut, 3)) {
                        return;
                    }
                    depositAll();
                    citizen.setStatus(Status.of("waiting_compost"));
                    idle(100);
                    return;
                }
                BlockPos pos = composters.get(index);
                BlockState composter = level().getBlockState(pos);
                if (!(composter.getBlock() instanceof ComposterBlock)) {
                    index++;
                    return;
                }
                if (!walkTo(pos, 3)) {
                    citizen.setStatus(Status.of("composting"));
                    return;
                }
                if (++timer < breakDelay(10)) {
                    return;
                }
                timer = 0;
                collectNearbyItems(3);
                if (!tend(pos, composter)) {
                    index++;
                }
            }
            case IDLE -> {
                if (--timer <= 0) {
                    state = State.PREPARE;
                }
            }
        }
    }

    /** Does one step at a composter. Returns true while there is more to do there. */
    private boolean tend(BlockPos pos, BlockState composter) {
        int fill = composter.getValue(ComposterBlock.LEVEL);
        citizen.swing(InteractionHand.MAIN_HAND);
        if (fill == ComposterBlock.READY) {
            ComposterBlock.extractProduce(citizen, composter, level(), pos);
            // The bone meal pops out as an item that can be picked up after a short delay.
            timer = -15;
            return true;
        }
        if (fill >= ComposterBlock.READY - 1) {
            return false;
        }
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            ItemStack stack = citizen.inventory().getItem(i);
            if (!stack.isEmpty() && dev.pikachux.hearthstead.colony.HutSettings.compostInput(building()).test(stack)) {
                ComposterBlock.insertItem(citizen, composter, level(), stack, pos);
                citizen.setStatus(Status.of("composting"));
                return true;
            }
        }
        citizen.setStatus(Status.of("needs_plant_waste_compost"));
        return false;
    }

    private void findComposters(BuildingData building) {
        composters.clear();
        BoundingBox box = Blueprints.footprint(level().getServer(), level(), building);
        if (box == null) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level().getBlockState(pos).getBlock() instanceof ComposterBlock) {
                composters.add(pos.immutable());
            }
        }
    }

    private void idle(int ticks) {
        timer = ticks;
        state = State.IDLE;
    }
}
