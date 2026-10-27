package me.night.module.modules.movement;

import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.util.math.Vec3d;

public class Flight extends Module {
    public final ModeProperty mode = new ModeProperty("Mode", "Motion", "Motion", "Creative", "Vanilla");
    public final NumberProperty speed = new NumberProperty("Speed", 2.0, 0.5, 10.0, 0.1);
    public final NumberProperty verticalSpeed = new NumberProperty("Vertical Speed", 1.2, 0.2, 5.0, 0.1);

    public Flight() {
        super("Flight", "Enables flight across the world", Category.MOVEMENT);
        addProperties(mode, speed, verticalSpeed);
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && mode.is("Creative")) {
            mc.player.getAbilities().flying = false;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        if (mode.is("Creative")) {
            mc.player.getAbilities().flying = true;
        } else if (mode.is("Motion")) {
            double s = speed.getValue();
            double vs = verticalSpeed.getValue();

            double forward = mc.player.input.getMovementInput().y;
            double strafe = mc.player.input.getMovementInput().x;
            float yaw = mc.player.getYaw();

            double motionX = 0;
            double motionZ = 0;

            if (forward != 0.0 || strafe != 0.0) {
                if (forward != 0.0) {
                    if (strafe > 0.0) {
                        yaw += (forward > 0.0 ? -45 : 45);
                    } else if (strafe < 0.0) {
                        yaw += (forward > 0.0 ? 45 : -45);
                    }
                    strafe = 0.0;
                    if (forward > 0.0) {
                        forward = 1.0;
                    } else if (forward < 0.0) {
                        forward = -1.0;
                    }
                }
                double rad = Math.toRadians(yaw + 90.0F);
                motionX = (forward * s * Math.cos(rad) + strafe * s * Math.sin(rad));
                motionZ = (forward * s * Math.sin(rad) - strafe * s * Math.cos(rad));
            }

            double y = 0.0;
            if (mc.options.jumpKey.isPressed()) y += vs;
            if (mc.options.sneakKey.isPressed()) y -= vs;

            mc.player.setVelocity(motionX, y, motionZ);
        } else {
            double s = speed.getValue();
            Vec3d vel = mc.player.getVelocity();
            double y = 0.0;
            if (mc.options.jumpKey.isPressed()) y += s * 0.5;
            if (mc.options.sneakKey.isPressed()) y -= s * 0.5;

            mc.player.setVelocity(vel.x, y, vel.z);
        }
    }
}
