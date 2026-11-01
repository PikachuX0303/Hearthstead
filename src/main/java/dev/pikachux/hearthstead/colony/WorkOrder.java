package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.blueprint.Styles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * A request for a builder: build a hut up to the given level, repair a hut's current level,
 * take a hut down (or clear the site a hut moved away from), or build a decoration (which has no
 * hut block, so it carries its own blueprint name, facing and style).
 */
public class WorkOrder {
    public enum Kind {
        /** Build a hut level or a decoration. */
        BUILD,
        /** Put back what is missing from the hut's current level. */
        REPAIR,
        /** Take down the blocks of a hut's blueprint. */
        REMOVE;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final BlockPos buildingPos;
    private final int targetLevel;
    private final Kind kind;
    /** For removals: the building type whose blueprint is taken down. */
    @Nullable
    private final BuildingType hutType;
    /** For removals: true to remove the hut itself at the end, false to only clear an old site. */
    private final boolean demolish;
    /** Blueprint name of the decoration to build, or null for a hut. */
    @Nullable
    private final String decoration;
    private final Direction facing;
    private final String style;
    @Nullable
    private BlockPos builder;

    public WorkOrder(BlockPos buildingPos, int targetLevel) {
        this(buildingPos, targetLevel, Kind.BUILD, null, Direction.NORTH, Styles.CLASSIC, null, false);
    }

    private WorkOrder(BlockPos buildingPos, int targetLevel, Kind kind, @Nullable String decoration, Direction facing, String style,
                      @Nullable BuildingType hutType, boolean demolish) {
        this.buildingPos = buildingPos.immutable();
        this.targetLevel = targetLevel;
        this.kind = kind;
        this.decoration = decoration;
        this.facing = facing;
        this.style = style;
        this.hutType = hutType;
        this.demolish = demolish;
    }

    /** An order to build the decoration {@code name} anchored at {@code pos}. */
    public static WorkOrder decoration(BlockPos pos, String name, Direction facing, String style) {
        return new WorkOrder(pos, 1, Kind.BUILD, name, facing, style, null, false);
    }

    /** An order to repair the hut at {@code pos}: rebuild its current level where blocks are missing. */
    public static WorkOrder repair(BlockPos pos, int level) {
        return new WorkOrder(pos, level, Kind.REPAIR, null, Direction.NORTH, Styles.CLASSIC, null, false);
    }

    /**
     * An order to take down a hut's blueprint at {@code pos}: with {@code demolish} the hut itself
     * goes too, otherwise only the site a moved hut left behind is cleared.
     */
    public static WorkOrder removal(BlockPos pos, BuildingType type, String style, int level, Direction facing, boolean demolish) {
        return new WorkOrder(pos, level, Kind.REMOVE, null, facing, style, type, demolish);
    }

    public Kind kind() {
        return kind;
    }

    @Nullable
    public BuildingType hutType() {
        return hutType;
    }

    public boolean demolish() {
        return demolish;
    }

    /** The hut to build, or the anchor of a decoration. */
    public BlockPos buildingPos() {
        return buildingPos;
    }

    public int targetLevel() {
        return targetLevel;
    }

    public boolean isDecoration() {
        return decoration != null;
    }

    @Nullable
    public String decoration() {
        return decoration;
    }

    /** Facing of a decoration (huts take theirs from the hut block). */
    public Direction facing() {
        return facing;
    }

    /** Style of a decoration (huts use the building's style). */
    public String style() {
        return style;
    }

    /** Builder's hut that has claimed this order, if any. */
    @Nullable
    public BlockPos builder() {
        return builder;
    }

    public void setBuilder(@Nullable BlockPos builder) {
        this.builder = builder == null ? null : builder.immutable();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", buildingPos.asLong());
        tag.putInt("level", targetLevel);
        if (builder != null) {
            tag.putLong("builder", builder.asLong());
        }
        if (kind != Kind.BUILD) {
            tag.putString("kind", kind.id());
        }
        if (decoration != null) {
            tag.putString("decoration", decoration);
        }
        if (decoration != null || kind == Kind.REMOVE) {
            tag.putString("facing", facing.getSerializedName());
            tag.putString("style", style);
        }
        if (hutType != null) {
            tag.putString("hutType", hutType.id());
            tag.putBoolean("demolish", demolish);
        }
        return tag;
    }

    public static WorkOrder load(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLongOr("pos", 0L));
        WorkOrder order;
        String decoration = tag.getStringOr("decoration", "");
        Direction parsed = Direction.byName(tag.getStringOr("facing", "north"));
        Direction facing = parsed == null || !parsed.getAxis().isHorizontal() ? Direction.NORTH : parsed;
        String style = tag.getStringOr("style", Styles.CLASSIC);
        int level = tag.getIntOr("level", 1);
        String kind = tag.getStringOr("kind", "build");
        if (!decoration.isEmpty()) {
            order = decoration(pos, decoration, facing, style);
        } else if (kind.equals("repair")) {
            order = repair(pos, level);
        } else if (kind.equals("remove")) {
            BuildingType type = BuildingType.byId(tag.getStringOr("hutType", ""));
            order = removal(pos, type, style, level, facing, tag.getBooleanOr("demolish", false));
        } else {
            order = new WorkOrder(pos, level);
        }
        tag.getLong("builder").ifPresent(l -> order.builder = BlockPos.of(l));
        return order;
    }
}
