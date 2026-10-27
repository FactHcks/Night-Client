package me.night.mixin;

import me.night.util.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
    @Redirect(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float night$jumpYaw(LivingEntity entity) {
        if (entity == MinecraftClient.getInstance().player && RotationManager.hasRotations() && RotationManager.isMoveFixActive()) {
            if (RotationManager.getMoveFix() == RotationManager.MoveFix.STRICT) {
                return RotationManager.getYaw();
            } else {
                return RotationManager.adjustYaw(entity.getYaw(), RotationManager.getForwardValue(), RotationManager.getLeftValue());
            }
        }
        return entity.getYaw();
    }
}
