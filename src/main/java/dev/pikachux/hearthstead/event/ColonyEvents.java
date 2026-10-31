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
            if (level instanceof ServerLevel && isProtected(level, player, pos)) {
                deny(player);
                return false;
            }
            if (state.getBlock() instanceof HutBlock && level instanceof ServerLevel serverLevel) {
                Colony colony = ColonyManager.get(serverLevel.getServer()).colonyAt(level.dimension(), pos);
                return colony == null || colony.isMember(player);
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() && player.getItemInHand(hand).is(ModItems.BUILD_TOOL)) {
                // The build tool works on the client; the server only blocks normal block use.
                return InteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel && !player.isSpectator() && isProtected(level, player, hit.getBlockPos())
                    && !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof HutBlock)) {
                deny(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean isProtected(Level level, Player player, BlockPos pos) {
        if (player.isCreative() && player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
            return false;
        }
        Colony colony = ColonyManager.get(((ServerLevel) level).getServer()).colonyAt(level.dimension(), pos);
        return colony != null && !colony.isMember(player);
    }

    private static void deny(Player player) {
        player.displayClientMessage(Component.translatable("message.hearthstead.protected").withStyle(ChatFormatting.RED), true);
    }
}
