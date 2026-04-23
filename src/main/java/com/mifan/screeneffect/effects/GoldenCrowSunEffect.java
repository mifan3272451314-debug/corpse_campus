package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.resources.ResourceLocation;

public class GoldenCrowSunEffect extends SpellScreenEffect {

    public GoldenCrowSunEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "golden_crow_sun"),
                80, 8, 30);
    }

    @Override
    public void renderOverlay(EffectContext ctx) {
        // TODO rizhao/日轮金乌: 炽金+朱红 日轮图腾、三足乌、过曝白闪、热浪扭曲
    }
}
