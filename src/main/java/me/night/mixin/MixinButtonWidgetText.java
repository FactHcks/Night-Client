package me.night.mixin;

import me.night.ui.glass.GlassWidgets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Glass style for vanilla text buttons. */
@Mixin(targets = "net.minecraft.client.gui.widget.ButtonWidget$Text")
public class MixinButtonWidgetText {
    @Inject(method = "drawIcon", at = @At("HEAD"), cancellable = true)
    private void night$glassButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!GlassWidgets.enabled()) return;
        GlassWidgets.drawButton(context, (ClickableWidget) (Object) this);
        ci.cancel();
    }
}
