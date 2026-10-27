package me.night.mixin;

import me.night.Night;
import me.night.event.events.Render2DEvent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class MixinInGameHud {
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        me.night.render.Blur.newFrame();
        me.night.module.modules.render.HUD hud = me.night.Night.INSTANCE.getModuleManager().getModule(me.night.module.modules.render.HUD.class);
        me.night.render.Blur.prepare(hud != null && hud.isEnabled() && hud.blur.getValue());
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true)
    private void night$scoreboard(DrawContext context, net.minecraft.scoreboard.ScoreboardObjective objective, CallbackInfo ci) {
        me.night.module.modules.render.HUD hud = me.night.Night.INSTANCE.getModuleManager().getModule(me.night.module.modules.render.HUD.class);
        if (hud != null && hud.replacesScoreboard()) {
            hud.drawScoreboard(context, objective);
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void night$effects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        me.night.module.modules.render.HUD hud = me.night.Night.INSTANCE.getModuleManager().getModule(me.night.module.modules.render.HUD.class);
        if (hud != null && hud.replacesEffects()) ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Night.INSTANCE != null && Night.INSTANCE.getEventManager() != null) {
            Night.INSTANCE.getEventManager().post(new Render2DEvent(context, tickCounter));
        }
    }
}
