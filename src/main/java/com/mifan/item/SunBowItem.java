package com.mifan.item;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.EvolutionAwakeningService;
import com.mifan.corpsecampus;
import com.mifan.registry.ModItems;
import com.mifan.registry.ModSchools;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * 日轮弓 — 日兆·日轮金乌觉醒道具。
 *
 * 由「日兆·日轮金乌·胚胎」右键变形而来。**不消耗箭矢、无耐久**。
 *
 * 玩法（仅服务端权威）：
 *   - 主序列必须为日兆系列
 *   - 仅白天可拉弓（dayTime % 24000 ∈ [0, 12000)）
 *   - 拉满弓（≥ 20 tick）+ 视线仰角 ≥ 35° 视为命中太阳
 *   - 每次命中：清零自身全部法术值 + 推进世界时间 3000 tick（"太阳坠落"）+ 累加 SunHits
 *   - 累计 4 次命中后弓原位变成「日兆·日轮金乌」核心，右键吞下完成 A→S 升位
 *   - 早晨 0 tick 起连射 4 次正好抵达 12000 tick（深夜）
 *
 * 互转：shift+右键变回胚胎（用户给"可回退"决策，避免误操作锁死）。
 */
public class SunBowItem extends BowItem {

    public static final String TAG_SUN_HITS = "SunHits";
    public static final ResourceLocation GOLDEN_CROW_SUN =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "golden_crow_sun");

    /** 每次命中推进的世界时间步长。早晨 0 → 12000（深夜起点）= 4 步 × 3000 tick。 */
    private static final long DAY_STEP_TICKS = 3000L;
    /** 拉满弓所需 tick 数（与原版弓一致）。 */
    private static final int REQUIRED_DRAW_TICKS = 20;
    /** 仰角阈值：sin(35°) ≈ 0.5736，避免玩家平射蒙混过关。 */
    private static final double SUN_AIM_THRESHOLD_Y = 0.5736D;
    /** 觉醒所需累计命中次数。 */
    private static final int REQUIRED_HITS = 4;

    public SunBowItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }
    @Override public int getEnchantmentValue() { return 0; }
    @Override public boolean canBeDepleted() { return false; }
    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    /** 不需要箭矢，所有 ItemStack 都被视为合法发射物（实际不会消耗）。 */
    @Override public Predicate<ItemStack> getAllSupportedProjectiles() { return s -> true; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // shift+右键 → 回退胚胎（保留可逆性）
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack embryo = SpellEmbryoItem.createFor(GOLDEN_CROW_SUN);
                embryo.setCount(Math.max(1, stack.getCount()));
                player.setItemInHand(hand, embryo);
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        // 主序列日兆才允许拉弓
        ItemStack book = AnomalyBookService.findBookForRead(player);
        ResourceLocation seq = book.isEmpty() ? null : AnomalyBookService.getMainSequenceId(book);
        if (seq == null || !seq.equals(ModSchools.RIZHAO_RESOURCE)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.wrong_sequence")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // 仅白天可拉弓（夜晚直接拒绝拉弓动作）
        if (!level.isDay()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.sun_bow_only_day")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // 自己开始拉弓（不调 super.use，避免父类的箭矢检查）
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        int drawTime = getUseDuration(stack) - timeLeft;
        if (drawTime < REQUIRED_DRAW_TICKS) return;       // 必须满弓

        // 白天再次校验（拉弓中天黑仍然拒绝）
        long dayTime = serverLevel.getDayTime() % 24000L;
        if (!serverLevel.isDay() || dayTime >= 12000L) {
            player.displayClientMessage(Component.translatable("message.corpse_campus.sun_bow_only_day")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // 视线仰角校验
        if (player.getViewVector(1F).y < SUN_AIM_THRESHOLD_Y) {
            player.displayClientMessage(Component.translatable("message.corpse_campus.sun_bow_aim_higher")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        // 主序列校验（双保险）
        ItemStack book = AnomalyBookService.getPlayerBook(player);
        ResourceLocation seq = book.isEmpty() ? null : AnomalyBookService.getMainSequenceId(book);
        if (seq == null || !seq.equals(ModSchools.RIZHAO_RESOURCE)) {
            player.displayClientMessage(Component.translatable("message.corpse_campus.evolution_denied.wrong_sequence")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // 清零自身全部法术值（ISS MagicData 自带 tick 同步，无需手动 sync）
        MagicData magicData = MagicData.getPlayerMagicData(player);
        magicData.setMana(0F);

        // 推进世界时间，模拟"太阳坠落"
        serverLevel.setDayTime(serverLevel.getDayTime() + DAY_STEP_TICKS);

        int hits = stack.getOrCreateTag().getInt(TAG_SUN_HITS) + 1;

        // 视听反馈：弓声 + 远雷模拟太阳坠落的轰鸣
        serverLevel.playSound(null, player.blockPosition(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.7F);
        serverLevel.playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0F, 1.5F);

        if (hits >= REQUIRED_HITS) {
            // 觉醒：弓 → 日兆·日轮金乌 S 核心（绕过 isAlreadyARankOrHigher 拦截）
            EvolutionAwakeningService.replaceHandStackWithCoreForcing(
                    player, player.getUsedItemHand(), GOLDEN_CROW_SUN);
            player.displayClientMessage(Component.translatable("message.corpse_campus.sun_bow_awaken")
                    .withStyle(ChatFormatting.GOLD), false);
        } else {
            stack.getOrCreateTag().putInt(TAG_SUN_HITS, hits);
            player.displayClientMessage(Component.translatable(
                    "message.corpse_campus.sun_falls", hits, REQUIRED_HITS)
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int hits = stack.getOrCreateTag().getInt(TAG_SUN_HITS);
        tooltip.add(Component.translatable("tooltip.corpse_campus.sun_bow_hits", hits, REQUIRED_HITS)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.corpse_campus.sun_bow_revert")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.corpse_campus.sun_bow_only_day")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.corpse_campus.sun_bow_drain_mana")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** 让 ModItems 注册时引用 SUN_BOW，避免循环依赖在编译期出现。 */
    @SuppressWarnings("unused")
    private static void touchModItemsAtCompileTime() {
        Object ignore = ModItems.SUN_BOW;
    }
}
