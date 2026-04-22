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
            "§a§l▌ §e§l三、状态查询 §8（权限 2）",
            "§f  /magic state §7<玩家>",
            "§7    显示书 ID、主人、额外法力值及五大流派（虚/日/东/愚/圣）强化百分比。",
            "§a§l▌ §e§l四、上限系统 §8（权限 2/3）",
            "§f  /magic limit info §8（权限 2）",
            "§7    显示全服已觉醒人数、当前上限值及满额状态。",
            "§f  /magic limit set §7<数值 1-9999> §c（权限 3）",
            "§7    运行时修改上限数值，立即生效并持久化（重启后保留）。",
            "§7    示例：/magic limit set 50  →  上限改为 50 人",
            "§f  /magic limit enable §c（权限 3）",
            "§7    开启上限检测（B 级掉落拦截恢复）。",
            "§f  /magic limit disable §c（权限 3）",
            "§7    关闭上限检测（B 级掉落不再受限）。",
            "§f  /magic limit recount §c（权限 3）",
            "§7    重扫所有在线玩家书内法术状态，更新觉醒计数；离线记录保留。",
            "§a§l▌ §e§l五、P1 推荐指令 §8（尚未实现，规划中）",
            "§8  /magic setsequence <玩家> <序列>     §7手动修正主序列   §c[权限 3]",
            "§8  /magic setrank <玩家> <B|A|S>        §7手动修正最高阶   §c[权限 3]",
            "§8  /magic recalc <玩家>                 §7重算法力与流派强化 §7[权限 2]",
            "§8  /magic unique info [序列]            §7查看 S 级唯一席位 §7[权限 2]",
            "§8  /magic unique release <序列>         §7强制释放 S 席位   §c[权限 4]",
            "§8  /magic trait give <玩家> <序列> <阶> <数>  §7发放异常吞噬物 §7[权限 2]",
            "§8  /magic promote <玩家> <目标阶>       §7强制晋升校验/调试 §c[权限 3]",
            "§8  /magic grafter unlock|lock <玩家>    §7嫁接师权限管理    §c[权限 3]",
            "§8  /magic seal endless_life <true|false> §7生生不息封号     §c[权限 4]",
            "§a§l▌ §e§l六、P2 高危/调试指令 §8（尚未实现）§c[权限 3/4]",
            "§8  /magic rewind backup create <槽位>   §7创建回溯之虫备份",
            "§8  /magic rewind backup enter <槽位>    §7进入镜像备份维度",
            "§8  /magic rewind backup info            §7查看备份状态",
            "§8  /magic debug dump <玩家>             §7导出玩家完整异常状态",
            "§8  /magic debug validateall             §7全服异常一致性校验",
            "§6§l══════════ 法术 ID 对照表（共 35 个） ══════════",
            "§b虚境序列§7（B×4 A×2 S×1）",
            "§7  B: §fsonic_sense§7=音波  §fdanger_sense§7=危机  §folfaction§7=嗅觉  §fmark§7=印记",
            "§7  A: §frecorder_officer§7=记录官  §felementalist§7=元素使",
            "§7  S: §frewind_worm§7=回溯之虫  §8[待实现]",
            "§eyellow日兆序列§7（B×4 A×2 S×1）",
            "§7  B: §ffertile_land§7=沃土§8[待]  §fninghe§7=宁禾§8[待]  §fsunlight§7=日光§8[待]  §faffinity§7=亲和",
            "§7  A: §flight_prayer§7=祈光人§8[待]  §fmidas_touch§7=点金客",
            "§7  S: §fgolden_crow_sun§7=日轮金乌  §8[待实现]",
            "§2东岳序列§7（B×4 A×2 S×1）",
            "§7  B: §fdaiyue§7=岱岳  §fmania§7=躁狂  §finstinct§7=本能  §fnecrotic_rebirth§7=冥化",
            "§7  A: §fimpermanence_monk§7=无常僧§8[待]  §fexecutioner§7=刽子手",
            "§7  S: §fgreat_necromancer§7=大冥鬼师  §8[待实现]",
            "§5愚者序列§7（B×4 A×2 S×1）",
            "§7  B: §fwanxiang§7=万象  §ftelekinesis§7=念力  §fdominance§7=支配  §fmagnetic_cling§7=磁吸",
            "§7  A: §fmimic§7=模仿者§8[待]  §flife_thief§7=盗命客",
            "§7  S: §fauthority_grasp§7=万权一手  §8[待实现]",
            "§3圣祈序列§7（B×4 A×2 S×1）",
            "§7  B: §fhuihun§7=回魂  §fhealing§7=愈合  §fstamina§7=耐力  §fapothecary§7=药师",
            "§7  A: §fgrafter§7=嫁接师§8[待]  §fferryman§7=摆渡人§8[待]",
            "§7  S: §fendless_life§7=生生不息  §8[待实现]",
            "§8用法示例：",
            "§8  /magic add Steve 音波 1 1",
            "§8  /magic add Steve sonic_sense 1 1",
            "§8  /magic add Steve corpse_campus:sonic_sense 1 1",
            "§8  /magic add Steve 点金客 3 1",
            "§8  /magic spells Steve",
            "§8  /magic clear Steve 危机",
            "§8  /magic state Steve",
            "§8  /magic limit info",
            "§8§l[待] = 法术已规划但尚未实现，命令写入会报错。"
        };
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
