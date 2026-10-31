package dev.pikachux.hearthstead.event;

import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.registry.ModItems;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import dev.pikachux.hearthstead.colony.ColonyPermissions;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side hooks: ticking colonies and protecting colony land from outsiders.
 */
public final class ColonyEvents {
    private ColonyEvents() {
    }

    public static void register() {
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> Blueprints.clearCache());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Blueprints.clearCache());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                ColonyManager.get(server).tick(server);
            }
        });

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel)) {
                return true;
            }
            if (state.getBlock() instanceof HutBlock) {
                // Huts belong to the colony: only its owner and officers may take them down.
                Colony colony = ColonyManager.get(serverLevel.getServer()).colonyAt(level.dimension(), pos);
                if (colony != null && !colony.isMember(player) && !isOperator(player)) {
                    deny(player);
                    return false;
                }
                return true;
            }
            if (denied(level, player, pos, ColonyPermissions.Action.BREAK)) {
                deny(player);
                return false;
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() && player.getItemInHand(hand).is(ModItems.BUILD_TOOL)) {
                // The build tool works on the client; the server only blocks normal block use.
                return InteractionResult.SUCCESS;
            }
            if (!(level instanceof ServerLevel) || player.isSpectator()) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof HutBlock) {
                return InteractionResult.PASS;
            }
            if (denied(level, player, pos, actionFor(level, player, player.getItemInHand(hand), pos, state))) {
                deny(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level instanceof ServerLevel && (entity instanceof CitizenEntity || entity instanceof Animal)
                    && denied(level, player, entity.blockPosition(), ColonyPermissions.Action.HURT)) {
                deny(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            for (Colony colony : ColonyManager.get(server).colonies()) {
                colony.permissions().rememberName(player.getUUID(), player.getName().getString());
            }
        });
    }

    /** What right-clicking the block needs: opening a container, using it, or building. */
    public static ColonyPermissions.Action actionFor(Level level, Player player, ItemStack held, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof Container || state.getMenuProvider(level, pos) != null) {
            return player.isShiftKeyDown() && !held.isEmpty() ? ColonyPermissions.Action.BUILD : ColonyPermissions.Action.CONTAINERS;
        }
        Block block = state.getBlock();
        boolean usable = block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof BedBlock
                || block instanceof CraftingTableBlock || block instanceof BellBlock;
        if (usable && !(player.isShiftKeyDown() && !held.isEmpty())) {
            return ColonyPermissions.Action.USE_BLOCKS;
        }
        // Anything else done with an item in hand places or changes blocks.
        return held.isEmpty() ? ColonyPermissions.Action.USE_BLOCKS : ColonyPermissions.Action.BUILD;
    }

    private static boolean isOperator(Player player) {
        return player.isCreative() && player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    private static boolean denied(Level level, Player player, BlockPos pos, ColonyPermissions.Action action) {
        if (isOperator(player)) {
            return false;
        }
        Colony colony = ColonyManager.get(((ServerLevel) level).getServer()).colonyAt(level.dimension(), pos);
        return colony != null && !colony.permissions().allows(player, action);
    }

    private static void deny(Player player) {
        player.displayClientMessage(Component.translatable("message.hearthstead.protected").withStyle(ChatFormatting.RED), true);
    }
}
