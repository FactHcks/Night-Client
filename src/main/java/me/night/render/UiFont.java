package me.night.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import me.night.Night;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

import static org.lwjgl.stb.STBTruetype.*;

/**
 * Anti-aliased TrueType text. Glyphs are rasterised by STB at the window's real pixel size
 * (GUI size x GUI scale) into one shared atlas, so text stays sharp at every GUI scale.
 * <p>
 * Coordinates are GUI units. {@link #draw} takes the y of the top of capital letters,
 * so centring text in a box is {@code y = boxY + (boxH - capHeight()) / 2}.
 */
public final class UiFont {
    // ------------------------------------------------------------------ the fonts

    public static final UiFont SMALL = new UiFont("poppins_medium.ttf", 8.5F, false);
    public static final UiFont REGULAR = new UiFont("poppins.ttf", 10.0F, false);
    public static final UiFont MEDIUM = new UiFont("poppins_medium.ttf", 10.0F, false);
    public static final UiFont BOLD = new UiFont("poppins_semibold.ttf", 10.5F, false);
    public static final UiFont TITLE = new UiFont("poppins_semibold.ttf", 13.0F, true);
    public static final UiFont LARGE = new UiFont("poppins_semibold.ttf", 20.0F, true);
    public static final UiFont HUGE = new UiFont("poppins_semibold.ttf", 48.0F, true);

    private static final List<UiFont> ALL = List.of(SMALL, REGULAR, MEDIUM, BOLD, TITLE, LARGE, HUGE);
    private static final Map<String, Face> FACES = new HashMap<>();

    private static final int[] CODE_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    // ------------------------------------------------------------------ shared atlas

    private static int atlasScale = -1;
    private static int atlasSize = 0;
    private static NativeImageBackedTexture atlasTexture;
    private static TextureSetup atlasSetup;

    private static final STBTTAlignedQuad QUAD = STBTTAlignedQuad.malloc();
    private static final FloatBuffer PEN_X = MemoryUtil.memAllocFloat(1);
    private static final FloatBuffer PEN_Y = MemoryUtil.memAllocFloat(1);

    private final String file;
    public final float size;
    private final int first = 32;
    private final int count;

    private Face face;
    private STBTTPackedchar.Buffer chars;
    private float pxScale;      // stb scale factor for this font at the atlas pixel size
    private float ascent;       // GUI units
    private float descent;      // GUI units (negative)
    private float capHeight;    // GUI units
    private final Map<String, Float> widthCache = new HashMap<>();

    private UiFont(String file, float size, boolean asciiOnly) {
        this.file = file;
        this.size = size;
        this.count = asciiOnly ? 95 : 224;
    }

    private static final class Face {
        final ByteBuffer data;
        final STBTTFontinfo info;

        Face(ByteBuffer data, STBTTFontinfo info) {
            this.data = data;
            this.info = info;
        }
    }

