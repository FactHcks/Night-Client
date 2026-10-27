import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Terminal demo of the gradient pattern system for the client.
 *
 * Run:   java demo/GradientDemo.java            (interactive, needs a truecolor terminal)
 *        java demo/GradientDemo.java --once     (print every preset once, no animation)
 *        java demo/GradientDemo.java --help
 *
 * The classes Gradient, Pattern and Presets have no Minecraft dependencies; they are the part
 * meant to move into the client (me.night.util.gradient). Everything below "CLI" is just the demo.
 */
public class GradientDemo {

    // =====================================================================================
    // Gradient: colour stops, sampled in OKLab (perceptual) or plain RGB
    // =====================================================================================

    public enum Space { OKLAB, RGB }

    public static final class Gradient {
        public final String name;
        private final double[] pos;
        private final int[] argb;
        private final double[][] lab;

        /** Colours are spread evenly from 0 to 1. */
        public Gradient(String name, int... colors) {
            this.name = name;
            this.argb = colors.clone();
            this.pos = new double[colors.length];
            this.lab = new double[colors.length][];
            for (int i = 0; i < colors.length; i++) {
                this.pos[i] = colors.length == 1 ? 0 : (double) i / (colors.length - 1);
                this.lab[i] = OkLab.fromArgb(colors[i]);
            }
        }

        /** Colour at t in [0,1]. Values outside are clamped. */
        public int sample(double t, Space space) {
            t = Math.max(0, Math.min(1, t));
            int n = argb.length;
            if (n == 1) return argb[0];
            int seg = Math.min(n - 2, (int) (t * (n - 1)));
            double local = (t - pos[seg]) / (pos[seg + 1] - pos[seg]);
            if (space == Space.RGB) return lerpRgb(argb[seg], argb[seg + 1], local);
            double[] a = lab[seg], b = lab[seg + 1];
            int alpha = (int) Math.round(((argb[seg] >>> 24) * (1 - local)) + ((argb[seg + 1] >>> 24) * local));
            return OkLab.toArgb(a[0] + (b[0] - a[0]) * local, a[1] + (b[1] - a[1]) * local, a[2] + (b[2] - a[2]) * local, alpha);
        }

        /** Seamless looping: 0 -> 1 -> 0 as x goes 0 -> 0.5 -> 1 (repeats). */
        public int sampleLooped(double x, Space space) {
            double f = x - Math.floor(x);
            return sample(1 - Math.abs(2 * f - 1), space);
        }

