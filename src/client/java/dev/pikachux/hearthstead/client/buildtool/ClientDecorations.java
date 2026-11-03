package dev.pikachux.hearthstead.client.buildtool;

import dev.pikachux.hearthstead.colony.Decoration;
import dev.pikachux.hearthstead.network.DecorationsPayload;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The decorations the server offers in the build tool, as last sent by the server.
 */
public final class ClientDecorations {
    private static List<DecorationsPayload.Entry> decorations = List.of();

    private ClientDecorations() {
    }

    public static List<DecorationsPayload.Entry> all() {
        return decorations;
    }

    public static void set(List<DecorationsPayload.Entry> entries) {
        decorations = List.copyOf(entries);
    }

    public static Component displayName(String name) {
        return Decoration.nameComponent(name);
    }
}
