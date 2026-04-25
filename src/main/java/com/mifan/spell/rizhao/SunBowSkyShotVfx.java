package com.mifan.spell.rizhao;

import com.mifan.corpsecampus;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 日轮弓「射日」视觉特效调度器（服务端 tick）。
 *
 * 双段式表现：
 *   - 弓口段（前 3 tick）：在弓口爆发 END_ROD/FLAME/LAVA/FLASH 蓄力光团
 *   - 冲天段（20 tick × 4 格 = 80 格）：沿视线方向逐 tick 推进，密集堆叠 FLAME（主体）
 *                                       + LAVA（重量感）+ END_ROD（光粒子核）
 *
 * 走 ServerLevel.sendParticles 服务端广播，无需 entity / 自定义 packet。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class SunBowSkyShotVfx {

    /** 总射程（格）。 */
    private static final int TOTAL_BLOCKS = 80;
    /** 每 tick 推进格数（4 × 20 = 80）。 */
    private static final int BLOCKS_PER_TICK = 4;
    /** 总持续 tick。 */
    private static final int TOTAL_TICKS = TOTAL_BLOCKS / BLOCKS_PER_TICK;
    /** 弓口蓄力段持续 tick。 */
    private static final int MUZZLE_TICKS = 3;

    private static final List<InFlight> ACTIVE = new ArrayList<>();

    private SunBowSkyShotVfx() {}

    /**
     * 由 {@code SunBowItem.releaseUsing} 在命中太阳成功后调用。
     *
     * @param level   施法所在维度
     * @param muzzle  弓口世界坐标（玩家眼前约 1 格）
     * @param viewDir 视线方向（任意长度，内部归一化）
     */
    public static void schedule(ServerLevel level, Vec3 muzzle, Vec3 viewDir) {
        ACTIVE.add(new InFlight(level, muzzle, viewDir.normalize()));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ACTIVE.isEmpty()) return;
        Iterator<InFlight> it = ACTIVE.iterator();
        while (it.hasNext()) {
            InFlight s = it.next();
            if (!s.advance()) it.remove();
        }
    }

    private static final class InFlight {
        private final ServerLevel level;
        private final Vec3 muzzle;
        private final Vec3 dir;
        private int tick;

        InFlight(ServerLevel level, Vec3 muzzle, Vec3 dir) {
            this.level = level;
            this.muzzle = muzzle;
            this.dir = dir;
            this.tick = 0;
        }

        /** @return false 表示生命周期结束、可从队列移除。 */
        boolean advance() {
            // 弓口蓄力段：FLASH 提供瞬时强光，配合密集 FLAME/END_ROD 营造"金乌张弦"
            if (tick < MUZZLE_TICKS) {
                level.sendParticles(ParticleTypes.END_ROD,
                        muzzle.x, muzzle.y, muzzle.z, 30, 0.5D, 0.5D, 0.5D, 0.05D);
                level.sendParticles(ParticleTypes.FLAME,
                        muzzle.x, muzzle.y, muzzle.z, 50, 0.5D, 0.5D, 0.5D, 0.08D);
                level.sendParticles(ParticleTypes.LAVA,
                        muzzle.x, muzzle.y, muzzle.z, 8, 0.3D, 0.3D, 0.3D, 0.0D);
                level.sendParticles(ParticleTypes.FLASH,
                        muzzle.x, muzzle.y, muzzle.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

            // 冲天段：每 tick 沿视线推进 BLOCKS_PER_TICK 格，沿途生成密集"光柱"段
            int from = tick * BLOCKS_PER_TICK;
            int to = Math.min(from + BLOCKS_PER_TICK, TOTAL_BLOCKS);
            for (int i = from; i < to; i++) {
                Vec3 p = muzzle.add(dir.scale(i));
                // 大火焰主体：高密度堆叠模拟"超大火焰"
                level.sendParticles(ParticleTypes.FLAME,
                        p.x, p.y, p.z, 18, 0.6D, 0.6D, 0.6D, 0.06D);
                // 重量感（少量岩浆碎屑）
                level.sendParticles(ParticleTypes.LAVA,
                        p.x, p.y, p.z, 3, 0.25D, 0.25D, 0.25D, 0.0D);
                // 光粒子核：END_ROD 撑起白光通道
                level.sendParticles(ParticleTypes.END_ROD,
                        p.x, p.y, p.z, 12, 0.35D, 0.35D, 0.35D, 0.03D);
            }

            tick++;
            return tick < TOTAL_TICKS;
        }
    }
}
