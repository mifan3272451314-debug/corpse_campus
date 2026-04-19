package com.mifan.spell;

import com.mifan.corpsecampus;
import com.mifan.client.screen.DominanceTargetScreen;
import com.mifan.client.screen.MidasTouchTimerScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mifan.network.clientbound.DangerSensePingPacket;
import com.mifan.network.clientbound.InstinctProcPacket;
import com.mifan.network.clientbound.OpenMidasTouchScreenPacket;
import com.mifan.registry.ModMobEffects;
import net.minecraft.client.Camera;
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
    private static final int ELEMENTAL_DOMAIN_OVERLAY_FADE_IN_TICKS = 20;
    private static final int ELEMENTAL_DOMAIN_OVERLAY_HOLD_TICKS = 60;
    private static final int ELEMENTAL_DOMAIN_OVERLAY_FADE_OUT_TICKS = 20;
    private static final ResourceLocation SONIC_ECHO_TEXTURE = ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID,
            "textures/gui/sound_gui.png");
    private static final int SONIC_ECHO_FRAME_WIDTH = 32;
    private static final int SONIC_ECHO_FRAME_HEIGHT = 32;
    private static final int SONIC_ECHO_FRAME_COUNT = 5;
    private static final int SONIC_ECHO_FRAME_DURATION = 10;
    private static final Map<Integer, Long> DANGER_ENTITY_MARKS = new HashMap<>();
    private static final List<SoundPing> SONIC_SOUND_PINGS = new ArrayList<>();
    private static boolean sonicListening;
    private static boolean sonicListeningActive;
    private static long sonicListenStartTime;
    private static int sonicHudTick;
    private static long instinctOverlayUntil;
    private static boolean instinctLastStand;
    private static boolean elementalDomainActive;
    private static int elementalDomainTick;
    private static int elementalDomainReleaseTick;

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
        updateElementalDomainState(player);
        cleanupExpired(gameTime);
        sonicHudTick++;
    }

    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        if (hasOlfaction(player) && event.getEntity() != null
                && player.distanceToSqr(event.getEntity()) <= AbilityRuntime.getOlfactionSilenceRadius()
                        * AbilityRuntime.getOlfactionSilenceRadius()) {
            event.setNewVolume(0.0F);
        }

        if (!canProcessSonicSound(player)) {
            return;
        }

        Entity soundEntity = event.getEntity();
        if (soundEntity == null || soundEntity == player || player.level() != soundEntity.level()) {
            return;
        }

        int spellLevel = getEffectLevel(player.getEffect(ModMobEffects.SONIC_ATTUNEMENT.get()));
        double maxRange = getSonicRevealRange(spellLevel);
        if (player.distanceToSqr(soundEntity) > maxRange * maxRange) {
            return;
        }

        event.setNewVolume(Math.min(event.getNewVolume(), event.getOriginalVolume() * 0.33F));
        int durationTicks = 18 + spellLevel * 5;
        SONIC_SOUND_PINGS.add(new SoundPing(
                soundEntity.position().add(0.0D, soundEntity.getBbHeight() * 0.6D, 0.0D),
                player.level().getGameTime() + durationTicks,
                durationTicks));
    }

    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        if (hasOlfaction(player)
                && player.position().distanceToSqr(event.getPosition()) <= AbilityRuntime.getOlfactionSilenceRadius()
                        * AbilityRuntime.getOlfactionSilenceRadius()) {
            event.setNewVolume(0.0F);
        }

        if (!canProcessSonicSound(player)) {
            return;
        }

        int spellLevel = getEffectLevel(player.getEffect(ModMobEffects.SONIC_ATTUNEMENT.get()));
        double maxRange = getSonicRevealRange(spellLevel);
        if (player.position().distanceToSqr(event.getPosition()) > maxRange * maxRange) {
            return;
        }

        event.setNewVolume(Math.min(event.getNewVolume(), event.getOriginalVolume() * 0.33F));
        int durationTicks = 16 + spellLevel * 5;
        SONIC_SOUND_PINGS.add(new SoundPing(
                event.getPosition(),
                player.level().getGameTime() + durationTicks,
                durationTicks));
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        if (shouldRenderElementalDomainForPlayer(player)) {
            float pulse = 0.78F + (float) (Math.sin(player.level().getGameTime() * 0.08D) * 0.05D);
            event.setRed(event.getRed() * 0.36F * pulse);
            event.setGreen(event.getGreen() * 0.42F * pulse);
            event.setBlue(event.getBlue() * 0.58F);
        }

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

        if (shouldRenderElementalDomainForPlayer(player)) {
            event.scaleFarPlaneDistance(0.78F);
            event.scaleNearPlaneDistance(1.1F);
        }

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

        if (shouldRenderElementalDomainForPlayer(player) && isElementalDomainVisualActive()) {
            drawElementalDomainOverlay(event.getGuiGraphics(), player, width, height, gameTime);
        }

        if (hasSonicSense(player)) {
            drawDarkSonicOverlay(event.getGuiGraphics(), width, height, gameTime);
            drawSonicPingHud(event.getGuiGraphics(), player, gameTime);
        }

        if (instinctOverlayUntil > gameTime) {
            drawInstinctOverlay(event.getGuiGraphics(), width, height, gameTime);
        }

        if (!DANGER_ENTITY_MARKS.isEmpty()) {
            drawDangerOverlay(event.getGuiGraphics(), player, width, height, gameTime);
        }
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

    private static void drawElementalDomainOverlay(GuiGraphics guiGraphics, Player player, int width, int height,
            long gameTime) {
        float alphaScale = getElementalDomainOverlayStrength();
        if (alphaScale <= 0.01F) {
            return;
        }

        int veilAlpha = Mth.clamp((int) (42.0F * alphaScale), 0, 72);
        int accentAlpha = Mth.clamp((int) ((22.0F + Math.sin(gameTime * 0.09D) * 7.0D) * alphaScale), 0, 52);
        int edgeAlpha = Mth.clamp((int) ((32.0F + Math.sin(gameTime * 0.12D) * 10.0D) * alphaScale), 0, 70);

        guiGraphics.fill(0, 0, width, height, (veilAlpha << 24) | 0x0C1320);
        drawElementalVignette(guiGraphics, width, height, edgeAlpha);

        int centerX = width / 2;
        int centerY = height / 2;
        float appearProgress = Mth.clamp(elementalDomainTick / (float) ELEMENTAL_DOMAIN_OVERLAY_FADE_IN_TICKS, 0.0F, 1.0F);
        int pulseRadius = (int) ((Math.min(width, height) / 4.5F) + (Math.min(width, height) / 9.0F) * appearProgress);
        int pulseThickness = 1 + (int) appearProgress;
        int ringAlpha = Mth.clamp((int) (accentAlpha * 0.9F), 0, 60);

        drawElementalRing(guiGraphics, centerX, centerY, pulseRadius, pulseThickness, ringAlpha, gameTime);
        drawElementalText(guiGraphics, Minecraft.getInstance().font, width, height, alphaScale, gameTime);
        drawElementalCrosshair(guiGraphics, centerX, centerY, alphaScale, gameTime);

        if (elementalDomainTick >= 12 && elementalDomainTick <= 32 && elementalDomainActive) {
            int lineAlpha = Math.min(28, ringAlpha);
            guiGraphics.fill(0, 0, width, 1, (lineAlpha << 24) | 0x78D7FF);
            guiGraphics.fill(0, height - 1, width, height, (lineAlpha << 24) | 0x4E7BFF);
        }

        spawnElementalAuraParticles(player, gameTime, alphaScale);
    }

    private static void drawElementalVignette(GuiGraphics guiGraphics, int width, int height, int alpha) {
        int topColor = (alpha << 24) | 0x18304A;
        int bottomColor = ((int) (alpha * 0.72F) << 24) | 0x16283F;
        int sideColor = ((int) (alpha * 0.8F) << 24) | 0x1A3550;
        int clear = 0x00000000;
        int band = Math.max(18, Math.min(width, height) / 7);

        guiGraphics.fillGradient(0, 0, width, band, topColor, clear);
        guiGraphics.fillGradient(0, height - band, width, height, clear, bottomColor);
        guiGraphics.fillGradient(0, 0, band, height, sideColor, clear);
        guiGraphics.fillGradient(width - band, 0, width, height, clear, sideColor);
    }

    private static void drawElementalRing(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int thickness,
            int alpha, long gameTime) {
        int cyan = (alpha << 24) | 0x69D8FF;
        int blue = ((int) (alpha * 0.8F) << 24) | 0x6A8DFF;
        int orange = ((int) (alpha * 0.75F) << 24) | 0xFFAE6B;

        for (int i = 0; i < 36; i++) {
            float angle = Mth.TWO_PI * i / 36.0F + (float) (gameTime * 0.03D);
            int x = centerX + Mth.floor(Mth.cos(angle) * radius);
            int y = centerY + Mth.floor(Mth.sin(angle) * radius * 0.55F);
            int color = i % 3 == 0 ? orange : i % 2 == 0 ? blue : cyan;
            guiGraphics.fill(x - thickness, y - 1, x + thickness, y + 1, color);
        }
    }

    private static void drawElementalText(GuiGraphics guiGraphics, Font font, int width, int height, float alphaScale,
            long gameTime) {
        String line1 = "领域展开";
        String line2 = "元素使";
        int alphaMain = Mth.clamp((int) (148.0F * alphaScale), 0, 148);
        int alphaShadow = Mth.clamp((int) (92.0F * alphaScale), 0, 92);
        int centerX = width / 2;
        int line1Y = height / 2 - 42;
        int line2Y = line1Y + 28;
        int wobble = (int) (Math.sin(gameTime * 0.15D) * 1.0D);

        int width1 = font.width(line1) * 2;
        int width2 = font.width(line2) * 2;

        guiGraphics.drawString(font, line1, centerX - width1 / 2 + 1, line1Y + 1, (alphaShadow << 24) | 0x48D6FF, false);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(2.0F, 2.0F, 1.0F);
        guiGraphics.drawString(font, line1, (centerX - width1 / 2) / 2, (line1Y + wobble) / 2,
                (alphaMain << 24) | 0xF4FDFF, false);
        guiGraphics.pose().popPose();

        guiGraphics.drawString(font, line2, centerX - width2 / 2 + 1, line2Y + 1, (alphaShadow << 24) | 0x7DA6FF, false);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(2.0F, 2.0F, 1.0F);
        guiGraphics.drawString(font, line2, (centerX - width2 / 2) / 2, line2Y / 2,
                (alphaMain << 24) | 0xFFFFFF, false);
        guiGraphics.pose().popPose();
    }

    private static void drawElementalCrosshair(GuiGraphics guiGraphics, int centerX, int centerY, float alphaScale,
            long gameTime) {
        int alpha = Mth.clamp((int) (54.0F * alphaScale), 0, 54);
        int warm = (alpha << 24) | 0xFF9D5C;
        int cold = (alpha << 24) | 0x71E7FF;
        int shift = (int) (Math.sin(gameTime * 0.2D) * 2.0D);
        guiGraphics.fill(centerX - 24, centerY - 1, centerX - 7 + shift, centerY + 1, cold);
        guiGraphics.fill(centerX + 7 - shift, centerY - 1, centerX + 24, centerY + 1, warm);
        guiGraphics.fill(centerX - 1, centerY - 18, centerX + 1, centerY - 7, cold);
        guiGraphics.fill(centerX - 1, centerY + 7, centerX + 1, centerY + 18, warm);
    }

    private static void spawnElementalAuraParticles(Player player, long gameTime, float alphaScale) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || gameTime % 2L != 0L) {
            return;
        }

        double radius = 1.2D + Math.min(1.1D, elementalDomainTick / 28.0D);
        for (int i = 0; i < 3; i++) {
            double angle = gameTime * 0.16D + i * (Math.PI * 2.0D / 3.0D);
            double x = player.getX() + Math.cos(angle) * radius;
            double y = player.getY() + 0.35D + (i * 0.22D);
            double z = player.getZ() + Math.sin(angle) * radius;
            Vector3f color = i == 0 ? new Vector3f(1.0F, 0.55F, 0.24F) : i == 1 ? new Vector3f(0.42F, 0.92F, 1.0F) : new Vector3f(0.58F, 0.66F, 1.0F);
            level.addParticle(new DustParticleOptions(color, 0.72F + alphaScale * 0.3F), x, y, z, 0.0D, 0.01D, 0.0D);
            level.addParticle(ParticleTypes.END_ROD, x, y + 0.1D, z, 0.0D, 0.0D, 0.0D);
        }
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

    private static void drawSonicPingHud(GuiGraphics guiGraphics, Player player, long gameTime) {
        if (!sonicListeningActive || SONIC_SOUND_PINGS.isEmpty()) {
            return;
        }

        int frameIndex = (sonicHudTick / SONIC_ECHO_FRAME_DURATION) % SONIC_ECHO_FRAME_COUNT;
        Vec3 playerPos = player.getPosition(1.0F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        PoseStack poseStack = guiGraphics.pose();
        for (int i = SONIC_SOUND_PINGS.size() - 1; i >= 0; i--) {
            SoundPing ping = SONIC_SOUND_PINGS.get(i);
            Vector3f screen = worldToScreen(ping.position.x, ping.position.y, ping.position.z);
            if (Float.isNaN(screen.x())) {
                continue;
            }

            poseStack.pushPose();

            int x = Mth.floor(screen.x());
            int y = Mth.floor(screen.y());
            int drawX = -SONIC_ECHO_FRAME_WIDTH / 2;
            int drawY = -SONIC_ECHO_FRAME_HEIGHT / 2;

            double distance = playerPos.distanceTo(ping.position);
            float dynamicScale = distance <= 0.1D
                    ? 1.35F
                    : 0.45F + 1.1F * (float) Math.pow(Math.max(0.0D, 1.0D - distance / 28.0D), 1.45D);
            float ageProgress = 1.0F - Mth.clamp((float) (ping.expireAt - gameTime) / ping.durationTicks, 0.0F, 1.0F);
            float pulseScale = 1.0F + ageProgress * 1.15F;
            int alpha = Mth.clamp((int) ((1.08F - ageProgress * 0.38F) * 255.0F), 160, 255);

            poseStack.translate(x, y, 0.0F);
            poseStack.scale(dynamicScale * pulseScale, dynamicScale * pulseScale, 1.0F);
            RenderSystem.setShaderColor(0.86F, 0.98F, 1.0F, alpha / 255.0F);
            guiGraphics.blit(
                    SONIC_ECHO_TEXTURE,
                    drawX,
                    drawY,
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
        SONIC_SOUND_PINGS.removeIf(ping -> ping.expireAt <= gameTime);
    }

    private static void updateSonicListeningState(Player player, long gameTime) {
        if (!hasSonicSense(player)) {
            sonicListening = false;
            sonicListeningActive = false;
            sonicListenStartTime = 0L;
            return;
        }

        if (player.isCrouching()) {
            if (!sonicListening) {
                sonicListening = true;
                sonicListenStartTime = gameTime;
            }

            if (gameTime - sonicListenStartTime >= 15L) {
                sonicListeningActive = true;
            }
        } else {
            sonicListening = false;
            sonicListeningActive = false;
            sonicListenStartTime = 0L;
        }
    }

    private static void clearClientMarks() {
        DANGER_ENTITY_MARKS.clear();
        SONIC_SOUND_PINGS.clear();
        sonicListening = false;
        sonicListeningActive = false;
        sonicListenStartTime = 0L;
        sonicHudTick = 0;
        elementalDomainActive = false;
        elementalDomainTick = 0;
        elementalDomainReleaseTick = 0;
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

    private static boolean hasElementalDomain(Player player) {
        return player.hasEffect(ModMobEffects.ELEMENTAL_DOMAIN.get());
    }

    private static boolean shouldRenderElementalDomainForPlayer(Player player) {
        if (hasElementalDomain(player)) {
            return true;
        }

        if (!AbilityRuntime.hasElementalistCenter(player.getPersistentData())) {
            return isInsideElementalDomainBlocks(player);
        }

        Vec3 center = AbilityRuntime.getElementalistCenter(player);
        double radius = AbilityRuntime.getElementalistRadius();
        return player.position().distanceToSqr(center) <= radius * radius || isInsideElementalDomainBlocks(player);
    }

    private static boolean isInsideElementalDomainBlocks(Player player) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }

        net.minecraft.core.BlockPos feet = player.blockPosition();
        net.minecraft.core.BlockPos eye = net.minecraft.core.BlockPos.containing(player.getEyePosition());
        return AbilityRuntime.isElementalDomainVisualBlock(level.getBlockState(feet))
                || AbilityRuntime.isElementalDomainVisualBlock(level.getBlockState(eye));
    }

    private static boolean isElementalDomainVisualActive() {
        return elementalDomainActive || elementalDomainReleaseTick > 0;
    }

    private static void updateElementalDomainState(Player player) {
        boolean activeNow = hasElementalDomain(player);
        if (activeNow) {
            elementalDomainActive = true;
            elementalDomainTick = Math.min(elementalDomainTick + 1,
                    ELEMENTAL_DOMAIN_OVERLAY_FADE_IN_TICKS + ELEMENTAL_DOMAIN_OVERLAY_HOLD_TICKS);
            elementalDomainReleaseTick = 0;
        } else if (elementalDomainActive) {
            elementalDomainActive = false;
            elementalDomainReleaseTick = 1;
        } else if (elementalDomainReleaseTick > 0) {
            elementalDomainReleaseTick++;
            if (elementalDomainReleaseTick > ELEMENTAL_DOMAIN_OVERLAY_FADE_OUT_TICKS) {
                elementalDomainReleaseTick = 0;
                elementalDomainTick = 0;
            }
        } else {
            elementalDomainTick = 0;
        }
    }

    private static float getElementalDomainOverlayStrength() {
        if (elementalDomainActive) {
            if (elementalDomainTick <= ELEMENTAL_DOMAIN_OVERLAY_FADE_IN_TICKS) {
                return Mth.clamp(elementalDomainTick / (float) ELEMENTAL_DOMAIN_OVERLAY_FADE_IN_TICKS, 0.0F, 1.0F);
            }

            int holdTicks = elementalDomainTick - ELEMENTAL_DOMAIN_OVERLAY_FADE_IN_TICKS;
            if (holdTicks <= ELEMENTAL_DOMAIN_OVERLAY_HOLD_TICKS) {
                return 1.0F;
            }

            float fade = 1.0F - (holdTicks - ELEMENTAL_DOMAIN_OVERLAY_HOLD_TICKS)
                    / (float) ELEMENTAL_DOMAIN_OVERLAY_FADE_OUT_TICKS;
            return Mth.clamp(fade, 0.0F, 1.0F);
        }

        if (elementalDomainReleaseTick > 0) {
            return Mth.clamp(1.0F - elementalDomainReleaseTick / (float) ELEMENTAL_DOMAIN_OVERLAY_FADE_OUT_TICKS,
                    0.0F, 1.0F);
        }

        return 0.0F;
    }

    private static boolean canProcessSonicSound(Player player) {
        return hasSonicSense(player) && sonicListeningActive;
    }

    private static void spawnOlfactionTrails(Player player, ClientLevel level, long gameTime) {
        if (!hasOlfaction(player) || gameTime % 3L != 0L) {
            return;
        }

        MobEffectInstance effectInstance = player.getEffect(ModMobEffects.OLFACTION.get());
        int spellLevel = getEffectLevel(effectInstance);
        double radius = AbilityRuntime.getOlfactionTrackRange(spellLevel);
        AABB box = player.getBoundingBox().inflate(radius, 6.0D, radius);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                target -> target != player && target.isAlive() && isOlfactionTrackable(target))) {
            spawnOlfactionFootprint(level, entity, gameTime);
        }
    }

    private static void spawnOlfactionFootprint(ClientLevel level, LivingEntity entity, long gameTime) {
        double motion = entity.position().distanceToSqr(new Vec3(entity.xo, entity.yo, entity.zo));
        if (motion < 0.0025D && gameTime % 9L != 0L) {
            return;
        }

        double progress = ((gameTime / 3L) % 2L == 0L) ? 0.35D : 0.7D;
        double x = Mth.lerp(progress, entity.xo, entity.getX());
        double z = Mth.lerp(progress, entity.zo, entity.getZ());
        double y = entity.getY() + 0.03D;

        level.addParticle(new DustParticleOptions(new Vector3f(0.92F, 0.12F, 0.16F), 0.9F),
                x,
                y,
                z,
                0.0D,
                0.01D,
                0.0D);
        level.addParticle(new DustParticleOptions(new Vector3f(0.72F, 0.05F, 0.08F), 0.55F),
                x + (entity.getBbWidth() * 0.2D),
                y,
                z - (entity.getBbWidth() * 0.2D),
                0.0D,
                0.005D,
                0.0D);
    }

    private static boolean isOlfactionTrackable(LivingEntity entity) {
        return entity.getMaxHealth() > 0.0F && entity.getHealth() / entity.getMaxHealth() < 0.75F;
    }

    private static int getEffectLevel(MobEffectInstance effectInstance) {
        return effectInstance == null ? 1 : effectInstance.getAmplifier() + 1;
    }

    private static double getSonicRevealRange(int spellLevel) {
        return 18.0D + spellLevel * 4.0D;
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

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        float screenX = (clip.x * 0.5F + 0.5F) * screenWidth;
        float screenY = (clip.y * -0.5F + 0.5F) * screenHeight;
        return new Vector3f(screenX, screenY, clip.z);
    }

    private record SoundPing(Vec3 position, long expireAt, long durationTicks) {
    }
}
