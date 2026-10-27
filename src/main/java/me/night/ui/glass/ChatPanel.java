package me.night.ui.glass;

import me.night.Night;
import me.night.module.modules.render.HUD;
import me.night.render.Render2D;
import me.night.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.joml.Vector2f;

/**
 * Replaces the per-line chat backgrounds with one rounded glass panel.
 * The line backgrounds are measured while chat draws, then the panel is drawn
 * behind the text at the start of the next frame.
 */
public final class ChatPanel {
    private static float minX, minY, maxX, maxY;
    private static int maxAlpha;
    private static boolean any;
    private static final float[][] lines = new float[128][];
    private static int lineCount;
    private static final Vector2f tmp = new Vector2f();

    private ChatPanel() {
    }

    public static boolean enabled() {
        return GlassWidgets.enabled();
    }

    /** One chat line background (in the chat's own coordinates). */
    public static void record(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int a = color >>> 24;
        if (a == 0 || lineCount >= lines.length) return;
        ctx.getMatrices().transformPosition(x1, y1, tmp);
        float ax = tmp.x, ay = tmp.y;
        ctx.getMatrices().transformPosition(x2, y2, tmp);
        lines[lineCount++] = new float[]{Math.min(ax, tmp.x), Math.min(ay, tmp.y), Math.max(ax, tmp.x), Math.max(ay, tmp.y), a};
        maxAlpha = Math.max(maxAlpha, a);
    }

    /** Draws the panel measured last frame, then starts measuring again. */
    public static void drawAndReset(DrawContext ctx) {
        // panel covers the lines that are still mostly visible
        any = false;
        for (int i = 0; i < lineCount; i++) {
            float[] l = lines[i];
            if (l[4] < maxAlpha * 0.5F) continue;
            if (!any) {
                minX = l[0];
                minY = l[1];
                maxX = l[2];
                maxY = l[3];
                any = true;
            } else {
                minX = Math.min(minX, l[0]);
                minY = Math.min(minY, l[1]);
                maxX = Math.max(maxX, l[2]);
                maxY = Math.max(maxY, l[3]);
            }
        }
        if (any) {
            HUD hud = Night.INSTANCE.getModuleManager().getModule(HUD.class);
            boolean blur = hud != null && hud.blur.getValue();
            float frac = Math.min(1.0F, maxAlpha / 127.0F);
            Render2D.setAlpha(frac);
            float x = minX - 2, y = minY - 3, w = maxX - minX + 4, h = maxY - minY + 6;
            Render2D.shadow(ctx, x, y + 2, w, h, 7, 10, 0x40000000);
            Render2D.glass(ctx, x, y, w, h, 7, 0x8C0C0C12, blur);
            Render2D.outline(ctx, x, y, w, h, 7, 0.5F, 0x1CFFFFFF);
            Render2D.rect(ctx, x + 2, y + 6, 1.5F, h - 12, 0.75F, Theme.accent(0));
            Render2D.resetAlpha();
        }
        lineCount = 0;
        maxAlpha = 0;
    }
}
