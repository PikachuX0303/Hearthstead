package dev.pikachux.hearthstead.colony;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/**
 * Looks up the game's crafting recipes for a grid of items, so crafting jobs work with
 * vanilla and data pack recipes alike.
 */
public final class Crafting {
    private Crafting() {
    }

    /** What a crafting table makes from the grid (row by row), or empty. */
    public static ItemStack result(ServerLevel level, int width, int height, List<ItemStack> grid) {
        CraftingInput input = CraftingInput.of(width, height, grid);
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(recipe -> recipe.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }
}
