package com.mifan.command;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalyLimitService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
                .then(Commands.literal("limit")
                        .then(Commands.literal("info")
                                .executes(context -> limitInfo(context.getSource())))
                        .then(Commands.literal("recount")
                                .requires(source -> source.hasPermission(3))
                                .executes(context -> limitRecount(context.getSource()))))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource()))));
    }

    private static int addSpell(CommandSourceStack source, ServerPlayer target, String spellInput, int level, int count) {
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

    private static int showHelp(CommandSourceStack source) {
        String[] lines = {
            "§6§l═══ /magic 指令文档 ═══",
            "§e书管理",
            "§f  /magic givebook §7<玩家> §8[0|1]",
            "§7    发放或重建异常法术书。第二参数传 1 时强制重建。",
            "§f  /magic forceequip §7<玩家>",
            "§7    强制将异常书放回 Curios 书槽。",
            "§f  /magic fixbook §7<玩家>",
            "§7    自动修复书绑定并强制佩戴，等同于 givebook + forceequip。",
            "§e法术管理",
            "§f  /magic add §7<玩家> <法术ID或中文名> <等级> <次数>",
            "§7    向目标异常书写入法术。若已有低等级版本则升级，不产生重复槽位。",
            "§7    中文别名示例：音波 危机 嗅觉 印记 记录官 元素使",
            "§7                  亲和 点金客 岱岳 躁狂 本能 冥化 刽子手",
            "§7                  万象 念力 支配 磁吸 盗命客 回魂 愈合 耐力 药师",
            "§f  /magic spells §7<玩家>",
            "§7    列出目标异常书中当前装载的所有法术（ID / 等级 / 阶级）。",
            "§f  /magic clear §7<玩家> <法术>",
            "§7    完整移除目标书中的指定法术（不留任何等级）。",
            "§f  /magic clearall §7<玩家>",
            "§7    清空目标书中的全部法术。",
            "§f  /magic remove §7<玩家> <法术> §8[次数]",
            "§7    从指定法术的等级中扣除 count 次（默认 1）。降至 0 则移除。",
            "§e状态查询",
            "§f  /magic state §7<玩家>",
            "§7    查看目标玩家的书 ID、绑定信息、额外法力与五大流派强化加成。",
            "§e上限系统",
            "§f  /magic limit info",
            "§7    查看全服已觉醒人数、上限值及是否满额。",
            "§f  /magic limit recount §c[需权限 3]",
            "§7    重新扫描在线玩家，刷新觉醒计数（离线记录保留）。",
            "§8提示：法术 ID 支持注册名（如 sonic_sense）、中文名（音波）",
            "§8      以及 corpse_campus:sonic_sense 完整格式。"
        };
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
