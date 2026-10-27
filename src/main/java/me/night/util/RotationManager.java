package me.night.util;

public class RotationManager {
    private static float yaw;
    private static float pitch;
    private static boolean active = false;

    public static void setRotations(float targetYaw, float targetPitch) {
        yaw = targetYaw;
        pitch = Math.max(-90.0F, Math.min(90.0F, targetPitch));
        active = true;
    }

    public static boolean hasRotations() {
        return active;
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    // last silent rotation actually sent to the server (for showing it on the player model)
    private static float sentYaw;
    private static float sentPitch;
    private static long sentTime;

    public static void markSent(float yaw, float pitch) {
        sentYaw = yaw;
        sentPitch = pitch;
        sentTime = System.currentTimeMillis();
    }

    /** True while silent rotations were sent in the last few ticks. */
    public static boolean recentlySent() {
        return System.currentTimeMillis() - sentTime < 160;
    }

    public static float getSentYaw() {
        return sentYaw;
    }

    public static float getSentPitch() {
        return sentPitch;
    }

    public static void reset() {
        active = false;
    }

    public static float[] calculateRotations(double eyeX, double eyeY, double eyeZ, double targetX, double targetY, double targetZ) {
        double diffX = targetX - eyeX;
        double diffY = targetY - eyeY;
        double diffZ = targetZ - eyeZ;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[]{yaw, pitch};
    }
}
