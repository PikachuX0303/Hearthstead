package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.blueprint.Styles;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingInfo;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.colony.ColonyRequests;
import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.colony.WorkOrder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers the mod's packets and handles button presses from the hut screen.
 */
public final class ModNetworking {
    private static final double MAX_DISTANCE = 16;

    private ModNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(BuildingViewPayload.TYPE, BuildingViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CitizenViewPayload.TYPE, CitizenViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BlueprintPayload.TYPE, BlueprintPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HutActionPayload.TYPE, HutActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintRequestPayload.TYPE, BlueprintRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlaceHutPayload.TYPE, PlaceHutPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HutActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BlueprintRequestPayload.TYPE,
                (payload, context) -> BuildToolHandler.sendBlueprint(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(PlaceHutPayload.TYPE,
                (payload, context) -> BuildToolHandler.placeHut(context.player(), payload));
    }

    private static void handle(ServerPlayer player, HutActionPayload payload) {
        Colony colony = ColonyManager.get(player.level().getServer()).colonyAt(player.level().dimension(), payload.pos());
        BuildingData building = colony == null ? null : colony.building(payload.pos());
        if (building == null || !colony.isMember(player)
                || !player.blockPosition().closerToCenterThan(payload.pos().getCenter(), MAX_DISTANCE)) {
            return;
        }
        switch (payload.action()) {
            case REFRESH -> {
            }
            case OPEN_INVENTORY -> {
                if (player.level().getBlockEntity(payload.pos()) instanceof HutBlockEntity hut) {
                    player.openMenu(hut);
                }
                return;
            }
            case UPGRADE -> {
                // The build tool may pick a new style for the upgrade.
                if (!payload.argument().isEmpty() && colony.workOrder(building.pos()) == null
                        && Styles.isValid(player.level().getServer(), payload.argument())) {
                    building.setStyle(payload.argument());
                    colony.markDirty();
                }
                String error = BuildingInfo.requestUpgrade(player.level(), colony, building);
                if (error != null) {
                    player.displayClientMessage(Component.translatable(error).withStyle(ChatFormatting.RED), true);
                }
            }
            case CANCEL_ORDER -> {
                WorkOrder order = colony.workOrder(building.pos());
                if (order != null) {
                    colony.removeWorkOrder(order);
                }
            }
            case START_RESEARCH -> {
                Research research = Research.byId(payload.argument());
                if (building.type() == BuildingType.UNIVERSITY && research != null) {
                    String error = colony.research().start(player.level(), colony, building, research);
                    if (error != null) {
                        player.displayClientMessage(Component.translatable(error).withStyle(ChatFormatting.RED), true);
                    }
                }
            }
            case CYCLE_STYLE -> {
                if (building.type() == BuildingType.TOWN_HALL && colony.owner().equals(player.getUUID())) {
                    colony.setStyle(Styles.next(Styles.available(player.level().getServer()), colony.style(), 1));
                }
            }
            case TOGGLE_RAIDS -> {
                if (building.type() == BuildingType.TOWN_HALL && colony.owner().equals(player.getUUID())) {
                    colony.raid().setEnabled(!colony.raid().enabled());
                    colony.markDirty();
                }
            }
            case FULFILL_REQUEST -> {
                int split = payload.argument().indexOf('|');
                if (split < 0) {
                    return;
                }
                BlockPos target;
                try {
                    target = BlockPos.of(Long.parseLong(payload.argument().substring(0, split)));
                } catch (NumberFormatException e) {
                    return;
                }
                // Huts hand in their own requests; the town hall hands in any request of the colony.
                if (!target.equals(building.pos()) && building.type() != BuildingType.TOWN_HALL) {
                    return;
                }
                int moved = ColonyRequests.fulfill(player.level(), colony, player, target, payload.argument().substring(split + 1));
                player.displayClientMessage(moved > 0
                        ? Component.translatable("message.hearthstead.request_fulfilled", moved).withStyle(ChatFormatting.GREEN)
                        : Component.translatable("message.hearthstead.request_nothing").withStyle(ChatFormatting.RED), true);
            }
            case RENAME_COLONY -> {
                String name = payload.argument().strip();
                if (building.type() == BuildingType.TOWN_HALL && colony.owner().equals(player.getUUID())
                        && !name.isEmpty() && name.length() <= 32) {
                    colony.setName(name);
                }
            }
        }
        BuildingViews.updateBuilding(player, colony, building);
    }
}
