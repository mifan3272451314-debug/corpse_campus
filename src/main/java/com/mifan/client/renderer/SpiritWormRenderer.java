package com.mifan.client.renderer;

import com.mifan.entity.SpiritWormEntity;
import net.minecraft.client.model.SilverfishModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SpiritWormRenderer extends MobRenderer<SpiritWormEntity, SilverfishModel<SpiritWormEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/silverfish.png");

    public SpiritWormRenderer(EntityRendererProvider.Context context) {
        super(context, new SilverfishModel<>(context.bakeLayer(ModelLayers.SILVERFISH)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritWormEntity entity) {
        return TEXTURE;
    }

    @Override
    protected float getFlipDegrees(SpiritWormEntity entity) {
        return 0.0F;
    }
}
