package dev.pikachux.hearthstead.blueprint;

import dev.pikachux.hearthstead.block.HutBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A parsed hut schematic. Positions are stored relative to the hut block inside the
 * template, so the blueprint can be placed around any hut regardless of rotation.
 */
public class Blueprint {
    /** A single block of the blueprint, relative to the anchor (hut) position. */
    public record Entry(BlockPos offset, BlockState state) {
    }

    /** A block resolved to world coordinates. */
    public record Placement(BlockPos pos, BlockState state) {
    }

    private final List<Entry> entries;
    private final Direction anchorFacing;
    private final BlockPos min;
    private final BlockPos max;

    Blueprint(List<Entry> entries, Direction anchorFacing) {
        this.entries = entries;
        this.anchorFacing = anchorFacing;
        int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for (Entry entry : entries) {
            BlockPos p = entry.offset();
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        this.min = new BlockPos(minX, minY, minZ);
        this.max = new BlockPos(maxX, maxY, maxZ);
    }

    /** Builds a blueprint from entries relative to the hut, e.g. one received from the server. */
    public static Blueprint of(List<Entry> entries, Direction anchorFacing) {
        return new Blueprint(entries, anchorFacing);
    }

    public List<Entry> entries() {
        return entries;
    }

    public Direction anchorFacing() {
        return anchorFacing;
    }

    public int size() {
        return entries.size();
    }

    /** Rotation that turns the template's hut facing into the placed hut's facing. */
    public Rotation rotationFor(Direction hutFacing) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(anchorFacing) == hutFacing) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }

    /** World-space bounding box of the blueprint around a hut. */
    public BoundingBox bounds(BlockPos hut, Direction hutFacing) {
        Rotation rotation = rotationFor(hutFacing);
        return BoundingBox.fromCorners(hut.offset(min.rotate(rotation)), hut.offset(max.rotate(rotation)));
    }

    /**
     * Every block to build, in build order: first everything that has to be cleared
     * (top to bottom), then solid blocks (bottom to top), then blocks that need
     * support such as torches, doors and beds. The hut itself and the second halves
     * of doors and beds are not listed; they are placed together with their partner.
     */
    public List<Placement> placements(BlockPos hut, Direction hutFacing) {
        Rotation rotation = rotationFor(hutFacing);
        List<Placement> clear = new ArrayList<>();
        List<Placement> solid = new ArrayList<>();
        List<Placement> attached = new ArrayList<>();
        for (Entry entry : entries) {
            BlockState state = entry.state().rotate(rotation);
            BlockPos pos = hut.offset(entry.offset().rotate(rotation));
            if (state.getBlock() instanceof HutBlock || state.is(Blocks.STRUCTURE_VOID) || isSecondHalf(state)) {
                continue;
            }
            if (state.isAir()) {
                clear.add(new Placement(pos, state));
            } else if (needsSupport(state)) {
                attached.add(new Placement(pos, state));
            } else {
                solid.add(new Placement(pos, state));
            }
        }
        clear.sort(Comparator.comparingInt((Placement p) -> -p.pos().getY()));
        solid.sort(Comparator.comparingInt((Placement p) -> p.pos().getY()));
        attached.sort(Comparator.comparingInt((Placement p) -> p.pos().getY()));
        List<Placement> result = new ArrayList<>(clear.size() + solid.size() + attached.size());
        result.addAll(clear);
        result.addAll(solid);
        result.addAll(attached);
        return result;
    }

    /** The other half of a two-block structure, or null. */
    public static BlockPos partnerOf(Placement placement) {
        BlockState state = placement.state();
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            return placement.pos().above();
        }
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            return placement.pos().relative(state.getValue(BedBlock.FACING));
        }
        return null;
    }

    /** State of the other half for a two-block structure. */
    public static BlockState partnerState(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        }
        return state.setValue(BedBlock.PART, BedPart.HEAD);
    }

    private static boolean isSecondHalf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
        }
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    private static boolean needsSupport(BlockState state) {
        var block = state.getBlock();
        return block instanceof BaseTorchBlock || block instanceof DoorBlock || block instanceof BedBlock
                || block instanceof LadderBlock || block instanceof FaceAttachedHorizontalDirectionalBlock
                || block instanceof CarpetBlock || block instanceof BaseRailBlock || block instanceof LanternBlock
                || block instanceof SignBlock || block instanceof FlowerPotBlock || block instanceof TrapDoorBlock
                || block instanceof BasePressurePlateBlock || block instanceof VegetationBlock
                || !state.getFluidState().isEmpty();
    }
}
