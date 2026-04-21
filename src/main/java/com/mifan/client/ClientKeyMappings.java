package com.mifan.client;

import com.mifan.corpsecampus;
import com.mifan.client.screen.PlayerStatusScreen;
import com.mifan.registry.ModItems;
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

    private ClientKeyMappings() {
    }

    @Mod.EventBusSubscriber(modid = corpsecampus.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_PLAYER_STATUS);
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
