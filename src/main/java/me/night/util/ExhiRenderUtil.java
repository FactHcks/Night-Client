package me.night.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class ExhiRenderUtil {

    public static void drawSkeetBox(DrawContext context, int x, int y, int w, int h) {
        // Authentic Skeet triple-bevel border with zero heavy loops
        context.fill(x, y, x + w, y + h, 0xFF080808);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF3C3C3C);
        context.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF282828);
        context.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF141414);
    }

    public static void drawHorizontalGradient(DrawContext context, int x1, int y1, int x2, int y2, int color1, int color2) {
        int width = x2 - x1;
        if (width <= 0) return;
        int steps = Math.min(width, 24);
        float stepW = (float) width / steps;

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        for (int i = 0; i < steps; i++) {
            float ratio = (float) i / steps;
            int a = (int) (a1 + (a2 - a1) * ratio);
            int r = (int) (r1 + (r2 - r1) * ratio);
            int g = (int) (g1 + (g2 - g1) * ratio);
            int b = (int) (b1 + (b2 - b1) * ratio);
            int col = (a << 24) | (r << 16) | (g << 8) | b;

            int sx = (int) (x1 + i * stepW);
            int ex = (int) Math.min(x2, x1 + (i + 1) * stepW + 1);
            context.fill(sx, y1, ex, y2, col);
        }
    }

    public static void drawSkeetRainbowBar(DrawContext context, int x, int y, int w, int h) {
        int half = w / 2;
        // Authentic Exhibition top stripe: Cyan (#37B1DA) -> Magenta (#CC4DC6) -> Lime (#CCE335)
        drawHorizontalGradient(context, x, y, x + half, y + h, 0xFF37B1DA, 0xFFCC4DC6);
        drawHorizontalGradient(context, x + half, y, x + w, y + h, 0xFFCC4DC6, 0xFFCCE335);
    }

    public static void drawGroupBox(DrawContext context, int x, int y, int w, int h, String title) {
        // Outer border
        context.fill(x, y, x + w, y + h, 0xFF0D0D0D);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2A2A2A);
        context.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF141414);

        if (title != null && !title.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int titleW = mc.textRenderer.getWidth(title);
            // Header cutout into groupbox border
            context.fill(x + 8, y, x + 12 + titleW, y + 2, 0xFF141414);
            context.drawTextWithShadow(mc.textRenderer, title, x + 10, y - 4, 0xFFD8D8D8);
        }
    }

    public static void drawOutline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
