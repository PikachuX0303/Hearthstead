package dev.pikachux.hearthstead.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.pikachux.hearthstead.Hearthstead;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Looks for a newer Hearthstead release on GitHub, downloads its jar next to the installed one
 * and swaps the jars when the game closes.
 *
 * <p>The download is saved as {@code <name>.jar.part}, which Fabric does not load, and only gets
 * its {@code .jar} name once the old jar is gone, so the mods folder never holds two copies.
 */
public final class Updater {
    public static final String REPOSITORY = "PikachuX0303/Hearthstead";
    private static final URI RELEASES = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases?per_page=20");
    private static final String PART = ".part";

    /** A release jar for this Minecraft version. */
    public record Release(String version, String assetName, URI download, long size, @Nullable String sha256) {
    }

    private record Pending(Path oldJar, Path part, Path target) {
    }

    private static volatile @Nullable Pending pending;

    private Updater() {
    }

    /** The jar the mod was loaded from, or empty in a development environment or when it is nested. */
    public static Optional<Path> installedJar() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return Optional.empty();
        }
        return FabricLoader.getInstance().getModContainer(Hearthstead.MOD_ID)
                .map(ModContainer::getOrigin)
                .filter(origin -> origin.getKind() == ModOrigin.Kind.PATH && origin.getPaths().size() == 1)
                .map(origin -> origin.getPaths().getFirst())
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"));
    }

    /** The installed version, for example {@code 0.17.1-alpha+1.21.11}. */
    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer(Hearthstead.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    /**
     * Checks GitHub and downloads a newer release, off the calling thread.
     * Completes with the downloaded version, or empty when there is nothing to install.
     */
    public static CompletableFuture<Optional<String>> checkAndDownload() {
        Optional<Path> jar = installedJar();
        if (jar.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return download(jar.get());
            } catch (IOException e) {
                Hearthstead.LOGGER.warn("Could not check for Hearthstead updates: {}", e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                Hearthstead.LOGGER.warn("Could not check for Hearthstead updates", e);
            }
            return Optional.empty();
        });
    }

    private static Optional<String> download(Path oldJar) throws IOException, InterruptedException {
        Path folder = oldJar.getParent();
        deleteLeftoverDownloads(folder);
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> response = http.send(request(RELEASES)
                .header("Accept", "application/vnd.github+json")
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("GitHub answered " + response.statusCode());
        }
        Optional<Release> newest = newest(JsonParser.parseString(response.body()).getAsJsonArray(), currentVersion());
        if (newest.isEmpty()) {
            Hearthstead.LOGGER.info("Hearthstead is up to date ({})", currentVersion());
            return Optional.empty();
        }
        Release release = newest.get();
        Path target = folder.resolve(release.assetName());
        if (Files.exists(target)) {
            return Optional.empty();
        }
        Path part = folder.resolve(release.assetName() + PART);
        Hearthstead.LOGGER.info("Downloading Hearthstead {} from {}", release.version(), release.download());
        HttpResponse<Path> file = http.send(request(release.download()).build(), HttpResponse.BodyHandlers.ofFile(part));
        if (file.statusCode() != 200 || !matches(part, release)) {
            Files.deleteIfExists(part);
            throw new IOException("The download of " + release.assetName() + " was incomplete or damaged");
        }
        pending = new Pending(oldJar, part, target);
        return Optional.of(release.version());
    }

    private static HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "Hearthstead/" + currentVersion());
    }

    private static boolean matches(Path file, Release release) throws IOException {
        if (release.size() > 0 && Files.size(file) != release.size()) {
            return false;
        }
        if (release.sha256() == null) {
            return true;
        }
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int read; (read = in.read(buffer)) > 0; ) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(release.sha256());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Removes downloads from an earlier session that never got installed. */
    private static void deleteLeftoverDownloads(Path folder) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, Hearthstead.MOD_ID + "-*.jar" + PART)) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        }
    }

    /**
     * The newest release in a GitHub releases list that is newer than {@code current} and has a jar
     * for the same Minecraft version (the part after {@code +}).
     */
    public static Optional<Release> newest(JsonArray releases, String current) {
        int plus = current.indexOf('+');
        String installed = plus < 0 ? current : current.substring(0, plus);
        String minecraft = plus < 0 ? "" : current.substring(plus);
        Release best = null;
        for (JsonElement element : releases) {
            JsonObject release = element.getAsJsonObject();
            if (bool(release, "draft") || !release.has("tag_name")) {
                continue;
            }
            String tag = release.get("tag_name").getAsString();
            String version = tag.startsWith("v") ? tag.substring(1) : tag;
            if (compare(version, installed) <= 0 || (best != null && compare(version, best.version()) <= 0)) {
                continue;
            }
            String wanted = Hearthstead.MOD_ID + "-" + version + minecraft + ".jar";
            for (JsonElement assetElement : release.getAsJsonArray("assets")) {
                JsonObject asset = assetElement.getAsJsonObject();
                if (!asset.get("name").getAsString().equals(wanted)) {
                    continue;
                }
                String digest = asset.has("digest") && !asset.get("digest").isJsonNull() ? asset.get("digest").getAsString() : null;
                String sha256 = digest != null && digest.startsWith("sha256:") ? digest.substring("sha256:".length()) : null;
                best = new Release(version, wanted, URI.create(asset.get("browser_download_url").getAsString()),
                        asset.has("size") ? asset.get("size").getAsLong() : -1, sha256);
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
    }

    /**
     * Compares versions like {@code 0.18.0-alpha}: numbers first, then a version without a
     * pre-release label is newer than one with it, then the labels alphabetically.
     */
    public static int compare(String a, String b) {
        String[] aParts = a.split("-", 2);
        String[] bParts = b.split("-", 2);
        List<Integer> aNumbers = numbers(aParts[0]);
        List<Integer> bNumbers = numbers(bParts[0]);
        for (int i = 0; i < Math.max(aNumbers.size(), bNumbers.size()); i++) {
            int x = i < aNumbers.size() ? aNumbers.get(i) : 0;
            int y = i < bNumbers.size() ? bNumbers.get(i) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        String aLabel = aParts.length > 1 ? aParts[1] : null;
        String bLabel = bParts.length > 1 ? bParts[1] : null;
        if (aLabel == null || bLabel == null) {
            return aLabel == null ? (bLabel == null ? 0 : 1) : -1;
        }
        return aLabel.compareTo(bLabel);
    }

    private static List<Integer> numbers(String version) {
        List<Integer> numbers = new ArrayList<>();
        for (String part : version.split("\\.")) {
            try {
                numbers.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                numbers.add(0);
            }
        }
        return numbers;
    }

    /** Whether a downloaded update waits to be installed when the game closes. */
    public static boolean hasPendingUpdate() {
        return pending != null;
    }

    /**
     * Replaces the installed jar with the downloaded one. Call while the game shuts down. Where the
     * running jar is locked (Windows), a hidden helper finishes the swap after the game has exited.
     */
    public static void installPending() {
        Pending update = pending;
        pending = null;
        if (update == null || !Files.isRegularFile(update.part())) {
            return;
        }
        // Move the new jar in first and only then remove the old one, so a failure never leaves
        // the mods folder without Hearthstead.
        try {
            Files.move(update.part(), update.target(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Hearthstead.LOGGER.warn("Could not install {}", update.target().getFileName(), e);
            return;
        }
        try {
            Files.delete(update.oldJar());
            Hearthstead.LOGGER.info("Installed {}", update.target().getFileName());
            return;
        } catch (IOException e) {
            // The running jar is locked: take the new one out again so the game never sees two copies.
            try {
                Files.move(update.target(), update.part(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException undo) {
                Hearthstead.LOGGER.warn("Could not undo the update; removing {}", update.target(), undo);
                try {
                    Files.deleteIfExists(update.target());
                } catch (IOException ignored) {
                    // Nothing more we can do; Fabric will report the duplicate.
                }
                return;
            }
        }
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            Hearthstead.LOGGER.warn("Could not replace {}; the update is skipped", update.oldJar());
            return;
        }
        String script = "$old=" + quote(update.oldJar()) + ";$part=" + quote(update.part()) + ";$new=" + quote(update.target()) + ";"
                + "for($i=0;$i -lt 120 -and (Test-Path -LiteralPath $old);$i++){"
                + "try{Remove-Item -LiteralPath $old -Force -ErrorAction Stop}catch{Start-Sleep -Milliseconds 500}};"
                + "if(Test-Path -LiteralPath $old){Remove-Item -LiteralPath $part -Force}"
                + "else{Move-Item -LiteralPath $part -Destination $new -Force}";
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                    .start();
            Hearthstead.LOGGER.info("{} is installed once the game has closed", update.target().getFileName());
        } catch (IOException e) {
            Hearthstead.LOGGER.warn("Could not start the update helper", e);
        }
    }

    private static String quote(Path path) {
        return "'" + path.toAbsolutePath().toString().replace("'", "''") + "'";
    }
}
