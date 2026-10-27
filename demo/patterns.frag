#version 330 core
// Gradient pattern shaders for the Night client.
// Every pattern takes the active palette (up to 8 colours, given in OKLab) and paints it
// across the screen in a different way. Colours are blended in OKLab, lit in linear RGB,
// and converted to sRGB at the end.

out vec4 fragColor;

uniform vec2  uRes;        // framebuffer size in pixels
uniform float uTime;       // seconds, already multiplied by speed
uniform int   uPattern;    // pattern shown in focus mode
uniform int   uGallery;    // 1 = grid of every pattern
uniform int   uUi;         // 1 = draw the mock client UI on top
uniform vec3  uPal[8];     // palette in OKLab
uniform int   uPalCount;
uniform float uScale;      // UI scale (1 = 1080p)

const int   PATTERN_COUNT = 10;
const float PI = 3.14159265;

// ------------------------------------------------------------------ colour

vec3 oklabToLinear(vec3 c) {
    float l_ = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
    float m_ = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
    float s_ = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
    float l = l_ * l_ * l_, m = m_ * m_ * m_, s = s_ * s_ * s_;
    return vec3( 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193263 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
}

vec3 linearToSrgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}

vec3 srgbToLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}

vec3 palLab(float t) {
    t = clamp(t, 0.0, 1.0);
    float f = t * float(uPalCount - 1);
    int i = int(min(floor(f), float(uPalCount - 2)));
    return mix(uPal[i], uPal[i + 1], f - float(i));
}

/** Palette colour at t in [0,1], linear RGB. */
vec3 pal(float t) { return max(oklabToLinear(palLab(t)), 0.0); }

/** Palette that loops seamlessly (0 -> 1 -> 0). */
vec3 palLoop(float x) { float f = fract(x); return pal(1.0 - abs(2.0 * f - 1.0)); }

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

// ------------------------------------------------------------------ noise

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec2 hash2(vec2 p) { float n = hash(p); return vec2(n, hash(p + n + 17.0)); }

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
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

// ------------------------------------------------------------------ patterns
// uv: centred, height spans -0.5..0.5. Returns linear RGB.

vec3 pFlow(vec2 uv, float t) {
    float x = dot(uv, vec2(0.85, 0.53)) * 0.9 - t * 0.12;
    x += 0.04 * sin(uv.y * 6.0 + t * 0.7);
    return palLoop(x);
}

vec3 pMesh(vec2 uv, float t) {
    vec2 p0 = vec2(sin(t * 0.31) * 0.55, cos(t * 0.23) * 0.35);
    vec2 p1 = vec2(cos(t * 0.27 + 1.0) * 0.60, sin(t * 0.35 + 2.0) * 0.38);
    vec2 p2 = vec2(sin(t * 0.19 + 4.0) * 0.50, cos(t * 0.29 + 3.0) * 0.40);
    vec2 p3 = vec2(cos(t * 0.33 + 5.0) * 0.45, sin(t * 0.21 + 1.5) * 0.33);
    float w0 = 1.0 / pow(length(uv - p0) + 0.05, 2.5);
    float w1 = 1.0 / pow(length(uv - p1) + 0.05, 2.5);
    float w2 = 1.0 / pow(length(uv - p2) + 0.05, 2.5);
    float w3 = 1.0 / pow(length(uv - p3) + 0.05, 2.5);
    vec3 lab = (palLab(0.0) * w0 + palLab(0.33) * w1 + palLab(0.66) * w2 + palLab(1.0) * w3) / (w0 + w1 + w2 + w3);
    return max(oklabToLinear(lab), 0.0);
}

vec3 pAurora(vec2 uv, float t) {
    vec3 col = pal(0.0) * 0.02 + vec3(0.002, 0.002, 0.004);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float band = (fbm(vec2(uv.x * 1.3 + t * 0.07 + fi * 3.1, fi * 1.7 + t * 0.05)) - 0.5) * 0.7 + 0.12 - fi * 0.09;
        float d = uv.y - band;
        float line = exp(-abs(d) * (14.0 + fi * 4.0));
        float curtain = smoothstep(-0.01, 0.06, d) * exp(-max(d, 0.0) * 4.0);
        float rays = 0.55 + 0.45 * noise(vec2(uv.x * 28.0 + fi * 10.0, t * 0.5));
        col += pal(fi / 3.0) * (line * 0.7 + curtain * 0.45 * rays) * 0.6;
    }
    // stars
    vec2 sp = floor(gl_FragCoord.xy / 3.0);
    float star = step(0.9975, hash(sp)) * (0.5 + 0.5 * sin(t * 3.0 + hash(sp + 3.0) * 20.0));
    col += vec3(star) * 0.6 * smoothstep(0.0, 0.3, uv.y);
    return col;
}

