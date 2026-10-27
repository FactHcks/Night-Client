package me.night.module.modules.movement;

import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;

public class Speed extends Module {
    public final ModeProperty mode = new ModeProperty("Mode", "Bhop", "Bhop", "Strafe", "Vanilla", "Ground");
    public final NumberProperty speed = new NumberProperty("Speed", 1.4, 0.5, 5.0, 0.05);
    public final BooleanProperty autoJump = new BooleanProperty("Auto Jump", true);

    public Speed() {
        super("Speed", "Increases player movement speed and air acceleration", Category.MOVEMENT);
        addProperties(mode, speed, autoJump);
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null) return;
        if (TargetStrafe.isStrafing) return;

        double forward = mc.player.input.getMovementInput().y;
        double strafe = mc.player.input.getMovementInput().x;
        boolean moving = forward != 0.0 || strafe != 0.0;

        if (!moving) return;

        double baseSpeed = speed.getValue();

        switch (mode.getValue().toLowerCase()) {
            case "bhop":
                if (mc.player.isOnGround()) {
                    if (autoJump.getValue()) {
                        mc.player.jump();
                    }
                    setMoveSpeed(baseSpeed * 0.48);
                } else {
                    setMoveSpeed(baseSpeed * 0.38);
                }
                break;

            case "strafe":
                if (mc.player.isOnGround() && autoJump.getValue()) {
                    mc.player.jump();
                }
                setMoveSpeed(baseSpeed * 0.36);
                break;

            case "vanilla":
                setMoveSpeed(baseSpeed * 0.2873);
                break;

            case "ground":
                if (mc.player.isOnGround()) {
                    setMoveSpeed(baseSpeed * 0.38);
                    mc.player.setVelocity(mc.player.getVelocity().x, 0.05, mc.player.getVelocity().z);
                }
                break;
        }
    }

    private void setMoveSpeed(double speedVal) {
        if (mc.player == null) return;
        double forward = mc.player.input.getMovementInput().y;
        double strafe = mc.player.input.getMovementInput().x;
        float yaw = mc.player.getYaw();

        if (forward == 0.0 && strafe == 0.0) {
            mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
            return;
        }

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
        double motionX = (forward * speedVal * Math.cos(rad) + strafe * speedVal * Math.sin(rad));
        double motionZ = (forward * speedVal * Math.sin(rad) - strafe * speedVal * Math.cos(rad));

        mc.player.setVelocity(motionX, mc.player.getVelocity().y, motionZ);
    }
}
