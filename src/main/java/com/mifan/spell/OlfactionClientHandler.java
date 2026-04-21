package com.mifan.spell;

import com.mifan.network.clientbound.OlfactionTrailSyncPacket;
import com.mifan.registry.ModMobEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class OlfactionClientHandler {
    private static final float FOOTPRINT_HALF_WIDTH = 0.075F;
    private static final float FOOTPRINT_SOLE_LENGTH = 0.18F;
    private static final float FOOTPRINT_TOE_SIZE = 0.045F;
    private static final long FOOTPRINT_DURATION_TICKS = 100L;

    private static final List<OlfactionFootprintParticle> FOOTPRINTS = new ArrayList<>();

    private OlfactionClientHandler() {
    }

    public static void tick(Player player, ClientLevel level, long gameTime) {
        if (!hasOlfaction(player)) {
            FOOTPRINTS.clear();
        }
    }

    public static void cleanupExpired(long gameTime) {
        FOOTPRINTS.removeIf(footprint -> footprint.expireAt <= gameTime);
    }

    public static void clear() {
        FOOTPRINTS.clear();
    }

    public static void handleTrailSync(OlfactionTrailSyncPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        long gameTime = level.getGameTime();
        FOOTPRINTS.clear();
        for (OlfactionTrailSyncPacket.Entry entry : packet.getEntries()) {
            FOOTPRINTS.add(new OlfactionFootprintParticle(
                    entry.getEntityId(),
                    entry.getStepIndex(),
                    entry.getX(),
                    entry.getY(),
                    entry.getZ(),
                    gameTime + entry.getRemainingTicks()));
        }
    }

    public static boolean shouldMuteEntitySound(Player player, LivingEntity entity) {
        return hasOlfaction(player)
                && entity != null
                && player.distanceToSqr(entity) <= AbilityRuntime.getOlfactionSilenceRadius()
                        * AbilityRuntime.getOlfactionSilenceRadius();
    }

    public static boolean shouldMutePositionSound(Player player, Vec3 position) {
        return hasOlfaction(player)
                && player.position().distanceToSqr(position) <= AbilityRuntime.getOlfactionSilenceRadius()
                        * AbilityRuntime.getOlfactionSilenceRadius();
    }

    public static void renderLevel(PoseStack poseStack, Camera camera, long gameTime) {
        if (FOOTPRINTS.isEmpty()) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Iterator<OlfactionFootprintParticle> iterator = FOOTPRINTS.iterator();
        while (iterator.hasNext()) {
            OlfactionFootprintParticle footprint = iterator.next();
            if (footprint.expireAt <= gameTime) {
                iterator.remove();
                continue;
            }

            float lifeRatio = (float) (footprint.expireAt - gameTime) / (float) FOOTPRINT_DURATION_TICKS;
            float alpha = Mth.clamp(0.38F + lifeRatio * 0.45F, 0.38F, 0.9F);
            float yaw = ((footprint.entityId + footprint.stepIndex) & 1) == 0 ? 35.0F : -35.0F;
            float lateralOffset = (footprint.stepIndex & 1) == 0 ? 0.09F : -0.09F;

            addFootprintQuad(bufferBuilder, matrix, footprint.x, footprint.y, footprint.z, yaw, lateralOffset,
                    1.0F, 0.22F, 0.28F, alpha);
            addFootprintQuad(bufferBuilder, matrix, footprint.x, footprint.y + 0.002D, footprint.z, yaw, lateralOffset,
                    1.0F, 0.72F, 0.76F, alpha * 0.42F);
        }

        BufferUploader.drawWithShader(bufferBuilder.end());
        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void addFootprintQuad(BufferBuilder bufferBuilder, Matrix4f matrix,
            double x, double y, double z, float yawDegrees, float lateralOffset,
            float red, float green, float blue, float alpha) {
        float radians = yawDegrees * Mth.DEG_TO_RAD;
        float cos = Mth.cos(radians);
        float sin = Mth.sin(radians);

        double centerX = x + cos * lateralOffset;
        double centerZ = z + sin * lateralOffset;
        double renderY = y + 0.02D;

        addRotatedQuad(bufferBuilder, matrix, centerX, renderY, centerZ,
                FOOTPRINT_HALF_WIDTH, FOOTPRINT_SOLE_LENGTH, cos, sin,
                red, green, blue, alpha);

        addRotatedQuad(bufferBuilder, matrix,
                centerX + cos * 0.035D - sin * 0.045D,
                renderY + 0.001D,
                centerZ + sin * 0.035D + cos * 0.045D,
                FOOTPRINT_TOE_SIZE, FOOTPRINT_TOE_SIZE, cos, sin,
                red, green, blue, alpha * 0.95F);
        addRotatedQuad(bufferBuilder, matrix,
                centerX + cos * 0.065D,
                renderY + 0.001D,
                centerZ + sin * 0.065D,
                FOOTPRINT_TOE_SIZE, FOOTPRINT_TOE_SIZE, cos, sin,
                red, green, blue, alpha);
        addRotatedQuad(bufferBuilder, matrix,
                centerX + cos * 0.035D + sin * 0.045D,
                renderY + 0.001D,
                centerZ + sin * 0.035D - cos * 0.045D,
                FOOTPRINT_TOE_SIZE, FOOTPRINT_TOE_SIZE, cos, sin,
                red, green, blue, alpha * 0.95F);
    }

    private static void addRotatedQuad(BufferBuilder bufferBuilder, Matrix4f matrix,
            double centerX, double y, double centerZ, float halfWidth, float halfLength,
            float forwardX, float forwardZ, float red, float green, float blue, float alpha) {
        float sideX = -forwardZ;
        float sideZ = forwardX;

        float x1 = (float) (centerX - sideX * halfWidth - forwardX * halfLength);
        float z1 = (float) (centerZ - sideZ * halfWidth - forwardZ * halfLength);
        float x2 = (float) (centerX - sideX * halfWidth + forwardX * halfLength);
        float z2 = (float) (centerZ - sideZ * halfWidth + forwardZ * halfLength);
        float x3 = (float) (centerX + sideX * halfWidth + forwardX * halfLength);
        float z3 = (float) (centerZ + sideZ * halfWidth + forwardZ * halfLength);
        float x4 = (float) (centerX + sideX * halfWidth - forwardX * halfLength);
        float z4 = (float) (centerZ + sideZ * halfWidth - forwardZ * halfLength);
        float renderY = (float) y;

        bufferBuilder.vertex(matrix, x1, renderY, z1).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, x2, renderY, z2).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, x3, renderY, z3).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, x4, renderY, z4).color(red, green, blue, alpha).endVertex();
    }

    private static boolean hasOlfaction(Player player) {
        return player.hasEffect(ModMobEffects.OLFACTION.get());
    }

    private static final class OlfactionFootprintParticle {
        private final int entityId;
        private final int stepIndex;
        private final double x;
        private final double y;
        private final double z;
        private final long expireAt;

        private OlfactionFootprintParticle(int entityId, int stepIndex, double x, double y, double z, long expireAt) {
            this.entityId = entityId;
            this.stepIndex = stepIndex;
            this.x = x;
            this.y = y;
            this.z = z;
            this.expireAt = expireAt;
        }
    }
}
