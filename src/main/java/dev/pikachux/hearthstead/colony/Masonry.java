package dev.pikachux.hearthstead.colony;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.List;

/**
 * The stonemason's craft: cutting stone into building parts with the game's own
 * stonecutter recipes (stone bricks, stairs, slabs, walls, polished and chiseled blocks).
 */
public final class Masonry {
    private Masonry() {
    }

    /** What one block of the input becomes when cut into the wanted item, or empty. */
    public static ItemStack cut(ServerLevel level, Item input, Item wanted) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(new ItemStack(input));
        for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
            if (holder.value() instanceof StonecutterRecipe recipe && recipe.matches(recipeInput, level)) {
                ItemStack result = recipe.assemble(recipeInput, level.registryAccess());
                if (result.is(wanted)) {
                    return result;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Cuts one block from storage into the wanted item. Returns what was made, or empty. */
    public static ItemStack craft(ServerLevel level, List<Container> storage, Item wanted) {
        for (Item input : Logistics.MASONRY) {
            if (Storage.count(storage, s -> s.is(input)) == 0) {
                continue;
            }
            ItemStack result = cut(level, input, wanted);
            if (!result.isEmpty()) {
                Storage.extract(storage, s -> s.is(input), 1);
                ItemStack rest = Storage.insert(storage, result.copy());
                return rest.isEmpty() ? result : ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }
}
