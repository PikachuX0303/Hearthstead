package dev.pikachux.hearthstead.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.pikachux.hearthstead.Hearthstead;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Moves the automatic-updates answer from {@code config/hearthstead-updates.json} (0.18.0)
 * into {@code hearthstead.toml} and removes the old file.
 */
final class LegacyUpdateSettings {
    private LegacyUpdateSettings() {
    }

    /** Returns true when a value was carried over and the config file needs saving. */
    static boolean migrate() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("hearthstead-updates.json");
        if (!Files.isRegularFile(file)) {
            return false;
        }
        boolean migrated = false;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("automaticUpdates") && HearthsteadConfig.AUTOMATIC_UPDATES.is("ask")) {
                HearthsteadConfig.AUTOMATIC_UPDATES.set(json.get("automaticUpdates").getAsBoolean() ? "on" : "off");
                migrated = true;
            }
            Files.delete(file);
            Hearthstead.LOGGER.info("Moved the automatic updates setting into {}", HearthsteadConfig.FILE_NAME);
        } catch (IOException | RuntimeException e) {
            Hearthstead.LOGGER.warn("Could not migrate {}", file, e);
        }
        return migrated;
    }
}