vec3 pPlasma(vec2 uv, float t) {
    vec2 p = uv * 3.0;
    float v = sin(p.x + t) + sin(p.y * 1.2 + t * 1.3) + sin((p.x + p.y) * 0.8 + t * 0.7)
            + sin(length(p + vec2(sin(t * 0.5), cos(t * 0.4)) * 2.0) * 1.5 - t);
    return palLoop(v * 0.125 + 0.5 + t * 0.03);
}

vec3 pConic(vec2 uv, float t) {
    float a = atan(uv.y, uv.x) / (2.0 * PI) + t * 0.06;
    float r = length(uv);
    vec3 c = palLoop(a * 2.0 + r * 0.25);
    return c * (0.35 + 0.65 * smoothstep(0.95, 0.0, r));
}

vec3 pLiquid(vec2 uv, float t) {
    vec2 p = uv * 2.2;
    vec2 q = vec2(fbm(p + t * 0.06), fbm(p + vec2(5.2, 1.3) - t * 0.05));
    vec2 r = vec2(fbm(p + 3.5 * q + vec2(1.7, 9.2) + t * 0.08), fbm(p + 3.5 * q + vec2(8.3, 2.8) - t * 0.07));
    float f = fbm(p + 3.0 * r);
    vec3 c = pal(clamp(f * 1.5 - 0.25, 0.0, 1.0));
    return c * (0.25 + 1.3 * clamp(length(q) * f * 1.2, 0.0, 1.0));
}

vec3 pWaves(vec2 uv, float t) {
    vec3 col = pal(0.0) * 0.03;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float y = 0.36 - fi * 0.13
                + sin(uv.x * (2.2 + fi * 0.4) + t * (0.6 + fi * 0.15) + fi * 1.7) * 0.045
                + sin(uv.x * 5.3 - t * 0.8 + fi) * 0.015;
        float aa = 1.5 / uRes.y;
        float m = smoothstep(aa, -aa, uv.y - y);
        float shade = 1.0 - 0.45 * smoothstep(0.0, 0.14, y - uv.y);
        float sh = smoothstep(0.035, 0.0, uv.y - y) * 0.45 * (1.0 - m);
        col = mix(col * (1.0 - sh), pal(fi / 5.0) * shade, m);
    }
    return col;
}

vec3 pCells(vec2 uv, float t) {
    vec2 p = uv * 4.0;
    vec2 ip = floor(p), fp = fract(p);
    float d1 = 8.0, d2 = 8.0;
    vec2 best = vec2(0);
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y);
            vec2 o = 0.5 + 0.4 * sin(t * 0.6 + 6.2831 * hash2(ip + g));
            float d = length(g + o - fp);
            if (d < d1) { d2 = d1; d1 = d; best = ip + g; }
            else if (d < d2) { d2 = d; }
        }
    vec3 c = pal(fract(hash(best) + t * 0.03)) * (0.45 + 0.55 * (1.0 - d1));
    float edge = smoothstep(0.0, 0.05, d2 - d1);
    return mix(pal(1.0) * 1.3 + 0.1, c, edge);
}

vec3 pShimmer(vec2 uv, float t) {
    float x = dot(uv, vec2(0.85, 0.53));
    vec3 c = pal(clamp(x * 0.9 + 0.5, 0.0, 1.0)) * 0.85;
    float pos = mod(t * 0.35, 2.4) - 1.2;
    float g = exp(-pow((x - pos) * 9.0, 2.0));
    float fine = exp(-pow((x - pos + 0.07) * 45.0, 2.0)) * 0.6;
    return c + vec3(g * 0.45 + fine) * (0.6 + 0.4 * c);
}

vec3 pPulse(vec2 uv, float t) {
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float wob = r + 0.02 * sin(a * 6.0 + t);
    vec3 c = palLoop(wob * 1.3 - t * 0.2);
    c *= 0.7 + 0.3 * (0.5 + 0.5 * sin(wob * 40.0 - t * 5.0));
    return c * (smoothstep(1.1, 0.1, r) * 0.8 + 0.2);
}

vec3 pattern(int id, vec2 uv, float t) {
    if (id == 0) return pFlow(uv, t);
    if (id == 1) return pMesh(uv, t);
    if (id == 2) return pAurora(uv, t);
    if (id == 3) return pPlasma(uv, t);
    if (id == 4) return pConic(uv, t);
    if (id == 5) return pLiquid(uv, t);
    if (id == 6) return pWaves(uv, t);
    if (id == 7) return pCells(uv, t);
    if (id == 8) return pShimmer(uv, t);
    return pPulse(uv, t);
}

// ------------------------------------------------------------------ shapes (pixel space)

float sdRoundBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

