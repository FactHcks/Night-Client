package me.night.mixin;

import me.night.Night;
import me.night.module.modules.render.HUD;
import me.night.render.Render2D;
import me.night.ui.glass.ChatPanel;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Glass chat input box. */
@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private void night$inputBox(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        if (!ChatPanel.enabled()) {
            ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        HUD hud = Night.INSTANCE.getModuleManager().getModule(HUD.class);
        boolean blur = hud != null && hud.blur.getValue();
        float x = x1, y = y1 - 1, w = x2 - x1, h = y2 - y1 + 2;
        Render2D.shadow(ctx, x, y + 1, w, h, 6, 8, 0x50000000);
        Render2D.glass(ctx, x, y, w, h, 6, 0xA00C0C12, blur);
        Render2D.outline(ctx, x, y, w, h, 6, 0.6F, ColorUtil.withAlpha(Theme.accent(0), 0x90));
    }
}
