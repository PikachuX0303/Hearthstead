package dev.pikachux.hearthstead.client.buildtool;

import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.colony.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The building or decoration currently previewed with the build tool (client side only).
 * A decoration preview has no building type but the decoration's name.
 */
public final class BuildPreview {
    @Nullable
    private static BuildPreview current;

    @Nullable
    private final BuildingType type;
    @Nullable
    private final String decoration;
    private final int level;
    private final Blueprint blueprint;
    /** Set for an upgrade preview of an existing hut; such previews cannot be moved. */
    @Nullable
    private final BlockPos existingHut;
    private final String style;
    private final List<String> styles;
    private BlockPos pos;
    private Direction facing;
    /** A move preview: the existing hut's current level, which can be moved and rotated. */
    private boolean moving;

    public BuildPreview(BuildingType type, int level, Blueprint blueprint, BlockPos pos, Direction facing,
                        @Nullable BlockPos existingHut, String style, List<String> styles) {
        this(type, null, level, blueprint, pos, facing, existingHut, style, styles);
    }

    /** A preview of a decoration. */
    public static BuildPreview decoration(String name, Blueprint blueprint, BlockPos pos, Direction facing, String style,
                                          List<String> styles) {
        return new BuildPreview(null, name, 1, blueprint, pos, facing, null, style, styles);
    }

    private BuildPreview(@Nullable BuildingType type, @Nullable String decoration, int level, Blueprint blueprint, BlockPos pos,
                         Direction facing, @Nullable BlockPos existingHut, String style, List<String> styles) {
        this.type = type;
        this.decoration = decoration;
        this.level = level;
        this.blueprint = blueprint;
        this.pos = pos;
        this.facing = facing;
        this.existingHut = existingHut;
        this.style = style;
        this.styles = styles;
    }

    public String style() {
        return style;
    }

    public List<String> styles() {
        return styles;
    }

    @Nullable
    public static BuildPreview current() {
        return current;
    }

    public static void set(@Nullable BuildPreview preview) {
        current = preview;
    }

    /** The hut being previewed; null for a decoration. */
    @Nullable
    public BuildingType type() {
        return type;
    }

    @Nullable
    public String decoration() {
        return decoration;
    }

    public boolean isDecoration() {
        return decoration != null;
    }

    public int level() {
        return level;
    }

    public Blueprint blueprint() {
        return blueprint;
    }

    @Nullable
    public BlockPos existingHut() {
        return existingHut;
    }

    /** An upgrade preview, fixed to the existing hut. */
    public boolean isUpgrade() {
        return existingHut != null && !moving;
    }

    public boolean isMove() {
        return moving;
    }

    /** A preview for moving the existing hut, starting where it stands. */
    public static BuildPreview move(BuildingType type, int level, Blueprint blueprint, BlockPos hut, Direction facing, String style) {
        BuildPreview preview = new BuildPreview(type, null, level, blueprint, hut, facing, hut, style, List.of(style));
        preview.moving = true;
        return preview;
    }

    public BlockPos pos() {
        return pos;
    }

    public Direction facing() {
        return facing;
    }

    public void moveTo(BlockPos pos) {
        if (!isUpgrade()) {
            this.pos = pos.immutable();
        }
    }

    public void move(Direction direction, int distance) {
        moveTo(pos.relative(direction, distance));
    }

    public void rotate(boolean clockwise) {
        if (!isUpgrade()) {
            facing = clockwise ? facing.getClockWise() : facing.getCounterClockWise();
        }
    }
}
