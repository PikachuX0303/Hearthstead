package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.blueprint.Styles;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingInfo;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.colony.ColonyPermissions;
import dev.pikachux.hearthstead.colony.ColonyRequests;
import dev.pikachux.hearthstead.colony.HutSettings;
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
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

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
        PayloadTypeRegistry.playS2C().register(PermissionsViewPayload.TYPE, PermissionsViewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PermissionActionPayload.TYPE, PermissionActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClipboardActionPayload.TYPE, ClipboardActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HutActionPayload.TYPE, HutActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintRequestPayload.TYPE, BlueprintRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlaceHutPayload.TYPE, PlaceHutPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HutActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(PermissionActionPayload.TYPE,
                (payload, context) -> handlePermissions(context.player(), payload));
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
        if (building == null || !player.blockPosition().closerToCenterThan(payload.pos().getCenter(), MAX_DISTANCE)
                || !mayUse(player, colony, payload.action())) {
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
            case OPEN_PERMISSIONS -> {
                if (building.type() == BuildingType.TOWN_HALL) {
                    sendPermissions(player, colony, false);
                }
                return;
            }
            case CHANGE_SETTING -> {
                HutSettings.Setting setting = HutSettings.byId(building.type(), payload.argument());
                if (setting != null) {
                    HutSettings.cycle(building, setting);
                    colony.markDirty();
                }
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

    /**
     * Owners and officers may press every button. Friends and visitors only get what the colony's
     * permissions allow: looking at hut windows (and handing in items), or opening hut inventories.
     */
    private static boolean mayUse(ServerPlayer player, Colony colony, HutActionPayload.Action action) {
        if (colony.isMember(player)) {
            return true;
        }
        var permissions = colony.permissions();
        return switch (action) {
            case REFRESH, FULFILL_REQUEST -> permissions.allows(player, ColonyPermissions.Action.HUT_WINDOWS);
            case OPEN_INVENTORY -> permissions.allows(player, ColonyPermissions.Action.CONTAINERS);
            default -> false;
        };
    }

    /** The colony's members and rules; everyone in it may look, only the owner changes them. */
    public static void sendPermissions(ServerPlayer player, Colony colony, boolean updateOnly) {
        ColonyPermissions permissions = colony.permissions();
        List<PermissionsViewPayload.Member> members = new ArrayList<>();
        members.add(new PermissionsViewPayload.Member(colony.owner(), colony.ownerName(), ColonyPermissions.Rank.OWNER.id()));
        for (UUID officer : colony.officers()) {
            members.add(new PermissionsViewPayload.Member(officer, permissions.name(officer), ColonyPermissions.Rank.OFFICER.id()));
        }
        for (UUID friend : permissions.friends()) {
            members.add(new PermissionsViewPayload.Member(friend, permissions.name(friend), ColonyPermissions.Rank.FRIEND.id()));
        }
        ServerPlayNetworking.send(player, new PermissionsViewPayload(colony.name(), colony.owner().equals(player.getUUID()), members,
                permissions.rules(ColonyPermissions.Rank.FRIEND).stream().map(ColonyPermissions.Action::id).toList(),
                permissions.rules(ColonyPermissions.Rank.VISITOR).stream().map(ColonyPermissions.Action::id).toList(),
                updateOnly));
    }

    private static void handlePermissions(ServerPlayer player, PermissionActionPayload payload) {
        Colony colony = ColonyManager.get(player.level().getServer()).colonyOf(player);
        if (colony == null) {
            return;
        }
        boolean owner = colony.owner().equals(player.getUUID());
        ColonyPermissions permissions = colony.permissions();
        switch (payload.action()) {
            case REFRESH -> {
            }
            case SET_RANK -> {
                ColonyPermissions.Rank rank = rank(payload.second());
                if (owner && rank != null) {
                    try {
                        UUID target = UUID.fromString(payload.first());
                        permissions.setRank(target, permissions.name(target), rank);
                    } catch (IllegalArgumentException ignored) {
                        // Not a UUID: nothing to change.
                    }
                }
            }
            case ADD_PLAYER -> {
                ColonyPermissions.Rank rank = rank(payload.second());
                ServerPlayer target = player.level().getServer().getPlayerList().getPlayerByName(payload.first().strip());
                if (!owner || rank == null || rank == ColonyPermissions.Rank.VISITOR) {
                    return;
                }
                if (target == null) {
                    player.displayClientMessage(Component.translatable("message.hearthstead.player_not_online", payload.first().strip())
                            .withStyle(ChatFormatting.RED), true);
                } else {
                    permissions.setRank(target.getUUID(), target.getName().getString(), rank);
                }
            }
            case TOGGLE -> {
                ColonyPermissions.Rank rank = rank(payload.first());
                ColonyPermissions.Action action = ColonyPermissions.Action.byId(payload.second());
                if (owner && action != null && (rank == ColonyPermissions.Rank.FRIEND || rank == ColonyPermissions.Rank.VISITOR)) {
                    permissions.setAllowed(rank, action, !permissions.rules(rank).contains(action));
                }
            }
        }
        sendPermissions(player, colony, true);
    }

    @Nullable
    private static ColonyPermissions.Rank rank(String id) {
        for (ColonyPermissions.Rank rank : ColonyPermissions.Rank.values()) {
            if (rank.id().equals(id)) {
                return rank;
            }
        }
        return null;
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
