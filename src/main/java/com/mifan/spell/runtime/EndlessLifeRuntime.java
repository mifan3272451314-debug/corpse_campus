package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

public final class EndlessLifeRuntime {
    public static final ResourceLocation ENDLESS_LIFE_ID = ResourceLocation.fromNamespaceAndPath("corpse_campus",
            "endless_life");

    private static final String SEAL_TAG = "corpse_campus.endless_life_burned";

    private static final List<ResourceLocation> INHERITED_SPELLS = List.of(
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "huihun"),
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "healing"),
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "stamina"),
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "apothecary"),
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "grafter"),
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "ferryman"));

    private EndlessLifeRuntime() {
    }

    public static boolean isSealed(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            // overworld 还没准备好的极端情况：保守地不视为封锁
            return false;
        }
        return overworld.getDataStorage()
                .computeIfAbsent(EndlessLifeState::load, EndlessLifeState::new, "corpse_campus_endless_life")
                .sealed;
    }

    public static void setSealed(MinecraftServer server, boolean sealed) {
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        EndlessLifeState state = overworld.getDataStorage()
                .computeIfAbsent(EndlessLifeState::load, EndlessLifeState::new, "corpse_campus_endless_life");
        if (state.sealed != sealed) {
            state.sealed = sealed;
            state.setDirty();
        }
    }

    /**
     * 写入圣祈一阶 + 二阶共 6 项，已存在于书中的法术按各自实现的添加策略处理
     * （AnomalyBookService.addSpell 内部对低于现有等级的写入会跳过）。
     * @return 实际新增/升级成功的数量
     */
    public static int grantInheritedSpells(ServerPlayer caster) {
        ItemStack book = AnomalyBookService.ensureBookPresent(caster);
        if (book.isEmpty()) {
            return 0;
        }
        int granted = 0;
        for (ResourceLocation id : INHERITED_SPELLS) {
            AbstractSpell spell = SpellRegistry.getSpell(id);
            if (spell == null || spell == SpellRegistry.none()) {
                continue;
            }
            if (AnomalyBookService.addSpell(caster, book, spell, 1, 1)) {
                granted++;
            }
        }
        return granted;
    }

    public static boolean removeEndlessLifeFromBook(ServerPlayer caster) {
        AbstractSpell endlessLife = SpellRegistry.getSpell(ENDLESS_LIFE_ID);
        if (endlessLife == null || endlessLife == SpellRegistry.none()) {
            return false;
        }
        ItemStack book = AnomalyBookService.ensureBookPresent(caster);
        if (book.isEmpty()) {
            return false;
        }
        return AnomalyBookService.clearSpell(caster, book, endlessLife);
    }

    /**
     * 通知所有权限 >=2 的管理员：施法者刚释放生生不息，
     * 复活动作交由管理员处理。消息附带可点击坐标供管理员传送过去。
     */
    public static void notifyOperators(ServerPlayer caster) {
        ResourceKey<Level> dimensionKey = caster.level().dimension();
        String dimensionId = dimensionKey.location().toString();
        String teleportCommand = String.format(Locale.ROOT,
                "/execute in %s run tp @s %.2f %.2f %.2f",
                dimensionId,
                caster.getX(),
                caster.getY(),
                caster.getZ());

        MutableComponent locationComponent = Component.translatable(
                "message.corpse_campus.endless_life_location",
                String.format(Locale.ROOT, "%.1f", caster.getX()),
                String.format(Locale.ROOT, "%.1f", caster.getY()),
                String.format(Locale.ROOT, "%.1f", caster.getZ()),
                dimensionId).withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, teleportCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.corpse_campus.endless_life_click_hint"))));

        MutableComponent notifyMessage = Component.translatable(
                "message.corpse_campus.endless_life_notify_ops",
                caster.getDisplayName(),
                locationComponent);

        for (ServerPlayer player : caster.server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(notifyMessage);
            }
        }
    }

    /**
     * SavedData：用主世界的存档目录持久化封锁标记。
     * 使用 SavedData 而非裸 PersistentData 是因为 Forge 1.20.1 的 ServerLevel
     * 没有公开 PersistentData 入口，SavedData 是官方推荐的世界级持久化通道，
     * 写入 world/data/corpse_campus_endless_life.dat。
     */
    private static final class EndlessLifeState extends net.minecraft.world.level.saveddata.SavedData {
        private boolean sealed;

        private EndlessLifeState() {
            this.sealed = false;
        }

        private EndlessLifeState(boolean sealed) {
            this.sealed = sealed;
        }

        private static EndlessLifeState load(CompoundTag tag) {
            return new EndlessLifeState(tag.getBoolean(SEAL_TAG));
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean(SEAL_TAG, sealed);
            return tag;
        }
    }
}
