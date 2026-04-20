package com.mifan.spell;

import com.mifan.corpsecampus;
import com.mifan.client.screen.DominanceTargetScreen;
import com.mifan.client.screen.MidasTouchTimerScreen;
import com.mifan.client.screen.RecorderOfficerTimerScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mifan.network.clientbound.DangerSensePingPacket;
import com.mifan.network.clientbound.InstinctProcPacket;
import com.mifan.network.clientbound.OpenMidasTouchScreenPacket;
import com.mifan.network.clientbound.OpenRecorderOfficerScreenPacket;
import com.mifan.registry.ModMobEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = corpsecampus.MODID, value = Dist.CLIENT)
public final class AbilityClientHandler {
    private static final ResourceLocation SONIC_ECHO_TEXTURE = ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID,
            "textures/gui/sound_gui.png");
    private static final int SONIC_ECHO_FRAME_WIDTH = 32;
    private static final int SONIC_ECHO_FRAME_HEIGHT = 32;
    private static final int SONIC_ECHO_FRAME_COUNT = 5;
    private static final int SONIC_ECHO_FRAME_DURATION = 10;
    private static final float SONIC_WORLD_ICON_BASE_SCALE = 0.045F;
    private static final double SONIC_NEAR_SOUND_MUTE_RADIUS = 1.5D;
    private static final float OLFACTION_FOOTPRINT_HALF_WIDTH = 0.075F;
    private static final float OLFACTION_FOOTPRINT_SOLE_LENGTH = 0.18F;
    private static final float OLFACTION_FOOTPRINT_TOE_SIZE = 0.045F;
    private static final long OLFACTION_FOOTPRINT_DURATION_TICKS = 300L;
    private static final long OLFACTION_FOOTPRINT_SPAWN_INTERVAL_TICKS = 6L;
    private static final Map<Integer, Long> DANGER_ENTITY_MARKS = new HashMap<>();
    private static final List<OlfactionFootprintParticle> OLFACTION_FOOTPRINTS = new ArrayList<>();
    private static boolean sonicListening;
    private static boolean sonicListeningActive;
    private static long sonicListenStartTime;
    private static int sonicHudTick;
    private static long instinctOverlayUntil;
    private static boolean instinctLastStand;
    private static final double SONIC_MIN_ENTITY_SOUND_DISTANCE_SQR = 1.0D;

    private AbilityClientHandler() {
    }

    public static void openDominanceTargetScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new DominanceTargetScreen());
    }

    public static void openMidasTouchTimerScreen(OpenMidasTouchScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new MidasTouchTimerScreen(
                packet.getSpellLevel(),
                packet.getDefaultSeconds(),
                packet.getMinSeconds(),
                packet.getMaxSeconds(),
                packet.getDefaultPowerLevel(),
                packet.getMinPowerLevel(),
                packet.getMaxPowerLevel()));
    }

    public static void openRecorderOfficerScreen(OpenRecorderOfficerScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new RecorderOfficerTimerScreen(
                packet.getSpellLevel(),
                packet.getTargetEntityId(),
                packet.getTargetName(),
                packet.getDefaultSeconds(),
                packet.getMinSeconds(),
                packet.getMaxSeconds()));
    }

    public static void handleDangerPing(DangerSensePingPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        long expireAt = minecraft.level.getGameTime() + packet.getDurationTicks();
        DANGER_ENTITY_MARKS.merge(packet.getEntityId(), expireAt, Math::max);

        if (packet.shouldPlayAlert() && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.09F, 1.55F);
        }
    }

    public static void handleInstinctProc(InstinctProcPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        instinctOverlayUntil = minecraft.level.getGameTime() + 24L;
        instinctLastStand = packet.isLastStand();

        minecraft.player.displayClientMessage(Component.translatable(
                packet.isLastStand()
                        ? "message.corpse_campus.instinct_last_stand"
                        : "message.corpse_campus.instinct_dodge"),
                true);

        minecraft.player.playSound(
                packet.isLastStand() ? SoundEvents.TOTEM_USE : SoundEvents.PLAYER_ATTACK_NODAMAGE,
                packet.isLastStand() ? 0.35F : 0.24F,
                packet.isLastStand() ? 1.08F : 1.45F);

        for (int i = 0; i < (packet.isLastStand() ? 18 : 12); i++) {
            double angle = i * (Math.PI * 2.0D / (packet.isLastStand() ? 18.0D : 12.0D));
            double radius = packet.isLastStand() ? 0.75D : 0.52D;
            double x = minecraft.player.getX() + Math.cos(angle) * radius;
            double y = minecraft.player.getY() + 0.9D + (i % 3) * 0.08D;
            double z = minecraft.player.getZ() + Math.sin(angle) * radius;
            minecraft.level.addParticle(
                    new DustParticleOptions(
                            packet.isLastStand() ? new Vector3f(0.86F, 1.0F, 0.72F) : new Vector3f(0.62F, 1.0F, 0.72F),
                            packet.isLastStand() ? 1.0F : 0.72F),
                    x,
                    y,
                    z,
                    0.0D,
                    0.02D,
                    0.0D);
            minecraft.level.addParticle(ParticleTypes.CRIT, x, y, z, 0.0D, 0.01D, 0.0D);
        }
    }

    private static boolean hasInstinct(Player player) {
        return player.hasEffect(ModMobEffects.INSTINCT.get());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null) {
            clearClientMarks();
            return;
        }

        long gameTime = level.getGameTime();
        updateSonicListeningState(player, gameTime);
        spawnOlfactionTrails(player, level, gameTime);
        ElementalDomainClientHandler.tick(player);
        cleanupExpired(gameTime);
        sonicHudTick++;
    }

    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        if (hasSonicSense(player) && event.getEntity() != null
                && player.distanceToSqr(event.getEntity()) <= SONIC_NEAR_SOUND_MUTE_RADIUS
                        * SONIC_NEAR_SOUND_MUTE_RADIUS) {
            event.setNewVolume(0.0F);
        }

        if (hasOlfaction(player) && event.getEntity() != null
                && player.distanceToSqr(event.getEntity()) <= AbilityRuntime.getOlfactionSilenceRadius()
                        * AbilityRuntime.getOlfactionSilenceRadius()) {
            event.setNewVolume(0.0F);
        }

    }

    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        if (hasSonicSense(player)
                && player.position().distanceToSqr(event.getPosition()) <= SONIC_NEAR_SOUND_MUTE_RADIUS
                        * SONIC_NEAR_SOUND_MUTE_RADIUS) {
            event.setNewVolume(0.0F);
        }

        if (hasOlfaction(player)
                && player.position().distanceToSqr(event.getPosition()) <= AbilityRuntime.getOlfactionSilenceRadius()
                        * AbilityRuntime.getOlfactionSilenceRadius()) {
            event.setNewVolume(0.0F);
        }

    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        ElementalDomainClientHandler.onComputeFogColor(event, player);

        if (!hasSonicSense(player)) {
            return;
        }

        event.setRed(event.getRed() * 0.95F);
        event.setGreen(event.getGreen() * 0.95F);
        event.setBlue(event.getBlue() * 0.95F);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        ElementalDomainClientHandler.onRenderFog(event, player);

        if (!hasSonicSense(player)) {
            return;
        }

        event.scaleFarPlaneDistance(0.98F);
        event.scaleNearPlaneDistance(1.0F);
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        long gameTime = player.level().getGameTime();

        ElementalDomainClientHandler.renderOverlay(event.getGuiGraphics(), player, width, height, gameTime);

        if (hasSonicSense(player)) {
            drawDarkSonicOverlay(event.getGuiGraphics(), width, height, gameTime);
            drawSonicEntitiesOnScreen(event.getGuiGraphics(), player, gameTime);
        }

        if (instinctOverlayUntil > gameTime) {
            drawInstinctOverlay(event.getGuiGraphics(), width, height, gameTime);
        }

        if (!DANGER_ENTITY_MARKS.isEmpty()) {
            drawDangerOverlay(event.getGuiGraphics(), player, width, height, gameTime);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !hasOlfaction(player) || OLFACTION_FOOTPRINTS.isEmpty()) {
            return;
        }

        renderOlfactionFootprints(event.getPoseStack(), event.getCamera(), level.getGameTime());
    }

    private static void drawInstinctOverlay(GuiGraphics guiGraphics, int width, int height, long gameTime) {
        int alpha = instinctLastStand ? 28 : 18;
        int color = instinctLastStand ? 0xA9FF8E : 0x8AF3AE;
        guiGraphics.fill(0, 0, width, 6, (alpha << 24) | color);
        guiGraphics.fill(0, height - 6, width, height, (alpha << 24) | color);

        int centerX = width / 2;
        int centerY = height / 2;
        int pulseAlpha = 24 + (int) (Math.sin(gameTime * 0.45D) * 5.0D + 5.0D);
        guiGraphics.fill(centerX - 26, centerY - 1, centerX + 26, centerY + 1, (pulseAlpha << 24) | color);
        guiGraphics.fill(centerX - 1, centerY - 26, centerX + 1, centerY + 26, (pulseAlpha << 24) | color);
    }

    private static void drawDarkSonicOverlay(GuiGraphics guiGraphics, int width, int height, long gameTime) {
        int veilAlpha = 4 + (int) (Math.sin(gameTime * 0.05D) * 1.0D);
        guiGraphics.fill(0, 0, width, height, (veilAlpha << 24) | 0x050608);

        int vignetteAlpha = 1 + (int) (Math.sin(gameTime * 0.08D) * 1.0D);
        guiGraphics.fill(0, 0, width, 20, (vignetteAlpha << 24) | 0x0D1318);
        guiGraphics.fill(0, height - 20, width, height, (vignetteAlpha << 24) | 0x0D1318);
        guiGraphics.fill(0, 0, 14, height, (vignetteAlpha << 24) | 0x0D1318);
        guiGraphics.fill(width - 14, 0, width, height, (vignetteAlpha << 24) | 0x0D1318);
    }

    private static void drawSonicEntitiesOnScreen(GuiGraphics guiGraphics, Player player, long gameTime) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        Vec3 playerPos = player.getPosition(1.0F);
        int frameIndex = (sonicHudTick / SONIC_ECHO_FRAME_DURATION) % SONIC_ECHO_FRAME_COUNT;
        int spellLevel = getEffectLevel(player.getEffect(ModMobEffects.SONIC_ATTUNEMENT.get()));
        double revealRange = getSonicRevealRange(spellLevel);
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

    private static void drawDangerOverlay(GuiGraphics guiGraphics, Player player, int width, int height,
            long gameTime) {
        int pulseAlpha = 36 + (int) (Math.sin(gameTime * 0.35D) * 10.0D + 10.0D);
        int accentAlpha = 62 + (int) (Math.sin(gameTime * 0.35D + 0.6D) * 14.0D + 14.0D);

        drawCornerBracket(guiGraphics, 18, 18, 1, 1, 26, 3, (accentAlpha << 24) | 0xF04A54);
        drawCornerBracket(guiGraphics, width - 18, 18, -1, 1, 26, 3, (accentAlpha << 24) | 0xF04A54);
        drawCornerBracket(guiGraphics, 18, height - 18, 1, -1, 26, 3, (accentAlpha << 24) | 0xF04A54);
        drawCornerBracket(guiGraphics, width - 18, height - 18, -1, -1, 26, 3, (accentAlpha << 24) | 0xF04A54);
        guiGraphics.fill(0, 0, width, 2, (pulseAlpha << 24) | 0x7A111A);
        guiGraphics.fill(0, height - 2, width, height, (pulseAlpha << 24) | 0x7A111A);

        int centerX = width / 2;
        int centerY = height / 2;
        int radius = Math.min(width, height) / 3;

        for (Map.Entry<Integer, Long> entry : DANGER_ENTITY_MARKS.entrySet()) {
            if (entry.getValue() <= gameTime) {
                continue;
            }

            Entity entity = player.level().getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isAlive()) {
                continue;
            }

            Vec3 toTarget = livingEntity.getEyePosition().subtract(player.getEyePosition());
            double targetYaw = Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0D;
            float relativeYaw = Mth.wrapDegrees((float) (targetYaw - player.getYRot()));
            double radians = Math.toRadians(relativeYaw);
            int indicatorX = centerX + (int) (Math.sin(radians) * radius);
            int indicatorY = centerY - (int) (Math.cos(radians) * radius);

            int markerAlpha = 120 + (int) (Math.sin(gameTime * 0.45D + entry.getKey()) * 18.0D + 18.0D);
            drawDangerIndicator(guiGraphics, indicatorX, indicatorY, relativeYaw,
                    (markerAlpha << 24) | 0xFF5563,
                    ((markerAlpha + 30) << 24) | 0xFFF3F4);
        }
    }

    private static void drawCornerBracket(GuiGraphics guiGraphics, int x, int y, int horizontalDir, int verticalDir,
            int length, int thickness, int color) {
        fillRect(guiGraphics, x, y, x + horizontalDir * length, y + thickness * verticalDir, color);
        fillRect(guiGraphics, x, y, x + thickness * horizontalDir, y + verticalDir * length, color);
    }

    private static void drawDangerIndicator(GuiGraphics guiGraphics, int x, int y, float relativeYaw, int outerColor,
            int innerColor) {
        fillRect(guiGraphics, x - 4, y - 4, x + 4, y + 4, outerColor);
        fillRect(guiGraphics, x - 2, y - 2, x + 2, y + 2, innerColor);

        if (Math.abs(relativeYaw) >= 45.0F && Math.abs(relativeYaw) <= 135.0F) {
            int dir = relativeYaw > 0.0F ? -1 : 1;
            fillRect(guiGraphics, x + dir * 16, y - 1, x + dir * 6, y + 1, outerColor);
            fillRect(guiGraphics, x + dir * 11, y - 3, x + dir * 8, y + 3, innerColor);
        } else {
            int dir = relativeYaw > 135.0F || relativeYaw < -135.0F ? 1 : -1;
            fillRect(guiGraphics, x - 1, y + dir * 16, x + 1, y + dir * 6, outerColor);
            fillRect(guiGraphics, x - 3, y + dir * 11, x + 3, y + dir * 8, innerColor);
        }
    }

    private static void fillRect(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        guiGraphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2), color);
    }

    private static void cleanupExpired(long gameTime) {
        DANGER_ENTITY_MARKS.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        OLFACTION_FOOTPRINTS.removeIf(footprint -> footprint.expireAt <= gameTime);
    }

    private static void updateSonicListeningState(Player player, long gameTime) {
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
    }

    private static void clearClientMarks() {
        DANGER_ENTITY_MARKS.clear();
        sonicListening = false;
        sonicListeningActive = false;
        sonicListenStartTime = 0L;
        sonicHudTick = 0;
        ElementalDomainClientHandler.clear();
    }

    private static Player localPlayer() {
        return Minecraft.getInstance().player;
    }

    private static boolean hasSonicSense(Player player) {
        return player.hasEffect(ModMobEffects.SONIC_ATTUNEMENT.get());
    }

    private static boolean hasOlfaction(Player player) {
        return player.hasEffect(ModMobEffects.OLFACTION.get());
    }

    private static void spawnOlfactionTrails(Player player, ClientLevel level, long gameTime) {
        if (!hasOlfaction(player)) {
            OLFACTION_FOOTPRINTS.clear();
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
                target -> target != player && target.isAlive() && isOlfactionTrackable(target))) {
            captureOlfactionFootprint(entity, gameTime);
        }
    }

    private static void captureOlfactionFootprint(LivingEntity entity, long gameTime) {
        double motion = entity.position().distanceToSqr(new Vec3(entity.xo, entity.yo, entity.zo));
        if (motion < 0.0025D && gameTime % 9L != 0L) {
            return;
        }

        long lastSpawnTime = Long.MIN_VALUE;
        for (int i = OLFACTION_FOOTPRINTS.size() - 1; i >= 0; i--) {
            OlfactionFootprintParticle footprint = OLFACTION_FOOTPRINTS.get(i);
            if (footprint.entityId == entity.getId()) {
                lastSpawnTime = footprint.createdAt;
                break;
            }
        }

        if (gameTime - lastSpawnTime < OLFACTION_FOOTPRINT_SPAWN_INTERVAL_TICKS) {
            return;
        }

        double progress = ((gameTime / 3L) % 2L == 0L) ? 0.35D : 0.7D;
        double x = Mth.lerp(progress, entity.xo, entity.getX());
        double z = Mth.lerp(progress, entity.zo, entity.getZ());
        double y = entity.getY() + 0.03D;

        OLFACTION_FOOTPRINTS.add(new OlfactionFootprintParticle(entity.getId(), x, y, z,
                gameTime + OLFACTION_FOOTPRINT_DURATION_TICKS, gameTime));
    }

    private static void renderOlfactionFootprints(PoseStack poseStack, Camera camera, long gameTime) {
        Vec3 cameraPos = camera.getPosition();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Iterator<OlfactionFootprintParticle> iterator = OLFACTION_FOOTPRINTS.iterator();
        while (iterator.hasNext()) {
            OlfactionFootprintParticle footprint = iterator.next();
            if (footprint.expireAt <= gameTime) {
                iterator.remove();
                continue;
            }

            float lifeRatio = (float) (footprint.expireAt - gameTime) / (float) OLFACTION_FOOTPRINT_DURATION_TICKS;
            float alpha = Mth.clamp(0.18F + lifeRatio * 0.5F, 0.18F, 0.68F);
            float yaw = ((footprint.entityId + footprint.createdAt) & 1L) == 0L ? 35.0F : -35.0F;
            float lateralOffset = ((footprint.createdAt / 3L) & 1L) == 0L ? 0.09F : -0.09F;

            addFootprintQuad(bufferBuilder, matrix, footprint.x, footprint.y, footprint.z, yaw, lateralOffset,
                    0.94F, 0.18F, 0.22F, alpha);
            addFootprintQuad(bufferBuilder, matrix, footprint.x, footprint.y + 0.002D, footprint.z, yaw, lateralOffset,
                    1.0F, 0.58F, 0.62F, alpha * 0.35F);
        }

        BufferUploader.drawWithShader(bufferBuilder.end());
        poseStack.popPose();

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
        double renderY = y + 0.01D;

        addRotatedQuad(bufferBuilder, matrix, centerX, renderY, centerZ,
                OLFACTION_FOOTPRINT_HALF_WIDTH, OLFACTION_FOOTPRINT_SOLE_LENGTH, cos, sin,
                red, green, blue, alpha);

        addRotatedQuad(bufferBuilder, matrix,
                centerX + cos * 0.035D - sin * 0.045D,
                renderY + 0.001D,
                centerZ + sin * 0.035D + cos * 0.045D,
                OLFACTION_FOOTPRINT_TOE_SIZE, OLFACTION_FOOTPRINT_TOE_SIZE, cos, sin,
                red, green, blue, alpha * 0.95F);
        addRotatedQuad(bufferBuilder, matrix,
                centerX + cos * 0.065D,
                renderY + 0.001D,
                centerZ + sin * 0.065D,
                OLFACTION_FOOTPRINT_TOE_SIZE, OLFACTION_FOOTPRINT_TOE_SIZE, cos, sin,
                red, green, blue, alpha);
        addRotatedQuad(bufferBuilder, matrix,
                centerX + cos * 0.035D + sin * 0.045D,
                renderY + 0.001D,
                centerZ + sin * 0.035D - cos * 0.045D,
                OLFACTION_FOOTPRINT_TOE_SIZE, OLFACTION_FOOTPRINT_TOE_SIZE, cos, sin,
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

    private static boolean isOlfactionTrackable(LivingEntity entity) {
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

    private static double getSonicRevealRange(int spellLevel) {
        return 18.0D + spellLevel * 4.0D;
    }

}
