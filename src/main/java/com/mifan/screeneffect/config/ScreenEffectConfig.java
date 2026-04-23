package com.mifan.screeneffect.config;

import com.mifan.screeneffect.api.EffectIntensity;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ScreenEffectConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.EnumValue<EffectIntensity> INTENSITY;
    public static final ForgeConfigSpec.DoubleValue COMBAT_ALPHA_MULT;
    public static final ForgeConfigSpec.IntValue COMBAT_DETECT_RADIUS;
    public static final ForgeConfigSpec.IntValue COMBAT_EXIT_DELAY_TICKS;
    public static final ForgeConfigSpec.DoubleValue MASTER_VOLUME;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("screen_effect");
        ENABLED = BUILDER
                .comment("Master toggle for spell screen effects")
                .define("enabled", true);
        INTENSITY = BUILDER
                .comment("Visual intensity: OFF / LOW / MEDIUM / HIGH")
                .defineEnum("intensity", EffectIntensity.MEDIUM);
        COMBAT_ALPHA_MULT = BUILDER
                .comment("Alpha multiplier for mid-ground elements while in combat (0.0~1.0)")
                .defineInRange("combat_alpha_multiplier", 0.4D, 0.0D, 1.0D);
        COMBAT_DETECT_RADIUS = BUILDER
                .comment("Combat detection radius in blocks")
                .defineInRange("combat_detect_radius", 16, 4, 64);
        COMBAT_EXIT_DELAY_TICKS = BUILDER
                .comment("Ticks before combat state clears after leaving danger (20 ticks = 1s)")
                .defineInRange("combat_exit_delay_ticks", 60, 20, 600);
        MASTER_VOLUME = BUILDER
                .comment("Master volume for client-only spell sounds (0.0~1.0)")
                .defineInRange("master_volume", 0.8D, 0.0D, 1.0D);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ScreenEffectConfig() {
    }
}
