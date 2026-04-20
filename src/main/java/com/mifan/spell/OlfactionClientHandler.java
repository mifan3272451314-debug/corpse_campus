package com.mifan.spell;

import com.mifan.registry.ModMobEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class OlfactionClientHandler {
    private static final float FOOTPRINT_HALF_WIDTH = 0.075F;
    private static final float FOOTPRINT_SOLE_LENGTH = 0.18F;
    private static final float FOOTPRINT_TOE_SIZE = 0.045F;
    private static final long FOOTPRINT_DURATION_TICKS = 300L;
    private static final long FOOTPRINT_SPAWN_INTERVAL_TICKS = 6L;
    private static final double FOOTPRINT_MIN_MOVEMENT_SQR = 0.0004D;

    private static final List<OlfactionFootprintParticle> FOOTPRINTS = new ArrayList<>();

    private OlfactionClientHandler() {
    }

    public static void tick(Player player, ClientLevel level, long gameTime) {
        if (!hasOlfaction(player)) {
            FOOTPRINTS.clear();
            return;
        }

        if (gameTime % 3L != 0L) {
            return;
        }

        MobEffectInstance effectInstance = player.getEffect(ModMobEffects.OLFACTION.get());
        int spellLevel = getEffectLevel(effectInstance);
        double radius = AbilityRuntime.getOlfactionTrackRange(spellLevel);
        AABB box = player.getBoundingBox().inflate(radius, 6.0D, radius);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                target -> target != player && target.isAlive() && isTrackable(target))) {
            captureFootprint(entity, gameTime);
        }
    }

    public static void cleanupExpired(long gameTime) {
        FOOTPRINTS.removeIf(footprint -> footprint.expireAt <= gameTime);
    }

    public static void clear() {
        FOOTPRINTS.clear();
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
            float yaw = ((footprint.entityId + footprint.createdAt) & 1L) == 0L ? 35.0F : -35.0F;
            float lateralOffset = ((footprint.createdAt / 3L) & 1L) == 0L ? 0.09F : -0.09F;

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

    private static void captureFootprint(LivingEntity entity, long gameTime) {
        double motion = entity.position().distanceToSqr(new Vec3(entity.xo, entity.yo, entity.zo));
        if (motion < FOOTPRINT_MIN_MOVEMENT_SQR) {
            return;
        }

        long lastSpawnTime = Long.MIN_VALUE;
        for (int i = FOOTPRINTS.size() - 1; i >= 0; i--) {
            OlfactionFootprintParticle footprint = FOOTPRINTS.get(i);
            if (footprint.entityId == entity.getId()) {
                lastSpawnTime = footprint.createdAt;
                break;
            }
        }

        if (gameTime - lastSpawnTime < FOOTPRINT_SPAWN_INTERVAL_TICKS) {
            return;
        }

        double progress = ((gameTime / 3L) % 2L == 0L) ? 0.25D : 0.75D;
        double x = Mth.lerp(progress, entity.xo, entity.getX());
        double z = Mth.lerp(progress, entity.zo, entity.getZ());
        double y = entity.getY() + 0.06D;

        FOOTPRINTS.add(new OlfactionFootprintParticle(entity.getId(), x, y, z,
                gameTime + FOOTPRINT_DURATION_TICKS, gameTime));
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

    private static boolean isTrackable(LivingEntity entity) {
        return entity.getMaxHealth() > 0.0F && entity.getHealth() / entity.getMaxHealth() < 0.75F;
    }

    private static int getEffectLevel(MobEffectInstance effectInstance) {
        return effectInstance == null ? 1 : effectInstance.getAmplifier() + 1;
    }

    private static final class OlfactionFootprintParticle {
        private final int entityId;
        private final double x;
        private final double y;
        private final double z;
        private final long expireAt;
        private final long createdAt;

        private OlfactionFootprintParticle(int entityId, double x, double y, double z, long expireAt, long createdAt) {
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.expireAt = expireAt;
            this.createdAt = createdAt;
        }
    }
}
