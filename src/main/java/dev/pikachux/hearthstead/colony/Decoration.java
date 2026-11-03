package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.blueprint.Blueprint;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import dev.pikachux.hearthstead.blueprint.Construction;
import dev.pikachux.hearthstead.blueprint.Styles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A decoration the colony's builders have finished: a well, a fountain, a market stall and so on.
 * Decorations have no hut block; they are remembered by their blueprint name, anchor, facing and
 * style. Every intact decoration makes the citizens a little happier.
 */
public class Decoration {
    /** Happiness one intact decoration gives every citizen. */
    public static final double HAPPINESS = 0.25;
    /** Most happiness decorations can give together. */
    public static final double MAX_HAPPINESS = 1.0;
    /** Share of a decoration's blocks that must still be in place for it to count. */
    public static final double INTACT_SHARE = 0.75;
    /** Below this share the decoration is considered torn down and forgotten. */
    public static final double REMOVED_SHARE = 0.25;

    private final String name;
    private final BlockPos pos;
    private final Direction facing;
    private final String style;
    private boolean intact = true;

    public Decoration(String name, BlockPos pos, Direction facing, String style) {
        this.name = name;
        this.pos = pos.immutable();
        this.facing = facing;
        this.style = style;
    }

    public String name() {
        return name;
    }

    public BlockPos pos() {
        return pos;
    }

    public Direction facing() {
        return facing;
    }

    public String style() {
        return style;
    }

    public boolean intact() {
        return intact;
    }

    public void setIntact(boolean intact) {
        this.intact = intact;
    }

    /** The decoration's blocks in the world, or null if its blueprint is gone. */
    @Nullable
    public List<Blueprint.Placement> plan(ServerLevel level) {
        Blueprint blueprint = Blueprints.decoration(level.getServer(), style, name);
        return blueprint == null ? null : blueprint.placements(pos, facing);
    }

    @Nullable
    public BoundingBox bounds(ServerLevel level) {
        Blueprint blueprint = Blueprints.decoration(level.getServer(), style, name);
        return blueprint == null ? null : blueprint.bounds(pos, facing);
    }

    /** True if every chunk the decoration touches is loaded, so it can be looked at without loading any. */
    public boolean loaded(ServerLevel level) {
        BoundingBox box = bounds(level);
        return box != null ? level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ()) : level.isLoaded(pos);
    }

    /** Share of the decoration's blocks (not counting cleared air) that are in place, from 0 to 1. */
    public double completeness(ServerLevel level) {
        List<Blueprint.Placement> plan = plan(level);
        if (plan == null) {
            return 0;
        }
        int total = 0;
        int done = 0;
        for (Blueprint.Placement placement : plan) {
            if (placement.state().isAir()) {
                continue;
            }
            total++;
            if (Construction.isDone(level, placement)) {
                done++;
            }
        }
        return total == 0 ? 1 : (double) done / total;
    }

    /**
     * Returns null if a decoration may be ordered here, otherwise a translation key: it must lie
     * inside the colony and must not overlap a hut, another decoration or an ordered one.
     */
    @Nullable
    public static String placementError(ServerLevel level, Colony colony, String name, BlockPos pos, Direction facing, String style) {
        Blueprint blueprint = Blueprints.decoration(level.getServer(), style, name);
        if (blueprint == null) {
            return "message.hearthstead.no_blueprint";
        }
        BoundingBox box = blueprint.bounds(pos, facing);
        if (!colony.isInside(level.dimension(), new BlockPos(box.minX(), box.minY(), box.minZ()))
                || !colony.isInside(level.dimension(), new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
            return "message.hearthstead.decoration_outside";
        }
        for (BuildingData building : colony.buildings()) {
            BoundingBox footprint = Blueprints.footprint(level.getServer(), level, building);
            if (footprint != null && footprint.intersects(box) || box.isInside(building.pos())) {
                return "message.hearthstead.decoration_overlaps";
            }
        }
        for (Decoration other : colony.decorations()) {
            BoundingBox bounds = other.bounds(level);
            if (bounds != null && bounds.intersects(box)) {
                return "message.hearthstead.decoration_overlaps";
            }
        }
        for (WorkOrder order : colony.workOrders()) {
            if (order.isDecoration()) {
                Blueprint ordered = Blueprints.decoration(level.getServer(), order.style(), order.decoration());
                if (ordered != null && ordered.bounds(order.buildingPos(), order.facing()).intersects(box)) {
                    return "message.hearthstead.decoration_overlaps";
                }
            }
        }
        return null;
    }

    /** English name for chat and status lines, e.g. "market_stall" → "Market stall". */
    /** The decoration's translated name, falling back to {@link #displayName(String)}. */
    public static net.minecraft.network.chat.Component nameComponent(String name) {
        return net.minecraft.network.chat.Component.translatableWithFallback("decoration.hearthstead." + name, displayName(name));
    }

    public static String displayName(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1).replace('_', ' ');
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putLong("pos", pos.asLong());
        tag.putString("facing", facing.getSerializedName());
        tag.putString("style", style);
        tag.putBoolean("intact", intact);
        return tag;
    }

    public static Decoration load(CompoundTag tag) {
        Direction facing = Direction.byName(tag.getStringOr("facing", "north"));
        Decoration decoration = new Decoration(tag.getStringOr("name", ""), BlockPos.of(tag.getLongOr("pos", 0L)),
                facing == null || !facing.getAxis().isHorizontal() ? Direction.NORTH : facing,
                tag.getStringOr("style", Styles.CLASSIC));
        decoration.intact = tag.getBooleanOr("intact", true);
        return decoration;
    }
}
