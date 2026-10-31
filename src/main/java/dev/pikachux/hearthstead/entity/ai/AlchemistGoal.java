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
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

/**
 * Alchemist: fills glass bottles with water, brews them into awkward potions with nether wart
 * and finishes those with whatever brewing ingredient the hut holds (sugar for swiftness, a
 * glistering melon slice for healing, ...), three bottles at a time. Blaze powder fuels the
 * brewing; one lasts twenty batches. Finished potions are never brewed further.
 */
public class AlchemistGoal extends WorkGoal {
    private static final int WORK_TICKS = 160;
    private static final int BATCHES_PER_BLAZE_POWDER = 20;

    private int timer;
    private int fuel;

    public AlchemistGoal(CitizenEntity citizen) {
        super(citizen, JobType.ALCHEMIST);
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
            citizen.setStatus("Going to the laboratory");
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus("Waiting for the laboratory to be built");
            return;
        }
        if (++timer < breakDelay(WORK_TICKS)) {
            if (timer % 20 == 0) {
                citizen.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        timer = 0;
        List<Container> storage = Storage.containers(level(), building);
        if (fillBottles(storage, hut)) {
            return;
        }
        if (fuel <= 0) {
            if (Storage.extract(storage, s -> s.is(Items.BLAZE_POWDER), 1).isEmpty()) {
                citizen.setStatus("Needs blaze powder to brew");
                return;
            }
            fuel = BATCHES_PER_BLAZE_POWDER;
        }
        if (brew(storage, hut)) {
            fuel--;
            return;
        }
        citizen.setStatus("Needs bottles, nether wart and ingredients");
    }

    /** Fills up to three glass bottles with water. */
    private boolean fillBottles(List<Container> storage, BlockPos hut) {
        int bottles = Storage.extract(storage, s -> s.is(Items.GLASS_BOTTLE), 3).stream().mapToInt(ItemStack::getCount).sum();
        if (bottles == 0) {
            return false;
        }
        for (int i = 0; i < bottles; i++) {
            Storage.insert(storage, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        }
        level().playSound(null, hut, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.6F, 1.0F);
        citizen.setStatus("Filling bottles");
        return true;
    }

    /** Brews up to three bottles of a water or awkward potion with one ingredient. */
    private boolean brew(List<Container> storage, BlockPos hut) {
        PotionBrewing brewing = level().potionBrewing();
        for (Container container : storage) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack base = container.getItem(i);
                if (!isBasePotion(base)) {
                    continue;
                }
                boolean water = isPotion(base, Potions.WATER);
                for (Container other : storage) {
                    for (int j = 0; j < other.getContainerSize(); j++) {
                        ItemStack ingredient = other.getItem(j);
                        // Water only becomes awkward; anything else would make a useless potion.
                        if (ingredient.isEmpty() || (water && !ingredient.is(Items.NETHER_WART))
                                || !brewing.isIngredient(ingredient) || !brewing.hasMix(base, ingredient)) {
                            continue;
                        }
                        ItemStack sample = base.copyWithCount(1);
                        int bottles = Math.min(3, Storage.count(storage, s -> ItemStack.isSameItemSameComponents(s, sample)));
                        ItemStack result = brewing.mix(ingredient, sample);
                        Storage.extract(storage, s -> ItemStack.isSameItemSameComponents(s, sample), bottles);
                        ingredient.shrink(1);
                        other.setChanged();
                        for (int b = 0; b < bottles; b++) {
                            Storage.insert(storage, result.copyWithCount(1));
                        }
                        level().sendParticles(ParticleTypes.WITCH, hut.getX() + 0.5, hut.getY() + 1.2, hut.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.1);
                        level().playSound(null, hut, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 1.0F);
                        citizen.setStatus("Brewing potions");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Water bottles and awkward potions: what the alchemist brews further. */
    public static boolean isBasePotion(ItemStack stack) {
        return isPotion(stack, Potions.WATER) || isPotion(stack, Potions.AWKWARD);
    }

    private static boolean isPotion(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        if (!stack.is(Items.POTION)) {
            return false;
        }
        PotionContents contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(potion);
    }

    /** Items the alchemist works with and keeps in the hut. */
    public static boolean isMaterial(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE) || stack.is(Items.NETHER_WART) || stack.is(Items.BLAZE_POWDER)
                || Logistics.isBrewingIngredient(stack) || isBasePotion(stack);
    }
}
