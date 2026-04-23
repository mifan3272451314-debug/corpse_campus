package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.resources.ResourceLocation;

public class AuthorityGraspEffect extends SpellScreenEffect {

    public AuthorityGraspEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "authority_grasp"),
                60, 6, 24);
    }

    @Override
    public void renderOverlay(EffectContext ctx) {
        // TODO yuzhe/万权一手: 皇金+赤红+白 巨掌、玉玺纹、白闪、HUD 压扁回弹
    }
}
