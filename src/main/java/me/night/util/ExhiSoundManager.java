package me.night.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public class ExhiSoundManager {
    public static void playClick(float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
        }
    }

    public static void playToggle(boolean enabled) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), enabled ? 1.0F : 0.8F));
        }
    }
}
