package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.client.buildtool.BuildToolScreen;
import dev.pikachux.hearthstead.client.buildtool.HutSelectScreen;
import dev.pikachux.hearthstead.client.buildtool.PendingPlacement;
import dev.pikachux.hearthstead.client.gui.BuildingScreen;
import dev.pikachux.hearthstead.client.gui.CitizenScreen;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.colony.WorkOrder;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import dev.pikachux.hearthstead.entity.raider.AbstractBarbarian;
import dev.pikachux.hearthstead.registry.ModEntities;
import dev.pikachux.hearthstead.network.BlueprintRequestPayload;
import dev.pikachux.hearthstead.network.BuildingViews;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.pikachux.hearthstead.registry.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Opens every mod screen in a real client and saves screenshots of them.
 */
public class ScreenshotClientTest implements FabricClientGameTest {
    private BlockPos townHall;
    private BlockPos residence;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.renderDistance().set(2);
            client.options.simulationDistance().set(5);
            client.options.framerateLimit().set(10);
            client.options.menuBackgroundBlurriness().set(0);
        });
        try (TestSingleplayerContext singleplayer = context.worldBuilder().adjustSettings(settings -> {
            settings.getNormalPresetList().stream()
                    .filter(entry -> entry.preset() != null && entry.preset().is(WorldPresets.FLAT))
                    .findFirst()
                    .ifPresent(settings::setWorldType);
            settings.setGenerateStructures(false);
        }).create()) {
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("welcome_message");
            singleplayer.getServer().runCommand("time set day");
            singleplayer.getServer().runCommand("gamerule doDaylightCycle false");
            singleplayer.getServer().runOnServer(this::setUpColony);
            context.waitTicks(60);

            singleplayer.getServer().runOnServer(server -> open(server, townHall));
            context.waitForScreen(BuildingScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("townhall_overview");
            context.clickScreenButton("gui.hearthstead.tab.citizens");
            context.waitTicks(2);
            context.takeScreenshot("townhall_citizens");
            context.clickScreenButton("gui.hearthstead.tab.colony");
            context.waitTicks(2);
            context.takeScreenshot("townhall_colony");
            context.setScreen(() -> null);

            singleplayer.getServer().runOnServer(server -> open(server, residence));
            context.waitForScreen(BuildingScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("residence_overview");
            context.clickScreenButton("gui.hearthstead.tab.construction");
            context.waitTicks(2);
            context.takeScreenshot("residence_construction");
            context.setScreen(() -> null);

            singleplayer.getServer().runOnServer(this::openCitizen);
            context.waitForScreen(CitizenScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("citizen");
            context.setScreen(() -> null);

            // University research tab with a research in progress.
            BlockPos university = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                Colony colony = ColonyManager.get(server).colonyAt(level.dimension(), townHall);
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, townHall.offset(0, 0, -16));
                BuildingData building = placeHut(level, colony, pos, BuildingType.UNIVERSITY, true);
                colony.research().complete(dev.pikachux.hearthstead.colony.Research.APPRENTICESHIP, colony);
                HutBlockEntity storage = (HutBlockEntity) level.getBlockEntity(pos);
                storage.setItem(0, new ItemStack(Items.BREAD, 16));
                colony.research().start(level, colony, building, dev.pikachux.hearthstead.colony.Research.COMMUNITY_SPIRIT);
                colony.research().addProgress(server, colony, 120);
                storage.setItem(1, new ItemStack(Items.IRON_INGOT, 5));
                return pos;
            });
            singleplayer.getServer().runOnServer(server -> open(server, university));
            context.waitForScreen(BuildingScreen.class);
            context.clickScreenButton("gui.hearthstead.tab.research");
            context.waitTicks(5);
            context.takeScreenshot("university_research");
            // Select "Comfortable Homes" (row 2 of the list) to show its details.
            context.runOnClient(client -> {
                var screen = client.screen;
                double x = (screen.width - 292) / 2.0 + 40;
                double y = (screen.height - 220) / 2.0 + 46 + 3 + 2 * 12 + 4;
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y,
                        new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
            });
            context.waitTicks(3);
            context.takeScreenshot("university_research_details");
            context.setScreen(() -> null);

            // Build tool: choose a hut, preview it, then place it.
            for (BuildingType type : BuildingType.values()) {
                String hut = type.blockName().substring("blockhut".length());
                singleplayer.getServer().runCommand("give @a hearthstead:blockhut" + hut);
            }
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = player(server);
                player.teleportTo(player.level(), townHall.getX() - 12.5, townHall.getY() + 6, townHall.getZ() - 18.5,
                        java.util.Set.of(), -30, 25, false);
            });
            context.waitTicks(20);
            BlockPos previewPos = singleplayer.getServer().computeOnServer(server -> player(server).level()
                    .getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, townHall.offset(-6, 0, -8)));
            context.setScreen(() -> new HutSelectScreen(previewPos, Direction.NORTH));
            context.waitTicks(5);
            context.takeScreenshot("build_tool_select");
            context.runOnClient(client -> {
                PendingPlacement.set(previewPos, Direction.NORTH);
                ClientPlayNetworking.send(new BlueprintRequestPayload(BuildingType.LUMBERJACK.id(), java.util.Optional.empty(), ""));
            });
            context.waitForScreen(BuildToolScreen.class);
            context.waitTicks(10);
            context.takeScreenshot("build_tool_preview");
            // Switch the preview to the desert style before placing it.
            context.runOnClient(client -> {
                PendingPlacement.set(previewPos, Direction.NORTH);
                ClientPlayNetworking.send(new BlueprintRequestPayload(BuildingType.LUMBERJACK.id(), java.util.Optional.empty(), "desert"));
            });
            context.waitTicks(10);
            context.takeScreenshot("build_tool_preview_desert");
            context.clickScreenButton("gui.hearthstead.build_tool.place");
            context.waitTicks(20);
            boolean placed = singleplayer.getServer().computeOnServer(server -> {
                Colony colony = ColonyManager.get(server).colonyAt(player(server).level().dimension(), previewPos);
                return colony != null && colony.building(previewPos) != null
                        && colony.building(previewPos).type() == BuildingType.LUMBERJACK
                        && colony.building(previewPos).style().equals("desert");
            });
            if (!placed) {
                throw new AssertionError("The build tool did not place the lumberjack hut");
            }

            // A row of hut blocks, to show their models.
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, townHall.offset(-14, 0, -24));
                BuildingType[] shown = {BuildingType.TOWN_HALL, BuildingType.LUMBERJACK, BuildingType.FARMER,
                        BuildingType.MINER, BuildingType.BUILDER, BuildingType.COOK};
                for (int i = 0; i < shown.length; i++) {
                    level.setBlockAndUpdate(base.offset(i - 2, 0, 0), ModBlocks.HUTS.get(shown[i]).defaultBlockState()
                            .setValue(HutBlock.FACING, Direction.NORTH));
                }
                // A timber-style residence with its fenced front yard behind the row of huts.
                Colony colony = ColonyManager.get(server).colonyAt(level.dimension(), townHall);
                BlockPos house = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.offset(1, 0, 14));
                level.setBlockAndUpdate(house, ModBlocks.HUTS.get(BuildingType.RESIDENCE).defaultBlockState()
                        .setValue(HutBlock.FACING, Direction.NORTH));
                BuildingData residence = new BuildingData(house, BuildingType.RESIDENCE, 0);
                residence.setStyle("timber");
                colony.addBuilding(residence);
                Construction.buildInstantly(level, Construction.plan(level, residence, 2));
                residence.setLevel(2);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, base.getX() + 1.0, base.getY() + 2.5, base.getZ() - 4.5, java.util.Set.of(), 0, 35, false);
            });
            context.waitTicks(40);
            context.takeScreenshot("hut_blocks");

            singleplayer.getServer().runCommand("difficulty normal");
            singleplayer.getServer().runOnServer(this::lineUpBarbarians);
            context.waitTicks(40);
            context.takeScreenshot("barbarians");

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = player(server);
                Colony colony = ColonyManager.get(server).colonyAt(player.level().dimension(), townHall);
                player.teleportTo(townHall.getX() + 0.5, townHall.getY() + 8, townHall.getZ() - 6.5);
                colony.raid().start(server, colony, player.level());
            });
            context.waitTicks(40);
            context.takeScreenshot("raid_started");
        }
    }

    /** Places one raider of each kind in front of the player, frozen for the picture. */
    private void lineUpBarbarians(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, townHall.offset(-10, 0, -20));
        player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, java.util.Set.of(), 0, 10, false);
        var types = java.util.List.of(ModEntities.ARCHER_BARBARIAN, ModEntities.CHIEF_BARBARIAN, ModEntities.BARBARIAN);
        for (int i = 0; i < types.size(); i++) {
            AbstractBarbarian raider = types.get(i).create(level, EntitySpawnReason.COMMAND);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.offset(i - 1, 0, 4));
            raider.snapTo(pos.getX() + 0.5 + (i - 1) * 0.6, pos.getY(), pos.getZ() + 0.5, 180, 0);
            raider.setYHeadRot(180);
            raider.setYBodyRot(180);
            raider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
            raider.setNoAi(true);
            level.addFreshEntity(raider);
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private void setUpColony(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        townHall = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(10, 0, 0));
        BlockPos builder = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, townHall.offset(0, 0, 16));
        residence = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, townHall.offset(16, 0, 0));

        ColonyManager manager = ColonyManager.get(server);
        Colony colony = manager.createColony(player, level.dimension(), townHall);
        colony.setName("Riverside");
        colony.removeWorkOrder(colony.workOrder(townHall));
        placeHut(level, colony, townHall, BuildingType.TOWN_HALL, true);
        BuildingData builderHut = placeHut(level, colony, builder, BuildingType.BUILDER, true);
        placeHut(level, colony, residence, BuildingType.RESIDENCE, false);
        colony.addWorkOrder(new WorkOrder(residence, 1));

        HutBlockEntity storage = (HutBlockEntity) level.getBlockEntity(builderHut.pos());
        storage.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        storage.setItem(1, new ItemStack(Items.OAK_PLANKS, 30));
        storage.setItem(2, new ItemStack(Items.SPRUCE_PLANKS, 64));
        storage.setItem(3, new ItemStack(Items.GLASS, 6));
        for (int i = 0; i < 4; i++) {
            colony.addNewCitizen(level);
        }
    }

    private static BuildingData placeHut(ServerLevel level, Colony colony, BlockPos pos, BuildingType type, boolean build) {
        level.setBlockAndUpdate(pos, ModBlocks.HUTS.get(type).defaultBlockState().setValue(HutBlock.FACING, Direction.WEST));
        ((HutBlockEntity) level.getBlockEntity(pos)).setColonyId(colony.id());
        BuildingData building = colony.building(pos);
        if (building == null) {
            building = new BuildingData(pos, type, 0);
            colony.addBuilding(building);
        }
        if (build) {
            Construction.buildInstantly(level, Construction.plan(level, building, 1));
            building.setLevel(1);
        }
        return building;
    }

    private void open(MinecraftServer server, BlockPos pos) {
        Colony colony = ColonyManager.get(server).colonyAt(player(server).level().dimension(), pos);
        BuildingViews.sendBuilding(player(server), colony, colony.building(pos));
    }

    private void openCitizen(MinecraftServer server) {
        ServerPlayer player = player(server);
        Colony colony = ColonyManager.get(server).colonyAt(player.level().dimension(), townHall);
        CitizenData data = colony.citizens().iterator().next();
        CitizenEntity entity = (CitizenEntity) player.level().getEntity(data.entityUuid());
        player.teleportTo(entity.getX() + 2, entity.getY(), entity.getZ());
        BuildingViews.sendCitizen(player, entity, data);
    }
}
