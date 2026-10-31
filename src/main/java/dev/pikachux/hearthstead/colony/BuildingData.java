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
        return tag;
    }

    public static BuildingData load(CompoundTag tag) {
        BuildingData building = new BuildingData(BlockPos.of(tag.getLongOr("pos", 0L)),
                BuildingType.byId(tag.getStringOr("type", "")),
                tag.getIntOr("level", 0));
        building.style = tag.getStringOr("style", Styles.CLASSIC);
        tag.getCompound("settings").ifPresent(saved -> {
            for (String key : saved.keySet()) {
                saved.getString(key).ifPresent(value -> building.settings.put(key, value));
            }
        });
        return building;
    }
}
