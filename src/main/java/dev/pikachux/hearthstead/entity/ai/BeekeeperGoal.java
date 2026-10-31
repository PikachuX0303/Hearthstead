package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Beekeeper: harvests the beehives and bee nests around the apiary once they are full of
 * honey, as honey bottles when it has glass bottles, otherwise as honeycomb with shears.
 * Harvesting this way never angers the bees.
 */
public class BeekeeperGoal extends WorkGoal {
    private enum State { PREPARE, WORK, COLLECT, DEPOSIT, IDLE }

    private State state = State.PREPARE;
    @Nullable
    private BlockPos hive;
    private int timer;

    public BeekeeperGoal(CitizenEntity citizen) {
        super(citizen, JobType.BEEKEEPER);
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
                    citizen.setStatus("Going to the apiary");
                    return;
                }
                depositAll();
                boolean shears = equipTool(s -> s.is(Items.SHEARS));
                takeFromHut(s -> s.is(Items.GLASS_BOTTLE), 8);
                if (!shears && citizen.inventory().countItem(Items.GLASS_BOTTLE) == 0) {
                    citizen.setStatus("Needs shears or glass bottles in the hut");
                    idle(200);
                    return;
                }
                hive = findFullHive(hut, building.workRadius());
                if (hive == null) {
                    citizen.setStatus("Waiting for the bees to make honey");
                    idle(200);
                    return;
                }
                state = State.WORK;
            }
            case WORK -> {
                if (hive == null) {
                    state = State.PREPARE;
                    return;
                }
                if (!walkTo(hive, 2.5)) {
                    citizen.setStatus("Walking to a hive");
                    return;
                }
                boolean dropped = harvest(level().getBlockState(hive));
                hive = null;
                // Honeycomb pops out as items that can only be picked up after a short delay.
                timer = 15;
                state = dropped ? State.COLLECT : State.DEPOSIT;
            }
            case COLLECT -> {
                citizen.setStatus("Gathering honeycomb");
                if (--timer > 0) {
                    return;
                }
                collectNearbyItems(4);
                state = State.DEPOSIT;
            }
            case DEPOSIT -> {
                if (!walkTo(hut, 3)) {
                    citizen.setStatus("Bringing honey home");
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

    /** Harvests a full hive. Returns true if honeycomb was dropped on the ground. */
    private boolean harvest(BlockState state) {
        if (!(state.getBlock() instanceof BeehiveBlock beehive)
                || state.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS) {
            return false;
        }
        boolean dropped = false;
        citizen.swing(InteractionHand.MAIN_HAND);
        if (citizen.inventory().countItem(Items.GLASS_BOTTLE) > 0) {
            for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
                if (citizen.inventory().getItem(i).is(Items.GLASS_BOTTLE)) {
                    citizen.inventory().getItem(i).shrink(1);
                    break;
                }
            }
            ItemStack rest = citizen.inventory().addItem(new ItemStack(Items.HONEY_BOTTLE));
            if (!rest.isEmpty()) {
                citizen.spawnAtLocation(level(), rest);
            }
            level().playSound(null, hive, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else if (citizen.getMainHandItem().is(Items.SHEARS)) {
            BeehiveBlock.dropHoneycomb(level(), citizen.getMainHandItem(), state, level().getBlockEntity(hive), citizen, hive);
            citizen.getMainHandItem().hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
            level().playSound(null, hive, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            dropped = true;
        } else {
            return false;
        }
        beehive.resetHoneyLevel(level(), state, hive);
        return dropped;
    }

    @Nullable
    private BlockPos findFullHive(BlockPos hut, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(hut.offset(-radius, -4, -radius), hut.offset(radius, 6, radius))) {
            BlockState state = level().getBlockState(pos);
            if (state.getBlock() instanceof BeehiveBlock
                    && state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS) {
                return pos.immutable();
            }
        }
        return null;
    }

    private void idle(int ticks) {
        timer = ticks;
        state = State.IDLE;
    }
}
