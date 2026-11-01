package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Netherworker: goes on expeditions to the Nether through the portal in the hut. With a sword
 * and food for the trip, they disappear for a while and come back with netherrack, quartz,
 * soul sand and gold; higher hut levels reach nether wart, glowstone, blaze rods and ghast tears.
 * Expeditions only start early enough in the day to be back before nightfall.
 */
public class NetherworkerGoal extends WorkGoal {
    /** Length of an expedition, before the work speed bonus. */
    public static final int TRIP_TICKS = 2400;
    public static final int FOOD = 8;
    private static final long DAY = 24000;
    private static final long LAST_DEPARTURE = 12000 - TRIP_TICKS;

    /** Something an expedition brings back: the hut level it needs and how many. */
    public record Loot(Item item, int minLevel, int min, int max, float chance) {
    }

    public static final List<Loot> LOOT = List.of(
            new Loot(Items.NETHERRACK, 1, 16, 32, 1F), new Loot(Items.QUARTZ, 1, 2, 8, 1F),
            new Loot(Items.SOUL_SAND, 1, 4, 12, 0.8F), new Loot(Items.GOLD_NUGGET, 1, 4, 12, 0.8F),
            new Loot(Items.NETHER_WART, 2, 1, 4, 0.8F), new Loot(Items.GLOWSTONE_DUST, 2, 2, 8, 0.8F),
            new Loot(Items.MAGMA_CREAM, 3, 1, 3, 0.5F), new Loot(Items.BLAZE_ROD, 4, 1, 2, 0.6F),
            new Loot(Items.GHAST_TEAR, 5, 1, 1, 0.3F));

    private boolean away;
    private long returnTime;

    public NetherworkerGoal(CitizenEntity citizen) {
        super(citizen, JobType.NETHERWORKER);
    }

    /** What one expedition from a hut of this level brings back. */
    public static List<ItemStack> expeditionLoot(int hutLevel, RandomSource random) {
        List<ItemStack> loot = new ArrayList<>();
        for (Loot entry : LOOT) {
            if (hutLevel >= entry.minLevel() && random.nextFloat() < entry.chance()) {
                loot.add(new ItemStack(entry.item(), entry.min() + random.nextInt(entry.max() - entry.min() + 1)));
            }
        }
        return loot;
    }

    @Override
    public void start() {
        resume();
        if (!away) {
            citizen.setInvisible(false);
        }
    }

    /** Picks up a trip that was under way when the game was saved. */
    private void resume() {
        var data = citizen.data();
        if (!away && data != null && data.awayUntil() > 0) {
            away = true;
            returnTime = data.awayUntil();
            citizen.setInvisible(true);
        }
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
        if (away) {
            // Called home early (nightfall, hunger, ...): back with what was found so far.
            comeBack();
        }
    }

    @Override
    public void tick() {
        BlockPos hut = hutPos();
        BuildingData building = building();
        if (hut == null || building == null) {
            return;
        }
        resume();
        if (away) {
            citizen.getNavigation().stop();
            long left = Math.max(0, returnTime - level().getGameTime());
            citizen.setStatus("On an expedition to the Nether (" + (left / 1200 + 1) + " min left)");
            if (left == 0) {
                comeBack();
            }
            return;
        }
        if (!walkTo(hut, 3)) {
            citizen.setStatus("Going to the portal");
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the Nether mine to be built");
            return;
        }
        if (!equipTool(ItemTags.SWORDS)) {
            citizen.setStatus("Needs a sword in the hut");
            return;
        }
        var storage = Storage.containers(level(), building);
        if (Storage.count(storage, Logistics::isMeal) < FOOD) {
            citizen.setStatus("Needs " + FOOD + " food for the expedition");
            return;
        }
        if (level().getDayTime() % DAY > LAST_DEPARTURE) {
            citizen.setStatus("Too late to set out today");
            return;
        }
        Storage.extract(storage, Logistics::isMeal, FOOD);
        away = true;
        returnTime = level().getGameTime() + breakDelay(TRIP_TICKS);
        if (citizen.data() != null) {
            citizen.data().setAwayUntil(returnTime);
        }
        citizen.setInvisible(true);
        level().sendParticles(ParticleTypes.PORTAL, citizen.getX(), citizen.getY() + 1, citizen.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
        level().playSound(null, hut, SoundEvents.PORTAL_TRIGGER, SoundSource.NEUTRAL, 0.3F, 1.2F);
    }

    private void comeBack() {
        away = false;
        if (citizen.data() != null) {
            citizen.data().setAwayUntil(0);
        }
        citizen.setInvisible(false);
        BuildingData building = building();
        if (building == null) {
            return;
        }
        for (ItemStack loot : expeditionLoot(building.level(), citizen.getRandom())) {
            storeInHut(loot);
        }
        ItemStack sword = citizen.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!sword.isEmpty() && sword.isDamageableItem()) {
            sword.hurtAndBreak(40, citizen, EquipmentSlot.MAINHAND);
        }
        if (citizen.getRandom().nextFloat() < 0.25F) {
            citizen.setHealth(Math.max(2F, citizen.getHealth() / 2));
        }
        level().sendParticles(ParticleTypes.PORTAL, citizen.getX(), citizen.getY() + 1, citizen.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
        citizen.setStatus("Back from the Nether");
    }
}
