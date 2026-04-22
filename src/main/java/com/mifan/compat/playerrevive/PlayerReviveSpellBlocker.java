package com.mifan.compat.playerrevive;

import com.mifan.corpsecampus;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class PlayerReviveSpellBlocker {

    private PlayerReviveSpellBlocker() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellPreCast(SpellPreCastEvent event) {
        if (!PlayerReviveCompat.isLoaded()) {
            return;
        }
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        if (PlayerReviveCompat.isBleeding(player)) {
            event.setCanceled(true);
        }
    }
}