        static int lerpRgb(int c1, int c2, double t) {
            int a = (int) Math.round((c1 >>> 24) + ((c2 >>> 24) - (c1 >>> 24)) * t);
            int r = (int) Math.round(((c1 >> 16) & 255) + (((c2 >> 16) & 255) - ((c1 >> 16) & 255)) * t);
            int g = (int) Math.round(((c1 >> 8) & 255) + (((c2 >> 8) & 255) - ((c1 >> 8) & 255)) * t);
            int b = (int) Math.round((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    /** sRGB <-> OKLab conversion (Björn Ottosson). Blends stay bright instead of going muddy. */
    static final class OkLab {
        static double toLinear(int c) {
            double v = c / 255.0;
            return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }

        static int fromLinear(double v) {
            v = Math.max(0, Math.min(1, v));
            double s = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
            return (int) Math.round(s * 255);
        }

        static double[] fromArgb(int c) {
            double r = toLinear((c >> 16) & 255), g = toLinear((c >> 8) & 255), b = toLinear(c & 255);
            double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
            double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
            double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
            return new double[]{
                    0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                    1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                    0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
        }

        static int toArgb(double L, double a, double b, int alpha) {
            double l = L + 0.3963377774 * a + 0.2158037573 * b;
            double m = L - 0.1055613458 * a - 0.0638541728 * b;
            double s = L - 0.0894841775 * a - 1.2914855480 * b;
            l = l * l * l; m = m * m * m; s = s * s * s;
            int r = fromLinear(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s);
            int g = fromLinear(-1.2684380046 * l + 2.6097574011 * m - 0.3413193263 * s);
            int bl = fromLinear(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
            return (alpha << 24) | (r << 16) | (g << 8) | bl;
        }
    }

    // =====================================================================================
    // Pattern: how a gradient is laid across N items (list lines, characters, bar pixels)
    // and how it moves over time.
    // =====================================================================================

    /** Settings shared by every pattern. */
    public static final class PatternSettings {
        public Space space = Space.OKLAB;
        /** Animation speed multiplier. */
        public double speed = 1.0;
        /** How much of the gradient one full set of items covers (1 = the whole gradient). */
        public double spread = 1.0;
    }

    public enum Pattern {
        /** Fixed gradient from first to last item. */
        STATIC("Static") {
            int color(Gradient g, int i, int n, double time, PatternSettings s) {
                return g.sample(frac(i, n) * s.spread, s.space);
            }
        },
        /** Gradient slides along the items and loops seamlessly. */
        SCROLL("Scroll") {
            int color(Gradient g, int i, int n, double time, PatternSettings s) {
                return g.sampleLooped(frac(i, n) * s.spread * 0.5 - time * s.speed * 0.25, s.space);
            }
        },
        /** Sine wave travelling through the items (what the HUD does today). */
        WAVE("Wave") {
            int color(Gradient g, int i, int n, double time, PatternSettings s) {
                double phase = time * s.speed * 2.0 - frac(i, n) * s.spread * Math.PI * 2;
                return g.sample((Math.sin(phase) + 1) / 2, s.space);
            }
        },
        /** Every item shares one colour that breathes through the gradient. */
        BREATHE("Breathe") {
            int color(Gradient g, int i, int n, double time, PatternSettings s) {
                double t = (Math.sin(time * s.speed * 1.6) + 1) / 2;
                return g.sample(smooth(t), s.space);
            }
        },
        /** Gradient window bounces back and forth with an ease at each end. */
        BOUNCE("Bounce") {
            int color(Gradient g, int i, int n, double time, PatternSettings s) {
                double x = time * s.speed * 0.3;
                double f = x - Math.floor(x);
                double offset = smooth(1 - Math.abs(2 * f - 1)) * 0.5;
                return g.sample(offset + frac(i, n) * s.spread * 0.5, s.space);
            }
        },
        /** Static gradient with a bright highlight band sweeping across (Rise-style shine). */
        SHIMMER("Shimmer") {
            int color(Gradient g, int i, int n, double time, PatternSettings s) {
                int base = g.sample(frac(i, n) * s.spread, s.space);
                double x = (time * s.speed * 0.45) % 1.6 - 0.3;   // band position, with a pause off-screen
                double d = Math.abs(frac(i, n) - x);
                double glow = Math.max(0, 1 - d / 0.12);
                return Gradient.lerpRgb(base, 0xFFFFFFFF, smooth(glow) * 0.75);
            }
        },
        /** Colours flow outward from the middle item. */
        CENTER("Center Out") {
            int color(Gradient g, int i, int n, double time, PatternSettings s) {
                double fromMid = Math.abs(frac(i, n) - 0.5) * 2;
                return g.sampleLooped(fromMid * s.spread * 0.5 - time * s.speed * 0.25, s.space);
            }
        };

        public final String label;

        Pattern(String label) {
            this.label = label;
        }

        abstract int color(Gradient g, int index, int count, double timeSeconds, PatternSettings s);

        static double frac(int i, int n) {
            return n <= 1 ? 0 : (double) i / (n - 1);
        }

        static double smooth(double t) {
            t = Math.max(0, Math.min(1, t));
            return t * t * (3 - 2 * t);
        }
    }

    // =====================================================================================
    // Presets
    // =====================================================================================

    public static final class Presets {
        public static final List<Gradient> ALL = List.of(
                new Gradient("Night", 0xFF9D4EDD, 0xFF00C8FF),
                new Gradient("Aqua", 0xFF3D8BFF, 0xFF4DF0E0),
                new Gradient("Sunset", 0xFFFF4D8D, 0xFFFFA14D),
                new Gradient("Blush", 0xFFFF8FB1, 0xFFB28DFF, 0xFF7FD1FF),
                new Gradient("Mint", 0xFF2BD99F, 0xFFB4F06E),
                new Gradient("Ember", 0xFFFF3D3D, 0xFFFF9F1C, 0xFFFFD166),
                new Gradient("Ocean", 0xFF1E3C72, 0xFF2A9DF4, 0xFF9BE7FF),
                new Gradient("Mono", 0xFFFFFFFF, 0xFF8A8AA0),
                new Gradient("Rainbow", 0xFFFF5A5A, 0xFFFFB347, 0xFFFFF06A, 0xFF6BE585, 0xFF5AC8FA, 0xFF8E7CFF, 0xFFFF6AD5)
        );
    }

    // =====================================================================================
    // CLI
    // =====================================================================================

    static final String ESC = "\u001b[";
    static final String RESET = ESC + "0m";
    static final int BG = 0xFF0E0E13;

    static String fg(int c) {
        return ESC + "38;2;" + ((c >> 16) & 255) + ";" + ((c >> 8) & 255) + ";" + (c & 255) + "m";
    }

    static String bg(int c) {
        return ESC + "48;2;" + ((c >> 16) & 255) + ";" + ((c >> 8) & 255) + ";" + (c & 255) + "m";
    }

    static final String[][] MODULES = {
            {"Killaura", "Single"}, {"Scaffold", "Telly"}, {"Velocity", "0% 0%"}, {"ChestStealer", null},
            {"InvManager", null}, {"Speed", "Strafe"}, {"NoFall", "Packet"}, {"Sprint", null},
    };

    int preset = 0;
    int pattern = 2;
    final PatternSettings settings = new PatternSettings();
    final long start = System.nanoTime();

    public static void main(String[] args) throws Exception {
        GradientDemo demo = new GradientDemo();
        boolean once = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> { printHelp(); return; }
                case "--once" -> once = true;
                case "--preset" -> demo.preset = findPreset(args[++i]);
                case "--pattern" -> demo.pattern = findPattern(args[++i]);
                case "--speed" -> demo.settings.speed = Double.parseDouble(args[++i]);
                case "--spread" -> demo.settings.spread = Double.parseDouble(args[++i]);
                case "--rgb" -> demo.settings.space = Space.RGB;
                default -> { System.err.println("Unknown option: " + args[i]); printHelp(); return; }
            }
        }
        if (once || System.console() == null) demo.printAll();
        else demo.runInteractive();
    }

    static void printHelp() {
        System.out.println("""
                Gradient pattern demo

                  java demo/GradientDemo.java [options]

                Options:
                  --preset <name>    start preset (Night, Aqua, Sunset, Blush, Mint, Ember, Ocean, Mono, Rainbow)
                  --pattern <name>   start pattern (Static, Scroll, Wave, Breathe, Bounce, Shimmer, Center)
                  --speed <x>        animation speed multiplier (default 1.0)
                  --spread <x>       how much of the gradient the item set covers (default 1.0)
                  --rgb              blend in plain RGB instead of OKLab
                  --once             print every preset once and exit (no animation)

                Keys (interactive):
                  left/right or a/d  preset        up/down or w/s  pattern
                  + / -              speed         [ / ]           spread
                  c                  OKLab <-> RGB q / Esc          quit
                """);
    }

    static int findPreset(String name) {
        for (int i = 0; i < Presets.ALL.size(); i++) {
            if (Presets.ALL.get(i).name.equalsIgnoreCase(name)) return i;
        }
        throw new IllegalArgumentException("No preset named " + name);
    }

    static int findPattern(String name) {
        for (Pattern p : Pattern.values()) {
            if (p.label.replace(" ", "").equalsIgnoreCase(name.replace(" ", "")) || p.name().equalsIgnoreCase(name)
                    || p.label.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT))) {
                return p.ordinal();
            }
        }
        throw new IllegalArgumentException("No pattern named " + name);
    }

    // ------------------------------------------------------------------ non-interactive

    void printAll() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n  Gradient presets  (top: OKLab blend, bottom: plain RGB for comparison)\n\n");
        for (Gradient g : Presets.ALL) {
            sb.append(String.format("  %-8s ", g.name));
            for (int x = 0; x < 56; x++) sb.append(bg(g.sample(x / 55.0, Space.OKLAB))).append(' ');
            sb.append(RESET).append("\n           ");
            for (int x = 0; x < 56; x++) sb.append(bg(g.sample(x / 55.0, Space.RGB))).append(' ');
            sb.append(RESET).append("\n\n");
        }
        System.out.print(sb);
    }

