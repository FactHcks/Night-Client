package me.night.mixin;

import me.night.util.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {
    @Unique
    private float realYaw;
    @Unique
    private float realPitch;

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
            RotationManager.reset();
        }
    }
}
