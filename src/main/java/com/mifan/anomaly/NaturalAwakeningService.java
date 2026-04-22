package com.mifan.anomaly;

import com.mifan.corpsecampus;
import com.mifan.registry.ModSchools;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自然觉醒通道的服务端数据层（见 自然觉醒.md）。
 *
 * 职责：
 *   1) 玩家 NBT 进度读写（AnomalyP0.NaturalAwakeningProgress）
 *   2) 原子条件阈值表与条件→(school, spell) 1:1 解析
 *   3) 累加 / 取最大 / 连续 tick / 累计 tick 四种计数语义
 *   4) 首次跨越阈值时调用 AnomalyBookService.applyAwakening 定向觉醒
 *   5) 死亡全量清零（§8.2）、为 NingheSpell 等外部调用方提供显式入口
 *
 * 不负责事件挂载，挂载在 NaturalAwakeningEventHandler 中完成。
 */
public final class NaturalAwakeningService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PLAYER_ROOT = "AnomalyP0";
    private static final String PROGRESS_TAG = "NaturalAwakeningProgress";

    // ─── 原子条件 NBT key 常量 ──────────────────────────────────────────

    public static final String KEY_SIGN_PLACE = "sign_place_count";            // 印记
    public static final String KEY_BLOCK_BREAK = "block_break_count";          // 耐力
    public static final String KEY_FARMLAND_TILL = "farmland_till_count";      // 沃土
    public static final String KEY_BONEMEAL_CROP = "bonemeal_crop_count";      // 宁禾
    public static final String KEY_MELEE_ATTACK = "melee_attack_count";        // 躁狂
    public static final String KEY_HIT_BY_MONSTER = "hit_by_monster_count";    // 本能
    public static final String KEY_MONSTER_KILL = "monster_kill_count";        // 冥化
    public static final String KEY_CROUCH_TICK = "continuous_crouch_tick";     // 危机(连续蹲伏)
    public static final String KEY_CRAWL_TICK = "continuous_crawl_tick";       // 危机(连续爬行)
    public static final String KEY_WALL_TOUCH_TICK = "wall_touch_tick";        // 磁吸(累计接触墙)
    public static final String KEY_FALL_BURST_MAX = "fall_burst_max";          // 万象(一次性坠落最大)

    public static final List<String> ALL_KEYS = List.of(
            KEY_SIGN_PLACE, KEY_BLOCK_BREAK, KEY_FARMLAND_TILL, KEY_BONEMEAL_CROP,
            KEY_MELEE_ATTACK, KEY_HIT_BY_MONSTER, KEY_MONSTER_KILL,
            KEY_CROUCH_TICK, KEY_CRAWL_TICK, KEY_WALL_TOUCH_TICK, KEY_FALL_BURST_MAX);

    // ─── 阈值常量 ──────────────────────────────────────────────────────

    public static final int THRESHOLD_SIGN_PLACE = 100;
    public static final int THRESHOLD_BLOCK_BREAK = 1000;
    public static final int THRESHOLD_FARMLAND_TILL = 75;
    public static final int THRESHOLD_BONEMEAL_CROP = 100;
    public static final int THRESHOLD_MELEE_ATTACK = 500;
    public static final int THRESHOLD_HIT_BY_MONSTER = 200;
    public static final int THRESHOLD_MONSTER_KILL = 200;
    public static final int THRESHOLD_CROUCH_TICK = 6000;      // 5 分钟 @ 20 tps
    public static final int THRESHOLD_CRAWL_TICK = 2400;       // 120 秒 @ 20 tps
    public static final int THRESHOLD_WALL_TOUCH_TICK = 6000;  // 5 分钟 @ 20 tps
    public static final int THRESHOLD_FALL_BURST_MAX = 200;    // 坠落方块数

    private NaturalAwakeningService() {
    }

    public static int getThreshold(String key) {
        return switch (key) {
            case KEY_SIGN_PLACE -> THRESHOLD_SIGN_PLACE;
            case KEY_BLOCK_BREAK -> THRESHOLD_BLOCK_BREAK;
            case KEY_FARMLAND_TILL -> THRESHOLD_FARMLAND_TILL;
            case KEY_BONEMEAL_CROP -> THRESHOLD_BONEMEAL_CROP;
            case KEY_MELEE_ATTACK -> THRESHOLD_MELEE_ATTACK;
            case KEY_HIT_BY_MONSTER -> THRESHOLD_HIT_BY_MONSTER;
            case KEY_MONSTER_KILL -> THRESHOLD_MONSTER_KILL;
            case KEY_CROUCH_TICK -> THRESHOLD_CROUCH_TICK;
            case KEY_CRAWL_TICK -> THRESHOLD_CRAWL_TICK;
            case KEY_WALL_TOUCH_TICK -> THRESHOLD_WALL_TOUCH_TICK;
            case KEY_FALL_BURST_MAX -> THRESHOLD_FALL_BURST_MAX;
            default -> Integer.MAX_VALUE;
        };
    }

    // ─── NBT 访问 ──────────────────────────────────────────────────────

    private static CompoundTag readProgressTag(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag anomaly = persisted.getCompound(PLAYER_ROOT);
        return anomaly.getCompound(PROGRESS_TAG);
    }

    private static CompoundTag getOrCreateProgressTag(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persistent.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persistent.put(Player.PERSISTED_NBT_TAG, persisted);
        }
        CompoundTag anomaly = persisted.getCompound(PLAYER_ROOT);
        if (!persisted.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)) {
            persisted.put(PLAYER_ROOT, anomaly);
        }
        CompoundTag progress = anomaly.getCompound(PROGRESS_TAG);
        if (!anomaly.contains(PROGRESS_TAG, Tag.TAG_COMPOUND)) {
            anomaly.put(PROGRESS_TAG, progress);
        }
        return progress;
    }

    public static int getProgress(ServerPlayer player, String key) {
        return readProgressTag(player).getInt(key);
    }

    public static Map<String, Integer> snapshotProgress(ServerPlayer player) {
        CompoundTag tag = readProgressTag(player);
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        for (String key : ALL_KEYS) {
            map.put(key, tag.getInt(key));
        }
        return map;
    }

    /** 死亡全量清零进度（§8.2）。NaturalAwakeningDone 字段由 AnomalyBookService.clearSequenceBinding 处理。 */
    public static void clearProgress(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag anomaly = persisted.getCompound(PLAYER_ROOT);
        if (anomaly.contains(PROGRESS_TAG)) {
            anomaly.remove(PROGRESS_TAG);
            persisted.put(PLAYER_ROOT, anomaly);
            persistent.put(Player.PERSISTED_NBT_TAG, persisted);
        }
    }

    // ─── 进度冻结 ──────────────────────────────────────────────────────

    /** 已觉醒玩家跳过累加（§7.2）。 */
    public static boolean isFrozen(ServerPlayer player) {
        ItemStack book = AnomalyBookService.getPlayerBook(player);
        return !book.isEmpty() && AnomalyBookService.isAwakened(book);
    }

    // ─── 计数入口（四种语义） ──────────────────────────────────────────

    /** 累加 delta，首次跨越阈值时触发觉醒。用于 sign_place / farmland_till / bonemeal_crop /
     *  melee_attack / hit_by_monster / monster_kill / block_break。 */
    public static void addAndCheck(ServerPlayer player, String key, int delta) {
        if (delta <= 0 || isFrozen(player)) {
            return;
        }
        CompoundTag progress = getOrCreateProgressTag(player);
        int oldValue = progress.getInt(key);
        int newValue = oldValue + delta;
        progress.putInt(key, newValue);
        checkThresholdCrossed(player, key, oldValue, newValue);
    }

    /** 取历史最大值，首次跨越阈值时触发觉醒。用于 fall_burst_max。 */
    public static void updateMaxAndCheck(ServerPlayer player, String key, int value) {
        if (value <= 0 || isFrozen(player)) {
            return;
        }
        CompoundTag progress = getOrCreateProgressTag(player);
        int oldValue = progress.getInt(key);
        if (value <= oldValue) {
            return;
        }
        progress.putInt(key, value);
        checkThresholdCrossed(player, key, oldValue, value);
    }

    /** 连续 tick：active=true 时 +1；active=false 时立即清零。用于危机(crouch / crawl)。
     *  中断后下次须从 0 重新累积（§10-4 已确认连续语义）。 */
    public static void tickContinuous(ServerPlayer player, String key, boolean active) {
        if (isFrozen(player)) {
            return;
        }
        CompoundTag progress = getOrCreateProgressTag(player);
        int oldValue = progress.getInt(key);
        if (!active) {
            if (oldValue != 0) {
                progress.putInt(key, 0);
            }
            return;
        }
        int newValue = oldValue + 1;
        progress.putInt(key, newValue);
        checkThresholdCrossed(player, key, oldValue, newValue);
    }

    /** 累计 tick：active=true 时 +1；active=false 时不变（不清零）。用于磁吸。 */
    public static void tickAccumulative(ServerPlayer player, String key, boolean active) {
        if (!active || isFrozen(player)) {
            return;
        }
        CompoundTag progress = getOrCreateProgressTag(player);
        int oldValue = progress.getInt(key);
        int newValue = oldValue + 1;
        progress.putInt(key, newValue);
        checkThresholdCrossed(player, key, oldValue, newValue);
    }

    private static void checkThresholdCrossed(ServerPlayer player, String key, int oldValue, int newValue) {
        int threshold = getThreshold(key);
        if (newValue >= threshold && oldValue < threshold) {
            tryAwaken(player, key);
        }
    }

    // ─── 解析 & 觉醒 ──────────────────────────────────────────────────

    /**
     * 原子条件 → (schoolId, spellId) 的 1:1 解析。
     * 副本探索 / 配方合成 两条本轮不实现，相关 key 不会进入本 service，因此无需返回 null 分支。
     * 校正后(2026-04-23)不存在多对一；若未来条件表再加回多对一，需在这里按流派/随机/UI 选择扩展。
     */
    @Nullable
    private static ResolvedAwakening resolveSpellByCondition(String key) {
        return switch (key) {
            case KEY_SIGN_PLACE -> new ResolvedAwakening(
                    ModSchools.XUJING_RESOURCE, spellId("mark"));
            case KEY_FARMLAND_TILL -> new ResolvedAwakening(
                    ModSchools.RIZHAO_RESOURCE, spellId("fertile_land"));
            case KEY_BONEMEAL_CROP -> new ResolvedAwakening(
                    ModSchools.RIZHAO_RESOURCE, spellId("ninghe"));
            case KEY_CROUCH_TICK, KEY_CRAWL_TICK -> new ResolvedAwakening(
                    ModSchools.XUJING_RESOURCE, spellId("danger_sense"));
            case KEY_MELEE_ATTACK -> new ResolvedAwakening(
                    ModSchools.DONGYUE_RESOURCE, spellId("mania"));
            case KEY_HIT_BY_MONSTER -> new ResolvedAwakening(
                    ModSchools.DONGYUE_RESOURCE, spellId("instinct"));
            case KEY_MONSTER_KILL -> new ResolvedAwakening(
                    ModSchools.DONGYUE_RESOURCE, spellId("necrotic_rebirth"));
            case KEY_FALL_BURST_MAX -> new ResolvedAwakening(
                    ModSchools.YUZHE_RESOURCE, spellId("wanxiang"));
            case KEY_WALL_TOUCH_TICK -> new ResolvedAwakening(
                    ModSchools.YUZHE_RESOURCE, spellId("magnetic_cling"));
            case KEY_BLOCK_BREAK -> new ResolvedAwakening(
                    ModSchools.SHENGQI_RESOURCE, spellId("stamina"));
            default -> null;
        };
    }

    private static ResourceLocation spellId(String path) {
        return ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, path);
    }

    private static void tryAwaken(ServerPlayer player, String key) {
        ResolvedAwakening resolved = resolveSpellByCondition(key);
        if (resolved == null) {
            return;
        }

        AnomalyBookService.AbsorbResult result = AnomalyBookService.applyAwakening(
                player,
                resolved.schoolId(),
                resolved.spellId(),
                /* markNatural */ true,
                "message.corpse_campus.awakened.natural");

        if (result.success()) {
            player.displayClientMessage(result.message(), false);
            return;
        }

        // 失败只静默记录（§9.3），进度按 §7.3 保留。席位释放后下一次事件会再次触发本方法。
        LOGGER.info("[natural-awakening] blocked player={} key={} reason={}",
                player.getGameProfile().getName(), key, result.message().getString());
    }

    // ─── 外部 spell 代码的显式入口 ──────────────────────────────────────

    /** 为 NingheSpell 等"法术自身驱动了作物生长"的路径提供显式入口（用户 2026-04-23 口径）。 */
    public static void onCropGrownByPlayer(ServerPlayer player) {
        addAndCheck(player, KEY_BONEMEAL_CROP, 1);
    }

    private record ResolvedAwakening(ResourceLocation schoolId, ResourceLocation spellId) {
    }
}
