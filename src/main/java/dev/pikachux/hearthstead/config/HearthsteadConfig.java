package dev.pikachux.hearthstead.config;

import dev.pikachux.hearthstead.Hearthstead;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The mod's settings, kept in {@code config/hearthstead.toml}: a commented file with one section
 * per topic. Missing or invalid entries fall back to their defaults and the file is rewritten
 * with every option and its description, so new options appear after an update.
 *
 * <p>Gameplay options are read by the server (the host in singleplayer); {@link #AUTOMATIC_UPDATES}
 * is read by the client.
 */
public final class HearthsteadConfig {
    public static final String FILE_NAME = "hearthstead.toml";

    private static final List<Option<?>> OPTIONS = new ArrayList<>();
    private static final Map<String, String> SECTIONS = new LinkedHashMap<>();

    // ---------------------------------------------------------------- general

    private static final String GENERAL = section("general", "General settings.");

    public static final ChoiceOption AUTOMATIC_UPDATES = add(new ChoiceOption(GENERAL, "automaticUpdates",
            "Look for new Hearthstead releases on GitHub when the game starts and install them when it closes.\n"
                    + "\"ask\" asks on the title screen, \"on\" updates, \"off\" never checks. Client only.",
            "ask", "ask", "on", "off"));

    public static final BoolOption WELCOME_MESSAGE = add(new BoolOption(GENERAL, "welcomeMessage",
            "Greet players in chat with the mod version and links when they join.", true));

    // ---------------------------------------------------------------- colonies

    private static final String COLONIES = section("colonies", "Colony size and placement.");

    public static final IntOption COLONY_RADIUS = add(new IntOption(COLONIES, "colonyRadius",
            "Blocks from the town hall to the edge of a colony (in every direction). Town halls must be at\n"
                    + "least twice this distance plus 16 blocks apart. Raiders spawn near the edge.",
            64, 32, 256));

    // ---------------------------------------------------------------- citizens

    private static final String CITIZENS = section("citizens", "Citizens and their work.");

    public static final IntOption NEW_CITIZEN_INTERVAL = add(new IntOption(CITIZENS, "newCitizenInterval",
            "Seconds between new settlers arriving while the colony has free beds.", 60, 5, 3600));

    public static final IntOption RESPAWN_DELAY = add(new IntOption(CITIZENS, "respawnDelay",
            "Seconds before a citizen who died or went missing comes back at the town hall.", 30, 5, 3600));

    public static final DoubleOption HUNGER_RATE = add(new DoubleOption(CITIZENS, "hungerRate",
            "How fast citizens get hungry. 1.0 is normal, 0.5 half as fast, 0 never.", 1.0, 0.0, 10.0));

    public static final DoubleOption WORK_SPEED = add(new DoubleOption(CITIZENS, "workSpeed",
            "How fast citizens work (building, crafting, farming, ...). 1.0 is normal, 2.0 twice as fast.",
            1.0, 0.1, 10.0));

    public static final DoubleOption RESEARCH_SPEED = add(new DoubleOption(CITIZENS, "researchSpeed",
            "How fast researchers make progress at the university. 1.0 is normal.", 1.0, 0.1, 100.0));

    // ---------------------------------------------------------------- raids

    private static final String RAIDS = section("raids", "Barbarian raids. Colony owners can also turn raids off for their\n"
            + "own colony with /hearthstead raids off.");

    public static final BoolOption RAIDS_ENABLED = add(new BoolOption(RAIDS, "enabled",
            "Whether colonies can be raided at night at all. Raids never happen on Peaceful.", true));

    public static final IntOption RAID_MIN_CITIZENS = add(new IntOption(RAIDS, "minCitizens",
            "Citizens a colony needs before it attracts raids.", 4, 1, 100));

    public static final IntOption RAID_MAX_RAIDERS = add(new IntOption(RAIDS, "maxRaiders",
            "Largest number of raiders in one raid. Raids grow with the colony: 2 raiders plus one\n"
                    + "for every two citizens, up to this limit.", 16, 1, 64));

    public static final DoubleOption RAID_CHANCE = add(new DoubleOption(RAIDS, "nightlyChance",
            "Chance of a raid at nightfall, from 0.0 (never) to 1.0 (every night).", 0.2, 0.0, 1.0));

    public static final DoubleOption RAID_CHANCE_INCREASE = add(new DoubleOption(RAIDS, "chanceIncreasePerQuietNight",
            "Added to the chance for every night without a raid, so raids do not stay away for long.",
            0.15, 0.0, 1.0));

    public static final BoolOption PIRATES = add(new BoolOption(RAIDS, "pirates",
            "Whether pirates raid colonies that have water nearby. They land on the shore with their boats.", true));

    public static final DoubleOption PIRATE_CHANCE = add(new DoubleOption(RAIDS, "pirateChance",
            "For a colony near water, the chance that a raid is by pirates instead of barbarians.", 0.5, 0.0, 1.0));

    // ---------------------------------------------------------------- colony events

    private static final String EVENTS = section("events", "Things that happen in colonies now and then. Rolled each morning\n"
            + "for colonies with at least three citizens.");

    public static final DoubleOption MERCHANT_CHANCE = add(new DoubleOption(EVENTS, "wanderingMerchantChance",
            "Chance each morning that a wandering merchant visits the town hall for the day.", 0.1, 0.0, 1.0));

    public static final IntOption FESTIVAL_INTERVAL = add(new IntOption(EVENTS, "festivalInterval",
            "Days between harvest festivals, when citizens are happier until nightfall and fireworks go up\n"
                    + "over the town hall. 0 turns festivals off.", 7, 0, 100));

    public static final DoubleOption BIRTH_CHANCE = add(new DoubleOption(EVENTS, "birthChance",
            "Chance each morning that a child is born in a residence where two adults live (needs a free bed).",
            0.25, 0.0, 1.0));

    public static final DoubleOption VISITOR_CHANCE = add(new DoubleOption(EVENTS, "visitorChance",
            "Chance each morning that a visitor arrives at a tavern with a free room (one room per tavern level).",
            0.5, 0.0, 1.0));

    // ---------------------------------------------------------------- world generation

    private static final String WORLDGEN = section("worldgen", "Structures generated in new parts of the world.");

    public static final BoolOption SETTLEMENTS = add(new BoolOption(WORLDGEN, "abandonedSettlements",
            "Generate abandoned settlements: a ruined camp with an unclaimed town hall (right-click it to\n"
                    + "found your colony there) and a chest of supplies.", true));

    public static final DoubleOption SETTLEMENT_CHANCE = add(new DoubleOption(WORLDGEN, "settlementChance",
            "How likely a possible spot (one every 32 chunks or so) gets a settlement, from 0.0 to 1.0.\n"
                    + "0.35 is about as rare as a pillager outpost.", 0.35, 0.0, 1.0));

    private HearthsteadConfig() {
    }

    private static String section(String name, String comment) {
        SECTIONS.put(name, comment);
        return name;
    }

    private static <T extends Option<?>> T add(T option) {
        OPTIONS.add(option);
        return option;
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    // ---------------------------------------------------------------- loading and saving

    /**
     * Reads the config file, creating or completing it when needed.
     * Returns the problems found (empty when the file was fine).
     */
    public static synchronized List<String> load() {
        Path file = file();
        List<String> problems = new ArrayList<>();
        boolean rewrite;
        if (Files.isRegularFile(file)) {
            try {
                rewrite = read(Files.readString(file, StandardCharsets.UTF_8), problems);
            } catch (IOException e) {
                problems.add("Could not read the file: " + e.getMessage());
                resetToDefaults();
                rewrite = false;
            }
        } else {
            resetToDefaults();
            rewrite = true;
        }
        rewrite |= LegacyUpdateSettings.migrate();
        for (String problem : problems) {
            Hearthstead.LOGGER.warn("{}: {}", FILE_NAME, problem);
        }
        if (rewrite) {
            save();
        }
        return problems;
    }

    /** Writes the current values, with all descriptions, to the config file. */
    public static synchronized void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temp, write(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Hearthstead.LOGGER.warn("Could not write {}", file, e);
        }
    }

    public static synchronized void resetToDefaults() {
        for (Option<?> option : OPTIONS) {
            option.reset();
        }
    }

    /**
     * Applies the settings in {@code text}. Options that are missing or invalid get their defaults.
     * Returns true when the file should be rewritten (something was missing, unknown or invalid).
     */
    public static synchronized boolean read(String text, List<String> problems) {
        resetToDefaults();
        Map<String, Option<?>> byKey = new LinkedHashMap<>();
        for (Option<?> option : OPTIONS) {
            byKey.put(option.path(), option);
        }
        List<Option<?>> seen = new ArrayList<>();
        String section = "";
        boolean rewrite = false;
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).strip();
            if (line.isEmpty()) {
                continue;
            }
            int number = i + 1;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).strip();
                if (!SECTIONS.containsKey(section)) {
                    problems.add("Line " + number + ": unknown section [" + section + "]");
                    rewrite = true;
                }
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                problems.add("Line " + number + ": expected 'name = value' but found '" + line + "'");
                rewrite = true;
                continue;
            }
            String key = line.substring(0, equals).strip();
            String value = line.substring(equals + 1).strip();
            Option<?> option = byKey.get(section + "." + key);
            if (option == null) {
                problems.add("Line " + number + ": unknown option '" + key + "'" + (section.isEmpty() ? "" : " in [" + section + "]"));
                rewrite = true;
                continue;
            }
            String problem = option.parse(value);
            if (problem != null) {
                problems.add("Line " + number + ": " + key + " " + problem);
                rewrite = true;
            }
            seen.add(option);
        }
        if (seen.size() < OPTIONS.size()) {
            rewrite = true;
        }
        return rewrite;
    }

    /** The file contents for the current values. */
    public static synchronized String write() {
        StringBuilder out = new StringBuilder();
        out.append("# Hearthstead configuration\n");
        out.append("#\n");
        out.append("# Edit the values after the '='. Lines starting with '#' are comments.\n");
        out.append("# Gameplay settings are read by the server (or the host in singleplayer) when it starts,\n");
        out.append("# or right away with '/hearthstead config reload'. Invalid values are replaced by their\n");
        out.append("# defaults, and options added in new versions are filled in automatically.\n");
        for (Map.Entry<String, String> section : SECTIONS.entrySet()) {
            out.append("\n").append(comment(section.getValue(), "")).append("[").append(section.getKey()).append("]\n");
            for (Option<?> option : OPTIONS) {
                if (!option.section.equals(section.getKey())) {
                    continue;
                }
                out.append("\n").append(comment(option.comment, "")).append("# ").append(option.describeRange()).append("\n");
                out.append(option.key).append(" = ").append(option.format()).append("\n");
            }
        }
        return out.toString();
    }

    private static String comment(String text, String indent) {
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            out.append(indent).append("# ").append(line).append("\n");
        }
        return out.toString();
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    // ---------------------------------------------------------------- option types

    /** One setting: where it lives in the file, its description, default and current value. */
    public abstract static sealed class Option<T> permits BoolOption, IntOption, DoubleOption, ChoiceOption {
        final String section;
        final String key;
        final String comment;
        final T defaultValue;
        volatile T value;

        Option(String section, String key, String comment, T defaultValue) {
            this.section = section;
            this.key = key;
            this.comment = comment;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        /** Sets the value in memory; call {@link HearthsteadConfig#save()} to keep it. */
        public void set(T value) {
            this.value = value;
        }

        public String path() {
            return section + "." + key;
        }

        void reset() {
            value = defaultValue;
        }

        /** Parses and applies a value; returns a problem description, or null when it was valid. */
        abstract String parse(String text);

        abstract String format();

        abstract String describeRange();
    }

    public static final class BoolOption extends Option<Boolean> {
        BoolOption(String section, String key, String comment, boolean defaultValue) {
            super(section, key, comment, defaultValue);
        }

        @Override
        String parse(String text) {
            switch (text.toLowerCase(Locale.ROOT)) {
                case "true" -> value = true;
                case "false" -> value = false;
                default -> {
                    return "must be true or false (was " + text + "); using " + defaultValue;
                }
            }
            return null;
        }

        @Override
        String format() {
            return value.toString();
        }

        @Override
        String describeRange() {
            return "true or false. Default: " + defaultValue;
        }
    }

    public static final class IntOption extends Option<Integer> {
        private final int min;
        private final int max;

        IntOption(String section, String key, String comment, int defaultValue, int min, int max) {
            super(section, key, comment, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        String parse(String text) {
            int parsed;
            try {
                parsed = Integer.parseInt(text.replace("_", ""));
            } catch (NumberFormatException e) {
                return "must be a whole number (was " + text + "); using " + defaultValue;
            }
            value = Math.clamp(parsed, min, max);
            return parsed < min || parsed > max ? "must be between " + min + " and " + max + " (was " + text + "); using " + value : null;
        }

        @Override
        String format() {
            return value.toString();
        }

        @Override
        String describeRange() {
            return "Whole number from " + min + " to " + max + ". Default: " + defaultValue;
        }
    }

    public static final class DoubleOption extends Option<Double> {
        private final double min;
        private final double max;

        DoubleOption(String section, String key, String comment, double defaultValue, double min, double max) {
            super(section, key, comment, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        String parse(String text) {
            double parsed;
            try {
                parsed = Double.parseDouble(text.replace("_", ""));
            } catch (NumberFormatException e) {
                return "must be a number (was " + text + "); using " + format(defaultValue);
            }
            if (!Double.isFinite(parsed)) {
                return "must be a number (was " + text + "); using " + format(defaultValue);
            }
            value = Math.clamp(parsed, min, max);
            return parsed < min || parsed > max
                    ? "must be between " + format(min) + " and " + format(max) + " (was " + text + "); using " + format(value) : null;
        }

        @Override
        String format() {
            return format(value);
        }

        private static String format(double number) {
            return Double.toString(number);
        }

        @Override
        String describeRange() {
            return "Number from " + format(min) + " to " + format(max) + ". Default: " + format(defaultValue);
        }
    }

    public static final class ChoiceOption extends Option<String> {
        private final List<String> choices;

        ChoiceOption(String section, String key, String comment, String defaultValue, String... choices) {
            super(section, key, comment, defaultValue);
            this.choices = List.of(choices);
        }

        public boolean is(String choice) {
            return value.equals(choice);
        }

        @Override
        String parse(String text) {
            String unquoted = text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")
                    ? text.substring(1, text.length() - 1) : text;
            String choice = unquoted.toLowerCase(Locale.ROOT);
            if (!choices.contains(choice)) {
                return "must be one of " + describeChoices() + " (was " + text + "); using \"" + defaultValue + "\"";
            }
            value = choice;
            return null;
        }

        @Override
        String format() {
            return "\"" + value + "\"";
        }

        private String describeChoices() {
            return String.join(", ", choices.stream().map(choice -> "\"" + choice + "\"").toList());
        }

        @Override
        String describeRange() {
            return "One of " + describeChoices() + ". Default: \"" + defaultValue + "\"";
        }
    }
}
