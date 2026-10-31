package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.registry.ModBlocks;
import dev.pikachux.hearthstead.worldgen.AbandonedSettlementPiece;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

public class WorldgenGameTests {
    /** The settlement's structure, structure set, template and loot table are all loaded. */
    @GameTest
    public void settlementDataLoads(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var registries = server.registryAccess();
        check(helper, registries.lookupOrThrow(Registries.STRUCTURE)
                .get(ResourceKey.create(Registries.STRUCTURE, Hearthstead.id("abandoned_settlement"))).isPresent(), "the structure");
        check(helper, registries.lookupOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceKey.create(Registries.STRUCTURE_SET, Hearthstead.id("abandoned_settlements"))).isPresent(), "the structure set");
        check(helper, server.reloadableRegistries().getLootTable(AbandonedSettlementPiece.LOOT) != LootTable.EMPTY, "the loot table");

        StructureTemplate template = server.getStructureManager().get(AbandonedSettlementPiece.TEMPLATE).orElse(null);
        check(helper, template != null, "the camp template");
        check(helper, template.getSize().getX() == 17 && template.getSize().getZ() == 17, "a 17 x 17 template, got " + template.getSize());
        var placeSettings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        var halls = template.filterBlocks(BlockPos.ZERO, placeSettings, ModBlocks.HUTS.get(BuildingType.TOWN_HALL));
        check(helper, halls.size() == 1 && halls.getFirst().pos().equals(new BlockPos(8, 1, 8)), "one town hall in the middle");
        var chests = template.filterBlocks(BlockPos.ZERO, placeSettings, Blocks.CHEST);
        check(helper, chests.size() == 1 && chests.getFirst().pos().equals(new BlockPos(3, 1, 3)), "the supply chest at 3 1 3");
        helper.succeed();
    }

    /** Right-clicking a town hall that belongs to no colony founds one there; others cannot claim it again. */
    @GameTest(structure = "hearthstead-gametest:platform")
    public void unclaimedTownHallCanBeClaimed(GameTestHelper helper) {
        var level = helper.getLevel();
        ColonyManager manager = ColonyManager.get(level.getServer());
        manager.colonies().stream().map(Colony::id).toList().forEach(manager::deleteColony);
        BlockPos pos = helper.absolutePos(new BlockPos(16, 1, 16));
        HutBlock townHall = (HutBlock) ModBlocks.HUTS.get(BuildingType.TOWN_HALL);
        level.setBlockAndUpdate(pos, townHall.defaultBlockState());
        check(helper, manager.colonyAt(level.dimension(), pos) == null, "no colony before claiming");

        Player founder = helper.makeMockPlayer(GameType.SURVIVAL);
        String error = townHall.claim(level, pos, founder);
        check(helper, error == null, "the claim to work, got " + error);
        Colony colony = manager.colonyAt(level.dimension(), pos);
        check(helper, colony != null && colony.owner().equals(founder.getUUID()) && colony.center().equals(pos),
                "a colony owned by the founder around the town hall");
        check(helper, colony.workOrder(pos) != null, "a work order to build the town hall");

        Player latecomer = helper.makeMockPlayer(GameType.SURVIVAL);
        check(helper, "message.hearthstead.townhall_inside_colony".equals(townHall.claim(level, pos, latecomer)),
                "a second claim to be refused");

        BlockPos other = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(other, townHall.defaultBlockState());
        check(helper, townHall.claim(level, other, founder) != null, "a player with a colony not to claim another town hall");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
