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

    /** Every hut with a worker (and the tavern and training grounds) has settings, all with English labels. */
    @GameTest
    public void everyWorkerHutHasSettings(GameTestHelper helper) {
        com.google.gson.JsonObject lang;
        try (var in = SettingsGameTests.class.getResourceAsStream("/assets/hearthstead/lang/en_us.json")) {
            lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (java.io.IOException | NullPointerException e) {
            throw helper.assertionException("Could not read the language file: " + e);
        }
        List<String> problems = new java.util.ArrayList<>();
        for (BuildingType type : BuildingType.values()) {
            boolean steerable = (type.job() != null && type != BuildingType.TOWN_HALL)
                    || type == BuildingType.TAVERN || type == BuildingType.COMBAT_ACADEMY || type == BuildingType.ARCHERY;
            if (steerable && HutSettings.of(type).isEmpty()) {
                problems.add(type + " has no settings");
            }
            for (HutSettings.Setting setting : HutSettings.of(type)) {
                String key = "setting.hearthstead." + setting.id();
                for (String needed : List.of(key, key + ".desc")) {
                    if (!lang.has(needed)) {
                        problems.add("missing " + needed);
                    }
                }
                for (String value : setting.values()) {
                    if (!value.matches("\\d+") && !value.equals("auto") && !lang.has(key + "." + value)) {
                        problems.add("missing " + key + "." + value);
                    }
                }
            }
        }
        check(helper, problems.isEmpty(), "settings with labels for every worker hut, got " + problems);
        helper.succeed();
    }

    /** The new settings change what huts ask for and what workers do. */
    @GameTest(structure = PLATFORM)
    public void newSettingsTakeEffect(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        var level = helper.getLevel();

        BuildingData bakery = TestColonies.hut(helper, colony, new BlockPos(4, 1, 4), BuildingType.BAKER, 1);
        check(helper, find(Logistics.requests(level, colony, bakery), "Wheat") != null, "wheat for bread by default");
        HutSettings.cycle(bakery, HutSettings.BAKE);
        check(helper, find(Logistics.requests(level, colony, bakery), "Cocoa Beans") != null, "cocoa beans for cookies");
        HutSettings.cycle(bakery, HutSettings.BAKE);
        var pie = Logistics.requests(level, colony, bakery);
        check(helper, find(pie, "Pumpkin") != null && find(pie, "Egg") != null && find(pie, "Wheat") == null,
                "pumpkins and eggs but no wheat for pies, got " + pie.stream().map(Logistics.Request::description).toList());

        BuildingData smeltery = TestColonies.hut(helper, colony, new BlockPos(14, 1, 4), BuildingType.STONE_SMELTERY, 1);
        check(helper, find(Logistics.requests(level, colony, smeltery), "Cobblestone") != null, "cobblestone by default");
        smeltery.setSetting("stone_smelt", "clay");
        check(helper, find(Logistics.requests(level, colony, smeltery), "Clay Ball") != null
                && find(Logistics.requests(level, colony, smeltery), "Cobblestone") == null, "clay instead of cobblestone");
        check(helper, HutSettings.stoneSmelterInput(smeltery).test(new ItemStack(Items.CLAY_BALL))
                && !HutSettings.stoneSmelterInput(smeltery).test(new ItemStack(Items.NETHERRACK)), "only clay to be smelted");

        BuildingData mechanic = TestColonies.hut(helper, colony, new BlockPos(24, 1, 4), BuildingType.MECHANIC, 1);
        check(helper, find(Logistics.requests(level, colony, mechanic), "Iron Ingots") != null, "iron for the mechanic's trade");
        check(helper, dev.pikachux.hearthstead.colony.Crafters.products(level, mechanic).test(Items.REPEATER), "repeaters made");
        HutSettings.cycle(mechanic, HutSettings.OWN_TRADE);
        check(helper, find(Logistics.requests(level, colony, mechanic), "Iron Ingots") == null, "no iron for taught-only");
        check(helper, !dev.pikachux.hearthstead.colony.Crafters.products(level, mechanic).test(Items.REPEATER),
                "no repeaters once set to taught recipes only");

        BuildingData lab = TestColonies.hut(helper, colony, new BlockPos(4, 1, 14), BuildingType.ALCHEMIST, 1);
        check(helper, find(Logistics.requests(level, colony, lab), "Gunpowder") == null, "no gunpowder by default");
        HutSettings.cycle(lab, HutSettings.SPLASH);
        check(helper, find(Logistics.requests(level, colony, lab), "Gunpowder") != null, "gunpowder for splash potions");

        BuildingData composter = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.COMPOSTER, 1);
        HutSettings.cycle(composter, HutSettings.COMPOST);
        check(helper, HutSettings.compostInput(composter).test(new ItemStack(Items.OAK_LEAVES))
                && !HutSettings.compostInput(composter).test(new ItemStack(Items.POPPY)), "only leaves and saplings composted");

        BuildingData enchanter = new BuildingData(BlockPos.ZERO, BuildingType.ENCHANTER, 5);
        check(helper, dev.pikachux.hearthstead.entity.ai.EnchanterGoal.workLevel(enchanter) == 5, "full power by default");
        HutSettings.cycle(enchanter, HutSettings.ENCHANT_POWER);
        check(helper, dev.pikachux.hearthstead.entity.ai.EnchanterGoal.workLevel(enchanter) == 1, "level 1 power to save lapis");

        BuildingData quarry = new BuildingData(BlockPos.ZERO, BuildingType.QUARRY, 5);
        check(helper, HutSettings.pitLevel(quarry) == 5, "the full pit by default");
        HutSettings.cycle(quarry, HutSettings.PIT_SIZE);
        check(helper, HutSettings.pitLevel(quarry) == 1, "a small pit");

        BuildingData builder = new BuildingData(BlockPos.ZERO, BuildingType.BUILDER, 1);
        var build = new dev.pikachux.hearthstead.colony.WorkOrder(new BlockPos(1, 0, 0), 1);
        var repair = dev.pikachux.hearthstead.colony.WorkOrder.repair(new BlockPos(2, 0, 0), 1);
        check(helper, HutSettings.builderTakes(builder, build) && HutSettings.builderTakes(builder, repair), "all orders by default");
        HutSettings.cycle(builder, HutSettings.BUILDER_JOBS);
        check(helper, HutSettings.builderTakes(builder, build) && !HutSettings.builderTakes(builder, repair), "building only");
        HutSettings.cycle(builder, HutSettings.BUILDER_JOBS);
        check(helper, !HutSettings.builderTakes(builder, build) && HutSettings.builderTakes(builder, repair), "repairs only");

        BuildingData archery = TestColonies.hut(helper, colony, new BlockPos(24, 1, 14), BuildingType.ARCHERY, 1);
        check(helper, colony.trainingGround(BuildingType.ARCHERY) == archery, "guards to train at the archery");
        HutSettings.cycle(archery, HutSettings.TRAINING);
        check(helper, colony.trainingGround(BuildingType.ARCHERY) == null, "no training once it is closed");
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
