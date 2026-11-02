package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Crafters;
import dev.pikachux.hearthstead.colony.Crafting;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.network.ModNetworking;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RecipeTeachingGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** Taught recipes are saved, and only crafting table recipes can be taught. */
    @GameTest
    public void recipesAreSaved(GameTestHelper helper) {
        BuildingData mechanic = new BuildingData(BlockPos.ZERO, BuildingType.MECHANIC, 2);
        mechanic.teach("minecraft:chest", 4);
        check(helper, BuildingData.load(mechanic.save()).recipes().equals(java.util.Map.of("minecraft:chest", 4)), "the recipe to be saved");
        check(helper, Crafting.hasRecipe(helper.getLevel(), Items.CHEST) && !Crafting.hasRecipe(helper.getLevel(), Items.BEDROCK),
                "a recipe for chests and none for bedrock");
        check(helper, Crafters.recipeSlots(0) == 2 && Crafters.recipeSlots(3) == 6, "two recipes per level");
        helper.succeed();
    }

    /** A taught mechanic asks for the ingredients and makes what a postbox ordered. */
    @GameTest(environment = "hearthstead-gametest:teach", structure = PLATFORM, maxTicks = 1200)
    public void taughtCraftersMakeOrders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData mechanic = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.MECHANIC, 1);
        BuildingData postbox = TestColonies.hut(helper, colony, new BlockPos(4, 1, 6), BuildingType.POSTBOX, 1);
        var player = helper.makeMockServerPlayerInLevel();
        try {
            check(helper, "message.hearthstead.recipe_not_teachable".equals(ModNetworking.teach(player, colony, postbox, "minecraft:chest|0")),
                    "a postbox not to learn recipes");
            check(helper, "message.hearthstead.recipe_none".equals(ModNetworking.teach(player, colony, mechanic, "minecraft:bedrock|0")),
                    "no recipe for bedrock");
            check(helper, ModNetworking.teach(player, colony, mechanic, "minecraft:chest|0") == null
                    && ModNetworking.teach(player, colony, mechanic, "minecraft:barrel|0") == null, "two recipes taught");
            check(helper, "message.hearthstead.recipe_slots_full".equals(ModNetworking.teach(player, colony, mechanic, "minecraft:ladder|0")),
                    "a level 1 hut to know only two");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        check(helper, Logistics.requests(level, colony, mechanic).stream().noneMatch(r -> r.matcher().test(new ItemStack(Items.OAK_PLANKS))),
                "no planks wanted while nobody needs a chest");
        postbox.setOrder("minecraft:chest", 1);
        check(helper, Logistics.requests(level, colony, mechanic).stream().anyMatch(r -> r.matcher().test(new ItemStack(Items.OAK_PLANKS))),
                "the mechanic to ask for planks for the ordered chest");
        ((HutBlockEntity) level.getBlockEntity(mechanic.pos())).setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
        colony.addNewCitizen(level);
        helper.succeedWhen(() -> check(helper, TestColonies.count(helper, mechanic, Items.CHEST) == 1, "the mechanic to make the chest"));
    }

    /** A taught recipe with a stock is made until the stock is there, and its product is not kept as material. */
    @GameTest(environment = "hearthstead-gametest:teach_stock", structure = PLATFORM, maxTicks = 1200)
    public void taughtCraftersKeepStock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData fletcher = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.FLETCHER, 1);
        fletcher.teach("minecraft:ladder", 3);
        check(helper, !Logistics.keeps(level, fletcher).test(new ItemStack(Items.LADDER))
                && Logistics.keeps(level, fletcher).test(new ItemStack(Items.STICK)), "ladders to be output and sticks material");
        ((HutBlockEntity) level.getBlockEntity(fletcher.pos())).setItem(0, new ItemStack(Items.STICK, 21));
        colony.addNewCitizen(level);
        helper.succeedWhen(() -> {
            check(helper, TestColonies.count(helper, fletcher, Items.LADDER) == 3, "three ladders in stock");
            check(helper, TestColonies.count(helper, fletcher, Items.STICK) == 14, "no more ladders than the stock");
        });
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
