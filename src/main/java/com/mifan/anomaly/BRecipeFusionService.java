package com.mifan.anomaly;

import com.mifan.corpsecampus;
import com.mifan.item.AnomalyTraitItem;
import com.mifan.item.EvolutionCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 6 条无祭坛 B 级"扔齐自动合成"配方服务。
 *
 * <p>玩家把所有材料丢在同一区域内（半径 1.5 格），命中任一配方即自动消耗 + 产出 B 级核心
 * （{@link EvolutionCoreItem}，NBT 绑该法术的 spellId）。玩家右键核心走
 * {@link AnomalyBookService#applyScrollSpell} 通道，自动走 applyAwakening（B 级）路径绑序列觉醒。
 *
 * <p>6 条配方对应 6 个原本"配方合成"觉醒条件下的 B 级法术：
 * <ul>
 *   <li>虚境.嗅觉 (olfaction): 1 炼药锅 + 16 腐肉 + 1 发酵蛛眼 + 4 骨头</li>
 *   <li>日兆.亲和 (affinity): 16 烈焰棒 + 64 马铃薯 + 64 胡萝卜 + 64 木炭 + 16 火药</li>
 *   <li>东岳.岱岳 (daiyue): 1 把耐久&lt;100 的剑 + 64 腐肉 + 15 铁锭</li>
 *   <li>愚者.支配 (dominance): 10 发酵蛛眼 + 64 腐肉 + 8 末影之眼 + 3 铁锭 + 3 金锭</li>
 *   <li>圣祈.愈合 (healing): 3 金苹果 + 3 附魔书</li>
 *   <li>圣祈.药师 (apothecary): 16 烈焰棒 + 64 马铃薯 + 64 胡萝卜 + 64 腐肉 + 16 火药 + 32 铁锭</li>
 * </ul>
 *
 * <p>匹配语义："数量足够即可"——玩家丢得超过最低需求也合成；只精确消耗配方所需。
 * 异常特性物品（{@link AnomalyTraitItem}）会被 aggregate 跳过，避免与祭坛 B→A 通道争抢。
 */
public final class BRecipeFusionService {

    private static final Logger LOGGER = LoggerFactory.getLogger("corpse_campus/b_recipe");
    private static final List<BRecipe> RECIPES = new ArrayList<>();

    private BRecipeFusionService() {
    }

    /** mod 加载期一次性注册 6 条配方。挂在 {@code commonSetup.enqueueWork} 上。 */
    public static void init() {
        if (!RECIPES.isEmpty()) {
            return;
        }
        RECIPES.add(new BRecipe(id("olfaction"),
                orderedMap(
                        Items.CAULDRON, 1,
                        Items.ROTTEN_FLESH, 16,
                        Items.FERMENTED_SPIDER_EYE, 1,
                        Items.BONE, 4),
                List.of()));
        RECIPES.add(new BRecipe(id("affinity"),
                orderedMap(
                        Items.BLAZE_ROD, 16,
                        Items.POTATO, 64,
                        Items.CARROT, 64,
                        Items.CHARCOAL, 64,
                        Items.GUNPOWDER, 16),
                List.of()));
        RECIPES.add(new BRecipe(id("daiyue"),
                orderedMap(
                        Items.ROTTEN_FLESH, 64,
                        Items.IRON_INGOT, 15),
                List.of(new SwordWithLowDurability(1))));
        RECIPES.add(new BRecipe(id("dominance"),
                orderedMap(
                        Items.FERMENTED_SPIDER_EYE, 10,
                        Items.ROTTEN_FLESH, 64,
                        Items.ENDER_EYE, 8,
                        Items.IRON_INGOT, 3,
                        Items.GOLD_INGOT, 3),
                List.of()));
        RECIPES.add(new BRecipe(id("healing"),
                orderedMap(
                        Items.GOLDEN_APPLE, 3,
                        Items.ENCHANTED_BOOK, 3),
                List.of()));
        RECIPES.add(new BRecipe(id("apothecary"),
                orderedMap(
                        Items.BLAZE_ROD, 16,
                        Items.POTATO, 64,
                        Items.CARROT, 64,
                        Items.ROTTEN_FLESH, 64,
                        Items.GUNPOWDER, 16,
                        Items.IRON_INGOT, 32),
                List.of()));
        LOGGER.info("[B-fusion] registered {} recipes", RECIPES.size());
    }

    public static List<BRecipe> getAllRecipes() {
        return List.copyOf(RECIPES);
    }

    /**
     * 在 anchor 周围 inflate(1.5) AABB 内尝试合成。命中任一配方 → 消耗物品并在 anchor 位置产出 B 级核心。
     *
     * @return true = 命中并产出了核心；false = 没匹配到任何配方
     */
    public static boolean tryFuseAround(ServerLevel level, ItemEntity anchor) {
        AABB box = new AABB(anchor.blockPosition()).inflate(1.5D);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive);
        if (items.size() < 2) {
            return false;
        }
        for (BRecipe recipe : RECIPES) {
            if (recipe.matches(items)) {
                BlockPos center = anchor.blockPosition();
                recipe.consume(items);
                spawnCore(level, center, recipe.outputSpellId());
                LOGGER.info("[B-fusion] recipe '{}' triggered at {} (anchor entityId={})",
                        recipe.outputSpellId(), center, anchor.getId());
                return true;
            }
        }
        return false;
    }

    private static void spawnCore(ServerLevel level, BlockPos pos, ResourceLocation spellId) {
        ItemStack core = EvolutionCoreItem.createFor(spellId);
        ItemEntity drop = new ItemEntity(level,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, core);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);

        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                30, 0.5D, 0.5D, 0.5D, 0.05D);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, path);
    }

    private static LinkedHashMap<Item, Integer> orderedMap(Object... kv) {
        LinkedHashMap<Item, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((Item) kv[i], (Integer) kv[i + 1]);
        }
        return map;
    }

    // ─── 内部数据结构 ────────────────────────────────────────────────

    /** 物品聚合（按 Item 类型分桶）。跳过：异常特性物品、剑（剑走 ExtraReq 单独处理）。 */
    private static Map<Item, Integer> aggregate(List<ItemEntity> items) {
        Map<Item, Integer> agg = new HashMap<>();
        for (ItemEntity e : items) {
            ItemStack s = e.getItem();
            if (s.isEmpty()) {
                continue;
            }
            if (s.getItem() instanceof AnomalyTraitItem) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (key != null && key.getPath().endsWith("_sword")) {
                continue;
            }
            agg.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return agg;
    }

    private static void consumeItem(List<ItemEntity> items, Item item, int count) {
        int remaining = count;
        for (ItemEntity e : items) {
            if (remaining <= 0) {
                break;
            }
            ItemStack s = e.getItem();
            if (s.getItem() == item) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                if (s.isEmpty()) {
                    e.discard();
                }
                remaining -= take;
            }
        }
    }

    /** 扩展条件接口：低耐久剑等不能用 Item 直接表达的需求。 */
    public interface ExtraReq {
        boolean matches(List<ItemEntity> items);
        void consume(List<ItemEntity> items);
    }

    /** 岱岳专用：n 把耐久 < 100 的剑（任意材质）。 */
    private static final class SwordWithLowDurability implements ExtraReq {
        private final int count;

        SwordWithLowDurability(int count) {
            this.count = count;
        }

        @Override
        public boolean matches(List<ItemEntity> items) {
            int found = 0;
            for (ItemEntity e : items) {
                ItemStack s = e.getItem();
                if (isQualified(s)) {
                    found += s.getCount();
                }
            }
            return found >= count;
        }

        @Override
        public void consume(List<ItemEntity> items) {
            int remaining = count;
            for (ItemEntity e : items) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack s = e.getItem();
                if (isQualified(s)) {
                    int take = Math.min(remaining, s.getCount());
                    s.shrink(take);
                    if (s.isEmpty()) {
                        e.discard();
                    }
                    remaining -= take;
                }
            }
        }

        private static boolean isQualified(ItemStack s) {
            if (s.isEmpty()) {
                return false;
            }
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (key == null || !key.getPath().endsWith("_sword")) {
                return false;
            }
            if (!s.isDamageableItem()) {
                return false;
            }
            return (s.getMaxDamage() - s.getDamageValue()) < 100;
        }
    }

    /** 配方记录：物品需求 + 可选扩展条件 + 产出法术 ID。 */
    public static final class BRecipe {
        private final ResourceLocation outputSpellId;
        private final LinkedHashMap<Item, Integer> itemInputs;
        private final List<ExtraReq> extras;

        BRecipe(ResourceLocation outputSpellId, Map<Item, Integer> itemInputs, List<ExtraReq> extras) {
            this.outputSpellId = outputSpellId;
            this.itemInputs = new LinkedHashMap<>(itemInputs);
            this.extras = List.copyOf(extras);
        }

        public ResourceLocation outputSpellId() {
            return outputSpellId;
        }

        public Map<Item, Integer> itemInputs() {
            return Map.copyOf(itemInputs);
        }

        boolean matches(List<ItemEntity> items) {
            Map<Item, Integer> agg = aggregate(items);
            for (Map.Entry<Item, Integer> entry : itemInputs.entrySet()) {
                Integer actual = agg.get(entry.getKey());
                if (actual == null || actual < entry.getValue()) {
                    return false;
                }
            }
            for (ExtraReq req : extras) {
                if (!req.matches(items)) {
                    return false;
                }
            }
            return true;
        }

        void consume(List<ItemEntity> items) {
            for (Map.Entry<Item, Integer> entry : itemInputs.entrySet()) {
                consumeItem(items, entry.getKey(), entry.getValue());
            }
            for (ExtraReq req : extras) {
                req.consume(items);
            }
        }
    }
}
