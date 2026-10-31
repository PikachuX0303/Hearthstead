package dev.pikachux.hearthstead.colony;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The glassblower's crafting, with the game's recipes: stained glass from eight glass
 * around a dye, and panes from six blocks of (stained) glass.
 */
public final class Glasswork {
    private Glasswork() {
    }

    private static ItemStack stained(ServerLevel level, Item dye) {
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, new ItemStack(Items.GLASS)));
        grid.set(4, new ItemStack(dye));
        return Crafting.result(level, 3, 3, grid);
    }

    private static ItemStack panes(ServerLevel level, Item glass) {
        return Crafting.result(level, 3, 2, Collections.nCopies(6, new ItemStack(glass)));
    }

    /** Crafts one batch of the wanted item from storage. Returns what was made, or empty. */
    public static ItemStack craft(ServerLevel level, List<Container> storage, Item wanted) {
        for (Item glass : glassTypes(level, storage)) {
            ItemStack result = panes(level, glass);
            if (result.is(wanted)) {
                if (!ensure(level, storage, glass, 6)) {
                    return ItemStack.EMPTY;
                }
                Storage.extract(storage, s -> s.is(glass), 6);
                return insert(storage, result);
            }
        }
        for (Item dye : dyes(storage)) {
            ItemStack result = stained(level, dye);
            if (result.is(wanted) && Storage.count(storage, s -> s.is(Items.GLASS)) >= 8) {
                Storage.extract(storage, s -> s.is(Items.GLASS), 8);
                Storage.extract(storage, s -> s.is(dye), 1);
                return insert(storage, result);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Glass and every stained glass that could be made from the dyes in storage. */
    private static List<Item> glassTypes(ServerLevel level, List<Container> storage) {
        List<Item> types = new ArrayList<>();
        types.add(Items.GLASS);
        for (Item dye : dyes(storage)) {
            ItemStack stained = stained(level, dye);
            if (!stained.isEmpty() && !types.contains(stained.getItem())) {
                types.add(stained.getItem());
            }
        }
        return types;
    }

    private static boolean ensure(ServerLevel level, List<Container> storage, Item glass, int amount) {
        while (Storage.count(storage, s -> s.is(glass)) < amount) {
            if (glass == Items.GLASS || craft(level, storage, glass).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<Item> dyes(List<Container> storage) {
        List<Item> dyes = new ArrayList<>();
        for (Container container : storage) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (Logistics.isDye(stack) && !dyes.contains(stack.getItem())) {
                    dyes.add(stack.getItem());
                }
            }
        }
        return dyes;
    }

    private static ItemStack insert(List<Container> storage, ItemStack result) {
        ItemStack rest = Storage.insert(storage, result.copy());
        return rest.isEmpty() ? result : ItemStack.EMPTY;
    }
}
