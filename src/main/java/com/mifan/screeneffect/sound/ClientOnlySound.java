package com.mifan.screeneffect.sound;

import com.mifan.screeneffect.config.ScreenEffectConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

public final class ClientOnlySound {

    private ClientOnlySound() {
    }

    public static void play(SoundEvent sound, float volume, float pitch) {
        if (sound == null) {
            return;
        }
        float master = ScreenEffectConfig.MASTER_VOLUME.get().floatValue();
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(sound, pitch, volume * master));
    }

    public static void play(SoundEvent sound) {
        play(sound, 1.0F, 1.0F);
    }
}
