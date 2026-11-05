package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/**
 * Sifter: sifts gravel, sand or dirt (the hut's setting) one block at a time. Each block may
 * turn up a few finds; the chances grow with the hut level, and the rarer finds need a higher
 * level.
 */
public class SifterGoal extends WorkGoal {
    private static final int WORK_TICKS = 50;

    /** Something a sifted block can turn up: base chance, the hut level it needs, and up to how many. */
    public record Find(Item item, float chance, int minLevel, int max) {
    }

    public static final Map<Item, List<Find>> FINDS = Map.of(
            Items.GRAVEL, List.of(
                    new Find(Items.FLINT, 0.30F, 1, 2), new Find(Items.IRON_NUGGET, 0.15F, 1, 3),
                    new Find(Items.GOLD_NUGGET, 0.08F, 1, 2), new Find(Items.COAL, 0.10F, 2, 1),
                    new Find(Items.RAW_COPPER, 0.06F, 3, 1), new Find(Items.RAW_IRON, 0.04F, 4, 1),
                    new Find(Items.EMERALD, 0.01F, 5, 1), new Find(Items.DIAMOND, 0.005F, 5, 1)),
            Items.SAND, List.of(
                    new Find(Items.CLAY_BALL, 0.25F, 1, 2), new Find(Items.GOLD_NUGGET, 0.08F, 1, 2),
                    new Find(Items.CACTUS, 0.05F, 1, 1), new Find(Items.GLOWSTONE_DUST, 0.05F, 3, 2),
                    new Find(Items.PRISMARINE_SHARD, 0.03F, 4, 1)),
            Items.DIRT, List.of(
                    new Find(Items.WHEAT_SEEDS, 0.20F, 1, 2), new Find(Items.POTATO, 0.08F, 1, 1),
                    new Find(Items.CARROT, 0.08F, 1, 1), new Find(Items.BEETROOT_SEEDS, 0.08F, 1, 2),
                    new Find(Items.BONE, 0.10F, 1, 1), new Find(Items.OAK_SAPLING, 0.06F, 1, 1),
                    new Find(Items.SPRUCE_SAPLING, 0.04F, 2, 1), new Find(Items.BIRCH_SAPLING, 0.04F, 2, 1),
                    new Find(Items.PUMPKIN_SEEDS, 0.03F, 3, 1), new Find(Items.MELON_SEEDS, 0.03F, 3, 1)));

    private int timer;

    public SifterGoal(CitizenEntity citizen) {
        super(citizen, JobType.SIFTER);
    }

    /** The chance of a find at a hut level: the base chance, from 70% of it at level 1 to 110% at level 5. */
    public static float chance(Find find, int hutLevel) {
        return hutLevel < find.minLevel() ? 0 : find.chance() * (0.6F + 0.1F * hutLevel);
    }

    /** Sifts one block: what it turned up. */
    public static List<ItemStack> sift(Item input, int hutLevel, RandomSource random) {
        List<ItemStack> found = new java.util.ArrayList<>();
        for (Find find : FINDS.getOrDefault(input, List.of())) {
            if (random.nextFloat() < chance(find, hutLevel)) {
                found.add(new ItemStack(find.item(), 1 + random.nextInt(find.max())));
            }
        }
        return found;
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
        if (!walkTo(hut, 3)) {
            citizen.setStatus(Status.of("going_sieve"));
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus(Status.of("waiting_hut_be_built"));
            return;
        }
        Item input = HutSettings.sifterInput(building);
        List<Container> storage = Storage.containers(level(), building);
        if (Storage.count(storage, s -> s.is(input)) == 0) {
            citizen.setStatus(Status.of("needs_to_sift", input.getDefaultInstance().getHoverName()));
            timer = 0;
            return;
        }
        if (++timer < breakDelay(WORK_TICKS)) {
            if (timer % 10 == 0) {
                citizen.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        timer = 0;
        Storage.extract(storage, s -> s.is(input), 1);
        for (ItemStack find : sift(input, building.level(), citizen.getRandom())) {
            ItemStack rest = Storage.insert(storage, find);
            if (!rest.isEmpty()) {
                storeInHut(rest);
            }
        }
        level().playSound(null, hut, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.5F, 1.2F);
        citizen.setStatus(Status.of("sifting", input.getDefaultInstance().getHoverName()));
    }
}
