package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.blueprint.Styles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * A request for a builder: build a hut up to the given level, or build a decoration
 * (which has no hut block, so it carries its own blueprint name, facing and style).
 */
public class WorkOrder {
    private final BlockPos buildingPos;
    private final int targetLevel;
    /** Blueprint name of the decoration to build, or null for a hut. */
    @Nullable
    private final String decoration;
    private final Direction facing;
    private final String style;
    @Nullable
    private BlockPos builder;

    public WorkOrder(BlockPos buildingPos, int targetLevel) {
        this(buildingPos, targetLevel, null, Direction.NORTH, Styles.CLASSIC);
    }

    private WorkOrder(BlockPos buildingPos, int targetLevel, @Nullable String decoration, Direction facing, String style) {
        this.buildingPos = buildingPos.immutable();
        this.targetLevel = targetLevel;
        this.decoration = decoration;
        this.facing = facing;
        this.style = style;
    }

    /** An order to build the decoration {@code name} anchored at {@code pos}. */
    public static WorkOrder decoration(BlockPos pos, String name, Direction facing, String style) {
        return new WorkOrder(pos, 1, name, facing, style);
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
        if (decoration != null) {
            tag.putString("decoration", decoration);
            tag.putString("facing", facing.getSerializedName());
            tag.putString("style", style);
        }
        return tag;
    }

    public static WorkOrder load(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLongOr("pos", 0L));
        WorkOrder order;
        String decoration = tag.getStringOr("decoration", "");
        if (decoration.isEmpty()) {
            order = new WorkOrder(pos, tag.getIntOr("level", 1));
        } else {
            Direction facing = Direction.byName(tag.getStringOr("facing", "north"));
            order = decoration(pos, decoration, facing == null || !facing.getAxis().isHorizontal() ? Direction.NORTH : facing,
                    tag.getStringOr("style", Styles.CLASSIC));
        }
        tag.getLong("builder").ifPresent(l -> order.builder = BlockPos.of(l));
        return order;
    }
}
