package me.night.alt;

import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.ExhiSoundManager;
import me.night.util.Theme;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Add, switch and remove offline accounts. */
public class AltManagerScreen extends Screen {
    private static final long START = System.nanoTime();
    private static final float W = 300;
    private static final float ROW = 28;

    private final Screen parent;
    private String input = "";
    private boolean inputFocused = true;
    private String status = "";
    private int statusColor = 0xFF9A9AAA;
    private final Anim fade = new Anim(0);
    private float scroll = 0;
    private final Anim scrollAnim = new Anim(0);
    private final Map<String, Anim> hover = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();

    private float px, py, ph, listY, listH;

    private record Hit(float x, float y, float w, float h, boolean inList, Runnable action) {
    }

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Accounts"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int[] stops = Theme.stops();
        Render2D.aurora(ctx, 0, 0, this.width, this.height, (System.nanoTime() - START) / 1.0E9F, stops[0], stops[1]);
        Render2D.rect(ctx, 0, 0, this.width, this.height, 0, 0x50000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float f = Anim.easeOutCubic(this.fade.update(1, 9));
        this.hits.clear();
        this.ph = Math.min(300, this.height - 24);
        this.px = (this.width - W) / 2;
        this.py = (this.height - this.ph) / 2 + (1 - f) * 10;
        int accent = Theme.accent(0);
        Render2D.setAlpha(f);

        // panel
        Render2D.shadow(ctx, px, py + 4, W, ph, 12, 24, 0x80000000);
        Render2D.rect(ctx, px, py, W, ph, 12, 0xD80E0E14);
        Render2D.outline(ctx, px, py, W, ph, 12, 0.6F, 0x22FFFFFF);
        UiFont.TITLE.draw(ctx, "Accounts", px + 16, py + 16, 0xFFFFFFFF, false);
        UiFont.SMALL.draw(ctx, "Offline accounts for cracked servers", px + 16, py + 34, 0xFF9A9AAA, false);

        // current account
        float cy = py + 50;
        String current = this.client.getSession().getUsername();
        Render2D.rect(ctx, px + 12, cy, W - 24, 34, 8, 0xFF16161D);
        Render2D.outline(ctx, px + 12, cy, W - 24, 34, 8, 0.5F, ColorUtil.withAlpha(accent, 0x60));
        avatar(ctx, current, px + 20, cy + 7, 20);
        UiFont.BOLD.draw(ctx, current, px + 48, cy + 8, 0xFFFFFFFF, false);
        UiFont.SMALL.draw(ctx, AltManager.isSwitched() ? "Offline alt" : "Your account", px + 48, cy + 21, 0xFF9A9AAA, false);
        if (AltManager.isSwitched()) {
            button(ctx, mouseX, mouseY, "Restore", px + W - 24 - 62, cy + 8, 62, 18, false, false, () -> {
                AltManager.restore();
                setStatus("Back on your account", 0xFF4ADE80);
            });
        }

        // add row
        float ry = cy + 44;
        float fieldW = W - 24 - 50 - 62 - 10;
        Render2D.rect(ctx, px + 12, ry, fieldW, 22, 6, this.inputFocused ? 0xFF1E1E26 : 0xFF16161D);
        Render2D.outline(ctx, px + 12, ry, fieldW, 22, 6, 0.5F, this.inputFocused ? ColorUtil.withAlpha(accent, 0xC0) : 0x18FFFFFF);
        float tx = px + 20;
        if (this.input.isEmpty()) {
            UiFont.REGULAR.draw(ctx, "Username...", tx, UiFont.REGULAR.centerY(ry, 22), 0xFF5F5F70, false);
        } else {
            tx = UiFont.REGULAR.draw(ctx, this.input, tx, UiFont.REGULAR.centerY(ry, 22), 0xFFFFFFFF, false);
        }
        if (this.inputFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            Render2D.rect(ctx, tx + 1, ry + 6, 0.75F, 10, 0, 0xFFFFFFFF);
        }
        this.hits.add(new Hit(px + 12, ry, fieldW, 22, false, () -> this.inputFocused = true));
        button(ctx, mouseX, mouseY, "Add", px + 12 + fieldW + 5, ry, 50, 22, true, false, this::addInput);
        button(ctx, mouseX, mouseY, "Random", px + 12 + fieldW + 60, ry, 62, 22, false, false, () -> {
            String name = AltManager.randomName();
            AltManager.add(name);
            AltManager.login(name);
            setStatus("Logged in as " + name, 0xFF4ADE80);
        });

        if (!this.status.isEmpty()) {
            UiFont.SMALL.draw(ctx, this.status, px + 14, ry + 28, this.statusColor, false);
        }

        // list
        this.listY = ry + 40;
        this.listH = py + ph - 40 - this.listY;
        List<String> alts = AltManager.alts();
        float s = this.scrollAnim.update(this.scroll, 14);
        ctx.enableScissor(Math.round(px + 8), Math.round(listY), Math.round(px + W - 8), Math.round(listY + listH));
        float y = listY + s;
        if (alts.isEmpty()) {
            UiFont.REGULAR.drawCentered(ctx, "No accounts yet", px + W / 2, listY + 20, 0xFF5F5F70, false);
        }
        for (String name : new ArrayList<>(alts)) {
            boolean active = name.equals(current);
            boolean hovered = inside(mouseX, mouseY, px + 12, y, W - 24, ROW - 4) && inside(mouseX, mouseY, px, listY, W, listH);
            float hv = this.hover.computeIfAbsent(name, k -> new Anim()).update(hovered ? 1 : 0, 16);
            Render2D.rect(ctx, px + 12, y, W - 24, ROW - 4, 7, ColorUtil.lerp(0xFF16161D, 0xFF1E1E26, hv));
            if (active) Render2D.outline(ctx, px + 12, y, W - 24, ROW - 4, 7, 0.6F, ColorUtil.withAlpha(accent, 0x90));
            avatar(ctx, name, px + 19, y + 4, 16);
            float end = UiFont.MEDIUM.draw(ctx, name, px + 42, UiFont.MEDIUM.centerY(y, ROW - 4), 0xFFFFFFFF, false);
            if (active) {
                Render2D.rect(ctx, end + 6, y + 6, 34, 12, 6, ColorUtil.withAlpha(accent, 0x45));
                UiFont.SMALL.drawCentered(ctx, "Active", end + 23, UiFont.SMALL.centerY(y + 6, 12), 0xFFFFFFFF, false);
            }
            button(ctx, mouseX, mouseY, "Login", px + W - 24 - 70, y + 3, 44, 18, !active, false, () -> {
                AltManager.login(name);
                setStatus("Logged in as " + name, 0xFF4ADE80);
            }, true);
            button(ctx, mouseX, mouseY, "×", px + W - 24 - 22, y + 3, 18, 18, false, true, () -> {
                AltManager.remove(name);
                setStatus("Removed " + name, 0xFF9A9AAA);
            }, true);
            y += ROW;
        }
        ctx.disableScissor();
        float content = alts.size() * ROW;
        this.scroll = Math.max(Math.min(0, this.listH - content), Math.min(0, this.scroll));

        // back
        button(ctx, mouseX, mouseY, "Back", px + W / 2 - 50, py + ph - 32, 100, 22, false, false, this::close);

        Render2D.resetAlpha();
    }

