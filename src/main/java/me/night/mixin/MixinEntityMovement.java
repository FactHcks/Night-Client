package me.night.mixin;

import me.night.module.modules.movement.TargetStrafe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** TargetStrafe: turns your normal movement towards the strafe direction. */
@Mixin(Entity.class)
public abstract class MixinEntityMovement {
    @Shadow
    protected static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        throw new AssertionError();
    }

    @Redirect(method = "updateVelocity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d night$strafe(Vec3d input, float speed, float yaw) {
        Float strafeYaw = TargetStrafe.strafeYaw;
        if (strafeYaw != null && (Object) this == MinecraftClient.getInstance().player) {
            double len = Math.min(1.0, Math.sqrt(input.x * input.x + input.z * input.z));
            if (len > 1.0E-4) {
                // same amount of movement, just pointed along the circle
                return movementInputToVelocity(new Vec3d(0, input.y, len), speed, strafeYaw);
            }
        }
        return movementInputToVelocity(input, speed, yaw);
    }
}
