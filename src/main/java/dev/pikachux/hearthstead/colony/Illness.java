package dev.pikachux.hearthstead.colony;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Illnesses citizens can catch. A sick citizen stops working and rests at a hospital (or at
 * home); a healer cures them with the illness's cure from the hospital's stock, otherwise
 * they get better on their own after some days.
 */
public enum Illness {
    COLD("Cold", Items.HONEY_BOTTLE, 3),
    FEVER("Fever", Items.GOLDEN_CARROT, 4),
    STOMACH_BUG("Stomach bug", Items.MILK_BUCKET, 2);

    private final String displayName;
    private final Item cure;
    private final int recoveryDays;

    Illness(String displayName, Item cure, int recoveryDays) {
        this.displayName = displayName;
        this.cure = cure;
        this.recoveryDays = recoveryDays;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    /** What a healer needs to cure it. */
    public Item cure() {
        return cure;
    }

    /** Days until a citizen gets better without a cure. */
    public int recoveryDays() {
        return recoveryDays;
    }

    @Nullable
    public static Illness byId(String id) {
        for (Illness illness : values()) {
            if (illness.id().equals(id)) {
                return illness;
            }
        }
        return null;
    }
}
