package com.mifan.command;

import com.mifan.anomaly.AnomalyBookService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
                .then(Commands.literal("clearall")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> clearAllSpells(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("state")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> showState(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("fixbook")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> fixBook(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
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
}
