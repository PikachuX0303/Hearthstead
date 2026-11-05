package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/**
 * Baker: bakes what the bakery is set to make, in batches of one per hut level: bread (three
 * wheat a loaf), cookies (two wheat and a cocoa bean make eight) or pumpkin pie (a pumpkin,
 * sugar and an egg).
 */
public class BakerGoal extends WorkGoal {
    private static final int BAKE_TICKS = 160;

    /** A bakery recipe: what one batch takes and makes. */
    public record Recipe(Map<Item, Integer> ingredients, Item result, int count, String bakingStatus, String missingStatus) {
        public boolean canBake(List<Container> storage) {
            return ingredients.entrySet().stream().allMatch(e -> Storage.count(storage, s -> s.is(e.getKey())) >= e.getValue());
        }

        /** Batches the storage has the ingredients for, up to {@code max}. */
        public int batches(List<Container> storage, int max) {
            int batches = max;
            for (Map.Entry<Item, Integer> e : ingredients.entrySet()) {
                batches = Math.min(batches, Storage.count(storage, s -> s.is(e.getKey())) / e.getValue());
            }
            return batches;
        }
    }

    public static final Recipe BREAD = new Recipe(Map.of(Items.WHEAT, 3), Items.BREAD, 1, "baking_bread", "needs_wheat");
    public static final Recipe COOKIES = new Recipe(Map.of(Items.WHEAT, 2, Items.COCOA_BEANS, 1), Items.COOKIE, 8,
            "baking_cookies", "needs_cookie_ingredients");
    public static final Recipe PIE = new Recipe(Map.of(Items.PUMPKIN, 1, Items.SUGAR, 1, Items.EGG, 1), Items.PUMPKIN_PIE, 1,
            "baking_pie", "needs_pie_ingredients");

    private int timer;

    public BakerGoal(CitizenEntity citizen) {
        super(citizen, JobType.BAKER);
    }

    /** What the bakery is set to bake. */
    public static Recipe recipe(BuildingData building) {
        return switch (HutSettings.get(building, HutSettings.BAKE)) {
            case "cookies" -> COOKIES;
            case "pie" -> PIE;
            default -> BREAD;
        };
    }

    /** Ingredients of every recipe: the bakery keeps them. */
    public static boolean isIngredient(ItemStack stack) {
        return BREAD.ingredients().containsKey(stack.getItem()) || COOKIES.ingredients().containsKey(stack.getItem())
                || PIE.ingredients().containsKey(stack.getItem());
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
            citizen.setStatus(Status.of("going_bakery"));
            return;
        }
        depositAll();
        if (building.level() == 0) {
            citizen.setStatus(Status.of("waiting_bakery_be_built"));
            return;
        }
        Recipe recipe = recipe(building);
        List<Container> storage = Storage.containers(level(), building);
        if (!recipe.canBake(storage)) {
            citizen.setStatus(Status.of(recipe.missingStatus()));
            timer = 0;
            return;
        }
        citizen.setStatus(Status.of(recipe.bakingStatus()));
        if (++timer % 20 == 0) {
            citizen.swing(InteractionHand.MAIN_HAND);
            level().sendParticles(ParticleTypes.SMOKE, hut.getX() + 0.5, hut.getY() + 1.2, hut.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0);
        }
        if (timer < breakDelay(BAKE_TICKS)) {
            return;
        }
        timer = 0;
        int batches = recipe.batches(storage, building.level());
        for (Map.Entry<Item, Integer> e : recipe.ingredients().entrySet()) {
            Storage.extract(storage, s -> s.is(e.getKey()), batches * e.getValue());
        }
        ItemStack rest = Storage.insert(storage, new ItemStack(recipe.result(), batches * recipe.count()));
        if (!rest.isEmpty()) {
            citizen.spawnAtLocation(level(), rest);
        }
    }
}
