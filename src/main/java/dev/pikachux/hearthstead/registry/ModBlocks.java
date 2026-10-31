package dev.pikachux.hearthstead.registry;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.colony.BuildingType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    public static final Map<BuildingType, HutBlock> HUTS = new EnumMap<>(BuildingType.class);
    public static BlockEntityType<HutBlockEntity> HUT_BLOCK_ENTITY;
    /** Wooden posts joined by rope; connects like any wooden fence. */
    public static FenceBlock ROPE_FENCE;

    private ModBlocks() {
    }

    public static void register() {
        for (BuildingType type : BuildingType.values()) {
            String name = type.blockName();
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hearthstead.id(name));
            HutBlock block = new HutBlock(type, BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F, 3600000.0F)
                    .sound(SoundType.WOOD)
                    // The hut model is a small house, not a full cube, so neighbours must stay visible.
                    .noOcclusion());
            Registry.register(BuiltInRegistries.BLOCK, key, block);

            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hearthstead.id(name));
            Registry.register(BuiltInRegistries.ITEM, itemKey,
                    new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().stacksTo(1)));
            HUTS.put(type, block);
        }

        ResourceKey<Block> ropeKey = ResourceKey.create(Registries.BLOCK, Hearthstead.id("rope_fence"));
        ROPE_FENCE = Registry.register(BuiltInRegistries.BLOCK, ropeKey, new FenceBlock(BlockBehaviour.Properties.of()
                .setId(ropeKey)
                .mapColor(MapColor.WOOD)
                .strength(1.5F, 3.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava()));
        ResourceKey<Item> ropeItemKey = ResourceKey.create(Registries.ITEM, Hearthstead.id("rope_fence"));
        Registry.register(BuiltInRegistries.ITEM, ropeItemKey,
                new BlockItem(ROPE_FENCE, new Item.Properties().setId(ropeItemKey).useBlockDescriptionPrefix()));

        HUT_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hearthstead.id("hut"),
                FabricBlockEntityTypeBuilder.create(HutBlockEntity::new, HUTS.values().toArray(new Block[0])).build());
    }
}
