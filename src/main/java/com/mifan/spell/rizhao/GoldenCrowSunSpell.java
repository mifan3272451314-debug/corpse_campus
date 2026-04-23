package com.mifan.spell.rizhao;

import com.mifan.entity.GoldenCrowSunEntity;
import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.GoldenCrowChannelingPacket;
import com.mifan.registry.ModSchools;
import com.mifan.spell.AbilityRuntime;
import com.mifan.spell.runtime.DailyAbilityRefreshState;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class GoldenCrowSunSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "golden_crow_sun");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(ModSchools.RIZHAO_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(AbilityRuntime.GOLDEN_CROW_COOLDOWN_SECONDS)
            .build();

    public GoldenCrowSunSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = AbilityRuntime.GOLDEN_CROW_CAST_TIME_TICKS;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.golden_crow_channel"),
                Component.translatable("tooltip.corpse_campus.golden_crow_summon"),
                Component.translatable("tooltip.corpse_campus.golden_crow_throw"),
                Component.translatable("tooltip.corpse_campus.golden_crow_damage",
                        AbilityRuntime.GOLDEN_CROW_DAMAGE_PER_MANA),
                Component.translatable("tooltip.corpse_campus.golden_crow_explosion_radius",
                        (int) AbilityRuntime.GOLDEN_CROW_EXPLOSION_RADIUS_MIN,
                        (int) AbilityRuntime.GOLDEN_CROW_EXPLOSION_RADIUS_MAX),
                Component.translatable("tooltip.corpse_campus.golden_crow_stun_radius",
                        (int) AbilityRuntime.GOLDEN_CROW_STUN_RADIUS_MIN,
                        (int) AbilityRuntime.GOLDEN_CROW_STUN_RADIUS_MAX,
                        AbilityRuntime.GOLDEN_CROW_STUN_DURATION_TICKS / 20),
                Component.translatable("tooltip.corpse_campus.golden_crow_duration",
                        AbilityRuntime.GOLDEN_CROW_DURATION_TICKS / 20 / 60),
                Component.translatable("tooltip.corpse_campus.golden_crow_mana_lock"),
                Component.translatable("tooltip.corpse_campus.golden_crow_daily"));
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.BEACON_POWER_SELECT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.GENERIC_EXPLODE);
    }

    @Override
    public void onClientCast(Level level, int spellLevel, LivingEntity entity,
            io.redspace.ironsspellbooks.api.spells.ICastData castData) {
        com.mifan.screeneffect.client.ScreenEffectClientHook.triggerIfLocalPlayer(entity, spellId);
        super.onClientCast(level, spellLevel, entity, castData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide && entity instanceof Player player) {
            handleCast(level, player, playerMagicData);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        super.onServerCastTick(level, spellLevel, entity, playerMagicData);
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player)) {
            return;
        }

        int castTime = AbilityRuntime.GOLDEN_CROW_CAST_TIME_TICKS;
        float progress;
        int elapsed;
        float previewMana;
        if (playerMagicData != null) {
            progress = Math.min(1.0F, Math.max(0.0F, playerMagicData.getCastCompletionPercent()));
            elapsed = Math.max(0, castTime - playerMagicData.getCastDurationRemaining());
            previewMana = playerMagicData.getMana();
        } else {
            progress = 0.0F;
            elapsed = 0;
            previewMana = 0.0F;
        }

        emitChannelingBuildup(server, player, progress, elapsed, previewMana);
    }

    private void emitChannelingBuildup(ServerLevel server, Player player, float progress, int tickElapsed,
            float previewMana) {
        double cx = player.getX();
        // 引导期太阳从玩家头顶 2 格逐步升到 HOVER_HEIGHT，和召唤完成后的悬浮位置连贯
        double targetCy = player.getEyeY() + AbilityRuntime.GOLDEN_CROW_HOVER_HEIGHT;
        double startCy = player.getEyeY() + 2.0D;
        double cy = startCy + (targetCy - startCy) * progress;
        double cz = player.getZ();

        // 目标半径：由即将消耗的法力推测，随进度线性插值到该目标
        double targetOrb = AbilityRuntime.goldenCrowOrbRadius(previewMana);
        float targetParticleScale = AbilityRuntime.goldenCrowParticleScale(previewMana);
        double radius = Math.max(0.6D, progress * targetOrb);
        double shellThickness = 0.8D + progress * Math.max(1.2D, targetOrb * 0.15D);

        // 1) 向心引力漩涡 + 地面日轮：迁到客户端本地生成（见 AbilityClientHandler.handleGoldenCrowChanneling）
        //    原 14×100 = 1400 packet/tick + 48 packet/tick 的循环全部归零，只发 1 个 trigger packet。
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            GoldenCrowChannelingPacket packet = new GoldenCrowChannelingPacket(
                    cx, cy, cz, player.getY() + 0.1D,
                    progress, targetOrb, targetParticleScale, tickElapsed);
            ModNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.NEAR.with(
                            () -> new net.minecraftforge.network.PacketDistributor.TargetPoint(
                                    cx, cy, cz, 128.0D, server.dimension())),
                    packet);
            // 让施法者自己也收到（NEAR 不包含发起玩家）
            ModNetwork.sendToPlayer(packet, serverPlayer);
        }

        // 2) 球心亮心——聚合 count 形式，仍由服务端发（单包，便宜，集群玩家共享观感）
        int coreDensity = Math.max(20, (int) (40 + progress * 300 * targetParticleScale));
        server.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, coreDensity,
                shellThickness, shellThickness, shellThickness, 0.02D);
        server.sendParticles(ParticleTypes.FLAME, cx, cy, cz, coreDensity * 2,
                shellThickness * 1.4D, shellThickness * 1.4D, shellThickness * 1.4D, 0.01D);

        // 3) 每 8 tick 心跳脉冲
        if (tickElapsed % 8 == 0) {
            server.sendParticles(ParticleTypes.FLASH, cx, cy, cz, Math.max(2, (int) (3 * targetParticleScale)),
                    0.0D, 0.0D, 0.0D, 0.0D);
            server.sendParticles(ParticleTypes.LAVA, cx, cy, cz, Math.max(8, (int) (24 * targetParticleScale)),
                    radius * 0.6D, radius * 0.6D, radius * 0.6D, 0.2D);
        }

        // 5) 后期每 10 tick 追加声音
        if (progress > 0.7F && tickElapsed % 10 == 0) {
            server.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.PLAYERS, 1.2F, 1.6F + progress);
        }
    }

    private void handleCast(Level level, Player player, MagicData magicData) {
        CompoundTag data = player.getPersistentData();

        // 锁定判定：只要玩家"上次使用时记录的 generation" >= 当前 generation，就视为"已用过且未被刷新"。
        // 管理员通过 /magic refresh all 让 generation++ 即可放行所有玩家（含离线）。
        MinecraftServer server = player.getServer();
        long currentGen = server == null ? 0L : DailyAbilityRefreshState.currentGeneration(server);
        if (data.contains(AbilityRuntime.TAG_GOLDEN_CROW_USED_GEN)
                && data.getLong(AbilityRuntime.TAG_GOLDEN_CROW_USED_GEN) >= currentGen) {
            player.displayClientMessage(
                    Component.translatable("message.corpse_campus.golden_crow_daily_limit"), true);
            return;
        }

        float manaSpent = magicData != null ? magicData.getMana() : 0.0F;
        if (magicData != null) {
            magicData.setMana(0.0F);
        }
        player.getPersistentData().putFloat(AbilityRuntime.TAG_RIZHAO_LAST_MANA, 0.0F);

        GoldenCrowSunEntity sun = new GoldenCrowSunEntity(level, player, manaSpent);
        level.addFreshEntity(sun);

        data.putBoolean(AbilityRuntime.TAG_GOLDEN_CROW_ACTIVE, true);
        data.putUUID(AbilityRuntime.TAG_GOLDEN_CROW_ENTITY_UUID, sun.getUUID());
        data.putLong(AbilityRuntime.TAG_GOLDEN_CROW_EXPIRE_TICK,
                level.getGameTime() + AbilityRuntime.GOLDEN_CROW_DURATION_TICKS);
        data.putFloat(AbilityRuntime.TAG_GOLDEN_CROW_MANA_SPENT, manaSpent);
        data.putLong(AbilityRuntime.TAG_GOLDEN_CROW_USED_GEN, currentGen);

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                1.6F, 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                2.0F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                3.0F, 1.2F);

        if (level instanceof ServerLevel serverLevel) {
            double ax = sun.getX();
            double ay = sun.getY();
            double az = sun.getZ();
            serverLevel.sendParticles(ParticleTypes.FLASH, ax, ay, az, 12, 1.5D, 1.5D, 1.5D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.LAVA, ax, ay, az, 80, 3.0D, 3.0D, 3.0D, 0.1D);
            serverLevel.sendParticles(ParticleTypes.FLAME, ax, ay, az, 300, 4.0D, 4.0D, 4.0D, 0.1D);
        }

        // 不再保留"召唤态等待二次右键投掷"的语义：召唤完成立刻自动甩出。
        sun.throwTowards(player);

        player.displayClientMessage(
                Component.translatable("message.corpse_campus.golden_crow_summoned",
                        String.format("%.0f", manaSpent)),
                true);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.RIZHAO.get();
    }
}
