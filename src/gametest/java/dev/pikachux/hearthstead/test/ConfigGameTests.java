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
