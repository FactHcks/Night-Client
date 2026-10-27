package me.night.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** The single GPU pipeline used for all client UI drawing (shapes, text, images, backgrounds). */
public final class UiPipeline {
    public static final RenderPipeline UI = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("night", "pipeline/ui"))
                    .withVertexShader(Identifier.of("night", "core/ui"))
                    .withFragmentShader(Identifier.of("night", "core/ui"))
                    .withSampler("Sampler0")
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
                    .build());

    /** Fullscreen pass that shrinks the world image to half size before blurring. */
    public static final RenderPipeline DOWNSAMPLE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
                    .withLocation(Identifier.of("night", "pipeline/downsample"))
                    .withVertexShader(Identifier.ofVanilla("core/screenquad"))
                    .withFragmentShader(Identifier.of("night", "post/downsample"))
                    .withSampler("InSampler")
                    .build());

    private UiPipeline() {
    }

    /** Forces class loading so the pipeline is registered before resources load. */
    public static void init() {
    }
}
