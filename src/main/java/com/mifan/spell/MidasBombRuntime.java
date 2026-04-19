package com.mifan.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MidasBombRuntime {
    public static final int MIN_TIMER_SECONDS = 1;
    public static final int MAX_TIMER_SECONDS = 120;
    public static final int DEFAULT_TIMER_SECONDS = 15;
    public static final int MIN_POWER_LEVEL = 1;
    public static final int MAX_POWER_LEVEL = 5;
    public static final int DEFAULT_POWER_LEVEL = 5;

    private static final String ITEM_TAG_ARMED = "corpse_campus_midas_bomb_armed";
    private static final String ITEM_TAG_END = "corpse_campus_midas_bomb_end";
    private static final String ITEM_TAG_LEVEL = "corpse_campus_midas_bomb_level";
    private static final String ITEM_TAG_OWNER = "corpse_campus_midas_bomb_owner";
    private static final String ITEM_TAG_SECONDS = "corpse_campus_midas_bomb_seconds";
    private static final String ITEM_TAG_POWER = "corpse_campus_midas_bomb_power";

    private static final float[] EXPLOSION_POWER_BY_LEVEL = {
            0.0F,
            1.5F,
            2.25F,
            3.0F,
            3.75F,
            4.5F
    };
    private static final int[] MANA_COST_BY_POWER_LEVEL = {
            0,
            30,
            55,
            90,
            140,
            220
    };
    private static final Map<Level, Map<BlockPos, ArmedBlockBomb>> BLOCK_BOMBS = new HashMap<>();

    private MidasBombRuntime() {
    }

    public static int clampSeconds(int seconds) {
        return Math.max(MIN_TIMER_SECONDS, Math.min(MAX_TIMER_SECONDS, seconds));
    }

    public static int clampPowerLevel(int powerLevel) {
        return Math.max(MIN_POWER_LEVEL, Math.min(MAX_POWER_LEVEL, powerLevel));
    }

    public static int getManaCostForPowerLevel(int powerLevel) {
        return MANA_COST_BY_POWER_LEVEL[clampPowerLevel(powerLevel)];
    }

    public static float getExplosionPowerForUi(int powerLevel) {
        return EXPLOSION_POWER_BY_LEVEL[clampPowerLevel(powerLevel)];
    }

    public static void armFromPlayerSelection(ServerPlayer player, int spellLevel, int timerSeconds, int powerLevel) {
        int seconds = clampSeconds(timerSeconds);
        int clampedPowerLevel = clampPowerLevel(powerLevel);
        long endGameTime = player.level().getGameTime() + seconds * 20L;
        boolean armedAnything = false;

        ItemStack heldStack = player.getMainHandItem();
        if (!heldStack.isEmpty()) {
            armItemStack(heldStack, player.getUUID(), spellLevel, seconds, clampedPowerLevel, endGameTime);
            armedAnything = true;
        }

        HitResult hitResult = player.pick(5.0D, 0.0F, false);
        if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() == HitResult.Type.BLOCK
                && player.level() instanceof ServerLevel serverLevel) {
            BlockPos pos = blockHitResult.getBlockPos();
            if (!serverLevel.isEmptyBlock(pos)) {
                armBlock(serverLevel, pos, player.getUUID(), spellLevel, seconds, clampedPowerLevel, endGameTime);
                armedAnything = true;
            }
        }

        if (!armedAnything) {
            player.displayClientMessage(Component.translatable("message.corpse_campus.midas_touch_no_target"), true);
            return;
        }

        player.level().playSound(null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS,
                0.4F,
                1.15F);
        player.displayClientMessage(
                Component.translatable("message.corpse_campus.midas_touch_armed", seconds, clampedPowerLevel),
                true);
    }

    public static void tickPlayerInventory(Player player) {
        long gameTime = player.level().getGameTime();
        tickInventoryList(player, player.getInventory().items, gameTime);
        tickInventoryList(player, player.getInventory().armor, gameTime);
        tickInventoryList(player, player.getInventory().offhand, gameTime);
    }

    private static void tickInventoryList(Player player, List<ItemStack> stacks, long gameTime) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!isBomb(stack) || getBombEndTime(stack) > gameTime) {
                continue;
            }

            float power = getExplosionPower(getBombLevel(stack), getBombPowerLevel(stack));
            clearBomb(stack);
            stacks.set(i, ItemStack.EMPTY);
            explode(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), power);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("message.corpse_campus.midas_touch_inventory_boom"), true);
            }
        }
    }

    public static void tickLevel(ServerLevel level) {
        Map<BlockPos, ArmedBlockBomb> blockBombs = BLOCK_BOMBS.get(level);
        if (blockBombs != null && !blockBombs.isEmpty()) {
            long gameTime = level.getGameTime();
            Iterator<Map.Entry<BlockPos, ArmedBlockBomb>> iterator = blockBombs.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, ArmedBlockBomb> entry = iterator.next();
                BlockPos pos = entry.getKey();
                ArmedBlockBomb bomb = entry.getValue();
                if (level.isEmptyBlock(pos)) {
                    iterator.remove();
                    continue;
                }
                if (bomb.endGameTime <= gameTime) {
                    iterator.remove();
                    detonateBlock(level, pos, bomb.level, bomb.powerLevel);
                }
            }
            if (blockBombs.isEmpty()) {
                BLOCK_BOMBS.remove(level);
            }
        }

        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, level.getWorldBorder().getCollisionShape().bounds())) {
            ItemStack stack = itemEntity.getItem();
            if (!isBomb(stack) || getBombEndTime(stack) > level.getGameTime()) {
                continue;
            }
            float power = getExplosionPower(getBombLevel(stack), getBombPowerLevel(stack));
            clearBomb(stack);
            itemEntity.discard();
            explode(level, itemEntity.getX(), itemEntity.getY() + 0.1D, itemEntity.getZ(), power);
        }
    }

    public static boolean detonateHeldBomb(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isBomb(stack)) {
            return false;
        }
        float power = getExplosionPower(getBombLevel(stack), getBombPowerLevel(stack));
        clearBomb(stack);
        player.setItemInHand(hand, ItemStack.EMPTY);
        explode(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), power);
        return true;
    }

    public static boolean detonateBlockIfArmed(Level level, BlockPos pos) {
        Map<BlockPos, ArmedBlockBomb> blockBombs = BLOCK_BOMBS.get(level);
        if (blockBombs == null) {
            return false;
        }
        ArmedBlockBomb bomb = blockBombs.remove(pos);
        if (bomb == null) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel) {
            detonateBlock(serverLevel, pos, bomb.level, bomb.powerLevel);
        }
        if (blockBombs.isEmpty()) {
            BLOCK_BOMBS.remove(level);
        }
        return true;
    }

    private static void armItemStack(ItemStack stack, UUID owner, int spellLevel, int seconds, int powerLevel,
            long endGameTime) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(ITEM_TAG_ARMED, true);
        tag.putLong(ITEM_TAG_END, endGameTime);
        tag.putInt(ITEM_TAG_LEVEL, spellLevel);
        tag.putUUID(ITEM_TAG_OWNER, owner);
        tag.putInt(ITEM_TAG_SECONDS, seconds);
        tag.putInt(ITEM_TAG_POWER, clampPowerLevel(powerLevel));
    }

    private static void armBlock(ServerLevel level, BlockPos pos, UUID owner, int spellLevel, int seconds,
            int powerLevel, long endGameTime) {
        BLOCK_BOMBS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(pos.immutable(), new ArmedBlockBomb(owner, spellLevel, seconds, clampPowerLevel(powerLevel), endGameTime));
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.35F, 1.4F);
    }

    private static void detonateBlock(ServerLevel level, BlockPos pos, int spellLevel, int powerLevel) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            level.removeBlock(pos, false);
        }
        explode(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, getExplosionPower(spellLevel, powerLevel));
    }

    private static void explode(Level level, double x, double y, double z, float power) {
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 0.5F, 1.0F);
        level.explode(null, x, y, z, power, Level.ExplosionInteraction.TNT);
    }

    private static boolean isBomb(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag() != null && stack.getTag().getBoolean(ITEM_TAG_ARMED);
    }

    private static long getBombEndTime(ItemStack stack) {
        return stack.getTag() == null ? 0L : stack.getTag().getLong(ITEM_TAG_END);
    }

    private static int getBombLevel(ItemStack stack) {
        return stack.getTag() == null ? 1 : Math.max(1, stack.getTag().getInt(ITEM_TAG_LEVEL));
    }

    private static int getBombPowerLevel(ItemStack stack) {
        return stack.getTag() == null ? DEFAULT_POWER_LEVEL : clampPowerLevel(stack.getTag().getInt(ITEM_TAG_POWER));
    }

    private static void clearBomb(ItemStack stack) {
        if (stack.getTag() == null) {
            return;
        }
        CompoundTag tag = stack.getTag();
        tag.remove(ITEM_TAG_ARMED);
        tag.remove(ITEM_TAG_END);
        tag.remove(ITEM_TAG_LEVEL);
        tag.remove(ITEM_TAG_OWNER);
        tag.remove(ITEM_TAG_SECONDS);
        tag.remove(ITEM_TAG_POWER);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    private static float getExplosionPower(int spellLevel, int powerLevel) {
        float basePower = EXPLOSION_POWER_BY_LEVEL[clampPowerLevel(powerLevel)];
        return basePower + Math.max(0, spellLevel - 1) * 0.2F;
    }

    private record ArmedBlockBomb(UUID owner, int level, int seconds, int powerLevel, long endGameTime) {
    }
}
