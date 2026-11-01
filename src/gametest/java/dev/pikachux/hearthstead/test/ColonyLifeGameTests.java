package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

import java.util.List;

/** Postbox, Mystical Site, scarecrow fields, tavern expeditions and away trips. */
public class ColonyLifeGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** Orders, trips and expeditions are saved. */
    @GameTest
    public void tripsAndOrdersAreSaved(GameTestHelper helper) {
        BuildingData postbox = new BuildingData(BlockPos.ZERO, BuildingType.POSTBOX, 1);
        postbox.setOrder("minecraft:bread", 12);
        check(helper, BuildingData.load(postbox.save()).orders().equals(java.util.Map.of("minecraft:bread", 12)), "the order to be saved");
        CitizenData citizen = CitizenData.create(1, helper.getLevel().getRandom());
        citizen.setAwayUntil(5000);
        citizen.startExpedition(9, true);
        CitizenData copy = CitizenData.load(citizen.save());
        check(helper, copy.awayUntil() == 5000 && copy.onExpedition() && copy.expeditionReturnDay() == 9 && copy.expeditionArmed(),
                "a netherworker's trip and an expedition to be saved");
        check(helper, !Colony.expeditionLoot(1, false, helper.getLevel().getRandom()).isEmpty(), "expeditions to find something");
        helper.succeed();
    }

    /** A Mystical Site makes everyone happier, more at higher levels. */
    @GameTest(environment = "hearthstead-gametest:mystical", structure = PLATFORM)
    public void mysticalSitesCheerUp(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        check(helper, colony.mysticalHappiness() == 0, "no bonus without a site");
        TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.MYSTICAL_SITE, 2);
        check(helper, colony.mysticalHappiness() == 2 * Colony.MYSTICAL_HAPPINESS_PER_LEVEL, "the bonus of a level 2 site");
        helper.succeed();
    }

    /** A postbox asks for what was ordered, and the order is done once it has all arrived. */
    @GameTest(environment = "hearthstead-gametest:postbox", structure = PLATFORM)
    public void postboxOrdersArrive(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData postbox = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.POSTBOX, 1);
        postbox.setOrder("minecraft:bread", 10);
        postbox.setOrder("hearthstead:not_an_item", 3);
        List<Logistics.Request> requests = Logistics.requests(level, colony, postbox);
        check(helper, requests.size() == 1 && requests.getFirst().count() == 10 && requests.getFirst().matcher().test(new ItemStack(Items.BREAD)),
                "a request for 10 bread, got " + requests.stream().map(Logistics.Request::description).toList());
        TestColonies.give(helper, postbox, Items.BREAD, 4);
        check(helper, Logistics.requests(level, colony, postbox).getFirst().count() == 6, "6 more bread to come");
        colony.checkPostboxes(level.getServer(), level);
        check(helper, postbox.orders().containsKey("minecraft:bread") && !postbox.orders().containsKey("hearthstead:not_an_item"),
                "the bread to stay on order and the unknown item to be dropped");
        TestColonies.give(helper, postbox, Items.BREAD, 6);
        colony.checkPostboxes(level.getServer(), level);
        check(helper, postbox.orders().isEmpty(), "the order to be done once all bread arrived");
        helper.succeed();
    }

    /** A scarecrow's field goes to a farmer with room, who harvests it; breaking it removes the field. */
    @GameTest(environment = "hearthstead-gametest:fields", structure = PLATFORM, maxTicks = 1200, skyAccess = true)
    public void farmersWorkFields(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData farm = TestColonies.hut(helper, colony, new BlockPos(16, 1, 16), BuildingType.FARMER, 1);
        TestColonies.give(helper, farm, Items.IRON_HOE, 1);
        BlockPos scarecrow = helper.absolutePos(new BlockPos(5, 1, 6));
        BlockPos second = helper.absolutePos(new BlockPos(20, 1, 4));
        level.setBlockAndUpdate(scarecrow, ModBlocks.SCARECROW.defaultBlockState());
        colony.addField(scarecrow);
        colony.addField(second);
        check(helper, colony.fieldsOf(farm).equals(List.of(scarecrow)), "a level 1 farm to get one field, the first one marked");
        level.setBlockAndUpdate(second, ModBlocks.SCARECROW.defaultBlockState());
        level.setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
        check(helper, !colony.fields().contains(second), "breaking a scarecrow to remove its field");
        BlockPos crop = scarecrow.offset(-3, 0, -3);
        level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
        level.setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
        CitizenData farmer = colony.addNewCitizen(level);
        helper.succeedWhen(() -> {
            var state = level.getBlockState(crop);
            check(helper, !(state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(state)),
                    "the farmer to harvest the field (" + farmer.status() + ")");
        });
    }

    /** A visitor sets out with food and a sword from the tavern and comes back the next morning, or is lost. */
    @GameTest(environment = "hearthstead-gametest:expedition", structure = PLATFORM)
    public void visitorsGoOnExpeditions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData tavern = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.TAVERN, 1);
        check(helper, "message.hearthstead.expedition_no_visitor".equals(colony.sendExpedition(level, tavern)), "no expedition without a visitor");
        CitizenData visitor = colony.addVisitor(level, tavern);
        check(helper, "message.hearthstead.expedition_needs_food".equals(colony.sendExpedition(level, tavern)), "no expedition without food");
        HutBlockEntity hut = (HutBlockEntity) level.getBlockEntity(tavern.pos());
        hut.setItem(0, new ItemStack(Items.BREAD, 8));
        hut.setItem(1, new ItemStack(Items.IRON_SWORD));
        check(helper, colony.sendExpedition(level, tavern) == null, "the expedition to set out");
        check(helper, visitor.onExpedition() && visitor.expeditionArmed() && level.getEntity(visitor.entityUuid()) == null,
                "the armed visitor to be away");
        check(helper, TestColonies.count(helper, tavern, Items.BREAD) == 2 && TestColonies.count(helper, tavern, Items.IRON_SWORD) == 0,
                "the food and sword to be taken along");
        long day = level.getDayTime() / 24000;
        colony.returnExpeditions(level, day);
        check(helper, visitor.onExpedition(), "the expedition to take until tomorrow");
        colony.returnExpeditions(level, day + 1);
        if (colony.citizen(visitor.id()) != null) {
            check(helper, !visitor.onExpedition() && visitor.arrivalDay() == day + 1 && level.getEntity(visitor.entityUuid()) != null,
                    "the visitor to be back at the tavern");
            check(helper, TestColonies.count(helper, tavern, Items.BREAD) < 64 && hut.getItem(0).getCount() >= 0
                    && java.util.stream.IntStream.range(0, hut.getContainerSize()).filter(i -> !hut.getItem(i).isEmpty()).count() > 1,
                    "loot in the tavern");
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
