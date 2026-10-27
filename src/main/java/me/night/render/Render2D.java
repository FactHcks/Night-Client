package me.night.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;

/**
 * GPU UI drawing. Every shape is one quad evaluated by a signed-distance shader, so corners,
 * outlines and shadows are smooth at any size or scale and cost almost nothing.
 * <p>
 * All coordinates are GUI units. Colours are ARGB. Everything respects {@link #setAlpha}.
 */
public final class Render2D {
    private static final int MODE_FILL = 0;
    private static final int MODE_OUTLINE = 1;
    private static final int MODE_SHADOW = 2;
    private static final int MODE_IMAGE = 4;
    private static final int MODE_AURORA = 5;
    private static final int MODE_BACKDROP = 6;

    private static final float PAD = 1.0F; // must match PAD in ui.fsh

    private static final UiMesh MESH = new UiMesh();
    private static float alpha = 1.0F;

    private Render2D() {
    }

    static UiMesh mesh() {
        return MESH;
    }

    // ------------------------------------------------------------------ global alpha

    public static void setAlpha(float a) {
        alpha = Math.max(0.0F, Math.min(1.0F, a));
    }

    public static float alpha() {
        return alpha;
    }

    public static void resetAlpha() {
        alpha = 1.0F;
    }

    private static int fade(int color) {
        if (alpha >= 1.0F) return color;
        int a = Math.round((color >>> 24) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------ core quad

    /**
     * Adds one shape quad. Colours are per corner: top-left, bottom-left, bottom-right, top-right.
     */
    private static void shape(DrawContext ctx, TextureSetup tex, float x, float y, float w, float h, float radius,
                              int mode, float param, float pad, int tl, int bl, int br, int tr,
                              float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        if (w <= 0 || h <= 0) return;
        tl = fade(tl);
        bl = fade(bl);
        br = fade(br);
        tr = fade(tr);
        if ((tl | bl | br | tr) >>> 24 == 0) return;

        float hw = Math.min(2040.0F, w / 2.0F + pad);
        float hh = Math.min(2040.0F, h / 2.0F + pad);
        float cx = x + w / 2.0F;
        float cy = y + h / 2.0F;
        int lx = Math.round(hw * 16.0F);
        int ly = Math.round(hh * 16.0F);
        int r16 = Math.round(Math.max(0.0F, Math.min(radius, Math.min(w, h) / 2.0F)) * 16.0F);
        int p16 = Math.round(Math.max(0.0F, Math.min(255.0F, param)) * 16.0F);
        int uv2y = mode * 4096 + p16;

        UiMesh m = MESH;
        m.begin(ctx);
        m.vertex(cx - hw, cy - hh, tl, u0, v0, -lx, -ly, r16, uv2y, nx, ny, nz);
        m.vertex(cx - hw, cy + hh, bl, u0, v1, -lx, ly, r16, uv2y, nx, ny, nz);
        m.vertex(cx + hw, cy + hh, br, u1, v1, lx, ly, r16, uv2y, nx, ny, nz);
        m.vertex(cx + hw, cy - hh, tr, u1, v0, lx, -ly, r16, uv2y, nx, ny, nz);
        m.submit(ctx, tex);
    }

    private static com.mojang.blaze3d.textures.GpuTextureView cachedAtlas;
    private static TextureSetup cachedSetup;

    /** The texture setup shared by shapes and text (the glyph atlas). */
    static TextureSetup uiSetup() {
        var atlas = UiFont.atlasView();
        if (cachedSetup == null || atlas != cachedAtlas) {
            cachedSetup = TextureSetup.of(atlas, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            cachedAtlas = atlas;
        }
        return cachedSetup;
    }

    private static TextureSetup atlas() {
        return uiSetup();
    }

    // ------------------------------------------------------------------ fills

    public static void rect(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
        shape(ctx, atlas(), x, y, w, h, radius, MODE_FILL, 0, PAD, color, color, color, color, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Left-to-right gradient. */
    public static void rectH(DrawContext ctx, float x, float y, float w, float h, float radius, int left, int right) {
        shape(ctx, atlas(), x, y, w, h, radius, MODE_FILL, 0, PAD, left, left, right, right, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Top-to-bottom gradient. */
    public static void rectV(DrawContext ctx, float x, float y, float w, float h, float radius, int top, int bottom) {
        shape(ctx, atlas(), x, y, w, h, radius, MODE_FILL, 0, PAD, top, bottom, bottom, top, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Four-corner gradient: top-left, top-right, bottom-right, bottom-left. */
    public static void rect4(DrawContext ctx, float x, float y, float w, float h, float radius, int tl, int tr, int br, int bl) {
        shape(ctx, atlas(), x, y, w, h, radius, MODE_FILL, 0, PAD, tl, bl, br, tr, 0, 0, 0, 0, 0, 0, 0);
    }

    public static void circle(DrawContext ctx, float cx, float cy, float radius, int color) {
        rect(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    // ------------------------------------------------------------------ outlines

    /** Border drawn just inside the shape's edge. */
    public static void outline(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
        shape(ctx, atlas(), x, y, w, h, radius, MODE_OUTLINE, thickness, PAD, color, color, color, color, 0, 0, 0, 0, 0, 0, 0);
    }

    public static void outlineH(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int left, int right) {
        shape(ctx, atlas(), x, y, w, h, radius, MODE_OUTLINE, thickness, PAD, left, left, right, right, 0, 0, 0, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ shadows

    /** Soft gaussian-style shadow / glow around a rounded rect. */
    public static void shadow(DrawContext ctx, float x, float y, float w, float h, float radius, float blur, int color) {
        if (blur <= 0) return;
        shape(ctx, atlas(), x, y, w, h, radius, MODE_SHADOW, blur, blur, color, color, color, color, 0, 0, 0, 0, 0, 0, 0);
    }

    public static void shadowH(DrawContext ctx, float x, float y, float w, float h, float radius, float blur, int left, int right) {
        if (blur <= 0) return;
        shape(ctx, atlas(), x, y, w, h, radius, MODE_SHADOW, blur, blur, left, left, right, right, 0, 0, 0, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ images

    /** Draws part of a texture inside a rounded rect. */
    public static void image(DrawContext ctx, Identifier texture, float x, float y, float w, float h, float radius,
                             float u0, float v0, float u1, float v1, int tint, boolean pixelated) {
        AbstractTexture t = MinecraftClient.getInstance().getTextureManager().getTexture(texture);
        if (t == null || t.getGlTextureView() == null) return;
        TextureSetup setup = TextureSetup.of(t.getGlTextureView(),
                RenderSystem.getSamplerCache().get(pixelated ? FilterMode.NEAREST : FilterMode.LINEAR));
        // stretch the UVs over the padding so the image lines up with the shape
        float du = (u1 - u0) / w * PAD;
        float dv = (v1 - v0) / h * PAD;
        shape(ctx, setup, x, y, w, h, radius, MODE_IMAGE, 0, PAD, tint, tint, tint, tint,
                u0 - du, v0 - dv, u1 + du, v1 + dv, 0, 0, 0);
    }

    /** A player's face (with hat layer) as a rounded square. */
    public static void head(DrawContext ctx, SkinTextures skin, float x, float y, float size, float radius, int tint) {
        Identifier tex = skin.body().texturePath();
        image(ctx, tex, x, y, size, size, radius, 8 / 64F, 8 / 64F, 16 / 64F, 16 / 64F, tint, true);
        image(ctx, tex, x - size * 0.04F, y - size * 0.04F, size * 1.08F, size * 1.08F, radius,
                40 / 64F, 8 / 64F, 48 / 64F, 16 / 64F, tint, true);
    }

    // ------------------------------------------------------------------ lines

    /** Line with round caps; colour fades from c1 to c2 along it. */
    public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, float thickness, int c1, int c2) {
        lineShape(ctx, x1, y1, x2, y2, thickness, MODE_FILL, 0, PAD, c1, c2);
    }

    /** Soft glow around a line. */
    public static void lineGlow(DrawContext ctx, float x1, float y1, float x2, float y2, float thickness, float blur, int color) {
        if (blur <= 0) return;
        lineShape(ctx, x1, y1, x2, y2, thickness, MODE_SHADOW, blur, blur, color, color);
    }

    private static void lineShape(DrawContext ctx, float x1, float y1, float x2, float y2, float thickness,
                                  int mode, float param, float pad, int c1, int c2) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01F || thickness <= 0) return;
        c1 = fade(c1);
        c2 = fade(c2);
        if ((c1 | c2) >>> 24 == 0) return;
        float ux = dx / len, uy = dy / len;   // along the line
        float vx = -uy, vy = ux;              // across it
        float ht = thickness / 2.0F;
        float hw = Math.min(2040.0F, len / 2.0F + ht + pad);
        float hh = Math.min(2040.0F, ht + pad);
        float cx = (x1 + x2) / 2.0F, cy = (y1 + y2) / 2.0F;
        int lx = Math.round(hw * 16.0F), ly = Math.round(hh * 16.0F);
        int r16 = Math.round(ht * 16.0F);
        int uv2y = mode * 4096 + Math.round(Math.max(0, Math.min(255, param)) * 16.0F);

        UiMesh m = MESH;
        m.begin(ctx);
        m.vertex(cx - ux * hw - vx * hh, cy - uy * hw - vy * hh, c1, 0, 0, -lx, -ly, r16, uv2y, 0, 0, 0);
        m.vertex(cx - ux * hw + vx * hh, cy - uy * hw + vy * hh, c1, 0, 0, -lx, ly, r16, uv2y, 0, 0, 0);
        m.vertex(cx + ux * hw + vx * hh, cy + uy * hw + vy * hh, c2, 0, 0, lx, ly, r16, uv2y, 0, 0, 0);
        m.vertex(cx + ux * hw - vx * hh, cy + uy * hw - vy * hh, c2, 0, 0, lx, -ly, r16, uv2y, 0, 0, 0);
        m.submit(ctx, atlas());
    }

    // ------------------------------------------------------------------ glass

    /** Blurred world behind a rounded rect. Returns false if blur isn't available. */
    public static boolean backdrop(DrawContext ctx, float x, float y, float w, float h, float radius) {
        TextureSetup blurred = Blur.texture();
        if (blurred == null) return false;
        int white = 0xFFFFFFFF;
        shape(ctx, blurred, x, y, w, h, radius, MODE_BACKDROP, 0, PAD, white, white, white, white, 0, 0, 0, 0, 0, 0, 0);
        return true;
    }

    /**
     * Frosted glass panel: blurred background with a tint on top. Without blur the tint is made
     * more opaque so text stays readable.
     */
    public static void glass(DrawContext ctx, float x, float y, float w, float h, float radius, int tint, boolean blur) {
        boolean blurred = blur && backdrop(ctx, x, y, w, h, radius);
        if (!blurred) {
            int a = Math.max(tint >>> 24, 0xD0);
            tint = (a << 24) | (tint & 0x00FFFFFF);
        }
        rect(ctx, x, y, w, h, radius, tint);
    }

    // ------------------------------------------------------------------ backgrounds

    /** Animated aurora sky (used by the main menu). */
    public static void aurora(DrawContext ctx, float x, float y, float w, float h, float timeSeconds, int colorA, int colorB) {
        aurora(ctx, x, y, w, h, 0, timeSeconds, colorA, colorB);
    }

    /** Aurora clipped to a rounded rect (for previews). */
    public static void aurora(DrawContext ctx, float x, float y, float w, float h, float radius, float timeSeconds, int colorA, int colorB) {
        float nx = ((colorB >> 16) & 255) / 255F * 2 - 1;
        float ny = ((colorB >> 8) & 255) / 255F * 2 - 1;
        float nz = (colorB & 255) / 255F * 2 - 1;
        int a = colorA | 0xFF000000;
        shape(ctx, atlas(), x, y, w, h, radius, MODE_AURORA, 0, 0, a, a, a, a, timeSeconds, 0, timeSeconds, 0, nx, ny, nz);
    }

    // ------------------------------------------------------------------ text shortcuts

    public static float text(DrawContext ctx, UiFont font, String text, float x, float y, int color) {
        return font.draw(ctx, text, x, y, color, false);
    }
}
