package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Fisherman: finds open water near the hut, fishes with a rod from the hut and
 * brings the catch home.
 */
public class FishermanGoal extends WorkGoal {
    private static final int CATCHES_PER_TRIP = 6;

    private enum State { PREPARE, SEARCH, FISH, DEPOSIT, IDLE }

    private State state = State.PREPARE;
    @Nullable
    private BlockPos water;
    @Nullable
    private BlockPos spot;
    private int catches;
    private int timer;
    private int biteTime;

    public FishermanGoal(CitizenEntity citizen) {
        super(citizen, JobType.FISHERMAN);
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
                if (!equipTool(Logistics::isFishingRod)) {
                    citizen.setStatus(Status.of("needs_fishing_rod_hut"));
                    idle(200);
                    return;
                }
                catches = 0;
                state = State.SEARCH;
            }
            case SEARCH -> {
                water = findWater(hut, building.workRadius());
                spot = water == null ? null : standingSpot(water);
                if (water == null || spot == null) {
                    citizen.setStatus(Status.of("no_open_water_nearby"));
                    idle(400);
                    return;
                }
                startCast();
                state = State.FISH;
            }
            case FISH -> fish();
            case DEPOSIT -> {
                if (!walkTo(hut, 3)) {
                    citizen.setStatus(Status.of("bringing_catch_home"));
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

    private void fish() {
        if (water == null || spot == null) {
            state = State.SEARCH;
            return;
        }
        if (!walkTo(spot, 1.5)) {
            citizen.setStatus(Status.of("walking_water"));
            return;
        }
        if (!Logistics.isFishingRod(citizen.getMainHandItem())) {
            state = State.DEPOSIT;
            return;
        }
        citizen.getLookControl().setLookAt(water.getCenter());
        citizen.setStatus(Status.of("fishing"));
        Vec3 bobber = Vec3.atCenterOf(water).add(0, 0.4, 0);
        if (++timer % 20 == 0) {
            level().sendParticles(ParticleTypes.FISHING, bobber.x, bobber.y, bobber.z, 2, 0.1, 0, 0.1, 0);
        }
        if (timer < biteTime) {
            return;
        }
        level().sendParticles(ParticleTypes.SPLASH, bobber.x, bobber.y, bobber.z, 12, 0.2, 0, 0.2, 0);
        level().playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
        citizen.swing(InteractionHand.MAIN_HAND);
        ItemStack rod = citizen.getMainHandItem();
        LootParams params = new LootParams.Builder(level())
                .withParameter(LootContextParams.ORIGIN, bobber)
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, citizen)
                .withLuck(0)
                .create(LootContextParamSets.FISHING);
        LootTable table = level().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        boolean fishOnly = "fish".equals(dev.pikachux.hearthstead.colony.HutSettings.get(building(), dev.pikachux.hearthstead.colony.HutSettings.CATCH));
        for (ItemStack loot : table.getRandomItems(params)) {
            if (fishOnly && !loot.is(net.minecraft.tags.ItemTags.FISHES)) {
                continue; // thrown back
            }
            ItemStack rest = citizen.inventory().addItem(loot);
            if (!rest.isEmpty()) {
                citizen.spawnAtLocation(level(), rest);
            }
        }
        rod.hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
        if (++catches >= CATCHES_PER_TRIP || inventoryFull()) {
            state = State.DEPOSIT;
        } else {
            startCast();
        }
    }

    private void startCast() {
        timer = 0;
        biteTime = breakDelay(100 + citizen.getRandom().nextInt(200));
        if (water != null) {
            level().playSound(null, citizen.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.6F);
        }
    }

    /** A water source with open sky above and plenty of water around it. */
    @Nullable
    private BlockPos findWater(BlockPos hut, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -4; y <= 2; y++) {
                    pos.set(hut.getX() + x, hut.getY() + y, hut.getZ() + z);
                    if (!level().getFluidState(pos).is(FluidTags.WATER) || !level().getFluidState(pos).isSource()
                            || !level().getBlockState(pos.above()).isAir() || !isOpenWater(pos)) {
                        continue;
                    }
                    double distance = pos.distSqr(hut);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean isOpenWater(BlockPos center) {
        int water = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 0, 2))) {
            if (level().getFluidState(pos).is(FluidTags.WATER)) {
                water++;
            }
        }
        return water >= 12;
    }

    private void idle(int ticks) {
        timer = ticks;
        state = State.IDLE;
    }
}
