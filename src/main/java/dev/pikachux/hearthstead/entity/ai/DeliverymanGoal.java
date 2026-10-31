package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Deliveryman: brings requested items (building materials, tools, seeds, food, fuel)
 * from the warehouse to the huts, and carries worker huts' output to the warehouse.
 */
public class DeliverymanGoal extends WorkGoal {
    private static final int CARRY_SLOTS = 9;

    private enum State { PREPARE, PLAN, FETCH, DELIVER, COLLECT, STORE, IDLE }

    private State state = State.PREPARE;
    @Nullable
    private BlockPos warehouse;
    @Nullable
    private BlockPos target;
    @Nullable
    private Predicate<ItemStack> matcher;
    private int amount;
    private String task = "";
    private int timer;

    public DeliverymanGoal(CitizenEntity citizen) {
        super(citizen, JobType.DELIVERYMAN);
    }

    @Override
    public void start() {
        state = inventoryEmpty() ? State.PLAN : State.STORE;
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
    }

    @Override
    public void tick() {
        Colony colony = citizen.colony();
        if (colony == null || hutPos() == null) {
            return;
        }
        switch (state) {
            case PREPARE, PLAN -> plan(colony);
            case FETCH -> {
                if (!walkTo(warehouse, 3)) {
                    citizen.setStatus("Fetching " + task + " from the warehouse");
                    return;
                }
                BuildingData building = colony.building(warehouse);
                if (building != null) {
                    for (ItemStack stack : Storage.extract(Storage.containers(level(), building), matcher, amount)) {
                        ItemStack rest = citizen.inventory().addItem(stack);
                        if (!rest.isEmpty()) {
                            Storage.insert(Storage.containers(level(), building), rest);
                        }
                    }
                }
                state = inventoryEmpty() ? State.PLAN : State.DELIVER;
            }
            case DELIVER -> {
                if (!walkTo(target, 3)) {
                    citizen.setStatus("Delivering " + task);
                    return;
                }
                BuildingData building = colony.building(target);
                if (building != null) {
                    unloadInto(Storage.containers(level(), building));
                }
                state = inventoryEmpty() ? State.PLAN : State.STORE;
            }
            case COLLECT -> {
                if (!walkTo(target, 3)) {
                    citizen.setStatus("Collecting from a hut");
                    return;
                }
                BuildingData building = colony.building(target);
                if (building != null) {
                    Predicate<ItemStack> keep = Logistics.keeps(building.type());
                    for (ItemStack stack : Storage.extract(Storage.containers(level(), building), s -> !keep.test(s),
                            64 * CARRY_SLOTS)) {
                        ItemStack rest = citizen.inventory().addItem(stack);
                        if (!rest.isEmpty()) {
                            Storage.insert(Storage.containers(level(), building), rest);
                        }
                    }
                }
                state = State.STORE;
            }
            case STORE -> {
                BlockPos store = warehouse != null ? warehouse : findWarehouse(colony);
                if (store == null) {
                    citizen.setStatus("Needs a warehouse");
                    idle(200);
                    return;
                }
                if (!walkTo(store, 3)) {
                    citizen.setStatus("Bringing items to the warehouse");
                    return;
                }
                BuildingData building = colony.building(store);
                if (building != null) {
                    unloadInto(Storage.containers(level(), building));
                }
                if (!inventoryEmpty()) {
                    citizen.setStatus("The warehouse is full");
                    idle(200);
                    return;
                }
                state = State.PLAN;
            }
            case IDLE -> {
                if (--timer <= 0) {
                    state = inventoryEmpty() ? State.PLAN : State.STORE;
                }
            }
        }
    }

    private void plan(Colony colony) {
        if (!inventoryEmpty()) {
            state = State.STORE;
            return;
        }
        warehouse = findWarehouse(colony);
        if (warehouse == null) {
            citizen.setStatus("Needs a warehouse");
            idle(200);
            return;
        }
        BuildingData warehouseBuilding = colony.building(warehouse);
        List<Container> stock = Storage.containers(level(), warehouseBuilding);

        // 1. Deliver what huts are waiting for, nearest first.
        List<Logistics.Request> requests = Logistics.requests(level(), colony);
        requests.sort(Comparator.comparingDouble(r -> r.target().distSqr(citizen.blockPosition())));
        for (Logistics.Request request : requests) {
            if (request.target().equals(warehouse) || Storage.count(stock, request.matcher()) == 0) {
                continue;
            }
            target = request.target();
            matcher = request.matcher();
            amount = request.count();
            task = request.description();
            state = State.FETCH;
            return;
        }

        // 2. Empty worker huts that have produced something.
        for (BuildingData building : colony.buildings()) {
            if (!Logistics.producesOutput(building.type())) {
                continue;
            }
            Predicate<ItemStack> keep = Logistics.keeps(building.type());
            List<Container> storage = Storage.containers(level(), building);
            if (Storage.count(storage, s -> !keep.test(s)) >= Logistics.PICKUP_THRESHOLD || !Storage.hasSpace(storage)) {
                target = building.pos();
                state = State.COLLECT;
                return;
            }
        }

        citizen.setStatus("Waiting for deliveries");
        idle(100);
    }

    private void unloadInto(List<Container> containers) {
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            ItemStack stack = citizen.inventory().getItem(i);
            if (!stack.isEmpty()) {
                citizen.inventory().setItem(i, Storage.insert(containers, stack));
            }
        }
    }

    @Nullable
    private BlockPos findWarehouse(Colony colony) {
        BuildingData nearest = colony.nearestBuilding(BuildingType.WAREHOUSE, citizen.blockPosition());
        return nearest == null ? null : nearest.pos();
    }

    private void idle(int ticks) {
        timer = ticks;
        state = State.IDLE;
    }
}
