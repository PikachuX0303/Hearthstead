package dev.pikachux.hearthstead.colony;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Looks up the game's crafting recipes for a grid of items, so crafting jobs work with
 * vanilla and data pack recipes alike.
 */
public final class Crafting {
    private Crafting() {
    }

    /**
     * Crafts one batch of {@code wanted} with any shaped or shapeless crafting recipe whose
     * ingredients are all in storage. Returns what was made, or empty.
     */
    public static ItemStack craftFromStorage(ServerLevel level, List<Container> storage, Item wanted) {
        for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
            Grid recipe = grid(holder);
            if (recipe == null) {
                continue;
            }
            List<ItemStack> grid = pick(storage, recipe.slots());
            if (grid == null) {
                continue;
            }
            ItemStack result = result(level, recipe.width(), recipe.height(), grid);
            if (!result.is(wanted)) {
                continue;
            }
            for (ItemStack used : grid) {
                if (!used.isEmpty()) {
                    Storage.extract(storage, s -> ItemStack.isSameItemSameComponents(s, used), 1);
                }
            }
            ItemStack rest = Storage.insert(storage, result.copy());
            return rest.isEmpty() ? result : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /** A crafting table recipe laid out as a grid of up to 3 by 3 ingredient slots. */
    private record Grid(int width, int height, List<Optional<Ingredient>> slots) {
    }

    @Nullable
    private static Grid grid(RecipeHolder<?> holder) {
        if (holder.value() instanceof ShapedRecipe shaped) {
            return new Grid(shaped.getWidth(), shaped.getHeight(), shaped.getIngredients());
        }
        if (holder.value() instanceof ShapelessRecipe shapeless) {
            List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
            if (ingredients.isEmpty() || ingredients.size() > 9) {
                return null;
            }
            int width = Math.min(3, ingredients.size());
            int height = (ingredients.size() + 2) / 3;
            List<Optional<Ingredient>> slots = new ArrayList<>();
            ingredients.forEach(i -> slots.add(Optional.of(i)));
            while (slots.size() < width * height) {
                slots.add(Optional.empty());
            }
            return new Grid(width, height, slots);
        }
        return null;
    }

    /** The crafting table recipes that make {@code item} (checked by crafting it from sample ingredients). */
    private static List<Grid> recipesFor(ServerLevel level, Item item) {
        List<Grid> found = new ArrayList<>();
        for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
            Grid recipe = grid(holder);
            if (recipe == null) {
                continue;
            }
            List<ItemStack> sample = new ArrayList<>();
            boolean complete = true;
            for (Optional<Ingredient> slot : recipe.slots()) {
                if (slot.isEmpty()) {
                    sample.add(ItemStack.EMPTY);
                    continue;
                }
                Optional<net.minecraft.core.Holder<Item>> first = slot.get().items().findFirst();
                if (first.isEmpty()) {
                    complete = false;
                    break;
                }
                sample.add(new ItemStack(first.get()));
            }
            if (complete && result(level, recipe.width(), recipe.height(), sample).is(item)) {
                found.add(recipe);
            }
        }
        return found;
    }

    /** Every ingredient of every crafting table recipe that makes the item. */
    public static List<Ingredient> ingredients(ServerLevel level, Item item) {
        List<Ingredient> all = new ArrayList<>();
        for (Grid recipe : recipesFor(level, item)) {
            recipe.slots().forEach(slot -> slot.ifPresent(all::add));
        }
        return all;
    }

    /** Whether a crafting table can make the item at all. */
    public static boolean hasRecipe(ServerLevel level, Item item) {
        return !recipesFor(level, item).isEmpty();
    }

    /**
     * What storage lacks to craft {@code item} once: the ingredients of the recipe that needs the
     * fewest extra items, each with how many are missing. Empty when it can be crafted now (or
     * when there is no recipe at all).
     */
    public static Map<Ingredient, Integer> missing(ServerLevel level, List<Container> storage, Item item) {
        Map<Ingredient, Integer> best = null;
        for (Grid recipe : recipesFor(level, item)) {
            Map<Item, Integer> used = new HashMap<>();
            Map<Ingredient, Integer> lacking = new java.util.LinkedHashMap<>();
            for (Optional<Ingredient> slot : recipe.slots()) {
                if (slot.isEmpty()) {
                    continue;
                }
                Item have = null;
                for (Container container : storage) {
                    for (int i = 0; i < container.getContainerSize() && have == null; i++) {
                        ItemStack stack = container.getItem(i);
                        if (!stack.isEmpty() && slot.get().test(stack)
                                && Storage.count(storage, s -> s.is(stack.getItem())) > used.getOrDefault(stack.getItem(), 0)) {
                            have = stack.getItem();
                        }
                    }
                }
                if (have != null) {
                    used.merge(have, 1, Integer::sum);
                } else {
                    lacking.merge(slot.get(), 1, Integer::sum);
                }
            }
            if (best == null || lacking.values().stream().mapToInt(Integer::intValue).sum()
                    < best.values().stream().mapToInt(Integer::intValue).sum()) {
                best = lacking;
            }
        }
        return best == null ? Map.of() : best;
    }

    /** One item from storage for every filled slot, or null if storage cannot fill them all. */
    @Nullable
    private static List<ItemStack> pick(List<Container> storage, List<Optional<Ingredient>> slots) {
        Map<Item, Integer> used = new HashMap<>();
        List<ItemStack> grid = new ArrayList<>();
        for (Optional<Ingredient> slot : slots) {
            if (slot.isEmpty()) {
                grid.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack found = null;
            search:
            for (Container container : storage) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (!stack.isEmpty() && slot.get().test(stack)
                            && Storage.count(storage, s -> s.is(stack.getItem())) > used.getOrDefault(stack.getItem(), 0)) {
                        found = stack.copyWithCount(1);
                        break search;
                    }
                }
            }
            if (found == null) {
                return null;
            }
            used.merge(found.getItem(), 1, Integer::sum);
            grid.add(found);
        }
        return grid;
    }

    /** What a crafting table makes from the grid (row by row), or empty. */
    public static ItemStack result(ServerLevel level, int width, int height, List<ItemStack> grid) {
        CraftingInput input = CraftingInput.of(width, height, grid);
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(recipe -> recipe.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }
}
