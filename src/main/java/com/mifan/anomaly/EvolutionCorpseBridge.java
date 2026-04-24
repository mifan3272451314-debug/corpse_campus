package com.mifan.anomaly;

/**
 * 反射桥：绕过 Corpse mod (henkelmax' corpse-forge) 的编译期硬依赖，
 * 在运行期查到 {@code de.maxhenkel.corpse.entities.CorpseEntity} Class。
 *
 * 当 Corpse mod 未加载时 {@link #CORPSE_ENTITY_CLASS} 为 null；调用方在 null 分支下必须
 * 降级为"0 具尸体"，避免 NPE 并允许服务端正常启动。
 */
public final class EvolutionCorpseBridge {

    /** 运行期查表到的 Corpse mod 玩家尸体实体类；mod 缺失时为 null。 */
    public static final Class<?> CORPSE_ENTITY_CLASS = resolveCorpseClass();

    private EvolutionCorpseBridge() {
    }

    private static Class<?> resolveCorpseClass() {
        try {
            return Class.forName("de.maxhenkel.corpse.entities.CorpseEntity");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static boolean isCorpsePresent() {
        return CORPSE_ENTITY_CLASS != null;
    }
}
