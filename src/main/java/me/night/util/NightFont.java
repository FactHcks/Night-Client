package me.night.util;

import me.night.render.UiFont;
import net.minecraft.client.gui.DrawContext;

import java.util.function.IntUnaryOperator;

/**
 * Text helpers over {@link UiFont}. For compatibility with older code, {@code y} here is the
 * vanilla-style text top: the baseline sits 7 units below it. Use {@link #centerY} to centre text in a box.
 */
public class NightFont {

    public enum Type {
        SMALL(UiFont.SMALL),
        REGULAR(UiFont.REGULAR),
        MEDIUM(UiFont.MEDIUM),
        BOLD(UiFont.BOLD),
        TITLE(UiFont.TITLE),
        LARGE(UiFont.LARGE),
        HUGE(UiFont.HUGE);

        public final UiFont font;

        Type(UiFont font) {
            this.font = font;
        }
    }

    private static final float BASELINE = 7.0F;

    private static float capTop(Type type, float y) {
        return y + BASELINE - type.font.capHeight();
    }

    public static void draw(DrawContext ctx, Type type, String text, float x, float y, int color, boolean shadow) {
        type.font.draw(ctx, text, x, capTop(type, y), color, shadow);
    }

    public static int width(Type type, String text) {
        return (int) Math.ceil(type.font.width(text));
    }

    public static float widthF(Type type, String text) {
        return type.font.width(text);
    }

    /** y to pass to draw() so the text is vertically centred inside a box. */
    public static float centerY(Type type, float boxY, float boxH) {
        return boxY + (boxH - type.font.capHeight()) / 2.0F - BASELINE + type.font.capHeight();
    }

    public static void drawGradient(DrawContext ctx, Type type, String text, float x, float y, IntUnaryOperator colorAt, boolean shadow) {
        type.font.drawGradient(ctx, text, x, capTop(type, y), colorAt, shadow);
    }

    public static String trim(Type type, String text, int maxWidth) {
        return type.font.trim(text, maxWidth);
    }

    // ------------------------------------------------------------------ legacy API

    public static void draw(DrawContext context, String text, int x, int y, int color) {
        draw(context, Type.REGULAR, text, x, y, color, true);
    }

    public static void drawBold(DrawContext context, String text, int x, int y, int color) {
        draw(context, Type.BOLD, text, x, y, color, true);
    }

    public static void drawTitle(DrawContext context, String text, int x, int y, int color) {
        draw(context, Type.TITLE, text, x, y, color, true);
    }

    public static int getWidth(String text) {
        return width(Type.REGULAR, text);
    }

    public static int getBoldWidth(String text) {
        return width(Type.BOLD, text);
    }

    public static int getTitleWidth(String text) {
        return width(Type.TITLE, text);
    }
}
