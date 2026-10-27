package me.night.mixin;

import me.night.Night;
import me.night.event.events.KeyEvent;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyInput keyInput, CallbackInfo ci) {
        if (Night.INSTANCE != null && Night.INSTANCE.getEventManager() != null) {
            boolean hadScreen = MinecraftClient.getInstance().currentScreen != null;
            KeyEvent event = new KeyEvent(keyInput.key(), keyInput.scancode(), action, keyInput.modifiers());
            Night.INSTANCE.getEventManager().post(event);
            if (!hadScreen && MinecraftClient.getInstance().currentScreen != null) {
                ci.cancel();
            }
        }
    }
}
