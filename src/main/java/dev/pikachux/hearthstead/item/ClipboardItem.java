package dev.pikachux.hearthstead.item;

import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.network.BuildingViews;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The clipboard: shows the open requests of the player's colony from anywhere, and lets the
 * player hand items in for them.
 */
public class ClipboardItem extends Item {
    public ClipboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Colony colony = ColonyManager.get(serverPlayer.level().getServer()).colonyOf(serverPlayer);
            if (colony == null) {
                serverPlayer.displayClientMessage(Component.translatable("message.hearthstead.no_colony")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            BuildingViews.sendRequests(serverPlayer, colony, false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.hearthstead.clipboard.desc").withStyle(ChatFormatting.GRAY));
    }
}
