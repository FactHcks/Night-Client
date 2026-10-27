package me.night.util;

public final class ColorUtil {
    private ColorUtil() {
    }

    public static int alpha(int c) {
        return (c >>> 24) & 0xFF;
    }

    public static int withAlpha(int c, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (c & 0x00FFFFFF);
    }

    public static int mulAlpha(int c, float factor) {
        return withAlpha(c, Math.round(alpha(c) * factor));
    }

    public static int lerp(int c1, int c2, float t) {
        t = Anim.clamp01(t);
        int a = (int) (alpha(c1) + (alpha(c2) - alpha(c1)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int brighter(int c, float amount) {
        return lerp(c, (c & 0xFF000000) | 0x00FFFFFF, amount);
    }

    public static int darker(int c, float amount) {
        return lerp(c, c & 0xFF000000, amount);
    }
}
