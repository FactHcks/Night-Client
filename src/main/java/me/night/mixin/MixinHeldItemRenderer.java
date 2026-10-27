package me.night.mixin;

import me.night.module.modules.render.Animations;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Old (1.7 / 1.8) first-person sword animations, see the Animations module. */
@Mixin(HeldItemRenderer.class)
public abstract class MixinHeldItemRenderer {

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void night$oldAnimations(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                                     ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue,
                                     int light, CallbackInfo ci) {
        Animations anim = Animations.active();
        if (anim == null || hand != Hand.MAIN_HAND || item.isEmpty()) return;
        boolean block = anim.shouldBlock(item);
        boolean handheld = anim.oldPosition.getValue() && Animations.isHandheld(item);
        if (!block && !handheld) return;
        if (player.isUsingItem() && !Animations.isSword(item)) return; // eating etc. stays vanilla

        float side = player.getMainArm() == Arm.RIGHT ? 1 : -1;
        matrices.push();
        matrices.translate(anim.offsetX.getFloat() * side, anim.offsetY.getFloat(), anim.offsetZ.getFloat());

        if (block) {
            // 1.7 keeps the swing while blocking (block-hitting), 1.8 doesn't
            transformFirstPersonItem(matrices, side, equipProgress, anim.style.is("1.7") ? swingProgress : 0);
            matrices.translate(-0.5F * side, 0.2F, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.0F * side));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(60.0F * side));
        } else {
            // 1.8 "item used" push, then the normal 1.8 hold
            float s = MathHelper.sqrt(swingProgress);
            matrices.translate(-0.4F * MathHelper.sin(s * (float) Math.PI) * side,
                    0.2F * MathHelper.sin(s * (float) Math.PI * 2.0F),
                    -0.2F * MathHelper.sin(swingProgress * (float) Math.PI));
            transformFirstPersonItem(matrices, side, equipProgress, swingProgress);
        }

        // 1.8 first-person display of a handheld item: translation [0,4,2], rotation [0,-135,25], scale 1.7
        matrices.translate(0.0F, 4.0F / 16.0F, 2.0F / 16.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-135.0F * side));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(25.0F * side));
        float sc = 1.7F * anim.scale.getFloat();
        matrices.scale(sc, sc, sc);

        ((HeldItemRenderer) (Object) this).renderItem(player, item, ItemDisplayContext.NONE, matrices, queue, light);
        matrices.pop();
        ci.cancel();
    }

    /** 1.8's ItemRenderer.transformFirstPersonItem. */
    private static void transformFirstPersonItem(MatrixStack m, float side, float equip, float swing) {
        m.translate(0.56F * side, -0.52F, -0.72F);
        m.translate(0.0F, equip * -0.6F, 0.0F);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * side));
        float f = MathHelper.sin(swing * swing * (float) Math.PI);
        float f1 = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * -20.0F * side));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f1 * -20.0F * side));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f1 * -80.0F));
        m.scale(0.4F, 0.4F, 0.4F);
    }

    /** No item dip after attacking (the 1.9+ cooldown animation). */
    @Redirect(method = "updateHeldItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getHandEquippingProgress(F)F"))
    private float night$noCooldownDip(ClientPlayerEntity player, float tickDelta) {
        Animations anim = Animations.active();
        if (anim != null && anim.noEquipDip.getValue()) return 1.0F;
        return player.getHandEquippingProgress(tickDelta);
    }
}
