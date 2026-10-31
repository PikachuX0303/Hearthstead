package dev.pikachux.hearthstead.event;

import dev.pikachux.hearthstead.Hearthstead;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;

/**
 * Greets players in chat when they join a world or server that runs the mod.
 */
public final class WelcomeMessage {
    private static final String REPOSITORY = "https://github.com/PikachuX0303/Hearthstead";
    private static final TextColor GOLD = TextColor.fromRgb(0xF5B942);
    private static final TextColor AMBER = TextColor.fromRgb(0xC9822B);
    private static final TextColor HEART = TextColor.fromRgb(0xFF4D6D);
    private static final TextColor AUTHOR = TextColor.fromRgb(0x5FD4FF);
    /** Struck-through spaces draw a solid divider line in chat. */
    private static final String RULE = " ".repeat(64);

    private WelcomeMessage() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
    }

    public static void send(ServerPlayer player) {
        String version = FabricLoader.getInstance().getModContainer(Hearthstead.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString().split("\\+")[0])
                .orElse("");

        player.sendSystemMessage(Component.literal(RULE).withStyle(style -> style.withColor(AMBER).withStrikethrough(true)));

        player.sendSystemMessage(Component.literal("  ⚒ ").withStyle(style -> style.withColor(AMBER))
                .append(Component.literal("Hearthstead").withStyle(style -> style.withColor(GOLD).withBold(true)))
                .append(Component.literal("  v" + version).withStyle(ChatFormatting.DARK_GRAY)));

        player.sendSystemMessage(Component.literal("  ")
                .append(Component.translatableWithFallback("chat.hearthstead.welcome.created_with", "Created with")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" ❤ ").withStyle(style -> style.withColor(HEART)))
                .append(Component.translatableWithFallback("chat.hearthstead.welcome.by", "by")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" PerniciousPx").withStyle(style -> style.withColor(AUTHOR).withBold(true)))
                .append(Component.literal(" & ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("FallenStar114").withStyle(style -> style.withColor(AUTHOR).withBold(true))));

        player.sendSystemMessage(Component.literal("  ")
                .append(link(Component.translatableWithFallback("chat.hearthstead.welcome.releases", "Releases"), REPOSITORY + "/releases"))
                .append(Component.literal("  "))
                .append(link(Component.translatableWithFallback("chat.hearthstead.welcome.changelog", "Changelog"),
                        REPOSITORY + "/blob/HEAD/CHANGELOG.md")));

        player.sendSystemMessage(Component.literal(RULE).withStyle(style -> style.withColor(AMBER).withStrikethrough(true)));
    }

    private static MutableComponent link(MutableComponent label, String url) {
        return Component.literal("[").append(label).append("]").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(url).withStyle(ChatFormatting.GRAY))));
    }
}
