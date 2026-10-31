package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.colony.WorkOrder;
import dev.pikachux.hearthstead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Helpers for setting up colonies in game tests. Tests in the same test environment run
 * at the same time, and {@link #found} removes every existing colony, so each colony test
 * needs its own environment.
 */
final class TestColonies {
    private TestColonies() {
    }

    /** Removes leftover colonies and founds a new one with an unbuilt town hall. */
    static Colony found(GameTestHelper helper, BlockPos relativeTownHall) {
        ServerLevel level = helper.getLevel();
        ColonyManager manager = ColonyManager.get(level.getServer());
        manager.colonies().stream().map(Colony::id).toList().forEach(manager::deleteColony);
        BlockPos pos = helper.absolutePos(relativeTownHall);
        level.setBlockAndUpdate(pos, ModBlocks.HUTS.get(BuildingType.TOWN_HALL).defaultBlockState());
        Colony colony = manager.createColony(helper.makeMockPlayer(GameType.SURVIVAL), level.dimension(), pos);
        ((HutBlockEntity) level.getBlockEntity(pos)).setColonyId(colony.id());
        colony.removeWorkOrder(colony.workOrder(pos));
        helper.onEachTick(() -> level.setDayTime(1000));
        return colony;
    }

    /** Places a hut and builds it instantly to the given level. */
    static BuildingData hut(GameTestHelper helper, Colony colony, BlockPos relative, BuildingType type, int level) {
        ServerLevel world = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);
        world.setBlockAndUpdate(pos, ModBlocks.HUTS.get(type).defaultBlockState());
        ((HutBlockEntity) world.getBlockEntity(pos)).setColonyId(colony.id());
        BuildingData building = new BuildingData(pos, type, 0);
        colony.addBuilding(building);
        for (int i = 1; i <= level; i++) {
            Construction.buildInstantly(world, Construction.plan(world, building, i));
            building.setLevel(i);
        }
        WorkOrder order = colony.workOrder(pos);
        if (order != null) {
            colony.removeWorkOrder(order);
        }
        return building;
    }

    static void give(GameTestHelper helper, BuildingData building, Item item, int count) {
        ItemStack rest = Storage.insert(Storage.containers(helper.getLevel(), building), new ItemStack(item, count));
        if (!rest.isEmpty()) {
            throw helper.assertionException("No room for " + item);
        }
    }

    static int count(GameTestHelper helper, BuildingData building, Item item) {
        return Storage.count(Storage.containers(helper.getLevel(), building), s -> s.is(item));
    }
}
