package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingInfo;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.WorkOrder;
import dev.pikachux.hearthstead.network.BuildToolHandler;
import dev.pikachux.hearthstead.network.MoveHutPayload;
import dev.pikachux.hearthstead.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class BuilderJobGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** Repair and removal orders keep what they need when saved. */
    @GameTest
    public void builderOrdersAreSaved(GameTestHelper helper) {
        WorkOrder repair = WorkOrder.load(WorkOrder.repair(new BlockPos(1, 2, 3), 2).save());
        check(helper, repair.kind() == WorkOrder.Kind.REPAIR && repair.targetLevel() == 2, "the repair to load as saved");
        WorkOrder removal = WorkOrder.load(WorkOrder.removal(new BlockPos(4, 5, 6), BuildingType.BAKER, "desert", 3,
                Direction.WEST, true).save());
        check(helper, removal.kind() == WorkOrder.Kind.REMOVE && removal.hutType() == BuildingType.BAKER
                && removal.style().equals("desert") && removal.targetLevel() == 3 && removal.facing() == Direction.WEST
                && removal.demolish(), "the removal to load as saved");
        check(helper, WorkOrder.load(new WorkOrder(BlockPos.ZERO, 1).save()).kind() == WorkOrder.Kind.BUILD, "old orders to be builds");
        helper.succeed();
    }

    /** A builder puts back the missing blocks of a damaged hut. */
    @GameTest(environment = "hearthstead-gametest:repair", structure = PLATFORM, maxTicks = 3000, skyAccess = true)
    public void buildersRepairHuts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData builder = TestColonies.hut(helper, colony, new BlockPos(18, 1, 18), BuildingType.BUILDER, 1);
        BuildingData residence = TestColonies.hut(helper, colony, new BlockPos(6, 1, 7), BuildingType.RESIDENCE, 1);
        check(helper, "message.hearthstead.nothing_to_repair".equals(BuildingInfo.requestRepair(level, colony, residence)),
                "nothing to repair on a complete hut");
        List<Blueprint.Placement> plan = Construction.plan(level, residence, 1);
        List<Blueprint.Placement> walls = plan.stream()
                .filter(p -> !p.state().isAir() && p.pos().getY() == residence.pos().getY() + 1).limit(3).toList();
        walls.forEach(p -> level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), 2));
        check(helper, BuildingInfo.requestRepair(level, colony, residence) == null, "the repair to be ordered");
        Construction.progress(level, plan).remaining().forEach((item, count) -> TestColonies.give(helper, builder, item, count));
        colony.addNewCitizen(level);
        helper.succeedWhen(() -> {
            check(helper, walls.stream().allMatch(p -> Construction.isDone(level, p)), "the walls to be repaired");
            check(helper, colony.workOrders().isEmpty() && residence.level() == 1, "the repair to be finished at level 1");
        });
    }

    /** A builder takes a hut down and keeps its materials and the hut block; the town hall stays. */
    @GameTest(environment = "hearthstead-gametest:demolish", structure = PLATFORM, maxTicks = 8000, skyAccess = true)
    public void buildersTakeDownHuts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData builder = TestColonies.hut(helper, colony, new BlockPos(18, 1, 18), BuildingType.BUILDER, 1);
        BuildingData baker = TestColonies.hut(helper, colony, new BlockPos(6, 1, 7), BuildingType.BAKER, 1);
        BlockPos hut = baker.pos();
        ((HutBlockEntity) level.getBlockEntity(hut)).setItem(0, new ItemStack(Items.WHEAT, 7));
        check(helper, "message.hearthstead.cannot_demolish_townhall".equals(BuildingInfo.requestDemolish(level, colony,
                colony.building(colony.center()))), "the town hall to stay");
        check(helper, BuildingInfo.requestDemolish(level, colony, baker) == null, "the deconstruction to be ordered");
        BlockPos corner = hut.offset(-3, 1, -3);
        check(helper, !level.getBlockState(corner).isAir(), "a wall corner before");
        colony.addNewCitizen(level);
        helper.succeedWhen(() -> {
            check(helper, colony.building(hut) == null && level.getBlockState(hut).isAir(), "the bakery to be gone");
            check(helper, level.getBlockState(corner).isAir(), "its walls to be gone");
            check(helper, !level.getBlockState(hut.below()).isAir(), "its floor to stay");
            check(helper, TestColonies.count(helper, builder, ModBlocks.HUTS.get(BuildingType.BAKER).asItem()) == 1,
                    "the hut block in the builder's hut");
            check(helper, TestColonies.count(helper, builder, Items.WHEAT) == 7, "the bakery's wheat in the builder's hut");
        });
    }

    /** Moving a hut takes everything along at once and leaves orders to clear the old site and rebuild. */
    @GameTest(environment = "hearthstead-gametest:move_hut", structure = PLATFORM)
    public void hutsMove(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData residence = TestColonies.hut(helper, colony, new BlockPos(6, 1, 7), BuildingType.RESIDENCE, 1);
        BlockPos from = residence.pos();
        BlockPos to = helper.absolutePos(new BlockPos(18, 1, 16));
        CitizenData resident = colony.addNewCitizen(level);
        resident.setHomePos(from);
        ((HutBlockEntity) level.getBlockEntity(from)).setItem(3, new ItemStack(Items.BREAD, 5));

        var player = helper.makeMockServerPlayerInLevel();
        colony.officers().add(player.getUUID());
        player.teleportTo(to.getX(), to.getY(), to.getZ());
        check(helper, "message.hearthstead.cannot_move_townhall".equals(BuildToolHandler.moveError(player,
                new MoveHutPayload(colony.center(), to, Direction.NORTH))), "the town hall to stay");
        check(helper, BuildToolHandler.moveError(player, new MoveHutPayload(from, to, Direction.EAST)) == null, "the move to be allowed");
        level.getServer().getPlayerList().remove(player);

        BuildingData moved = colony.moveBuilding(level, residence, to, Direction.EAST);
        check(helper, colony.building(from) == null && colony.building(to) == moved && moved.level() == 1, "the residence at its new spot");
        check(helper, level.getBlockState(from).isAir() && level.getBlockState(to).is(ModBlocks.HUTS.get(BuildingType.RESIDENCE))
                && level.getBlockState(to).getValue(HutBlock.FACING) == Direction.EAST, "the hut block moved and turned");
        check(helper, ((HutBlockEntity) level.getBlockEntity(to)).getItem(3).is(Items.BREAD), "the hut's contents moved along");
        check(helper, to.equals(resident.homePos()), "the resident to move along");
        check(helper, colony.workOrders().stream().anyMatch(o -> o.kind() == WorkOrder.Kind.REMOVE && o.buildingPos().equals(from)
                && !o.demolish()), "an order to clear the old site");
        WorkOrder rebuild = colony.workOrder(to);
        check(helper, rebuild != null && rebuild.kind() == WorkOrder.Kind.REPAIR && rebuild.targetLevel() == 1,
                "an order to rebuild level 1 at the new spot");
        List<Blueprint.Placement> clear = Construction.plan(level, colony,
                colony.workOrders().stream().filter(o -> o.kind() == WorkOrder.Kind.REMOVE).findFirst().orElseThrow());
        check(helper, clear != null && !clear.isEmpty() && clear.stream().noneMatch(p -> Construction.isRemoved(level, p)),
                "the old walls to be taken down");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
