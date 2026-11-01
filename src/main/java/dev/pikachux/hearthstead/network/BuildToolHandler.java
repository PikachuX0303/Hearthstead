package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.blueprint.Styles;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.colony.Decoration;
import dev.pikachux.hearthstead.colony.WorkOrder;
import dev.pikachux.hearthstead.registry.ModBlocks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server side of the build tool: sends blueprints for previews and places huts.
 */
public final class BuildToolHandler {
    /** How far from the player a hut may be placed with the build tool. */
    private static final double MAX_DISTANCE = 64;

    private BuildToolHandler() {
    }

    static void sendBlueprint(ServerPlayer player, BlueprintRequestPayload request) {
        ServerLevel level = player.level();
        BuildingType type = BuildingType.byId(request.buildingType());
        int targetLevel = 1;
        Direction facing = Direction.NORTH;
        Colony playerColony = ColonyManager.get(level.getServer()).colonyAt(level.dimension(), player.blockPosition());
        if (playerColony == null) {
            playerColony = ColonyManager.get(level.getServer()).colonyOf(player);
        }
        String style = playerColony == null ? Styles.CLASSIC : playerColony.style();
        if (!request.decoration().isEmpty()) {
            sendDecoration(player, request, style);
            return;
        }
        if (request.existingHut().isPresent()) {
            BlockPos pos = request.existingHut().get();
            Colony colony = ColonyManager.get(level.getServer()).colonyAt(level.dimension(), pos);
            BuildingData building = colony == null ? null : colony.building(pos);
            if (building == null) {
                player.displayClientMessage(Component.translatable("message.hearthstead.missing_hut").withStyle(ChatFormatting.RED), true);
                return;
            }
            if (building.level() >= BuildingType.MAX_LEVEL) {
                player.displayClientMessage(Component.translatable("message.hearthstead.max_level").withStyle(ChatFormatting.RED), true);
                return;
            }
            type = building.type();
            targetLevel = building.level() + 1;
            facing = Blueprints.facing(level, pos);
            style = building.style();
        }
        if (!request.style().isEmpty() && Styles.isValid(level.getServer(), request.style())) {
            style = request.style();
        }
        BuildingData preview = new BuildingData(BlockPos.ZERO, type, 0);
        preview.setStyle(style);
        Blueprint blueprint = Blueprints.get(level.getServer(), preview, targetLevel);
        if (blueprint == null) {
            player.displayClientMessage(Component.translatable("message.hearthstead.no_blueprint").withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerPlayNetworking.send(player, new BlueprintPayload(type.id(), targetLevel, blueprint, request.existingHut(), facing,
                style, Styles.available(level.getServer()), ""));
    }

    private static void sendDecoration(ServerPlayer player, BlueprintRequestPayload request, String style) {
        MinecraftServer server = player.level().getServer();
        if (!request.style().isEmpty() && Styles.isValid(server, request.style())) {
            style = request.style();
        }
        Blueprint blueprint = Blueprints.decoration(server, style, request.decoration());
        if (blueprint == null) {
            fail(player, "message.hearthstead.no_blueprint");
            return;
        }
        ServerPlayNetworking.send(player, new BlueprintPayload("", 1, blueprint, Optional.empty(), Direction.NORTH,
                style, Styles.available(server), request.decoration()));
    }

    /** Tells a player which decorations the build tool can place. */
    public static void sendDecorations(ServerPlayer player) {
        ServerPlayNetworking.send(player, decorationList(player.level().getServer()));
    }

    public static DecorationsPayload decorationList(MinecraftServer server) {
        List<DecorationsPayload.Entry> entries = new ArrayList<>();
        for (String name : Blueprints.decorations(server)) {
            Blueprint blueprint = Blueprints.decoration(server, Styles.CLASSIC, name);
            if (blueprint != null) {
                entries.add(new DecorationsPayload.Entry(name, Blueprints.decorationIcon(server, name, blueprint)));
            }
        }
        return new DecorationsPayload(entries);
    }

    static void placeDecoration(ServerPlayer player, PlaceDecorationPayload request) {
        ServerLevel level = player.level();
        BlockPos pos = request.pos();
        if (!request.facing().getAxis().isHorizontal() || !level.isLoaded(pos)
                || !player.blockPosition().closerToCenterThan(pos.getCenter(), MAX_DISTANCE)) {
            return;
        }
        Colony colony = ColonyManager.get(level.getServer()).colonyAt(level.dimension(), pos);
        if (colony == null) {
            fail(player, "message.hearthstead.not_in_colony");
            return;
        }
        if (!colony.isMember(player)) {
            fail(player, "message.hearthstead.no_permission");
            return;
        }
        String error = orderDecoration(level, colony, request.decoration(), pos, request.facing(), request.style());
        if (error != null) {
            fail(player, error);
            return;
        }
        player.displayClientMessage(Component.translatable("message.hearthstead.decoration_ordered",
                Component.translatableWithFallback("decoration.hearthstead." + request.decoration(),
                        Decoration.displayName(request.decoration()))).withStyle(ChatFormatting.GREEN), true);
    }

    /** Queues a decoration for the colony's builders; returns null or a translation key describing the problem. */
    @Nullable
    public static String orderDecoration(ServerLevel level, Colony colony, String name, BlockPos pos, Direction facing, String style) {
        if (!Styles.isValid(level.getServer(), style)) {
            style = colony.style();
        }
        String error = Decoration.placementError(level, colony, name, pos, facing, style);
        if (error == null) {
            colony.addWorkOrder(WorkOrder.decoration(pos, name, facing, style));
        }
        return error;
    }

    static void placeHut(ServerPlayer player, PlaceHutPayload request) {
        ServerLevel level = player.level();
        BuildingType type = BuildingType.byId(request.buildingType());
        HutBlock block = ModBlocks.HUTS.get(type);
        BlockPos pos = request.pos();
        if (!request.facing().getAxis().isHorizontal() || !level.isLoaded(pos)
                || !player.blockPosition().closerToCenterThan(pos.getCenter(), MAX_DISTANCE)) {
            return;
        }
        if (!level.getBlockState(pos).canBeReplaced()) {
            fail(player, "message.hearthstead.place_blocked");
            return;
        }
        int slot = findHutItem(player.getInventory(), block);
        if (slot < 0 && !player.getAbilities().instabuild) {
            fail(player, "message.hearthstead.no_hut_item");
            return;
        }
        String error = block.placementError(level, pos, player);
        if (error != null) {
            fail(player, error);
            return;
        }
        BlockState state = block.defaultBlockState().setValue(HutBlock.FACING, request.facing());
        ItemStack stack = slot >= 0 ? player.getInventory().getItem(slot) : new ItemStack(block);
        level.setBlockAndUpdate(pos, state);
        block.setPlacedBy(level, pos, state, player, stack);
        Colony colony = ColonyManager.get(level.getServer()).colonyAt(level.dimension(), pos);
        BuildingData building = colony == null ? null : colony.building(pos);
        if (building != null && Styles.isValid(level.getServer(), request.style())) {
            building.setStyle(request.style());
            colony.markDirty();
        }
        if (slot >= 0 && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private static int findHutItem(Inventory inventory, HutBlock block) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(block.asItem())) {
                return i;
            }
        }
        return -1;
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }
}
