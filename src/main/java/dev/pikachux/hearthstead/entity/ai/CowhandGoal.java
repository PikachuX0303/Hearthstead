package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cowhand: milks the cows around the hut into buckets from the hut and breeds them with
 * wheat while the herd is smaller than the hut allows. Each cow can be milked once every
 * few minutes.
 */
public class CowhandGoal extends HerderGoal<Cow> {
    private static final long MILKING_COOLDOWN = 20 * 60 * 3;

    private final Map<UUID, Long> lastMilked = new HashMap<>();

    public CowhandGoal(CitizenEntity citizen) {
        super(citizen, JobType.COWHAND, Cow.class);
    }

    @Override
    protected boolean prepare() {
        takeFromHut(s -> s.is(Items.BUCKET), 4);
        return citizen.inventory().countItem(Items.BUCKET) > 0;
    }

    @Override
    protected boolean canTend(Cow cow) {
        return citizen.inventory().countItem(Items.BUCKET) > 0
                && level().getGameTime() - lastMilked.getOrDefault(cow.getUUID(), -MILKING_COOLDOWN) >= MILKING_COOLDOWN;
    }

    @Override
    protected boolean tend(Cow cow) {
        removeOne(Items.BUCKET);
        ItemStack rest = citizen.inventory().addItem(new ItemStack(Items.MILK_BUCKET));
        if (!rest.isEmpty()) {
            citizen.spawnAtLocation(level(), rest);
        }
        level().playSound(null, cow.blockPosition(), SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        lastMilked.put(cow.getUUID(), level().getGameTime());
        return false;
    }

    @Override
    protected String workStatus() {
        return "Milking cows";
    }

    @Override
    protected String missingToolStatus() {
        return "Needs buckets in the hut";
    }
}
