package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.item.AnomalyTraitItem;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class GrafterRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceLocation GRAFTER_ID = ResourceLocation.fromNamespaceAndPath("corpse_campus", "grafter");
    public static final ResourceLocation ENDLESS_LIFE_ID = ResourceLocation.fromNamespaceAndPath("corpse_campus", "endless_life");

    public record EligibleSpell(ResourceLocation id, int level) {
    }

    private GrafterRuntime() {
    }

    public static List<EligibleSpell> collectTransferableSpells(ServerPlayer player) {
        List<EligibleSpell> list = new ArrayList<>();
        for (SpellSlot slot : AnomalyBookService.getPlayerLoadedSpellSlots(player)) {
            ResourceLocation id = slot.getSpell().getSpellResource();
            if (isForbidden(id)) {
                continue;
            }
            list.add(new EligibleSpell(id, slot.getLevel()));
        }
        return list;
    }

    public static boolean isForbidden(ResourceLocation id) {
        return GRAFTER_ID.equals(id) || ENDLESS_LIFE_ID.equals(id);
    }

    /**
     * 嫁接师吸收：从 target 法术书随机抽 1 条非禁用法术写到 caster 身上，
     * 并把这条法术从 target 自己的法术书移除（"抽走"而非"复制"）。
     *
     * 执行顺序：先从 target 移除，确认成功后再写入 caster。
     * 若写入 caster 失败则回滚，把 spell 放回 target，保证不会出现双份。
     *
     * @return 被抽走的法术，若没有候选、移除失败或写入失败返回 null。
     */
    @Nullable
    public static EligibleSpell absorb(ServerPlayer caster, ServerPlayer target) {
        List<EligibleSpell> candidates = collectTransferableSpells(target);
        if (candidates.isEmpty()) {
            return null;
        }
        EligibleSpell chosen = candidates.get(caster.getRandom().nextInt(candidates.size()));
        if (!removeSpellFromOwnedBook(target, chosen)) {
            LOGGER.warn("[Grafter] absorb aborted: failed to remove spell {} from target {}",
                    chosen.id(), target.getGameProfile().getName());
            return null;
        }
        if (!writeSpellIntoBook(caster, chosen)) {
            LOGGER.warn("[Grafter] absorb write failed, rolling back spell {} back to target {}",
                    chosen.id(), target.getGameProfile().getName());
            writeSpellIntoBook(target, chosen);
            return null;
        }
        return chosen;
    }

    /**
     * 嫁接师嫁接：从 caster 法术书随机抽 1 条非禁用法术写给 target，
     * 并把这条法术从 caster 自己的法术书移除（对称语义：自己付出一条才能给出去）。
     *
     * 执行顺序：先从 caster 移除，确认成功后再写入 target。
     * 若写入 target 失败则回滚，把 spell 放回 caster。
     *
     * @return 被嫁接出去的法术，若没有候选、移除失败或写入失败返回 null。
     */
    @Nullable
    public static EligibleSpell graft(ServerPlayer caster, ServerPlayer target) {
        List<EligibleSpell> candidates = collectTransferableSpells(caster);
        if (candidates.isEmpty()) {
            return null;
        }
        EligibleSpell chosen = candidates.get(caster.getRandom().nextInt(candidates.size()));
        if (!removeSpellFromOwnedBook(caster, chosen)) {
            LOGGER.warn("[Grafter] graft aborted: failed to remove spell {} from caster {}",
                    chosen.id(), caster.getGameProfile().getName());
            return null;
        }
        if (!writeSpellIntoBook(target, chosen)) {
            LOGGER.warn("[Grafter] graft write failed, rolling back spell {} back to caster {}",
                    chosen.id(), caster.getGameProfile().getName());
            writeSpellIntoBook(caster, chosen);
            return null;
        }
        return chosen;
    }

    public static boolean isGraftTargetEligible(ServerPlayer target) {
        // 非异能者：未搭载任何异常法术
        return !AnomalyBookService.hasLoadedSpells(target);
    }

    /**
     * 目标是否持有任意 S 级（LEGENDARY，对应项目内四字异能）法术。
     * 以该 slot 的当前等级下的实际 rarity 为准。
     */
    public static boolean hasSTierSpell(ServerPlayer target) {
        return getFirstSTierSpell(target) != null;
    }

    /**
     * 返回目标身上第一条 S 级法术，供失败提示回显名称；没有则返回 null。
     * 与 {@link #collectTransferableSpells} 的 slot 来源保持一致，
     * 不忽略 {@link #isForbidden} 的条目——嫁接师自身/生生不息若存在也算 S 级的一部分。
     */
    @Nullable
    public static EligibleSpell getFirstSTierSpell(ServerPlayer target) {
        for (SpellSlot slot : AnomalyBookService.getPlayerLoadedSpellSlots(target)) {
            AbstractSpell spell = slot.getSpell();
            if (spell == null) {
                continue;
            }
            if (spell.getRarity(slot.getLevel()) == SpellRarity.LEGENDARY) {
                return new EligibleSpell(spell.getSpellResource(), slot.getLevel());
            }
        }
        return null;
    }

    public static ItemEntity findNearbyDroppedTraitItem(ServerPlayer caster, double range) {
        Level level = caster.level();
        AABB box = caster.getBoundingBox().inflate(range);
        return level.getEntitiesOfClass(ItemEntity.class, box,
                        e -> e.isAlive() && !e.getItem().isEmpty()
                                && e.getItem().getItem() instanceof AnomalyTraitItem
                                && e.getItem().getTag() != null
                                && e.getItem().getTag().contains(AnomalyTraitItem.TAG_STAGE_ABILITIES))
                .stream()
                .min((a, b) -> Double.compare(caster.distanceToSqr(a), caster.distanceToSqr(b)))
                .orElse(null);
    }

    public static String absorbDroppedTraitItem(ServerPlayer caster, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(AnomalyTraitItem.TAG_STAGE_ABILITIES)) {
            return null;
        }
        String abilitiesCsv = tag.getString(AnomalyTraitItem.TAG_STAGE_ABILITIES);
        if (abilitiesCsv.isEmpty() || "该阶段暂无已记录异能".equals(abilitiesCsv.trim())) {
            return null;
        }

        // TAG_STAGE_ABILITIES 存的是中文名（可能以 "、" 或 "," 分隔），按别名表反查
        String[] parts = abilitiesCsv.split("[、,，]\\s*");
        List<EligibleSpell> resolved = new ArrayList<>();
        for (String part : parts) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            ResourceLocation id = AnomalyBookService.resolveSpellId(name);
            if (id == null || isForbidden(id)) {
                continue;
            }
            resolved.add(new EligibleSpell(id, 1));
        }
        if (resolved.isEmpty()) {
            return null;
        }

        EligibleSpell chosen = resolved.get(caster.getRandom().nextInt(resolved.size()));
        if (!writeSpellIntoBook(caster, chosen)) {
            return null;
        }

        // 消耗掉落物
        stack.shrink(1);
        if (stack.isEmpty()) {
            itemEntity.discard();
        }

        AbstractSpell spell = SpellRegistry.getSpell(chosen.id());
        return spell != null ? spell.getSpellName() : chosen.id().getPath();
    }

    private static boolean writeSpellIntoBook(ServerPlayer player, EligibleSpell spell) {
        AbstractSpell abstractSpell = SpellRegistry.getSpell(spell.id());
        if (abstractSpell == null) {
            return false;
        }
        ItemStack book = AnomalyBookService.ensureBookPresent(player);
        if (book.isEmpty()) {
            return false;
        }
        return AnomalyBookService.addSpell(player, book, abstractSpell, spell.level(), 1);
    }

    private static boolean removeSpellFromOwnedBook(ServerPlayer player, EligibleSpell spell) {
        AbstractSpell abstractSpell = SpellRegistry.getSpell(spell.id());
        if (abstractSpell == null) {
            LOGGER.warn("[Grafter] remove failed: spell {} not in registry for player {}",
                    spell.id(), player.getGameProfile().getName());
            return false;
        }
        ItemStack book = AnomalyBookService.getOwnedBook(player);
        if (book.isEmpty()) {
            LOGGER.warn("[Grafter] remove failed: player {} has no owned anomaly book (spell {})",
                    player.getGameProfile().getName(), spell.id());
            return false;
        }
        boolean cleared = AnomalyBookService.clearSpell(player, book, abstractSpell);
        if (!cleared) {
            LOGGER.warn("[Grafter] remove failed: clearSpell returned false for spell {} on player {}",
                    spell.id(), player.getGameProfile().getName());
        }
        return cleared;
    }
}