    private static Face face(String file) {
        return FACES.computeIfAbsent(file, f -> {
            try (InputStream in = UiFont.class.getResourceAsStream("/assets/night/font/" + f)) {
                if (in == null) throw new IllegalStateException("Missing font " + f);
                byte[] bytes = in.readAllBytes();
                ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
                buf.put(bytes).flip();
                STBTTFontinfo info = STBTTFontinfo.malloc();
                if (!stbtt_InitFont(info, buf)) throw new IllegalStateException("Bad font " + f);
                return new Face(buf, info);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** Rebuilds the glyph atlas when the GUI scale changes. Render thread only. */
    static TextureSetup ensureAtlas() {
        int scale = Math.max(1, MinecraftClient.getInstance().getWindow().getScaleFactor());
        if (scale == atlasScale && atlasSetup != null) return atlasSetup;
        build(scale);
        return atlasSetup;
    }

    /** The glyph atlas texture (building it if needed). */
    static com.mojang.blaze3d.textures.GpuTextureView atlasView() {
        ensureAtlas();
        return atlasTexture.getGlTextureView();
    }

    private static void build(int scale) {
        long area = 0;
        for (UiFont f : ALL) {
            float px = f.size * scale;
            area += (long) (px * px * 0.55F * f.count * (px < 32 ? 2 : 1));
        }
        int size = 256;
        while ((long) size * size < area * 1.3) size *= 2;

        ByteBuffer bitmap = null;
        while (size <= 8192) {
            bitmap = MemoryUtil.memCalloc(size * size);
            if (pack(bitmap, size, scale)) break;
            MemoryUtil.memFree(bitmap);
            bitmap = null;
            size *= 2;
        }
        if (bitmap == null) throw new IllegalStateException("Font atlas too large");

        NativeImage image = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            int row = y * size;
            for (int x = 0; x < size; x++) {
                int a = bitmap.get(row + x) & 0xFF;
                if (a != 0) image.setColorArgb(x, y, (a << 24) | 0xFFFFFF);
            }
        }
        MemoryUtil.memFree(bitmap);

        if (atlasTexture != null) atlasTexture.close();
        atlasTexture = new NativeImageBackedTexture(() -> "night-font-atlas", image);
        atlasTexture.upload();
        atlasSetup = TextureSetup.of(atlasTexture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
        atlasScale = scale;
        atlasSize = size;
        Night.LOGGER.info("Built font atlas {}x{} for GUI scale {}", size, size, scale);
    }

    private static boolean pack(ByteBuffer bitmap, int size, int scale) {
        try (STBTTPackContext pc = STBTTPackContext.malloc()) {
            if (!stbtt_PackBegin(pc, bitmap, size, size, 0, 1, MemoryUtil.NULL)) return false;
            boolean ok = true;
            for (UiFont f : ALL) {
                f.face = face(f.file);
                float px = f.size * scale;
                stbtt_PackSetOversampling(pc, px < 32 ? 2 : 1, 1);
                if (f.chars != null) f.chars.free();
                f.chars = STBTTPackedchar.malloc(f.count);
                if (!stbtt_PackFontRange(pc, f.face.data, 0, px, f.first, f.chars)) {
                    ok = false;
                    break;
                }
                f.computeMetrics(scale);
            }
            stbtt_PackEnd(pc);
            return ok;
        }
    }

    private void computeMetrics(int scale) {
        this.pxScale = stbtt_ScaleForPixelHeight(this.face.info, this.size * scale);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer a = stack.mallocInt(1), d = stack.mallocInt(1), g = stack.mallocInt(1);
            stbtt_GetFontVMetrics(this.face.info, a, d, g);
            this.ascent = a.get(0) * this.pxScale / scale;
            this.descent = d.get(0) * this.pxScale / scale;
            IntBuffer x0 = stack.mallocInt(1), y0 = stack.mallocInt(1), x1 = stack.mallocInt(1), y1 = stack.mallocInt(1);
            stbtt_GetCodepointBox(this.face.info, 'H', x0, y0, x1, y1);
            this.capHeight = y1.get(0) * this.pxScale / scale;
        }
        this.widthCache.clear();
    }

    // ------------------------------------------------------------------ metrics

    public float capHeight() {
        ensureAtlas();
        return this.capHeight;
    }

    public float ascent() {
        ensureAtlas();
        return this.ascent;
    }

    public float descent() {
        ensureAtlas();
        return this.descent;
    }

    /** y for {@link #draw} so capitals sit centred in a box. */
    public float centerY(float boxY, float boxH) {
        return boxY + (boxH - capHeight()) / 2.0F;
    }

    private int glyphIndex(int c) {
        int i = c - this.first;
        if (i < 0 || i >= this.count || (c >= 127 && c < 160)) return '?' - this.first;
        return i;
    }

    public float width(String text) {
        if (text == null || text.isEmpty()) return 0;
        ensureAtlas();
        Float cached = this.widthCache.get(text);
        if (cached != null) return cached;
        float scale = atlasScale;
        float w = 0;
        int prev = -1;
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == '§' && k + 1 < text.length()) {
                k++;
                continue;
            }
            if (prev >= 0) w += stbtt_GetCodepointKernAdvance(this.face.info, prev, c) * this.pxScale;
            w += this.chars.get(glyphIndex(c)).xadvance();
            prev = c;
        }
        float result = w / scale;
        if (this.widthCache.size() > 4096) this.widthCache.clear();
        this.widthCache.put(text, result);
        return result;
    }

    /** Shortens text with "..." so it fits in maxWidth. */
    public String trim(String text, float maxWidth) {
        if (text == null || width(text) <= maxWidth) return text;
        float dots = width("...");
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (width(sb.toString() + c) + dots > maxWidth) break;
            sb.append(c);
        }
        return sb.toString().trim() + "...";
    }

    // ------------------------------------------------------------------ drawing

    /** Draws text; y is the top of capital letters. Supports § colour codes. Returns the end x. */
    public float draw(DrawContext ctx, String text, float x, float y, int color, boolean shadow) {
        return drawInternal(ctx, text, x, y, color, null, shadow);
    }

    /** Draws text with one colour per character, e.g. a gradient. */
    public float drawGradient(DrawContext ctx, String text, float x, float y, IntUnaryOperator colorAt, boolean shadow) {
        return drawInternal(ctx, text, x, y, 0, colorAt, shadow);
    }

