package com.mifan.registry;

import com.mifan.corpsecampus;
import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMobEffects {
    private static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,
            corpsecampus.MODID);

    public static final RegistryObject<MobEffect> SONIC_ATTUNEMENT = MOB_EFFECTS.register("sonic_attunement",
            () -> new AbilityMarkerEffect(0x24313B));

    public static final RegistryObject<MobEffect> DANGER_SENSE = MOB_EFFECTS.register("danger_sense",
            () -> new AbilityMarkerEffect(0x9B1C27));

    public static final RegistryObject<MobEffect> OLFACTION = MOB_EFFECTS.register("olfaction",
            () -> new AbilityMarkerEffect(0x9E2230));

    public static final RegistryObject<MobEffect> ELEMENTAL_DOMAIN = MOB_EFFECTS.register("elemental_domain",
            () -> new AbilityMarkerEffect(0x6A8BFF));

    public static final RegistryObject<MobEffect> MAGNETIC_CLING = MOB_EFFECTS.register("magnetic_cling",
            () -> new AbilityMarkerEffect(0x5E4CB3));

    public static final RegistryObject<MobEffect> STAMINA = MOB_EFFECTS.register("stamina",
            () -> new AbilityMarkerEffect(0x7FCF8A));

    public static final RegistryObject<MobEffect> INSTINCT = MOB_EFFECTS.register("instinct",
            () -> new AbilityMarkerEffect(0x7CA76E));

    public static final RegistryObject<MobEffect> MANIA = MOB_EFFECTS.register("mania",
            () -> new AbilityMarkerEffect(0xB13B3B));

    public static final RegistryObject<MobEffect> NECROTIC_REBIRTH_ARMED = MOB_EFFECTS.register("necrotic_rebirth_armed",
            () -> new AbilityMarkerEffect(0x4F5B44));

    public static final RegistryObject<MobEffect> NECROTIC_UNDEAD = MOB_EFFECTS.register("necrotic_undead",
            () -> new AbilityMarkerEffect(0x6B8A5A));

    public static final RegistryObject<MobEffect> LIFE_THIEF = MOB_EFFECTS.register("life_thief",
            () -> new AbilityMarkerEffect(0x6C2A8A));

    public static final RegistryObject<MobEffect> MIMIC = MOB_EFFECTS.register("mimic",
            () -> new AbilityMarkerEffect(0x9B6BCC));

    public static final RegistryObject<MobEffect> IMPERMANENCE_MONK = MOB_EFFECTS.register("impermanence_monk",
            () -> new AbilityMarkerEffect(0x3D7A5A));

    public static final RegistryObject<MobEffect> IMPERMANENCE_MONK_INFECTED = MOB_EFFECTS.register("impermanence_monk_infected",
            () -> new AbilityMarkerEffect(0x2E5E3A));

    public static final RegistryObject<MobEffect> NINGHE = MOB_EFFECTS.register("ninghe",
            () -> new AbilityMarkerEffect(0xDFC36A));

    public static final RegistryObject<MobEffect> SUNLIGHT = MOB_EFFECTS.register("sunlight",
            () -> new AbilityMarkerEffect(0xFFC542));

    public static final RegistryObject<MobEffect> SUNLIGHT_NEUTRALIZED = MOB_EFFECTS.register("sunlight_neutralized",
            () -> new AbilityMarkerEffect(0xFFE880));

    public static final RegistryObject<MobEffect> LIGHT_PRAYER = MOB_EFFECTS.register("light_prayer",
            LightPrayerEffect::new);

    private ModMobEffects() {
    }

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }

    private static final class AbilityMarkerEffect extends MobEffect {
        private AbilityMarkerEffect(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return false;
        }
    }

    private static final class LightPrayerEffect extends MobEffect {
        private LightPrayerEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xFFE066);
            this.addAttributeModifier(
                    AttributeRegistry.SPELL_RESIST.get(),
                    AbilityRuntime.LIGHT_PRAYER_SPELL_RESIST_UUID,
                    AbilityRuntime.LIGHT_PRAYER_SPELL_RESIST_BONUS,
                    AttributeModifier.Operation.MULTIPLY_BASE);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return false;
        }
    }
}
