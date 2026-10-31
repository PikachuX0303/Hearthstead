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
import dev.pikachux.hearthstead.registry.ModItems;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

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
        PayloadTypeRegistry.playS2C().register(RequestsViewPayload.TYPE, RequestsViewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClipboardActionPayload.TYPE, ClipboardActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HutActionPayload.TYPE, HutActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintRequestPayload.TYPE, BlueprintRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlaceHutPayload.TYPE, PlaceHutPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HutActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ClipboardActionPayload.TYPE,
                (payload, context) -> handleClipboard(context.player(), payload));
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
            // Huts hand in their own requests; the town hall hands in any request of the colony.
            case FULFILL_REQUEST -> handIn(player, colony, payload.argument(),
                    building.type() == BuildingType.TOWN_HALL ? null : building.pos());
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

    /** Buttons on the clipboard, which shows the requests of the player's own colony from anywhere. */
    private static void handleClipboard(ServerPlayer player, ClipboardActionPayload payload) {
        Colony colony = ColonyManager.get(player.level().getServer()).colonyOf(player);
        if (colony == null || !(player.getMainHandItem().is(ModItems.CLIPBOARD) || player.getOffhandItem().is(ModItems.CLIPBOARD))) {
            return;
        }
        if (payload.action() == ClipboardActionPayload.Action.FULFILL_REQUEST) {
            handIn(player, colony, payload.argument(), null);
        }
        BuildingViews.sendRequests(player, colony, true);
    }

    /**
     * Hands in items for the request named in {@code argument} ({@link HutActionPayload#requestArgument}).
     * When {@code onlyTarget} is set, only that building's requests may be handed in.
     */
    private static void handIn(ServerPlayer player, Colony colony, String argument, @Nullable BlockPos onlyTarget) {
        int split = argument.indexOf('|');
        if (split < 0) {
            return;
        }
        BlockPos target;
        try {
            target = BlockPos.of(Long.parseLong(argument.substring(0, split)));
        } catch (NumberFormatException e) {
            return;
        }
        if (onlyTarget != null && !onlyTarget.equals(target)) {
            return;
        }
        int moved = ColonyRequests.fulfill(player.level(), colony, player, target, argument.substring(split + 1));
        player.displayClientMessage(moved > 0
                ? Component.translatable("message.hearthstead.request_fulfilled", moved).withStyle(ChatFormatting.GREEN)
                : Component.translatable("message.hearthstead.request_nothing").withStyle(ChatFormatting.RED), true);
    }
}
