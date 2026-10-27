package me.night.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.Arrays;

/**
 * A batch of quads for the UI pipeline. Vertices are transformed by the current matrix when added,
 * and the whole batch is submitted to the GUI renderer as one element.
 */
final class UiMesh {
    private static final int F = 7; // x, y, u, v, nx, ny, nz
    private static final int I = 5; // color, uv1x, uv1y, uv2x, uv2y

    private float[] floats = new float[F * 64];
    private int[] ints = new int[I * 64];
    private int vertices = 0;

    private final Matrix3x2f pose = new Matrix3x2f();
    private final Vector2f tmp = new Vector2f();
    private float minX, minY, maxX, maxY;

    void begin(DrawContext ctx) {
        this.vertices = 0;
        this.pose.set(ctx.getMatrices());
        this.minX = this.minY = Float.MAX_VALUE;
        this.maxX = this.maxY = -Float.MAX_VALUE;
    }

    boolean isEmpty() {
        return this.vertices == 0;
    }

    void vertex(float x, float y, int color, float u, float v, int uv1x, int uv1y, int uv2x, int uv2y, float nx, float ny, float nz) {
        if ((this.vertices + 1) * F > this.floats.length) {
            this.floats = Arrays.copyOf(this.floats, this.floats.length * 2);
            this.ints = Arrays.copyOf(this.ints, this.ints.length * 2);
        }
        this.pose.transformPosition(x, y, this.tmp);
        int f = this.vertices * F;
        int i = this.vertices * I;
        this.floats[f] = this.tmp.x;
        this.floats[f + 1] = this.tmp.y;
        this.floats[f + 2] = u;
        this.floats[f + 3] = v;
        this.floats[f + 4] = nx;
        this.floats[f + 5] = ny;
        this.floats[f + 6] = nz;
        this.ints[i] = color;
        this.ints[i + 1] = uv1x;
        this.ints[i + 2] = uv1y;
        this.ints[i + 3] = uv2x;
        this.ints[i + 4] = uv2y;
        this.vertices++;
        if (this.tmp.x < this.minX) this.minX = this.tmp.x;
        if (this.tmp.y < this.minY) this.minY = this.tmp.y;
        if (this.tmp.x > this.maxX) this.maxX = this.tmp.x;
        if (this.tmp.y > this.maxY) this.maxY = this.tmp.y;
    }

    /** Hands the batch to the GUI renderer. Elements fully outside the scissor are dropped. */
    void submit(DrawContext ctx, TextureSetup textureSetup) {
        if (this.vertices == 0) return;
        int x0 = (int) Math.floor(this.minX);
        int y0 = (int) Math.floor(this.minY);
        int x1 = (int) Math.ceil(this.maxX);
        int y1 = (int) Math.ceil(this.maxY);
        ScreenRect bounds = new ScreenRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
        ScreenRect scissor = ctx.scissorStack.peekLast();
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) return;
        }
        ctx.state.addSimpleElement(new Element(UiPipeline.UI, textureSetup, scissor, bounds,
                Arrays.copyOf(this.floats, this.vertices * F), Arrays.copyOf(this.ints, this.vertices * I), this.vertices));
    }

    private record Element(RenderPipeline pipeline, TextureSetup textureSetup, ScreenRect scissorArea, ScreenRect bounds,
                           float[] floats, int[] ints, int count) implements SimpleGuiElementRenderState {
        @Override
        public void setupVertices(VertexConsumer vc) {
            for (int v = 0; v < this.count; v++) {
                int f = v * F;
                int i = v * I;
                vc.vertex(this.floats[f], this.floats[f + 1], 0.0F)
                        .color(this.ints[i])
                        .texture(this.floats[f + 2], this.floats[f + 3])
                        .overlay(this.ints[i + 1], this.ints[i + 2])
                        .light(this.ints[i + 3], this.ints[i + 4])
                        .normal(this.floats[f + 4], this.floats[f + 5], this.floats[f + 6]);
            }
        }
    }
}
