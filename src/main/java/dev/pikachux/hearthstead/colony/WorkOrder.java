package dev.pikachux.hearthstead.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * A request for a builder to build a hut up to the given level.
 */
public class WorkOrder {
    private final BlockPos buildingPos;
    private final int targetLevel;
    @Nullable
    private BlockPos builder;

    public WorkOrder(BlockPos buildingPos, int targetLevel) {
        this.buildingPos = buildingPos.immutable();
        this.targetLevel = targetLevel;
    }

    public BlockPos buildingPos() {
        return buildingPos;
    }

    public int targetLevel() {
        return targetLevel;
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
        return tag;
    }

    public static WorkOrder load(CompoundTag tag) {
        WorkOrder order = new WorkOrder(BlockPos.of(tag.getLongOr("pos", 0L)), tag.getIntOr("level", 1));
        tag.getLong("builder").ifPresent(l -> order.builder = BlockPos.of(l));
        return order;
    }
}
