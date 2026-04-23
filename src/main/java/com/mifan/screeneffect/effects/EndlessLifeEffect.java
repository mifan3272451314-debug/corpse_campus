package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.resources.ResourceLocation;

public class EndlessLifeEffect extends SpellScreenEffect {

    public EndlessLifeEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "endless_life"),
                160, 20, 40);
    }

    @Override
    public void renderOverlay(EffectContext ctx) {
        // TODO shengqi/生生不息: 翠绿+嫩金 脉动光晕、藤蔓生长、花瓣粒子
    }
}
