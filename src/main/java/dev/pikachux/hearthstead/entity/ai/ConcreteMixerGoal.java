package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Crafting;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete mixer: mixes sand, gravel and a dye into concrete powder (with the game's
 * crafting recipe) and wets the powder into solid concrete.
 */
public class ConcreteMixerGoal extends WorkGoal {
    private static final int WORK_TICKS = 80;

    private int timer;

    public ConcreteMixerGoal(CitizenEntity citizen) {
        super(citizen, JobType.CONCRETE_MIXER);
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
            citizen.setStatus(Status.of("going_workshop"));
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus(Status.of("waiting_workshop_be_built"));
            return;
        }
        if (++timer < breakDelay(WORK_TICKS)) {
            if (timer % 10 == 0) {
                citizen.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        timer = 0;
        List<Container> storage = Storage.containers(level(), building);
        if ((dev.pikachux.hearthstead.colony.HutSettings.isOn(building, dev.pikachux.hearthstead.colony.HutSettings.SET_CONCRETE) && setConcrete(storage, hut)) || mixPowder(storage)) {
            return;
        }
        citizen.setStatus(Status.of("needs_sand_gravel_dye"));
    }

    /** Wets up to eight concrete powder into concrete. */
    private boolean setConcrete(List<Container> storage, BlockPos hut) {
        for (Container container : storage) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!Logistics.isConcretePowder(stack)) {
                    continue;
                }
                Item concrete = concreteFor(stack.getItem());
                if (concrete == Items.AIR) {
                    continue;
                }
                int amount = Math.min(8, stack.getCount());
                Storage.extract(storage, s -> s.is(stack.getItem()), amount);
                Storage.insert(storage, new ItemStack(concrete, amount));
                level().sendParticles(ParticleTypes.SPLASH, hut.getX() + 0.5, hut.getY() + 1, hut.getZ() + 0.5, 10, 0.3, 0.1, 0.3, 0);
                level().playSound(null, hut, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.0F);
                citizen.setStatus(Status.of("setting_concrete"));
                return true;
            }
        }
        return false;
    }

    /** Mixes four sand, four gravel and a dye into eight concrete powder. */
    private boolean mixPowder(List<Container> storage) {
        if (Storage.count(storage, Logistics::isSand) < 4 || Storage.count(storage, s -> s.is(Items.GRAVEL)) < 4) {
            return false;
        }
        Item sand = firstItem(storage, Logistics::isSand);
        Item dye = firstItem(storage, Logistics::isDye);
        if (sand == null || dye == null) {
            return false;
        }
        List<ItemStack> grid = new ArrayList<>();
        grid.add(new ItemStack(dye));
        for (int i = 0; i < 4; i++) {
            grid.add(new ItemStack(sand));
            grid.add(new ItemStack(Items.GRAVEL));
        }
        ItemStack powder = Crafting.result(level(), 3, 3, grid);
        if (powder.isEmpty()) {
            return false;
        }
        Storage.extract(storage, s -> s.is(sand), 4);
        Storage.extract(storage, s -> s.is(Items.GRAVEL), 4);
        Storage.extract(storage, s -> s.is(dye), 1);
        Storage.insert(storage, powder);
        citizen.setStatus(Status.of("mixing_concrete_powder"));
        return true;
    }

    private static Item firstItem(List<Container> storage, java.util.function.Predicate<ItemStack> matcher) {
        for (Container container : storage) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && matcher.test(stack)) {
                    return stack.getItem();
                }
            }
        }
        return null;
    }

    /** The concrete a powder hardens into: "red_concrete_powder" becomes "red_concrete". */
    public static Item concreteFor(Item powder) {
        Identifier id = BuiltInRegistries.ITEM.getKey(powder);
        if (!id.getPath().endsWith("_powder")) {
            return Items.AIR;
        }
        Identifier concrete = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - "_powder".length()));
        return BuiltInRegistries.ITEM.getValue(concrete);
    }
}
