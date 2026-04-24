package com.mifan.compat.customnpcs;

import com.mifan.corpsecampus;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CustomNpcsCompat {
    private static final Logger LOGGER = LogManager.getLogger(corpsecampus.MODID + "/customnpcs-compat");

    private static final String NPC_INTERFACE_CLS = "noppes.npcs.entity.EntityNPCInterface";

    private static final Class<?> NPC_INTERFACE;
    private static final boolean LOADED;

    static {
        Class<?> cls = null;
        try {
            cls = Class.forName(NPC_INTERFACE_CLS);
            LOGGER.info("CustomNPCs detected, compat layer enabled");
        } catch (ClassNotFoundException notInstalled) {
            LOGGER.info("CustomNPCs not installed, compat layer disabled");
        } catch (Throwable t) {
            LOGGER.warn("CustomNPCs compat layer failed to initialize, disabled", t);
        }
        NPC_INTERFACE = cls;
        LOADED = cls != null;
    }

    private CustomNpcsCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isCustomNpc(Entity entity) {
        return LOADED && entity != null && NPC_INTERFACE.isInstance(entity);
    }
}
