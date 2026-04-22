package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.item.AnomalyTraitItem;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GrafterRuntime {
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
     * @return 被抽走的法术，若没有候选或写入失败返回 null（失败时不会扣 target）。
     */
    @Nullable
    public static EligibleSpell absorb(ServerPlayer caster, ServerPlayer target) {
        List<EligibleSpell> candidates = collectTransferableSpells(target);
        if (candidates.isEmpty()) {
            return null;
        }
        EligibleSpell chosen = candidates.get(caster.getRandom().nextInt(candidates.size()));
        if (!writeSpellIntoBook(caster, chosen)) {
            return null;
        }
        removeSpellFromOwnedBook(target, chosen);
        return chosen;
    }

    /**
     * 嫁接师嫁接：从 caster 法术书随机抽 1 条非禁用法术写给 target，
     * 并把这条法术从 caster 自己的法术书移除（对称语义：自己付出一条才能给出去）。
     *
     * @return 被嫁接出去的法术，若没有候选或写入失败返回 null（失败时不会扣 caster）。
     */
    @Nullable
    public static EligibleSpell graft(ServerPlayer caster, ServerPlayer target) {
        List<EligibleSpell> candidates = collectTransferableSpells(caster);
        if (candidates.isEmpty()) {
            return null;
        }
        EligibleSpell chosen = candidates.get(caster.getRandom().nextInt(candidates.size()));
        if (!writeSpellIntoBook(target, chosen)) {
            return null;
        }
        removeSpellFromOwnedBook(caster, chosen);
        return chosen;
    }

    public static boolean isGraftTargetEligible(ServerPlayer target) {
        // 非异能者：未搭载任何异常法术
        return !AnomalyBookService.hasLoadedSpells(target);
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

    private static void removeSpellFromOwnedBook(ServerPlayer player, EligibleSpell spell) {
        AbstractSpell abstractSpell = SpellRegistry.getSpell(spell.id());
        if (abstractSpell == null) {
            return;
        }
        ItemStack book = AnomalyBookService.getOwnedBook(player);
        if (book.isEmpty()) {
            return;
        }
        AnomalyBookService.clearSpell(player, book, abstractSpell);
    }
}
