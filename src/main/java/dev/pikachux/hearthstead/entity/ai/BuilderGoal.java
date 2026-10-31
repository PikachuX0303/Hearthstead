package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingInfo;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.colony.WorkOrder;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder: takes work orders from the colony and builds hut blueprints block by
 * block, using materials stored in the Builder's Hut.
 */
public class BuilderGoal extends WorkGoal {
    private static final int MAX_CARRIED_TYPES = 8;

    private enum State { PREPARE, FETCH, BUILD, RETURN, IDLE }

    private State state = State.PREPARE;
    @Nullable
    private WorkOrder order;
    @Nullable
    private List<Blueprint.Placement> plan;
    private int index;
    private int timer;

    public BuilderGoal(CitizenEntity citizen) {
        super(citizen, JobType.BUILDER);
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
        BuildingData builderHut = building();
        Colony colony = citizen.colony();
        if (hut == null || builderHut == null || colony == null) {
            return;
        }
        if (order != null && !colony.workOrders().contains(order)) {
            // Cancelled, or the target hut was removed.
            order = null;
            plan = null;
            state = State.RETURN;
        }
        switch (state) {
            case PREPARE -> prepare(hut, builderHut, colony);
            case FETCH -> fetch(hut, colony);
            case BUILD -> build(colony);
            case RETURN -> {
                if (!walkTo(hut, 3)) {
                    citizen.setStatus("Returning to the builder's hut");
                    return;
                }
                depositAll();
                state = order == null ? State.PREPARE : State.FETCH;
            }
            case IDLE -> {
                if (--timer <= 0) {
                    state = order == null ? State.PREPARE : State.FETCH;
                }
            }
        }
    }

    private void prepare(BlockPos hut, BuildingData builderHut, Colony colony) {
        if (!walkTo(hut, 3)) {
            citizen.setStatus("Going to the builder's hut");
            return;
        }
        depositAll();
        order = colony.claimWorkOrder(builderHut);
        if (order == null) {
            citizen.setStatus("Waiting for work orders");
            idle(200);
            return;
        }
        BuildingData target = colony.building(order.buildingPos());
        plan = target == null ? null : Construction.plan(level(), target, order.targetLevel());
        if (plan == null) {
            citizen.setStatus("Missing blueprint for " + BuildingInfo.describe(colony, order));
            colony.removeWorkOrder(order);
            order = null;
            idle(200);
            return;
        }
        index = 0;
        state = State.FETCH;
    }

    /** At the builder's hut: picks up materials for the next steps. */
    private void fetch(BlockPos hut, Colony colony) {
        if (!walkTo(hut, 3)) {
            citizen.setStatus("Going to get materials");
            return;
        }
        if (plan == null || order == null) {
            state = State.PREPARE;
            return;
        }
        depositAll();
        Map<Item, Integer> needed = new LinkedHashMap<>();
        Item first = null;
        for (int i = index; i < plan.size() && needed.size() <= MAX_CARRIED_TYPES; i++) {
            Blueprint.Placement placement = plan.get(i);
            if (Construction.isDone(level(), placement)) {
                continue;
            }
            Item item = Construction.requiredItem(placement.state());
            if (item == null) {
                continue;
            }
            if (first == null) {
                first = item;
            }
            if (needed.containsKey(item) || needed.size() < MAX_CARRIED_TYPES) {
                needed.merge(item, 1, (a, b) -> Math.min(item.getDefaultMaxStackSize(), a + b));
            }
        }
        needed.forEach((item, count) -> takeFromHut(s -> s.is(item), count));
        if (first != null && citizen.inventory().countItem(first) == 0) {
            Item missing = first;
            int total = (int) plan.stream().skip(index)
                    .filter(p -> Construction.requiredItem(p.state()) == missing && !Construction.isDone(level(), p))
                    .count();
            citizen.setStatus("Waiting for materials: " + total + "x " + missing.getDefaultInstance().getHoverName().getString());
            idle(100);
            return;
        }
        state = State.BUILD;
    }

    private void build(Colony colony) {
        if (plan == null || order == null) {
            state = State.PREPARE;
            return;
        }
        while (index < plan.size() && Construction.isDone(level(), plan.get(index))) {
            index++;
        }
        if (index >= plan.size()) {
            finish(colony);
            return;
        }
        Blueprint.Placement step = plan.get(index);
        Item item = Construction.requiredItem(step.state());
        if (item != null && citizen.inventory().countItem(item) == 0) {
            state = State.RETURN;
            return;
        }
        citizen.setStatus("Building " + BuildingInfo.describe(colony, order));
        if (!walkTo(step.pos(), 4.5)) {
            return;
        }
        int delay = colony.research().has(Research.SWIFT_CONSTRUCTION) ? 5 : 8;
        if (++timer < breakDelay(delay)) {
            return;
        }
        timer = 0;

        BlockState current = level().getBlockState(step.pos());
        if (!current.isAir() && !(current.getBlock() instanceof HutBlock) && !current.canBeReplaced()) {
            harvest(step.pos());
        } else if (!current.isAir() && step.state().isAir()) {
            level().removeBlock(step.pos(), false);
        }
        if (!step.state().isAir()) {
            if (!step.state().getCollisionShape(level(), step.pos()).isEmpty()
                    && citizen.getBoundingBox().intersects(new AABB(step.pos()))) {
                BlockPos spot = standingSpot(step.pos());
                if (spot != null) {
                    citizen.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                }
            }
            if (item != null) {
                consume(item);
            }
            Construction.place(level(), step);
            citizen.swing(InteractionHand.MAIN_HAND);
        }
        index++;
        if (inventoryFull()) {
            state = State.RETURN;
        }
    }

    private void consume(Item item) {
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            ItemStack stack = citizen.inventory().getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                return;
            }
        }
    }

    private void finish(Colony colony) {
        BuildingData target = colony.building(order.buildingPos());
        if (target != null) {
            target.setLevel(order.targetLevel());
            colony.broadcast(level().getServer(), Component.translatable("message.hearthstead.construction_done",
                    target.type().displayName(), order.targetLevel()).withStyle(ChatFormatting.GREEN));
        }
        colony.removeWorkOrder(order);
        order = null;
        plan = null;
        state = State.RETURN;
    }

    private void idle(int ticks) {
        timer = ticks;
        state = State.IDLE;
    }
}
