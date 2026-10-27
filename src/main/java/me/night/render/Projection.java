package me.night.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** Converts world positions to HUD (GUI-scaled) screen positions. */
public final class Projection {
    private Projection() {
    }

    private static Vec3d forward(Camera camera) {
        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());
        return new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    /** Distance of a point in front of the camera (negative = behind). */
    public static double depth(Vec3d world) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        return world.subtract(camera.getCameraPos()).dotProduct(forward(camera));
    }

    /** Screen position {x, y} in GUI units, or null if the point is behind the camera. */
    public static float[] toScreen(Vec3d world) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.gameRenderer.getCamera().isReady() || depth(world) <= 0.05) return null;
        Vec3d ndc = mc.gameRenderer.project(world);
        return new float[]{
                (float) ((ndc.x + 1.0) / 2.0 * mc.getWindow().getScaledWidth()),
                (float) ((1.0 - ndc.y) / 2.0 * mc.getWindow().getScaledHeight())
        };
    }
}
