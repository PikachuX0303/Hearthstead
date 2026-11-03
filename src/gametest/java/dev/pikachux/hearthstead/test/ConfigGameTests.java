package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.config.HearthsteadConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.List;

public class ConfigGameTests {
    /** The written file reads back to the same values without problems. */
    @GameTest
    public void configRoundTrips(GameTestHelper helper) {
        try {
            HearthsteadConfig.resetToDefaults();
            HearthsteadConfig.COLONY_RADIUS.set(100);
            HearthsteadConfig.RAID_CHANCE.set(0.35);
            HearthsteadConfig.AUTOMATIC_UPDATES.set("off");
            String text = HearthsteadConfig.write();

            List<String> problems = new ArrayList<>();
            boolean rewrite = HearthsteadConfig.read(text, problems);
            check(helper, problems.isEmpty() && !rewrite, "no problems, got " + problems);
            check(helper, HearthsteadConfig.COLONY_RADIUS.get() == 100, "colonyRadius 100");
            check(helper, HearthsteadConfig.RAID_CHANCE.get() == 0.35, "nightlyChance 0.35");
            check(helper, HearthsteadConfig.AUTOMATIC_UPDATES.is("off"), "automaticUpdates off");
            check(helper, text.contains("# Whole number from 32 to 256. Default: 64"), "a range description in the file");
        } finally {
            HearthsteadConfig.load();
        }
        helper.succeed();
    }

    /** The settings screen shows a name for every option and section. */
    @GameTest
    public void everyOptionHasAName(GameTestHelper helper) {
        com.google.gson.JsonObject lang;
        try (var in = ConfigGameTests.class.getResourceAsStream("/assets/hearthstead/lang/en_us.json")) {
            lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (java.io.IOException | NullPointerException e) {
            throw helper.assertionException("Could not read the language file: " + e);
        }
        List<String> missing = new ArrayList<>();
        for (HearthsteadConfig.Option<?> option : HearthsteadConfig.options()) {
            if (!lang.has("config.hearthstead." + option.path())) {
                missing.add(option.path());
            }
            if (option instanceof HearthsteadConfig.ChoiceOption choice) {
                choice.choices().stream().filter(c -> !lang.has("config.hearthstead.choice." + c)).forEach(missing::add);
            }
        }
        for (String section : HearthsteadConfig.sections().keySet()) {
            if (!lang.has("config.hearthstead.category." + section)) {
                missing.add("[" + section + "]");
            }
        }
        check(helper, missing.isEmpty(), "names for every option, missing " + missing);
        helper.succeed();
    }

    /** Languages shipped next to English. */
    private static final List<String> LANGUAGES = List.of(
            "de_de", "fr_fr", "es_es", "it_it", "pt_br", "ru_ru", "pl_pl", "nl_nl", "tr_tr", "zh_cn", "ja_jp", "ko_kr");
    private static final java.util.regex.Pattern ARGUMENT = java.util.regex.Pattern.compile("%(?:\\d+\\$)?s|%%");

    private static com.google.gson.JsonObject readLanguage(GameTestHelper helper, String code) {
        try (var in = ConfigGameTests.class.getResourceAsStream("/assets/hearthstead/lang/" + code + ".json")) {
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (java.io.IOException | NullPointerException | IllegalStateException e) {
            throw helper.assertionException("Could not read the language file " + code + ": " + e);
        }
    }

    private static List<String> arguments(String text) {
        List<String> found = new ArrayList<>();
        java.util.regex.Matcher matcher = ARGUMENT.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group().equals("%%") ? "%%" : "%s");
        }
        java.util.Collections.sort(found);
        return found;
    }

    /** Every translation has exactly the English keys, with the same number of arguments in each text. */
    @GameTest
    public void everyLanguageIsComplete(GameTestHelper helper) {
        com.google.gson.JsonObject english = readLanguage(helper, "en_us");
        for (dev.pikachux.hearthstead.colony.JobType job : dev.pikachux.hearthstead.colony.JobType.values()) {
            check(helper, english.has(job.translationKey()), "an English name for the job " + job);
        }
        for (dev.pikachux.hearthstead.colony.BuildingType type : dev.pikachux.hearthstead.colony.BuildingType.values()) {
            check(helper, english.has("block.hearthstead." + type.blockName()), "an English name for the hut " + type);
        }
        List<String> problems = new ArrayList<>();
        for (String code : LANGUAGES) {
            com.google.gson.JsonObject language = readLanguage(helper, code);
            for (String key : english.keySet()) {
                if (!language.has(key)) {
                    problems.add(code + " lacks " + key);
                } else if (!arguments(english.get(key).getAsString()).equals(arguments(language.get(key).getAsString()))) {
                    problems.add(code + " " + key + " has other arguments");
                }
            }
            language.keySet().stream().filter(key -> !english.has(key)).forEach(key -> problems.add(code + " has extra " + key));
        }
        check(helper, problems.isEmpty(), "complete translations, got " + problems);
        helper.succeed();
    }

    /** Out-of-range values are clamped, invalid ones get defaults, and every problem is reported. */
    @GameTest
    public void invalidConfigIsRepaired(GameTestHelper helper) {
        try {
            List<String> problems = new ArrayList<>();
            boolean rewrite = HearthsteadConfig.read("""
                    # a comment
                    [colonies]
                    colonyRadius = 9999   # far too large

                    [citizens]
                    hungerRate = lots
                    workSpeed = 2.5
                    oldOption = 3

                    [raids]
                    enabled = false
                    nightlyChance = -1
                    """, problems);
            check(helper, rewrite, "the file to be rewritten");
            check(helper, problems.size() == 4, "4 problems, got " + problems);
            check(helper, HearthsteadConfig.COLONY_RADIUS.get() == 256, "colonyRadius clamped to 256");
            check(helper, HearthsteadConfig.HUNGER_RATE.get() == 1.0, "hungerRate back to its default");
            check(helper, HearthsteadConfig.WORK_SPEED.get() == 2.5, "workSpeed 2.5");
            check(helper, !HearthsteadConfig.RAIDS_ENABLED.get(), "raids turned off");
            check(helper, HearthsteadConfig.RAID_CHANCE.get() == 0.0, "nightlyChance clamped to 0");
            check(helper, HearthsteadConfig.WELCOME_MESSAGE.get(), "missing options keep their defaults");
        } finally {
            HearthsteadConfig.load();
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
