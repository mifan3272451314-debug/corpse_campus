package com.mifan.anomaly;

import com.mifan.corpsecampus;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * 异常系统成就发放钩子。
 *
 * 成就 JSON 本身使用 {@code minecraft:impossible} 作为触发器占位，实际发放全部走这里。
 * 任何需要成就联动的业务事件（觉醒、晋升、学习法术、首次发书……）都应该调用本类对应方法。
 *
 * 每个方法内部都做了 null / 错误码保护，**调用方不需要判空**，可以直接在业务流水线里塞一行。
 */
public final class AnomalyAdvancements {

    private static final String NS = corpsecampus.MODID;

    // ─── 成就路径（与 data/corpse_campus/advancements 下文件一一对应） ───────
    private static final ResourceLocation ROOT =
            new ResourceLocation(NS, "root/root");
    private static final ResourceLocation FIRST_DEATH =
            new ResourceLocation(NS, "finale/first_death");
    private static final ResourceLocation ENDLESS_LIFE_CAST =
            new ResourceLocation(NS, "finale/endless_life_cast");
    private static final ResourceLocation REWIND_MIRROR =
            new ResourceLocation(NS, "finale/rewind_mirror");
    private static final ResourceLocation FORTY_CAP =
            new ResourceLocation(NS, "finale/forty_cap");
    private static final ResourceLocation GRAFTER_CROSS =
            new ResourceLocation(NS, "finale/grafter_cross");
    private static final ResourceLocation ISOLATED_ASCEND =
            new ResourceLocation(NS, "finale/isolated_ascend");

    /** 五流派 path → 踏入成就路径。 */
    private static final Map<String, ResourceLocation> SEQ_ENTER = Map.of(
            "xujing",  new ResourceLocation(NS, "sequence/xujing"),
            "rizhao",  new ResourceLocation(NS, "sequence/rizhao"),
            "dongyue", new ResourceLocation(NS, "sequence/dongyue"),
            "yuzhe",   new ResourceLocation(NS, "sequence/yuzhe"),
            "shengqi", new ResourceLocation(NS, "sequence/shengqi")
    );

    /** 五流派 path → B→A 进阶成就。 */
    private static final Map<String, ResourceLocation> SEQ_ADVANCE = Map.of(
            "xujing",  new ResourceLocation(NS, "advance/xujing"),
            "rizhao",  new ResourceLocation(NS, "advance/rizhao"),
            "dongyue", new ResourceLocation(NS, "advance/dongyue"),
            "yuzhe",   new ResourceLocation(NS, "advance/yuzhe"),
            "shengqi", new ResourceLocation(NS, "advance/shengqi")
    );

    /** 五流派 path → A→S 登顶成就。 */
    private static final Map<String, ResourceLocation> SEQ_SUMMIT = Map.of(
            "xujing",  new ResourceLocation(NS, "summit/xujing"),
            "rizhao",  new ResourceLocation(NS, "summit/rizhao"),
            "dongyue", new ResourceLocation(NS, "summit/dongyue"),
            "yuzhe",   new ResourceLocation(NS, "summit/yuzhe"),
            "shengqi", new ResourceLocation(NS, "summit/shengqi")
    );

    /** spell id (short path) → L6 学习成就路径。15 个 A/S 法术全部登记。 */
    private static final Map<String, ResourceLocation> SPELL_LEARN = Map.ofEntries(
            Map.entry("recorder_officer",  new ResourceLocation(NS, "ability/recorder_officer")),
            Map.entry("elementalist",      new ResourceLocation(NS, "ability/elementalist")),
            Map.entry("rewind_worm",       new ResourceLocation(NS, "ability/rewind_worm")),
            Map.entry("light_prayer",      new ResourceLocation(NS, "ability/light_prayer")),
            Map.entry("midas_touch",       new ResourceLocation(NS, "ability/midas_touch")),
            Map.entry("golden_crow_sun",   new ResourceLocation(NS, "ability/golden_crow_sun")),
            Map.entry("impermanence_monk", new ResourceLocation(NS, "ability/impermanence_monk")),
            Map.entry("executioner",       new ResourceLocation(NS, "ability/executioner")),
            Map.entry("great_necromancer", new ResourceLocation(NS, "ability/great_necromancer")),
            Map.entry("mimic",             new ResourceLocation(NS, "ability/mimic")),
            Map.entry("life_thief",        new ResourceLocation(NS, "ability/life_thief")),
            Map.entry("authority_grasp",   new ResourceLocation(NS, "ability/authority_grasp")),
            Map.entry("grafter",           new ResourceLocation(NS, "ability/grafter")),
            Map.entry("ferryman",          new ResourceLocation(NS, "ability/ferryman")),
            Map.entry("endless_life",      new ResourceLocation(NS, "ability/endless_life"))
    );

