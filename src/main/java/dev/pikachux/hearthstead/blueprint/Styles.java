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
    private static final String COMPACT_STYLE_FOLDER = "blueprints/styles/";
    /** Styles of earlier versions and the imported style that replaces each. */
    private static final java.util.Map<String, String> RENAMED = java.util.Map.of(
            "medieval", "medievaloak", "desert", "sandstone", "timber", "medievaldarkoak");

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
            for (String folder : List.of(STYLE_FOLDER, COMPACT_STYLE_FOLDER)) {
                String extension = folder.equals(STYLE_FOLDER) ? ".nbt" : ".blueprint";
                server.getResourceManager().listResources(folder.substring(0, folder.length() - 1),
                                id -> id.getPath().endsWith(extension))
                        .keySet().stream()
                        .filter(id -> id.getNamespace().equals("hearthstead"))
                        .map(Identifier::getPath)
                        .map(path -> path.substring(folder.length()))
                        .filter(path -> path.indexOf('/') > 0)
                        .map(path -> path.substring(0, path.indexOf('/')))
                        // A style is a folder of hut blueprints; decorations alone do not make one.
                        .forEach(found::add);
            }
            found.removeIf(style -> !hasHuts(server, style));
            List<String> styles = new ArrayList<>();
            styles.add(CLASSIC);
            found.remove(CLASSIC);
            styles.addAll(found);
            cache = List.copyOf(styles);
        }
        return cache;
    }

    /** A style name from an older save, mapped to the style that replaced it. */
    public static String migrate(String style) {
        return RENAMED.getOrDefault(style, style);
    }

    private static boolean hasHuts(MinecraftServer server, String style) {
        var manager = server.getResourceManager();
        return !manager.listResources("structure/styles/" + style,
                        id -> id.getPath().endsWith(".nbt") && !id.getPath().contains("/decorations/")).isEmpty()
                || !manager.listResources("blueprints/styles/" + style,
                        id -> id.getPath().endsWith(".blueprint")).isEmpty();
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
