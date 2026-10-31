package dev.pikachux.hearthstead.registry;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import dev.pikachux.hearthstead.entity.raider.AbstractBarbarian;
import dev.pikachux.hearthstead.entity.raider.ArcherBarbarianEntity;
import dev.pikachux.hearthstead.entity.raider.BarbarianEntity;
import dev.pikachux.hearthstead.entity.raider.ChiefBarbarianEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final ResourceKey<EntityType<?>> CITIZEN_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Hearthstead.id("citizen"));

    public static final EntityType<CitizenEntity> CITIZEN = Registry.register(BuiltInRegistries.ENTITY_TYPE, CITIZEN_KEY,
            EntityType.Builder.of(CitizenEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build(CITIZEN_KEY));

    public static final EntityType<BarbarianEntity> BARBARIAN = raider("barbarian", BarbarianEntity::new);
    public static final EntityType<ArcherBarbarianEntity> ARCHER_BARBARIAN = raider("archer_barbarian", ArcherBarbarianEntity::new);
    public static final EntityType<ChiefBarbarianEntity> CHIEF_BARBARIAN = raider("chief_barbarian", ChiefBarbarianEntity::new);

    private ModEntities() {
    }

    private static <T extends AbstractBarbarian> EntityType<T> raider(String name, EntityType.EntityFactory<T> factory) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Hearthstead.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(factory, MobCategory.MONSTER)
                .sized(0.6F, 1.95F)
                .eyeHeight(1.62F)
                .clientTrackingRange(10)
                .build(key));
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(CITIZEN, CitizenEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BARBARIAN, AbstractBarbarian.createBarbarianAttributes());
        FabricDefaultAttributeRegistry.register(ARCHER_BARBARIAN, AbstractBarbarian.createBarbarianAttributes());
        FabricDefaultAttributeRegistry.register(CHIEF_BARBARIAN, ChiefBarbarianEntity.createChiefAttributes());
    }
}
