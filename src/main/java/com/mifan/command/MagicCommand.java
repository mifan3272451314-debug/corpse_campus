package com.mifan.command;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalyConfig;
import com.mifan.anomaly.AnomalyLimitService;
import com.mifan.anomaly.AnomalySpellRank;
import com.mifan.registry.ModItems;
import com.mifan.spell.runtime.EndlessLifeRuntime;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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
                .then(Commands.literal("seal")
                        .then(Commands.literal("endless_life")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> sealStatus(context.getSource()))
                                .then(Commands.argument("sealed", BoolArgumentType.bool())
                                        .executes(context -> sealEndlessLife(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "sealed"))))))
                .then(Commands.literal("config")
                        .executes(context -> showConfig(context.getSource())))
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
        String[] lines = {
                "§6§l══════════ corpse_campus 当前运行时配置 ══════════",
                "§a§l▌ §e§l上限系统",
                "§f  limit.globalCapEnabled = §e" + service.isCapEnabled()
                        + "§7（SavedData 实时值；/magic limit enable|disable 可切）",
                "§f  limit.globalCapValue = §e" + service.getCapValue()
                        + "§7（SavedData 实时值；/magic limit set <value> 可改）",
                "§f  limit.countAwakenedPlayers = §e" + AnomalyConfig.countAwakenedPlayers
                        + "§7（config 文件值，需重载 config）",
                "§f  limit.disableBDropWhenFull = §e" + AnomalyConfig.disableBDropWhenFull
                        + "§7（config 文件值，需重载 config）",
                "§f  limit.autoRecountOnServerStart = §e" + AnomalyConfig.autoRecountOnServerStart
                        + "§7（config 文件值，需重载 config）",
                "§a§l▌ §e§l数值规则（代码常量，不可运行时调）",
                "§f  法力加成: B=+75 / A=+150 / S=+250 §7（AnomalySpellRank）",
                "§f  流派强化: B=+25% / A=+50% / S=+50% §7（AnomalySpellRank；MULTIPLY_BASE）",
                "§f  异常法术书槽位上限: " + AnomalyBookService.MAX_SPELL_SLOTS,
                "§a§l▌ §e§l其它",
                "§f  已觉醒玩家数: §e" + service.getAnomalyCount(),
                "§f  生生不息封锁: §e" + EndlessLifeRuntime.isSealed(server)
        };
        for (String s : lines) {
            source.sendSuccess(() -> Component.literal(s), false);
        }
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
            "§8§l[待] = 法术已规划但尚未实现，命令写入会报错。"
        };
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
