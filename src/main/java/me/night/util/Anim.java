package me.night.util;

/**
 * Frame-rate independent value animator. Call {@link #update(float, float)} once per frame
 * with the target value; the value approaches it exponentially.
 */
public class Anim {
    private float value;
    private long lastTime;

    public Anim(float initial) {
        this.value = initial;
        this.lastTime = System.nanoTime();
    }

    public Anim() {
        this(0.0F);
    }

    /**
     * @param target value to approach
     * @param speed  higher is faster (≈ 1/seconds to reach ~63% of the way)
     */
    public float update(float target, float speed) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - this.lastTime) / 1.0E9F);
        this.lastTime = now;
        this.value += (target - this.value) * (1.0F - (float) Math.exp(-speed * dt));
        if (Math.abs(target - this.value) < 0.001F) {
            this.value = target;
        }
        return this.value;
    }

    public float get() {
        return this.value;
    }

    public void set(float value) {
        this.value = value;
        this.lastTime = System.nanoTime();
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3) / 2.0F;
    }

    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(t - 1.0F, 3) + c1 * (float) Math.pow(t - 1.0F, 2);
    }

    public static float clamp01(float t) {
        return t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
    }
}
