package com.mifan.spell;

import com.mifan.corpsecampus;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public final class SonicSenseClientHandler {
    private static final ResourceLocation SONIC_ECHO_TEXTURE = ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID,
            "textures/gui/sound_gui.png");
    private static final int SONIC_ECHO_FRAME_WIDTH = 32;
    private static final int SONIC_ECHO_FRAME_HEIGHT = 32;
    private static final int SONIC_ECHO_FRAME_COUNT = 5;
    private static final int SONIC_ECHO_FRAME_DURATION = 10;
    private static final double SONIC_NEAR_SOUND_MUTE_RADIUS = 1.5D;
    private static final double SONIC_FRONT_DOT_THRESHOLD = 0.05D;

    private static boolean sonicListening;
    private static boolean sonicListeningActive;
    private static long sonicListenStartTime;
    private static int sonicHudTick;

    private SonicSenseClientHandler() {
    }

    public static void tick(Player player, long gameTime) {
        if (!hasSonicSense(player)) {
            sonicListening = false;
            sonicListeningActive = false;
            sonicListenStartTime = 0L;
            return;
        }

        sonicListening = true;
        sonicListeningActive = true;
        if (sonicListenStartTime == 0L) {
            sonicListenStartTime = gameTime;
        }
        sonicHudTick++;
    }

    public static void clear() {
        sonicListening = false;
        sonicListeningActive = false;
        sonicListenStartTime = 0L;
        sonicHudTick = 0;
    }

    public static boolean shouldMuteEntitySound(Player player, LivingEntity entity) {
        return hasSonicSense(player)
                && entity != null
                && player.distanceToSqr(entity) <= SONIC_NEAR_SOUND_MUTE_RADIUS * SONIC_NEAR_SOUND_MUTE_RADIUS;
    }

    public static boolean shouldMutePositionSound(Player player, Vec3 position) {
        return hasSonicSense(player)
                && player.position().distanceToSqr(position) <= SONIC_NEAR_SOUND_MUTE_RADIUS
                        * SONIC_NEAR_SOUND_MUTE_RADIUS;
    }

    public static void onComputeFogColor(net.minecraftforge.client.event.ViewportEvent.ComputeFogColor event,
            Player player) {
        if (!hasSonicSense(player)) {
            return;
        }

        event.setRed(event.getRed() * 0.95F);
        event.setGreen(event.getGreen() * 0.95F);
        event.setBlue(event.getBlue() * 0.95F);
    }

    public static void onRenderFog(net.minecraftforge.client.event.ViewportEvent.RenderFog event, Player player) {
        if (!hasSonicSense(player)) {
            return;
        }

        event.scaleFarPlaneDistance(0.98F);
        event.scaleNearPlaneDistance(1.0F);
    }

    public static void renderOverlay(GuiGraphics guiGraphics, Player player, int width, int height, long gameTime) {
        if (!hasSonicSense(player)) {
            return;
        }

        drawDarkOverlay(guiGraphics, width, height, gameTime);
    }

    private static void drawDarkOverlay(GuiGraphics guiGraphics, int width, int height, long gameTime) {
        int veilAlpha = 4 + (int) (Math.sin(gameTime * 0.05D) * 1.0D);
        guiGraphics.fill(0, 0, width, height, (veilAlpha << 24) | 0x050608);

        int vignetteAlpha = 1 + (int) (Math.sin(gameTime * 0.08D) * 1.0D);
        guiGraphics.fill(0, 0, width, 20, (vignetteAlpha << 24) | 0x0D1318);
        guiGraphics.fill(0, height - 20, width, height, (vignetteAlpha << 24) | 0x0D1318);
        guiGraphics.fill(0, 0, 14, height, (vignetteAlpha << 24) | 0x0D1318);
        guiGraphics.fill(width - 14, 0, width, height, (vignetteAlpha << 24) | 0x0D1318);
    }

    public static void renderLevel(PoseStack poseStack, Camera camera, Player player, long gameTime,
            float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        Vec3 playerPos = player.getPosition(partialTick);
        int frameIndex = (sonicHudTick / SONIC_ECHO_FRAME_DURATION) % SONIC_ECHO_FRAME_COUNT;
        int spellLevel = getEffectLevel(player.getEffect(ModMobEffects.SONIC_ATTUNEMENT.get()));
        double revealRange = getRevealRange(spellLevel);
        double revealRangeSqr = revealRange * revealRange;
        AABB revealBox = player.getBoundingBox().inflate(revealRange, 6.0D, revealRange);
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, revealBox,
                target -> target != player && target.isAlive() && player.distanceToSqr(target) <= revealRangeSqr);

        if (nearbyEntities.isEmpty()) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SONIC_ECHO_TEXTURE);

        Vec3 cameraPos = camera.getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (LivingEntity target : nearbyEntities) {
            Vec3 markerPos = getEyeBasedMarkerPos(target, partialTick);
            if (markerPos.distanceToSqr(cameraPos) > revealRangeSqr * 1.5D) {
                continue;
            }
            if (!isInFrontOfCamera(camera, cameraPos, markerPos)) {
                continue;
            }

            double distance = playerPos.distanceTo(markerPos);
            float distanceScale = distance <= 0.1D
                    ? 1.35F
                    : 0.55F + 0.9F * (float) Math.pow(Math.max(0.0D, 1.0D - distance / 28.0D), 1.2D);
            float pulse = 0.9F + 0.2F * (float) Math.sin((gameTime + target.getId() * 7L) * 0.18D);
            float pulseScale = 1.0F + pulse * 0.35F;
            float renderScale = distanceScale * pulseScale;
            int alpha = Mth.clamp((int) ((0.78F + pulse * 0.22F) * 255.0F), 180, 255);

            addBillboardQuad(bufferBuilder, matrix, camera,
                    markerPos.x,
                    markerPos.y,
                    markerPos.z,
                    0.26F * renderScale,
                    frameIndex,
                    alpha);
        }

        BufferUploader.drawWithShader(bufferBuilder.end());
        poseStack.popPose();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void addBillboardQuad(BufferBuilder bufferBuilder, Matrix4f matrix, Camera camera,
            double centerX, double centerY, double centerZ, float scale, int frameIndex, int alpha) {
        org.joml.Vector3f leftVector = camera.getLeftVector();
        org.joml.Vector3f upVector = camera.getUpVector();
        Vec3 left = new Vec3(leftVector.x() * scale, leftVector.y() * scale, leftVector.z() * scale);
        Vec3 up = new Vec3(upVector.x() * scale, upVector.y() * scale, upVector.z() * scale);

        float minU = (frameIndex * SONIC_ECHO_FRAME_WIDTH) / (float) (SONIC_ECHO_FRAME_WIDTH * SONIC_ECHO_FRAME_COUNT);
        float maxU = ((frameIndex + 1) * SONIC_ECHO_FRAME_WIDTH)
                / (float) (SONIC_ECHO_FRAME_WIDTH * SONIC_ECHO_FRAME_COUNT);
        float minV = 0.0F;
        float maxV = 1.0F;

        float x1 = (float) (centerX - left.x - up.x);
        float y1 = (float) (centerY - left.y - up.y);
        float z1 = (float) (centerZ - left.z - up.z);
        float x2 = (float) (centerX - left.x + up.x);
        float y2 = (float) (centerY - left.y + up.y);
        float z2 = (float) (centerZ - left.z + up.z);
        float x3 = (float) (centerX + left.x + up.x);
        float y3 = (float) (centerY + left.y + up.y);
        float z3 = (float) (centerZ + left.z + up.z);
        float x4 = (float) (centerX + left.x - up.x);
        float y4 = (float) (centerY + left.y - up.y);
        float z4 = (float) (centerZ + left.z - up.z);

        bufferBuilder.vertex(matrix, x1, y1, z1).uv(minU, maxV).color(255, 255, 255, alpha).endVertex();
        bufferBuilder.vertex(matrix, x2, y2, z2).uv(minU, minV).color(255, 255, 255, alpha).endVertex();
        bufferBuilder.vertex(matrix, x3, y3, z3).uv(maxU, minV).color(255, 255, 255, alpha).endVertex();
        bufferBuilder.vertex(matrix, x4, y4, z4).uv(maxU, maxV).color(255, 255, 255, alpha).endVertex();
    }

    private static Vec3 getEyeBasedMarkerPos(LivingEntity target, float partialTick) {
        Vec3 eyePos = target.getEyePosition(partialTick);
        double downwardOffset = Math.min(0.22D, target.getBbHeight() * 0.18D);
        return eyePos.add(0.0D, -downwardOffset, 0.0D);
    }

    private static boolean isInFrontOfCamera(Camera camera, Vec3 cameraPos, Vec3 markerPos) {
        Vec3 toMarker = markerPos.subtract(cameraPos);
        if (toMarker.lengthSqr() < 1.0E-6D) {
            return true;
        }

        org.joml.Vector3f leftVector = camera.getLeftVector();
        org.joml.Vector3f upVector = camera.getUpVector();
        Vec3 cameraForward = new Vec3(upVector.x(), upVector.y(), upVector.z())
                .cross(new Vec3(leftVector.x(), leftVector.y(), leftVector.z()))
                .normalize();

        return toMarker.normalize().dot(cameraForward) > SONIC_FRONT_DOT_THRESHOLD;
    }

    private static boolean hasSonicSense(Player player) {
        return player.hasEffect(ModMobEffects.SONIC_ATTUNEMENT.get());
    }

    private static int getEffectLevel(MobEffectInstance effectInstance) {
        return effectInstance == null ? 1 : effectInstance.getAmplifier() + 1;
    }

    public static double getRevealRange(int spellLevel) {
        return 18.0D + spellLevel * 4.0D;
    }
}
