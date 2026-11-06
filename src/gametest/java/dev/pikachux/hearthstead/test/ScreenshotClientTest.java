package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.client.buildtool.BuildToolScreen;
import dev.pikachux.hearthstead.client.buildtool.HutSelectScreen;
import dev.pikachux.hearthstead.client.buildtool.PendingPlacement;
import dev.pikachux.hearthstead.client.gui.BuildingScreen;
import dev.pikachux.hearthstead.client.gui.CitizenScreen;
import dev.pikachux.hearthstead.client.gui.UpdatePromptScreen;
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
        // The automatic updates question, shown on the title screen at the first start.
        context.setScreen(() -> new UpdatePromptScreen(new net.minecraft.client.gui.screens.TitleScreen(), enabled -> {
        }));
        context.waitTicks(5);
        context.takeScreenshot("update_prompt");
        // The settings screen (Cloth Config is on the development classpath) and its Raids tab.
        context.setScreen(() -> dev.pikachux.hearthstead.client.config.ConfigScreens.create(
                new net.minecraft.client.gui.screens.TitleScreen()));
        context.waitTicks(5);
        context.takeScreenshot("config_general");
        // Cloth Config's tabs are not vanilla buttons, so click the Raids tab with the mouse.
        double[] tab = context.computeOnClient(client -> {
            for (var child : client.screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                        && widget.getMessage().getString().equals("Raids")) {
                    double scale = client.getWindow().getGuiScale();
                    return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale,
                            (widget.getY() + widget.getHeight() / 2.0) * scale};
                }
            }
            throw new AssertionError("No Raids tab in the settings screen");
        });
        context.getInput().setCursorPos(tab[0], tab[1]);
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(5);
        context.takeScreenshot("config_raids");
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
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
            // The permissions screen: let friends break blocks, then back to the town hall.
            context.clickScreenButton("gui.hearthstead.permissions.open");
            context.waitForScreen(dev.pikachux.hearthstead.client.gui.PermissionsScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("permissions");
            context.clickScreenButton("gui.hearthstead.permissions.no");
            context.waitTicks(10);
            boolean friendsBreak = singleplayer.getServer().computeOnServer(server -> ColonyManager.get(server)
                    .colonyAt(player(server).level().dimension(), townHall).permissions()
                    .rules(dev.pikachux.hearthstead.colony.ColonyPermissions.Rank.FRIEND)
                    .contains(dev.pikachux.hearthstead.colony.ColonyPermissions.Action.BREAK));
            if (!friendsBreak) {
                throw new AssertionError("The first toggle should allow friends to break blocks");
            }
            singleplayer.getServer().runOnServer(server -> open(server, townHall));
            context.waitForScreen(BuildingScreen.class);
            context.waitTicks(5);
            // Carry some bread, so the requests tab offers to hand it in for the residents.
            singleplayer.getServer().runCommand("give @a minecraft:bread 8");
            context.clickScreenButton("gui.hearthstead.tab.requests");
            context.waitTicks(50);
            context.takeScreenshot("townhall_requests");
            // Press Give on the first request (the residents' food) and check the bread arrives.
            context.runOnClient(client -> {
                var screen = client.screen;
                int left = (screen.width - 292) / 2;
                int top = (screen.height - 220) / 2;
                double x = left + 10 + 3 + (292 - 20 - 10) - 34 + 10;
                double y = top + 58 + 3 + 2 + 6;
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y,
                        new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
            });
            context.waitTicks(10);
            int bread = singleplayer.getServer().computeOnServer(server ->
                    ((HutBlockEntity) player(server).level().getBlockEntity(townHall)).countItem(Items.BREAD));
            if (bread != 8) {
                throw new AssertionError("Give should hand the 8 bread to the town hall, found " + bread);
            }
            context.setScreen(() -> null);

            // The clipboard shows the same requests from anywhere.
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = player(server);
                // Eat up the bread handed in above, so the residents ask for food again.
                ((HutBlockEntity) player.level().getBlockEntity(townHall)).clearContent();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(dev.pikachux.hearthstead.registry.ModItems.CLIPBOARD));
                player.getInventory().add(new ItemStack(Items.BREAD, 5));
                dev.pikachux.hearthstead.network.BuildingViews.sendRequests(player,
                        ColonyManager.get(server).colonyOf(player), false);
            });
            context.waitForScreen(dev.pikachux.hearthstead.client.gui.ClipboardScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("clipboard");
            context.runOnClient(client -> {
                var screen = client.screen;
                int left = (screen.width - 260) / 2;
                int top = (screen.height - 220) / 2;
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(left + 219, top + 47,
                        new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
            });
            context.waitTicks(10);
            int handedIn = singleplayer.getServer().computeOnServer(server ->
                    ((HutBlockEntity) player(server).level().getBlockEntity(townHall)).countItem(Items.BREAD));
            if (handedIn != 5) {
                throw new AssertionError("Give on the clipboard should hand the 5 bread to the town hall, found " + handedIn);
            }
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
            context.getInput().setCursorPos(0, 0); // keep the skill tooltips out of the picture
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
            // Switch the preview to the Desert Oasis (sandstone) style before placing it.
            context.runOnClient(client -> {
                PendingPlacement.set(previewPos, Direction.NORTH);
                ClientPlayNetworking.send(new BlueprintRequestPayload(BuildingType.LUMBERJACK.id(), java.util.Optional.empty(), "sandstone"));
            });
            context.waitTicks(10);
            context.takeScreenshot("build_tool_preview_sandstone");
            context.clickScreenButton("gui.hearthstead.build_tool.place");
            context.waitTicks(20);
            boolean placed = singleplayer.getServer().computeOnServer(server -> {
                Colony colony = ColonyManager.get(server).colonyAt(player(server).level().dimension(), previewPos);
                return colony != null && colony.building(previewPos) != null
                        && colony.building(previewPos).type() == BuildingType.LUMBERJACK
                        && colony.building(previewPos).style().equals("sandstone");
            });
            if (!placed) {
                throw new AssertionError("The build tool did not place the lumberjack hut");
            }

            // Decorations: the build tool's second tab, a preview, and the order for the builders.
            HutSelectScreen.showDecorations(true);
            context.setScreen(() -> new HutSelectScreen(previewPos, Direction.NORTH));
            context.waitTicks(5);
            context.takeScreenshot("build_tool_decorations");
            HutSelectScreen.showDecorations(false);
            BlockPos fountainPos = singleplayer.getServer().computeOnServer(server -> freeDecorationSpot(server, townHall, previewPos));
            context.runOnClient(client -> {
                PendingPlacement.set(fountainPos, Direction.NORTH);
                ClientPlayNetworking.send(BlueprintRequestPayload.decoration("fountain", ""));
            });
            context.waitForScreen(BuildToolScreen.class);
            context.waitTicks(10);
            context.takeScreenshot("build_tool_decoration_preview");
            context.clickScreenButton("gui.hearthstead.build_tool.order_decoration");
            context.waitTicks(10);
            boolean ordered = singleplayer.getServer().computeOnServer(server -> {
                Colony colony = ColonyManager.get(server).colonyAt(player(server).level().dimension(), townHall);
                return colony.workOrders().stream().anyMatch(o -> o.isDecoration() && "fountain".equals(o.decoration())
                        && o.buildingPos().equals(fountainPos));
            });
            if (!ordered) {
                throw new AssertionError("The build tool did not order the fountain");
            }

            // The lumberjack's Settings tab: turn replanting off.
            singleplayer.getServer().runOnServer(server -> open(server, previewPos));
            context.waitForScreen(BuildingScreen.class);
            context.clickScreenButton("gui.hearthstead.tab.settings");
            context.waitTicks(5);
            context.takeScreenshot("lumberjack_settings");
            context.clickScreenButton("setting.hearthstead.replant.on");
            context.waitTicks(10);
            String replant = singleplayer.getServer().computeOnServer(server -> {
                Colony colony = ColonyManager.get(server).colonyAt(player(server).level().dimension(), previewPos);
                return dev.pikachux.hearthstead.colony.HutSettings.get(colony.building(previewPos),
                        dev.pikachux.hearthstead.colony.HutSettings.REPLANT);
            });
            if (!"off".equals(replant)) {
                throw new AssertionError("The settings button should turn replanting off, got " + replant);
            }
            context.setScreen(() -> null);

            // A postbox with two orders: its Orders tab.
            BlockPos postboxPos = singleplayer.getServer().computeOnServer(server -> {
                ServerLevel level = player(server).level();
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, previewPos.offset(-10, 0, 0));
                level.setBlockAndUpdate(pos, ModBlocks.HUTS.get(BuildingType.POSTBOX).defaultBlockState());
                Colony colony = ColonyManager.get(server).colonyAt(level.dimension(), pos);
                BuildingData postbox = new BuildingData(pos, BuildingType.POSTBOX, 1);
                postbox.setOrder("minecraft:bread", 16);
                postbox.setOrder("minecraft:torch", 32);
                colony.addBuilding(postbox);
                return pos;
            });
            singleplayer.getServer().runOnServer(server -> open(server, postboxPos));
            context.waitForScreen(BuildingScreen.class);
            context.clickScreenButton("gui.hearthstead.tab.orders");
            context.waitTicks(5);
            context.takeScreenshot("postbox_orders");
            context.setScreen(() -> null);

            // A blacksmith's Settings tab: two settings.
            BlockPos smithyPos = singleplayer.getServer().computeOnServer(server -> {
                ServerLevel level = player(server).level();
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, previewPos.offset(-10, 0, 4));
                level.setBlockAndUpdate(pos, ModBlocks.HUTS.get(BuildingType.BLACKSMITH).defaultBlockState());
                Colony colony = ColonyManager.get(server).colonyAt(level.dimension(), pos);
                colony.addBuilding(new BuildingData(pos, BuildingType.BLACKSMITH, 1));
                return pos;
            });
            singleplayer.getServer().runOnServer(server -> open(server, smithyPos));
            context.waitForScreen(BuildingScreen.class);
            context.clickScreenButton("gui.hearthstead.tab.settings");
            context.waitTicks(5);
            context.takeScreenshot("blacksmith_settings");
            context.setScreen(() -> null);

            // A sawmill taught two recipes: its Recipes tab.
            BlockPos sawmillPos = singleplayer.getServer().computeOnServer(server -> {
                ServerLevel level = player(server).level();
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, previewPos.offset(-10, 0, 4));
                level.setBlockAndUpdate(pos, ModBlocks.HUTS.get(BuildingType.SAWMILL).defaultBlockState());
                Colony colony = ColonyManager.get(server).colonyAt(level.dimension(), pos);
                BuildingData sawmill = new BuildingData(pos, BuildingType.SAWMILL, 2);
                sawmill.teach("minecraft:chest", 4);
                sawmill.teach("minecraft:barrel", 0);
                sawmill.teach("minecraft:bowl", 8);
                colony.addBuilding(sawmill);
                return pos;
            });
            singleplayer.getServer().runOnServer(server -> open(server, sawmillPos));
            context.waitForScreen(BuildingScreen.class);
            context.clickScreenButton("gui.hearthstead.tab.recipes");
            context.waitTicks(5);
            context.takeScreenshot("recipes");
            context.setScreen(() -> null);

            // A row of hut blocks, to show their models.
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, townHall.offset(-14, 0, -24));
                BuildingType[] shown = {BuildingType.TOWN_HALL, BuildingType.LIBRARY, BuildingType.SCHOOL,
                        BuildingType.TAVERN, BuildingType.GRAVEYARD, BuildingType.POSTBOX,
                        BuildingType.MYSTICAL_SITE, BuildingType.HOSPITAL};
                for (int i = 0; i < shown.length; i++) {
                    level.setBlockAndUpdate(base.offset(i - 2, 0, 0), ModBlocks.HUTS.get(shown[i]).defaultBlockState()
                            .setValue(HutBlock.FACING, Direction.NORTH));
                }
                level.setBlockAndUpdate(base.offset(shown.length - 2, 0, 0), ModBlocks.SCARECROW.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
                // A Shire-style residence behind the row of huts.
                Colony colony = ColonyManager.get(server).colonyAt(level.dimension(), townHall);
                BlockPos house = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.offset(1, 0, 14));
                level.setBlockAndUpdate(house, ModBlocks.HUTS.get(BuildingType.RESIDENCE).defaultBlockState()
                        .setValue(HutBlock.FACING, Direction.NORTH));
                BuildingData residence = new BuildingData(house, BuildingType.RESIDENCE, 0);
                residence.setStyle("shire");
                colony.addBuilding(residence);
                Construction.buildInstantly(level, Construction.plan(level, residence, 2));
                residence.setLevel(2);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, base.getX() + 2.5, base.getY() + 2.5, base.getZ() - 5.5, java.util.Set.of(), 0, 32, false);
            });
            context.waitTicks(20);
            // Many blocks just changed nearby: rebuild every chunk mesh so none is left out of the picture.
            // The mouse turns the camera when no screen is open, so the view is set on the client too.
            context.runOnClient(client -> {
                client.player.setYRot(0);
                client.player.setXRot(32);
                client.levelRenderer.allChanged();
            });
            try {
                singleplayer.getClientWorld().waitForChunksRender();
            } catch (AssertionError slowRendering) {
                context.waitTicks(60);
            }
            context.takeScreenshot("hut_blocks");

            singleplayer.getServer().runCommand("difficulty normal");
            singleplayer.getServer().runOnServer(server -> lineUpRaiders(server, java.util.List.of(ModEntities.ARCHER_BARBARIAN,
                    ModEntities.CHIEF_BARBARIAN, ModEntities.BARBARIAN), -10, -20));
            context.waitTicks(40);
            context.takeScreenshot("barbarians");
            // The pirates take the barbarians' place for their picture.
            singleplayer.getServer().runOnServer(server -> {
                ServerLevel level = player(server).level();
                level.getEntitiesOfClass(AbstractBarbarian.class, new net.minecraft.world.phys.AABB(townHall).inflate(48))
                        .forEach(net.minecraft.world.entity.Entity::discard);
                lineUpRaiders(server, java.util.List.of(ModEntities.PIRATE_ARCHER, ModEntities.PIRATE_CAPTAIN, ModEntities.PIRATE), -10, -20);
            });
            context.waitTicks(40);
            context.takeScreenshot("pirates");

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = player(server);
                Colony colony = ColonyManager.get(server).colonyAt(player.level().dimension(), townHall);
                player.teleportTo(townHall.getX() + 0.5, townHall.getY() + 8, townHall.getZ() - 6.5);
                colony.raid().start(server, colony, player.level());
            });
            context.waitTicks(40);
            context.takeScreenshot("raid_started");

            // An abandoned settlement, generated by the real structure code away from the colony.
            singleplayer.getServer().runCommand("execute as @a at @s run hearthstead raid stop");
            // The camp generates around the middle of the chunk it is placed in.
            BlockPos camp = new net.minecraft.world.level.ChunkPos(townHall.offset(32, 0, 30)).getMiddleBlockPosition(townHall.getY());
            context.runOnClient(client -> client.options.renderDistance().set(4));
            singleplayer.getServer().runCommand("forceload add " + (camp.getX() - 24) + " " + (camp.getZ() - 24) + " "
                    + (camp.getX() + 24) + " " + (camp.getZ() + 24));
            singleplayer.getServer().runOnServer(server -> {
                dev.pikachux.hearthstead.config.HearthsteadConfig.SETTLEMENT_CHANCE.set(1.0);
                ServerPlayer player = player(server);
                player.teleportTo(player.level(), camp.getX() + 0.5, camp.getY() + 14, camp.getZ() - 13.5,
                        java.util.Set.of(), 0, 45, false);
            });
            context.waitTicks(40);
            singleplayer.getServer().runCommand("place structure hearthstead:abandoned_settlement "
                    + camp.getX() + " " + camp.getY() + " " + camp.getZ());
            context.waitTicks(40);
            try {
                singleplayer.getClientWorld().waitForChunksRender();
            } catch (AssertionError slowRendering) {
                // Rendering at 10 fps can take a while; the check below does not depend on it.
                context.waitTicks(100);
            }
            context.takeScreenshot("abandoned_settlement");
            boolean generated = singleplayer.getServer().computeOnServer(server -> {
                dev.pikachux.hearthstead.config.HearthsteadConfig.load();
                var level = player(server).level();
                return BlockPos.betweenClosedStream(camp.offset(-2, -3, -2), camp.offset(2, 3, 2))
                        .anyMatch(pos -> level.getBlockState(pos).is(ModBlocks.HUTS.get(BuildingType.TOWN_HALL)));
            });
            if (!generated) {
                throw new AssertionError("/place structure did not generate an abandoned settlement");
            }

            // Every decoration in rows of five, fronts towards the camera. The server only sends chunks
            // close to the player, so the camera looks down on the rows from above their front.
            BlockPos showcase = camp.offset(0, 0, 70);
            singleplayer.getServer().runCommand("forceload add " + (showcase.getX() - 40) + " " + (showcase.getZ() - 30) + " "
                    + (showcase.getX() + 40) + " " + (showcase.getZ() + 20));
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = player(server);
                player.teleportTo(player.level(), showcase.getX() + 0.5, showcase.getY() + 26, showcase.getZ() - 3.5,
                        java.util.Set.of(), 0, 62, false);
            });
            context.waitTicks(40);
            singleplayer.getServer().runOnServer(server -> buildDecorations(server, showcase));
            // Wait until the client has every chunk of the showcase, then for them to be drawn.
            for (int attempt = 0; attempt < 30; attempt++) {
                context.waitTicks(20);
                boolean received = context.computeOnClient(client -> {
                    for (int x = showcase.getX() - 26; x <= showcase.getX() + 26; x += 8) {
                        for (int z = showcase.getZ() - 6; z <= showcase.getZ() + 30; z += 8) {
                            if (!client.level.hasChunk(x >> 4, z >> 4)) {
                                return false;
                            }
                        }
                    }
                    return true;
                });
                if (received) {
                    break;
                }
            }
            context.runOnClient(client -> {
                // The mouse turns the camera when no screen is open, so set the view on the client.
                client.player.setYRot(0);
                client.player.setXRot(62);
                client.levelRenderer.allChanged();
            });
            try {
                singleplayer.getClientWorld().waitForChunksRender();
            } catch (AssertionError slowRendering) {
                context.waitTicks(100);
            }
            context.takeScreenshot("decorations");

            // Every job outfit, male and female, in a row on the open ground in front of the decorations.
            singleplayer.getServer().runOnServer(server -> lineUpOutfits(server, showcase.offset(0, 0, -18)));
            context.waitTicks(20);
            context.runOnClient(client -> {
                client.player.setYRot(0);
                client.player.setXRot(8);
            });
            context.waitTicks(20);
            context.takeScreenshot("citizen_outfits");
            // A closer look at the last few (the row ends with a woman), almost from the side.
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = player(server);
                BlockPos base = player.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, showcase.offset(0, 0, -18));
                player.teleportTo(player.level(), base.getX() + 12.3, base.getY(), base.getZ() + 11.3, java.util.Set.of(), 70, 8, false);
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                client.player.setYRot(70);
                client.player.setXRot(8);
            });
            context.waitTicks(10);
            context.takeScreenshot("citizen_outfits_close");
        }
    }

    /** A spot near the lumberjack, in the player's view, where a fountain may be ordered. */
    private static BlockPos freeDecorationSpot(MinecraftServer server, BlockPos townHall, BlockPos near) {
        ServerLevel level = player(server).level();
        Colony colony = ColonyManager.get(server).colonyAt(level.dimension(), townHall);
        BlockPos target = near.offset(2, 0, 9);
        BlockPos best = null;
        for (int dx = -15; dx <= 15; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.offset(dx, 0, dz));
                if ((best == null || pos.distManhattan(target) < best.distManhattan(target))
                        && dev.pikachux.hearthstead.colony.Decoration.placementError(level, colony, "fountain", pos,
                        Direction.NORTH, colony.style()) == null) {
                    best = pos;
                }
            }
        }
        if (best == null) {
            throw new AssertionError("No room for a fountain near " + near);
        }
        return best;
    }

    /** Builds every decoration, in the classic and Medieval Oak styles, in rows facing north. */
    private static void buildDecorations(MinecraftServer server, BlockPos center) {
        ServerLevel level = player(server).level();
        java.util.List<String> names = dev.pikachux.hearthstead.blueprint.Blueprints.decorations(server);
        int perRow = 5;
        for (int i = 0; i < names.size(); i++) {
            int column = i % perRow;
            int row = i / perRow;
            BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.offset((column - perRow / 2) * 9, 0, row * 10));
            var decoration = new dev.pikachux.hearthstead.colony.Decoration(names.get(i), spot, Direction.NORTH,
                    i % 2 == 0 ? dev.pikachux.hearthstead.blueprint.Styles.CLASSIC : "medievaloak");
            Construction.buildInstantly(level, decoration.plan(level));
        }
    }

    /** Places one raider of each kind in front of the player, frozen for the picture. */
    private void lineUpRaiders(MinecraftServer server, java.util.List<? extends net.minecraft.world.entity.EntityType<? extends AbstractBarbarian>> types,
                               int offsetX, int offsetZ) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, townHall.offset(offsetX, 0, offsetZ));
        player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, java.util.Set.of(), 0, 10, false);
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

    /** One frozen citizen per job outfit and gender, facing the player, who stands at {@code spot}. */
    private void lineUpOutfits(MinecraftServer server, BlockPos spot) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot);
        player.teleportTo(level, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, java.util.Set.of(), 0, 8, false);
        java.util.List<String> outfits = CitizenEntity.OUTFITS;
        // One row, each job's man and woman side by side.
        int count = outfits.size() * 2;
        for (int i = 0; i < count; i++) {
            CitizenEntity citizen = dev.pikachux.hearthstead.registry.ModEntities.CITIZEN.create(level, EntitySpawnReason.COMMAND);
            boolean female = i % 2 == 1;
            double x = base.getX() + 0.5 + (i - (count - 1) / 2.0) * 0.62;
            double z = base.getZ() + 12.5;
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, base.getY(), z));
            citizen.snapTo(x, pos.getY(), z, 180, 0);
            citizen.setYHeadRot(180);
            citizen.setYBodyRot(180);
            citizen.setNoAi(true);
            citizen.showOutfit(outfits.get(i / 2), female);
            level.addFreshEntity(citizen);
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
