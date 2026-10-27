#version 330

// Night UI shader (vertex). One shader draws every UI primitive; the mode is packed in UV2.
//   UV0  float2 : texture coordinate (glyphs, images) or extra data (time for backgrounds)
//   UV1  short2 : quad corner position relative to the shape centre, in 1/16 GUI pixels
//   UV2  short2 : x = corner radius * 16, y = mode * 4096 + param * 16
//   Normal      : second colour for backgrounds (rgb mapped to -1..1)

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vColor;
out vec2 vUv;
out vec2 vLocal;
flat out vec2 vCorner;
flat out vec2 vParams;
flat out int vMode;
flat out vec3 vExtra;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vColor = Color;
    vUv = UV0;
    vLocal = vec2(UV1) / 16.0;
    vCorner = abs(vec2(UV1)) / 16.0;
    vMode = UV2.y / 4096;
    vParams = vec2(float(UV2.x) / 16.0, float(UV2.y - vMode * 4096) / 16.0);
    vExtra = Normal;
}
