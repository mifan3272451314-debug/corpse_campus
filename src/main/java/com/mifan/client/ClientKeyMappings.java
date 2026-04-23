package com.mifan.client;

import com.mifan.corpsecampus;
import com.mifan.client.screen.PlayerStatusScreen;
import com.mifan.network.ModNetwork;
import com.mifan.network.serverbound.RequestAdminPanelPacket;
import com.mifan.registry.ModItems;
import com.mifan.screeneffect.client.ScreenEffectSettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyMappings {
    public static final String CATEGORY = "key.categories.corpse_campus";
    public static final KeyMapping OPEN_PLAYER_STATUS = new KeyMapping(
            "key.corpse_campus.open_player_status",
            GLFW.GLFW_KEY_V,
            CATEGORY);
    public static final KeyMapping OPEN_ADMIN_PANEL = new KeyMapping(
            "key.corpse_campus.open_admin_panel",
            GLFW.GLFW_KEY_K,
            CATEGORY);
    public static final KeyMapping OPEN_SCREEN_EFFECT_SETTINGS = new KeyMapping(
            "key.corpse_campus.open_screen_effect_settings",
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);

    private ClientKeyMappings() {
    }

    @Mod.EventBusSubscriber(modid = corpsecampus.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_PLAYER_STATUS);
            event.register(OPEN_ADMIN_PANEL);
            event.register(OPEN_SCREEN_EFFECT_SETTINGS);
        }
    }

    @Mod.EventBusSubscriber(modid = corpsecampus.MODID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) {
                return;
            }

            while (OPEN_PLAYER_STATUS.consumeClick()) {
                if (minecraft.screen == null && hasDetector(minecraft)) {
                    minecraft.setScreen(new PlayerStatusScreen());
                }
            }

            while (OPEN_ADMIN_PANEL.consumeClick()) {
                if (minecraft.screen == null) {
                    // 服务端会 check op2 后回 OpenAdminPanelPacket 开屏;非 op 收到拒绝消息
                    ModNetwork.CHANNEL.sendToServer(new RequestAdminPanelPacket());
                }
            }

            while (OPEN_SCREEN_EFFECT_SETTINGS.consumeClick()) {
                if (minecraft.screen == null) {
                    minecraft.setScreen(new ScreenEffectSettingsScreen());
                }
            }
        }

        private static boolean hasDetector(Minecraft minecraft) {
            if (minecraft.player == null) {
                return false;
            }

            ItemStack mainHand = minecraft.player.getMainHandItem();
            ItemStack offHand = minecraft.player.getOffhandItem();
            if (mainHand.is(ModItems.ANOMALY_DETECTOR.get()) || offHand.is(ModItems.ANOMALY_DETECTOR.get())) {
                return true;
            }

            for (ItemStack stack : minecraft.player.getInventory().items) {
                if (stack.is(ModItems.ANOMALY_DETECTOR.get())) {
                    return true;
                }
            }

            for (ItemStack stack : minecraft.player.getInventory().offhand) {
                if (stack.is(ModItems.ANOMALY_DETECTOR.get())) {
                    return true;
                }
            }
            return false;
        }
    }
}
