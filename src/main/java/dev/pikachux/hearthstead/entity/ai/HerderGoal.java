package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * A worker that looks after a herd of animals around the hut: it tends them (shearing,
 * milking, ...) and breeds them while the herd is smaller than the hut allows.
 */
public abstract class HerderGoal<T extends Animal> extends WorkGoal {
    private enum State { PREPARE, GATHER, WORK, COLLECT, DEPOSIT, IDLE }

    private final Class<T> animalType;
    private State state = State.PREPARE;
    @Nullable
    private T target;
    @Nullable
    private ItemEntity loot;
    private boolean breeding;
    private int timer;
    /** Adult animals counted on the last round. */
    protected int herdCount;

    protected HerderGoal(CitizenEntity citizen, JobType job, Class<T> animalType) {
        super(citizen, job);
        this.animalType = animalType;
    }

    /** Fetches what the herder needs from the hut; returns false if it cannot tend at all. */
    protected abstract boolean prepare();

    /** Whether the animal can be tended right now. */
    protected abstract boolean canTend(T animal);

    /** Tends the animal. Returns true if items were dropped that should be picked up. */
    protected abstract boolean tend(T animal);

    protected abstract String workStatus();

    protected abstract String missingToolStatus();

    /** Produce lying on the ground that the herder picks up (eggs, wool, ...). */
    protected boolean isLoot(ItemStack stack) {
        return false;
    }

    /** What the animals are bred with; wheat unless the herder says otherwise. */
    protected boolean isBreedingFood(ItemStack stack) {
        return stack.is(Items.WHEAT);
    }

    private int breedingFood() {
        int count = 0;
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            ItemStack stack = citizen.inventory().getItem(i);
            if (isBreedingFood(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Maximum number of adult animals the herder keeps. */
    protected int herdSize(BuildingData building) {
        int chosen = HutSettings.herdSize(building);
        if (chosen > 0) {
            return chosen;
        }
        return 2 + 2 * building.level();
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
                boolean canTend = prepare();
                takeFromHut(this::isBreedingFood, 4);
                List<T> herd = level().getEntitiesOfClass(animalType, new AABB(hut).inflate(building.workRadius()),
                        animal -> animal.isAlive() && !animal.isBaby());
                herdCount = herd.size();
                loot = level().getEntitiesOfClass(ItemEntity.class, new AABB(hut).inflate(building.workRadius()),
                        item -> item.isAlive() && isLoot(item.getItem())).stream()
                        .min(Comparator.comparingDouble(item -> item.distanceToSqr(citizen))).orElse(null);
                if (loot != null && !inventoryFull()) {
                    timer = 0;
                    state = State.GATHER;
                    return;
                }
                target = null;
                if (canTend) {
                    target = nearest(herd.stream().filter(this::canTend).toList());
                    breeding = false;
                }
                if (target == null && HutSettings.isOn(building, HutSettings.BREED)
                        && herd.size() >= 2 && herd.size() < herdSize(building)
                        && breedingFood() >= 2) {
                    target = nearest(herd.stream().filter(a -> a.canFallInLove() && a.getAge() == 0).toList());
                    breeding = true;
                }
                if (target == null) {
                    citizen.setStatus(herd.isEmpty() ? "No animals nearby" : canTend ? "Tending the herd" : missingToolStatus());
                    timer = 200;
                    state = inventoryEmpty() ? State.IDLE : State.DEPOSIT;
                    return;
                }
                timer = 0;
                state = State.WORK;
            }
            case GATHER -> {
                if (loot == null || !loot.isAlive()) {
                    state = State.PREPARE;
                    return;
                }
                citizen.setStatus("Gathering produce");
                if (walkTo(loot.blockPosition(), 1.5) || ++timer > 600) {
                    collectNearbyItems(2);
                    state = State.PREPARE;
                }
            }
            case WORK -> {
                if (target == null || !target.isAlive()) {
                    state = State.PREPARE;
                    return;
                }
                if (!walkTo(target.blockPosition(), 2.5)) {
                    citizen.setStatus(breeding ? "Feeding the animals" : workStatus());
                    // Animals wander; pick a new target if this one takes too long to reach.
                    if (++timer > 600) {
                        state = State.PREPARE;
                    }
                    return;
                }
                citizen.getLookControl().setLookAt(target);
                citizen.swing(InteractionHand.MAIN_HAND);
                if (breeding) {
                    if (target.canFallInLove()) {
                        target.setInLove(null);
                        removeOne(this::isBreedingFood);
                    }
                } else if (canTend(target) && tend(target)) {
                    // Dropped items can only be picked up after a short delay.
                    timer = 15;
                    state = State.COLLECT;
                    return;
                }
                state = inventoryFull() ? State.DEPOSIT : State.PREPARE;
            }
            case COLLECT -> {
                if (--timer > 0) {
                    return;
                }
                collectNearbyItems(4);
                state = State.DEPOSIT;
            }
            case DEPOSIT -> {
                if (!walkTo(hut, 3)) {
                    citizen.setStatus("Bringing the produce home");
                    return;
                }
                depositAll();
                state = State.IDLE;
            }
            case IDLE -> {
                if (--timer <= 0) {
                    state = State.PREPARE;
                }
            }
        }
    }

    @Nullable
    private T nearest(List<T> animals) {
        return animals.stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(citizen))).orElse(null);
    }

    protected void removeOne(Item item) {
        removeOne(stack -> stack.is(item));
    }

    protected void removeOne(java.util.function.Predicate<ItemStack> matcher) {
        for (int i = 0; i < citizen.inventory().getContainerSize(); i++) {
            if (matcher.test(citizen.inventory().getItem(i))) {
                citizen.inventory().getItem(i).shrink(1);
                return;
            }
        }
    }
}
