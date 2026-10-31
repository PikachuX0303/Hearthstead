package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyRequests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;

public class RequestGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /**
     * Requests show who they wait on, and a player can hand in items for them: only the
     * requested items move, and no more than requested.
     */
    @GameTest(structure = PLATFORM)
    public void requestsCanBeHandedIn(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData farm = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.FARMER, 1);
        BuildingData warehouse = TestColonies.hut(helper, colony, new BlockPos(4, 1, 4), BuildingType.WAREHOUSE, 0);
        TestColonies.give(helper, warehouse, Items.IRON_HOE, 1);
        var level = helper.getLevel();

        List<ColonyRequests.Entry> requests = ColonyRequests.list(level, colony, farm);
        ColonyRequests.Entry hoe = find(helper, requests, "Hoe");
        ColonyRequests.Entry seeds = find(helper, requests, "Seeds");
        check(helper, hoe.status() == ColonyRequests.Status.NEEDS_DELIVERYMAN,
                "the hoe in the warehouse to wait for a deliveryman, got " + hoe.status());
        check(helper, seeds.status() == ColonyRequests.Status.NEEDS_PLAYER, "the seeds to need a player, got " + seeds.status());
        check(helper, requests.getFirst().status() == ColonyRequests.Status.NEEDS_PLAYER, "requests needing a player listed first");
        check(helper, seeds.icon() != Items.BARRIER, "an icon for the seeds request");
        check(helper, ColonyRequests.list(level, colony, null).size() >= requests.size(), "the colony list to include the farm's");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(Items.WHEAT_SEEDS, 20));
        player.getInventory().add(new ItemStack(Items.DIRT, 5));
        int moved = ColonyRequests.fulfill(level, colony, player, farm.pos(), "Seeds");
        check(helper, moved == 20, "20 seeds handed in, got " + moved);
        check(helper, TestColonies.count(helper, farm, Items.WHEAT_SEEDS) == 20, "the seeds in the farm");
        check(helper, player.getInventory().countItem(Items.WHEAT_SEEDS) == 0, "the seeds gone from the player");
        check(helper, player.getInventory().countItem(Items.DIRT) == 5, "other items left alone");

        player.getInventory().add(new ItemStack(Items.WHEAT_SEEDS, 64));
        int topUp = ColonyRequests.fulfill(level, colony, player, farm.pos(), "Seeds");
        check(helper, topUp == 0, "no more seeds taken once the farm has enough, got " + topUp);
        check(helper, ColonyRequests.fulfill(level, colony, player, farm.pos(), "Hoe") == 0, "nothing handed in without a hoe");
        helper.succeed();
    }

    /** The clipboard can be crafted and makes the expected item. */
    @GameTest
    public void clipboardRecipeExists(GameTestHelper helper) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                dev.pikachux.hearthstead.Hearthstead.id("clipboard"));
        var recipe = helper.getLevel().getServer().getRecipeManager().byKey(key);
        check(helper, recipe.isPresent(), "a clipboard recipe");
        helper.succeed();
    }

    private static ColonyRequests.Entry find(GameTestHelper helper, List<ColonyRequests.Entry> requests, String description) {
        return requests.stream().filter(e -> e.request().description().equals(description)).findFirst()
                .orElseThrow(() -> helper.assertionException("Expected a request for " + description + " in "
                        + requests.stream().map(e -> e.request().description()).toList()));
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
