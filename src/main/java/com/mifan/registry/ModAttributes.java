package com.mifan.registry;

import com.mifan.corpsecampus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public final class ModAttributes {
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES,
            corpsecampus.MODID);

    public static final RegistryObject<Attribute> XUJING_SPELL_POWER = register("xujing_spell_power");
    public static final RegistryObject<Attribute> RIZHAO_SPELL_POWER = register("rizhao_spell_power");
    public static final RegistryObject<Attribute> DONGYUE_SPELL_POWER = register("dongyue_spell_power");
    public static final RegistryObject<Attribute> YUZHE_SPELL_POWER = register("yuzhe_spell_power");
    public static final RegistryObject<Attribute> SHENGQI_SPELL_POWER = register("shengqi_spell_power");

    private ModAttributes() {
    }

    public static void register(IEventBus eventBus) {
        ATTRIBUTES.register(eventBus);
        eventBus.addListener(ModAttributes::onEntityAttributeModification);
    }

    @Nullable
    public static Attribute getSpellPowerAttribute(ResourceLocation schoolId) {
        return switch (schoolId.getPath()) {
            case "xujing" -> XUJING_SPELL_POWER.get();
            case "rizhao" -> RIZHAO_SPELL_POWER.get();
            case "dongyue" -> DONGYUE_SPELL_POWER.get();
            case "yuzhe" -> YUZHE_SPELL_POWER.get();
            case "shengqi" -> SHENGQI_SPELL_POWER.get();
            default -> null;
        };
    }

    private static RegistryObject<Attribute> register(String name) {
        return ATTRIBUTES.register(name,
                () -> new RangedAttribute("attribute.name." + corpsecampus.MODID + "." + name, 0.0D, 0.0D, 1024.0D)
                        .setSyncable(true));
    }

    private static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, XUJING_SPELL_POWER.get());
        event.add(EntityType.PLAYER, RIZHAO_SPELL_POWER.get());
        event.add(EntityType.PLAYER, DONGYUE_SPELL_POWER.get());
        event.add(EntityType.PLAYER, YUZHE_SPELL_POWER.get());
        event.add(EntityType.PLAYER, SHENGQI_SPELL_POWER.get());
    }
}