    private void avatar(DrawContext ctx, String name, float x, float y, float size) {
        int hue = Math.abs(name.hashCode());
        int c1 = Theme.accent(hue % 40);
        int c2 = Theme.accentAlt(hue % 40);
        Render2D.rect4(ctx, x, y, size, size, size * 0.28F, c1, c2, c1, c2);
        UiFont f = size >= 20 ? UiFont.BOLD : UiFont.MEDIUM;
        f.drawCentered(ctx, name.substring(0, 1).toUpperCase(), x + size / 2, f.centerY(y, size), 0xFFFFFFFF, false);
    }

    private void button(DrawContext ctx, int mouseX, int mouseY, String label, float x, float y, float w, float h,
                        boolean primary, boolean danger, Runnable action) {
        button(ctx, mouseX, mouseY, label, x, y, w, h, primary, danger, action, false);
    }

    private void button(DrawContext ctx, int mouseX, int mouseY, String label, float x, float y, float w, float h,
                        boolean primary, boolean danger, Runnable action, boolean inList) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h) && (!inList || inside(mouseX, mouseY, px, listY, W, listH));
        float hv = this.hover.computeIfAbsent("b:" + label + (int) x + ":" + (int) (y - (inList ? this.scrollAnim.get() : 0)), k -> new Anim())
                .update(hovered ? 1 : 0, 16);
        int accent = Theme.accent(0);
        float r = Math.min(6, h / 2);
        if (primary) {
            Render2D.shadow(ctx, x, y, w, h, r, 8, ColorUtil.withAlpha(accent, Math.round(0x30 + 0x40 * hv)));
            Render2D.rectH(ctx, x, y, w, h, r, ColorUtil.brighter(accent, 0.12F * hv), ColorUtil.brighter(Theme.accentAlt(0), 0.12F * hv));
        } else if (danger) {
            Render2D.rect(ctx, x, y, w, h, r, ColorUtil.lerp(0x30FF5A6E, 0x90FF5A6E, hv));
        } else {
            Render2D.rect(ctx, x, y, w, h, r, ColorUtil.lerp(0xFF1A1A22, 0xFF262630, hv));
            Render2D.outline(ctx, x, y, w, h, r, 0.5F, ColorUtil.lerp(0x18FFFFFF, 0x40FFFFFF, hv));
        }
        UiFont f = h >= 20 ? UiFont.MEDIUM : UiFont.SMALL;
        f.drawCentered(ctx, label, x + w / 2, f.centerY(y, h), 0xFFFFFFFF, false);
        this.hits.add(new Hit(x, y, w, h, inList, action));
    }

    private void setStatus(String text, int color) {
        this.status = text;
        this.statusColor = color;
        ExhiSoundManager.playClick(1.1F);
    }

    private void addInput() {
        String name = this.input.trim();
        if (!AltManager.validName(name)) {
            setStatus("Names are 3-16 letters, numbers or _", 0xFFFF8A98);
            return;
        }
        AltManager.add(name);
        AltManager.login(name);
        this.input = "";
        setStatus("Logged in as " + name, 0xFF4ADE80);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            boolean inList = inside(click.x(), click.y(), this.px, this.listY, W, this.listH);
            for (int i = this.hits.size() - 1; i >= 0; i--) {
                Hit h = this.hits.get(i);
                if (h.inList && !inList) continue;
                if (inside(click.x(), click.y(), h.x, h.y, h.w, h.h)) {
                    this.inputFocused = false;
                    h.action.run();
                    return true;
                }
            }
            this.inputFocused = false;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scroll += (float) verticalAmount * 24;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_BACKSPACE && !this.input.isEmpty()) {
            this.input = this.input.substring(0, this.input.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            addInput();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        String s = input.asString();
        if (s.matches("[A-Za-z0-9_]") && this.input.length() < 16) {
            this.input += s;
            this.inputFocused = true;
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }
}
