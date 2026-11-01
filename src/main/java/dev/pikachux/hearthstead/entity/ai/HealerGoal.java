package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;

import dev.pikachux.hearthstead.colony.Illness;
import dev.pikachux.hearthstead.colony.Storage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Healer: works at the hospital and tends to hurt citizens around it. Badly hurt citizens come
 * to the hospital on their own ({@link SeekHealingGoal}).
 */
public class HealerGoal extends WorkGoal {
    private static final int TREAT_TICKS = 40;

    private int timer;

    public HealerGoal(CitizenEntity citizen) {
        super(citizen, JobType.HEALER);
    }

    /** Cures the sick citizens at the hospital that it has a cure for; returns how many. */
    private int cure(BuildingData building, Colony colony) {
        List<CitizenEntity> sick = level().getEntitiesOfClass(CitizenEntity.class,
                new AABB(building.pos()).inflate(building.workRadius()),
                other -> other.isAlive() && other.colony() == colony && other.data() != null && other.data().sick());
        int cured = 0;
        for (CitizenEntity patient : sick) {
            Illness illness = patient.data().illness();
            List<net.minecraft.world.Container> storage = Storage.containers(level(), building);
            if (Storage.extract(storage, s -> s.is(illness.cure()), 1).isEmpty()) {
                continue;
            }
            // Bottles and buckets go back on the shelf.
            if (illness.cure() == Items.HONEY_BOTTLE) {
                Storage.insert(storage, new ItemStack(Items.GLASS_BOTTLE));
            } else if (illness.cure() == Items.MILK_BUCKET) {
                Storage.insert(storage, new ItemStack(Items.BUCKET));
            }
            patient.data().cure();
            colony.markDirty();
            cured++;
            level().sendParticles(ParticleTypes.HAPPY_VILLAGER, patient.getX(), patient.getY() + 1.5, patient.getZ(), 8, 0.4, 0.4, 0.4, 0);
            colony.broadcast(level().getServer(), Component.translatable("message.hearthstead.cured", citizen.data().name(),
                    patient.data().name(), Component.translatable("illness.hearthstead." + illness.id()))
                    .withStyle(ChatFormatting.GREEN));
            citizen.setStatus("Cured " + patient.data().name());
        }
        return cured;
    }

    private String waitingForCures(BuildingData building, Colony colony) {
        long sick = colony.citizens().stream().filter(c -> c.sick()).count();
        return sick == 0 ? "No one needs care" : "Waiting for cures for " + sick + (sick == 1 ? " sick citizen" : " sick citizens");
    }

    /** Health restored per treatment: 2, plus 1 for every two hut levels. */
    public static float healing(int hutLevel) {
        return 2 + hutLevel / 2;
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos hut = hutPos();
        BuildingData building = building();
        Colony colony = citizen.colony();
        if (hut == null || building == null || colony == null) {
            return;
        }
        if (!walkTo(hut, 3)) {
            citizen.setStatus("Going to the hospital");
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the hospital to be built");
            return;
        }
        if (++timer < breakDelay(TREAT_TICKS)) {
            return;
        }
        timer = 0;
        int cured = cure(building, colony);
        List<CitizenEntity> patients = level().getEntitiesOfClass(CitizenEntity.class,
                new AABB(hut).inflate(building.workRadius()),
                other -> other != citizen && other.isAlive() && other.getHealth() < other.getMaxHealth() && other.colony() == colony);
        if (patients.isEmpty()) {
            if (cured == 0) {
                citizen.setStatus(waitingForCures(building, colony));
            }
            return;
        }
        for (CitizenEntity patient : patients) {
            patient.heal(healing(building.level()));
            level().sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + 2, patient.getZ(), 2, 0.3, 0.2, 0.3, 0);
        }
        citizen.swing(InteractionHand.MAIN_HAND);
        citizen.setStatus("Tending to " + patients.size() + (patients.size() == 1 ? " patient" : " patients"));
    }
}
