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
            List<Optional<Ingredient>> slots;
            int width;
            int height;
            if (holder.value() instanceof ShapedRecipe shaped) {
                slots = shaped.getIngredients();
                width = shaped.getWidth();
                height = shaped.getHeight();
            } else if (holder.value() instanceof ShapelessRecipe shapeless) {
                List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
                if (ingredients.isEmpty() || ingredients.size() > 9) {
                    continue;
                }
                width = Math.min(3, ingredients.size());
                height = (ingredients.size() + 2) / 3;
                slots = new ArrayList<>();
                ingredients.forEach(i -> slots.add(Optional.of(i)));
                while (slots.size() < width * height) {
                    slots.add(Optional.empty());
                }
            } else {
                continue;
            }
            List<ItemStack> grid = pick(storage, slots);
            if (grid == null) {
                continue;
            }
            ItemStack result = result(level, width, height, grid);
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
