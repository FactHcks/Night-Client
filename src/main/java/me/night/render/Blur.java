package me.night.render;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import me.night.Night;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.memory.ObjectPool;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * Background blur for glass HUD panels.
 * <p>
 * Once per frame, the first time a panel asks for it, the rendered world is shrunk to half size
 * into a private framebuffer and blurred there (three separable box-blur passes, close to a gaussian).
 * Panels then sample that texture behind themselves, so the HUD stays sharp.
 */
public final class Blur {
    private static final Identifier EFFECT = Identifier.of("night", "blur");
    private static final ObjectPool POOL = new ObjectPool(3);

    private static SimpleFramebuffer target;
    private static boolean capturedThisFrame = false;
    private static boolean failed = false;
    private static TextureSetup setup;

    private Blur() {
    }

    /** Called at the start of every HUD frame. */
    public static void newFrame() {
        capturedThisFrame = false;
        POOL.decrementLifespan();
    }

    /** Blurred view if it was captured this frame, else null. */
    static com.mojang.blaze3d.textures.GpuTextureView currentView() {
        return capturedThisFrame && !failed && target != null ? target.getColorAttachmentView() : null;
    }

    /** Captures now if blur will be needed this frame (keeps all UI in one texture group). */
    public static void prepare(boolean needed) {
        if (needed) texture();
    }

    /** The blurred world texture for this frame, or null if blur is unavailable. */
    public static TextureSetup texture() {
        if (failed) return null;
        if (!capturedThisFrame) {
            capturedThisFrame = true;
            try {
                capture();
            } catch (Throwable t) {
                failed = true;
                Night.LOGGER.error("HUD blur disabled", t);
                return null;
            }
        }
        return setup;
    }

    private static void capture() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer main = mc.getFramebuffer();
        // blur at half resolution: four times fewer pixels, looks the same once blurred
        int w = Math.max(1, main.textureWidth / 2);
        int h = Math.max(1, main.textureHeight / 2);
        if (target == null) {
            target = new SimpleFramebuffer("night-blur", w, h, false);
        } else if (target.textureWidth != w || target.textureHeight != h) {
            target.resize(w, h);
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Night blur downsample", target.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(UiPipeline.DOWNSAMPLE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", main.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.draw(0, 3);
        }

        PostEffectProcessor effect = mc.getShaderLoader().loadPostEffect(EFFECT, DefaultFramebufferSet.MAIN_ONLY);
        if (effect != null) effect.render(target, POOL);

        setup = TextureSetup.of(target.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
    }
}
