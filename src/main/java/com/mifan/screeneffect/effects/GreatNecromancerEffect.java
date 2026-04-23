package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.resources.ResourceLocation;

public class GreatNecromancerEffect extends SpellScreenEffect {

    public GreatNecromancerEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "great_necromancer"),
                140, 20, 40);
    }

    @Override
    public void renderOverlay(EffectContext ctx) {
        // TODO dongyue/大冥鬼师: 幽紫+尸青+纯黑 黑雾、符箓、骷髅剪影、鬼火粒子
    }
}
