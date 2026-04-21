package com.mifan.registry;

import com.mifan.corpsecampus;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.damage.ISSDamageTypes;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSchools {
    private static final DeferredRegister<SchoolType> SCHOOLS = DeferredRegister
            .create(SchoolRegistry.SCHOOL_REGISTRY_KEY, corpsecampus.MODID);

    public static final ResourceLocation XUJING_RESOURCE = id("xujing");
    public static final ResourceLocation RIZHAO_RESOURCE = id("rizhao");
    public static final ResourceLocation DONGYUE_RESOURCE = id("dongyue");
    public static final ResourceLocation YUZHE_RESOURCE = id("yuzhe");
    public static final ResourceLocation SHENGQI_RESOURCE = id("shengqi");

    public static final RegistryObject<SchoolType> XUJING = registerSchool(
            "xujing",
            XUJING_RESOURCE,
            focus("xujing"),
            "school.corpse_campus.xujing",
            0x7A6FF0,
            ModAttributes.XUJING_SPELL_POWER,
            SoundRegistry.ENDER_CAST,
            ISSDamageTypes.ENDER_MAGIC);

    public static final RegistryObject<SchoolType> RIZHAO = registerSchool(
            "rizhao",
            RIZHAO_RESOURCE,
            focus("rizhao"),
            "school.corpse_campus.rizhao",
            0xF7C948,
            ModAttributes.RIZHAO_SPELL_POWER,
            SoundRegistry.HOLY_CAST,
            ISSDamageTypes.HOLY_MAGIC);

    public static final RegistryObject<SchoolType> DONGYUE = registerSchool(
            "dongyue",
            DONGYUE_RESOURCE,
            focus("dongyue"),
            "school.corpse_campus.dongyue",
            0x3BA56A,
            ModAttributes.DONGYUE_SPELL_POWER,
            SoundRegistry.BLOOD_CAST,
            ISSDamageTypes.BLOOD_MAGIC);

    public static final RegistryObject<SchoolType> YUZHE = registerSchool(
            "yuzhe",
            YUZHE_RESOURCE,
            focus("yuzhe"),
            "school.corpse_campus.yuzhe",
            0x8B7AA8,
            ModAttributes.YUZHE_SPELL_POWER,
            SoundRegistry.EVOCATION_CAST,
            ISSDamageTypes.ELDRITCH_MAGIC);

    public static final RegistryObject<SchoolType> SHENGQI = registerSchool(
            "shengqi",
            SHENGQI_RESOURCE,
            focus("shengqi"),
            "school.corpse_campus.shengqi",
            0xB8FFF1,
            ModAttributes.SHENGQI_SPELL_POWER,
            SoundRegistry.HOLY_CAST,
            ISSDamageTypes.HOLY_MAGIC);

    private ModSchools() {
    }

    public static void register(IEventBus eventBus) {
        SCHOOLS.register(eventBus);
    }

    private static RegistryObject<SchoolType> registerSchool(
            String name,
            ResourceLocation schoolId,
            TagKey<Item> focusTag,
            String translationKey,
            int color,
            RegistryObject<Attribute> powerAttribute,
            RegistryObject<SoundEvent> castSound,
            ResourceKey<DamageType> damageType) {
        return SCHOOLS.register(name, () -> new SchoolType(
                schoolId,
                focusTag,
                Component.translatable(translationKey).withStyle(Style.EMPTY.withColor(color)),
                attribute(powerAttribute),
                attribute(AttributeRegistry.SPELL_RESIST),
                LazyOptional.of(castSound::get),
                damageType));
    }

    private static LazyOptional<Attribute> attribute(RegistryObject<Attribute> attribute) {
        return LazyOptional.of(attribute::get);
    }

    private static TagKey<Item> focus(String name) {
        return ItemTags.create(id("focus/" + name));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, path);
    }
}
