package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.colony.Logistics;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class SettingsGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** Settings start at their default, cycle through their values and survive saving. */
    @GameTest
    public void settingsCycleAndSave(GameTestHelper helper) {
        BuildingData farm = new BuildingData(BlockPos.ZERO, BuildingType.FARMER, 1);
        check(helper, HutSettings.of(BuildingType.FARMER).contains(HutSettings.CROP), "the farmer to have a crop setting");
        check(helper, HutSettings.of(BuildingType.TOWN_HALL).isEmpty(), "no settings for the town hall");
        check(helper, HutSettings.get(farm, HutSettings.CROP).equals("any"), "any seeds by default");
        check(helper, HutSettings.cycle(farm, HutSettings.CROP).equals("wheat"), "wheat after one click");
        HutSettings.cycle(farm, HutSettings.CROP);
        check(helper, HutSettings.get(BuildingData.load(farm.save()), HutSettings.CROP).equals("carrots"), "carrots after saving");
        for (int i = 0; i < 3; i++) {
            HutSettings.cycle(farm, HutSettings.CROP);
        }
        check(helper, HutSettings.get(farm, HutSettings.CROP).equals("any") && farm.setting("crop") == null,
                "back to the default, which is not stored");
        farm.setSetting("crop", "nonsense");
        check(helper, HutSettings.get(farm, HutSettings.CROP).equals("any"), "unknown values to read as the default");
        helper.succeed();
    }

    /** What huts ask for follows their settings. */
    @GameTest(structure = PLATFORM)
    public void requestsFollowSettings(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        var level = helper.getLevel();
        BuildingData farm = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.FARMER, 1);
        farm.setSetting("crop", "carrots");
        Logistics.Request seeds = find(Logistics.requests(level, colony, farm), "Carrots");
        check(helper, seeds != null, "a request for carrots");
        check(helper, seeds.matcher().test(new ItemStack(Items.CARROT)) && !seeds.matcher().test(new ItemStack(Items.WHEAT_SEEDS)),
                "only carrots to fit it");

        BuildingData mine = TestColonies.hut(helper, colony, new BlockPos(4, 1, 4), BuildingType.MINER, 0);
        check(helper, find(Logistics.requests(level, colony, mine), "Torches") != null, "torches requested by default");
        HutSettings.cycle(mine, HutSettings.TORCHES);
        check(helper, find(Logistics.requests(level, colony, mine), "Torches") == null, "no torches once turned off");

        BuildingData pen = TestColonies.hut(helper, colony, new BlockPos(24, 1, 4), BuildingType.SHEPHERD, 0);
        check(helper, find(Logistics.requests(level, colony, pen), "Wheat") != null, "wheat for breeding by default");
        HutSettings.cycle(pen, HutSettings.BREED);
        check(helper, find(Logistics.requests(level, colony, pen), "Wheat") == null, "no wheat once breeding is off");
        HutSettings.cycle(pen, HutSettings.HERD_SIZE);
        check(helper, HutSettings.herdSize(pen) == 2, "a fixed herd size of 2");
        helper.succeed();
    }

    private static Logistics.Request find(List<Logistics.Request> requests, String description) {
        return requests.stream().filter(r -> r.description().equals(description)).findFirst().orElse(null);
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
