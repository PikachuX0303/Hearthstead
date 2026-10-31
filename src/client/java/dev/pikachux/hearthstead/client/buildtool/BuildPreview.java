package dev.pikachux.hearthstead.client.buildtool;

import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.colony.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The building currently previewed with the build tool (client side only).
 */
public final class BuildPreview {
    @Nullable
    private static BuildPreview current;

    private final BuildingType type;
    private final int level;
    private final Blueprint blueprint;
    /** Set for an upgrade preview of an existing hut; such previews cannot be moved. */
    @Nullable
    private final BlockPos existingHut;
    private final String style;
    private final List<String> styles;
    private BlockPos pos;
    private Direction facing;

    public BuildPreview(BuildingType type, int level, Blueprint blueprint, BlockPos pos, Direction facing,
                        @Nullable BlockPos existingHut, String style, List<String> styles) {
        this.type = type;
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

    public BuildingType type() {
        return type;
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

    public boolean isUpgrade() {
        return existingHut != null;
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
