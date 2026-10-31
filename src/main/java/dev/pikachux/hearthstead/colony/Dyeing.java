package dev.pikachux.hearthstead.colony;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The dyer's craft, using the game's crafting recipes: flowers into dyes, white wool
 * into coloured wool, and coloured wool and planks into beds.
 */
public final class Dyeing {
    private Dyeing() {
    }

    private static List<Item> itemsIn(List<Container> storage, java.util.function.Predicate<ItemStack> matcher) {
        List<Item> items = new ArrayList<>();
        for (Container container : storage) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && matcher.test(stack) && !items.contains(stack.getItem())) {
                    items.add(stack.getItem());
                }
            }
        }
        return items;
    }

    /** The dye a flower makes, or empty. */
    public static ItemStack dyeFrom(ServerLevel level, Item flower) {
        return Crafting.result(level, 1, 1, List.of(new ItemStack(flower)));
    }

    /** Crafts one batch of the wanted item from storage. Returns what was made, or empty. */
    public static ItemStack craft(ServerLevel level, List<Container> storage, Item wanted) {
        ItemStack sample = new ItemStack(wanted);
        if (sample.is(ItemTags.BEDS)) {
            return craftBed(level, storage, wanted);
        }
        if (sample.is(ItemTags.WOOL)) {
            return craftWool(level, storage, wanted);
        }
        for (Item flower : itemsIn(storage, Logistics::isFlower)) {
            ItemStack dye = dyeFrom(level, flower);
            if (dye.is(wanted)) {
                Storage.extract(storage, s -> s.is(flower), 1);
                return insert(storage, dye);
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack craftWool(ServerLevel level, List<Container> storage, Item wool) {
        if (Storage.count(storage, s -> s.is(Items.WHITE_WOOL)) == 0) {
            return ItemStack.EMPTY;
        }
        for (Item dye : BuiltInRegistries.ITEM) {
            ItemStack result = Crafting.result(level, 2, 1, List.of(new ItemStack(dye), new ItemStack(Items.WHITE_WOOL)));
            if (dye == Items.WHITE_WOOL || !result.is(wool)) {
                continue;
            }
            if (Storage.count(storage, s -> s.is(dye)) == 0 && craft(level, storage, dye).isEmpty()) {
                return ItemStack.EMPTY;
            }
            Storage.extract(storage, s -> s.is(dye), 1);
            Storage.extract(storage, s -> s.is(Items.WHITE_WOOL), 1);
            return insert(storage, result);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack craftBed(ServerLevel level, List<Container> storage, Item bed) {
        List<Item> planks = itemsIn(storage, s -> s.is(ItemTags.PLANKS));
        if (planks.isEmpty() || Storage.count(storage, s -> s.is(planks.getFirst())) < 3) {
            return ItemStack.EMPTY;
        }
        Item plank = planks.getFirst();
        for (Item wool : BuiltInRegistries.ITEM) {
            if (!new ItemStack(wool).is(ItemTags.WOOL)) {
                continue;
            }
            ItemStack result = Crafting.result(level, 3, 2, List.of(new ItemStack(wool), new ItemStack(wool), new ItemStack(wool),
                    new ItemStack(plank), new ItemStack(plank), new ItemStack(plank)));
            if (!result.is(bed)) {
                continue;
            }
            while (Storage.count(storage, s -> s.is(wool)) < 3) {
                if (craftWool(level, storage, wool).isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
            Storage.extract(storage, s -> s.is(wool), 3);
            Storage.extract(storage, s -> s.is(plank), 3);
            return insert(storage, result);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack insert(List<Container> storage, ItemStack result) {
        ItemStack rest = Storage.insert(storage, result.copy());
        return rest.isEmpty() ? result : ItemStack.EMPTY;
    }
}
