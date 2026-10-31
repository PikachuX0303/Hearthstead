package dev.pikachux.hearthstead.worldgen;

import com.mojang.serialization.MapCodec;
import dev.pikachux.hearthstead.config.HearthsteadConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * An abandoned settlement: a ruined camp around an unclaimed town hall. Generates on fairly flat,
 * dry ground; how often is set in the config ({@code [worldgen]}).
 */
public class AbandonedSettlementStructure extends Structure {
    public static final MapCodec<AbandonedSettlementStructure> CODEC = simpleCodec(AbandonedSettlementStructure::new);

    /** Largest height difference between the corners of the camp. */
    private static final int MAX_SLOPE = 3;

    public AbandonedSettlementStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!HearthsteadConfig.SETTLEMENTS.get() || context.random().nextDouble() >= HearthsteadConfig.SETTLEMENT_CHANCE.get()) {
            return Optional.empty();
        }
        Rotation rotation = Rotation.getRandom(context.random());
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor heights = context.heightAccessor();
        RandomState randomState = context.randomState();

        int half = AbandonedSettlementPiece.SIZE / 2;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx = -half; dx <= half; dx += half) {
            for (int dz = -half; dz <= half; dz += half) {
                int y = generator.getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState);
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y);
            }
        }
        if (highest - lowest > MAX_SLOPE) {
            return Optional.empty();
        }
        int ground = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState);
        NoiseColumn column = generator.getBaseColumn(x, z, heights, randomState);
        if (!column.getBlock(ground).getFluidState().isEmpty()) {
            return Optional.empty();
        }
        BlockPos center = new BlockPos(x, ground, z);
        return Optional.of(new GenerationStub(center, pieces -> pieces.addPiece(
                new AbandonedSettlementPiece(context.structureTemplateManager(), center, rotation))));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.ABANDONED_SETTLEMENT;
    }
}
