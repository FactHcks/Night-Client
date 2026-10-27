#version 330

// Shrinks the world image to half size for the HUD blur.
// Four bilinear taps average a 4x4 block of source pixels, which avoids shimmering.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 t = 1.0 / vec2(textureSize(InSampler, 0));
    fragColor = 0.25 * (texture(InSampler, texCoord + t * vec2(-1.0, -1.0))
                      + texture(InSampler, texCoord + t * vec2( 1.0, -1.0))
                      + texture(InSampler, texCoord + t * vec2(-1.0,  1.0))
                      + texture(InSampler, texCoord + t * vec2( 1.0,  1.0)));
}
