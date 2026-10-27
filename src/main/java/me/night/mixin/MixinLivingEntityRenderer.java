package me.night.mixin;

import me.night.util.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shows silent (server-side) rotations on your own body in third person. */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @Unique
    private static float night$yaw;
    @Unique
    private static float night$pitch;
    @Unique
    private static boolean night$active;
    @Unique
    private static long night$lastTime;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void night$silentRotations(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity != mc.player || mc.options.getPerspective().isFirstPerson()) {
            if (entity == mc.player) night$active = false;
            return;
        }

        boolean silent = RotationManager.recentlySent();
        float realYaw = state.bodyYaw + state.relativeHeadYaw;
        float realPitch = state.pitch;
        if (!silent && !night$active) return;

        long now = System.nanoTime();
        float dt = night$active ? Math.min(0.1F, (now - night$lastTime) / 1.0E9F) : 0;
        night$lastTime = now;
        if (!night$active) {
            night$yaw = realYaw;
            night$pitch = realPitch;
            night$active = true;
        }

        // ease towards the server rotation (or back to the real one once it stops)
        float targetYaw = silent ? RotationManager.getSentYaw() : realYaw;
        float targetPitch = silent ? RotationManager.getSentPitch() : realPitch;
        float k = 1.0F - (float) Math.exp(-dt * 22.0F);
        night$yaw += MathHelper.wrapDegrees(targetYaw - night$yaw) * k;
        night$pitch += (targetPitch - night$pitch) * k;

        if (!silent && Math.abs(MathHelper.wrapDegrees(realYaw - night$yaw)) < 1.5F && Math.abs(realPitch - night$pitch) < 1.5F) {
            night$active = false;
            return;
        }

        // head follows the rotation exactly; the body turns with it but lags up to 50 degrees
        float bodyOffset = MathHelper.clamp(MathHelper.wrapDegrees(night$yaw - state.bodyYaw), -50.0F, 50.0F);
        state.bodyYaw = night$yaw - bodyOffset;
        state.relativeHeadYaw = bodyOffset;
        state.pitch = night$pitch;
    }
}
