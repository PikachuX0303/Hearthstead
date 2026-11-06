package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.blueprint.Styles;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.WorkOrder;
import dev.pikachux.hearthstead.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Map;

public class ColonyGameTests {
    private static String describeEntity(ServerLevel level, CitizenData citizen) {
        var entity = citizen.entityUuid() == null ? null : level.getEntity(citizen.entityUuid());
        return entity == null ? "<none>" : entity.blockPosition().toShortString() + " nav=" + ((net.minecraft.world.entity.Mob) entity).getNavigation().isDone();
    }

    /** Every bundled blueprint of every style loads, contains its hut and can be placed in every direction. */
    @GameTest
    public void allBlueprintsLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var styles = Styles.available(level.getServer());
        var expected = java.util.List.of("classic", "acacia", "ancientathens", "birch", "caledonia", "cavern", "colonial",
                "darkoak", "fortress", "incan", "jungle", "lostcity", "medievalbirch", "medievaldarkoak", "medievaloak",
                "medievalspruce", "nordic", "pagoda", "sandstone", "shire", "spacewars", "truedwarven", "warped");
        if (!styles.equals(expected)) {
            throw helper.assertionException("Expected the imported styles, got " + styles);
        }
        for (String style : styles) for (BuildingType type : BuildingType.values()) {
            BuildingData building = new BuildingData(BlockPos.ZERO, type, 0);
            building.setStyle(style);
            for (int lvl = 1; lvl <= BuildingType.MAX_LEVEL; lvl++) {
                // A style without a blueprint for some hut or level uses the classic one.
                Blueprint blueprint = Blueprints.get(level.getServer(), building, lvl);
                if (blueprint == null) {
                    throw helper.assertionException("Missing blueprint " + Blueprints.id(building, lvl));
                }
                var entries = blueprint.entries();
                // Exactly one hut block, this hut's, at the anchor.
                long huts = entries.stream().filter(e -> e.state().getBlock() instanceof dev.pikachux.hearthstead.block.HutBlock).count();
                boolean anchored = entries.stream().anyMatch(e -> e.offset().equals(BlockPos.ZERO)
                        && e.state().is(ModBlocks.HUTS.get(type)));
                if (blueprint.placements(BlockPos.ZERO, Direction.NORTH).isEmpty()) {
                    throw helper.assertionException("Empty blueprint " + Blueprints.id(building, lvl));
                }
                if (huts != 1 || !anchored) {
                    throw helper.assertionException(Blueprints.id(building, lvl) + " has " + huts + " hut blocks, anchored " + anchored);
                }
            }
        }
        helper.succeed();
    }

    /** A builder with materials in its hut constructs its own hut to level 1. */
    @GameTest(environment = "hearthstead-gametest:builder", maxTicks = 12000, skyAccess = true, structure = "hearthstead-gametest:platform")
    public void builderBuildsItsHut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(1000);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ColonyManager manager = ColonyManager.get(level.getServer());
        manager.colonies().stream().map(Colony::id).toList().forEach(manager::deleteColony);

        BlockPos townHallPos = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos builderPos = helper.absolutePos(new BlockPos(18, 1, 18));
        level.setBlockAndUpdate(townHallPos, ModBlocks.HUTS.get(BuildingType.TOWN_HALL).defaultBlockState());
        level.setBlockAndUpdate(builderPos, ModBlocks.HUTS.get(BuildingType.BUILDER).defaultBlockState()
                .setValue(HutBlock.FACING, Direction.SOUTH));

        Colony colony = manager.createColony(player, level.dimension(), townHallPos);
        BuildingData builderHut = new BuildingData(builderPos, BuildingType.BUILDER, 0);
        colony.addBuilding(builderHut);
        colony.addWorkOrder(new WorkOrder(builderPos, 1));
        // Only the builder's hut should be built in this test.
        colony.removeWorkOrder(colony.workOrder(townHallPos));

        List<Blueprint.Placement> plan = Construction.plan(level, builderHut, 1);
        Map<Item, Integer> materials = Construction.progress(level, plan).remaining();
        HutBlockEntity hut = (HutBlockEntity) level.getBlockEntity(builderPos);
        hut.setColonyId(colony.id());
        int slot = 0;
        for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
            int count = entry.getValue();
            while (count > 0) {
                int stack = Math.min(count, entry.getKey().getDefaultMaxStackSize());
                hut.setItem(slot++, new ItemStack(entry.getKey(), stack));
                count -= stack;
            }
        }

        CitizenData citizen = colony.addNewCitizen(level);
        helper.onEachTick(() -> {
            level.setDayTime(1000);
            if (level.isRaining() || level.isThundering()) {
                level.setWeatherParameters(24000, 0, false, false);
            }
        });
        helper.succeedWhen(() -> {
            if (citizen.job() != JobType.BUILDER) {
                throw helper.assertionException("Citizen did not become a builder");
            }
            if (builderHut.level() != 1) {
                Construction.Progress progress = Construction.progress(level, plan);
                throw helper.assertionException("Builder's hut not built yet: " + progress.done() + "/" + progress.total()
                        + " (" + citizen.status() + ") citizen at " + describeEntity(level, citizen) + " hut at " + builderPos);
            }
        });
    }

    /** A building in a non-classic style is built from that style's blueprint. */
    @GameTest(environment = "hearthstead-gametest:styles", structure = "hearthstead-gametest:platform", skyAccess = true)
    public void stylesChangeTheBuilding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        colony.setStyle(dev.pikachux.hearthstead.blueprint.Styles.migrate("desert"));
        if (!"sandstone".equals(colony.style())) {
            throw helper.assertionException("Expected the old desert style to become sandstone, got " + colony.style());
        }
        BuildingData residence = TestColonies.hut(helper, colony, new BlockPos(16, 1, 14), BuildingType.RESIDENCE, 0);
        residence.setStyle(colony.style());
        var sandstone = Construction.plan(level, residence, 1);
        residence.setStyle(dev.pikachux.hearthstead.blueprint.Styles.CLASSIC);
        var classic = Construction.plan(level, residence, 1);
        residence.setStyle(colony.style());
        if (sandstone.equals(classic)) {
            throw helper.assertionException("Expected the sandstone residence to differ from the classic one");
        }
        Construction.buildInstantly(level, sandstone);
        long matching = sandstone.stream().filter(p -> level.getBlockState(p.pos()).equals(p.state())).count();
        if (matching < sandstone.size() * 9 / 10) {
            throw helper.assertionException("Expected the sandstone residence to be built, " + matching + " of " + sandstone.size());
        }
        helper.succeed();
    }

    /** Worlds saved before the rename refer to blocks, items and entities as "minecolonies:...". */
    @GameTest
    public void legacyIdsStillResolve(GameTestHelper helper) {
        var oldHut = net.minecraft.resources.Identifier.fromNamespaceAndPath("minecolonies", "blockhuttownhall");
        var oldCitizen = net.minecraft.resources.Identifier.fromNamespaceAndPath("minecolonies", "citizen");
        if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(oldHut) != ModBlocks.HUTS.get(BuildingType.TOWN_HALL)) {
            throw helper.assertionException("Old town hall id does not resolve");
        }
        if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(oldCitizen)
                != dev.pikachux.hearthstead.registry.ModEntities.CITIZEN) {
            throw helper.assertionException("Old citizen id does not resolve");
        }
        helper.succeed();
    }
}
