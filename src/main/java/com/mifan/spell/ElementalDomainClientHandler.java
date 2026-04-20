package com.mifan.spell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

public final class ElementalDomainClientHandler {
    private static final int ELEMENTAL_DOMAIN_OVERLAY_FADE_IN_TICKS = 10;
    private static final int ELEMENTAL_DOMAIN_OVERLAY_HOLD_TICKS = 30;
    private static final int ELEMENTAL_DOMAIN_OVERLAY_FADE_OUT_TICKS = 10;

    private static boolean elementalDomainActive;
    private static int elementalDomainTick;
    private static int elementalDomainReleaseTick;

    private ElementalDomainClientHandler() {
    }

    public static void tick(Player player) {
        boolean activeNow = hasElementalDomain(player);
        if (activeNow) {
            elementalDomainActive = true;
            elementalDomainTick = Math.min(elementalDomainTick + 1,
                    ELEMENTAL_DOMAIN_OVERLAY_FADE_IN_TICKS
                            + ELEMENTAL_DOMAIN_OVERLAY_HOLD_TICKS
                            + ELEMENTAL_DOMAIN_OVERLAY_FADE_OUT_TICKS);
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

    public static void clear() {
        elementalDomainActive = false;
        elementalDomainTick = 0;
        elementalDomainReleaseTick = 0;
    }

    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event, Player player) {
        if (!shouldRenderElementalDomainForPlayer(player)) {
            return;
        }

        float pulse = 0.78F + (float) (Math.sin(player.level().getGameTime() * 0.08D) * 0.05D);
        event.setRed(event.getRed() * 0.36F * pulse);
        event.setGreen(event.getGreen() * 0.42F * pulse);
        event.setBlue(event.getBlue() * 0.58F);
    }

    public static void onRenderFog(ViewportEvent.RenderFog event, Player player) {
        if (!shouldRenderElementalDomainForPlayer(player)) {
            return;
        }

        event.scaleFarPlaneDistance(0.78F);
        event.scaleNearPlaneDistance(1.1F);
    }

    public static void renderOverlay(GuiGraphics guiGraphics, Player player, int width, int height, long gameTime) {
        if (!shouldRenderElementalDomainForPlayer(player) || !isVisualActive()) {
            return;
        }

        float alphaScale = getOverlayStrength();
        if (alphaScale <= 0.01F) {
            return;
        }

        int veilAlpha = Mth.clamp((int) (42.0F * alphaScale), 0, 72);
        int accentAlpha = Mth.clamp((int) ((22.0F + Math.sin(gameTime * 0.09D) * 7.0D) * alphaScale), 0, 52);
        int edgeAlpha = Mth.clamp((int) ((32.0F + Math.sin(gameTime * 0.12D) * 10.0D) * alphaScale), 0, 70);

        guiGraphics.fill(0, 0, width, height, (veilAlpha << 24) | 0x0C1320);
        drawVignette(guiGraphics, width, height, edgeAlpha);

        int centerX = width / 2;
        int centerY = height / 2;
        float appearProgress = Mth.clamp(elementalDomainTick / (float) ELEMENTAL_DOMAIN_OVERLAY_FADE_IN_TICKS, 0.0F,
                1.0F);
        int pulseRadius = (int) ((Math.min(width, height) / 4.5F) + (Math.min(width, height) / 9.0F) * appearProgress);
        int pulseThickness = 1 + (int) appearProgress;
        int ringAlpha = Mth.clamp((int) (accentAlpha * 0.9F), 0, 60);

        drawRing(guiGraphics, centerX, centerY, pulseRadius, pulseThickness, ringAlpha, gameTime);
        drawText(guiGraphics, Minecraft.getInstance().font, width, height, alphaScale, gameTime);
        drawCrosshair(guiGraphics, centerX, centerY, alphaScale, gameTime);

        if (elementalDomainTick >= 12 && elementalDomainTick <= 32 && elementalDomainActive) {
            int lineAlpha = Math.min(28, ringAlpha);
            guiGraphics.fill(0, 0, width, 1, (lineAlpha << 24) | 0x78D7FF);
            guiGraphics.fill(0, height - 1, width, height, (lineAlpha << 24) | 0x4E7BFF);
        }

        spawnAuraParticles(player, gameTime, alphaScale);
    }

    private static boolean hasElementalDomain(Player player) {
        return player.hasEffect(com.mifan.registry.ModMobEffects.ELEMENTAL_DOMAIN.get());
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

    private static boolean isVisualActive() {
        return elementalDomainActive || elementalDomainReleaseTick > 0;
    }

    private static float getOverlayStrength() {
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

    private static void drawVignette(GuiGraphics guiGraphics, int width, int height, int alpha) {
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

    private static void drawRing(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int thickness,
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

    private static void drawText(GuiGraphics guiGraphics, Font font, int width, int height, float alphaScale,
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

        guiGraphics.drawString(font, line1, centerX - width1 / 2 + 1, line1Y + 1, (alphaShadow << 24) | 0x48D6FF,
                false);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(2.0F, 2.0F, 1.0F);
        guiGraphics.drawString(font, line1, (centerX - width1 / 2) / 2, (line1Y + wobble) / 2,
                (alphaMain << 24) | 0xF4FDFF, false);
        guiGraphics.pose().popPose();

        guiGraphics.drawString(font, line2, centerX - width2 / 2 + 1, line2Y + 1, (alphaShadow << 24) | 0x7DA6FF,
                false);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(2.0F, 2.0F, 1.0F);
        guiGraphics.drawString(font, line2, (centerX - width2 / 2) / 2, line2Y / 2,
                (alphaMain << 24) | 0xFFFFFF, false);
        guiGraphics.pose().popPose();
    }

    private static void drawCrosshair(GuiGraphics guiGraphics, int centerX, int centerY, float alphaScale,
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

    private static void spawnAuraParticles(Player player, long gameTime, float alphaScale) {
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
            org.joml.Vector3f color = i == 0
                    ? new org.joml.Vector3f(1.0F, 0.55F, 0.24F)
                    : i == 1
                            ? new org.joml.Vector3f(0.42F, 0.92F, 1.0F)
                            : new org.joml.Vector3f(0.58F, 0.66F, 1.0F);
            level.addParticle(new net.minecraft.core.particles.DustParticleOptions(color, 0.72F + alphaScale * 0.3F),
                    x, y, z, 0.0D, 0.01D, 0.0D);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, x, y + 0.1D, z, 0.0D, 0.0D, 0.0D);
        }
    }
}