package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.registry.ModBlocks;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The colony's crafting huts and what each of them makes. Crafters work through the shortages
 * of every hut ({@link Logistics#shortages}); a request the warehouse cannot fill but a staffed
 * crafter can make shows as "being crafted".
 */
public final class Crafters {
    /** Huts that craft whatever other huts are short of, in the order they are asked. */
    public static final List<BuildingType> TYPES = List.of(BuildingType.SAWMILL, BuildingType.STONEMASON,
            BuildingType.DYER, BuildingType.GLASSBLOWER);

    private static RecipeManager cachedRecipes;
    private static Set<Item> cachedMasonry = Set.of();

    private Crafters() {
    }

    /** What a crafting hut of this type makes, or null if it is not a crafter. */
    @Nullable
    public static Predicate<Item> products(ServerLevel level, BuildingType type) {
        return switch (type) {
            case SAWMILL -> Crafters::isWoodwork;
            case STONEMASON -> masonry(level)::contains;
            case DYER -> Crafters::isDyerProduct;
            case GLASSBLOWER -> Crafters::isGlassProduct;
            default -> null;
        };
    }

    /** Planks and wooden parts: stairs, slabs, doors, trapdoors, fences, gates, sticks and rope fences. */
    public static boolean isWoodwork(Item item) {
        ItemStack stack = item.getDefaultInstance();
        return stack.is(ItemTags.PLANKS) || stack.is(ItemTags.WOODEN_STAIRS) || stack.is(ItemTags.WOODEN_SLABS)
                || stack.is(ItemTags.WOODEN_DOORS) || stack.is(ItemTags.WOODEN_TRAPDOORS) || stack.is(ItemTags.WOODEN_FENCES)
                || stack.is(ItemTags.FENCE_GATES) || stack.is(Items.STICK) || stack.is(ModBlocks.ROPE_FENCE.asItem());
    }

    /** Beds, wool and dyes. */
    public static boolean isDyerProduct(Item item) {
        ItemStack stack = item.getDefaultInstance();
        return stack.is(ItemTags.BEDS) || stack.is(ItemTags.WOOL) || stack.is(ConventionalItemTags.DYES);
    }

    /** Glass panes and stained glass (plain glass comes from the smeltery). */
    public static boolean isGlassProduct(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return item != Items.GLASS && (path.endsWith("glass") || path.endsWith("glass_pane"));
    }

    /** Everything a stonecutter makes from the stonemason's materials. */
    public static synchronized Set<Item> masonry(ServerLevel level) {
        RecipeManager recipes = level.getServer().getRecipeManager();
        if (recipes != cachedRecipes) {
            Set<Item> products = new HashSet<>();
            for (Item input : Logistics.MASONRY) {
                SingleRecipeInput recipeInput = new SingleRecipeInput(new ItemStack(input));
                for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
                    if (holder.value() instanceof StonecutterRecipe recipe && recipe.matches(recipeInput, level)) {
                        products.add(recipe.assemble(recipeInput, level.registryAccess()).getItem());
                    }
                }
            }
            cachedMasonry = Set.copyOf(products);
            cachedRecipes = recipes;
        }
        return cachedMasonry;
    }

    /** A built crafting hut with a worker that can make something the request accepts, or null. */
    @Nullable
    public static BuildingData crafterFor(ServerLevel level, Colony colony, Logistics.Request request) {
        for (BuildingType type : TYPES) {
            Predicate<Item> products = products(level, type);
            for (BuildingData building : colony.buildings()) {
                if (building.type() != type || building.level() == 0 || !staffed(colony, building)) {
                    continue;
                }
                for (Item item : BuiltInRegistries.ITEM) {
                    if (products.test(item) && request.matcher().test(new ItemStack(item))) {
                        return building;
                    }
                }
            }
        }
        return null;
    }

    private static boolean staffed(Colony colony, BuildingData building) {
        return colony.citizens().stream().anyMatch(c -> building.pos().equals(c.workPos()));
    }
}
