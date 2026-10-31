package dev.pikachux.hearthstead.colony;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The sawmill's crafting: turns logs into planks, sticks and wooden building parts.
 * Results come from the game's own crafting recipes, so every wood type works.
 */
public final class Woodworking {
    /** A crafting grid layout: 'P' planks, 'S' stick, 'R' string, '.' empty. */
    private record Pattern(int width, int height, String cells) {
        int count(char c) {
            return (int) cells.chars().filter(ch -> ch == c).count();
        }
    }

    private static final List<Pattern> PATTERNS = List.of(
            new Pattern(1, 2, "PP"),         // sticks
            new Pattern(3, 1, "PPP"),        // slab
            new Pattern(3, 3, "P..PP.PPP"),  // stairs
            new Pattern(2, 3, "PPPPPP"),     // door
            new Pattern(3, 2, "PPPPPP"),     // trapdoor
            new Pattern(3, 2, "PSPPSP"),     // fence
            new Pattern(3, 2, "SPSSPS"),     // fence gate
            new Pattern(3, 2, "SRSSRS"));    // rope fence

    private Woodworking() {
    }

    /** The planks a log is sawn into, or empty. */
    public static ItemStack planksFrom(ServerLevel level, Item log) {
        ItemStack result = Crafting.result(level, 1, 1, List.of(new ItemStack(log)));
        return result.is(ItemTags.PLANKS) ? result : ItemStack.EMPTY;
    }

    private static ItemStack result(ServerLevel level, Pattern pattern, Item planks) {
        List<ItemStack> grid = new ArrayList<>();
        for (char c : pattern.cells().toCharArray()) {
            grid.add(switch (c) {
                case 'P' -> new ItemStack(planks);
                case 'S' -> new ItemStack(Items.STICK);
                case 'R' -> new ItemStack(Items.STRING);
                default -> ItemStack.EMPTY;
            });
        }
        return Crafting.result(level, pattern.width(), pattern.height(), grid);
    }

    /** Planks types that can be made from what is in storage. */
    private static Set<Item> planksTypes(ServerLevel level, List<Container> storage) {
        Set<Item> types = new LinkedHashSet<>();
        for (Container container : storage) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (stack.is(ItemTags.PLANKS)) {
                    types.add(stack.getItem());
                } else if (stack.is(ItemTags.LOGS)) {
                    ItemStack planks = planksFrom(level, stack.getItem());
                    if (!planks.isEmpty()) {
                        types.add(planks.getItem());
                    }
                }
            }
        }
        return types;
    }

    /** Saws logs until at least {@code amount} planks of the type are in storage. */
    private static boolean ensurePlanks(ServerLevel level, List<Container> storage, Item planks, int amount) {
        while (Storage.count(storage, s -> s.is(planks)) < amount) {
            Item log = null;
            for (Container container : storage) {
                for (int i = 0; i < container.getContainerSize() && log == null; i++) {
                    ItemStack stack = container.getItem(i);
                    if (stack.is(ItemTags.LOGS) && planksFrom(level, stack.getItem()).is(planks)) {
                        log = stack.getItem();
                    }
                }
            }
            if (log == null) {
                return false;
            }
            Item source = log;
            Storage.extract(storage, s -> s.is(source), 1);
            Storage.insert(storage, planksFrom(level, log));
        }
        return true;
    }

    private static boolean ensureSticks(ServerLevel level, List<Container> storage, Item planks, int amount) {
        while (Storage.count(storage, s -> s.is(Items.STICK)) < amount) {
            if (!ensurePlanks(level, storage, planks, 2)) {
                return false;
            }
            Storage.extract(storage, s -> s.is(planks), 2);
            Storage.insert(storage, result(level, PATTERNS.getFirst(), planks));
        }
        return true;
    }

    /**
     * Crafts one batch of the wanted item from the logs, planks and sticks in storage.
     * Returns the crafted stack, or empty if the item cannot be made from what is there.
     */
    public static ItemStack craft(ServerLevel level, List<Container> storage, Item wanted) {
        for (Item planks : planksTypes(level, storage)) {
            if (planks == wanted) {
                int before = Storage.count(storage, s -> s.is(planks));
                return ensurePlanks(level, storage, planks, before + 1)
                        ? new ItemStack(planks, Storage.count(storage, s -> s.is(planks)) - before) : ItemStack.EMPTY;
            }
            for (Pattern pattern : PATTERNS) {
                ItemStack result = result(level, pattern, planks);
                if (!result.is(wanted)) {
                    continue;
                }
                int planksNeeded = pattern.count('P');
                int sticksNeeded = pattern.count('S');
                int stringNeeded = pattern.count('R');
                if (Storage.count(storage, s -> s.is(Items.STRING)) < stringNeeded
                        || !ensureSticks(level, storage, planks, sticksNeeded) || !ensurePlanks(level, storage, planks, planksNeeded)) {
                    return ItemStack.EMPTY;
                }
                Storage.extract(storage, s -> s.is(planks), planksNeeded);
                Storage.extract(storage, s -> s.is(Items.STICK), sticksNeeded);
                Storage.extract(storage, s -> s.is(Items.STRING), stringNeeded);
                ItemStack rest = Storage.insert(storage, result.copy());
                return rest.isEmpty() ? result : ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }
}
