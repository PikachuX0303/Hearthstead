package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.Crafters;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Blacksmith: forges the tools, weapons, armour and shields the colony's huts ask for. Prefers
 * iron, then stone; diamond gear needs a level 3 smithy.
 */
public class BlacksmithGoal extends RecipeCrafterGoal {
    /** Hut level from which the blacksmith works diamonds. */
    public static final int DIAMOND_LEVEL = 3;

    public BlacksmithGoal(CitizenEntity citizen) {
        super(citizen, JobType.BLACKSMITH);
    }

    @Override
    protected Predicate<Item> products() {
        return Crafters::isSmithing;
    }

    @Override
    protected SoundEvent workSound() {
        return SoundEvents.ANVIL_USE;
    }

    @Override
    protected List<Item> order(List<Item> wanted, BuildingData building) {
        String material = dev.pikachux.hearthstead.colony.HutSettings.get(building, dev.pikachux.hearthstead.colony.HutSettings.SMITH_MATERIAL);
        boolean diamond = building.level() >= DIAMOND_LEVEL && "best".equals(material);
        Comparator<Item> order = Comparator.comparingInt(BlacksmithGoal::preference);
        return wanted.stream()
                .filter(item -> diamond || !path(item).startsWith("diamond_"))
                .sorted("stone".equals(material) ? Comparator.comparingInt(BlacksmithGoal::stonePreference) : order)
                .toList();
    }

    /** Stone first, to save iron. */
    private static int stonePreference(Item item) {
        String path = path(item);
        return path.startsWith("stone_") ? 0 : path.startsWith("iron_") ? 1 : 2;
    }

    private static int preference(Item item) {
        String path = path(item);
        return path.startsWith("iron_") ? 0 : path.startsWith("stone_") ? 1 : 2;
    }

    private static String path(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }
}
