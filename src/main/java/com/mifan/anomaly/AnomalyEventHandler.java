package com.mifan.anomaly;

import com.mifan.corpsecampus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

import net.minecraft.world.item.ItemStack;

@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class AnomalyEventHandler {
    private static final String PENDING_TRAIT_DROPS = "PendingTraitDrops";

    private AnomalyEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AnomalyBookService.ensureBookPresent(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AnomalyBookService.ensureBookPresent(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag oldPersisted = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, oldPersisted.copy());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (event.player instanceof ServerPlayer player && player.tickCount % AnomalyBookService.CHECK_INTERVAL_TICKS == 0) {
            AnomalyBookService.ensureBookPresent(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }

        List<ItemStack> pendingDrops = AnomalyBookService.clearBookAndCollectTraitDrops(player);
        if (pendingDrops.isEmpty()) {
            return;
        }

        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag anomalyData = persisted.getCompound("AnomalyP0");
        for (int i = 0; i < pendingDrops.size(); i++) {
            anomalyData.put("PendingTraitDrop_" + i, pendingDrops.get(i).save(new CompoundTag()));
        }
        anomalyData.putInt(PENDING_TRAIT_DROPS, pendingDrops.size());
        persisted.put("AnomalyP0", anomalyData);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    @SubscribeEvent
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }

        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag anomalyData = persisted.getCompound("AnomalyP0");
        int count = anomalyData.getInt(PENDING_TRAIT_DROPS);
        for (int i = 0; i < count; i++) {
            ItemStack stack = ItemStack.of(anomalyData.getCompound("PendingTraitDrop_" + i));
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
            event.getDrops().add(entity);
        }

        for (int i = 0; i < count; i++) {
            anomalyData.remove("PendingTraitDrop_" + i);
        }
        anomalyData.remove(PENDING_TRAIT_DROPS);
        persisted.put("AnomalyP0", anomalyData);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
