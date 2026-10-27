package me.night.mixin;

import me.night.module.modules.render.ESP;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Colours the glow outline of ESP targets. */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
            at = @At("TAIL"))
    private void night$glowColor(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (me.night.module.modules.render.Nametags.replaces(entity)) {
            state.displayName = null; // our glass nametag replaces it
        }
        if (ESP.shouldGlow(entity)) {
            state.outlineColor = ESP.glowColor(entity);
        }
    }
}
