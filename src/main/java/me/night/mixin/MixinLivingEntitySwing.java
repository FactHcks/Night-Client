package me.night.mixin;

import me.night.module.modules.render.Animations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swing speed setting of the Animations module (your own arm only). */
@Mixin(LivingEntity.class)
public class MixinLivingEntitySwing {
    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void night$swingSpeed(CallbackInfoReturnable<Integer> cir) {
        Animations anim = Animations.active();
        if (anim == null || (Object) this != MinecraftClient.getInstance().player) return;
        double speed = anim.swingSpeed.getValue();
        if (speed != 1.0) cir.setReturnValue(Math.max(1, (int) Math.round(cir.getReturnValue() / speed)));
    }
}
