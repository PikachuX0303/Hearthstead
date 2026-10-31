package dev.pikachux.hearthstead.worldgen;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * The mod's structure types. Where and how often they generate is set in
 * {@code data/hearthstead/worldgen} (structure, structure set and biome tag) and the config.
 */
public final class ModWorldgen {
    public static final StructureType<AbandonedSettlementStructure> ABANDONED_SETTLEMENT = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE, Hearthstead.id("abandoned_settlement"), () -> AbandonedSettlementStructure.CODEC);
    public static final StructurePieceType ABANDONED_SETTLEMENT_PIECE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE, Hearthstead.id("abandoned_settlement"),
            (StructurePieceType.StructureTemplateType) AbandonedSettlementPiece::new);

    private ModWorldgen() {
    }

    /** Loads the class, which registers the types above. */
    public static void register() {
    }
}
