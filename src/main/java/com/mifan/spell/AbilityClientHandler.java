package com.mifan.spell;

import com.mifan.corpsecampus;
import com.mifan.client.screen.AdminPanelScreen;
import com.mifan.client.screen.DesignatedAbilityScreen;
import com.mifan.client.screen.DominanceTargetScreen;
import com.mifan.client.screen.FerrymanTargetScreen;
import com.mifan.client.screen.MidasTouchTimerScreen;
import com.mifan.client.screen.MimicAbsorbScreen;
import com.mifan.client.screen.MimicReleaseScreen;
import com.mifan.client.screen.NecromancerScreen;
import com.mifan.client.screen.PlayerStatusScreen;
import com.mifan.client.screen.RecorderOfficerTimerScreen;
import com.mifan.network.clientbound.DangerSensePingPacket;
import com.mifan.network.clientbound.GoldenCrowChannelingPacket;
import com.mifan.network.clientbound.GoldenCrowExplosionPacket;
import com.mifan.network.clientbound.InstinctProcPacket;
import com.mifan.network.clientbound.OlfactionTrailSyncPacket;
import com.mifan.network.clientbound.OpenAdminPanelPacket;
import com.mifan.network.clientbound.OpenFerrymanScreenPacket;
import com.mifan.network.clientbound.OpenMidasTouchScreenPacket;
import com.mifan.network.clientbound.OpenMimicAbsorbScreenPacket;
import com.mifan.network.clientbound.OpenMimicReleaseScreenPacket;
import com.mifan.network.clientbound.OpenNecromancerScreenPacket;
import com.mifan.network.clientbound.OpenRecorderOfficerScreenPacket;
import com.mifan.network.clientbound.UpdateNecromancerScreenPacket;
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
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
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
    private static int necromancerSoulCount;

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

    public static void openNecromancerScreen(OpenNecromancerScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new NecromancerScreen(packet));
    }

    public static void updateNecromancerScreen(UpdateNecromancerScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof NecromancerScreen screen) {
            screen.applyUpdate(packet);
        }
    }

    public static void updateNecromancerSoulCount(int total) {
        necromancerSoulCount = Math.max(0, total);
    }

    public static void openPlayerStatusScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        minecraft.setScreen(new PlayerStatusScreen());
    }

    public static void openDesignatedAbilityScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new DesignatedAbilityScreen());
    }

    public static void openAdminPanelScreen(OpenAdminPanelPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new AdminPanelScreen(packet.fields(), packet.commands()));
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

    public static void handleGoldenCrowChanneling(GoldenCrowChannelingPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        double cx = packet.getCenterX();
        double cy = packet.getCenterY();
        double cz = packet.getCenterZ();
        double groundY = packet.getGroundY();
        float progress = packet.getProgress();
        double targetOrb = packet.getTargetOrb();
        float pScale = packet.getParticleScale();
        int elapsed = packet.getElapsed();

        double radius = Math.max(0.6D, progress * targetOrb);
        double shellThickness = 0.8D + progress * Math.max(1.2D, targetOrb * 0.15D);

        // 1) 向心引力漩涡：多层圆环的粒子被吸向中心（本地生成，0 网络成本）
        int ringCount = 4 + (int) (progress * 10 * pScale);
        int perRing = 20 + (int) (progress * 80 * pScale);
        for (int r = 0; r < ringCount; r++) {
            double ringRadius = radius + (r / (double) ringCount) * Math.max(4.0D, targetOrb * 0.5D);
            double phase = elapsed * 0.18D + r * 0.6D;
            for (int i = 0; i < perRing; i++) {
                double theta = (Math.PI * 2.0D * i) / perRing + phase;
                double dx = Math.cos(theta) * ringRadius;
                double dz = Math.sin(theta) * ringRadius;
                double dy = Math.sin(theta * 2.0D + phase) * shellThickness;
                Vec3 toward = new Vec3(-dx, -dy, -dz).normalize().scale(0.3D);
                level.addParticle(ParticleTypes.FLAME,
                        cx + dx, cy + dy, cz + dz,
                        toward.x, toward.y, toward.z);
            }
        }

        // 2) 地面日轮：密集外圈，真·完整圆环
        int groundCount = Math.max(48, (int) (120 * pScale));
        double groundRadius = 2.0D + progress * Math.max(4.0D, targetOrb * 0.3D);
        double groundPhase = elapsed * 0.22D;
        for (int i = 0; i < groundCount; i++) {
            double theta = (Math.PI * 2.0D * i) / groundCount + groundPhase;
            double dx = Math.cos(theta) * groundRadius;
            double dz = Math.sin(theta) * groundRadius;
            level.addParticle(ParticleTypes.FLAME,
                    cx + dx, groundY, cz + dz, 0.0D, 0.02D, 0.0D);
        }
        // 内圈再加一圈更小更密的辅助，让"圆环"更饱满
        int innerCount = Math.max(36, (int) (80 * pScale));
        double innerRadius = groundRadius * 0.72D;
        double innerPhase = -elapsed * 0.18D;
        for (int i = 0; i < innerCount; i++) {
            double theta = (Math.PI * 2.0D * i) / innerCount + innerPhase;
            double dx = Math.cos(theta) * innerRadius;
            double dz = Math.sin(theta) * innerRadius;
            level.addParticle(ParticleTypes.SMALL_FLAME,
                    cx + dx, groundY, cz + dz, 0.0D, 0.015D, 0.0D);
        }
    }

    public static void handleGoldenCrowExplosion(GoldenCrowExplosionPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        double px = packet.getX();
        double py = packet.getY();
        double pz = packet.getZ();
        double explosionRadius = packet.getExplosionRadius();
        double stunRadius = packet.getStunRadius();
        float pScale = packet.getParticleScale();

        // 1) 七圈同心震荡波——本地生成，从内到外半径递增、颜色金→红→白
        spawnRing(level, px, py + 0.2D, pz, explosionRadius * 0.16D,
                Math.max(24, (int) (80 * pScale)), ParticleTypes.FLAME);
        spawnRing(level, px, py + 0.2D, pz, explosionRadius * 0.32D,
                Math.max(32, (int) (128 * pScale)), ParticleTypes.FLAME);
        spawnRing(level, px, py + 0.2D, pz, explosionRadius * 0.50D,
                Math.max(48, (int) (200 * pScale)), ParticleTypes.END_ROD);
        spawnRing(level, px, py + 0.2D, pz, explosionRadius * 0.68D,
                Math.max(64, (int) (256 * pScale)), ParticleTypes.FLAME);
        spawnRing(level, px, py + 0.2D, pz, explosionRadius * 0.84D,
                Math.max(80, (int) (320 * pScale)), ParticleTypes.SMALL_FLAME);
        spawnRing(level, px, py + 0.2D, pz, explosionRadius,
                Math.max(96, (int) (400 * pScale)), ParticleTypes.END_ROD);
        spawnRing(level, px, py + 0.2D, pz, stunRadius,
                Math.max(128, (int) (480 * pScale)), ParticleTypes.FLASH);

        // 2) 冲天火柱——本地生成，垂直向上喷射，强调"砸落"的反冲
        int pillarCount = Math.max(60, (int) (200 * pScale));
        double pillarHeight = explosionRadius * 0.8D;
        for (int i = 0; i < pillarCount; i++) {
            double rnd = level.random.nextDouble();
            double dx = (level.random.nextDouble() - 0.5D) * explosionRadius * 0.5D;
            double dz = (level.random.nextDouble() - 0.5D) * explosionRadius * 0.5D;
            double dy = rnd * pillarHeight;
            double vy = 0.4D + rnd * 0.8D;
            level.addParticle(ParticleTypes.FLAME,
                    px + dx, py + dy, pz + dz, 0.0D, vy, 0.0D);
            if (i % 2 == 0) {
                level.addParticle(ParticleTypes.LAVA,
                        px + dx, py + dy, pz + dz, 0.0D, vy * 0.5D, 0.0D);
            }
        }

        // 3) 巨型扩散光环——三层，大半径、极密集
        int haloCount = Math.max(180, (int) (600 * pScale));
        for (int i = 0; i < haloCount; i++) {
            double theta = (Math.PI * 2.0D * i) / haloCount;
            double rr = explosionRadius * (1.05D + level.random.nextDouble() * 0.15D);
            double dx = Math.cos(theta) * rr;
            double dz = Math.sin(theta) * rr;
            double vx = Math.cos(theta) * 0.3D;
            double vz = Math.sin(theta) * 0.3D;
            level.addParticle(ParticleTypes.END_ROD,
                    px + dx, py + 0.3D, pz + dz, vx, 0.1D, vz);
        }

        // 4) 地面冲击烟尘——水平向外扩散
        int dustCount = Math.max(120, (int) (400 * pScale));
        for (int i = 0; i < dustCount; i++) {
            double theta = level.random.nextDouble() * Math.PI * 2.0D;
            double rr = level.random.nextDouble() * explosionRadius * 0.9D;
            double dx = Math.cos(theta) * rr;
            double dz = Math.sin(theta) * rr;
            double vx = Math.cos(theta) * (0.4D + level.random.nextDouble() * 0.6D);
            double vz = Math.sin(theta) * (0.4D + level.random.nextDouble() * 0.6D);
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    px + dx, py + 0.1D, pz + dz, vx, 0.05D, vz);
        }

        // 5) 中心白光闪——只对本地玩家播一次短促亮闪
        for (int i = 0; i < Math.max(6, (int) (16 * pScale)); i++) {
            double dx = (level.random.nextDouble() - 0.5D) * explosionRadius * 0.6D;
            double dy = (level.random.nextDouble() - 0.5D) * explosionRadius * 0.3D;
            double dz = (level.random.nextDouble() - 0.5D) * explosionRadius * 0.6D;
            level.addParticle(ParticleTypes.FLASH, px + dx, py + dy, pz + dz, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void spawnRing(ClientLevel level, double cx, double cy, double cz,
            double radius, int count, net.minecraft.core.particles.SimpleParticleType particle) {
        for (int i = 0; i < count; i++) {
            double theta = (Math.PI * 2.0D * i) / count;
            double dx = Math.cos(theta) * radius;
            double dz = Math.sin(theta) * radius;
            double vx = Math.cos(theta) * 0.12D;
            double vz = Math.sin(theta) * 0.12D;
            level.addParticle(particle, cx + dx, cy, cz + dz, vx, 0.05D, vz);
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
        SonicSenseClientHandler.tick(player, gameTime);
        OlfactionClientHandler.tick(player, level, gameTime);
        ElementalDomainClientHandler.tick(player);
        com.mifan.screeneffect.manager.CombatStateTracker.tick(player, gameTime);
        com.mifan.screeneffect.manager.ScreenEffectManager.tick(player, gameTime);
        cleanupExpired(gameTime);
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearClientMarks();
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

        if (necromancerSoulCount > 0) {
            drawNecromancerSoulCount(event.getGuiGraphics(), width, height);
        }

        com.mifan.screeneffect.manager.ScreenEffectManager.renderOverlay(
                event.getGuiGraphics(), player, width, height, gameTime, event.getPartialTick());
    }

    private static void drawNecromancerSoulCount(GuiGraphics guiGraphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font == null) {
            return;
        }
        Component label = Component.translatable("hud.corpse_campus.necromancer_souls", necromancerSoulCount);
        int textWidth = minecraft.font.width(label);
        int padding = 4;
        int boxWidth = textWidth + padding * 2;
        int boxHeight = minecraft.font.lineHeight + padding * 2;
        int right = width - 6;
        int bottom = height - 42;
        int left = right - boxWidth;
        int top = bottom - boxHeight;
        guiGraphics.fill(left, top, right, bottom, 0x88000000);
        guiGraphics.fill(left, top, right, top + 1, 0x88A06BE0);
        guiGraphics.fill(left, bottom - 1, right, bottom, 0x88A06BE0);
        guiGraphics.drawString(minecraft.font, label, left + padding, top + padding, 0xE7C6FF, false);
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
        com.mifan.screeneffect.manager.CombatStateTracker.clear();
        com.mifan.screeneffect.manager.ScreenEffectManager.clear();
    }

    private static Player localPlayer() {
        return Minecraft.getInstance().player;
    }

}
