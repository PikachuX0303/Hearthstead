package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.blueprint.Styles;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.colony.Decoration;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.WorkOrder;
import dev.pikachux.hearthstead.network.BuildToolHandler;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.Map;

public class DecorationGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";
    private static final List<String> EXPECTED = List.of("banner_pole", "bench", "campfire", "flower_bed", "fountain", "gate",
            "hay_pile", "lamppost", "market_stall", "path", "path_crossing", "pavilion", "wall", "well");

    /** Every decoration has a blueprint in every style, sitting on the ground layer, and an icon. */
    @GameTest
    public void everyDecorationHasBlueprints(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        List<String> names = Blueprints.decorations(server);
        check(helper, names.containsAll(EXPECTED), "all decorations to be listed, got " + names);
        for (String style : Styles.available(server)) {
            for (String name : EXPECTED) {
                Blueprint blueprint = Blueprints.decoration(server, style, name);
                check(helper, blueprint != null && blueprint.size() > 0, "a " + style + " " + name);
                BoundingBox box = blueprint.bounds(BlockPos.ZERO, Direction.NORTH);
                check(helper, box.minY() == -1, "the " + style + " " + name + " to replace the ground layer");
                check(helper, box.isInside(BlockPos.ZERO), "the " + style + " " + name + " to contain its anchor");
            }
        }
        for (String name : EXPECTED) {
            Item icon = Blueprints.decorationIcon(server, name, Blueprints.decoration(server, Styles.CLASSIC, name));
            check(helper, icon != Items.AIR, "an icon for " + name);
        }
        check(helper, Blueprints.decorationIcon(server, "well", Blueprints.decoration(server, Styles.CLASSIC, "well")) == Items.IRON_CHAIN,
                "the well's icon to be a chain");
        check(helper, Blueprints.decoration(server, Styles.CLASSIC, "../huts/residence/level1") == null, "no paths as names");
        helper.succeed();
    }

    /** Decoration orders and finished decorations keep everything they need when saved. */
    @GameTest
    public void decorationsAreSaved(GameTestHelper helper) {
        WorkOrder order = WorkOrder.decoration(new BlockPos(10, 64, -3), "fountain", Direction.EAST, "desert");
        order.setBuilder(new BlockPos(1, 2, 3));
        WorkOrder loaded = WorkOrder.load(order.save());
        check(helper, loaded.isDecoration() && "fountain".equals(loaded.decoration()) && loaded.facing() == Direction.EAST
                && loaded.style().equals("desert") && loaded.buildingPos().equals(order.buildingPos())
                && new BlockPos(1, 2, 3).equals(loaded.builder()), "the decoration order to load as saved");
        check(helper, !WorkOrder.load(new WorkOrder(BlockPos.ZERO, 2).save()).isDecoration(), "hut orders to stay hut orders");

        Decoration decoration = new Decoration("well", new BlockPos(5, 70, 5), Direction.WEST, "nordic");
        decoration.setIntact(false);
        Decoration copy = Decoration.load(decoration.save());
        check(helper, copy.name().equals("well") && copy.pos().equals(decoration.pos()) && copy.facing() == Direction.WEST
                && copy.style().equals("nordic") && !copy.intact(), "the decoration to load as saved");
        helper.succeed();
    }

    /** A builder with the materials builds an ordered decoration, which then cheers up the colony. */
    @GameTest(environment = "hearthstead-gametest:decorations", structure = PLATFORM, maxTicks = 6000, skyAccess = true)
    public void builderBuildsDecorations(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData builder = TestColonies.hut(helper, colony, new BlockPos(17, 1, 17), BuildingType.BUILDER, 1);
        BlockPos anchor = helper.absolutePos(new BlockPos(5, 1, 6));
        String error = BuildToolHandler.orderDecoration(level, colony, "well", anchor, Direction.NORTH, Styles.CLASSIC);
        check(helper, error == null, "the well to be ordered, got " + error);
        WorkOrder order = colony.workOrders().stream().filter(WorkOrder::isDecoration).findFirst().orElseThrow();
        List<Blueprint.Placement> plan = Construction.plan(level, colony, order);
        Map<Item, Integer> materials = Construction.progress(level, plan).remaining();
        check(helper, materials.containsKey(Items.IRON_CHAIN) && materials.containsKey(Items.DIRT),
                "the well to need a chain and dirt for the path, got " + materials);
        materials.forEach((item, count) -> TestColonies.give(helper, builder, item, count));
        check(helper, colony.decorationHappiness() == 0, "no decoration happiness yet");

        colony.addNewCitizen(level);
        helper.succeedWhen(() -> {
            check(helper, colony.citizens().stream().anyMatch(c -> c.job() == JobType.BUILDER), "a builder");
            check(helper, colony.workOrders().isEmpty() && colony.decoration(anchor) != null, "the well to be finished, "
                    + Construction.progress(level, plan).done() + "/" + plan.size());
            check(helper, level.getBlockState(anchor.offset(0, 2, 0)).is(Blocks.IRON_CHAIN), "the chain over the well");
            check(helper, level.getBlockState(anchor.offset(-2, -1, -2)).is(Blocks.DIRT_PATH), "the path around the well");
            check(helper, colony.decorationHappiness() == Decoration.HAPPINESS, "happiness from the well");
        });
    }

    /** Decorations have to be inside the colony and may not overlap huts or each other. */
    @GameTest(environment = "hearthstead-gametest:decoration_rules", structure = PLATFORM)
    public void decorationsMustFit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData residence = TestColonies.hut(helper, colony, new BlockPos(17, 1, 17), BuildingType.RESIDENCE, 0);
        BlockPos free = helper.absolutePos(new BlockPos(5, 1, 6));
        check(helper, "message.hearthstead.decoration_overlaps".equals(BuildToolHandler.orderDecoration(level, colony, "fountain",
                residence.pos().offset(2, 0, 0), Direction.NORTH, Styles.CLASSIC)), "no fountain inside the residence");
        BlockPos far = colony.center().offset(ColonyManager.colonyRadius(), 0, 0);
        check(helper, "message.hearthstead.decoration_outside".equals(BuildToolHandler.orderDecoration(level, colony, "fountain",
                far, Direction.NORTH, Styles.CLASSIC)), "no fountain across the colony border");
        check(helper, "message.hearthstead.no_blueprint".equals(BuildToolHandler.orderDecoration(level, colony, "castle",
                free, Direction.NORTH, Styles.CLASSIC)), "no unknown decorations");
        check(helper, BuildToolHandler.orderDecoration(level, colony, "fountain", free, Direction.NORTH, "no_such_style") == null,
                "the fountain to be ordered (in the colony's style)");
        check(helper, "message.hearthstead.decoration_overlaps".equals(BuildToolHandler.orderDecoration(level, colony, "bench",
                free.offset(3, 0, 0), Direction.NORTH, Styles.CLASSIC)), "no bench on the ordered fountain");
        check(helper, BuildToolHandler.orderDecoration(level, colony, "bench", free.offset(6, 0, 0), Direction.EAST, Styles.CLASSIC) == null,
                "a bench beside the fountain");
        check(helper, colony.workOrders().stream().filter(WorkOrder::isDecoration).count() == 2, "two decoration orders");
        check(helper, colony.workOrder(free) == null, "hut lookups to ignore decorations");
        helper.succeed();
    }

    /** A decoration counts while most of it stands, and is forgotten once torn down. */
    @GameTest(environment = "hearthstead-gametest:decoration_damage", structure = PLATFORM)
    public void brokenDecorationsStopCounting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BlockPos anchor = helper.absolutePos(new BlockPos(6, 1, 6));
        Decoration fountain = new Decoration("fountain", anchor, Direction.SOUTH, "medievaloak");
        List<Blueprint.Placement> plan = fountain.plan(level);
        Construction.buildInstantly(level, plan);
        colony.addDecoration(fountain);
        colony.addDecoration(new Decoration("lamppost", helper.absolutePos(new BlockPos(14, 1, 6)), Direction.NORTH, "medievaloak"));
        colony.checkDecorations(level);
        check(helper, colony.decorations().size() == 1, "the unbuilt lamppost to be forgotten");
        check(helper, fountain.intact() && colony.decorationHappiness() == Decoration.HAPPINESS, "the fountain to count");

        List<Blueprint.Placement> solid = plan.stream().filter(p -> !p.state().isAir()).toList();
        for (int i = 0; i < solid.size() / 2; i++) {
            level.setBlock(solid.get(i).pos(), Blocks.AIR.defaultBlockState(), 2);
        }
        colony.checkDecorations(level);
        check(helper, colony.decorations().size() == 1 && !fountain.intact() && colony.decorationHappiness() == 0,
                "the damaged fountain to stop counting");
        Construction.buildInstantly(level, plan);
        colony.checkDecorations(level);
        check(helper, fountain.intact(), "the repaired fountain to count again");
        solid.forEach(p -> level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), 2));
        colony.checkDecorations(level);
        check(helper, colony.decorations().isEmpty(), "the torn down fountain to be forgotten");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
