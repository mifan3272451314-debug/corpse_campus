package com.mifan.spell;

import com.mifan.corpsecampus;
import com.mifan.client.screen.DominanceTargetScreen;
import com.mifan.client.screen.FerrymanTargetScreen;
import com.mifan.client.screen.MidasTouchTimerScreen;
import com.mifan.client.screen.MimicAbsorbScreen;
import com.mifan.client.screen.MimicReleaseScreen;
import com.mifan.client.screen.PlayerStatusScreen;
import com.mifan.client.screen.RecorderOfficerTimerScreen;
import com.mifan.network.clientbound.DangerSensePingPacket;
import com.mifan.network.clientbound.InstinctProcPacket;
import com.mifan.network.clientbound.OlfactionTrailSyncPacket;
import com.mifan.network.clientbound.OpenFerrymanScreenPacket;
import com.mifan.network.clientbound.OpenMidasTouchScreenPacket;
import com.mifan.network.clientbound.OpenMimicAbsorbScreenPacket;
import com.mifan.network.clientbound.OpenMimicReleaseScreenPacket;
import com.mifan.network.clientbound.OpenRecorderOfficerScreenPacket;
import com.mifan.registry.ModMobEffects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = corpsecampus.MODID, value = Dist.CLIENT)
public final class AbilityClientHandler {
    private static final Map<Integer, Long> DANGER_ENTITY_MARKS = new HashMap<>();
    private static long instinctOverlayUntil;
    private static boolean instinctLastStand;

    private AbilityClientHandler() {
    }

    public static void openDominanceTargetScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new DominanceTargetScreen());
    }

    public static void openFerrymanTargetScreen(OpenFerrymanScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new FerrymanTargetScreen(packet.getSpellLevel()));
    }

    public static void openPlayerStatusScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        minecraft.setScreen(new PlayerStatusScreen());
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

    public static void openMimicAbsorbScreen(OpenMimicAbsorbScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new MimicAbsorbScreen(
                packet.targetPlayerId(),
                packet.targetPlayerName(),
                packet.spellIds()));
    }

    public static void openMimicReleaseScreen(OpenMimicReleaseScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new MimicReleaseScreen(packet.entries()));
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

    public static void handleOlfactionTrailSync(OlfactionTrailSyncPacket packet) {
        OlfactionClientHandler.handleTrailSync(packet);
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
        SonicSenseClientHandler.tick(player, gameTime);
        OlfactionClientHandler.tick(player, level, gameTime);
        ElementalDomainClientHandler.tick(player);
        cleanupExpired(gameTime);
    }

    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
            if (SonicSenseClientHandler.shouldMuteEntitySound(player, livingEntity)
                    || OlfactionClientHandler.shouldMuteEntitySound(player, livingEntity)) {
                event.setNewVolume(0.0F);
            }
        }

    }

    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        if (SonicSenseClientHandler.shouldMutePositionSound(player, event.getPosition())
                || OlfactionClientHandler.shouldMutePositionSound(player, event.getPosition())) {
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

        SonicSenseClientHandler.onComputeFogColor(event, player);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Player player = localPlayer();
        if (player == null) {
            return;
        }

        ElementalDomainClientHandler.onRenderFog(event, player);

        SonicSenseClientHandler.onRenderFog(event, player);
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

        SonicSenseClientHandler.renderOverlay(event.getGuiGraphics(), player, width, height, gameTime);

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
        if (player == null || level == null) {
            return;
        }

        SonicSenseClientHandler.renderLevel(
                event.getPoseStack(),
                event.getCamera(),
                player,
                level.getGameTime(),
                event.getPartialTick());
        OlfactionClientHandler.renderLevel(event.getPoseStack(), event.getCamera(), level.getGameTime());
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
        OlfactionClientHandler.cleanupExpired(gameTime);
    }

    private static void clearClientMarks() {
        DANGER_ENTITY_MARKS.clear();
        SonicSenseClientHandler.clear();
        OlfactionClientHandler.clear();
        ElementalDomainClientHandler.clear();
    }

    private static Player localPlayer() {
        return Minecraft.getInstance().player;
    }

}
