package dev.pikachux.hearthstead.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.colony.ColonyRaid;
import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.colony.BuildingInfo;
import dev.pikachux.hearthstead.colony.WorkOrder;
import dev.pikachux.hearthstead.config.HearthsteadConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The {@code /hearthstead} command tree (alias {@code /hs}).
 */
public final class ColonyCommands {
    private ColonyCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("hearthstead")
                .then(Commands.literal("info").executes(ColonyCommands::info))
                .then(Commands.literal("list").executes(ColonyCommands::list))
                .then(Commands.literal("citizens").executes(ColonyCommands::citizens))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ColonyCommands::rename)))
                .then(Commands.literal("addofficer")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> officer(ctx, true))))
                .then(Commands.literal("removeofficer")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> officer(ctx, false))))
                .then(Commands.literal("upgrade")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ColonyCommands::upgrade)))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ColonyCommands::cancel)))
                .then(Commands.literal("workorders").executes(ColonyCommands::workOrders))
                .then(Commands.literal("build")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ColonyCommands::instantBuild)))
                .then(Commands.literal("raid")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("start").executes(ctx -> raid(ctx, true, null))
                                .then(Commands.literal("barbarians").executes(ctx -> raid(ctx, true, ColonyRaid.Culture.BARBARIANS)))
                                .then(Commands.literal("pirates").executes(ctx -> raid(ctx, true, ColonyRaid.Culture.PIRATES))))
                        .then(Commands.literal("stop").executes(ctx -> raid(ctx, false, null))))
                .then(Commands.literal("event")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("merchant").executes(ctx -> event(ctx, false)))
                        .then(Commands.literal("festival").executes(ctx -> event(ctx, true))))
                .then(Commands.literal("raids")
                        .then(Commands.literal("on").executes(ctx -> toggleRaids(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> toggleRaids(ctx, false))))
                .then(Commands.literal("research")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("complete")
                                .then(Commands.argument("research", StringArgumentType.word())
                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(Research.values()).map(Research::id), builder))
                                        .executes(ColonyCommands::completeResearch))))
                .then(Commands.literal("config")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("reload").executes(ColonyCommands::reloadConfig)))
                .then(Commands.literal("delete")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ColonyCommands::delete)));
        var node = dispatcher.register(root);
        dispatcher.register(Commands.literal("hs").redirect(node));
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        List<String> problems = HearthsteadConfig.load();
        if (problems.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.hearthstead.config_reloaded",
                    HearthsteadConfig.FILE_NAME).withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.hearthstead.config_problems",
                HearthsteadConfig.FILE_NAME, problems.size()).withStyle(ChatFormatting.GOLD), true);
        for (String problem : problems) {
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + problem).withStyle(ChatFormatting.GRAY), false);
        }
        return problems.size();
    }

    private static Colony ownColony(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Colony colony = ColonyManager.get(ctx.getSource().getServer()).colonyOf(player);
        if (colony == null) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.no_colony"));
        }
        return colony;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Colony colony = ownColony(ctx);
        if (colony == null) {
            return 0;
        }
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("command.hearthstead.info.header", colony.name(), colony.id()).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("command.hearthstead.info.owner", colony.ownerName(),
                colony.center().toShortString(), colony.dimension().identifier().toString()), false);
        source.sendSuccess(() -> Component.translatable("command.hearthstead.info.counts", colony.citizens().size(),
                colony.housingCapacity(), colony.buildings().size()), false);
        for (BuildingData building : colony.buildings()) {
            source.sendSuccess(() -> Component.translatable("command.hearthstead.info.building", building.type().nameComponent(),
                    building.level(), building.pos().toShortString()).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ColonyManager manager = ColonyManager.get(ctx.getSource().getServer());
        if (manager.colonies().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.hearthstead.list.none"), false);
        }
        for (Colony colony : manager.colonies()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.hearthstead.list.entry", colony.id(), colony.name(),
                    colony.ownerName(), colony.center().toShortString(), colony.citizens().size()), false);
        }
        return manager.colonies().size();
    }

    private static int citizens(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Colony colony = ownColony(ctx);
        if (colony == null) {
            return 0;
        }
        for (CitizenData citizen : colony.citizens()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.hearthstead.citizens.entry", citizen.name(),
                    Component.translatable(dev.pikachux.hearthstead.entity.CitizenEntity.roleKey(colony, citizen)),
                    String.format("%.1f", citizen.saturation()), String.format("%.1f", citizen.happiness()),
                    citizen.statusComponent()), false);
        }
        return colony.citizens().size();
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Colony colony = ownColony(ctx);
        if (colony == null) {
            return 0;
        }
        if (!colony.owner().equals(ctx.getSource().getPlayerOrException().getUUID())) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.owner_only"));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        colony.setName(name);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.hearthstead.renamed", name), false);
        return 1;
    }

    private static int officer(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        Colony colony = ownColony(ctx);
        if (colony == null) {
            return 0;
        }
        if (!colony.owner().equals(ctx.getSource().getPlayerOrException().getUUID())) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.owner_only"));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        colony.permissions().setRank(target.getUUID(), target.getName().getString(),
                add ? dev.pikachux.hearthstead.colony.ColonyPermissions.Rank.OFFICER
                        : dev.pikachux.hearthstead.colony.ColonyPermissions.Rank.VISITOR);
        ctx.getSource().sendSuccess(() -> Component.translatable(add ? "command.hearthstead.officer_added" : "command.hearthstead.officer_removed",
                target.getName(), colony.name()), false);
        return 1;
    }

    /** Looks up the hut at the position argument, checking membership and distance. */
    private static BuildingData targetHut(CommandContext<CommandSourceStack> ctx, boolean checkDistance) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        Colony colony = ColonyManager.get(ctx.getSource().getServer()).colonyAt(player.level().dimension(), pos);
        BuildingData building = colony == null ? null : colony.building(pos);
        if (building == null || !colony.isMember(player)) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.missing_hut"));
            return null;
        }
        if (checkDistance && !player.blockPosition().closerThan(pos, 16)) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.too_far"));
            return null;
        }
        return building;
    }

    private static Colony colonyAtArgument(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return ColonyManager.get(ctx.getSource().getServer())
                .colonyAt(player.level().dimension(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"));
    }

    private static int upgrade(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BuildingData building = targetHut(ctx, true);
        if (building == null) {
            return 0;
        }
        Colony colony = colonyAtArgument(ctx);
        String error = BuildingInfo.requestUpgrade(ctx.getSource().getPlayerOrException().level(), colony, building);
        if (error != null) {
            ctx.getSource().sendFailure(Component.translatable(error));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.hearthstead.order_created",
                building.type().nameComponent(), building.level() + 1).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BuildingData building = targetHut(ctx, false);
        if (building == null) {
            return 0;
        }
        Colony colony = colonyAtArgument(ctx);
        WorkOrder order = colony.workOrder(building.pos());
        if (order == null) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.no_order"));
            return 0;
        }
        colony.removeWorkOrder(order);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.hearthstead.order_cancelled",
                building.type().nameComponent()), false);
        return 1;
    }

    private static int workOrders(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Colony colony = ownColony(ctx);
        if (colony == null) {
            return 0;
        }
        if (colony.workOrders().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.hearthstead.workorders.none"), false);
        }
        for (WorkOrder order : colony.workOrders()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(order.builder() == null ? "command.hearthstead.workorders.waiting"
                    : "command.hearthstead.workorders.in_progress", BuildingInfo.describeComponent(colony, order)), false);
        }
        return colony.workOrders().size();
    }

    private static int instantBuild(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BuildingData building = targetHut(ctx, false);
        if (building == null) {
            return 0;
        }
        Colony colony = colonyAtArgument(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        WorkOrder order = colony.workOrder(building.pos());
        int target = order != null ? order.targetLevel() : building.level() + 1;
        List<Blueprint.Placement> plan = target > BuildingType.MAX_LEVEL ? null : Construction.plan(player.level(), building, target);
        if (plan == null) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.no_blueprint"));
            return 0;
        }
        Construction.buildInstantly(player.level(), plan);
        building.setLevel(target);
        if (order != null) {
            colony.removeWorkOrder(order);
        }
        colony.markDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.hearthstead.construction_done",
                building.type().nameComponent(), target).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Starts or stops a raid on the colony the operator stands in (or owns). */
    private static int raid(CommandContext<CommandSourceStack> ctx, boolean start, @org.jetbrains.annotations.Nullable ColonyRaid.Culture culture)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ColonyManager manager = ColonyManager.get(ctx.getSource().getServer());
        Colony colony = manager.colonyAt(player.level().dimension(), player.blockPosition());
        if (colony == null) {
            colony = manager.colonyOf(player);
        }
        if (colony == null) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.no_colony"));
            return 0;
        }
        if (!start) {
            if (!colony.raid().active()) {
                ctx.getSource().sendFailure(Component.translatable("message.hearthstead.no_raid"));
                return 0;
            }
            colony.raid().stop(ctx.getSource().getServer(), colony);
            return 1;
        }
        if (colony.raid().active()) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.raid_running"));
            return 0;
        }
        ServerLevel level = colony.level(ctx.getSource().getServer());
        if (level == null || !colony.raid().start(ctx.getSource().getServer(), colony, level, culture)) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.raid_failed"));
            return 0;
        }
        return 1;
    }

    /** Starts a colony event (a merchant visit or a festival) in the colony the operator stands in (or owns). */
    private static int event(CommandContext<CommandSourceStack> ctx, boolean festival) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ColonyManager manager = ColonyManager.get(ctx.getSource().getServer());
        Colony colony = manager.colonyAt(player.level().dimension(), player.blockPosition());
        if (colony == null) {
            colony = manager.colonyOf(player);
        }
        ServerLevel level = colony == null ? null : colony.level(ctx.getSource().getServer());
        if (level == null) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.no_colony"));
            return 0;
        }
        boolean started = festival ? colony.happenings().startFestival(ctx.getSource().getServer(), colony, level)
                : colony.happenings().sendMerchant(ctx.getSource().getServer(), colony, level);
        if (!started) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.event_failed"));
            return 0;
        }
        return 1;
    }

    private static int toggleRaids(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        Colony colony = ownColony(ctx);
        if (colony == null) {
            return 0;
        }
        if (!colony.owner().equals(ctx.getSource().getPlayerOrException().getUUID())) {
            ctx.getSource().sendFailure(Component.translatable("message.hearthstead.owner_only"));
            return 0;
        }
        colony.raid().setEnabled(enabled);
        colony.markDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.hearthstead.raids_toggled", colony.name(),
                Component.translatable(enabled ? "gui.hearthstead.raids_on" : "gui.hearthstead.raids_off")), false);
        return 1;
    }

    private static int completeResearch(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Colony colony = ownColony(ctx);
        Research research = Research.byId(StringArgumentType.getString(ctx, "research"));
        if (colony == null || research == null) {
            ctx.getSource().sendFailure(Component.translatable("command.hearthstead.unknown_research"));
            return 0;
        }
        colony.research().complete(research, colony);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.hearthstead.research_done",
                Component.translatable(research.nameKey()), colony.name()), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        ColonyManager manager = ColonyManager.get(ctx.getSource().getServer());
        if (manager.colony(id) == null) {
            ctx.getSource().sendFailure(Component.translatable("command.hearthstead.no_colony_id", id));
            return 0;
        }
        manager.deleteColony(id);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.hearthstead.deleted", id), true);
        return 1;
    }
}
