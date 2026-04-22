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

    private static final ForgeConfigSpec.BooleanValue DISABLE_B_DROP_WHEN_FULL = BUILDER
            .comment("满额时禁止 B 级异常特性物品从玩家死亡时掉落")
            .define("limit.disableBDropWhenFull", true);

    private static final ForgeConfigSpec.BooleanValue AUTO_RECOUNT_ON_SERVER_START = BUILDER
            .comment("服务端启动后自动将所有在线玩家纳入觉醒统计（离线玩家保留已存记录）")
            .define("limit.autoRecountOnServerStart", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean globalCapEnabled = true;
    public static int globalCapValue = 40;
    public static boolean countAwakenedPlayers = true;
    public static boolean disableBDropWhenFull = true;
    public static boolean autoRecountOnServerStart = true;

    private AnomalyConfig() {}

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        globalCapEnabled = GLOBAL_CAP_ENABLED.get();
        globalCapValue = GLOBAL_CAP_VALUE.get();
        countAwakenedPlayers = COUNT_AWAKENED_PLAYERS.get();
        disableBDropWhenFull = DISABLE_B_DROP_WHEN_FULL.get();
        autoRecountOnServerStart = AUTO_RECOUNT_ON_SERVER_START.get();
    }
}
