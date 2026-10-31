package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.blueprint.Styles;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.registry.ModBlocks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

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
                style, Styles.available(level.getServer())));
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
