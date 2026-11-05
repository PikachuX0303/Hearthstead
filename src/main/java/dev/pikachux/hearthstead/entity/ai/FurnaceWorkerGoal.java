package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A worker that keeps the furnaces (smokers, blast furnaces) inside its building
 * loaded with input and fuel from the hut, and stores the results in the hut, where
 * the deliveryman picks them up.
 */
public abstract class FurnaceWorkerGoal extends WorkGoal {
    private static final int INPUT_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int RESULT_SLOT = 2;

    private enum State { PREPARE, TEND, IDLE }

    private State state = State.PREPARE;
    private final List<BlockPos> furnaces = new ArrayList<>();
    private int index;
    private int timer;

    private final Predicate<ItemStack> input;
    private final Predicate<ItemStack> fuel;
    private final net.minecraft.network.chat.Component workStatus;
    private final net.minecraft.network.chat.Component needsInputStatus;

    /** What goes into the furnaces; subclasses may follow the hut's settings. */
    protected Predicate<ItemStack> input() {
        return input;
    }

    protected FurnaceWorkerGoal(CitizenEntity citizen, JobType job, Predicate<ItemStack> input, Predicate<ItemStack> fuel,
                                net.minecraft.network.chat.Component workStatus, net.minecraft.network.chat.Component needsInputStatus) {
        super(citizen, job);
        this.input = input;
        this.fuel = fuel;
        this.workStatus = workStatus;
        this.needsInputStatus = needsInputStatus;
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
                    citizen.setStatus(Status.of("going_workshop"));
                    return;
                }
                depositAll();
                if (building.level() == 0) {
                    citizen.setStatus(Status.of("waiting_workshop_be_built"));
                    idle(200);
                    return;
                }
                findFurnaces(building);
                if (furnaces.isEmpty()) {
                    citizen.setStatus(Status.of("needs_furnace_building"));
                    idle(200);
                    return;
                }
                index = 0;
                state = State.TEND;
            }
            case TEND -> {
                if (index >= furnaces.size()) {
                    if (!walkTo(hut, 3)) {
                        return;
                    }
                    depositAll();
                    workAtHut(Storage.containers(level(), building));
                    idle(100);
                    return;
                }
                BlockPos pos = furnaces.get(index);
                if (!(level().getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
                    index++;
                    return;
                }
                if (!walkTo(pos, 3)) {
                    citizen.setStatus(workStatus);
                    return;
                }
                tend(furnace);
                index++;
            }
            case IDLE -> {
                if (--timer <= 0) {
                    state = State.PREPARE;
                }
            }
        }
    }

    /** Extra work done at the hut after each round of the furnaces. */
    protected void workAtHut(List<Container> storage) {
    }

    private void tend(AbstractFurnaceBlockEntity furnace) {
        List<Container> hut = Storage.containers(level(), building());
        ItemStack result = furnace.getItem(RESULT_SLOT);
        if (!result.isEmpty()) {
            ItemStack rest = citizen.inventory().addItem(furnace.removeItemNoUpdate(RESULT_SLOT));
            furnace.setItem(RESULT_SLOT, rest);
        }
        boolean hasInput = Storage.count(hut, input()) > 0 || !furnace.getItem(INPUT_SLOT).isEmpty();
        if (furnace.getItem(INPUT_SLOT).isEmpty()) {
            for (ItemStack stack : Storage.extract(hut, input(), 16)) {
                if (furnace.getItem(INPUT_SLOT).isEmpty()) {
                    furnace.setItem(INPUT_SLOT, stack);
                } else {
                    Storage.insert(hut, stack);
                }
            }
        }
        if (furnace.getItem(FUEL_SLOT).isEmpty() && hasInput) {
            for (ItemStack stack : Storage.extract(hut, fuel, 4)) {
                if (furnace.getItem(FUEL_SLOT).isEmpty() && furnace.canPlaceItem(FUEL_SLOT, stack)) {
                    furnace.setItem(FUEL_SLOT, stack);
                } else {
                    Storage.insert(hut, stack);
                }
            }
        }
        furnace.setChanged();
        citizen.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!hasInput) {
            citizen.setStatus(needsInputStatus);
        } else if (furnace.getItem(FUEL_SLOT).isEmpty()) {
            citizen.setStatus(Status.of("needs_coal_or_charcoal"));
        } else {
            citizen.setStatus(workStatus);
        }
    }

    private void findFurnaces(BuildingData building) {
        furnaces.clear();
        BoundingBox box = Blueprints.footprint(level().getServer(), level(), building);
        if (box == null) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level().getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
                furnaces.add(pos.immutable());
            }
        }
    }

    private void idle(int ticks) {
        timer = ticks;
        state = State.IDLE;
    }
}
