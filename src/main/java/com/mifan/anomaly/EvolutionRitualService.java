package com.mifan.anomaly;

import com.mifan.corpsecampus;
import com.mifan.item.AnomalyTraitItem;
import com.mifan.item.SpellEmbryoItem;
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
    private static final List<EvolutionSRecipe> S_RECIPES = new ArrayList<>();

    private EvolutionRitualService() {
    }

    /** mod 加载期一次性注册所有 10 条 B→A 配方 + 5 条 A→S 配方。 */
    public static void init() {
        if (!RECIPES.isEmpty() || !S_RECIPES.isEmpty()) {
            return;
        }
        registerXujing();
        registerRizhao();
        registerDongyue();
        registerYuzhe();
        registerShengqi();
        registerSRanks();
    }

    /** 获取所有已注册 B→A 配方（只读）。供调试指令 / 文档使用。 */
    public static List<EvolutionRecipe> getAllRecipes() {
        return List.copyOf(RECIPES);
    }

    /** 获取所有已注册 A→S 配方（只读）。 */
    public static List<EvolutionSRecipe> getAllSRecipes() {
        return List.copyOf(S_RECIPES);
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
                orderedMap(Items.GOLDEN_APPLE, 8, Items.ECHO_SHARD, 16),
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
        return attemptFusionInternal(level, center, player, false);
    }

    /**
     * 静默自动入口：tick 调度自动触发用。所有校验失败都返回 false 且不弹任何消息（避免 spam）；
     * 成功时仍给玩家显示"胚胎诞生"提示与音效粒子。
     */
    public static boolean attemptFusionAuto(ServerLevel level, BlockPos center, ServerPlayer player) {
        return attemptFusionInternal(level, center, player, true);
    }

    private static boolean attemptFusionInternal(ServerLevel level, BlockPos center, ServerPlayer player, boolean silent) {
        EvolutionAltarStructure altar = EvolutionAltarStructure.fromCenterBlock(level.getBlockState(center));
        if (altar == null) {
            return false;
        }
        if (!silent) {
            org.slf4j.LoggerFactory.getLogger("corpse_campus/evolution").info(
                    "[ritual 3x3] {} shift+right-click {} altar at {}",
                    player.getGameProfile().getName(), altar.schoolId().getPath(), center);
        }
        if (!altar.matches(level, center)) {
            if (!silent) {
                String reason = altar.describeMismatch(level, center, 1);
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_altar_incomplete")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
                if (reason != null) {
                    player.displayClientMessage(Component.literal("[祭坛] " + reason)
                            .withStyle(net.minecraft.ChatFormatting.GRAY), false);
                }
            }
            return false;
        }

        // 已 A 级拦截
        if (EvolutionAwakeningService.isAlreadyARankOrHigher(player)) {
            if (!silent) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.already_a")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
            }
            return false;
        }

        // 主序列校验：必须是该流派
        ItemStack book = AnomalyBookService.getPlayerBook(player);
        if (book.isEmpty() || !AnomalyBookService.isAwakened(book)) {
            if (!silent) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.not_awakened")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
            }
            return false;
        }
        ResourceLocation mainSeq = AnomalyBookService.getMainSequenceId(book);
        if (mainSeq == null || !mainSeq.equals(altar.schoolId())) {
            if (!silent) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.wrong_sequence")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
            }
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
            if (!silent) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_recipe_not_matched")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW), false);
            }
            return false;
        }

        // 消耗材料：item + corpse（带物品落地）+ B 特性
        picked.consume(level, itemEntities, corpseEntities, altar.schoolId());

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

    /**
     * 统计 5×5 底座（含 center 自身）内有多少格是该流派 floorBlock。
     * 用于判定玩家"是否在搭 5×5"——避免对搭 3×3 的玩家弹 [5×5 祭坛] 诊断。
     */
    private static int countFloorIn5x5(ServerLevel level, BlockPos center, EvolutionAltarStructure altar) {
        int count = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (level.getBlockState(center.offset(dx, 0, dz)).is(altar.floorBlock())) {
                    count++;
                }
            }
        }
        return count;
    }

    // ────────────────────────────────────────────────────────────────────
    // 内部数据结构：EvolutionRecipe + ExtraRequirement 扩展点
    // ────────────────────────────────────────────────────────────────────

    public static final class EvolutionRecipe {
        private final EvolutionAltarStructure altar;
        private final ResourceLocation outputSpellId;
        /** Map&lt;Item, count&gt;：每种至少 count 个；玩家多扔的留在原地不消耗。 */
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
            // 2) 物品需求：每种 Item 数量足够即可（≥ 配方需求；多余的留在祭坛不消耗）
            //    旧实现使用 `actual != entry.getValue()` 是 Integer 引用比较，对 >127 的数值
            //    （如刽子手 192）会误判 fail。改成 `<` 自动拆箱按 int 比较，并放宽到 "数量足够即可"。
            Map<Item, Integer> aggregated = aggregateNonTraitItems(itemEntities);
            for (var entry : itemInputs.entrySet()) {
                Integer actual = aggregated.get(entry.getKey());
                if (actual == null || actual < entry.getValue()) {
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

        void consume(ServerLevel level, List<ItemEntity> itemEntities, List<Entity> corpses, ResourceLocation schoolId) {
            // 消耗 B 特性 1 枚
            consumeOneTraitB(itemEntities, schoolId);
            // 消耗常规物品
            for (var entry : itemInputs.entrySet()) {
                consumeItem(itemEntities, entry.getKey(), entry.getValue());
            }
            // 消耗尸体：调反射桥落下玩家物品再 discard，避免静默销毁死者装备
            int consumed = 0;
            for (Entity corpse : corpses) {
                if (consumed >= requiredCorpses) break;
                if (EvolutionCorpseBridge.dropContentsAndDiscard(level, corpse)) {
                    consumed++;
                }
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

    // ════════════════════════════════════════════════════════════════════
    // A → S 级进化通道（5×5 祭坛）
    //   配方统一格式：流派异常石板（TRAIT_*_S）×1 + 下界之星 ×1 + A 级流派特性（TRAIT_*_A）×1
    //   产物：对应流派 S 级 spellId 的胚胎
    //   前置：玩家已 A 级 + 主序列 = 该流派；已 S 级拒绝
    // ════════════════════════════════════════════════════════════════════

    private static void registerSRanks() {
        S_RECIPES.add(new EvolutionSRecipe(EvolutionAltarStructure.XUJING,  id("rewind_worm")));
        S_RECIPES.add(new EvolutionSRecipe(EvolutionAltarStructure.RIZHAO,  id("golden_crow_sun")));
        S_RECIPES.add(new EvolutionSRecipe(EvolutionAltarStructure.DONGYUE, id("great_necromancer")));
        S_RECIPES.add(new EvolutionSRecipe(EvolutionAltarStructure.YUZHE,   id("authority_grasp")));
        S_RECIPES.add(new EvolutionSRecipe(EvolutionAltarStructure.SHENGQI, id("endless_life")));
    }

    /**
     * A→S 通道融合入口。EventHandler 在右键中心方块时**先**调用本方法尝试 5×5 仪式；
     * 若结构不通过（baseRadius=2 校验失败），返回 false，由调用方 fallback 到 {@link #attemptFusion}。
     *
     * 与 B→A 通道的关键区别：
     *   - 5×5 底座（baseRadius=2）
     *   - 玩家最高位阶必须 == A（已 S 级或未 A 级都拒绝）
     *   - 配方不要求 B 级特性；强制 1 件 S 级流派特性 + 1 件 nether_star + 1 件 A 级流派特性，且无其他物品
     *   - 物品扫描 AABB inflate(2.2D, 2.0D, 2.2D)
     *
     * @return true = 命中 5×5 + 完成融合或显式拒绝（结构不完整不算）；false = 5×5 不通过，应继续尝试 B→A
     */
    public static boolean attemptFusionS(ServerLevel level, BlockPos center, ServerPlayer player) {
        return attemptFusionSInternal(level, center, player, false);
    }

    /** 静默自动版（tick 调度用）：所有失败静默 return；成功仍弹提示 + 音效粒子。 */
    public static boolean attemptFusionSAuto(ServerLevel level, BlockPos center, ServerPlayer player) {
        return attemptFusionSInternal(level, center, player, true);
    }

    private static boolean attemptFusionSInternal(ServerLevel level, BlockPos center, ServerPlayer player, boolean silent) {
        EvolutionAltarStructure altar = EvolutionAltarStructure.fromCenterBlock(level.getBlockState(center));
        if (altar == null) {
            return false;
        }
        if (!altar.matches(level, center, 2)) {
            // 5×5 结构残缺：判定玩家是否"在搭 5×5"——5×5 底座（24 格）floor 方块 >= 12 算搭着了。
            // 比"看玩家位阶"更鲁棒：玩家可能位阶 == B/S 但仍在搭 5×5 测试。
            if (!silent && countFloorIn5x5(level, center, altar) >= 12) {
                String reason = altar.describeMismatch(level, center, 2);
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_altar_incomplete")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
                if (reason != null) {
                    player.displayClientMessage(Component.literal("[5×5 祭坛] " + reason)
                            .withStyle(net.minecraft.ChatFormatting.GRAY), false);
                }
                return true;     // 阻止 fallback 到 3×3，避免同时弹两条诊断
            }
            return false;
        }
        if (!silent) {
            org.slf4j.LoggerFactory.getLogger("corpse_campus/evolution").info(
                    "[ritual 5x5] {} interacts with {} S-altar at {}",
                    player.getGameProfile().getName(), altar.schoolId().getPath(), center);
            org.slf4j.LoggerFactory.getLogger("corpse_campus/evolution").info(
                    "[ritual 5x5] {} shift+right-click {} altar at {} (5x5 structure OK)",
                    player.getGameProfile().getName(), altar.schoolId().getPath(), center);
        }

        // 玩家书校验：必须已觉醒 + 最高位阶 == A
        ItemStack book = AnomalyBookService.getPlayerBook(player);
        if (book.isEmpty() || !AnomalyBookService.isAwakened(book)) {
            if (!silent) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.not_awakened")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
            }
            return silent ? false : true;
        }
        AnomalySpellRank highest = AnomalyBookService.getHighestRank(book);
        if (highest == AnomalySpellRank.S) {
            if (!silent) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.already_s")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
                return true;
            }
            return false;
        }
        if (highest != AnomalySpellRank.A) {
            if (!silent) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.not_a_rank")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
                return true;
            }
            return false;
        }

        // 主序列匹配祭坛流派
        ResourceLocation mainSeq = AnomalyBookService.getMainSequenceId(book);
        if (mainSeq == null || !mainSeq.equals(altar.schoolId())) {
            if (!silent) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.wrong_sequence")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
                return true;
            }
            return false;
        }

        // 5×5 上方 AABB 扫描（半径 2.2 略大于祭坛对角，防止漏掉边缘掉落物）
        AABB itemBox = new AABB(center).inflate(2.2D, 2.0D, 2.2D);
        List<ItemEntity> itemEntities = level.getEntitiesOfClass(ItemEntity.class, itemBox, ItemEntity::isAlive);

        // 在 S_RECIPES 里找匹配
        EvolutionSRecipe picked = null;
        for (EvolutionSRecipe recipe : S_RECIPES) {
            if (!recipe.altar().equals(altar)) {
                continue;
            }
            if (recipe.matches(itemEntities, altar.schoolId())) {
                picked = recipe;
                break;
            }
        }
        if (picked == null) {
            if (!silent) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_recipe_not_matched")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW), false);
                return true;
            }
            return false;
        }

        // 消耗材料
        picked.consume(itemEntities, altar.schoolId());

        // 生成 S 胚胎
        ItemStack embryo = SpellEmbryoItem.createFor(picked.outputSpellId());
        ItemEntity drop = new ItemEntity(level, center.getX() + 0.5D, center.getY() + 1.4D, center.getZ() + 0.5D, embryo);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);

        // 视听反馈：S 级用更夸张的音效
        level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, center, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5F, 1.4F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                center.getX() + 0.5D, center.getY() + 1.4D, center.getZ() + 0.5D,
                80, 1.2D, 1.2D, 1.2D, 0.08D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                center.getX() + 0.5D, center.getY() + 1.4D, center.getZ() + 0.5D,
                40, 1.0D, 1.0D, 1.0D, 0.05D);

        AnomalyBookService.SpellSpec spec = AnomalyBookService.getSpellSpec(picked.outputSpellId());
        String zhName = spec != null ? spec.zhName() : picked.outputSpellId().getPath();
        player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_embryo_born", zhName)
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        return true;
    }

    /**
     * A→S 级配方记录。每条对应一个流派的 S 级法术 spellId。
     * 材料固定：1 × TRAIT_*_S（异常石板）+ 1 × NETHER_STAR + 1 × TRAIT_*_A
     */
    public static final class EvolutionSRecipe {
        private final EvolutionAltarStructure altar;
        private final ResourceLocation outputSpellId;

        EvolutionSRecipe(EvolutionAltarStructure altar, ResourceLocation outputSpellId) {
            this.altar = altar;
            this.outputSpellId = outputSpellId;
        }

        public EvolutionAltarStructure altar() { return altar; }
        public ResourceLocation outputSpellId() { return outputSpellId; }

        /**
         * 精确匹配：祭坛上方必须**恰好**有：
         *   1) 1 枚 schoolId 流派的 S 级 AnomalyTraitItem **或** 1 个绑定到该流派的 SlateItem（"愚者石板"等）
         *   2) 1 枚 schoolId 流派的 A 级 AnomalyTraitItem
         *   3) 1 个原版 NETHER_STAR
         * 多任何一种或多余物品都不匹配。SlateItem 与 TRAIT_*_S 等价（玩家用任一即可）。
         */
        boolean matches(List<ItemEntity> items, ResourceLocation schoolId) {
            int sRankCount = 0;
            int aRankCount = 0;
            int netherStarCount = 0;
            for (ItemEntity e : items) {
                ItemStack s = e.getItem();
                if (s.isEmpty()) continue;
                if (s.getItem() instanceof AnomalyTraitItem trait && trait.getSchoolId().equals(schoolId)) {
                    if (trait.getRank() == AnomalySpellRank.S) sRankCount += s.getCount();
                    else if (trait.getRank() == AnomalySpellRank.A) aRankCount += s.getCount();
                    else return false;          // 同流派 B 特性出现 → 不匹配（避免 B 配方被误触发）
                } else if (s.getItem() == Items.NETHER_STAR) {
                    netherStarCount += s.getCount();
                } else if (s.getItem() == com.mifan.registry.ModItems.SLATE.get()) {
                    // 流派石板（NBT 绑定到该流派）等价于 S 级特性
                    String boundPath = com.mifan.item.SlateItem.getBoundSchoolPath(s);
                    if (boundPath != null && schoolId.getPath().equals(boundPath)) {
                        sRankCount += s.getCount();
                    } else {
                        return false;     // 绑定到其他流派 / 未绑定 → 不匹配
                    }
                } else if (s.getItem() instanceof AnomalyTraitItem) {
                    // 其他流派特性出现 → 不匹配
                    return false;
                } else {
                    // 其他任何物品出现 → 不匹配
                    return false;
                }
            }
            return sRankCount == 1 && aRankCount == 1 && netherStarCount == 1;
        }

        void consume(List<ItemEntity> items, ResourceLocation schoolId) {
            // S 级先尝试消耗 TRAIT_*_S，没找到则消耗绑定该流派的 SlateItem
            if (!consumeOneTraitOfRank(items, schoolId, AnomalySpellRank.S)) {
                consumeOneSlate(items, schoolId);
            }
            consumeOneTraitOfRank(items, schoolId, AnomalySpellRank.A);
            consumeItem(items, Items.NETHER_STAR, 1);
        }

        private static boolean consumeOneTraitOfRank(List<ItemEntity> items, ResourceLocation schoolId, AnomalySpellRank rank) {
            for (ItemEntity e : items) {
                ItemStack s = e.getItem();
                if (s.getItem() instanceof AnomalyTraitItem trait
                        && trait.getRank() == rank
                        && trait.getSchoolId().equals(schoolId)) {
                    s.shrink(1);
                    if (s.isEmpty()) e.discard();
                    return true;
                }
            }
            return false;
        }

        private static void consumeOneSlate(List<ItemEntity> items, ResourceLocation schoolId) {
            for (ItemEntity e : items) {
                ItemStack s = e.getItem();
                if (s.getItem() == com.mifan.registry.ModItems.SLATE.get()) {
                    String boundPath = com.mifan.item.SlateItem.getBoundSchoolPath(s);
                    if (boundPath != null && schoolId.getPath().equals(boundPath)) {
                        s.shrink(1);
                        if (s.isEmpty()) e.discard();
                        return;
                    }
                }
            }
        }
    }
}
