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

    /** 全局不透明度系数，所有特效最终 alpha 再乘此值 */
    public static final ForgeConfigSpec.DoubleValue ALPHA_MULTIPLIER;

    /** 单个技能的不透明度系数（在全局基础上再乘） */
    public static final ForgeConfigSpec.DoubleValue ALPHA_ENDLESS_LIFE;
    public static final ForgeConfigSpec.DoubleValue ALPHA_GOLDEN_CROW_SUN;
    public static final ForgeConfigSpec.DoubleValue ALPHA_GREAT_NECROMANCER;
    public static final ForgeConfigSpec.DoubleValue ALPHA_AUTHORITY_GRASP;
    public static final ForgeConfigSpec.DoubleValue ALPHA_REWIND_WORM;

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
        ALPHA_MULTIPLIER = BUILDER
                .comment("Global opacity multiplier for all screen effects (0.0=invisible, 1.0=full).",
                        "Usually adjusted via in-game settings screen.")
                .defineInRange("alpha_multiplier", 1.0D, 0.0D, 1.0D);

        BUILDER.push("per_spell_alpha");
        ALPHA_ENDLESS_LIFE = BUILDER.defineInRange("endless_life", 1.0D, 0.0D, 1.0D);
        ALPHA_GOLDEN_CROW_SUN = BUILDER.defineInRange("golden_crow_sun", 1.0D, 0.0D, 1.0D);
        ALPHA_GREAT_NECROMANCER = BUILDER.defineInRange("great_necromancer", 1.0D, 0.0D, 1.0D);
        ALPHA_AUTHORITY_GRASP = BUILDER.defineInRange("authority_grasp", 1.0D, 0.0D, 1.0D);
        ALPHA_REWIND_WORM = BUILDER.defineInRange("rewind_worm", 1.0D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ScreenEffectConfig() {
    }

    /** 根据 spell path 查对应 per-spell alpha 配置值 */
    public static float getPerSpellAlpha(String spellPath) {
        return switch (spellPath) {
            case "endless_life" -> ALPHA_ENDLESS_LIFE.get().floatValue();
            case "golden_crow_sun" -> ALPHA_GOLDEN_CROW_SUN.get().floatValue();
            case "great_necromancer" -> ALPHA_GREAT_NECROMANCER.get().floatValue();
            case "authority_grasp" -> ALPHA_AUTHORITY_GRASP.get().floatValue();
            case "rewind_worm" -> ALPHA_REWIND_WORM.get().floatValue();
            default -> 1.0F;
        };
    }
}
