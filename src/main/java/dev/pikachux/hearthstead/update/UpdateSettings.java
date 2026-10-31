package dev.pikachux.hearthstead.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.pikachux.hearthstead.Hearthstead;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Whether the player agreed to automatic updates, stored in {@code config/hearthstead-updates.json}.
 * Empty until the player has answered the prompt.
 */
public final class UpdateSettings {
    private static final String KEY = "automaticUpdates";

    private UpdateSettings() {
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("hearthstead-updates.json");
    }

    public static Optional<Boolean> automaticUpdates() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.has(KEY) ? Optional.of(json.get(KEY).getAsBoolean()) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            Hearthstead.LOGGER.warn("Could not read {}", file, e);
            return Optional.empty();
        }
    }

    public static void setAutomaticUpdates(boolean enabled) {
        Path file = file();
        JsonObject json = new JsonObject();
        json.addProperty(KEY, enabled);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Hearthstead.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
