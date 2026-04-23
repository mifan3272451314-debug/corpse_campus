package com.mifan.anomaly;

import com.mifan.corpsecampus;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = corpsecampus.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AnomalyConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue GLOBAL_CAP_ENABLED = BUILDER
            .comment("是否启用全服异常者数量上限（true = 开启，false = 关闭）")
            .define("limit.globalCapEnabled", true);

    private static final ForgeConfigSpec.IntValue GLOBAL_CAP_VALUE = BUILDER
            .comment("全服异常者数量上限值，默认 40")
            .defineInRange("limit.globalCapValue", 40, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.BooleanValue COUNT_AWAKENED_PLAYERS = BUILDER
            .comment("是否将书内有法术的玩家计入异常者人数")
            .define("limit.countAwakenedPlayers", true);

    private static final ForgeConfigSpec.BooleanValue AUTO_RECOUNT_ON_SERVER_START = BUILDER
            .comment("服务端启动后自动将所有在线玩家纳入觉醒统计（离线玩家保留已存记录）")
            .define("limit.autoRecountOnServerStart", true);

    // ────────────── 核心吞噬规则（用户 2026-04-23 需求）──────────────

    private static final ForgeConfigSpec.BooleanValue SCROLL_RESPECT_CAP = BUILDER
            .comment("异常异能核心（SpellScrollItem）走觉醒通道时是否受 40 人上限拦截（默认 true）")
            .define("rules.scrollRespectCap", true);

    private static final ForgeConfigSpec.BooleanValue DIRECT_AWAKEN_RESPECT_CAP = BUILDER
            .comment("指定异能核心（DesignatedAbilityItem）是否受 40 人上限拦截（默认 false，管理员专用）")
            .define("rules.directAwakenRespectCap", false);

    private static final ForgeConfigSpec.BooleanValue RANK_BLESSING_RESPECT_CAP = BUILDER
            .comment("B/A/S 位阶核心（RankBlessingItem）是否受 40 人上限拦截（默认 false，管理员专用）")
            .define("rules.rankBlessingRespectCap", false);

    private static final ForgeConfigSpec.BooleanValue REQUIRE_B_FOR_A = BUILDER
            .comment("吃 A 级核心前必须已拥有 B 位阶（默认 true）")
            .define("rules.requireBForA", true);

    private static final ForgeConfigSpec.BooleanValue REQUIRE_A_FOR_S = BUILDER
            .comment("吃 S 级核心前必须已拥有 A 位阶（默认 true）")
            .define("rules.requireAForS", true);

    private static final ForgeConfigSpec.BooleanValue REJECT_SAME_OR_LOWER_RANK = BUILDER
            .comment("已有位阶 >= 核心位阶时拒绝吞噬（默认 true：有 A 者 B/A 都吞不了，有 S 者 B/A/S 都吞不了）")
            .define("rules.rejectSameOrLowerRank", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean globalCapEnabled = true;
    public static int globalCapValue = 40;
    public static boolean countAwakenedPlayers = true;
    public static boolean autoRecountOnServerStart = true;

    public static boolean scrollRespectCap = true;
    public static boolean directAwakenRespectCap = false;
    public static boolean rankBlessingRespectCap = false;
    public static boolean requireBForA = true;
    public static boolean requireAForS = true;
    public static boolean rejectSameOrLowerRank = true;

    private AnomalyConfig() {}

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        globalCapEnabled = GLOBAL_CAP_ENABLED.get();
        globalCapValue = GLOBAL_CAP_VALUE.get();
        countAwakenedPlayers = COUNT_AWAKENED_PLAYERS.get();
        autoRecountOnServerStart = AUTO_RECOUNT_ON_SERVER_START.get();

        scrollRespectCap = SCROLL_RESPECT_CAP.get();
        directAwakenRespectCap = DIRECT_AWAKEN_RESPECT_CAP.get();
        rankBlessingRespectCap = RANK_BLESSING_RESPECT_CAP.get();
        requireBForA = REQUIRE_B_FOR_A.get();
        requireAForS = REQUIRE_A_FOR_S.get();
        rejectSameOrLowerRank = REJECT_SAME_OR_LOWER_RANK.get();
    }
}
