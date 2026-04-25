package com.mifan.anomaly;

import com.mifan.item.EvolutionCoreItem;
import com.mifan.item.SpellEmbryoItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 进化觉醒服务：承载 10 条 A 级进化条件的"进度 NBT + 触发判定 + 胚胎→核心转化"。
 *
 * NBT 结构（挂在 `player.getPersistentData()[Player.PERSISTED_NBT_TAG] → AnomalyP0.EvolutionProgress`）：
 * <pre>
 *   EvolutionProgress:
 *     recorder_officer:          // 以 A 级 spellId 的 path 为键
 *       cast_sliding_timestamps: [long, long, ...]   // 记录官：10s 内 2 名施法者的时间戳
 *     elementalist:
 *       flame_seen: boolean
 *       drown_seen: boolean
 *       lightning_seen: boolean
 *     light_prayer:
 *       continuous_light_tick: long
 *     midas_touch:
 *       // 无进度字段 —— 受爆炸伤害瞬时触发
 *     impermanence_monk:
 *       continuous_crouch_tick: long
 *     executioner:
 *       player_kill_count: int
 *     mimic:
 *       // 瞬时：附近 B+ 异能者死亡即触发
 *     life_thief:
 *       attack_sliding_timestamps: [long, long, ...]
 *     grafter:
 *       // 瞬时：法术值 == 0 即触发
 *     ferryman:
 *       // 瞬时：10 格内 5 具尸体即触发
 * </pre>
 *
 * 处理规则（与用户锁定口径一致）：
 *   - 多胚胎无限制并存（含跨流派）。同 tick 只处理背包顺序靠前的第一枚（rule #7）。
 *   - 已 A 级玩家不可进化（rule #3）：{@link #isAlreadyARankOrHigher} 由 RitualService / 各触发点共同守关。
 *   - "其他途径异能者" 由 {@link #isOtherPathAwakener} 统一判定（rule #4）。
 *   - 胚胎 / 核心本体 **不** 在 onPlayerDeath 清理；只清 Progress NBT（rule #6）。
 */
public final class EvolutionAwakeningService {

    private static final String PLAYER_ROOT = "AnomalyP0";
    private static final String EVOLUTION_PROGRESS = "EvolutionProgress";

    private EvolutionAwakeningService() {
    }

    // ────────────────────────────────────────────────────────────────────
    // 守关工具：判定"是否已 A+"、"是否为其他途径已 B 觉醒者"
    // ────────────────────────────────────────────────────────────────────

    public static boolean isAlreadyARankOrHigher(ServerPlayer player) {
        ItemStack book = AnomalyBookService.getPlayerBook(player);
        if (book.isEmpty()) {
            return false;
        }
        AnomalySpellRank highest = AnomalyBookService.getHighestRank(book);
        return highest != null && highest != AnomalySpellRank.B;
    }

    /**
     * 给"其他途径异能者"统一判定（用户口径 #4：MainSequence ≠ 且已 B 级觉醒）。
     * 若 self 尚未觉醒（没绑定主序列），其角度下的"其他途径"判定降级为"对方已觉醒即算"。
     */
    public static boolean isOtherPathAwakener(ServerPlayer self, Player other) {
        if (self == null || other == null || self.getUUID().equals(other.getUUID())) {
            return false;
        }
        ItemStack otherBook = AnomalyBookService.findBookForRead(other);
        if (otherBook.isEmpty() || !AnomalyBookService.isAwakened(otherBook)) {
            return false;
        }
        ResourceLocation otherSeq = AnomalyBookService.getMainSequenceId(otherBook);
        if (otherSeq == null) {
            return false;
        }
        ItemStack selfBook = AnomalyBookService.findBookForRead(self);
        if (selfBook.isEmpty()) {
            return true;
        }
        ResourceLocation selfSeq = AnomalyBookService.getMainSequenceId(selfBook);
        if (selfSeq == null) {
            return true;
        }
        return !selfSeq.equals(otherSeq);
    }

    // ────────────────────────────────────────────────────────────────────
    // 胚胎扫描：按背包顺序（hotbar 0-8 → main 9-35 → offhand）定位首个胚胎
    // ────────────────────────────────────────────────────────────────────

    /** 按背包顺序列出玩家当前所有进化胚胎（hotbar 先于主物品栏先于副手）。 */
    public static List<EmbryoRef> listEmbryos(ServerPlayer player) {
        List<EmbryoRef> out = new ArrayList<>();
        var items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            ResourceLocation spellId = embryoSpellIdOf(s);
            if (spellId != null) {
                out.add(new EmbryoRef(s, spellId, EmbryoLocation.MAIN, i));
            }
        }
        var offhand = player.getInventory().offhand;
        for (int i = 0; i < offhand.size(); i++) {
            ItemStack s = offhand.get(i);
            ResourceLocation spellId = embryoSpellIdOf(s);
            if (spellId != null) {
                out.add(new EmbryoRef(s, spellId, EmbryoLocation.OFFHAND, i));
            }
        }
        return out;
    }

    @Nullable
    private static ResourceLocation embryoSpellIdOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof SpellEmbryoItem)) {
            return null;
        }
        return SpellEmbryoItem.getSpellId(stack);
    }

    /**
     * 把首枚条件达成的胚胎原位替换为同 spellId 的 EvolutionCore（rule #7）。
     * 调用方 = 具体触发条件判定代码；条件达成后调用本方法完成转化 + 视听反馈。
     */
    public static boolean transformEmbryoToCore(ServerPlayer player, ResourceLocation spellId) {
        if (isAlreadyARankOrHigher(player)) {
            // 已 A+：静默拒绝（rule #3）。胚胎保留，但此 spellId 的触发路径不再进化。
            return false;
        }
        List<EmbryoRef> refs = listEmbryos(player);
        for (EmbryoRef ref : refs) {
            if (!ref.spellId.equals(spellId)) {
                continue;
            }
            ItemStack core = EvolutionCoreItem.createFor(spellId);
            core.setCount(Math.max(1, ref.stack.getCount()));
            setAtLocation(player, ref.location, ref.index, core);
            // 清除该 spellId 的进度（完成后无需再跑条件）
            clearProgressForSpell(player, spellId);
            // 视听反馈
            player.level().playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.4F);
            AnomalyBookService.SpellSpec spec = AnomalyBookService.getSpellSpec(spellId);
            String zhName = spec != null ? spec.zhName() : spellId.getPath();
            player.displayClientMessage(
                    Component.translatable("message.corpse_campus.evolution_core_forged", zhName)
                            .withStyle(ChatFormatting.GOLD),
                    false);
            return true;
        }
        return false;
    }

    private static void setAtLocation(ServerPlayer player, EmbryoLocation loc, int index, ItemStack stack) {
        if (loc == EmbryoLocation.MAIN) {
            player.getInventory().items.set(index, stack);
        } else {
            player.getInventory().offhand.set(index, stack);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    /**
     * 强制把玩家手中的物品替换为该 spellId 的进化核心 — 不做 isAlreadyARankOrHigher 拦截。
     *
     * 用途：A→S 进化通道（日轮金乌的弓→核心觉醒）。该通道的前置就是"已 A 级"，
     * 所以 {@link #transformEmbryoToCore} 内置的"已 A+ 拒绝"在此处反向不适用。
     *
     * 仅做位阶字段修改和物品替换；右键核心走 {@code AnomalyBookService.applyScrollSpell}
     * 完成 S 法术装入 + 升位。
     */
    public static void replaceHandStackWithCoreForcing(ServerPlayer player,
                                                        net.minecraft.world.InteractionHand hand,
                                                        ResourceLocation spellId) {
        ItemStack core = com.mifan.item.EvolutionCoreItem.createFor(spellId);
        core.setCount(1);
        player.setItemInHand(hand, core);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        clearProgressForSpell(player, spellId);
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.4F);
        AnomalyBookService.SpellSpec spec = AnomalyBookService.getSpellSpec(spellId);
        String zhName = spec != null ? spec.zhName() : spellId.getPath();
        player.displayClientMessage(
                Component.translatable("message.corpse_campus.evolution_core_forged", zhName)
                        .withStyle(ChatFormatting.GOLD),
                false);
    }

    // ────────────────────────────────────────────────────────────────────
    // 进度 NBT 读写：统一入口，避免字段散落
    // ────────────────────────────────────────────────────────────────────

    public static CompoundTag getOrCreateProgress(Player player, ResourceLocation spellId) {
        CompoundTag all = getOrCreateAllProgress(player);
        String key = spellId.getPath();
        if (!all.contains(key, Tag.TAG_COMPOUND)) {
            all.put(key, new CompoundTag());
        }
        return all.getCompound(key);
    }

    private static CompoundTag getOrCreateAllProgress(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)) {
            persisted.put(PLAYER_ROOT, new CompoundTag());
        }
        CompoundTag anomaly = persisted.getCompound(PLAYER_ROOT);
        if (!anomaly.contains(EVOLUTION_PROGRESS, Tag.TAG_COMPOUND)) {
            anomaly.put(EVOLUTION_PROGRESS, new CompoundTag());
        }
        return anomaly.getCompound(EVOLUTION_PROGRESS);
    }

    public static void clearProgressForSpell(Player player, ResourceLocation spellId) {
        CompoundTag all = getOrCreateAllProgress(player);
        all.remove(spellId.getPath());
    }

    /** 死亡重置：清空所有进化进度（胚胎 / 核心物品**不**清）。 */
    public static void clearProgress(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag persisted = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        persisted.getCompound(PLAYER_ROOT).remove(EVOLUTION_PROGRESS);
    }

    /** 滑窗通用：返回指定 key 下的时间戳列表（long 数组）。 */
    public static long[] getTimestampWindow(Player player, ResourceLocation spellId, String key) {
        CompoundTag progress = getOrCreateProgress(player, spellId);
        return progress.contains(key, Tag.TAG_LONG_ARRAY) ? progress.getLongArray(key) : new long[0];
    }

    public static void setTimestampWindow(Player player, ResourceLocation spellId, String key, long[] values) {
        getOrCreateProgress(player, spellId).putLongArray(key, values);
    }

    /**
     * 向滑窗追加一个时间戳并丢弃早于 (now - windowTicks) 的元素。
     * 返回追加后窗口内的时间戳数量。
     */
    public static int pushTimestampWindowAndCount(Player player, ResourceLocation spellId, String key,
                                                   long now, long windowTicks) {
        long[] existing = getTimestampWindow(player, spellId, key);
        List<Long> kept = new ArrayList<>(existing.length + 1);
        for (long t : existing) {
            if (now - t <= windowTicks) kept.add(t);
        }
        kept.add(now);
        long[] updated = new long[kept.size()];
        for (int i = 0; i < updated.length; i++) updated[i] = kept.get(i);
        setTimestampWindow(player, spellId, key, updated);
        return updated.length;
    }

    /** 滑窗变体：按"独立施法者"去重计数。useUuidDedupe = true 时再维护一份同长度的 UUID 数组（分别存 most/least long）。 */
    public static int pushUniqueActorAndCount(Player player, ResourceLocation spellId, String tsKey,
                                               String mostKey, String leastKey,
                                               long now, long windowTicks, java.util.UUID actor) {
        long[] existingTs = getTimestampWindow(player, spellId, tsKey);
        long[] existingMost = getTimestampWindow(player, spellId, mostKey);
        long[] existingLeast = getTimestampWindow(player, spellId, leastKey);
        int n = Math.min(existingTs.length, Math.min(existingMost.length, existingLeast.length));

        List<Long> keptTs = new ArrayList<>();
        List<Long> keptMost = new ArrayList<>();
        List<Long> keptLeast = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (now - existingTs[i] <= windowTicks) {
                keptTs.add(existingTs[i]);
                keptMost.add(existingMost[i]);
                keptLeast.add(existingLeast[i]);
            }
        }
        // 去重：若 actor 已存在于窗口内，更新其时间戳为 now；否则追加
        boolean found = false;
        for (int i = 0; i < keptMost.size(); i++) {
            if (keptMost.get(i) == actor.getMostSignificantBits()
                    && keptLeast.get(i) == actor.getLeastSignificantBits()) {
                keptTs.set(i, now);
                found = true;
                break;
            }
        }
        if (!found) {
            keptTs.add(now);
            keptMost.add(actor.getMostSignificantBits());
            keptLeast.add(actor.getLeastSignificantBits());
        }

        setTimestampWindow(player, spellId, tsKey, toArray(keptTs));
        setTimestampWindow(player, spellId, mostKey, toArray(keptMost));
        setTimestampWindow(player, spellId, leastKey, toArray(keptLeast));
        return keptTs.size();
    }

    private static long[] toArray(List<Long> list) {
        long[] arr = new long[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    // ────────────────────────────────────────────────────────────────────
    // 内部数据结构
    // ────────────────────────────────────────────────────────────────────

    public enum EmbryoLocation {
        MAIN,
        OFFHAND
    }

    public record EmbryoRef(ItemStack stack, ResourceLocation spellId, EmbryoLocation location, int index) {
    }
}
