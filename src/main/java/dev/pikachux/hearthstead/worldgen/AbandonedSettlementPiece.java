package dev.pikachux.hearthstead.worldgen;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The single template of an abandoned settlement ({@code tools/generate_worldgen.py}), placed
 * with the town hall at the chosen spot and the supply chest filled from a loot table.
 */
public class AbandonedSettlementPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE = Hearthstead.id("abandoned_settlement/camp");
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Hearthstead.id("chests/abandoned_settlement"));
    /** Template size and the positions of the town hall and the chest in it (see generate_worldgen.py). */
    static final int SIZE = 17;
    private static final BlockPos CENTER = new BlockPos(8, 1, 8);
    private static final BlockPos CHEST = new BlockPos(3, 1, 3);

    public AbandonedSettlementPiece(StructureTemplateManager templates, BlockPos center, Rotation rotation) {
        super(ModWorldgen.ABANDONED_SETTLEMENT_PIECE, 0, templates, TEMPLATE, TEMPLATE.toString(), settings(rotation),
                center.offset(-CENTER.getX(), 0, -CENTER.getZ()));
    }

    public AbandonedSettlementPiece(StructureTemplateManager templates, CompoundTag tag) {
        super(ModWorldgen.ABANDONED_SETTLEMENT_PIECE, tag, templates,
                id -> settings(tag.read("Rot", Rotation.LEGACY_CODEC).orElse(Rotation.NONE)));
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(new BlockPos(CENTER.getX(), 0, CENTER.getZ()))
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.store("Rot", Rotation.LEGACY_CODEC, placeSettings.getRotation());
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        super.postProcess(level, structures, generator, random, box, chunkPos, pivot);
        BlockPos chest = templatePosition.offset(StructureTemplate.calculateRelativePosition(placeSettings, CHEST));
        if (box.isInside(chest)) {
            RandomizableContainer.setBlockEntityLootTable(level, random, chest, LOOT);
        }
    }

    /** Where the town hall of a settlement placed at {@code templatePosition} ends up. */
    public BlockPos townHall() {
        return templatePosition.offset(StructureTemplate.calculateRelativePosition(placeSettings, CENTER));
    }
}