    /** Plain text and per-character colours of a Minecraft Text (styles and \u00a7 codes). */
    private static String flatten(net.minecraft.text.Text text, int defaultColor, it.unimi.dsi.fastutil.ints.IntArrayList colors) {
        StringBuilder sb = new StringBuilder();
        text.visit((style, str) -> {
            int base = style.getColor() != null ? (0xFF000000 | style.getColor().getRgb()) : defaultColor;
            int current = base;
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c == '\u00a7' && i + 1 < str.length()) {
                    char code = Character.toLowerCase(str.charAt(++i));
                    int ci = "0123456789abcdef".indexOf(code);
                    if (ci >= 0) current = 0xFF000000 | CODE_COLORS[ci];
                    else if (code == 'r') current = base;
                    continue;
                }
                sb.append(c);
                colors.add(current);
            }
            return java.util.Optional.empty();
        }, net.minecraft.text.Style.EMPTY);
        return sb.toString();
    }

    /** Draws a Minecraft Text keeping its colours. Returns the end x. */
    public float drawText(DrawContext ctx, net.minecraft.text.Text text, float x, float y, int defaultColor, boolean shadow) {
        it.unimi.dsi.fastutil.ints.IntArrayList colors = new it.unimi.dsi.fastutil.ints.IntArrayList();
        String plain = flatten(text, defaultColor, colors);
        int alpha = defaultColor & 0xFF000000;
        return drawGradient(ctx, plain, x, y, i -> i < colors.size() ? (colors.getInt(i) & 0x00FFFFFF) | (alpha == 0 ? 0xFF000000 : alpha) : defaultColor, shadow);
    }

    public float width(net.minecraft.text.Text text) {
        return width(flatten(text, 0xFFFFFFFF, new it.unimi.dsi.fastutil.ints.IntArrayList()));
    }

    public void drawCentered(DrawContext ctx, String text, float cx, float y, int color, boolean shadow) {
        draw(ctx, text, cx - width(text) / 2.0F, y, color, shadow);
    }

    private float drawInternal(DrawContext ctx, String text, float x, float y, int color, IntUnaryOperator colorAt, boolean shadow) {
        if (text == null || text.isEmpty()) return x;
        ensureAtlas();
        TextureSetup setup = Render2D.uiSetup();
        float alphaMul = Render2D.alpha();
        if (alphaMul <= 0.004F) return x + width(text);

        UiMesh mesh = Render2D.mesh();
        mesh.begin(ctx);
        float endX = x;
        if (shadow) {
            float off = Math.max(0.5F, 1.0F / atlasScale);
            emit(mesh, text, x + off, y + off, color, colorAt, true, alphaMul);
        }
        endX = emit(mesh, text, x, y, color, colorAt, false, alphaMul);
        mesh.submit(ctx, setup);
        return endX;
    }

    private float emit(UiMesh mesh, String text, float x, float y, int color, IntUnaryOperator colorAt, boolean shadow, float alphaMul) {
        float scale = atlasScale;
        int current = color;
        PEN_X.put(0, Math.round(x * scale));
        PEN_Y.put(0, Math.round((y + this.capHeight) * scale));
        int prev = -1;
        int index = 0;
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == '§' && k + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++k));
                int ci = "0123456789abcdef".indexOf(code);
                if (ci >= 0) current = (color & 0xFF000000) | CODE_COLORS[ci];
                else if (code == 'r') current = color;
                continue;
            }
            if (prev >= 0) PEN_X.put(0, PEN_X.get(0) + stbtt_GetCodepointKernAdvance(this.face.info, prev, c) * this.pxScale);
            prev = c;
            stbtt_GetPackedQuad(this.chars, atlasSize, atlasSize, glyphIndex(c), PEN_X, PEN_Y, QUAD, false);
            int col = colorAt != null ? colorAt.applyAsInt(index) : current;
            index++;
            if (c == ' ') continue;
            int srcA = col >>> 24;
            if (srcA == 0) srcA = 255; // colours given without alpha are opaque
            int a = Math.round(srcA * alphaMul);
            if (shadow) {
                col = ((int) (a * 0.45F) << 24);
            } else {
                col = (a << 24) | (col & 0xFFFFFF);
            }
            float gx0 = QUAD.x0() / scale, gy0 = QUAD.y0() / scale, gx1 = QUAD.x1() / scale, gy1 = QUAD.y1() / scale;
            int uv2y = 3 * 4096;
            mesh.vertex(gx0, gy0, col, QUAD.s0(), QUAD.t0(), 0, 0, 0, uv2y, 0, 0, 0);
            mesh.vertex(gx0, gy1, col, QUAD.s0(), QUAD.t1(), 0, 0, 0, uv2y, 0, 0, 0);
            mesh.vertex(gx1, gy1, col, QUAD.s1(), QUAD.t1(), 0, 0, 0, uv2y, 0, 0, 0);
            mesh.vertex(gx1, gy0, col, QUAD.s1(), QUAD.t0(), 0, 0, 0, uv2y, 0, 0, 0);
        }
        return PEN_X.get(0) / scale;
    }
}