    // ------------------------------------------------------------------ interactive

    void runInteractive() throws Exception {
        stty("-icanon -echo min 0");
        System.out.print(ESC + "?1049h" + ESC + "?25l"); // alt screen, hide cursor
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.print(RESET + ESC + "?25h" + ESC + "?1049l");
            System.out.flush();
            stty("sane");
        }));

        InputStream in = System.in;
        boolean running = true;
        while (running) {
            while (in.available() > 0) {
                int c = in.read();
                if (c == 27 && in.available() >= 2) {          // arrow keys: ESC [ A/B/C/D
                    in.read();
                    int k = in.read();
                    if (k == 'C') cyclePreset(1);
                    if (k == 'D') cyclePreset(-1);
                    if (k == 'A') cyclePattern(-1);
                    if (k == 'B') cyclePattern(1);
                } else if (c == 27 || c == 'q' || c == 'Q' || c == 3) {
                    running = false;
                } else {
                    switch (c) {
                        case 'd' -> cyclePreset(1);
                        case 'a' -> cyclePreset(-1);
                        case 'w' -> cyclePattern(-1);
                        case 's' -> cyclePattern(1);
                        case '+', '=' -> settings.speed = Math.min(5, settings.speed + 0.25);
                        case '-', '_' -> settings.speed = Math.max(0, settings.speed - 0.25);
                        case ']' -> settings.spread = Math.min(4, settings.spread + 0.25);
                        case '[' -> settings.spread = Math.max(0.25, settings.spread - 0.25);
                        case 'c', 'C' -> settings.space = settings.space == Space.OKLAB ? Space.RGB : Space.OKLAB;
                        default -> { }
                    }
                }
            }
            System.out.print(ESC + "H" + frame());
            System.out.flush();
            Thread.sleep(33);
        }
        System.exit(0);
    }

    void cyclePreset(int d) {
        preset = Math.floorMod(preset + d, Presets.ALL.size());
    }

    void cyclePattern(int d) {
        pattern = Math.floorMod(pattern + d, Pattern.values().length);
    }

    static void stty(String args) {
        try {
            new ProcessBuilder("sh", "-c", "stty " + args + " < /dev/tty").inheritIO().start().waitFor();
        } catch (IOException | InterruptedException ignored) {
        }
    }

    String frame() {
        double time = (System.nanoTime() - start) / 1e9;
        Gradient g = Presets.ALL.get(preset);
        Pattern p = Pattern.values()[pattern];
        int width = 78;
        List<String> lines = new ArrayList<>();

        // header
        int accent = p.color(g, 0, 1, time, settings);
        lines.add(" " + fg(accent) + ESC + "1m" + "Gradient Pattern System" + RESET + fg(0xFF5F5F70) + "  ·  client preview" + RESET);
        lines.add("");
        lines.add(String.format(" %sPreset%s %s‹%-7s›%s  %sPattern%s %s‹%-10s›%s  %sBlend%s %-5s %sSpeed%s %.2fx  %sSpread%s %.2f",
                dim(), RESET, fg(0xFFEDEDF3), g.name, RESET,
                dim(), RESET, fg(0xFFEDEDF3), p.label, RESET,
                dim(), RESET, settings.space == Space.OKLAB ? "OKLab" : "RGB",
                dim(), RESET, settings.speed, dim(), RESET, settings.spread));
        lines.add("");

        // bars
        int barW = 62;
        lines.add(" " + dim() + "Live     " + RESET + bar(barW, x -> p.color(g, x, barW, time, settings)));
        lines.add(" " + dim() + "Stops    " + RESET + bar(barW, x -> g.sample(x / (barW - 1.0), Space.OKLAB)));
        lines.add(" " + dim() + "RGB lerp " + RESET + bar(barW, x -> g.sample(x / (barW - 1.0), Space.RGB)));
        lines.add("");

        // watermark (per character)
        String logo = "Night Client";
        StringBuilder wm = new StringBuilder(" " + dim() + "Text     " + RESET + ESC + "1m");
        for (int i = 0; i < logo.length(); i++) {
            wm.append(fg(p.color(g, i, logo.length(), time, settings))).append(logo.charAt(i));
        }
        wm.append(RESET);
        // health bar mock next to it
        wm.append("      ").append(dim()).append("Health ").append(RESET);
        double hp = 0.35 + 0.6 * (Math.sin(time * 0.7) + 1) / 2;
        int hpW = 26;
        int filled = (int) Math.round(hp * hpW);
        for (int x = 0; x < hpW; x++) {
            wm.append(x < filled ? bg(p.color(g, x, hpW, time, settings)) : bg(0xFF22222C)).append(' ');
        }
        wm.append(RESET);
        lines.add(wm.toString());
        lines.add("");

        // array list mock, right aligned, one colour per line
        lines.add(" " + dim() + "ArrayList" + RESET);
        String[][] sorted = MODULES.clone();
        java.util.Arrays.sort(sorted, (a, b) -> label(b).length() - label(a).length());
        for (int i = 0; i < sorted.length; i++) {
            int col = p.color(g, i, sorted.length, time, settings);
            String name = sorted[i][0];
            String suffix = sorted[i][1];
            int len = label(sorted[i]).length() + 2;
            StringBuilder row = new StringBuilder();
            row.append(" ".repeat(Math.max(0, width - len - 1)));
            row.append(bg(BG)).append(' ').append(fg(col)).append(name);
            if (suffix != null) row.append(fg(0xFF9C9CA8)).append(' ').append(suffix);
            row.append(' ').append(bg(col)).append(' ').append(RESET);
            lines.add(row.toString());
        }
        lines.add("");
        lines.add(" " + fg(0xFF5F5F70) + "←/→ preset   ↑/↓ pattern   +/- speed   [ ] spread   c blend   q quit" + RESET);

        StringBuilder out = new StringBuilder();
        for (String l : lines) out.append(l).append(ESC).append("K\n"); // clear rest of each line
        out.append(ESC).append("J");
        return out.toString();
    }

    static String label(String[] m) {
        return m[1] == null ? m[0] : m[0] + " " + m[1];
    }

    static String dim() {
        return fg(0xFF8A8A9A);
    }

    interface IntColor {
        int at(int x);
    }

    static String bar(int w, IntColor color) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < w; x++) sb.append(bg(color.at(x))).append(' ');
        return sb.append(RESET).toString();
    }
}
