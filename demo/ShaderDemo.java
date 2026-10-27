import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBImageWrite;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * OpenGL window that shows the client's gradient patterns as real fragment shaders.
 *
 * Run with demo/run-shader-demo.sh (it finds LWJGL in the Gradle cache).
 * The shader lives in demo/patterns.frag and reloads automatically when you save it.
 */
public class ShaderDemo {

    static final String[] PATTERNS = {
            "Flow", "Mesh", "Aurora", "Plasma", "Conic", "Liquid", "Waves", "Cells", "Shimmer", "Pulse"
    };

    record Palette(String name, int... colors) {
    }

    static final List<Palette> PALETTES = List.of(
            new Palette("Night", 0x9D4EDD, 0x00C8FF),
            new Palette("Aqua", 0x3D8BFF, 0x4DF0E0),
            new Palette("Sunset", 0xFF4D8D, 0xFFA14D),
            new Palette("Blush", 0xFF8FB1, 0xB28DFF, 0x7FD1FF),
            new Palette("Mint", 0x2BD99F, 0xB4F06E),
            new Palette("Ember", 0xFF3D3D, 0xFF9F1C, 0xFFD166),
            new Palette("Ocean", 0x1E3C72, 0x2A9DF4, 0x9BE7FF),
            new Palette("Mono", 0xFFFFFF, 0x8A8AA0),
            new Palette("Rainbow", 0xFF5A5A, 0xFFB347, 0xFFF06A, 0x6BE585, 0x5AC8FA, 0x8E7CFF, 0xFF6AD5)
    );

    // state
    int pattern = 5;
    int palette = 0;
    boolean gallery = false;
    boolean ui = true;
    boolean paused = false;
    double speed = 1.0;
    double time = 0.0;

    long window;
    int program = 0;
    Path shaderPath;
    long shaderMtime = 0;
    long lastCheck = 0;
    String title = "";

