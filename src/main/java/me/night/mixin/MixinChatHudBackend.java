package me.night.mixin;

import me.night.ui.glass.ChatPanel;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measures chat line backgrounds instead of drawing them (see ChatPanel). */
@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$Hud")
public class MixinChatHudBackend {
    @Shadow
    @Final
    private DrawContext context;

    @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
    private void night$fill(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        if (!ChatPanel.enabled()) return;
        ChatPanel.record(this.context, x1, y1, x2, y2, color);
        ci.cancel();
    }
}
