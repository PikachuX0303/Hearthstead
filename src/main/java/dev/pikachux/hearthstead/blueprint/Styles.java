package dev.pikachux.hearthstead.blueprint;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Building styles. "classic" blueprints live at {@code hearthstead:huts/<hut>/level<N>};
 * every other style lives at {@code hearthstead:styles/<style>/<hut>/level<N>}, so a
 * data pack can add a style just by adding blueprints in a new folder.
 */
public final class Styles {
    public static final String CLASSIC = "classic";
    private static final String STYLE_FOLDER = "structure/styles/";

    private static List<String> cache;

    private Styles() {
    }

    public static void clearCache() {
        cache = null;
    }

    /** All styles found in the loaded data packs, classic first. */
    public static List<String> available(MinecraftServer server) {
        if (cache == null) {
            TreeSet<String> found = new TreeSet<>();
            server.getResourceManager().listResources("structure/styles", id -> id.getPath().endsWith(".nbt"))
                    .keySet().stream()
                    .filter(id -> id.getNamespace().equals("hearthstead"))
                    .map(Identifier::getPath)
                    .map(path -> path.substring(STYLE_FOLDER.length()))
                    .map(path -> path.substring(0, path.indexOf('/')))
                    .forEach(found::add);
            List<String> styles = new ArrayList<>();
            styles.add(CLASSIC);
            found.remove(CLASSIC);
            styles.addAll(found);
            cache = List.copyOf(styles);
        }
        return cache;
    }

    public static boolean isValid(MinecraftServer server, String style) {
        return available(server).contains(style);
    }

    /** The style after the given one, wrapping around. */
    public static String next(List<String> styles, String style, int direction) {
        int index = styles.indexOf(style);
        return styles.get(Math.floorMod(index + direction, styles.size()));
    }

    public static Component displayName(String style) {
        String fallback = style.isEmpty() ? style : style.substring(0, 1).toUpperCase(Locale.ROOT) + style.substring(1).replace('_', ' ');
        return Component.translatableWithFallback("style.hearthstead." + style, fallback);
    }
}
