package me.night.util;

import me.night.render.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

/**
 * Convenience layer over {@link Render2D} (GPU signed-distance shapes).
 * Kept so older callers keep working; new code can use Render2D directly.
 */
public final class NightRenderUtil {

    private NightRenderUtil() {
    }

    // ------------------------------------------------------------------ global alpha

    public static void setGlobalAlpha(float alpha) {
        Render2D.setAlpha(alpha);
    }

    public static float getGlobalAlpha() {
        return Render2D.alpha();
    }

    public static void resetGlobalAlpha() {
        Render2D.resetAlpha();
    }

    static int applyAlpha(int color) {
        float a = Render2D.alpha();
        return a >= 1.0F ? color : ColorUtil.mulAlpha(color, a);
    }

    // ------------------------------------------------------------------ shapes

    public static void roundedRect(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
        Render2D.rect(ctx, x, y, w, h, radius, color);
    }

    public static void roundedGradient(DrawContext ctx, float x, float y, float w, float h, float radius, int c1, int c2) {
        Render2D.rectH(ctx, x, y, w, h, radius, c1, c2);
    }

    public static void roundedOutline(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
        Render2D.outline(ctx, x, y, w, h, radius, thickness, color);
    }

    public static void shadow(DrawContext ctx, float x, float y, float w, float h, float radius, float spread, int color) {
        Render2D.shadow(ctx, x, y, w, h, radius, spread, color);
    }

    public static void circle(DrawContext ctx, float cx, float cy, float radius, int color) {
        Render2D.circle(ctx, cx, cy, radius, color);
    }

    /** Rainbow hue bar used by colour pickers. */
    public static void hueBar(DrawContext ctx, float x, float y, float w, float h) {
        float r = h / 2.0F;
        int segments = 12;
        float inner = w - 2 * r;
        Render2D.circle(ctx, x + r, y + r, r, Color.HSBtoRGB(0.0F, 0.75F, 1.0F));
        Render2D.circle(ctx, x + w - r, y + r, r, Color.HSBtoRGB(0.999F, 0.75F, 1.0F));
        for (int i = 0; i < segments; i++) {
            float sx = x + r + inner * i / segments;
            float ex = x + r + inner * (i + 1) / segments;
            Render2D.rectH(ctx, sx - 0.3F, y, ex - sx + 0.6F, h, 0,
                    Color.HSBtoRGB((float) i / segments, 0.75F, 1.0F),
                    Color.HSBtoRGB((float) (i + 1) / segments, 0.75F, 1.0F));
        }
    }

    /** Animated toggle switch. {@code progress} goes 0 (off) .. 1 (on). */
    public static void toggleSwitch(DrawContext ctx, float x, float y, float w, float h, float progress, int accent) {
        progress = Anim.clamp01(progress);
        float r = h / 2.0F;
        float e = Anim.easeInOutCubic(progress);
        Render2D.rect(ctx, x, y, w, h, r, 0xFF262630);
        if (progress > 0.01F) {
            Render2D.shadow(ctx, x, y, w, h, r, 5, ColorUtil.withAlpha(accent, Math.round(0x50 * progress)));
            Render2D.rectH(ctx, x, y, w, h, r, ColorUtil.mulAlpha(accent | 0xFF000000, progress),
                    ColorUtil.mulAlpha(ColorUtil.brighter(accent | 0xFF000000, 0.25F), progress));
        }
        Render2D.outline(ctx, x, y, w, h, r, 0.5F, ColorUtil.withAlpha(0xFFFFFF, Math.round(0x14 * (1 - progress))));
        float knobR = r - 2.0F;
        float kx = x + r + (w - 2 * r) * e;
        Render2D.shadow(ctx, kx - knobR, y + r - knobR + 0.5F, knobR * 2, knobR * 2, knobR, 2.5F, 0x60000000);
        Render2D.circle(ctx, kx, y + r, knobR, ColorUtil.lerp(0xFFA0A0AE, 0xFFFFFFFF, progress));
    }

    /** Card with a soft shadow, body and hairline border. */
    public static void modernCard(DrawContext ctx, float x, float y, float w, float h, float radius, int cardColor, int outlineColor, int glowColor) {
        Render2D.shadow(ctx, x, y + 2, w, h, radius, 12, 0x55000000);
        if (ColorUtil.alpha(glowColor) > 0) {
            Render2D.shadow(ctx, x, y, w, h, radius, 10, glowColor);
        }
        Render2D.rect(ctx, x, y, w, h, radius, cardColor);
        if (ColorUtil.alpha(outlineColor) > 0) {
            Render2D.outline(ctx, x, y, w, h, radius, 0.5F, outlineColor);
        }
    }

    // ------------------------------------------------------------------ legacy int API

    public static void drawRoundedRect(DrawContext context, int x, int y, int w, int h, int radius, int color) {
        Render2D.rect(context, x, y, w, h, radius, color);
    }

    public static void drawRoundedOutline(DrawContext context, int x, int y, int w, int h, int radius, int outlineColor) {
        Render2D.outline(context, x, y, w, h, radius, 0.5F, outlineColor);
    }

    public static void drawCircle(DrawContext context, int centerX, int centerY, int radius, int color) {
        Render2D.circle(context, centerX, centerY, radius, color);
    }

    public static void drawPillSwitch(DrawContext context, int x, int y, int w, int h, boolean enabled) {
        toggleSwitch(context, x, y, w, h, enabled ? 1.0F : 0.0F, Theme.accent(0));
    }

    public static void drawGlow(DrawContext context, int x, int y, int w, int h, int cornerRadius, int glowRadius, int glowColor) {
        Render2D.shadow(context, x, y, w, h, cornerRadius, glowRadius, glowColor);
    }

    public static void drawGlow(DrawContext context, int x, int y, int w, int h, int radius, int glowColor) {
        Render2D.shadow(context, x, y, w, h, 6, radius, glowColor);
    }

    public static void drawModernCard(DrawContext context, int x, int y, int w, int h, int cornerRadius, int cardColor, int outlineColor, int glowColor) {
        modernCard(context, x, y, w, h, cornerRadius, cardColor, outlineColor, glowColor);
    }

    public static void drawHorizontalGradient(DrawContext context, int x, int y, int w, int h, int color1, int color2) {
        Render2D.rectH(context, x, y, w, h, 0, color1, color2);
    }

    // ------------------------------------------------------------------ palettes

    public static int getNightColor(int index, float speed) {
        double time = (System.currentTimeMillis() / 1000.0) * speed + (index * 0.15);
        return ColorUtil.lerp(0xFF9D4EDD, 0xFF00C8FF, (float) ((Math.sin(time) + 1.0) / 2.0));
    }

    public static int getSunsetColor(int index, float speed) {
        double time = (System.currentTimeMillis() / 1000.0) * speed + (index * 0.15);
        return ColorUtil.lerp(0xFFFF4D8D, 0xFFFFA14D, (float) ((Math.sin(time) + 1.0) / 2.0));
    }

    public static void applyBlur(DrawContext context) {
        if (context != null) {
            try {
                context.applyBlur();
            } catch (Throwable ignored) {
            }
        }
    }
}
