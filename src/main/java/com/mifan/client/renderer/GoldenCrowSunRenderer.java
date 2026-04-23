package com.mifan.client.renderer;

import com.mifan.entity.GoldenCrowSunEntity;
import com.mifan.spell.AbilityRuntime;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

public class GoldenCrowSunRenderer extends EntityRenderer<GoldenCrowSunEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "corpse_campus", "textures/mob_effect/golden_crow_sun.png");

    public GoldenCrowSunRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GoldenCrowSunEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(GoldenCrowSunEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
            double x, double y, double z) {
        return true;
    }

    @Override
    public void render(GoldenCrowSunEntity entity, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        float mana = entity.getManaSpent();
        double baseOrb = AbilityRuntime.goldenCrowOrbRadius(mana);

        float ageT = (entity.tickCount + partialTick);

        // 生长：前 1/3 寿命从 0.30 长到 1.05，之后持续缓慢膨胀到 1.30
        int dur = AbilityRuntime.GOLDEN_CROW_DURATION_TICKS;
        float lifeFrac = Math.min(1.0F, ageT / (dur * 0.33F));
        float laterFrac = Mth.clamp((ageT - dur * 0.33F) / (dur * 0.67F), 0.0F, 1.0F);
        float growth = Mth.lerp(lifeFrac, 0.30F, 1.05F) + laterFrac * 0.25F;

        // 投掷阶段再放大 + 轻微"汇能压缩-放大"节奏
        float throwBoost = entity.isThrown() ? 1.22F : 1.0F;

        // 呼吸脉冲
        float breath = 0.94F + 0.06F * Mth.sin(ageT * 0.15F);

        float coreRadius = (float) baseOrb * growth * throwBoost * breath;
        float haloRadius = coreRadius * 1.9F;
        float emblemRadius = coreRadius * 1.45F;
        float flareRadius = coreRadius * 2.7F;

        // 让整体位置微微抬升（悬浮时画面重心更偏上），投掷时加一个拖影偏移
        poseStack.pushPose();

        // --- Layer 0: 外爆闪光（最外圈，极淡，投掷时明显拉远） ---
        poseStack.pushPose();
        float flareAngle = -ageT * 0.8F;
        renderBillboard(poseStack, buffer, flareRadius, flareAngle,
                0.12F, 255, 200, 120, packedLight);
        poseStack.popPose();

        // --- Layer 1: 光晕（反向自转，金白） ---
        poseStack.pushPose();
        float haloAngle = -ageT * 1.3F;
        renderBillboard(poseStack, buffer, haloRadius, haloAngle,
                0.32F, 255, 230, 140, packedLight);
        poseStack.popPose();

        // --- Layer 2: 8 芒日轮纹章（明暗相间，正向旋转） ---
        poseStack.pushPose();
        float emblemAngle = ageT * 2.8F;
        renderEmblem(poseStack, buffer, emblemRadius, emblemAngle, packedLight);
        poseStack.popPose();

        // --- Layer 3: 核心太阳本体（白金炽热） ---
        poseStack.pushPose();
        float coreAngle = ageT * 0.55F;
        renderBillboard(poseStack, buffer, coreRadius, coreAngle,
                1.0F, 255, 245, 190, packedLight);
        poseStack.popPose();

        poseStack.popPose();

        // --- Layer 4: 地面日轮（仅悬浮态） ---
        if (!entity.isThrown()) {
            Entity owner = entity.getOwner();
            if (owner instanceof LivingEntity) {
                double dx = owner.getX() - entity.getX();
                double dy = owner.getY() + 0.08D - entity.getY();
                double dz = owner.getZ() - entity.getZ();
                poseStack.pushPose();
                poseStack.translate(dx, dy, dz);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                float groundAngle = ageT * 3.5F;
                poseStack.mulPose(Axis.ZP.rotationDegrees(groundAngle));
                float groundRadius = Math.min(14.0F, coreRadius * 0.75F);
                renderGroundDisc(poseStack, buffer, groundRadius, packedLight);
                renderGroundSpokes(poseStack, buffer, groundRadius * 1.4F, packedLight);
                poseStack.popPose();
            }
        }

        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    private void renderBillboard(PoseStack poseStack, MultiBufferSource buffer, float radius,
            float rotationDeg, float alpha, int r, int g, int b, int packedLight) {
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotationDeg));

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        int po = OverlayTexture.NO_OVERLAY;

        vc.vertex(matrix, -radius, -radius, 0).color(r, g, b, a).uv(0, 0).overlayCoords(po).uv2(packedLight).normal(0, 0, 1).endVertex();
        vc.vertex(matrix,  radius, -radius, 0).color(r, g, b, a).uv(1, 0).overlayCoords(po).uv2(packedLight).normal(0, 0, 1).endVertex();
        vc.vertex(matrix,  radius,  radius, 0).color(r, g, b, a).uv(1, 1).overlayCoords(po).uv2(packedLight).normal(0, 0, 1).endVertex();
        vc.vertex(matrix, -radius,  radius, 0).color(r, g, b, a).uv(0, 1).overlayCoords(po).uv2(packedLight).normal(0, 0, 1).endVertex();
    }

    private void renderEmblem(PoseStack poseStack, MultiBufferSource buffer, float radius,
            float rotationDeg, int packedLight) {
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotationDeg));

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        int po = OverlayTexture.NO_OVERLAY;

        int spokes = 8;
        float halfWidth = (float) (Math.PI / spokes * 0.28F);

        for (int i = 0; i < spokes; i++) {
            boolean bright = (i % 2 == 0);
            int r = 255;
            int g = bright ? 240 : 170;
            int b = bright ? 110 : 50;
            int aCenter = bright ? 230 : 140;

            float ang = (float) (i * Math.PI * 2.0 / spokes);
            float angL = ang - halfWidth;
            float angR = ang + halfWidth;
            float outerX = Mth.cos(ang) * radius;
            float outerY = Mth.sin(ang) * radius;
            float innerX = Mth.cos(ang) * radius * 0.15F;
            float innerY = Mth.sin(ang) * radius * 0.15F;
            float baseLX = Mth.cos(angL) * radius * 0.18F;
            float baseLY = Mth.sin(angL) * radius * 0.18F;
            float baseRX = Mth.cos(angR) * radius * 0.18F;
            float baseRY = Mth.sin(angR) * radius * 0.18F;

            vc.vertex(matrix, innerX, innerY, 0).color(r, g, b, aCenter).uv(0.5F, 0.5F).overlayCoords(po).uv2(packedLight).normal(0, 0, 1).endVertex();
            vc.vertex(matrix, baseLX, baseLY, 0).color(r, g, b, aCenter).uv(0.0F, 0.5F).overlayCoords(po).uv2(packedLight).normal(0, 0, 1).endVertex();
            vc.vertex(matrix, outerX, outerY, 0).color(r, g, b, 0).uv(0.5F, 0.0F).overlayCoords(po).uv2(packedLight).normal(0, 0, 1).endVertex();
            vc.vertex(matrix, baseRX, baseRY, 0).color(r, g, b, aCenter).uv(1.0F, 0.5F).overlayCoords(po).uv2(packedLight).normal(0, 0, 1).endVertex();
        }
    }

    private void renderGroundDisc(PoseStack poseStack, MultiBufferSource buffer, float radius, int packedLight) {
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        int po = OverlayTexture.NO_OVERLAY;
        int r = 255, g = 220, b = 90, a = 180;

        vc.vertex(matrix, -radius, -radius, 0).color(r, g, b, a).uv(0, 0).overlayCoords(po).uv2(packedLight).normal(0, 1, 0).endVertex();
        vc.vertex(matrix,  radius, -radius, 0).color(r, g, b, a).uv(1, 0).overlayCoords(po).uv2(packedLight).normal(0, 1, 0).endVertex();
        vc.vertex(matrix,  radius,  radius, 0).color(r, g, b, a).uv(1, 1).overlayCoords(po).uv2(packedLight).normal(0, 1, 0).endVertex();
        vc.vertex(matrix, -radius,  radius, 0).color(r, g, b, a).uv(0, 1).overlayCoords(po).uv2(packedLight).normal(0, 1, 0).endVertex();
    }

    private void renderGroundSpokes(PoseStack poseStack, MultiBufferSource buffer, float radius, int packedLight) {
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        int po = OverlayTexture.NO_OVERLAY;
        int spokes = 8;
        float halfWidth = (float) (Math.PI / spokes * 0.22F);

        for (int i = 0; i < spokes; i++) {
            boolean bright = (i % 2 == 0);
            int r = 255;
            int g = bright ? 235 : 160;
            int b = bright ? 80 : 30;
            int aIn = bright ? 210 : 130;

            float ang = (float) (i * Math.PI * 2.0 / spokes);
            float angL = ang - halfWidth;
            float angR = ang + halfWidth;
            float outerX = Mth.cos(ang) * radius;
            float outerY = Mth.sin(ang) * radius;
            float innerX = Mth.cos(ang) * radius * 0.20F;
            float innerY = Mth.sin(ang) * radius * 0.20F;
            float baseLX = Mth.cos(angL) * radius * 0.22F;
            float baseLY = Mth.sin(angL) * radius * 0.22F;
            float baseRX = Mth.cos(angR) * radius * 0.22F;
            float baseRY = Mth.sin(angR) * radius * 0.22F;

            vc.vertex(matrix, innerX, innerY, 0).color(r, g, b, aIn).uv(0.5F, 0.5F).overlayCoords(po).uv2(packedLight).normal(0, 1, 0).endVertex();
            vc.vertex(matrix, baseLX, baseLY, 0).color(r, g, b, aIn).uv(0.0F, 0.5F).overlayCoords(po).uv2(packedLight).normal(0, 1, 0).endVertex();
            vc.vertex(matrix, outerX, outerY, 0).color(r, g, b, 0).uv(0.5F, 0.0F).overlayCoords(po).uv2(packedLight).normal(0, 1, 0).endVertex();
            vc.vertex(matrix, baseRX, baseRY, 0).color(r, g, b, aIn).uv(1.0F, 0.5F).overlayCoords(po).uv2(packedLight).normal(0, 1, 0).endVertex();
        }
    }
}
