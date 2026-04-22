package com.mifan.registry;

import com.mifan.corpsecampus;
import com.mifan.entity.GoldenCrowSunEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            ForgeRegistries.ENTITY_TYPES, corpsecampus.MODID);

    public static final RegistryObject<EntityType<GoldenCrowSunEntity>> GOLDEN_CROW_SUN = ENTITIES.register(
            "golden_crow_sun",
            () -> EntityType.Builder.<GoldenCrowSunEntity>of(GoldenCrowSunEntity::new, MobCategory.MISC)
                    .sized(6.0F, 6.0F)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .build(ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "golden_crow_sun").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