    /** L2 自然觉醒里两个 impossible 占位（爬行 / 副本）对应的路径。 */
    private static final ResourceLocation NATURAL_CRAWL =
            new ResourceLocation(NS, "natural/danger_sense_crawl");
    private static final ResourceLocation NATURAL_DUNGEON =
            new ResourceLocation(NS, "natural/dungeon");

    private AnomalyAdvancements() {}

    // ─── 公共 API ────────────────────────────────────────────────────────

    /** 发放（或补发）异常特性法术书成功时调用。 */
    public static void onBookGranted(@Nullable ServerPlayer player) {
        awardAll(player, ROOT);
    }

    /** 玩家首次觉醒到某流派（B 级）。schoolId 可为 ResourceLocation 或其 path。 */
    public static void onSequenceEntered(@Nullable ServerPlayer player, @Nullable String schoolPath) {
        if (schoolPath == null) return;
        awardAll(player, SEQ_ENTER.get(schoolPath.toLowerCase(Locale.ROOT)));
    }

    /** 玩家最高位阶达到 A。 */
    public static void onRankAdvancedToA(@Nullable ServerPlayer player, @Nullable String schoolPath) {
        if (schoolPath == null) return;
        awardAll(player, SEQ_ADVANCE.get(schoolPath.toLowerCase(Locale.ROOT)));
    }

    /** 玩家最高位阶达到 S。 */
    public static void onRankAdvancedToS(@Nullable ServerPlayer player, @Nullable String schoolPath) {
        if (schoolPath == null) return;
        awardAll(player, SEQ_SUMMIT.get(schoolPath.toLowerCase(Locale.ROOT)));
    }

    /** 成功把一条 A/S 法术写入玩家的异常法术书。spellId 传 ResourceLocation.toString() 或 path。 */
    public static void onSpellLearned(@Nullable ServerPlayer player, @Nullable String spellIdOrPath) {
        if (spellIdOrPath == null) return;
        int slash = spellIdOrPath.indexOf(':');
        String path = slash >= 0 ? spellIdOrPath.substring(slash + 1) : spellIdOrPath;
        awardAll(player, SPELL_LEARN.get(path.toLowerCase(Locale.ROOT)));
    }

    /** 玩家连续爬行达到 120 秒阈值。 */
    public static void onNaturalCrawlReached(@Nullable ServerPlayer player) {
        awardAll(player, NATURAL_CRAWL);
    }

    /** 副本探索触发（预留给未来副本子系统）。 */
    public static void onDungeonCleared(@Nullable ServerPlayer player) {
        awardAll(player, NATURAL_DUNGEON);
    }

    /** 施放生生不息瞬间（全服封禁动作）。 */
    public static void onEndlessLifeCast(@Nullable ServerPlayer player) {
        awardAll(player, ENDLESS_LIFE_CAST);
    }

    /** 使用回溯之虫成功进入镜像备份维度。 */
    public static void onRewindMirrorEntered(@Nullable ServerPlayer player) {
        awardAll(player, REWIND_MIRROR);
    }

    /** 全局异常上限 40 触达瞬间。建议对全服在线玩家 broadcast。 */
    public static void onFortyCapReached(@Nullable MinecraftServer server) {
        if (server == null) return;
        Advancement advancement = resolve(server, FORTY_CAP);
        if (advancement == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.getAdvancements().award(advancement, "witness");
        }
    }

    /** 嫁接师跨 4 流派吸收达成。 */
    public static void onGrafterCrossFour(@Nullable ServerPlayer player) {
        awardAll(player, GRAFTER_CROSS);
    }

    /** 未装嫁接师独立完成某流派 B→S 登顶。 */
    public static void onIsolatedAscend(@Nullable ServerPlayer player) {
        awardAll(player, ISOLATED_ASCEND);
    }

    // ─── 内部工具 ────────────────────────────────────────────────────────

    private static void awardAll(@Nullable ServerPlayer player, @Nullable ResourceLocation id) {
        if (player == null || id == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Advancement advancement = resolve(server, id);
        if (advancement == null) return;
        PlayerAdvancements progress = player.getAdvancements();
        for (String key : advancement.getCriteria().keySet()) {
            progress.award(advancement, key);
        }
    }

    private static @Nullable Advancement resolve(MinecraftServer server, ResourceLocation id) {
        ServerAdvancementManager manager = server.getAdvancements();
        return manager == null ? null : manager.getAdvancement(id);
    }
}
