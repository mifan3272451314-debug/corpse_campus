package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.resources.ResourceLocation;

public class RewindWormEffect extends SpellScreenEffect {

    public RewindWormEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "rewind_worm"),
                100, 12, 32);
    }

    @Override
    public void renderOverlay(EffectContext ctx) {
        // TODO xujing/回溯之虫: 深紫+银白 倒带扫描线、沙漏虫群、RGB 色差、残影
    }
}
