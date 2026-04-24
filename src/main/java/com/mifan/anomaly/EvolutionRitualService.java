package com.mifan.anomaly;

import com.mifan.corpsecampus;
import com.mifan.item.AnomalyTraitItem;
import com.mifan.item.SpellEmbryoItem;
import com.mifan.registry.ModSchools;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进化觉醒仪式服务：注册 10 条仪式配方，提供"融合"入口。
 *
 * 触发路径：玩家 {@code shift + 右键} 祭坛中心方块 → {@link EvolutionEventHandler} → 本类 {@link #attemptFusion}。
 *
 * 融合算法：
 *   1. 校验祭坛结构（{@link EvolutionAltarStructure#matches}）
 *   2. 扫描 center 上方 2 格 AABB 内的 {@link ItemEntity}，收集物品计数
 *   3. 扫描 center 半径 5 格内的 CorpseEntity（反射，避免 Corpse mod 硬依赖），得到可消耗尸体数量
 *   4. 匹配 10 条已注册 {@link EvolutionRecipe} 里唯一满足"结构 + 物品精确 + 尸体足量 + 流派 B 特性存在"的那条
 *   5. 消耗 item 与 corpse entity，生成胚胎 ItemStack 丢在 center.above()
 *
 * 关键约束：
 *   - 物品清单按 Item 类型精确比对（不做 tag 匹配），并且**必须完全一致**（没有多余物品，没有多于/少于需要数量的同类）
 *   - 已 A 级玩家走融合 → 立即拒绝，给 {@code message.corpse_campus.evolution_denied.already_a}
 *   - 必须持有绑定异常书且已觉醒 + 主序列等于本流派；否则返回 {@code evolution_denied.wrong_sequence}
 */
public final class EvolutionRitualService {

    private static final List<EvolutionRecipe> RECIPES = new ArrayList<>();

    private EvolutionRitualService() {
    }

    /** mod 加载期一次性注册所有 10 条配方。 */
    public static void init() {
        if (!RECIPES.isEmpty()) {
            return;
        }
        registerXujing();
        registerRizhao();
        registerDongyue();
        registerYuzhe();
        registerShengqi();
    }

    /** 获取所有已注册配方（只读）。供调试指令 / 文档使用。 */
    public static List<EvolutionRecipe> getAllRecipes() {
        return List.copyOf(RECIPES);
    }

    private static void registerXujing() {
        // 记录官：3 附魔书 + 64 纸
        RECIPES.add(new EvolutionRecipe(
                EvolutionAltarStructure.XUJING,
                id("recorder_officer"),
                Map.of(Items.ENCHANTED_BOOK, 3, Items.PAPER, 64),
                0));
        // 元素使：铜 / 铁 / 金 / 钻石 / 绿宝石 / 青金石 / 下界合金锭 各 1
        RECIPES.add(new EvolutionRecipe(
                EvolutionAltarStructure.XUJING,
                id("elementalist"),
                orderedMap(
                        Items.COPPER_INGOT, 1,
                        Items.IRON_INGOT, 1,
                        Items.GOLD_INGOT, 1,
                        Items.DIAMOND, 1,
                        Items.EMERALD, 1,
                        Items.LAPIS_LAZULI, 1,
                        Items.NETHERITE_INGOT, 1),
                0));
    }

    private static void registerRizhao() {
        // 祈光人：16 烈焰棒 + 64 马铃薯 + 64 胡萝卜
        RECIPES.add(new EvolutionRecipe(
                EvolutionAltarStructure.RIZHAO,
                id("light_prayer"),
                orderedMap(Items.BLAZE_ROD, 16, Items.POTATO, 64, Items.CARROT, 64),
                0));
        // 点金客：64 木炭 + 16 火药
        RECIPES.add(new EvolutionRecipe(
                EvolutionAltarStructure.RIZHAO,
                id("midas_touch"),
                orderedMap(Items.CHARCOAL, 64, Items.GUNPOWDER, 16),
                0));
    }

    private static void registerDongyue() {
        // 无常僧：3 具玩家尸体 + 32 腐肉
        RECIPES.add(new EvolutionRecipe(
                EvolutionAltarStructure.DONGYUE,
                id("impermanence_monk"),
                orderedMap(Items.ROTTEN_FLESH, 32),
                3));
        // 刽子手：3 把耐久 < 100 的剑 + 3 组腐肉
        // 剑的耐久校验走特殊路径，Map 里仅登记类型 + 数量，融合时再过滤
        RECIPES.add(new EvolutionRecipe(
                EvolutionAltarStructure.DONGYUE,
                id("executioner"),
                orderedMap(Items.ROTTEN_FLESH, 192),
                0,
                List.of(new SwordWithLowDurability(3))));
    }

    private static void registerYuzhe() {
        // 模仿者：16 末影之眼 + 1 水瓶
        RECIPES.add(new EvolutionRecipe(
                EvolutionAltarStructure.YUZHE,
                id("mimic"),
                orderedMap(Items.ENDER_EYE, 16, Items.POTION, 1),
                0));
        // 盗命客：1 个其他流派 B 特性 + 1 水瓶 + (规范要求本流派 B 特性通用处理)
        RECIPES.add(new EvolutionRecipe(
                EvolutionAltarStructure.YUZHE,
                id("life_thief"),
                orderedMap(Items.POTION, 1),
                0,
                List.of(new OtherSchoolBRankTrait())));
    }

    private static void registerShengqi() {
        // 嫁接师：8 金苹果 + 2 玩家尸体 + 16 回响碎片
        RECIPES.add(new EvolutionRecipe(
                EvolutionAltarStructure.SHENGQI,
                id("grafter"),
                orderedMap(Items.ENCHANTED_GOLDEN_APPLE, 8, Items.ECHO_SHARD, 16),
                2));
        // 摆渡人：1 不死图腾 + 2 玩家尸体 + 4 骷髅头颅
        RECIPES.add(new EvolutionRecipe(
                EvolutionAltarStructure.SHENGQI,
                id("ferryman"),
                orderedMap(Items.TOTEM_OF_UNDYING, 1, Items.SKELETON_SKULL, 4),
                2));
    }

    /**
     * 核心入口：玩家 shift+右键祭坛中心方块时调用。
     * 返回值：true = 融合成功（生成胚胎）；false = 任何一项校验失败（原地无事发生）。
     */
    public static boolean attemptFusion(ServerLevel level, BlockPos center, ServerPlayer player) {
        EvolutionAltarStructure altar = EvolutionAltarStructure.fromCenterBlock(level.getBlockState(center));
        if (altar == null) {
            return false;
        }
        if (!altar.matches(level, center)) {
            player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_altar_incomplete")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }

        // 已 A 级拦截
        if (EvolutionAwakeningService.isAlreadyARankOrHigher(player)) {
            player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.already_a")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }

        // 主序列校验：必须是该流派
        ItemStack book = AnomalyBookService.getPlayerBook(player);
        if (book.isEmpty() || !AnomalyBookService.isAwakened(book)) {
            player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.not_awakened")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }
        ResourceLocation mainSeq = AnomalyBookService.getMainSequenceId(book);
        if (mainSeq == null || !mainSeq.equals(altar.schoolId())) {
            player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.wrong_sequence")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }

        // 收集祭坛上的 ItemEntity
        AABB itemBox = new AABB(center).inflate(1.2D, 2.0D, 1.2D);
        List<ItemEntity> itemEntities = level.getEntitiesOfClass(ItemEntity.class, itemBox, ItemEntity::isAlive);

        // 扫描 CorpseEntity（反射）
        AABB corpseBox = new AABB(center).inflate(5.0D);
        List<Entity> corpseEntities = scanCorpseEntities(level, corpseBox);

        // 匹配配方
        EvolutionRecipe picked = null;
        for (EvolutionRecipe recipe : RECIPES) {
            if (!recipe.altar().equals(altar)) {
                continue;
            }
            if (recipe.matches(itemEntities, corpseEntities, altar.schoolId())) {
                picked = recipe;
                break;
            }
        }
        if (picked == null) {
            player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_recipe_not_matched")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return false;
        }

        // 消耗材料：item + corpse + B 特性
        picked.consume(itemEntities, corpseEntities, altar.schoolId());

        // 生成胚胎丢在 center 上方
        ItemStack embryo = SpellEmbryoItem.createFor(picked.outputSpellId());
        ItemEntity drop = new ItemEntity(level, center.getX() + 0.5D, center.getY() + 1.2D, center.getZ() + 0.5D, embryo);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);

        // 视听反馈
        level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                center.getX() + 0.5D, center.getY() + 1.2D, center.getZ() + 0.5D,
                40, 0.6D, 0.8D, 0.6D, 0.05D);

        AnomalyBookService.SpellSpec spec = AnomalyBookService.getSpellSpec(picked.outputSpellId());
        String zhName = spec != null ? spec.zhName() : picked.outputSpellId().getPath();
        player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_embryo_born", zhName)
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        return true;
    }

    private static List<Entity> scanCorpseEntities(ServerLevel level, AABB box) {
        Class<?> corpseClass = EvolutionCorpseBridge.CORPSE_ENTITY_CLASS;
        if (corpseClass == null) {
            return List.of();
        }
        List<Entity> all = level.getEntities((Entity) null, box, e -> corpseClass.isInstance(e) && e.isAlive());
        return all;
    }

    // ────────────────────────────────────────────────────────────────────
    // 内部数据结构：EvolutionRecipe + ExtraRequirement 扩展点
    // ────────────────────────────────────────────────────────────────────

    public static final class EvolutionRecipe {
        private final EvolutionAltarStructure altar;
        private final ResourceLocation outputSpellId;
        /** Map<Item, count>：必须精确等量；不接受多余堆量 */
        private final LinkedHashMap<Item, Integer> itemInputs;
        private final int requiredCorpses;
        private final List<ExtraRequirement> extras;

        EvolutionRecipe(EvolutionAltarStructure altar, ResourceLocation outputSpellId,
                        Map<Item, Integer> itemInputs, int requiredCorpses) {
            this(altar, outputSpellId, itemInputs, requiredCorpses, List.of());
        }

        EvolutionRecipe(EvolutionAltarStructure altar, ResourceLocation outputSpellId,
                        Map<Item, Integer> itemInputs, int requiredCorpses, List<ExtraRequirement> extras) {
            this.altar = altar;
            this.outputSpellId = outputSpellId;
            this.itemInputs = new LinkedHashMap<>(itemInputs);
            this.requiredCorpses = requiredCorpses;
            this.extras = List.copyOf(extras);
        }

        public EvolutionAltarStructure altar() { return altar; }
        public ResourceLocation outputSpellId() { return outputSpellId; }
        public Map<Item, Integer> itemInputs() { return Map.copyOf(itemInputs); }
        public int requiredCorpses() { return requiredCorpses; }
        public List<ExtraRequirement> extras() { return extras; }

        boolean matches(List<ItemEntity> itemEntities, List<Entity> corpses, ResourceLocation schoolId) {
            // 1) B 级流派特性必须存在（恰好 1 枚本流派 B 级特性）
            if (countTraitBOfSchool(itemEntities, schoolId) < 1) {
                return false;
            }
            // 2) 物品需求：每种 Item 数量精确匹配
            Map<Item, Integer> aggregated = aggregateNonTraitItems(itemEntities);
            for (var entry : itemInputs.entrySet()) {
                Integer actual = aggregated.get(entry.getKey());
                if (actual == null || actual != entry.getValue()) {
                    return false;
                }
            }
            // 3) 尸体数量
            if (corpses.size() < requiredCorpses) {
                return false;
            }
            // 4) 额外需求（低耐久剑 / 其他流派 B 特性等）
            for (ExtraRequirement extra : extras) {
                if (!extra.matches(itemEntities, schoolId)) {
                    return false;
                }
            }
            return true;
        }

        void consume(List<ItemEntity> itemEntities, List<Entity> corpses, ResourceLocation schoolId) {
            // 消耗 B 特性 1 枚
            consumeOneTraitB(itemEntities, schoolId);
            // 消耗常规物品
            for (var entry : itemInputs.entrySet()) {
                consumeItem(itemEntities, entry.getKey(), entry.getValue());
            }
            // 消耗尸体
            for (int i = 0; i < requiredCorpses && i < corpses.size(); i++) {
                corpses.get(i).discard();
            }
            // 额外需求消耗
            for (ExtraRequirement extra : extras) {
                extra.consume(itemEntities, schoolId);
            }
        }
    }

    /** 非标准物品条件的扩展点：低耐久剑、其他流派 B 特性等。 */
    public interface ExtraRequirement {
        boolean matches(List<ItemEntity> items, ResourceLocation schoolId);
        void consume(List<ItemEntity> items, ResourceLocation schoolId);
    }

    /** 刽子手专用：3 把剑，耐久值 < 100（即 damageValue > maxDamage - 100）。 */
    private static final class SwordWithLowDurability implements ExtraRequirement {
        private final int count;
        SwordWithLowDurability(int count) { this.count = count; }

        @Override
        public boolean matches(List<ItemEntity> items, ResourceLocation schoolId) {
            int found = 0;
            for (ItemEntity e : items) {
                ItemStack s = e.getItem();
                if (isQualifiedSword(s)) {
                    found += s.getCount();
                }
            }
            return found >= count;
        }

        @Override
        public void consume(List<ItemEntity> items, ResourceLocation schoolId) {
            int remaining = count;
            for (ItemEntity e : items) {
                if (remaining <= 0) break;
                ItemStack s = e.getItem();
                if (isQualifiedSword(s)) {
                    int take = Math.min(remaining, s.getCount());
                    s.shrink(take);
                    if (s.isEmpty()) {
                        e.discard();
                    }
                    remaining -= take;
                }
            }
        }

        private static boolean isQualifiedSword(ItemStack s) {
            if (s.isEmpty()) return false;
            String path = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath();
            if (!path.endsWith("_sword")) return false;
            if (!s.isDamageableItem()) return false;
            int remaining = s.getMaxDamage() - s.getDamageValue();
            return remaining < 100;
        }
    }

    /** 盗命客专用：1 枚"其他流派 B 特性"（schoolId ≠ 愚者）。 */
    private static final class OtherSchoolBRankTrait implements ExtraRequirement {
        @Override
        public boolean matches(List<ItemEntity> items, ResourceLocation schoolId) {
            for (ItemEntity e : items) {
                ItemStack s = e.getItem();
                if (s.getItem() instanceof AnomalyTraitItem trait
                        && trait.getRank() == AnomalySpellRank.B
                        && !trait.getSchoolId().equals(schoolId)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void consume(List<ItemEntity> items, ResourceLocation schoolId) {
            for (ItemEntity e : items) {
                ItemStack s = e.getItem();
                if (s.getItem() instanceof AnomalyTraitItem trait
                        && trait.getRank() == AnomalySpellRank.B
                        && !trait.getSchoolId().equals(schoolId)) {
                    s.shrink(1);
                    if (s.isEmpty()) e.discard();
                    return;
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 工具：物品聚合 / 消耗 / 特性过滤
    // ────────────────────────────────────────────────────────────────────

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

    private static Map<Item, Integer> aggregateNonTraitItems(List<ItemEntity> items) {
        Map<Item, Integer> agg = new HashMap<>();
        for (ItemEntity e : items) {
            ItemStack s = e.getItem();
            if (s.isEmpty()) continue;
            // B 级特性独立处理；剑的耐久判定由 ExtraRequirement 负责
            if (s.getItem() instanceof AnomalyTraitItem) continue;
            String path = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath();
            if (path.endsWith("_sword")) continue;
            agg.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return agg;
    }

    private static int countTraitBOfSchool(List<ItemEntity> items, ResourceLocation schoolId) {
        int count = 0;
        for (ItemEntity e : items) {
            ItemStack s = e.getItem();
            if (s.getItem() instanceof AnomalyTraitItem trait
                    && trait.getRank() == AnomalySpellRank.B
                    && trait.getSchoolId().equals(schoolId)) {
                count += s.getCount();
            }
        }
        return count;
    }

    private static void consumeOneTraitB(List<ItemEntity> items, ResourceLocation schoolId) {
        for (ItemEntity e : items) {
            ItemStack s = e.getItem();
            if (s.getItem() instanceof AnomalyTraitItem trait
                    && trait.getRank() == AnomalySpellRank.B
                    && trait.getSchoolId().equals(schoolId)) {
                s.shrink(1);
                if (s.isEmpty()) e.discard();
                return;
            }
        }
    }

    private static void consumeItem(List<ItemEntity> items, Item item, int count) {
        int remaining = count;
        for (ItemEntity e : items) {
            if (remaining <= 0) break;
            ItemStack s = e.getItem();
            if (s.getItem() == item) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                if (s.isEmpty()) e.discard();
                remaining -= take;
            }
        }
    }
}
