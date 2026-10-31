package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.blueprint.Styles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Server-side record of a hut placed inside a colony.
 */
public class BuildingData {
    private final BlockPos pos;
    private final BuildingType type;
    private int level;
    private String style = Styles.CLASSIC;

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
        return tag;
    }

    public static BuildingData load(CompoundTag tag) {
        BuildingData building = new BuildingData(BlockPos.of(tag.getLongOr("pos", 0L)),
                BuildingType.byId(tag.getStringOr("type", "")),
                tag.getIntOr("level", 0));
        building.style = tag.getStringOr("style", Styles.CLASSIC);
        return building;
    }
}
