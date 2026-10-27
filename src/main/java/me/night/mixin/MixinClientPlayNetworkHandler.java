package me.night.mixin;

import me.night.Night;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String content, CallbackInfo ci) {
        if (content != null && content.startsWith(".")) {
            if (Night.INSTANCE != null && Night.INSTANCE.getCommandManager() != null) {
                boolean handled = Night.INSTANCE.getCommandManager().execute(content);
                if (handled) {
                    ci.cancel();
                }
            }
        }
    }
}
