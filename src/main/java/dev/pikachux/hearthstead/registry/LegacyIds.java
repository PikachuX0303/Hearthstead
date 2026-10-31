package dev.pikachux.hearthstead.registry;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.colony.BuildingType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The mod used the id "minecolonies" before it was renamed to Hearthstead. These aliases
 * let worlds from that time load their huts, items and citizens under the new ids.
 */
public final class LegacyIds {
    private static final String LEGACY_NAMESPACE = "minecolonies";

    private LegacyIds() {
    }

    public static void register() {
        for (BuildingType type : BuildingType.values()) {
            alias(BuiltInRegistries.BLOCK, type.blockName());
            alias(BuiltInRegistries.ITEM, type.blockName());
        }
        alias(BuiltInRegistries.BLOCK, "rope_fence");
        alias(BuiltInRegistries.ITEM, "rope_fence");
        alias(BuiltInRegistries.ITEM, "build_tool");
        alias(BuiltInRegistries.BLOCK_ENTITY_TYPE, "hut");
        for (String entity : List.of("citizen", "barbarian", "archer_barbarian", "chief_barbarian")) {
            alias(BuiltInRegistries.ENTITY_TYPE, entity);
        }
    }

    private static void alias(Registry<?> registry, String path) {
        registry.addAlias(Identifier.fromNamespaceAndPath(LEGACY_NAMESPACE, path), Hearthstead.id(path));
    }
}
