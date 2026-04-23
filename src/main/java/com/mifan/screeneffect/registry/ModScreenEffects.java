package com.mifan.screeneffect.registry;

import com.mifan.screeneffect.api.SpellScreenEffect;
import com.mifan.screeneffect.effects.AuthorityGraspEffect;
import com.mifan.screeneffect.effects.EndlessLifeEffect;
import com.mifan.screeneffect.effects.GoldenCrowSunEffect;
import com.mifan.screeneffect.effects.GreatNecromancerEffect;
import com.mifan.screeneffect.effects.RewindWormEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ModScreenEffects {
    private static final Map<ResourceLocation, SpellScreenEffect> REGISTRY = new HashMap<>();

    public static final EndlessLifeEffect ENDLESS_LIFE = register(new EndlessLifeEffect());
    public static final GoldenCrowSunEffect GOLDEN_CROW_SUN = register(new GoldenCrowSunEffect());
    public static final GreatNecromancerEffect GREAT_NECROMANCER = register(new GreatNecromancerEffect());
    public static final AuthorityGraspEffect AUTHORITY_GRASP = register(new AuthorityGraspEffect());
    public static final RewindWormEffect REWIND_WORM = register(new RewindWormEffect());

    private ModScreenEffects() {
    }

    private static <T extends SpellScreenEffect> T register(T effect) {
        REGISTRY.put(effect.getSpellId(), effect);
        return effect;
    }

    public static SpellScreenEffect get(ResourceLocation spellId) {
        return REGISTRY.get(spellId);
    }

    public static Map<ResourceLocation, SpellScreenEffect> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    public static void bootstrap() {
        // 触发静态初始化；corpsecampus#commonSetup 里调用一次即可
    }
}
