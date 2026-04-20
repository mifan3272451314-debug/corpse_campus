package com.mifan.spell;

import com.mifan.corpsecampus;
import com.mifan.registry.ModMobEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public final class SonicSenseClientHandler {
    private static final ResourceLocation SONIC_ECHO_TEXTURE = ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID,
            "textures/gui/sound_gui.png");
    private static final int SONIC_ECHO_FRAME_WIDTH = 32;
    private static final int SONIC_ECHO_FRAME_HEIGHT = 32;
    private static final int SONIC_ECHO_FRAME_COUNT = 5;
    private static final int SONIC_ECHO_FRAME_DURATION = 10;
    private static final double SONIC_NEAR_SOUND_MUTE_RADIUS = 1.5D;

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
        drawEntitiesOnScreen(guiGraphics, player, gameTime);
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

    private static void drawEntitiesOnScreen(GuiGraphics guiGraphics, Player player, long gameTime) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        Vec3 playerPos = player.getPosition(1.0F);
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
        PoseStack poseStack = guiGraphics.pose();

        for (LivingEntity target : nearbyEntities) {
            Vec3 markerPos = target.getBoundingBox().getCenter().add(0.0D, target.getBbHeight() * 0.15D, 0.0D);
            Vector3f screenPos = worldToScreen(markerPos.x, markerPos.y, markerPos.z);
            if (Float.isNaN(screenPos.x()) || screenPos.z() < -1.0F || screenPos.z() > 1.0F) {
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
            int drawX = Mth.floor(screenPos.x());
            int drawY = Mth.floor(screenPos.y());

            poseStack.pushPose();
            poseStack.translate(drawX, drawY, 0.0F);
            poseStack.scale(renderScale, renderScale, 1.0F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / 255.0F);
            guiGraphics.blit(
                    SONIC_ECHO_TEXTURE,
                    -SONIC_ECHO_FRAME_WIDTH / 2,
                    -SONIC_ECHO_FRAME_HEIGHT / 2,
                    frameIndex * SONIC_ECHO_FRAME_WIDTH,
                    0,
                    SONIC_ECHO_FRAME_WIDTH,
                    SONIC_ECHO_FRAME_HEIGHT,
                    SONIC_ECHO_FRAME_WIDTH * SONIC_ECHO_FRAME_COUNT,
                    SONIC_ECHO_FRAME_HEIGHT);
            poseStack.popPose();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static Vector3f worldToScreen(double worldX, double worldY, double worldZ) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        float x = (float) (worldX - cameraPos.x);
        float y = (float) (worldY - cameraPos.y);
        float z = (float) (worldZ - cameraPos.z);

        Quaternionf rotation = camera.rotation().conjugate(new Quaternionf());
        Vector3f local = new Vector3f(x, y, z);
        rotation.transform(local);

        Matrix4f projection = minecraft.gameRenderer.getProjectionMatrix(minecraft.options.fov().get());
        Vector4f clip = new Vector4f(local.x(), local.y(), local.z(), 1.0F);
        clip.mul(projection);

        if (clip.w <= 0.0F) {
            return new Vector3f(Float.NaN, Float.NaN, -1.0F);
        }

        clip.div(clip.w);

        int windowWidth = minecraft.getWindow().getWidth();
        int windowHeight = minecraft.getWindow().getHeight();
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        float screenX = ((clip.x * 0.5F + 0.5F) * windowWidth) / guiScale;
        float screenY = ((clip.y * -0.5F + 0.5F) * windowHeight) / guiScale;
        return new Vector3f(screenX, screenY, clip.z);
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