/** Signed distance to a rounded rect given by its top-left corner and size (y down). */
float box(vec2 P, vec2 pos, vec2 size, float r) {
    return sdRoundBox(P - (pos + size * 0.5), size * 0.5, r);
}

float fillAA(float d) { return clamp(0.5 - d, 0.0, 1.0); }

// ------------------------------------------------------------------ mock client UI

vec2 toUv(vec2 frag) { return (frag - 0.5 * uRes) / uRes.y; }

/** Frosted glass: average the pattern around the pixel. */
vec3 blurred(vec2 frag, float t) {
    vec3 acc = vec3(0);
    float rad = 14.0 * uScale;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * (2.0 * PI / 12.0);
        float rr = (i % 2 == 0) ? rad : rad * 2.0;
        acc += pattern(uPattern, toUv(frag + vec2(cos(a), sin(a)) * rr), t);
    }
    return acc / 12.0;
}

/** Bright, readable accent taken from the pattern under the pixel (for "text", bars, toggles). */
vec3 accentAt(vec3 under, vec2 P) {
    vec3 flow = pal(fract(P.x / uRes.x * 0.7 + P.y / uRes.y * 0.3 - uTime * 0.08) * 0.999);
    float l = luma(under);
    vec3 norm = clamp(under / max(l, 1e-4) * 0.5, 0.0, 1.5);
    return mix(flow, norm, smoothstep(0.02, 0.12, l));
}

vec3 drawUi(vec3 col, vec2 frag, float t) {
    float s = uScale;
    vec2 P = vec2(frag.x, uRes.y - frag.y);        // pixel coords, y down
    vec3 dark = srgbToLinear(vec3(12.0, 12.0, 17.0) / 255.0);
    vec3 card = srgbToLinear(vec3(24.0, 24.0, 31.0) / 255.0);
    vec3 grey = srgbToLinear(vec3(150.0, 150.0, 168.0) / 255.0);
    vec3 dim  = srgbToLinear(vec3(80.0, 80.0, 95.0) / 255.0);
    vec3 acc = accentAt(col, P);

    // ---------- ClickGUI panel (left)
    vec2 pp = vec2(48.0, 48.0) * s;
    vec2 ps = vec2(360.0, 330.0) * s;
    float dPanel = box(P, pp, ps, 16.0 * s);
    col *= 1.0 - 0.55 * exp(-max(dPanel, 0.0) / (22.0 * s)) * step(0.0, dPanel);   // drop shadow
    if (dPanel < 1.0) {
        vec3 glass = mix(blurred(frag, t), dark, 0.80);
        col = mix(col, glass, fillAA(dPanel));
        col = mix(col, vec3(1.0), 0.10 * fillAA(abs(dPanel + 0.5) - 0.5));        // hairline border
        vec2 L = P - pp;                                                           // local coords

        // title "text" painted with the pattern + subtitle
        col = mix(col, acc * 1.15, fillAA(box(L, vec2(24, 26) * s, vec2(118, 15) * s, 7.5 * s)));
        col = mix(col, dim, fillAA(box(L, vec2(24, 50) * s, vec2(70, 7) * s, 3.5 * s)));

        for (int i = 0; i < 5; i++) {
            float y = (82.0 + float(i) * 47.0) * s;
            bool on = (i == 0 || i == 2 || i == 3);
            vec2 cp = vec2(16.0 * s, y);
            vec2 cs = vec2(328.0, 39.0) * s;
            float dc = box(L, cp, cs, 10.0 * s);
            col = mix(col, card, fillAA(dc) * 0.9);
            if (on) col += acc * 0.10 * fillAA(dc) * smoothstep(cp.x + cs.x * 0.7, cp.x, L.x);   // tinted when on
            float nameW = (70.0 + mod(float(i) * 37.0, 60.0)) * s;
            col = mix(col, on ? acc * 1.1 : grey, fillAA(box(L, cp + vec2(14, 11) * s, vec2(nameW, 8.0 * s), 4.0 * s)));
            col = mix(col, dim, fillAA(box(L, cp + vec2(14, 24) * s, vec2(nameW * 1.6, 5.0 * s), 2.5 * s)));

            // toggle switch
            vec2 tp = cp + vec2(cs.x - 50.0 * s, 10.5 * s);
            vec2 ts = vec2(36.0, 18.0) * s;
            col = mix(col, on ? acc : srgbToLinear(vec3(0.17, 0.17, 0.21)), fillAA(box(L, tp, ts, 9.0 * s)));
            vec2 kc = tp + vec2(on ? ts.x - 9.0 * s : 9.0 * s, 9.0 * s);
            col *= 1.0 - 0.25 * fillAA(length(L - kc - vec2(0, 1.0 * s)) - 6.5 * s);
            col = mix(col, on ? vec3(1.0) : grey, fillAA(length(L - kc) - 6.5 * s));
        }
    }

    // ---------- ArrayList (top right)
    float rowH = 22.0 * s;
    for (int i = 0; i < 7; i++) {
        float w = (200.0 - float(i) * 19.0) * s;
        vec2 rp = vec2(uRes.x - 24.0 * s - w, (24.0 * s) + float(i) * rowH);
        vec2 rs = vec2(w, rowH);
        float d = box(P, rp, rs, 0.0);
        col += acc * 0.20 * exp(-max(d, 0.0) / (9.0 * s)) * step(0.0, d);             // glow
        col = mix(col, dark, fillAA(d) * 0.72);
        col = mix(col, acc * 1.1, fillAA(box(P, rp + vec2(10.0 * s, rowH * 0.5 - 4.0 * s), vec2(w - 30.0 * s, 8.0 * s), 4.0 * s)));
        col = mix(col, acc, fillAA(box(P, rp + vec2(w + 3.0 * s, 0), vec2(3.0 * s, rowH), 1.5 * s)));
    }

    // ---------- TargetHUD (bottom centre)
    vec2 hs = vec2(300.0, 84.0) * s;
    vec2 hp = vec2(uRes.x * 0.5 - hs.x * 0.5, uRes.y - hs.y - 56.0 * s);
    float dh = box(P, hp, hs, 14.0 * s);
    col *= 1.0 - 0.5 * exp(-max(dh, 0.0) / (18.0 * s)) * step(0.0, dh);
    if (dh < 1.0) {
        col = mix(col, mix(blurred(frag, t), dark, 0.80), fillAA(dh));
        col = mix(col, vec3(1.0), 0.10 * fillAA(abs(dh + 0.5) - 0.5));
        vec2 L = P - hp;
        // "head": the pattern itself through a rounded window
        float dHead = box(L, vec2(14, 14) * s, vec2(56, 56) * s, 10.0 * s);
        col = mix(col, pattern(uPattern, toUv(frag) * 2.5, t * 1.5) * 1.2 + 0.03, fillAA(dHead));
        col = mix(col, vec3(0.92), fillAA(box(L, vec2(84, 18) * s, vec2(110, 10) * s, 5.0 * s)));
        col = mix(col, dim, fillAA(box(L, vec2(84, 36) * s, vec2(64, 6) * s, 3.0 * s)));
        // health bar with glow
        float hpv = 0.35 + 0.6 * (0.5 + 0.5 * sin(t * 0.8));
        vec2 bp = vec2(84, 54) * s;
        vec2 bs = vec2(200, 9) * s;
        col = mix(col, srgbToLinear(vec3(0.13, 0.13, 0.17)), fillAA(box(L, bp, bs, 4.5 * s)));
        float dFill = box(L, bp, vec2(bs.x * hpv, bs.y), 4.5 * s);
        col += acc * 0.35 * exp(-max(dFill, 0.0) / (6.0 * s)) * step(0.0, dFill) * step(0.0, -dh);
        col = mix(col, acc * 1.15, fillAA(dFill));
    }
    return col;
}

