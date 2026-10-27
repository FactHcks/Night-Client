package me.night.ui.menu;

import me.night.Night;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.ExhiSoundManager;
import me.night.util.Theme;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/** Client main menu: aurora shader sky, glowing logo and glass buttons. */
public class NightMainMenu extends Screen {
    private static final long START = System.nanoTime();
    private static boolean playedIntro = false;

    private static final String[][] CHANGELOG = {
            {"+", "GPU shader renderer"},
            {"+", "New main menu"},
            {"+", "Crisp font engine"},
            {"*", "Smoother animations"},
            {"*", "Faster HUD"},
    };

    // design-size layout (scaled down on short screens)
    private static final float BUTTON_W = 210;
    private static final float BUTTON_H = 30;
    private static final float BUTTON_GAP = 7;

    private final List<MenuButton> buttons = new ArrayList<>();
    private final Anim intro = new Anim(playedIntro ? 0.65F : 0.0F);
    private float fit = 1.0F;

    public NightMainMenu() {
        super(Text.literal("Night Client"));
    }

    private record MenuButton(String label, Runnable action, Anim hover) {
    }

    @Override
    protected void init() {
        this.buttons.clear();
        this.buttons.add(new MenuButton("Singleplayer", () -> this.client.setScreen(new SelectWorldScreen(this)), new Anim()));
        this.buttons.add(new MenuButton("Multiplayer", () -> this.client.setScreen(new MultiplayerScreen(this)), new Anim()));
        this.buttons.add(new MenuButton("Accounts", () -> this.client.setScreen(new me.night.alt.AltManagerScreen(this)), new Anim()));
        this.buttons.add(new MenuButton("Settings", () -> this.client.setScreen(new OptionsScreen(this, this.client.options)), new Anim()));
        this.buttons.add(new MenuButton("Quit Game", () -> this.client.scheduleStop(), new Anim()));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    private float logoCap() {
        return UiFont.HUGE.capHeight();
    }

    private float blockHeight() {
        return logoCap() + 18 + 34 + this.buttons.size() * (BUTTON_H + BUTTON_GAP) - BUTTON_GAP;
    }

    private float blockTop() {
        return this.height / 2.0F - blockHeight() / 2.0F - 4;
    }

    private float buttonY(int i) {
        return blockTop() + logoCap() + 18 + 34 + i * (BUTTON_H + BUTTON_GAP);
    }

    private double local(double v, double centre) {
        return centre + (v - centre) / this.fit;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float time = (System.nanoTime() - START) / 1.0E9F;
        int[] stops = Theme.stops();
        Render2D.aurora(ctx, 0, 0, this.width, this.height, time, stops[0], stops[1]);
        Render2D.rectV(ctx, 0, this.height * 0.5F, this.width, this.height * 0.5F, 0, 0x00000000, 0x80000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float in = this.intro.update(1.0F, 2.4F);
        playedIntro = true;
        float e = Anim.easeOutCubic(in);
        int accent = Theme.accent(0);
        float cx = this.width / 2.0F;
        float cy = this.height / 2.0F;

        // fit the logo + buttons block on short screens
        this.fit = Math.min(1.0F, (this.height - 56) / blockHeight());
        double mx = local(mouseX, cx);
        double my = local(mouseY, cy);

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.scale(this.fit, this.fit);
        m.translate(-cx, -cy);

        // ---- logo
        UiFont big = UiFont.HUGE;
        String logo = "Night";
        float logoW = big.width(logo);
        float logoY = blockTop() + (1 - e) * 12;
        Render2D.setAlpha(e);
        Render2D.shadow(ctx, cx - logoW / 2 + 8, logoY + 4, logoW - 16, logoCap() - 8, 14, 40, ColorUtil.withAlpha(accent, 0x5A));
        big.drawGradient(ctx, logo, cx - logoW / 2, logoY, i -> Theme.accent(i * 7), false);
        UiFont.SMALL.drawCentered(ctx, "C  L  I  E  N  T", cx, logoY + logoCap() + 12, 0xB4FFFFFF, false);

        // ---- buttons
        for (int i = 0; i < this.buttons.size(); i++) {
            MenuButton b = this.buttons.get(i);
            float slide = Anim.easeOutCubic(Anim.clamp01(in * 1.5F - 0.15F - i * 0.1F));
            float bx = cx - BUTTON_W / 2;
            float by = buttonY(i) + (1 - slide) * 16;
            boolean hovered = inside(mx, my, bx, buttonY(i), BUTTON_W, BUTTON_H);
            float hv = b.hover.update(hovered ? 1 : 0, 14);
            drawButton(ctx, b.label, bx, by, BUTTON_W, BUTTON_H, hv, e * slide, accent, i == 0);
        }
        m.popMatrix();

        // ---- footer
        Render2D.setAlpha(e);
        UiFont s = UiFont.SMALL;
        float fy = this.height - 9 - s.capHeight();
        s.draw(ctx, Night.CLIENT_NAME + " " + Night.CLIENT_VERSION, 10, fy, 0xC8FFFFFF, false);
        s.draw(ctx, "Minecraft " + SharedConstants.getGameVersion().name(), 10, fy - s.capHeight() - 6, 0x70FFFFFF, false);

        String user = this.client.getSession().getUsername();
        float uw = s.width(user) + 24;
        float px = this.width - 10 - uw;
        float py = this.height - 27;
        Render2D.rect(ctx, px, py, uw, 18, 9, 0x60000000);
        Render2D.outline(ctx, px, py, uw, 18, 9, 0.5F, 0x22FFFFFF);
        Render2D.shadow(ctx, px + 8, py + 7, 4, 4, 2, 4, 0x9040FF80);
        Render2D.circle(ctx, px + 10, py + 9, 2.5F, 0xFF4ADE80);
        s.draw(ctx, user, px + 18, s.centerY(py, 18), 0xFFE6E6EE, false);

        // ---- changelog card (wide screens only)
        if (this.width > 520) drawChangelog(ctx, 12, 12, e);

        Render2D.resetAlpha();
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawButton(DrawContext ctx, String label, float bx, float by, float bw, float bh, float hv, float alpha, int accent, boolean primary) {
        Render2D.setAlpha(alpha);
        float grow = hv * 2.5F;
        float x = bx - grow, y = by - grow * 0.3F, w = bw + grow * 2, h = bh + grow * 0.6F;
        float r = h / 2;

        Render2D.shadow(ctx, x, y + 3, w, h, r, 16, 0x55000000);
        if (hv > 0.01F) Render2D.shadow(ctx, x, y, w, h, r, 16, ColorUtil.withAlpha(accent, Math.round(0x55 * hv)));
        if (primary) {
            int a1 = ColorUtil.withAlpha(accent, Math.round(0x80 + 0x50 * hv));
            int a2 = ColorUtil.withAlpha(Theme.accentAlt(0), Math.round(0x80 + 0x50 * hv));
            Render2D.rectH(ctx, x, y, w, h, r, a1, a2);
        } else {
            Render2D.rect(ctx, x, y, w, h, r, ColorUtil.lerp(0x700E0E16, 0xA01C1C28, hv));
        }
        Render2D.rectV(ctx, x, y, w, h * 0.55F, r, 0x18FFFFFF, 0x00FFFFFF);
        int b1 = ColorUtil.lerp(0x26FFFFFF, ColorUtil.withAlpha(accent, 0xD0), hv);
        int b2 = ColorUtil.lerp(0x26FFFFFF, ColorUtil.withAlpha(Theme.accentAlt(0), 0xD0), hv);
        Render2D.outlineH(ctx, x, y, w, h, r, 0.75F, b1, b2);

        UiFont f = UiFont.MEDIUM;
        f.drawCentered(ctx, label, bx + bw / 2, f.centerY(y, h), primary ? 0xFFFFFFFF : ColorUtil.lerp(0xFFD8D8E4, 0xFFFFFFFF, hv), false);
    }

    private void drawChangelog(DrawContext ctx, float x, float y, float alpha) {
        Render2D.setAlpha(alpha);
        UiFont title = UiFont.BOLD;
        UiFont line = UiFont.SMALL;
        float w = 132;
        float h = 30 + CHANGELOG.length * 14;
        Render2D.shadow(ctx, x, y + 2, w, h, 10, 14, 0x50000000);
        Render2D.rect(ctx, x, y, w, h, 10, 0x8C0C0C14);
        Render2D.outline(ctx, x, y, w, h, 10, 0.5F, 0x22FFFFFF);
        title.draw(ctx, "What's new", x + 11, y + 11, 0xFFFFFFFF, false);
        Render2D.rect(ctx, x + w - 34, y + 9, 24, 11, 5.5F, ColorUtil.withAlpha(Theme.accent(0), 0x40));
        line.drawCentered(ctx, "v" + Night.CLIENT_VERSION, x + w - 22, line.centerY(y + 9, 11), 0xFFFFFFFF, false);
        float ly = y + 30;
        for (String[] entry : CHANGELOG) {
            boolean added = entry[0].equals("+");
            int dot = added ? 0xFF4ADE80 : 0xFF60A5FA;
            Render2D.circle(ctx, x + 14, ly + line.capHeight() / 2, 2, dot);
            line.draw(ctx, entry[1], x + 22, ly, 0xD0FFFFFF, false);
            ly += 14;
        }
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = local(click.x(), this.width / 2.0);
            double my = local(click.y(), this.height / 2.0);
            float cx = this.width / 2.0F;
            for (int i = 0; i < this.buttons.size(); i++) {
                if (inside(mx, my, cx - BUTTON_W / 2, buttonY(i), BUTTON_W, BUTTON_H)) {
                    ExhiSoundManager.playClick(1.0F);
                    this.buttons.get(i).action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }
}
