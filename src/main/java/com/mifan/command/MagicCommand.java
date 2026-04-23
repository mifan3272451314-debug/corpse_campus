package com.mifan.command;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalyConfig;
import com.mifan.anomaly.AnomalyLimitService;
import com.mifan.anomaly.AnomalySpellRank;
import com.mifan.anomaly.NaturalAwakeningService;
import com.mifan.registry.ModItems;
import com.mifan.spell.runtime.DailyAbilityRefreshState;
import com.mifan.spell.runtime.EndlessLifeRuntime;
import com.mifan.spell.runtime.RewindBackupService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mifan.corpsecampus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class MagicCommand {
    private MagicCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("magic")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("spell", StringArgumentType.word())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(context -> addSpell(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "spell"),
                                                                IntegerArgumentType.getInteger(context, "level"),
                                                                IntegerArgumentType.getInteger(context, "count"))))))))
                .then(Commands.literal("givebook")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> giveBook(context.getSource(), EntityArgument.getPlayer(context, "player"), false))
                                .then(Commands.argument("forceRebuild", IntegerArgumentType.integer(0, 1))
                                        .executes(context -> giveBook(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "forceRebuild") == 1)))))
                .then(Commands.literal("forceequip")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> forceEquip(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("spells")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> showSpells(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("spell", StringArgumentType.word())
                                        .executes(context -> clearSpell(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "spell"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("spell", StringArgumentType.word())
                                        .executes(context -> removeSpell(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "spell"),
                                                1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> removeSpell(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "spell"),
                                                        IntegerArgumentType.getInteger(context, "count")))))))
                .then(Commands.literal("clearall")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> clearAllSpells(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("state")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> showState(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("fixbook")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> fixBook(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("recalc")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> recalc(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("unawaken")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> unawaken(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("setsequence")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("sequence", StringArgumentType.word())
                                        .executes(context -> setSequence(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "sequence"))))))
                .then(Commands.literal("setrank")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .executes(context -> setRank(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "rank"))))))
                .then(Commands.literal("mana")
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 999999.0D))
                                                .executes(context -> manaSet(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        DoubleArgumentType.getDouble(context, "value"))))))
                        .then(Commands.literal("fill")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> manaFill(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("bonus")
                                .requires(source -> source.hasPermission(3))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 99999))
                                                .executes(context -> manaBonus(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "value")))))))
                .then(Commands.literal("schoolbonus")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("school", StringArgumentType.word())
                                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0D, 500.0D))
                                                .executes(context -> setSchoolBonus(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "school"),
                                                        DoubleArgumentType.getDouble(context, "percent")))))))
                .then(Commands.literal("trait")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("sequence", StringArgumentType.word())
                                                .then(Commands.argument("rank", StringArgumentType.word())
                                                        .executes(context -> traitGive(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "sequence"),
                                                                StringArgumentType.getString(context, "rank"),
                                                                1))
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                                .executes(context -> traitGive(
                                                                        context.getSource(),
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        StringArgumentType.getString(context, "sequence"),
                                                                        StringArgumentType.getString(context, "rank"),
                                                                        IntegerArgumentType.getInteger(context, "count")))))))))
                .then(Commands.literal("limit")
                        .then(Commands.literal("info")
                                .executes(context -> limitInfo(context.getSource())))
                        .then(Commands.literal("recount")
                                .requires(source -> source.hasPermission(3))
                                .executes(context -> limitRecount(context.getSource())))
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(3))
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 9999))
                                        .executes(context -> limitSet(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "value")))))
                        .then(Commands.literal("enable")
                                .requires(source -> source.hasPermission(3))
                                .executes(context -> limitSetEnabled(context.getSource(), true)))
                        .then(Commands.literal("disable")
                                .requires(source -> source.hasPermission(3))
                                .executes(context -> limitSetEnabled(context.getSource(), false))))
                .then(Commands.literal("natural")
                        .then(Commands.literal("progress")
                                .executes(context -> naturalProgress(
                                        context.getSource(),
                                        context.getSource().getEntity() instanceof ServerPlayer self ? self : null))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> naturalProgress(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("seal")
                        .then(Commands.literal("endless_life")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> sealStatus(context.getSource()))
                                .then(Commands.argument("sealed", BoolArgumentType.bool())
                                        .executes(context -> sealEndlessLife(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "sealed"))))))
                .then(Commands.literal("rewind")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("backup")
                                .then(Commands.literal("create")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> rewindBackupCreate(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        RewindBackupService.DEFAULT_RADIUS_BLOCKS))
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 10000))
                                                        .executes(context -> rewindBackupCreate(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                IntegerArgumentType.getInteger(context, "radius"))))))
                                .then(Commands.literal("status")
                                        .executes(context -> rewindBackupStatus(context.getSource())))
                                .then(Commands.literal("cancel")
                                        .executes(context -> rewindBackupCancel(context.getSource())))))
                .then(Commands.literal("refresh")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("all")
                                .executes(context -> refreshDailyAll(context.getSource()))))
                .then(Commands.literal("config")
                        .executes(context -> showConfig(context.getSource())))
                .then(Commands.literal("list")
                        .then(Commands.literal("schools")
                                .executes(context -> listSchools(context.getSource())))
                        .then(Commands.literal("spells")
                                .executes(context -> listSpells(context.getSource(), null))
                                .then(Commands.argument("school", StringArgumentType.word())
                                        .executes(context -> listSpells(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "school"))))))
                .then(Commands.literal("lookup")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> lookupSpell(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("who")
                        .executes(context -> listAwakened(context.getSource())))
                .then(Commands.literal("top")
                        .executes(context -> topPlayers(context.getSource(), "spells"))
                        .then(Commands.argument("metric", StringArgumentType.word())
                                .executes(context -> topPlayers(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "metric")))))
                .then(Commands.literal("dump")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> dumpBook(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("rules")
                        .then(Commands.literal("get")
                                .executes(context -> rulesGetAll(context.getSource()))
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .executes(context -> rulesGet(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "key")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .then(Commands.argument("value", StringArgumentType.string())
                                                .executes(context -> rulesSet(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "key"),
                                                        StringArgumentType.getString(context, "value"))))))
                        .then(Commands.literal("reset")
                                .executes(context -> rulesReset(context.getSource()))))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource()))));
    }

    private static int addSpell(CommandSourceStack source, ServerPlayer target, String spellInput, int level, int count) {
        ResourceLocation spellId = AnomalyBookService.resolveSpellId(spellInput);
        if (spellId == null) {
            source.sendFailure(Component.literal("未知异常法术：" + spellInput));
            return 0;
        }

        if (EndlessLifeRuntime.ENDLESS_LIFE_ID.equals(spellId)
                && EndlessLifeRuntime.isSealed(source.getServer())) {
            source.sendFailure(Component.literal(
                    "【生生不息】当前处于全局封锁状态，没有任何玩家能再获得它。"
                            + "如需解封请使用：/magic seal endless_life false （需要权限 4）"));
            return 0;
        }

        var spellSpec = AnomalyBookService.getSpellSpec(spellId);
        AbstractSpell spell = AnomalyBookService.getRegisteredSpell(spellId);
        if (spellSpec == null || spell == null) {
            source.sendFailure(Component.literal("该法术尚未注册或不在当前异常法术白名单中：" + spellId));
            return 0;
        }

        ItemStack book = AnomalyBookService.ensureBookPresent(target);
        int clampedLevel = Mth.clamp(level, 1, spell.getMaxLevel());
        boolean changed = AnomalyBookService.addSpell(target, book, spell, clampedLevel, count);
        if (!changed) {
            source.sendFailure(Component.literal("未能写入法术：目标书中已存在同级或更高级版本，或书槽已满。"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("已向 " + target.getGameProfile().getName() + " 的异常法术书写入【"
                + spellSpec.zhName() + "】Lv." + clampedLevel + " × " + count + "（重复写入仅用于升级或重试，不会占用多个同名槽位）"),
                false);
        return 1;
    }

    private static int giveBook(CommandSourceStack source, ServerPlayer target, boolean forceRebuild) {
        ItemStack book = forceRebuild ? AnomalyBookService.rebuildBook(target) : AnomalyBookService.ensureBookPresent(target);
        source.sendSuccess(() -> Component.literal((forceRebuild ? "已重建并发放 " : "已确认并发放 ")
                + target.getGameProfile().getName() + " 的异常法术书，当前绑定书 ID：" + AnomalyBookService.getBookId(book)), false);
        return 1;
    }

    private static int forceEquip(CommandSourceStack source, ServerPlayer target) {
        ItemStack book = AnomalyBookService.ensureBookPresent(target);
        source.sendSuccess(() -> Component.literal("已强制将 " + target.getGameProfile().getName()
                + " 的异常法术书放回书槽，当前绑定书 ID：" + AnomalyBookService.getBookId(book)), false);
        return 1;
    }

    private static int showSpells(CommandSourceStack source, ServerPlayer target) {
        for (Component line : AnomalyBookService.buildLoadedSpellLines(target)) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int clearSpell(CommandSourceStack source, ServerPlayer target, String spellInput) {
        ResourceLocation spellId = AnomalyBookService.resolveSpellId(spellInput);
        if (spellId == null) {
            source.sendFailure(Component.literal("未知异常法术：" + spellInput));
            return 0;
        }

        var spellSpec = AnomalyBookService.getSpellSpec(spellId);
        AbstractSpell spell = AnomalyBookService.getRegisteredSpell(spellId);
        if (spellSpec == null || spell == null) {
            source.sendFailure(Component.literal("该法术尚未注册或不在当前异常法术白名单中：" + spellId));
            return 0;
        }

        ItemStack book = AnomalyBookService.ensureBookPresent(target);
        if (!AnomalyBookService.clearSpell(target, book, spell)) {
            source.sendFailure(Component.literal("目标异常法术书中不存在法术【" + spellSpec.zhName() + "】"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("已从 " + target.getGameProfile().getName() + " 的异常法术书中移除【"
                + spellSpec.zhName() + "】"), false);
        return 1;
    }

    private static int clearAllSpells(CommandSourceStack source, ServerPlayer target) {
        ItemStack book = AnomalyBookService.ensureBookPresent(target);
        int removedCount = AnomalyBookService.clearAllSpells(target, book);
        if (removedCount <= 0) {
            source.sendFailure(Component.literal("目标异常法术书当前没有可清除的法术。"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("已清空 " + target.getGameProfile().getName()
                + " 的异常法术书，共移除 " + removedCount + " 个法术。"), false);
        return 1;
    }

    private static int removeSpell(CommandSourceStack source, ServerPlayer target, String spellInput, int count) {
        ResourceLocation spellId = AnomalyBookService.resolveSpellId(spellInput);
        if (spellId == null) {
            source.sendFailure(Component.literal("未知异常法术：" + spellInput));
            return 0;
        }

        var spellSpec = AnomalyBookService.getSpellSpec(spellId);
        AbstractSpell spell = AnomalyBookService.getRegisteredSpell(spellId);
        if (spellSpec == null || spell == null) {
            source.sendFailure(Component.literal("该法术尚未注册或不在当前异常法术白名单中：" + spellId));
            return 0;
        }

        ItemStack book = AnomalyBookService.ensureBookPresent(target);
        int removedLevels = AnomalyBookService.removeSpellLevels(target, book, spell, count);
        if (removedLevels <= 0) {
            source.sendFailure(Component.literal("目标异常法术书中不存在法术【" + spellSpec.zhName() + "】"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("已从 " + target.getGameProfile().getName() + " 的异常法术书中扣除【"
                + spellSpec.zhName() + "】等级 " + removedLevels + " 次"), false);
        return 1;
    }

    private static int showState(CommandSourceStack source, ServerPlayer target) {
        for (Component line : AnomalyBookService.buildStateLines(target)) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int fixBook(CommandSourceStack source, ServerPlayer target) {
        ItemStack book = AnomalyBookService.ensureBookPresent(target);
        source.sendSuccess(() -> Component.literal("已修复并强制佩戴 " + target.getGameProfile().getName()
                + " 的异常法术书，当前绑定书 ID：" + AnomalyBookService.getBookId(book)), false);
        return 1;
    }

    private static int recalc(CommandSourceStack source, ServerPlayer target) {
        AnomalyBookService.forceRecalc(target);
        source.sendSuccess(() -> Component.literal("已重算 " + target.getGameProfile().getName()
                + " 的异常法术书法力加成与流派强化缓存，并同步到 Curios。"), false);
        return 1;
    }

    private static int unawaken(CommandSourceStack source, ServerPlayer target) {
        int removed = AnomalyBookService.unawakenPlayer(target);
        source.sendSuccess(() -> Component.literal("已取消 " + target.getGameProfile().getName()
                + " 的觉醒状态：清空书内 " + removed + " 个法术，清除主序列与最高阶字段，并从全服觉醒统计移除。"), true);
        return 1;
    }

    private static int setSequence(CommandSourceStack source, ServerPlayer target, String sequenceInput) {
        String norm = sequenceInput.trim().toLowerCase(java.util.Locale.ROOT);
        if ("none".equals(norm) || "clear".equals(norm) || "无".equals(sequenceInput.trim())) {
            AnomalyBookService.setMainSequence(target, null);
            source.sendSuccess(() -> Component.literal("已清除 " + target.getGameProfile().getName()
                    + " 的主序列字段（书内法术与最高阶不动）。"), false);
            return 1;
        }

        ResourceLocation schoolId = AnomalyBookService.resolveSchoolId(sequenceInput);
        if (schoolId == null) {
            source.sendFailure(Component.literal("未知流派：" + sequenceInput
                    + "（支持 xujing/rizhao/dongyue/yuzhe/shengqi 或 虚境/日兆/东岳/愚者/圣祈，传 none/无 可清除）"));
            return 0;
        }
        AnomalyBookService.setMainSequence(target, schoolId);
        source.sendSuccess(() -> Component.literal("已将 " + target.getGameProfile().getName()
                + " 的主序列字段设为 " + schoolId.getPath() + "（不动书内法术与最高阶）。"), true);
        return 1;
    }

    private static int setRank(CommandSourceStack source, ServerPlayer target, String rankInput) {
        AnomalyBookService.RankResolution result = AnomalyBookService.resolveRank(rankInput);
        if (!result.valid()) {
            source.sendFailure(Component.literal("未知阶级：" + rankInput + "（支持 B/A/S/NONE 或 一级/二级/三级/无）"));
            return 0;
        }
        AnomalySpellRank rank = result.rank();
        AnomalyBookService.setHighestRank(target, rank);
        String label = rank == null ? "NONE（未觉醒）" : rank.name();
        source.sendSuccess(() -> Component.literal("已将 " + target.getGameProfile().getName()
                + " 的最高阶字段设为 " + label + "（不动书内法术与主序列）。"), true);
        return 1;
    }

    private static int manaSet(CommandSourceStack source, ServerPlayer target, double value) {
        MagicData magic = MagicData.getPlayerMagicData(target);
        if (magic == null) {
            source.sendFailure(Component.literal("目标没有 ISS MagicData。"));
            return 0;
        }
        float maxMana = (float) target.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        float clamped = (float) Math.min(maxMana, Math.max(0.0D, value));
        magic.setMana(clamped);
        source.sendSuccess(() -> Component.literal("已将 " + target.getGameProfile().getName()
                + " 的当前法力设为 " + clamped + "（上限 " + maxMana + "）"), false);
        return 1;
    }

    private static int manaFill(CommandSourceStack source, ServerPlayer target) {
        MagicData magic = MagicData.getPlayerMagicData(target);
        if (magic == null) {
            source.sendFailure(Component.literal("目标没有 ISS MagicData。"));
            return 0;
        }
        float maxMana = (float) target.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        magic.setMana(maxMana);
        source.sendSuccess(() -> Component.literal("已将 " + target.getGameProfile().getName()
                + " 的当前法力填满为 " + maxMana), false);
        return 1;
    }

    private static int manaBonus(CommandSourceStack source, ServerPlayer target, int value) {
        AnomalyBookService.overrideManaBonus(target, value);
        source.sendSuccess(() -> Component.literal("已覆盖 " + target.getGameProfile().getName()
                + " 的异常书额外法力 NBT 为 " + value
                + "（注意：下一次书内法术变动会触发自动重算，可能覆盖该值）"), true);
        return 1;
    }

    private static int setSchoolBonus(CommandSourceStack source, ServerPlayer target, String schoolInput, double percent) {
        ResourceLocation schoolId = AnomalyBookService.resolveSchoolId(schoolInput);
        if (schoolId == null) {
            source.sendFailure(Component.literal("未知流派：" + schoolInput
                    + "（支持 xujing/rizhao/dongyue/yuzhe/shengqi 或 虚境/日兆/东岳/愚者/圣祈）"));
            return 0;
        }
        AnomalyBookService.overrideSchoolBonus(target, schoolId, percent);
        source.sendSuccess(() -> Component.literal("已覆盖 " + target.getGameProfile().getName()
                + " 的异常书【" + schoolId.getPath() + "】流派强化百分比为 +" + percent + "%"
                + "（注意：下一次书内法术变动会触发自动重算，可能覆盖该值）"), true);
        return 1;
    }

    private static int traitGive(CommandSourceStack source, ServerPlayer target, String sequenceInput, String rankInput, int count) {
        ResourceLocation schoolId = AnomalyBookService.resolveSchoolId(sequenceInput);
        if (schoolId == null) {
            source.sendFailure(Component.literal("未知流派：" + sequenceInput));
            return 0;
        }
        AnomalyBookService.RankResolution result = AnomalyBookService.resolveRank(rankInput);
        if (!result.valid() || result.rank() == null) {
            source.sendFailure(Component.literal("未知阶级：" + rankInput + "（仅支持 B/A/S 或 一级/二级/三级）"));
            return 0;
        }
        AnomalySpellRank rank = result.rank();
        Item item;
        try {
            item = ModItems.getTraitItem(schoolId, rank);
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("无法定位异常特性物品: " + ex.getMessage()));
            return 0;
        }
        ItemStack stack = new ItemStack(item, count);
        if (!target.getInventory().add(stack)) {
            target.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal("已发放 " + count + " 个【" + schoolId.getPath() + "·"
                + rank.name() + "级异常特性】给 " + target.getGameProfile().getName()), false);
        return 1;
    }

    private static int limitInfo(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        AnomalyLimitService service = AnomalyLimitService.get(server);
        for (Component line : service.buildInfoLines()) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int naturalProgress(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendFailure(Component.literal("必须指定玩家；/magic natural progress <player> 或由玩家自身执行"));
            return 0;
        }
        java.util.Map<String, Integer> snapshot = NaturalAwakeningService.snapshotProgress(target);
        source.sendSuccess(() -> Component.literal(
                "§a[自然觉醒进度] §e" + target.getGameProfile().getName()), false);
        for (String key : NaturalAwakeningService.ALL_KEYS) {
            int value = snapshot.getOrDefault(key, 0);
            int threshold = NaturalAwakeningService.getThreshold(key);
            boolean reached = value >= threshold;
            String valueColor = reached ? "§a" : "§e";
            String line = String.format("§f  %s §7: %s%d§7/§e%d%s",
                    naturalProgressLabel(key), valueColor, value, threshold,
                    reached ? " §a✓" : "");
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal("§8（副本探索 / 配方合成两条原子条件本轮未实现）"), false);
        return 1;
    }

    private static String naturalProgressLabel(String key) {
        return switch (key) {
            case NaturalAwakeningService.KEY_SIGN_PLACE -> "告示牌放置(印记)";
            case NaturalAwakeningService.KEY_BLOCK_BREAK -> "破坏方块(耐力)";
            case NaturalAwakeningService.KEY_FARMLAND_TILL -> "开垦耕地(沃土)";
            case NaturalAwakeningService.KEY_BONEMEAL_CROP -> "催熟作物(宁禾)";
            case NaturalAwakeningService.KEY_MELEE_ATTACK -> "平砍次数(躁狂)";
            case NaturalAwakeningService.KEY_HIT_BY_MONSTER -> "被怪物攻击(本能)";
            case NaturalAwakeningService.KEY_MONSTER_KILL -> "击杀怪物(冥化)";
            case NaturalAwakeningService.KEY_CROUCH_TICK -> "连续蹲伏tick(危机)";
            case NaturalAwakeningService.KEY_CRAWL_TICK -> "连续爬行tick(危机)";
            case NaturalAwakeningService.KEY_WALL_TOUCH_TICK -> "累计接触墙tick(磁吸)";
            case NaturalAwakeningService.KEY_FALL_BURST_MAX -> "历史最大单次坠落(万象)";
            default -> key;
        };
    }

    private static int limitRecount(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        AnomalyLimitService service = AnomalyLimitService.get(server);
        int count = service.recountFromServer(server);
        source.sendSuccess(() -> Component.literal(
                "已重新统计全服异常者计数（在线玩家重算，离线保留原值），当前: " + count + " 人"), false);
        return 1;
    }

    private static int limitSet(CommandSourceStack source, int newValue) {
        MinecraftServer server = source.getServer();
        AnomalyLimitService service = AnomalyLimitService.get(server);
        int old = service.setCapValue(newValue);
        source.sendSuccess(() -> Component.literal(
                "全服异常者上限已从 " + old + " 调整为 " + newValue
                + "（当前已觉醒: " + service.getAnomalyCount() + " 人）"), false);
        return 1;
    }

    private static int limitSetEnabled(CommandSourceStack source, boolean enabled) {
        MinecraftServer server = source.getServer();
        AnomalyLimitService service = AnomalyLimitService.get(server);
        boolean old = service.setCapEnabled(enabled);
        if (old == enabled) {
            source.sendFailure(Component.literal("上限当前已经是「" + (enabled ? "开启" : "关闭") + "」状态，无需变更。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "全服异常者上限已" + (enabled ? "§a开启§r（上限: " + service.getCapValue() + "）" : "§7关闭§r")), false);
        return 1;
    }

    private static int sealStatus(CommandSourceStack source) {
        boolean sealed = EndlessLifeRuntime.isSealed(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "【生生不息】当前封锁状态：" + (sealed ? "§c已封锁§r（无人可获得）" : "§a未封锁§r（可正常获得）")), false);
        return 1;
    }

    private static int sealEndlessLife(CommandSourceStack source, boolean sealed) {
        boolean before = EndlessLifeRuntime.isSealed(source.getServer());
        EndlessLifeRuntime.setSealed(source.getServer(), sealed);
        if (before == sealed) {
            source.sendSuccess(() -> Component.literal(
                    "【生生不息】封锁状态未变化，仍为：" + (sealed ? "已封锁" : "未封锁")), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "【生生不息】封锁状态已切换：" + (before ? "已封锁" : "未封锁")
                            + " → " + (sealed ? "§c已封锁§r" : "§a未封锁§r")), true);
        }
        return 1;
    }

    private static int showConfig(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        AnomalyLimitService service = AnomalyLimitService.get(server);
        long dailyGen = DailyAbilityRefreshState.currentGeneration(server);
        String[] lines = {
                "§6§l══════════ corpse_campus 当前运行时配置 ══════════",
                "§a§l▌ §e§l上限系统",
                "§f  limit.globalCapEnabled = §e" + service.isCapEnabled()
                        + "§7（SavedData 实时值；/magic limit enable|disable 可切）",
                "§f  limit.globalCapValue = §e" + service.getCapValue()
                        + "§7（SavedData 实时值；/magic limit set <value> 可改）",
                "§f  limit.countAwakenedPlayers = §e" + AnomalyConfig.countAwakenedPlayers
                        + "§7（config 文件值，需重载 config）",
                "§f  limit.autoRecountOnServerStart = §e" + AnomalyConfig.autoRecountOnServerStart
                        + "§7（config 文件值，需重载 config）",
                "§a§l▌ §e§l数值规则（代码常量，不可运行时调）",
                "§f  法力加成: B=+75 / A=+150 / S=+250 §7（AnomalySpellRank）",
                "§f  流派强化: B=+25% / A=+50% / S=+50% §7（AnomalySpellRank；MULTIPLY_BASE）",
                "§f  异常法术书槽位上限: " + AnomalyBookService.MAX_SPELL_SLOTS,
                "§a§l▌ §e§l其它",
                "§f  已觉醒玩家数: §e" + service.getAnomalyCount(),
                "§f  生生不息封锁: §e" + EndlessLifeRuntime.isSealed(server),
                "§f  每日限定法术 generation: §e" + dailyGen
                        + "§7（/magic refresh all 让所有用过的玩家重新可用）"
        };
        for (String s : lines) {
            source.sendSuccess(() -> Component.literal(s), false);
        }
        return 1;
    }

    // ────────────────────────────────────────────────────────────────────
    //  查询类指令（权限 2，全部只读）
    // ────────────────────────────────────────────────────────────────────

    private static String localizeSchool(ResourceLocation schoolId) {
        return Component.translatable("school." + schoolId.getNamespace() + "." + schoolId.getPath()).getString();
    }

    private static String schoolColor(ResourceLocation schoolId) {
        return switch (schoolId.getPath()) {
            case "xujing" -> "§b";
            case "rizhao" -> "§e";
            case "dongyue" -> "§2";
            case "yuzhe" -> "§5";
            case "shengqi" -> "§3";
            default -> "§f";
        };
    }

    private static String rankColor(AnomalySpellRank rank) {
        if (rank == null) return "§7";
        return switch (rank) {
            case B -> "§a";
            case A -> "§6";
            case S -> "§c";
        };
    }

    private static int listSchools(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§l══════════ 异常序列（共 5 个） ══════════"), false);
        for (ResourceLocation schoolId : AnomalyBookService.getTrackedSchoolIds()) {
            String color = schoolColor(schoolId);
            String zh = localizeSchool(schoolId);
            long total = AnomalyBookService.getAllSpellSpecs().stream()
                    .filter(spec -> spec.schoolId().equals(schoolId))
                    .count();
            source.sendSuccess(() -> Component.literal(
                    color + "§l▌ §r" + color + zh + "§7 (" + schoolId.getPath() + ")"
                            + "§8  共 §f" + total + "§8 个异常法术"), false);
        }
        source.sendSuccess(() -> Component.literal(
                "§8提示：/magic list spells <序列> 可查看某序列下的全部法术。"), false);
        return 1;
    }

    private static int listSpells(CommandSourceStack source, String schoolInput) {
        ResourceLocation filter = null;
        if (schoolInput != null && !schoolInput.isBlank()) {
            filter = AnomalyBookService.resolveSchoolId(schoolInput);
            if (filter == null) {
                source.sendFailure(Component.literal("未知流派：" + schoolInput
                        + "（支持 xujing/rizhao/dongyue/yuzhe/shengqi 或 虚境/日兆/东岳/愚者/圣祈）"));
                return 0;
            }
        }

        Map<ResourceLocation, Map<AnomalySpellRank, List<AnomalyBookService.SpellSpec>>> grouped = new LinkedHashMap<>();
        for (ResourceLocation schoolId : AnomalyBookService.getTrackedSchoolIds()) {
            grouped.put(schoolId, new LinkedHashMap<>());
        }
        for (AnomalyBookService.SpellSpec spec : AnomalyBookService.getAllSpellSpecs()) {
            if (filter != null && !spec.schoolId().equals(filter)) {
                continue;
            }
            grouped.computeIfAbsent(spec.schoolId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(spec.rank(), ignored -> new ArrayList<>())
                    .add(spec);
        }

        String title = filter == null
                ? "§6§l══════════ 全部异常法术 ══════════"
                : "§6§l══════════ " + localizeSchool(filter) + " 序列法术 ══════════";
        source.sendSuccess(() -> Component.literal(title), false);

        int totalPrinted = 0;
        for (ResourceLocation schoolId : AnomalyBookService.getTrackedSchoolIds()) {
            if (filter != null && !schoolId.equals(filter)) continue;
            Map<AnomalySpellRank, List<AnomalyBookService.SpellSpec>> byRank = grouped.get(schoolId);
            if (byRank == null || byRank.isEmpty()) continue;
            String color = schoolColor(schoolId);
            source.sendSuccess(() -> Component.literal(color + "§l▌ " + localizeSchool(schoolId)
                    + "§7 (" + schoolId.getPath() + ")"), false);
            for (AnomalySpellRank rank : AnomalySpellRank.values()) {
                List<AnomalyBookService.SpellSpec> list = byRank.get(rank);
                if (list == null || list.isEmpty()) continue;
                StringBuilder sb = new StringBuilder();
                sb.append("  ").append(rankColor(rank)).append(rank.name()).append("§7: ");
                for (int i = 0; i < list.size(); i++) {
                    AnomalyBookService.SpellSpec spec = list.get(i);
                    sb.append("§f").append(spec.spellId().getPath())
                            .append("§7(").append(spec.zhName()).append("§7)");
                    if (i < list.size() - 1) sb.append("§8, ");
                }
                final String line = sb.toString();
                source.sendSuccess(() -> Component.literal(line), false);
                totalPrinted += list.size();
            }
        }
        final int printed = totalPrinted;
        source.sendSuccess(() -> Component.literal("§8共列出 §f" + printed + "§8 个法术。"), false);
        return 1;
    }

    private static int lookupSpell(CommandSourceStack source, String rawInput) {
        ResourceLocation spellId = AnomalyBookService.resolveSpellId(rawInput);
        if (spellId == null) {
            source.sendFailure(Component.literal("未找到匹配的异常法术：" + rawInput
                    + "（支持注册名 / 中文名 / 完整 ID，如 sonic_sense / 音波 / corpse_campus:sonic_sense）"));
            return 0;
        }
        AnomalyBookService.SpellSpec spec = AnomalyBookService.getSpellSpec(spellId);
        AbstractSpell spell = AnomalyBookService.getRegisteredSpell(spellId);
        if (spec == null) {
            source.sendFailure(Component.literal("法术规格查询失败：" + spellId));
            return 0;
        }

        String schoolColor = schoolColor(spec.schoolId());
        String schoolZh = localizeSchool(spec.schoolId());
        String rankStr = rankColor(spec.rank()) + spec.rank().name() + "§r（+"
                + spec.rank().getManaBonus() + " 法力 / +" + spec.rank().getSchoolBonusPercent() + "% 强化）";
        String registeredStr = spell == null
                ? "§c未在 ISS 注册表中找到（可能是待实现法术）"
                : "§a已注册（Max Lv." + spell.getMaxLevel() + "）";

        source.sendSuccess(() -> Component.literal("§6§l══════════ 异常法术详情 ══════════"), false);
        source.sendSuccess(() -> Component.literal("§f中文名：§r" + spec.zhName()), false);
        source.sendSuccess(() -> Component.literal("§f注册名：§r" + spec.spellId().getPath()), false);
        source.sendSuccess(() -> Component.literal("§f完整 ID：§r" + spec.spellId()), false);
        source.sendSuccess(() -> Component.literal("§f序列：§r" + schoolColor + schoolZh + "§7 ("
                + spec.schoolId().getPath() + ")"), false);
        source.sendSuccess(() -> Component.literal("§f阶级：§r" + rankStr), false);
        source.sendSuccess(() -> Component.literal("§f注册状态：§r" + registeredStr), false);

        if (EndlessLifeRuntime.ENDLESS_LIFE_ID.equals(spellId)
                && EndlessLifeRuntime.isSealed(source.getServer())) {
            source.sendSuccess(() -> Component.literal(
                    "§c⚠ 当前处于全局封锁状态，无人可再获得。可用 /magic seal endless_life false 解封。"), false);
        }
        return 1;
    }

    private static int listAwakened(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        AnomalyLimitService service = AnomalyLimitService.get(server);
        Set<UUID> uuids = service.getAwakenedPlayerUUIDs();
        if (uuids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7当前全服没有已觉醒的异常者。"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("§6§l══════════ 全服已觉醒玩家 ("
                + uuids.size() + "/" + service.getCapValue() + ") ══════════"), false);

        int online = 0;
        int offline = 0;
        for (UUID uuid : uuids) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                online++;
                ItemStack book = AnomalyBookService.getPlayerBook(player);
                ResourceLocation seq = book.isEmpty() ? null : AnomalyBookService.getMainSequenceId(book);
                AnomalySpellRank rank = book.isEmpty() ? null : AnomalyBookService.getHighestRank(book);
                int spellCount = 0;
                if (!book.isEmpty()) {
                    spellCount = ISpellContainer.getOrCreate(book).getActiveSpells().size();
                }
                int manaBonus = book.isEmpty() ? 0 : AnomalyBookService.getStoredManaBonus(book);
                String seqStr = seq == null
                        ? "§7[无序列]§r"
                        : schoolColor(seq) + localizeSchool(seq) + "§r";
                String rankStr = rank == null ? "§7[未定阶]§r" : rankColor(rank) + rank.name() + "§r";
                final String name = player.getGameProfile().getName();
                final int cnt = spellCount;
                final int mana = manaBonus;
                source.sendSuccess(() -> Component.literal(
                        "§a● §f" + name + "§r  " + seqStr + "  " + rankStr
                                + "§7  法术×§f" + cnt + "§7  法力+§f" + mana), false);
            } else {
                offline++;
                source.sendSuccess(() -> Component.literal(
                        "§8○ §7" + uuid + "§8  [离线]"), false);
            }
        }
        final int on = online;
        final int off = offline;
        source.sendSuccess(() -> Component.literal("§8在线 §f" + on + "§8 人，离线 §f" + off + "§8 人。"), false);
        return 1;
    }

    private static int topPlayers(CommandSourceStack source, String metricInput) {
        String metric = metricInput == null ? "spells" : metricInput.trim().toLowerCase(Locale.ROOT);
        if (!metric.equals("spells") && !metric.equals("mana") && !metric.equals("levels")) {
            source.sendFailure(Component.literal("未知排行指标：" + metricInput
                    + "（支持 spells=法术数 / levels=法术总等级 / mana=额外法力）"));
            return 0;
        }

        MinecraftServer server = source.getServer();
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack book = AnomalyBookService.getPlayerBook(player);
            if (!book.isEmpty()) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7当前没有在线玩家持有异常书。"), false);
            return 1;
        }

        record Row(String name, int spells, int totalLevels, int manaBonus,
                   ResourceLocation seq, AnomalySpellRank rank) {}
        List<Row> rows = new ArrayList<>();
        for (ServerPlayer p : candidates) {
            ItemStack book = AnomalyBookService.getPlayerBook(p);
            int spellCnt = 0;
            int levelSum = 0;
            for (SpellSlot slot : ISpellContainer.getOrCreate(book).getActiveSpells()) {
                spellCnt++;
                levelSum += slot.getLevel();
            }
            rows.add(new Row(
                    p.getGameProfile().getName(),
                    spellCnt,
                    levelSum,
                    AnomalyBookService.getStoredManaBonus(book),
                    AnomalyBookService.getMainSequenceId(book),
                    AnomalyBookService.getHighestRank(book)));
        }

        Comparator<Row> cmp = switch (metric) {
            case "mana" -> Comparator.<Row>comparingInt(r -> r.manaBonus).reversed();
            case "levels" -> Comparator.<Row>comparingInt(r -> r.totalLevels).reversed();
            default -> Comparator.<Row>comparingInt(r -> r.spells).reversed();
        };
        rows.sort(cmp);

        final String metricLabel = switch (metric) {
            case "mana" -> "额外法力";
            case "levels" -> "法术总等级";
            default -> "法术数";
        };
        source.sendSuccess(() -> Component.literal(
                "§6§l══════════ 异常者排行（按 " + metricLabel + "） ══════════"), false);
        int limit = Math.min(rows.size(), 10);
        for (int i = 0; i < limit; i++) {
            Row r = rows.get(i);
            String medal = switch (i) {
                case 0 -> "§6①";
                case 1 -> "§f②";
                case 2 -> "§c③";
                default -> "§8" + (i + 1) + ".";
            };
            String seqStr = r.seq == null ? "§7[无]"
                    : schoolColor(r.seq) + localizeSchool(r.seq);
            String rankStr = r.rank == null ? "§7[无]" : rankColor(r.rank) + r.rank.name();
            int value = switch (metric) {
                case "mana" -> r.manaBonus;
                case "levels" -> r.totalLevels;
                default -> r.spells;
            };
            final String line = medal + " §f" + r.name + "§r  " + seqStr + "§r " + rankStr
                    + "§r§7  " + metricLabel + "=§f" + value
                    + "§7  (法术×" + r.spells + " / 总等级 " + r.totalLevels + " / 法力+" + r.manaBonus + ")";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        if (rows.size() > 10) {
            final int remaining = rows.size() - 10;
            source.sendSuccess(() -> Component.literal("§8...另有 " + remaining + " 人未进入前十。"), false);
        }
        return 1;
    }

    private static int dumpBook(CommandSourceStack source, ServerPlayer target) {
        ItemStack book = AnomalyBookService.getPlayerBook(target);
        if (book.isEmpty()) {
            source.sendFailure(Component.literal(target.getGameProfile().getName()
                    + " 当前没有绑定的异常书（未装备 / 未觉醒 / 书被删除）。"));
            return 0;
        }
        CompoundTag tag = book.getTag();
        source.sendSuccess(() -> Component.literal("§6§l══════════ "
                + target.getGameProfile().getName() + " 异常书 NBT 转储 ══════════"), false);
        source.sendSuccess(() -> Component.literal("§f书 ID：§r"
                + AnomalyBookService.getBookId(book)), false);
        UUID owner = AnomalyBookService.getOwnerUuid(book);
        source.sendSuccess(() -> Component.literal("§f所属 UUID：§r"
                + (owner == null ? "未绑定" : owner)), false);

        if (tag == null) {
            source.sendSuccess(() -> Component.literal("§7该书 ItemStack 没有 NBT tag。"), false);
            return 1;
        }
        String raw = tag.toString();
        int maxLen = 900;
        for (int i = 0; i < raw.length(); i += maxLen) {
            final String chunk = raw.substring(i, Math.min(raw.length(), i + maxLen));
            source.sendSuccess(() -> Component.literal("§7" + chunk), false);
        }
        source.sendSuccess(() -> Component.literal("§8（上方为原始 NBT 字符串，可复制用于 /data modify 调试）"), false);
        return 1;
    }

    private static int rewindBackupCreate(CommandSourceStack source, ServerPlayer target, int radiusBlocks) {
        net.minecraft.server.level.ServerLevel sourceLevel = target.serverLevel();
        net.minecraft.core.BlockPos center = target.blockPosition();
        RewindBackupService.StartResult result = RewindBackupService.startBackup(
                source.getServer(), sourceLevel, center, radiusBlocks);
        switch (result) {
            case STARTED -> {
                int radiusChunks = Math.max(1, (radiusBlocks + 15) >> 4);
                int totalChunks = RewindBackupService.getState(source.getServer()).totalChunks;
                // 三阶段串行：总耗时 ≈ blocks/2 + BE/4 + entities/4 tick；按最慢的 blocks 相估算
                long estMin = (long) totalChunks / Math.max(1, RewindBackupService.CHUNKS_PER_TICK_BLOCKS) / 20L / 60L;
                source.sendSuccess(() -> Component.literal("§a已启动回溯之虫镜像备份 §7→ 玩家 "
                        + target.getGameProfile().getName() + "，中心维度 "
                        + sourceLevel.dimension().location() + "，中心方块 ("
                        + center.getX() + "," + center.getY() + "," + center.getZ()
                        + ")，半径 " + radiusBlocks + " 格 (" + radiusChunks + " chunks)"
                        + "，每阶段待扫描 " + totalChunks + " chunks。"
                        + "三阶段串行 (方块态 → 方块实体 → 生物实体)，按最慢阶段估算约 "
                        + estMin + " 分钟起；用 /magic rewind backup status 可查阶段进度。"), true);
                return 1;
            }
            case BUSY -> {
                source.sendFailure(Component.literal("已有备份任务在进行中。可用 /magic rewind backup status 查看进度，或 /magic rewind backup cancel 取消后重新启动。"));
                return 0;
            }
            case MIRROR_MISSING -> {
                source.sendFailure(Component.literal("镜像维度 corpse_campus:rewind_mirror 未加载。"
                        + "请确认 data/corpse_campus/dimension/rewind_mirror.json 随模组一起部署，"
                        + "并且服务器此次启动后至少执行过一次 /execute in corpse_campus:rewind_mirror run tp @s 0 80 0 触发维度创建。"));
                return 0;
            }
            case SOURCE_IS_MIRROR -> {
                source.sendFailure(Component.literal("玩家当前就在镜像维度，无法把镜像再备份进镜像。"
                        + "让玩家先离开 rewind_mirror 再试。"));
                return 0;
            }
        }
        return 0;
    }

    private static int rewindBackupStatus(CommandSourceStack source) {
        for (Component line : RewindBackupService.buildStatusLines(source.getServer())) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int rewindBackupCancel(CommandSourceStack source) {
        RewindBackupService.cancel(source.getServer());
        source.sendSuccess(() -> Component.literal("§e已取消当前回溯之虫镜像备份任务，镜像维度已有方块不会被清除；"
                + "下次使用 /magic rewind backup create 会重新扫描并覆盖。"), true);
        return 1;
    }

    private static int refreshDailyAll(CommandSourceStack source) {
        long newGen = DailyAbilityRefreshState.bumpGeneration(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "§a已刷新全服「每日限定」法术使用次数§r§7（当前 generation = §f" + newGen + "§7）。"
                        + "§r所有曾用过日轮金乌的玩家（含离线）下一次施法都会被重新放行。"), true);
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        String[] lines = {
            "§6§l══════════ /magic 全部指令文档 ══════════",
            "§a§l▌ §e§l一、书管理 §8（权限 2）",
            "§f  /magic givebook §7<玩家> §8[0|1]",
            "§7    发放或重建异常法术书。第二参数传 1 时强制重建（清除重复书再重建）。",
            "§f  /magic forceequip §7<玩家>",
            "§7    强制将异常书放回 Curios 书槽，并推送到客户端。",
            "§f  /magic fixbook §7<玩家>",
            "§7    自动定位、绑定并强制佩戴书，是 givebook+forceequip 的合并快捷版。",
            "§a§l▌ §e§l二、法术管理 §8（权限 2）",
            "§f  /magic add §7<玩家> <法术> <等级 1-10> <次数 1-64>",
            "§7    向目标书写入法术。已有低等级则升级；已是最高级则跳过；书满则报错。",
            "§7    <法术> 支持：注册名 / 中文名 / 完整 ID（见下方法术对照表）",
            "§f  /magic spells §7<玩家>",
            "§7    列出目标书内全部已装载法术（注册 ID / 等级 / 阶级）。",
            "§f  /magic clear §7<玩家> <法术>",
            "§7    完整移除目标书中指定法术，不留任何等级。",
            "§f  /magic clearall §7<玩家>",
            "§7    清空目标书内全部法术。",
            "§f  /magic remove §7<玩家> <法术> §8[次数=1]",
            "§7    扣除指定法术的等级 N 次；降至 0 则自动移除该法术。",
            "§a§l▌ §e§l三、觉醒状态 §8（权限 3）",
            "§f  /magic unawaken §7<玩家> §c[权限 3]",
            "§7    取消玩家觉醒：清空书内全部法术 + 清除主序列/最高阶字段 + 从 cap 集合移除。",
            "§f  /magic setsequence §7<玩家> <序列> §c[权限 3]",
            "§7    仅改 AnomalyMainSequence 字段。<序列> ∈ xujing/rizhao/dongyue/yuzhe/shengqi",
            "§7    或 虚境/日兆/东岳/愚者/圣祈；传 none 或 无 清除。书内法术、最高阶不动。",
            "§f  /magic setrank §7<玩家> <阶> §c[权限 3]",
            "§7    仅改 AnomalyHighestRank 字段。<阶> ∈ B/A/S/NONE 或 一级/二级/三级/无。",
            "§7    书内法术、主序列不动。",
            "§a§l▌ §e§l四、状态与重算 §8（权限 2）",
            "§f  /magic state §7<玩家>",
            "§7    显示书 ID、主人、额外法力值及五大流派（虚/日/东/愚/圣）强化百分比。",
            "§f  /magic recalc §7<玩家>",
            "§7    按当前书内法术重算法力加成 + 流派强化 NBT 缓存，并刷新 Curios。",
            "§f  /magic config",
            "§7    显示所有运行时配置的当前值（上限开关 / 数值 / 已觉醒数 / 封锁状态 / 数值规则）。",
            "§a§l▌ §e§l五、法力值 §8（权限 2/3）",
            "§f  /magic mana set §7<玩家> <值> §8（权限 2）",
            "§7    直接把玩家当前法力设为 <值>（会被 max_mana 夹紧）。",
            "§f  /magic mana fill §7<玩家> §8（权限 2）",
            "§7    把玩家当前法力填满到 max_mana。",
            "§f  /magic mana bonus §7<玩家> <值> §c[权限 3]",
            "§7    直接覆盖异常书上的额外法力 NBT（书内法术变动会触发重算并覆盖该值）。",
            "§a§l▌ §e§l六、流派强化直改 §8（权限 3）",
            "§f  /magic schoolbonus §7<玩家> <流派> <百分比> §c[权限 3]",
            "§7    直接覆盖异常书上某流派的强化百分比（MULTIPLY_BASE，0-500）。",
            "§7    书内法术变动会触发重算并覆盖该值。",
            "§a§l▌ §e§l七、异常特性吞噬物 §8（权限 2）",
            "§f  /magic trait give §7<玩家> <序列> <阶> §8[数量=1]",
            "§7    给玩家发放 trait_<序列>_<阶> 吞噬物物品。阶 ∈ B/A/S。",
            "§a§l▌ §e§l八、上限系统 §8（权限 2/3）",
            "§f  /magic limit info §8（权限 2）",
            "§7    显示全服已觉醒人数、当前上限值及满额状态。",
            "§f  /magic limit set §7<数值 1-9999> §c（权限 3）",
            "§7    运行时修改上限数值，立即生效并持久化（重启后保留）。",
            "§f  /magic limit enable §c（权限 3）",
            "§7    开启上限检测（B 级掉落拦截恢复）。",
            "§f  /magic limit disable §c（权限 3）",
            "§7    关闭上限检测（B 级掉落不再受限）。",
            "§f  /magic limit recount §c（权限 3）",
            "§7    重扫所有在线玩家书内法术状态，更新觉醒计数；离线记录保留。",
            "§a§l▌ §e§l九、生生不息封锁 §8（权限 4）",
            "§f  /magic seal endless_life",
            "§7    查看【生生不息】当前的全局封锁状态。",
            "§f  /magic seal endless_life <true|false>",
            "§7    手动开启或解除【生生不息】的全局封锁。",
            "§a§l▌ §e§l十、每日限定法术刷新 §8（权限 3）",
            "§f  /magic refresh all §c[权限 3]",
            "§7    全服刷新「每日限定」法术（当前仅日轮金乌 golden_crow_sun）。",
            "§7    实现机制：递增一个全局 generation；所有用过的玩家（含离线）下一次施法都会被放行。",
            "§7    注意：日轮金乌不再做游戏日检测，每次施放即被锁定，必须管理员调用本指令才能再次使用。",
            "§7    当前 generation 可在 /magic config 中查看。",
            "§6§l══════════ 法术 ID 对照表（共 35 个） ══════════",
            "§b虚境序列§7（B×4 A×2 S×1）",
            "§7  B: §fsonic_sense§7=音波  §fdanger_sense§7=危机  §folfaction§7=嗅觉  §fmark§7=印记",
            "§7  A: §frecorder_officer§7=记录官  §felementalist§7=元素使",
            "§7  S: §frewind_worm§7=回溯之虫  §8[待实现]",
            "§e日兆序列§7（B×4 A×2 S×1）",
            "§7  B: §ffertile_land§7=沃土  §fninghe§7=宁禾  §fsunlight§7=日光  §faffinity§7=亲和",
            "§7  A: §flight_prayer§7=祈光人  §fmidas_touch§7=点金客",
            "§7  S: §fgolden_crow_sun§7=日轮金乌",
            "§2东岳序列§7（B×4 A×2 S×1）",
            "§7  B: §fdaiyue§7=岱岳  §fmania§7=躁狂  §finstinct§7=本能  §fnecrotic_rebirth§7=冥化",
            "§7  A: §fimpermanence_monk§7=无常僧  §fexecutioner§7=刽子手",
            "§7  S: §fgreat_necromancer§7=大冥鬼师  §8[待实现]",
            "§5愚者序列§7（B×4 A×2 S×1）",
            "§7  B: §fwanxiang§7=万象  §ftelekinesis§7=念力  §fdominance§7=支配  §fmagnetic_cling§7=磁吸",
            "§7  A: §fmimic§7=模仿者  §flife_thief§7=盗命客",
            "§7  S: §fauthority_grasp§7=万权一手",
            "§3圣祈序列§7（B×4 A×2 S×1）",
            "§7  B: §fhuihun§7=回魂  §fhealing§7=愈合  §fstamina§7=耐力  §fapothecary§7=药师",
            "§7  A: §fgrafter§7=嫁接师  §fferryman§7=摆渡人",
            "§7  S: §fendless_life§7=生生不息",
            "§8用法示例：",
            "§8  /magic add Steve 音波 1 1",
            "§8  /magic unawaken Steve",
            "§8  /magic setsequence Steve 虚境",
            "§8  /magic setrank Steve A",
            "§8  /magic recalc Steve",
            "§8  /magic mana fill Steve",
            "§8  /magic schoolbonus Steve xujing 75",
            "§8  /magic trait give Steve shengqi B 2",
            "§8  /magic config",
            "§8  /magic refresh all",
            "§8§l[待] = 法术已规划但尚未实现，命令写入会报错。"
        };
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    // ────────────────────────── /magic rules ──────────────────────────

    private static int rulesGetAll(CommandSourceStack source) {
        var descriptors = com.mifan.admin.AdminPanelRegistry.scanConfigSpec(AnomalyConfig.SPEC);
        source.sendSuccess(() -> Component.literal("§b当前 " + descriptors.size() + " 条配置项:"), false);
        for (var d : descriptors) {
            source.sendSuccess(() -> Component.literal(
                    "§7- §e" + d.path() + " §7= §f" + d.currentValue()
                            + " §8(" + d.type().name().toLowerCase() + ", default=" + d.defaultValue() + ")"),
                    false);
        }
        return descriptors.size();
    }

    private static int rulesGet(CommandSourceStack source, String key) {
        var d = com.mifan.admin.AdminPanelRegistry.findField(AnomalyConfig.SPEC, key);
        if (d == null) {
            source.sendFailure(Component.literal("§c未找到配置项: " + key));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "§e" + d.path() + " §7= §f" + d.currentValue()
                        + " §8(default=" + d.defaultValue() + ", " + d.comment() + ")"),
                false);
        return 1;
    }

    private static int rulesSet(CommandSourceStack source, String key, String value) {
        boolean ok = com.mifan.admin.AdminPanelRegistry.writeField(AnomalyConfig.SPEC, key, value);
        if (!ok) {
            source.sendFailure(Component.literal("§c写入失败: " + key + " = " + value
                    + " (字段不存在或类型不匹配)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已更新: §e" + key + " §7→ §f" + value
                + " §8(已落盘,重载运行时副本中...)"), true);
        return 1;
    }

    private static int rulesReset(CommandSourceStack source) {
        var descriptors = com.mifan.admin.AdminPanelRegistry.scanConfigSpec(AnomalyConfig.SPEC);
        int n = 0;
        for (var d : descriptors) {
            if (com.mifan.admin.AdminPanelRegistry.writeField(AnomalyConfig.SPEC, d.path(),
                    String.valueOf(d.defaultValue()))) {
                n++;
            }
        }
        int count = n;
        source.sendSuccess(() -> Component.literal("§a已重置 §e" + count + " §a条配置项到默认值"), true);
        return n;
    }
}
