package me.night.mixin;

import me.night.Night;
import me.night.module.modules.world.Scaffold;
import me.night.util.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {
    @Unique
    private float realYaw;
    @Unique
    private float realPitch;

    @Unique
    private boolean night$canSprintOrFly(ClientPlayerEntity player) {
        return player.getHungerManager().canSprint() || player.getAbilities().allowFlying;
    }

    @Inject(method = "tickMovementInput", at = @At("TAIL"))
    private void onTickMovementInput(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;

        // Auto-jump for Scaffold KeepY / Telly KeepY without modifying motion
        if (Night.INSTANCE != null && Night.INSTANCE.getModuleManager() != null) {
            Scaffold scaffold = Night.INSTANCE.getModuleManager().getModule(Scaffold.class);
            if (scaffold != null && scaffold.shouldKeepYJump()) {
                player.setJumping(true);
            }
        }

        if (RotationManager.hasRotations() && RotationManager.isMoveFixActive() && RotationManager.isForwardPressed()) {
            float[] fixed = RotationManager.fixStrafe(RotationManager.getYaw());
            float forward = fixed[0];
            float strafe = fixed[1];
            player.sidewaysSpeed = strafe * (player.isSneaking() ? 0.3F : 1.0F);
            player.forwardSpeed = forward * (player.isSneaking() ? 0.3F : 1.0F);
        }
    }

    @Inject(method = "shouldStopSprinting", at = @At("HEAD"), cancellable = true)
    private void onShouldStopSprinting(CallbackInfoReturnable<Boolean> cir) {
        // Prevent MoveFix from stopping sprint when player is holding movement keys
        if (RotationManager.hasRotations() && RotationManager.isMoveFixActive() && RotationManager.isForwardPressed()) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            if (!this.night$canSprintOrFly(player)) {
                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void onCanStartSprinting(CallbackInfoReturnable<Boolean> cir) {
        // Allow starting sprint while MoveFix is active
        if (RotationManager.hasRotations() && RotationManager.isMoveFixActive() && RotationManager.isForwardPressed()) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            if (!player.isSprinting() && this.night$canSprintOrFly(player) && !player.isSneaking()) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (RotationManager.hasRotations()) {
            this.realYaw = player.getYaw();
            this.realPitch = player.getPitch();
            player.setYaw(RotationManager.getYaw());
            player.setPitch(RotationManager.getPitch());
            RotationManager.markSent(RotationManager.getYaw(), RotationManager.getPitch());
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void onSendMovementPacketsTail(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (RotationManager.hasRotations()) {
            player.setYaw(this.realYaw);
            player.setPitch(this.realPitch);
            RotationManager.onPacketSent();
        }
    }
}
