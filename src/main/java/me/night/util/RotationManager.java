package me.night.util;

import me.night.module.modules.movement.TargetStrafe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class RotationManager {
    public enum MoveFix {
        OFF, SILENT, STRICT;

        public static MoveFix from(String name) {
            if (name == null) return OFF;
            return switch (name.toUpperCase()) {
                case "SILENT" -> SILENT;
                case "STRICT" -> STRICT;
                default -> OFF;
            };
        }
    }

    private static float yaw;
    private static float pitch;
    private static float currentYaw;
    private static float currentPitch;
    private static boolean active = false;
    private static int lingerTicks = 0;
    private static MoveFix moveFix = MoveFix.OFF;

    // Last silent rotation actually sent to the server (for showing it on the player model)
    private static float sentYaw;
    private static float sentPitch;
    private static long sentTime;

    /**
     * Sets target rotations with smoothing, sensitivity GCD fix, and movement fix.
     */
    public static void setRotations(float targetYaw, float targetPitch, MoveFix fix, float turnSpeed, boolean gcd) {
        moveFix = fix;
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!active) {
            if (mc.player != null) {
                currentYaw = mc.player.getYaw();
                currentPitch = mc.player.getPitch();
            } else {
                currentYaw = targetYaw;
                currentPitch = targetPitch;
            }
        }

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;

        if (turnSpeed > 0 && turnSpeed < 180.0F) {
            deltaYaw = MathHelper.clamp(deltaYaw, -turnSpeed, turnSpeed);
            deltaPitch = MathHelper.clamp(deltaPitch, -turnSpeed, turnSpeed);
        }

        float nextYaw = currentYaw + deltaYaw;
        float nextPitch = MathHelper.clamp(currentPitch + deltaPitch, -90.0F, 90.0F);

        if (gcd) {
            float[] fixed = applyGcd(currentYaw, currentPitch, nextYaw, nextPitch);
            nextYaw = fixed[0];
            nextPitch = fixed[1];
        }

        currentYaw = nextYaw;
        currentPitch = nextPitch;
        yaw = currentYaw;
        pitch = currentPitch;
        active = true;
        lingerTicks = 5;
    }

    public static void setRotations(float targetYaw, float targetPitch, String moveFixMode, float turnSpeed, boolean gcd) {
        setRotations(targetYaw, targetPitch, MoveFix.from(moveFixMode), turnSpeed, gcd);
    }

    public static void setRotations(float targetYaw, float targetPitch) {
        setRotations(targetYaw, targetPitch, MoveFix.OFF, 180.0F, true);
    }

    public static void setRotations(float targetYaw, float targetPitch, String moveFixMode) {
        setRotations(targetYaw, targetPitch, MoveFix.from(moveFixMode), 180.0F, true);
    }

    public static void setRotations(float targetYaw, float targetPitch, MoveFix fix) {
        setRotations(targetYaw, targetPitch, fix, 180.0F, true);
    }

    /**
     * Anti-cheat bypass: quantizes rotation changes to the mouse sensitivity GCD.
     */
    public static float[] applyGcd(float curYaw, float curPitch, float targetYaw, float targetPitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return new float[]{targetYaw, targetPitch};

        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double f = sensitivity * 0.6 + 0.2;
        double gcd = f * f * f * 1.2;

        if (gcd <= 0.0001) return new float[]{targetYaw, targetPitch};

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
        float deltaPitch = targetPitch - curPitch;

        long deltaX = Math.round((double) deltaYaw / gcd);
        long deltaY = Math.round((double) deltaPitch / gcd);

        float fixedYaw = curYaw + (float) (deltaX * gcd);
        float fixedPitch = MathHelper.clamp(curPitch + (float) (deltaY * gcd), -90.0F, 90.0F);

        return new float[]{fixedYaw, fixedPitch};
    }

    public static boolean isForwardPressed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return false;
        boolean f = mc.options.forwardKey.isPressed() != mc.options.backKey.isPressed();
        boolean s = mc.options.leftKey.isPressed() != mc.options.rightKey.isPressed();
        return f || s;
    }

    public static int getForwardValue() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return 0;
        int forward = 0;
        if (mc.options.forwardKey.isPressed()) forward++;
        if (mc.options.backKey.isPressed()) forward--;
        return forward;
    }

    public static int getLeftValue() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return 0;
        int left = 0;
        if (mc.options.leftKey.isPressed()) left++;
        if (mc.options.rightKey.isPressed()) left--;
        return left;
    }

    public static float adjustYaw(float pYaw, float forward, float strafe) {
        Float strafeYaw = TargetStrafe.strafeYaw;
        if (strafeYaw != null) {
            return strafeYaw;
        }
        if (forward < 0.0f) {
            pYaw += 180.0f;
        }
        if (strafe != 0.0f) {
            float multiplier = forward == 0.0f ? 1.0f : 0.5f * Math.signum(forward);
            pYaw += -90.0f * multiplier * Math.signum(strafe);
        }
        return MathHelper.wrapDegrees(pYaw);
    }

    /**
     * Myau / OpenMyau fixStrafe implementation.
     * Maps player camera input to the 8 discrete directions relative to targetYaw.
     * Returns float[]{forward, strafe}.
     */
    public static float[] fixStrafe(float targetYaw) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return new float[]{0.0F, 0.0F};

        int fVal = getForwardValue();
        int lVal = getLeftValue();
        if (fVal == 0 && lVal == 0) {
            return new float[]{0.0F, 0.0F};
        }

        float angle = MathHelper.wrapDegrees(adjustYaw(mc.player.getYaw(), fVal, lVal) - targetYaw + 22.5F);
        int index = (int) Math.floor((angle + 180.0F) / 45.0F) % 8;
        if (index < 0) index += 8;

        float forward = 0.0F;
        float strafe = 0.0F;

        switch (index) {
            case 0 -> { forward = -1.0F; strafe = 0.0F; }
            case 1 -> { forward = -1.0F; strafe = 1.0F; }
            case 2 -> { forward = 0.0F;  strafe = 1.0F; }
            case 3 -> { forward = 1.0F;  strafe = 1.0F; }
            case 4 -> { forward = 1.0F;  strafe = 0.0F; }
            case 5 -> { forward = 1.0F;  strafe = -1.0F; }
            case 6 -> { forward = 0.0F;  strafe = -1.0F; }
            case 7 -> { forward = -1.0F; strafe = -1.0F; }
        }

        if (mc.options.sneakKey.isPressed()) {
            forward *= 0.3F;
            strafe *= 0.3F;
        }

        return new float[]{forward, strafe};
    }

    public static boolean hasRotations() {
        return active;
    }

    public static boolean isMoveFixActive() {
        return active && moveFix != MoveFix.OFF;
    }

    public static MoveFix getMoveFix() {
        return moveFix;
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    public static void markSent(float y, float p) {
        sentYaw = y;
        sentPitch = p;
        sentTime = System.currentTimeMillis();
    }

    public static void onPacketSent() {
        markSent(yaw, pitch);
        if (lingerTicks > 0) {
            lingerTicks--;
            if (lingerTicks == 0) {
                active = false;
            }
        }
    }

    /** True while silent rotations were sent in the last few ticks. */
    public static boolean recentlySent() {
        return System.currentTimeMillis() - sentTime < 250;
    }

    public static float getSentYaw() {
        return sentYaw;
    }

    public static float getSentPitch() {
        return sentPitch;
    }

    public static void reset() {
        active = false;
        lingerTicks = 0;
        moveFix = MoveFix.OFF;
    }

    public static float[] calculateRotations(double eyeX, double eyeY, double eyeZ, double targetX, double targetY, double targetZ) {
        double diffX = targetX - eyeX;
        double diffY = targetY - eyeY;
        double diffZ = targetZ - eyeZ;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float y = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float p = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[]{y, p};
    }
}
