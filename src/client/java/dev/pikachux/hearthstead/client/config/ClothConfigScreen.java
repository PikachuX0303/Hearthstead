package dev.pikachux.hearthstead.client.config;

import dev.pikachux.hearthstead.config.HearthsteadConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings screen, built with Cloth Config from the options in {@link HearthsteadConfig}: one
 * tab per section of {@code config/hearthstead.toml}, each option with its description as tooltip.
 * Saving writes the file and applies the values right away.
 */
public final class ClothConfigScreen {
    /** Whole-number options with at most this many values get a slider, the others a text field. */
    private static final int MAX_SLIDER_RANGE = 300;

    private ClothConfigScreen() {
    }

    public static Screen create(@Nullable Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.hearthstead.title"))
                .setSavingRunnable(HearthsteadConfig::save);
        ConfigEntryBuilder entries = builder.entryBuilder();
        for (Map.Entry<String, String> section : HearthsteadConfig.sections().entrySet()) {
            // Every section has a translated name (checked by a game test).
            ConfigCategory category = builder.getOrCreateCategory(Component.translatable(
                    "config.hearthstead.category." + section.getKey()));
            category.addEntry(entries.startTextDescription(Component.literal(section.getValue().replace('\n', ' '))
                    .withStyle(ChatFormatting.GRAY)).build());
            if (section.getKey().equals("general")) {
                category.addEntry(entries.startTextDescription(Component.translatable("config.hearthstead.where")
                        .withStyle(ChatFormatting.YELLOW)).build());
            }
            for (HearthsteadConfig.Option<?> option : HearthsteadConfig.options()) {
                if (option.section().equals(section.getKey())) {
                    category.addEntry(entry(entries, option));
                }
            }
        }
        return builder.build();
    }

    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> entry(ConfigEntryBuilder entries,
                                                                                   HearthsteadConfig.Option<?> option) {
        Component name = Component.translatableWithFallback("config.hearthstead." + option.path(), capitalize(option.key()));
        Component[] tooltip = tooltip(option);
        return switch (option) {
            case HearthsteadConfig.BoolOption bool -> entries.startBooleanToggle(name, bool.get())
                    .setDefaultValue(bool.defaultValue())
                    .setTooltip(tooltip)
                    .setSaveConsumer(bool::set)
                    .build();
            case HearthsteadConfig.IntOption number when number.max() - number.min() <= MAX_SLIDER_RANGE ->
                    entries.startIntSlider(name, number.get(), number.min(), number.max())
                            .setDefaultValue(number.defaultValue())
                            .setTooltip(tooltip)
                            .setSaveConsumer(number::set)
                            .build();
            case HearthsteadConfig.IntOption number -> entries.startIntField(name, number.get())
                    .setMin(number.min())
                    .setMax(number.max())
                    .setDefaultValue(number.defaultValue())
                    .setTooltip(tooltip)
                    .setSaveConsumer(number::set)
                    .build();
            case HearthsteadConfig.DoubleOption chance when chance.min() == 0 && chance.max() == 1 -> {
                // Chances read better as a percentage slider; keep finer values the slider cannot show.
                double current = chance.get();
                yield entries.startIntSlider(name, (int) Math.round(current * 100), 0, 100)
                        .setDefaultValue((int) Math.round(chance.defaultValue() * 100))
                        .setTextGetter(percent -> Component.literal(percent + "%"))
                        .setTooltip(tooltip)
                        .setSaveConsumer(percent -> {
                            if (percent != Math.round(current * 100)) {
                                chance.set(percent / 100.0);
                            }
                        })
                        .build();
            }
            case HearthsteadConfig.DoubleOption number -> entries.startDoubleField(name, number.get())
                    .setMin(number.min())
                    .setMax(number.max())
                    .setDefaultValue(number.defaultValue())
                    .setTooltip(tooltip)
                    .setSaveConsumer(number::set)
                    .build();
            case HearthsteadConfig.ChoiceOption choice -> entries.startSelector(name,
                            choice.choices().toArray(String[]::new), choice.get())
                    .setDefaultValue(choice.defaultValue())
                    .setNameProvider(value -> Component.translatableWithFallback("config.hearthstead.choice." + value, capitalize(value)))
                    .setTooltip(tooltip)
                    .setSaveConsumer(choice::set)
                    .build();
        };
    }

    private static Component[] tooltip(HearthsteadConfig.Option<?> option) {
        List<Component> lines = new ArrayList<>();
        for (String line : option.comment().split("\n")) {
            lines.add(Component.literal(line));
        }
        lines.add(Component.translatable("config.hearthstead.default", String.valueOf(option.defaultValue()))
                .withStyle(ChatFormatting.GRAY));
        if (HearthsteadConfig.isClientOption(option)) {
            lines.add(Component.translatable("config.hearthstead.client_only").withStyle(ChatFormatting.AQUA));
        }
        return lines.toArray(Component[]::new);
    }

    /** "nightlyChance" → "Nightly chance", "general" → "General". */
    private static String capitalize(String key) {
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
