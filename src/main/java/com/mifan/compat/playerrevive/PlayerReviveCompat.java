package com.mifan.compat.playerrevive;

import com.mifan.corpsecampus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

public final class PlayerReviveCompat {
    private static final Logger LOGGER = LogManager.getLogger(corpsecampus.MODID + "/playerrevive-compat");

    private static final String PR_SERVER_CLS = "team.creative.playerrevive.server.PlayerReviveServer";
    private static final String PR_IBLEEDING_CLS = "team.creative.playerrevive.api.IBleeding";
    private static final String PR_BLEEDING_NBT = "playerrevive:bleeding";

    private static final boolean LOADED;
    private static final MethodHandle MH_IS_BLEEDING;
    private static final MethodHandle MH_GET_BLEEDING;
    private static final MethodHandle MH_IBLEEDING_REVIVE;
    private static final MethodHandle MH_IBLEEDING_REVIVING_PLAYERS;
    private static final MethodHandle MH_SEND_UPDATE;

    static {
        boolean loaded = false;
        MethodHandle mhIsBleeding = null;
        MethodHandle mhGetBleeding = null;
        MethodHandle mhRevive = null;
        MethodHandle mhRevivingPlayers = null;
        MethodHandle mhSendUpdate = null;

        try {
            Class<?> serverCls = Class.forName(PR_SERVER_CLS);
            Class<?> iBleedingCls = Class.forName(PR_IBLEEDING_CLS);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            mhIsBleeding = lookup.findStatic(serverCls, "isBleeding",
                    MethodType.methodType(boolean.class, Player.class));
            mhGetBleeding = lookup.findStatic(serverCls, "getBleeding",
                    MethodType.methodType(iBleedingCls, Player.class));
            mhSendUpdate = lookup.findStatic(serverCls, "sendUpdatePacket",
                    MethodType.methodType(void.class, Player.class));
            mhRevive = lookup.findVirtual(iBleedingCls, "revive",
                    MethodType.methodType(void.class));
            mhRevivingPlayers = lookup.findVirtual(iBleedingCls, "revivingPlayers",
                    MethodType.methodType(List.class));

            loaded = true;
            LOGGER.info("PlayerRevive detected, compat layer enabled");
        } catch (ClassNotFoundException notInstalled) {
            LOGGER.info("PlayerRevive not installed, compat layer disabled");
        } catch (Throwable t) {
            LOGGER.warn("PlayerRevive compat layer failed to initialize, disabled", t);
        }

        LOADED = loaded;
        MH_IS_BLEEDING = mhIsBleeding;
        MH_GET_BLEEDING = mhGetBleeding;
        MH_IBLEEDING_REVIVE = mhRevive;
        MH_IBLEEDING_REVIVING_PLAYERS = mhRevivingPlayers;
        MH_SEND_UPDATE = mhSendUpdate;
    }

    private PlayerReviveCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isBleeding(Player player) {
        if (!LOADED || player == null) {
            return false;
        }
        try {
            return (boolean) MH_IS_BLEEDING.invoke(player);
        } catch (Throwable t) {
            LOGGER.debug("PlayerRevive isBleeding reflection failed", t);
            return false;
        }
    }

    public static void revive(ServerPlayer player) {
        if (!LOADED || player == null) {
            return;
        }
        try {
            Object bleeding = MH_GET_BLEEDING.invoke((Player) player);
            if (bleeding == null) {
                return;
            }
            MH_IBLEEDING_REVIVE.invoke(bleeding);
            Object helpers = MH_IBLEEDING_REVIVING_PLAYERS.invoke(bleeding);
            if (helpers instanceof List<?> list) {
                list.clear();
            }
            player.getPersistentData().remove(PR_BLEEDING_NBT);
            MH_SEND_UPDATE.invoke((Player) player);
            player.setForcedPose(null);
        } catch (Throwable t) {
            LOGGER.warn("PlayerRevive revive reflection failed for {}", player.getGameProfile().getName(), t);
        }
    }
}
