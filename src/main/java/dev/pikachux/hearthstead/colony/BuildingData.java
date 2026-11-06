package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.blueprint.Styles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

/**
 * Server-side record of a hut placed inside a colony.
 */
public class BuildingData {
    private final BlockPos pos;
    private final BuildingType type;
    private int level;
    private String style = Styles.CLASSIC;
    /** Values of the hut's settings that differ from their defaults (see {@link HutSettings}). */
    private final Map<String, String> settings = new TreeMap<>();
    /** A postbox's orders: item id to count, in the order they were made. */
    private final Map<String, Integer> orders = new java.util.LinkedHashMap<>();
    /** Recipes taught to a crafting hut: item id to how many to keep in stock (0: only on request). */
    private final Map<String, Integer> recipes = new java.util.LinkedHashMap<>();

    public BuildingData(BlockPos pos, BuildingType type, int level) {
        this.pos = pos.immutable();
        this.type = type;
        this.level = level;
    }

    public BlockPos pos() {
        return pos;
    }

    public BuildingType type() {
        return type;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, Math.min(BuildingType.MAX_LEVEL, level));
    }

    /** The building style used for the next construction or upgrade. */
    public String style() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    @Nullable
    public String setting(String id) {
        return settings.get(id);
    }

    /** Sets a setting; null restores its default. */
    public void setSetting(String id, @Nullable String value) {
        if (value == null) {
            settings.remove(id);
        } else {
            settings.put(id, value);
        }
    }

    /** Items a postbox has ordered for delivery, with how many (see {@link #setOrder}). */
    public Map<String, Integer> orders() {
        return orders;
    }

    /** Orders {@code count} of an item (by its id) for delivery to this postbox; 0 cancels the order. */
    public void setOrder(String itemId, int count) {
        if (count <= 0) {
            orders.remove(itemId);
        } else {
            orders.put(itemId, count);
        }
    }

    /** Recipes taught to this crafting hut, with how many of each to keep in stock (see {@link #teach}). */
    public Map<String, Integer> recipes() {
        return recipes;
    }

    /** Teaches the hut to make an item (by id), keeping {@code stock} of them (0: only when asked for). */
    public void teach(String itemId, int stock) {
        recipes.put(itemId, Math.max(0, stock));
    }

    public void forget(String itemId) {
        recipes.remove(itemId);
    }

    /** Radius around the hut in which its worker operates. */
    public int workRadius() {
        return 8 + level * 4;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        tag.putString("type", type.id());
        tag.putInt("level", level);
        tag.putString("style", style);
        if (!settings.isEmpty()) {
            CompoundTag saved = new CompoundTag();
            settings.forEach(saved::putString);
            tag.put("settings", saved);
        }
        if (!orders.isEmpty()) {
            CompoundTag saved = new CompoundTag();
            orders.forEach(saved::putInt);
            tag.put("orders", saved);
        }
        if (!recipes.isEmpty()) {
            CompoundTag saved = new CompoundTag();
            recipes.forEach(saved::putInt);
            tag.put("recipes", saved);
        }
        return tag;
    }

    /** A copy of this building at another spot (used when a hut is moved). */
    public BuildingData movedTo(BlockPos to) {
        BuildingData moved = new BuildingData(to, type, level);
        moved.style = style;
        moved.settings.putAll(settings);
        moved.orders.putAll(orders);
        moved.recipes.putAll(recipes);
        return moved;
    }

    public static BuildingData load(CompoundTag tag) {
        BuildingData building = new BuildingData(BlockPos.of(tag.getLongOr("pos", 0L)),
                BuildingType.byId(tag.getStringOr("type", "")),
                tag.getIntOr("level", 0));
        building.style = Styles.migrate(tag.getStringOr("style", Styles.CLASSIC));
        tag.getCompound("settings").ifPresent(saved -> {
            for (String key : saved.keySet()) {
                saved.getString(key).ifPresent(value -> building.settings.put(key, value));
            }
        });
        tag.getCompound("orders").ifPresent(saved -> {
            for (String key : saved.keySet()) {
                saved.getInt(key).ifPresent(count -> building.orders.put(key, count));
            }
        });
        tag.getCompound("recipes").ifPresent(saved -> {
            for (String key : saved.keySet()) {
                saved.getInt(key).ifPresent(stock -> building.recipes.put(key, stock));
            }
        });
        return building;
    }
}