// ------------------------------------------------------------------ main

void main() {
    vec2 frag = gl_FragCoord.xy;
    vec3 col;

    if (uGallery == 1) {
        // 5 x 2 grid of every pattern
        float margin = 28.0 * uScale, gap = 18.0 * uScale;
        vec2 tile = vec2((uRes.x - margin * 2.0 - gap * 4.0) / 5.0, (uRes.y - margin * 2.0 - gap) / 2.0);
        vec2 P = vec2(frag.x, uRes.y - frag.y) - margin;
        vec2 cell = floor(P / (tile + gap));
        vec2 local = P - cell * (tile + gap);
        int id = int(cell.y) * 5 + int(cell.x);
        col = srgbToLinear(vec3(10.0, 10.0, 14.0) / 255.0);
        if (cell.x >= 0.0 && cell.x < 5.0 && cell.y >= 0.0 && cell.y < 2.0) {
            float d = sdRoundBox(local - tile * 0.5, tile * 0.5, 16.0 * uScale);
            vec2 uv = (local - tile * 0.5) / tile.y;
            uv.y = -uv.y;
            col = mix(col, pattern(id, uv, uTime), fillAA(d));
            if (id == uPattern) {
                col = mix(col, vec3(1.0), 0.9 * fillAA(abs(d + 2.0 * uScale) - 1.5 * uScale));
            }
        }
    } else {
        col = pattern(uPattern, toUv(frag), uTime);
        if (uUi == 1) col = drawUi(col, frag, uTime);
    }

    // tiny noise to stop colour banding
    float dither = (hash(frag + fract(uTime) * 61.0) - 0.5) / 255.0;
    fragColor = vec4(linearToSrgb(col) + dither, 1.0);
}
