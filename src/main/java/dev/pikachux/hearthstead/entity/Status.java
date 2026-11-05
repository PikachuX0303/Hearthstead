package dev.pikachux.hearthstead.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

/**
 * Citizen status lines. A status is a translatable component ({@code status.hearthstead.<key>}),
 * so every player sees it in their own language.
 */
public final class Status {
    public static final String PREFIX = "status.hearthstead.";
    public static final Component IDLE = of("idle");

    private Status() {
    }

    public static Component of(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    /** The status key ({@code "patrolling"}) of a status made by {@link #of}, or null. */
    @Nullable
    public static String key(Component status) {
        if (status.getContents() instanceof TranslatableContents translatable && translatable.getKey().startsWith(PREFIX)) {
            return translatable.getKey().substring(PREFIX.length());
        }
        return null;
    }

    public static boolean is(Component status, String key) {
        return key.equals(key(status));
    }
}
