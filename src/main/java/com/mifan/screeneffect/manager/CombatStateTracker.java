package com.mifan.screeneffect.manager;

import com.mifan.screeneffect.config.ScreenEffectConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class CombatStateTracker {
    private static long lastHostileSeenTick = -1L;
    private static int cachedHostileCount;
    private static int scanCooldown;

    private CombatStateTracker() {
    }

    public static void clear() {
        lastHostileSeenTick = -1L;
        cachedHostileCount = 0;
        scanCooldown = 0;
    }

    public static void tick(Player player, long gameTime) {
        if (--scanCooldown > 0) {
            return;
        }
        scanCooldown = 5;

        int radius = ScreenEffectConfig.COMBAT_DETECT_RADIUS.get();
        AABB box = player.getBoundingBox().inflate(radius);
        int count = 0;
        for (LivingEntity entity : player.level().getEntitiesOfClass(
                LivingEntity.class, box,
                e -> e instanceof Enemy && e.isAlive() && !e.isInvisible())) {
            count++;
            if (count >= 1) {
                break;
            }
        }
        cachedHostileCount = count;
        if (count > 0) {
            lastHostileSeenTick = gameTime;
        }
    }

    public static boolean isInCombat(long gameTime) {
        if (cachedHostileCount > 0) {
            return true;
        }
        int exitDelay = ScreenEffectConfig.COMBAT_EXIT_DELAY_TICKS.get();
        return lastHostileSeenTick >= 0L && gameTime - lastHostileSeenTick < exitDelay;
    }

    public static float combatIntensity(long gameTime) {
        if (cachedHostileCount > 0) {
            return 1F;
        }
        if (lastHostileSeenTick < 0L) {
            return 0F;
        }
        int exitDelay = ScreenEffectConfig.COMBAT_EXIT_DELAY_TICKS.get();
        long since = gameTime - lastHostileSeenTick;
        if (since >= exitDelay) {
            return 0F;
        }
        return 1F - (since / (float) exitDelay);
    }
}
