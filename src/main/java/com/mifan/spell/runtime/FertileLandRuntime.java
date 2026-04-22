package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.corpsecampus;
import com.mifan.spell.rizhao.FertileLandSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * 沃土（fertile_land）法术：被动常驻，分 15 级按玩家原版 XP 等级解锁分段效果。
 * 所有等级判定基于 {@link Player#experienceLevel}。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class FertileLandRuntime {

    public static final ResourceLocation FERTILE_LAND_ID = FertileLandSpell.SPELL_ID;

    public static final String TAG_GRANT_COOLDOWN = "corpse_campus_fertile_grant_cd";
    public static final String TAG_OWN_GROWN = "corpse_campus_fertile_own";

    private static final int HAND_GROW_COOLDOWN_TICKS = 600;
    private static final int SAPLING_TICK_INTERVAL = 40;
    private static final int HYDRATE_INTERVAL_TICKS = 20;
    private static final int HOE_EXHAUSTION_INTERVAL_TICKS = 20;

    private FertileLandRuntime() {
    }

    public static boolean isActive(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack book = AnomalyBookService.findBookForRead(player);
        if (book.isEmpty()) {
            return false;
        }
        for (SpellSlot slot : ISpellContainer.getOrCreate(book).getActiveSpells()) {
            if (FERTILE_LAND_ID.equals(slot.getSpell().getSpellResource())) {
                return true;
            }
        }
        return false;
    }

    public static int tier(Player player) {
        return player == null ? 0 : Math.max(0, player.experienceLevel);
    }

    // ─── Tick-driven passives ────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!isActive(player)) {
            return;
        }

        int xp = tier(player);
        long gameTime = serverLevel.getGameTime();

        if (xp >= 7 && gameTime % HYDRATE_INTERVAL_TICKS == 0) {
            hydrateFarmland(serverLevel, player, xp >= 12 ? 4 : 3);
        }

        if (xp >= 5 && gameTime % HOE_EXHAUSTION_INTERVAL_TICKS == 0) {
            relieveHoeExhaustion(player);
        }

        if (xp >= 6 && gameTime % SAPLING_TICK_INTERVAL == 0) {
            accelerateSaplings(serverLevel, player, xp);
        }

        if (xp >= 10 && gameTime % 60 == 0) {
            sprinkleTreeFlora(serverLevel, player);
        }
    }

    private static void hydrateFarmland(ServerLevel level, Player player, int radius) {
        BlockPos center = player.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof FarmBlock
                            && state.hasProperty(FarmBlock.MOISTURE)
                            && state.getValue(FarmBlock.MOISTURE) < 7) {
                        level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void relieveHoeExhaustion(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (!(mainHand.getItem() instanceof HoeItem) && !(offHand.getItem() instanceof HoeItem)) {
            return;
        }
        net.minecraft.world.food.FoodData food = player.getFoodData();
        float exhaustion = food.getExhaustionLevel();
        if (exhaustion > 0.2F) {
            // Reflection-free path: cause a tiny negative exhaustion via addExhaustion(-x) isn't public.
            // Instead grant a tiny saturation tick when holding hoe and hungry.
            if (food.getFoodLevel() < 20 && food.getSaturationLevel() < 2.0F) {
                food.eat(0, 0.4F);
            }
        }
    }

    private static void accelerateSaplings(ServerLevel level, Player player, int xp) {
        BlockPos center = player.blockPosition();
        RandomSource rng = level.getRandom();
        // Each tick interval, attempt a handful of random ticks on nearby saplings.
        int attempts = xp >= 10 ? 6 : 3;
        for (int i = 0; i < attempts; i++) {
            int dx = rng.nextInt(11) - 5;
            int dz = rng.nextInt(11) - 5;
            int dy = rng.nextInt(5) - 2;
            BlockPos pos = center.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SaplingBlock) {
                state.randomTick(level, pos, rng);
            }
        }
    }

    private static void sprinkleTreeFlora(ServerLevel level, Player player) {
        BlockPos center = player.blockPosition();
        RandomSource rng = level.getRandom();
        for (int attempts = 0; attempts < 4; attempts++) {
            int dx = rng.nextInt(11) - 5;
            int dz = rng.nextInt(11) - 5;
            int dy = rng.nextInt(7) - 2;
            BlockPos logPos = center.offset(dx, dy, dz);
            BlockState logState = level.getBlockState(logPos);
            if (!logState.is(net.minecraft.tags.BlockTags.LOGS)) {
                continue;
            }
            // Try to drop a flower / grass at the base of the log.
            for (int r = 1; r <= 3; r++) {
                int fx = rng.nextInt(r * 2 + 1) - r;
                int fz = rng.nextInt(r * 2 + 1) - r;
                BlockPos floraPos = logPos.offset(fx, -1, fz);
                BlockState base = level.getBlockState(floraPos);
                BlockPos onTop = floraPos.above();
                BlockState above = level.getBlockState(onTop);
                if ((base.is(Blocks.GRASS_BLOCK) || base.is(Blocks.DIRT)) && above.isAir()) {
                    Block flora = switch (rng.nextInt(5)) {
                        case 0 -> Blocks.POPPY;
                        case 1 -> Blocks.DANDELION;
                        case 2 -> Blocks.OXEYE_DAISY;
                        case 3 -> Blocks.GRASS;
                        default -> Blocks.FERN;
                    };
                    level.setBlock(onTop, flora.defaultBlockState(), Block.UPDATE_ALL);
                    break;
                }
            }
        }
    }

    // ─── Block break: yield, seeds, own-grown tag, hunger restore ────────────

    @SubscribeEvent
    public static void onCropBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!isActive(player)) {
            return;
        }
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (!(block instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            return;
        }

        int xp = tier(player);
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        RandomSource rng = serverLevel.getRandom();
        BlockPos pos = event.getPos();

        double yieldChance = yieldChanceFor(xp);
        int extraItem = 0;
        if (rng.nextFloat() < yieldChance) {
            extraItem = xp >= 15 ? (1 + (rng.nextFloat() < 0.5F ? 1 : 0)) : 1;
        }
        if (extraItem > 0) {
            ItemStack cropItem = resolveCropItem(block);
            if (!cropItem.isEmpty()) {
                cropItem.setCount(extraItem);
                if (xp >= 15) {
                    cropItem.getOrCreateTag().putBoolean(TAG_OWN_GROWN, true);
                }
                net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                        serverLevel, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, cropItem);
                serverLevel.addFreshEntity(drop);
            }
        }

        if (xp >= 5 && rng.nextFloat() < 0.25F) {
            ItemStack seeds = resolveSeedItem(block);
            if (!seeds.isEmpty()) {
                net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                        serverLevel, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, seeds);
                serverLevel.addFreshEntity(drop);
            }
        }

        if (xp >= 9 && rng.nextFloat() < 0.2F) {
            player.getFoodData().eat(1, 0.2F);
        }
    }

    private static double yieldChanceFor(int xp) {
        if (xp >= 15) {
            return 1.0D;
        }
        if (xp >= 10) {
            return 0.6D;
        }
        if (xp >= 5) {
            return 0.4D;
        }
        if (xp >= 1) {
            return 0.2D;
        }
        return 0.0D;
    }

    private static ItemStack resolveCropItem(Block block) {
        if (block == Blocks.WHEAT) return new ItemStack(net.minecraft.world.item.Items.WHEAT);
        if (block == Blocks.CARROTS) return new ItemStack(net.minecraft.world.item.Items.CARROT);
        if (block == Blocks.POTATOES) return new ItemStack(net.minecraft.world.item.Items.POTATO);
        if (block == Blocks.BEETROOTS) return new ItemStack(net.minecraft.world.item.Items.BEETROOT);
        return ItemStack.EMPTY;
    }

    private static ItemStack resolveSeedItem(Block block) {
        if (block == Blocks.WHEAT) return new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS);
        if (block == Blocks.BEETROOTS) return new ItemStack(net.minecraft.world.item.Items.BEETROOT_SEEDS);
        if (block == Blocks.CARROTS) return new ItemStack(net.minecraft.world.item.Items.CARROT);
        if (block == Blocks.POTATOES) return new ItemStack(net.minecraft.world.item.Items.POTATO);
        return ItemStack.EMPTY;
    }

    // ─── Crop grow (post): speed + double fruit ──────────────────────────────

    @SubscribeEvent
    public static void onCropGrow(BlockEvent.CropGrowEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        Player nearest = findNearbyFertilePlayer(serverLevel, pos, 10.0D);
        if (nearest == null) {
            return;
        }
        int xp = tier(nearest);
        RandomSource rng = serverLevel.getRandom();

        double extraChance = xp >= 15 ? 0.5D : xp >= 10 ? 0.25D : 0.0D;
        if (extraChance > 0.0D && rng.nextFloat() < extraChance) {
            BlockState fresh = serverLevel.getBlockState(pos);
            if (fresh.getBlock() instanceof CropBlock
                    || fresh.getBlock() instanceof StemBlock
                    || fresh.getBlock() instanceof SaplingBlock) {
                fresh.randomTick(serverLevel, pos, rng);
            }
        }

        if (xp >= 15) {
            Block block = state.getBlock();
            if (block instanceof AttachedStemBlock) {
                tryDoubleFruit(serverLevel, pos, state, rng);
            } else if (block instanceof StemBlock && state.hasProperty(StemBlock.AGE)
                    && state.getValue(StemBlock.AGE) >= 7) {
                tryDoubleFruit(serverLevel, pos, state, rng);
            }
        }
    }

    private static void tryDoubleFruit(ServerLevel level, BlockPos stemPos, BlockState stemState, RandomSource rng) {
        if (rng.nextFloat() > 0.25F) {
            return;
        }
        Block fruit = null;
        // Melon/pumpkin stems grow fruit on adjacent tiles at similar block y.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos fruitPos = stemPos.relative(dir);
            BlockState fruitState = level.getBlockState(fruitPos);
            if (fruitState.is(Blocks.PUMPKIN)) {
                fruit = Blocks.PUMPKIN;
            } else if (fruitState.is(Blocks.MELON)) {
                fruit = Blocks.MELON;
            }
            if (fruit != null) {
                for (Direction dir2 : Direction.Plane.HORIZONTAL) {
                    BlockPos candidate = stemPos.relative(dir2);
                    if (candidate.equals(fruitPos)) {
                        continue;
                    }
                    BlockState candidateState = level.getBlockState(candidate);
                    BlockState base = level.getBlockState(candidate.below());
                    boolean solidBase = base.is(Blocks.DIRT) || base.is(Blocks.GRASS_BLOCK)
                            || base.is(Blocks.FARMLAND) || base.is(Blocks.COARSE_DIRT)
                            || base.is(Blocks.ROOTED_DIRT);
                    if (candidateState.isAir() && solidBase) {
                        level.setBlock(candidate, fruit.defaultBlockState(), Block.UPDATE_ALL);
                        return;
                    }
                }
                return;
            }
        }
    }

    @Nullable
    private static Player findNearbyFertilePlayer(ServerLevel level, BlockPos pos, double radius) {
        for (Player p : level.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radius * radius
                    && isActive(p)) {
                return p;
            }
        }
        return null;
    }

    // ─── Farmland trample: no trample for fertile players ────────────────────

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isActive(player)) {
            return;
        }
        if (tier(player) >= 3) {
            event.setCanceled(true);
        }
    }

    // ─── Bonemeal save + extra ages ──────────────────────────────────────────

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        Player player = event.getEntity();
        if (player == null || !isActive(player)) {
            return;
        }
        int xp = tier(player);
        if (xp < 4) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState state = event.getBlock();
        BlockPos pos = event.getPos();
        RandomSource rng = serverLevel.getRandom();

        double saveChance = xp >= 15 ? 0.35D : xp >= 8 ? 0.25D : 0.15D;
        if (rng.nextFloat() < saveChance) {
            ItemStack held = player.getMainHandItem();
            if (held.is(net.minecraft.world.item.Items.BONE_MEAL)) {
                held.grow(1);
            } else {
                ItemStack off = player.getOffhandItem();
                if (off.is(net.minecraft.world.item.Items.BONE_MEAL)) {
                    off.grow(1);
                }
            }
        }

        if (xp >= 8 && (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock
                || state.getBlock() instanceof SaplingBlock)) {
            int extraTicks = 1 + rng.nextInt(2);
            for (int i = 0; i < extraTicks; i++) {
                BlockState current = serverLevel.getBlockState(pos);
                current.randomTick(serverLevel, pos, rng);
            }
        }
    }

    // ─── Right-click: empty-hand crop advance + composter 2x ─────────────────

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (!isActive(player)) {
            return;
        }
        Level level = player.level();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        if (tier(player) >= 15 && player.getMainHandItem().isEmpty()
                && event.getHand() == InteractionHand.MAIN_HAND) {
            if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                CompoundTag data = player.getPersistentData();
                long now = level.getGameTime();
                long cd = data.getLong(TAG_GRANT_COOLDOWN);
                if (now >= cd) {
                    int age = state.getValue(CropBlock.AGE);
                    level.setBlock(pos, state.setValue(CropBlock.AGE, Math.min(crop.getMaxAge(), age + 1)),
                            Block.UPDATE_ALL);
                    data.putLong(TAG_GRANT_COOLDOWN, now + HAND_GROW_COOLDOWN_TICKS);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BONE_MEAL_USE,
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.2F);
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    return;
                }
            }
        }

        if (tier(player) >= 13 && state.getBlock() instanceof ComposterBlock
                && level instanceof ServerLevel serverLevel) {
            RandomSource rng = serverLevel.getRandom();
            if (rng.nextFloat() < 0.5F) {
                serverLevel.getServer().execute(() -> {
                    BlockState post = serverLevel.getBlockState(pos);
                    if (post.getBlock() instanceof ComposterBlock
                            && post.hasProperty(ComposterBlock.LEVEL)) {
                        int lv = post.getValue(ComposterBlock.LEVEL);
                        if (lv > 0 && lv < 8) {
                            serverLevel.setBlock(pos,
                                    post.setValue(ComposterBlock.LEVEL, Math.min(8, lv + 1)),
                                    Block.UPDATE_ALL);
                        }
                    }
                });
            }
        }
    }

    // ─── Food finish: own-grown duration bonus ───────────────────────────────

    @SubscribeEvent
    public static void onFoodFinish(LivingEntityUseItemEvent.Finish event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        if (!isActive(player)) {
            return;
        }
        if (tier(player) < 15) {
            return;
        }
        ItemStack stack = event.getItem();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_OWN_GROWN)) {
            return;
        }
        // Extend any active effects obtained from this meal by +15% duration.
        for (MobEffectInstance eff : new ArrayList<>(player.getActiveEffects())) {
            int extra = Math.max(20, Math.round(eff.getDuration() * 0.15F));
            MobEffectInstance extended = new MobEffectInstance(eff.getEffect(),
                    eff.getDuration() + extra, eff.getAmplifier(), eff.isAmbient(), eff.isVisible(),
                    eff.showIcon());
            player.addEffect(extended);
        }
    }
}
