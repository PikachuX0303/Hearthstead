package dev.pikachux.hearthstead.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.pikachux.hearthstead.update.Updater;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Optional;

public class UpdaterGameTests {
    /** Release versions are ordered by number first, and a final release beats its pre-release. */
    @GameTest
    public void versionsCompare(GameTestHelper helper) {
        check(helper, Updater.compare("0.18.0-alpha", "0.17.1-alpha") > 0, "0.18.0-alpha > 0.17.1-alpha");
        check(helper, Updater.compare("0.10.0-alpha", "0.9.0-alpha") > 0, "0.10.0-alpha > 0.9.0-alpha");
        check(helper, Updater.compare("0.17.1-alpha", "0.17.1-alpha") == 0, "equal versions");
        check(helper, Updater.compare("1.0.0", "1.0.0-alpha") > 0, "1.0.0 > 1.0.0-alpha");
        check(helper, Updater.compare("1.0.0-beta", "1.0.0-alpha") > 0, "1.0.0-beta > 1.0.0-alpha");
        helper.succeed();
    }

    /**
     * Picks the newest release with a jar for the installed Minecraft version, and skips drafts,
     * older releases and jars for other Minecraft versions.
     */
    @GameTest
    public void newestReleaseIsChosen(GameTestHelper helper) {
        JsonArray releases = JsonParser.parseString("""
                [
                  {"tag_name": "v0.20.0-alpha", "draft": true, "assets": [
                    {"name": "hearthstead-0.20.0-alpha+1.21.11.jar", "browser_download_url": "https://example.invalid/20.jar", "size": 1}]},
                  {"tag_name": "v0.19.0-alpha", "draft": false, "assets": [
                    {"name": "hearthstead-0.19.0-alpha+1.22.jar", "browser_download_url": "https://example.invalid/19.jar", "size": 1}]},
                  {"tag_name": "v0.18.0-alpha", "draft": false, "prerelease": true, "assets": [
                    {"name": "hearthstead-0.18.0-alpha+1.21.11.jar", "browser_download_url": "https://example.invalid/18.jar",
                     "size": 42, "digest": "sha256:abc123"}]},
                  {"tag_name": "v0.17.1-alpha", "draft": false, "assets": [
                    {"name": "hearthstead-0.17.1-alpha+1.21.11.jar", "browser_download_url": "https://example.invalid/17.jar", "size": 1}]}
                ]
                """).getAsJsonArray();

        Optional<Updater.Release> update = Updater.newest(releases, "0.17.1-alpha+1.21.11");
        check(helper, update.isPresent(), "an update to 0.18.0-alpha");
        Updater.Release release = update.get();
        check(helper, release.version().equals("0.18.0-alpha"), "version 0.18.0-alpha, got " + release.version());
        check(helper, release.assetName().equals("hearthstead-0.18.0-alpha+1.21.11.jar"), "the 1.21.11 jar, got " + release.assetName());
        check(helper, release.size() == 42 && "abc123".equals(release.sha256()), "size and checksum of the asset");

        check(helper, Updater.newest(releases, "0.18.0-alpha+1.21.11").isEmpty(), "no update when already on 0.18.0-alpha");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
