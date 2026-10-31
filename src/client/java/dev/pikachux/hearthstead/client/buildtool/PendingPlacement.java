package dev.pikachux.hearthstead.client.buildtool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Where a new preview should appear once the server has sent the blueprint.
 */
public final class PendingPlacement {
    @Nullable
    private static BlockPos pos;
    private static Direction facing = Direction.NORTH;

    private PendingPlacement() {
    }

    public static void set(BlockPos target, Direction direction) {
        pos = target.immutable();
        facing = direction;
    }

    @Nullable
    public static BlockPos pos() {
        return pos;
    }

    public static Direction facing() {
        return facing;
    }
}
