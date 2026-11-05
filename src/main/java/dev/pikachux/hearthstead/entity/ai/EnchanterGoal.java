package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.List;

/**
 * Enchanter: enchants books with lapis lazuli, as at an enchanting table. A higher hut level
 * gives stronger enchantments and costs more lapis.
 */
public class EnchanterGoal extends WorkGoal {
    private static final int WORK_TICKS = 200;

    private int timer;

    public EnchanterGoal(CitizenEntity citizen) {
        super(citizen, JobType.ENCHANTER);
    }

    /** Enchantment power for a hut level: about 9 at level 1 up to 30 at level 5. */
    public static int power(int hutLevel) {
        return Math.min(30, 4 + hutLevel * 5);
    }

    /** The level the enchanter works at: the hut's, or level 1 when it is set to save lapis. */
    public static int workLevel(BuildingData building) {
        return "cheap".equals(dev.pikachux.hearthstead.colony.HutSettings.get(building, dev.pikachux.hearthstead.colony.HutSettings.ENCHANT_POWER)) ? 1 : building.level();
    }

    /** Lapis lazuli used per book: 1 at levels 1-2, 2 at levels 3-4, 3 at level 5. */
    public static int lapisCost(int hutLevel) {
        return Math.min(3, 1 + (hutLevel - 1) / 2);
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
            citizen.setStatus(Status.of("going_enchanting_room"));
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus(Status.of("waiting_hut_be_built"));
            return;
        }
        List<Container> storage = Storage.containers(level(), building);
        int lapis = lapisCost(workLevel(building));
        if (Storage.count(storage, s -> s.is(Items.BOOK)) == 0 || Storage.count(storage, s -> s.is(Items.LAPIS_LAZULI)) < lapis) {
            citizen.setStatus(Status.of("needs_books_lapis_lazuli"));
            timer = 0;
            return;
        }
        citizen.setStatus(Status.of("enchanting_books"));
        if (++timer < breakDelay(WORK_TICKS)) {
            if (timer % 20 == 0) {
                citizen.swing(InteractionHand.MAIN_HAND);
                level().sendParticles(ParticleTypes.ENCHANT, hut.getX() + 0.5, hut.getY() + 1.5, hut.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.6);
            }
            return;
        }
        timer = 0;
        var enchantments = level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
        ItemStack book = EnchantmentHelper.enchantItem(citizen.getRandom(), new ItemStack(Items.BOOK), power(workLevel(building)),
                level().registryAccess(), enchantments);
        if (!book.is(Items.ENCHANTED_BOOK)) {
            return;
        }
        Storage.extract(storage, s -> s.is(Items.BOOK), 1);
        Storage.extract(storage, s -> s.is(Items.LAPIS_LAZULI), lapis);
        ItemStack rest = Storage.insert(storage, book);
        if (!rest.isEmpty()) {
            storeInHut(rest);
        }
        level().playSound(null, hut, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
    }
}