    public static void main(String[] args) throws Exception {
        ShaderDemo demo = new ShaderDemo();
        String screenshot = null;
        int width = 1600, height = 900;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--pattern" -> demo.pattern = indexOf(PATTERNS, args[++i]);
                case "--palette" -> demo.palette = paletteIndex(args[++i]);
                case "--gallery" -> demo.gallery = true;
                case "--no-ui" -> demo.ui = false;
                case "--speed" -> demo.speed = Double.parseDouble(args[++i]);
                case "--time" -> demo.time = Double.parseDouble(args[++i]);
                case "--size" -> {
                    String[] wh = args[++i].split("x");
                    width = Integer.parseInt(wh[0]);
                    height = Integer.parseInt(wh[1]);
                }
                case "--screenshot" -> screenshot = args[++i];
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown option " + args[i] + " (try --help)");
            }
        }
        demo.shaderPath = Path.of(System.getProperty("demo.dir", "demo"), "patterns.frag");
        demo.run(width, height, screenshot);
    }

    static void printHelp() {
        System.out.println("""
                Night gradient pattern shaders

                  demo/run-shader-demo.sh [options]

                  --pattern <name>    Flow Mesh Aurora Plasma Conic Liquid Waves Cells Shimmer Pulse
                  --palette <name>    Night Aqua Sunset Blush Mint Ember Ocean Mono Rainbow
                  --gallery           start in the grid view
                  --no-ui             hide the mock client UI
                  --speed <x>         animation speed
                  --size <WxH>        window size (default 1600x900)
                  --screenshot <png>  render one frame to a file and exit (uses --time)

                Keys:  left/right pattern   up/down palette   G gallery   U ui overlay
                       +/- speed   space pause   S screenshot   R reload shader   Esc quit
                       click a tile in the gallery to open it
                """);
    }

    static int indexOf(String[] names, String name) {
        for (int i = 0; i < names.length; i++) if (names[i].equalsIgnoreCase(name)) return i;
        throw new IllegalArgumentException("Unknown pattern " + name);
    }

    static int paletteIndex(String name) {
        for (int i = 0; i < PALETTES.size(); i++) if (PALETTES.get(i).name().equalsIgnoreCase(name)) return i;
        throw new IllegalArgumentException("Unknown palette " + name);
    }

    // ------------------------------------------------------------------ main loop

    void run(int width, int height, String screenshot) throws Exception {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Could not start GLFW");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 0);
        if (screenshot != null) glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        window = glfwCreateWindow(width, height, "Night Shaders", 0, 0);
        if (window == 0) throw new IllegalStateException("Could not create window");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        if (!reloadShader(true)) {
            glfwTerminate();
            System.exit(1);
        }

        if (screenshot != null) {
            renderToFile(width, height, screenshot);
            glfwTerminate();
            return;
        }

        glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            if (action == GLFW_RELEASE) return;
            switch (key) {
                case GLFW_KEY_ESCAPE, GLFW_KEY_Q -> glfwSetWindowShouldClose(w, true);
                case GLFW_KEY_RIGHT, GLFW_KEY_D -> pattern = Math.floorMod(pattern + 1, PATTERNS.length);
                case GLFW_KEY_LEFT, GLFW_KEY_A -> pattern = Math.floorMod(pattern - 1, PATTERNS.length);
                case GLFW_KEY_UP, GLFW_KEY_W -> palette = Math.floorMod(palette - 1, PALETTES.size());
                case GLFW_KEY_DOWN -> palette = Math.floorMod(palette + 1, PALETTES.size());
                case GLFW_KEY_G, GLFW_KEY_TAB -> gallery = !gallery;
                case GLFW_KEY_U -> ui = !ui;
                case GLFW_KEY_SPACE -> paused = !paused;
                case GLFW_KEY_EQUAL, GLFW_KEY_KP_ADD -> speed = Math.min(5, speed + 0.25);
                case GLFW_KEY_MINUS, GLFW_KEY_KP_SUBTRACT -> speed = Math.max(0.25, speed - 0.25);
                case GLFW_KEY_R -> reloadShader(false);
                case GLFW_KEY_S -> saveScreenshot();
                default -> {
                }
            }
        });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS && gallery) clickGallery();
        });

        long last = System.nanoTime();
        while (!glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            if (!paused) time += (now - last) / 1e9 * speed;
            last = now;

            if (now / 1_000_000 - lastCheck > 500) {             // hot reload on save
                lastCheck = now / 1_000_000;
                try {
                    if (Files.getLastModifiedTime(shaderPath).toMillis() != shaderMtime) reloadShader(false);
                } catch (Exception ignored) {
                }
            }

            int[] fw = new int[1], fh = new int[1];
            glfwGetFramebufferSize(window, fw, fh);
            draw(fw[0], fh[0]);
            updateTitle();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
        glfwTerminate();
    }

    void draw(int w, int h) {
        glViewport(0, 0, w, h);
        glUseProgram(program);
        glUniform2f(loc("uRes"), w, h);
        glUniform1f(loc("uTime"), (float) time);
        glUniform1i(loc("uPattern"), pattern);
        glUniform1i(loc("uGallery"), gallery ? 1 : 0);
        glUniform1i(loc("uUi"), ui ? 1 : 0);
        glUniform1f(loc("uScale"), h / 1080.0f * 1.25f);

        int[] cols = PALETTES.get(palette).colors();
        float[] lab = new float[8 * 3];
        for (int i = 0; i < cols.length && i < 8; i++) {
            double[] l = OkLab.fromRgb(cols[i]);
            lab[i * 3] = (float) l[0];
            lab[i * 3 + 1] = (float) l[1];
            lab[i * 3 + 2] = (float) l[2];
        }
        glUniform3fv(loc("uPal"), lab);
        glUniform1i(loc("uPalCount"), Math.min(8, cols.length));
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    int loc(String name) {
        return glGetUniformLocation(program, name);
    }

    void updateTitle() {
        String t = String.format(Locale.ROOT, "Night Shaders  —  %s  ·  %s  ·  %.2fx%s   |   ←/→ pattern  ↑/↓ palette  G gallery  U ui  S screenshot",
                gallery ? "Gallery" : PATTERNS[pattern], PALETTES.get(palette).name(), speed, paused ? "  (paused)" : "");
        if (!t.equals(title)) {
            title = t;
            glfwSetWindowTitle(window, t);
        }
    }

    void clickGallery() {
        double[] mx = new double[1], my = new double[1];
        glfwGetCursorPos(window, mx, my);
        int[] ww = new int[1], wh = new int[1], fw = new int[1], fh = new int[1];
        glfwGetWindowSize(window, ww, wh);
        glfwGetFramebufferSize(window, fw, fh);
        double x = mx[0] * fw[0] / ww[0];
        double y = my[0] * fh[0] / wh[0];
        // same layout maths as the shader
        double scale = fh[0] / 1080.0 * 1.25;
        double margin = 28 * scale, gap = 18 * scale;
        double tw = (fw[0] - margin * 2 - gap * 4) / 5.0;
        double th = (fh[0] - margin * 2 - gap) / 2.0;
        int col = (int) Math.floor((x - margin) / (tw + gap));
        int row = (int) Math.floor((y - margin) / (th + gap));
        if (col >= 0 && col < 5 && row >= 0 && row < 2) {
            pattern = row * 5 + col;
            gallery = false;
        }
    }

    // ------------------------------------------------------------------ shaders

    static final String VERTEX = """
            #version 330 core
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    boolean reloadShader(boolean first) {
        try {
            shaderMtime = Files.getLastModifiedTime(shaderPath).toMillis();
            String frag = Files.readString(shaderPath);
            int vs = compile(GL_VERTEX_SHADER, VERTEX);
            int fs = compile(GL_FRAGMENT_SHADER, frag);
            if (vs == 0 || fs == 0) return false;
            int prog = glCreateProgram();
            glAttachShader(prog, vs);
            glAttachShader(prog, fs);
            glLinkProgram(prog);
            glDeleteShader(vs);
            glDeleteShader(fs);
            if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
                System.err.println("Link failed:\n" + glGetProgramInfoLog(prog));
                glDeleteProgram(prog);
                return false;
            }
            if (program != 0) glDeleteProgram(program);
            program = prog;
            if (!first) System.out.println("Shader reloaded");
            return true;
        } catch (Exception e) {
            System.err.println("Could not load " + shaderPath.toAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }

    static int compile(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) {
            System.err.println((type == GL_VERTEX_SHADER ? "Vertex" : "Fragment") + " shader error:\n" + glGetShaderInfoLog(s));
            glDeleteShader(s);
            return 0;
        }
        return s;
    }

    // ------------------------------------------------------------------ screenshots

    void saveScreenshot() {
        int[] fw = new int[1], fh = new int[1];
        glfwGetFramebufferSize(window, fw, fh);
        draw(fw[0], fh[0]);
        try {
            Path dir = shaderPath.getParent().resolve("screenshots");
            Files.createDirectories(dir);
            String name = (gallery ? "gallery" : PATTERNS[pattern].toLowerCase(Locale.ROOT))
                    + "-" + PALETTES.get(palette).name().toLowerCase(Locale.ROOT) + "-" + System.currentTimeMillis() % 100000 + ".png";
            writePng(fw[0], fh[0], dir.resolve(name).toString());
            System.out.println("Saved " + dir.resolve(name));
        } catch (Exception e) {
            System.err.println("Screenshot failed: " + e.getMessage());
        }
    }

    void renderToFile(int w, int h, String file) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        draw(w, h);
        writePng(w, h, file);
        System.out.println("Saved " + file);
    }

    static void writePng(int w, int h, String file) {
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        STBImageWrite.stbi_flip_vertically_on_write(true);
        STBImageWrite.stbi_write_png(file, w, h, 4, buf, w * 4);
    }

    // ------------------------------------------------------------------ colour

    static final class OkLab {
        static double toLinear(int c) {
            double v = c / 255.0;
            return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }

        static double[] fromRgb(int c) {
            double r = toLinear((c >> 16) & 255), g = toLinear((c >> 8) & 255), b = toLinear(c & 255);
            double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
            double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
            double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
            return new double[]{
                    0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                    1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                    0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
        }
    }
}
