package com.mifan.spell;

import com.mifan.corpsecampus;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = corpsecampus.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpellBalanceConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.DoubleValue LIGHT_PRAYER_MONSTER_DAMAGE_MULTIPLIER = BUILDER
            .comment(
                    "祈光人：对敌对生物（Enemy）最终伤害倍率。",
                    "3.0 = 原伤害 ×3（即额外 +200%）；2.0 = ×2（+100%）。",
                    "涉及伤害源：佩戴 LIGHT_PRAYER 效果的玩家所造成的全部伤害，以及光环灼烧在点燃期内的火焰伤害。")
            .defineInRange("light_prayer.monsterDamageMultiplier", 3.0D, 0.0D, 100.0D);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private SpellBalanceConfig() {}

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        AbilityRuntime.LIGHT_PRAYER_MONSTER_BONUS_MULTIPLIER =
                LIGHT_PRAYER_MONSTER_DAMAGE_MULTIPLIER.get().floatValue();
    }
}
