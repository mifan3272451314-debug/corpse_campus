package com.mifan.anomaly;

import com.mifan.corpsecampus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 自然觉醒通道的事件挂载（见 自然觉醒.md §6）。
 *
 * 本次实现 9 类原子条件的事件监听；副本探索与配方合成两条按用户 2026-04-23 口径**本轮不接**。
 * 所有监听委托给 {@link NaturalAwakeningService}，本类只做事件过滤 + 入口转发。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class NaturalAwakeningEventHandler {

    private NaturalAwakeningEventHandler() {
    }

    // ─── 告示牌放置 → 印记 ──────────────────────────────────────────

    @SubscribeEvent
    public static void onSignPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        if (event.getPlacedBlock().is(BlockTags.ALL_SIGNS)) {
            NaturalAwakeningService.addAndCheck(player, NaturalAwakeningService.KEY_SIGN_PLACE, 1);
        }
    }

    // ─── 破坏方块 → 耐力 ───────────────────────────────────────────

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        NaturalAwakeningService.addAndCheck(player, NaturalAwakeningService.KEY_BLOCK_BREAK, 1);
    }

    // ─── 锄头开垦 → 沃土 ───────────────────────────────────────────
    // Forge 1.20.1 的 BlockToolModificationEvent 是 UseHoeEvent 的统一替代

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHoeTill(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || event.isCanceled()) {
            return;
        }
        if (event.getToolAction() != ToolActions.HOE_TILL) {
            return;
        }
        if (event.getFinalState() == null) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        NaturalAwakeningService.addAndCheck(player, NaturalAwakeningService.KEY_FARMLAND_TILL, 1);
    }

    // ─── 骨粉催熟作物 → 宁禾 ───────────────────────────────────────
    // 仅计玩家手持骨粉成功施用到合法 BonemealableBlock。
    // 宁禾法术自身将来若驱动作物生长，应调 NaturalAwakeningService.onCropGrownByPlayer。

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBonemeal(BonemealEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity user = event.getEntity();
        if (!(user instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        BlockState state = event.getBlock();
        Block block = state.getBlock();
        if (!(block instanceof BonemealableBlock bmb)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!bmb.isValidBonemealTarget(level, pos, state)) {
            return;
        }
        NaturalAwakeningService.addAndCheck(player, NaturalAwakeningService.KEY_BONEMEAL_CROP, 1);
    }

    // ─── 平砍 → 躁狂 ──────────────────────────────────────────────

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.isCanceled()) {
            return;
        }
        Player source = event.getEntity();
        if (!(source instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        if (event.getTarget() == null || event.getTarget() == player) {
            return;
        }
        NaturalAwakeningService.addAndCheck(player, NaturalAwakeningService.KEY_MELEE_ATTACK, 1);
    }

    // ─── 被怪物攻击 → 本能 ─────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Enemy)) {
            return;
        }
        NaturalAwakeningService.addAndCheck(victim, NaturalAwakeningService.KEY_HIT_BY_MONSTER, 1);
    }

    // ─── 击杀怪物 → 冥化 ──────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Enemy)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        NaturalAwakeningService.addAndCheck(player, NaturalAwakeningService.KEY_MONSTER_KILL, 1);
    }

    // ─── 一次性坠落 → 万象 ─────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        int distance = (int) Math.floor(event.getDistance());
        if (distance <= 0) {
            return;
        }
        NaturalAwakeningService.updateMaxAndCheck(player, NaturalAwakeningService.KEY_FALL_BURST_MAX, distance);
    }

    // ─── 每 tick：危机(蹲伏/爬行) + 磁吸(接触墙) ──────────────────

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }

        // 危机：两子条件都是"连续 tick 中断清零"
        NaturalAwakeningService.tickContinuous(player,
                NaturalAwakeningService.KEY_CROUCH_TICK, player.isCrouching());
        NaturalAwakeningService.tickContinuous(player,
                NaturalAwakeningService.KEY_CRAWL_TICK, player.isVisuallyCrawling());

        // 磁吸：累计 tick（用户 2026-04-23 口径），中断不清零
        NaturalAwakeningService.tickAccumulative(player,
                NaturalAwakeningService.KEY_WALL_TOUCH_TICK, isTouchingWall(player));
    }

    /**
     * 碰撞盒水平四个方向各探两个高度（脚/胸），命中非空气且 blocksMotion 的方块即视为"接触墙"。
     * 不检测顶/底面，避免把地板/天花板误判为墙。
     */
    private static boolean isTouchingWall(Player player) {
        Level level = player.level();
        AABB box = player.getBoundingBox();
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double halfX = (box.maxX - box.minX) * 0.5;
        double halfZ = (box.maxZ - box.minZ) * 0.5;
        double probe = 0.05;

        double[] probeYs = {
                box.minY + 0.3,
                box.maxY - 0.2
        };

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double y : probeYs) {
            int yi = (int) Math.floor(y);
            // +X
            pos.set((int) Math.floor(centerX + halfX + probe), yi, (int) Math.floor(centerZ));
            if (solidAt(level, pos)) return true;
            // -X
            pos.set((int) Math.floor(centerX - halfX - probe), yi, (int) Math.floor(centerZ));
            if (solidAt(level, pos)) return true;
            // +Z
            pos.set((int) Math.floor(centerX), yi, (int) Math.floor(centerZ + halfZ + probe));
            if (solidAt(level, pos)) return true;
            // -Z
            pos.set((int) Math.floor(centerX), yi, (int) Math.floor(centerZ - halfZ - probe));
            if (solidAt(level, pos)) return true;
        }
        return false;
    }

    private static boolean solidAt(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && s.blocksMotion();
    }
}
