#version 330

// Night UI shader (fragment).
// Modes: 0 rounded fill, 1 rounded outline, 2 soft shadow, 3 font glyph,
//        4 rounded image, 5 aurora background, 6 blurred backdrop

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

uniform sampler2D Sampler0; // glyph atlas, image, or blurred world (glass)

in vec4 vColor;
in vec2 vUv;
in vec2 vLocal;
flat in vec2 vCorner;
flat in vec2 vParams;
flat in int vMode;
flat in vec3 vExtra;

out vec4 fragColor;

// Quads are drawn this much larger than the shape so the anti-aliased edge is not cut off.
const float PAD = 1.0;
const float PI = 3.14159265;

// ------------------------------------------------------------------ shapes

float sdRoundBox(vec2 p, vec2 halfSize, float r) {
    r = min(r, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float coverage(float d) {
    float w = max(fwidth(d), 1e-4);
    return clamp(0.5 - d / w, 0.0, 1.0);
}

float erf(float x) {
    float s = sign(x), a = abs(x);
    x = 1.0 + (0.278393 + (0.230389 + 0.078108 * (a * a)) * a) * a;
    x *= x;
    return s - s / (x * x);
}

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// ------------------------------------------------------------------ colour helpers (aurora)

vec3 srgbToLinear(vec3 c) { return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c)); }
vec3 linearToSrgb(vec3 c) { c = clamp(c, 0.0, 1.0); return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c)); }

vec3 linearToOklab(vec3 c) {
    float l = pow(0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b, 1.0 / 3.0);
    float m = pow(0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b, 1.0 / 3.0);
    float s = pow(0.0883024619 * c.r + 0.2817188376 * c.g + 0.6299787005 * c.b, 1.0 / 3.0);
    return vec3(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s);
}

vec3 oklabToLinear(vec3 c) {
    float l_ = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
    float m_ = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
    float s_ = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
    float l = l_ * l_ * l_, m = m_ * m_ * m_, s = s_ * s_ * s_;
    return max(vec3( 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                    -1.2684380046 * l + 2.6097574011 * m - 0.3413193263 * s,
                    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s), 0.0);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    mat2 r = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = r * p * 2.02 + 3.1;
        a *= 0.5;
    }
    return v;
}

vec3 aurora(vec2 uv, float t, vec3 labA, vec3 labB) {
    // deep night sky with a soft tint of the first colour
    vec3 col = mix(vec3(0.004, 0.004, 0.009), oklabToLinear(labA) * 0.02, 0.6);
    col += oklabToLinear(labB) * 0.025 * smoothstep(0.6, -0.5, uv.y);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float band = (fbm(vec2(uv.x * 1.2 + t * 0.06 + fi * 3.1, fi * 1.7 + t * 0.045)) - 0.5) * 0.7 + 0.14 - fi * 0.085;
        float d = uv.y - band;
        float line = exp(-abs(d) * (14.0 + fi * 4.0));
        float curtain = smoothstep(-0.01, 0.07, d) * exp(-max(d, 0.0) * 3.6);
        float rays = 0.55 + 0.45 * noise(vec2(uv.x * 26.0 + fi * 10.0, t * 0.45));
        vec3 c = oklabToLinear(mix(labA, labB, fi / 3.0));
        col += c * (line * 0.65 + curtain * 0.42 * rays) * 0.55;
    }
    // stars
    vec2 sp = floor(gl_FragCoord.xy / 2.0);
    float star = step(0.9982, hash(sp)) * (0.55 + 0.45 * sin(t * 2.5 + hash(sp + 3.0) * 30.0));
    col += vec3(star) * 0.55 * smoothstep(0.05, 0.4, uv.y);
    // vignette
    col *= 1.0 - 0.45 * smoothstep(0.4, 1.1, length(uv * vec2(0.8, 1.2)));
    return col;
}

// ------------------------------------------------------------------ main

void main() {
    vec4 col = vColor;
    float radius = vParams.x;
    float param = vParams.y;
    float a = 1.0;

    if (vMode == 0) {
        a = coverage(sdRoundBox(vLocal, vCorner - PAD, radius));
    } else if (vMode == 1) {
        float d = sdRoundBox(vLocal, vCorner - PAD, radius);
        a = coverage(d) - coverage(d + param);
    } else if (vMode == 2) {
        float d = sdRoundBox(vLocal, vCorner - param, radius);
        a = 0.5 - 0.5 * erf(d / max(param * 0.5, 0.01));
    } else if (vMode == 3) {
        a = pow(texture(Sampler0, vUv).a, 0.85);
    } else if (vMode == 4) {
        col *= texture(Sampler0, vUv);
        a = coverage(sdRoundBox(vLocal, vCorner - PAD, radius));
    } else if (vMode == 6) {
        // frosted glass: the blurred world behind this pixel
        vec2 uv = gl_FragCoord.xy / ScreenSize;
        col = vec4(texture(Sampler0, uv).rgb * vColor.rgb, vColor.a);
        a = coverage(sdRoundBox(vLocal, vCorner - PAD, radius));
    } else if (vMode == 5) {
        vec2 uv = vLocal / (vCorner.y * 2.0);
        uv.y = -uv.y;
        vec3 labA = linearToOklab(srgbToLinear(vColor.rgb));
        vec3 labB = linearToOklab(srgbToLinear(clamp(vExtra * 0.5 + 0.5, 0.0, 1.0)));
        col = vec4(linearToSrgb(aurora(uv, vUv.x, labA, labB)), vColor.a);
        if (radius > 0.0) a = coverage(sdRoundBox(vLocal, vCorner, radius));
    }

    col.a *= a;
    if (col.a <= 0.001) {
        discard;
    }
    // tiny noise removes banding in soft gradients and shadows
    col.rgb += (hash(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = col * ColorModulator;
}
