package dev.pikachux.hearthstead.client.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Opens the settings screen when Cloth Config is installed. Cloth Config is optional, so its
 * classes are only touched through {@link ClothConfigScreen} after checking that it is there.
 */
public final class ConfigScreens {
    private ConfigScreens() {
    }

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("cloth-config");
    }

    /** The settings screen, or null without Cloth Config. */
    @Nullable
    public static Screen create(@Nullable Screen parent) {
        return available() ? ClothConfigScreen.create(parent) : null;
    }
}
