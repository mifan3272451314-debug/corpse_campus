package com.mifan.anomaly;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 反射桥：绕过 Corpse mod (henkelmax/corpse-forge) 的编译期硬依赖，运行期查 CorpseEntity 类
 * + 执行"落物品 + 销毁尸体"。
 *
 * 查到的关键方法（反射调用安全）：
 *   - {@code CorpseEntity#getDeath() -> de.maxhenkel.corpse.corelib.death.Death}
 *   - {@code Death#getAllItems() -> NonNullList<ItemStack>}（主物品栏 + 装备 + 副手 + 额外物品全集）
 *
 * **关键不变量**：Corpse mod 的 `CorpseEntity#discard()` **不会**自动掉落尸体内的玩家物品。
 * 本 bridge 的 {@link #dropContentsAndDiscard} 在 discard 前把所有物品生成 ItemEntity 掉在尸体原位，
 * 确保仪式祭坛消耗尸体时玩家不会"凭空失去"主物品栏 / 装备。
 */
public final class EvolutionCorpseBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvolutionCorpseBridge.class);

    /** 运行期查表到的 Corpse mod 玩家尸体实体类；mod 缺失时为 null。 */
    public static final Class<?> CORPSE_ENTITY_CLASS = resolveCorpseClass();

    private static final Method GET_DEATH = resolveMethod(CORPSE_ENTITY_CLASS, "getDeath");
    private static final Method GET_ALL_ITEMS = resolveMethod(
            resolveClass("de.maxhenkel.corpse.corelib.death.Death"), "getAllItems");

    private EvolutionCorpseBridge() {
    }

    private static Class<?> resolveCorpseClass() {
        return resolveClass("de.maxhenkel.corpse.entities.CorpseEntity");
    }

    private static Class<?> resolveClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Method resolveMethod(Class<?> clazz, String name) {
        if (clazz == null) return null;
        try {
            return clazz.getMethod(name);
        } catch (NoSuchMethodException e) {
            LOGGER.warn("[corpse_campus] Corpse mod API changed — method '{}' not found on {}", name, clazz.getName());
            return null;
        }
    }

    public static boolean isCorpsePresent() {
        return CORPSE_ENTITY_CLASS != null;
    }

    /**
     * 对一个 CorpseEntity 执行"先掉落所有玩家物品，再 discard 销毁尸体"。
     * 反射失败或 mod 缺失时：**保守不 discard**（宁可让尸体留下，也不吃玩家装备）。
     *
     * @param level  服务端世界
     * @param entity 必须是 CorpseEntity 实例；其他类型无操作
     * @return true = 成功掉物 + discard；false = 跳过（mod 缺失 / 类型不匹配 / 反射异常）
     */
    public static boolean dropContentsAndDiscard(ServerLevel level, Entity entity) {
        if (CORPSE_ENTITY_CLASS == null || GET_DEATH == null || GET_ALL_ITEMS == null) {
            return false;
        }
        if (!CORPSE_ENTITY_CLASS.isInstance(entity)) {
            return false;
        }
        try {
            Object death = GET_DEATH.invoke(entity);
            if (death == null) {
                // 理论不发生：CorpseEntity.getDeath() 始终非 null（mod 本身初始化写入）。
                entity.discard();
                return true;
            }
            Object allItems = GET_ALL_ITEMS.invoke(death);
            if (allItems instanceof NonNullList<?> list) {
                for (Object obj : list) {
                    if (obj instanceof ItemStack stack && !stack.isEmpty()) {
                        ItemEntity drop = new ItemEntity(level,
                                entity.getX(), entity.getY(), entity.getZ(),
                                stack.copy());
                        drop.setDefaultPickUpDelay();
                        level.addFreshEntity(drop);
                    }
                }
            }
            entity.discard();
            return true;
        } catch (Throwable t) {
            // 反射/初始化异常 → 保守不 discard，让玩家能继续找尸体。
            LOGGER.warn("[corpse_campus] CorpseEntity discard via reflection failed; leaving corpse intact", t);
            return false;
        }
    }
}
