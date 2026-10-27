package me.night.module.modules.render;

import me.night.Night;
import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.music.AlbumArt;
import me.night.music.MediaSession;
import me.night.property.properties.BooleanProperty;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.ui.hud.HudEditorScreen;
import me.night.ui.hud.HudPos;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

/** Now-playing card for the music on your computer, with controls while chat is open. */
public class MusicPlayer extends Module {
    private static MusicPlayer INSTANCE;

    public final HudPos pos = new HudPos("Music", 0.5, 0.02);
    private final BooleanProperty art = new BooleanProperty("Album Art", true);
    private final BooleanProperty visualizer = new BooleanProperty("Visualizer", "Animated bars while music plays", true);

    private static final float W = 220;
    private static final float H = 52;

    // control buttons (screen coordinates from the last frame)
    private final float[][] buttons = new float[3][];
    private final Anim[] hover = {new Anim(), new Anim(), new Anim()};
    private final Anim show = new Anim(0);

    public MusicPlayer() {
        super("MusicPlayer", "Shows the song playing on your computer (Linux and Windows)", Category.RENDER);
        addProperties(art, visualizer, pos.px, pos.py);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        MediaSession.start();
    }

    @Override
    protected void onDisable() {
        MediaSession.stop();
    }

    /** Clicks on the control buttons while chat is open. Returns true if one was hit. */
    public static boolean handleClick(double mx, double my) {
        MusicPlayer mp = INSTANCE;
        if (mp == null || !mp.isEnabled() || MediaSession.current() == null) return false;
        String[] actions = {"previous", "play-pause", "next"};
        for (int i = 0; i < 3; i++) {
            float[] b = mp.buttons[i];
            if (b != null && mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                MediaSession.command(actions[i]);
                me.night.util.ExhiSoundManager.playClick(1.1F);
                return true;
            }
        }
        return false;
    }

    private static String time(long us) {
        long s = Math.max(0, us / 1_000_000L);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        DrawContext ctx = event.getContext();
        MediaSession.Track track = MediaSession.current();
        boolean preview = track == null && HudEditorScreen.isOpen();
        float s = show.update(track != null || preview ? 1 : 0, 8);
        for (int i = 0; i < 3; i++) buttons[i] = null;
        if (s < 0.01F) return;

        String title = track != null ? track.title() : MediaSession.available() ? "Nothing playing" : "Media info unavailable";
        String artist = track != null ? (track.artist().isBlank() ? "Unknown artist" : track.artist()) : "Music player";
        boolean playing = track != null && track.playing();

        pos.place(W, H);
        float x = pos.x, y = pos.y;
        int accent = Theme.accent(0);
        Module hudMod = Night.INSTANCE.getModuleManager().getModule(HUD.class);
        boolean blur = hudMod instanceof HUD hud && hud.blur.getValue();
        boolean chat = mc.currentScreen instanceof ChatScreen;

        Render2D.setAlpha(Anim.easeOutCubic(s));
        Render2D.shadow(ctx, x, y + 2, W, H, 9, 12, 0x55000000);
        if (playing) Render2D.shadow(ctx, x, y, W, H, 9, 10, ColorUtil.withAlpha(accent, 0x28));
        Render2D.glass(ctx, x, y, W, H, 9, 0x8C0C0C12, blur);
        Render2D.outline(ctx, x, y, W, H, 9, 0.5F, 0x22FFFFFF);

        // cover
        float ax = x + 6, ay = y + 6, as = H - 12;
        if (art.getValue() && track != null && AlbumArt.ready(track.artUrl())) {
            Render2D.image(ctx, AlbumArt.ID, ax, ay, as, as, 7, 0, 0, 1, 1, 0xFFFFFFFF, false);
        } else {
            Render2D.rect4(ctx, ax, ay, as, as, 7, accent, Theme.accentAlt(0), accent, Theme.accentAlt(0));
            // simple note made of shapes
            Render2D.circle(ctx, ax + as * 0.38F, ay + as * 0.66F, as * 0.12F, 0xE0FFFFFF);
            Render2D.rect(ctx, ax + as * 0.47F, ay + as * 0.26F, 1.6F, as * 0.42F, 0.8F, 0xE0FFFFFF);
            Render2D.line(ctx, ax + as * 0.5F, ay + as * 0.27F, ax + as * 0.68F, ay + as * 0.36F, 1.6F, 0xE0FFFFFF, 0xE0FFFFFF);
        }

        // text (long titles scroll)
        float tx = ax + as + 9;
        float right = x + W - 52;
        float avail = right - tx;
        UiFont tf = UiFont.MEDIUM;
        float tw = tf.width(title);
        if (tw > avail) {
            ctx.enableScissor(Math.round(tx), Math.round(y), Math.round(right), Math.round(y + H));
            float gap = 30;
            float off = (float) ((System.currentTimeMillis() / 1000.0 * 22) % (tw + gap));
            tf.draw(ctx, title, tx - off, y + 9, 0xFFFFFFFF, false);
            tf.draw(ctx, title, tx - off + tw + gap, y + 9, 0xFFFFFFFF, false);
            ctx.disableScissor();
        } else {
            tf.draw(ctx, title, tx, y + 9, 0xFFFFFFFF, false);
        }
        UiFont.SMALL.draw(ctx, UiFont.SMALL.trim(artist, avail), tx, y + 21, 0xFF9A9AAA, false);

        // progress
        float bx = tx, bw = x + W - 10 - tx, by = y + 34;
        long len = track != null ? track.lengthUs() : 0;
        long now = track != null ? track.positionNowUs() : 0;
        float ratio = len > 0 ? Math.min(1, now / (float) len) : 0;
        Render2D.rect(ctx, bx, by, bw, 3, 1.5F, 0x30FFFFFF);
        if (ratio > 0) {
            Render2D.shadow(ctx, bx, by, bw * ratio, 3, 1.5F, 4, ColorUtil.withAlpha(accent, 0x60));
            Render2D.rectH(ctx, bx, by, Math.max(3, bw * ratio), 3, 1.5F, accent, Theme.accentAlt(0));
        }
        if (len > 0) {
            UiFont.SMALL.draw(ctx, time(now), bx, by + 6, 0xFF8A8A9A, false);
            String total = time(len);
            UiFont.SMALL.draw(ctx, total, bx + bw - UiFont.SMALL.width(total), by + 6, 0xFF8A8A9A, false);
        }

        // right side: controls while chat is open, otherwise a little visualizer
        float cy = y + 15;
        if (chat && track != null) {
            float cx = x + W - 48;
            int mx = (int) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth());
            int my = (int) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight());
            for (int i = 0; i < 3; i++) {
                float bxx = cx + i * 14, byy = cy - 7;
                buttons[i] = new float[]{bxx, byy, 13, 14};
                boolean hov = mx >= bxx && mx <= bxx + 13 && my >= byy && my <= byy + 14;
                float hv = hover[i].update(hov ? 1 : 0, 16);
                if (hv > 0.01F) Render2D.circle(ctx, bxx + 6.5F, cy, 7, ColorUtil.withAlpha(0xFFFFFF, Math.round(0x22 * hv)));
                int ic = ColorUtil.lerp(0xFFD0D0DA, 0xFFFFFFFF, hv);
                float ix = bxx + 6.5F;
                if (i == 1) {
                    if (playing) {
                        Render2D.rect(ctx, ix - 2.8F, cy - 3.5F, 1.8F, 7, 0.9F, ic);
                        Render2D.rect(ctx, ix + 1.0F, cy - 3.5F, 1.8F, 7, 0.9F, ic);
                    } else {
                        triangle(ctx, ix - 2.2F, cy, 1, ic);
                    }
                } else {
                    int dir = i == 0 ? -1 : 1;
                    triangle(ctx, ix - 1.6F * dir, cy, dir, ic);
                    Render2D.rect(ctx, ix + 2.6F * dir - 0.7F, cy - 3.2F, 1.4F, 6.4F, 0.7F, ic);
                }
            }
        } else if (visualizer.getValue()) {
            double t = System.currentTimeMillis() / 1000.0;
            for (int i = 0; i < 4; i++) {
                float hgt = playing ? (float) (3 + 8 * Math.abs(Math.sin(t * (3.1 + i * 1.3) + i * 1.7))) : 2;
                float vx = x + W - 40 + i * 6;
                Render2D.rect(ctx, vx, cy + 6 - hgt, 3, hgt, 1.5F, Theme.accent(i * 10));
            }
        }
        Render2D.resetAlpha();
    }

    /** Small rounded triangle pointing left (dir -1) or right (dir 1). */
    private static void triangle(DrawContext ctx, float x, float cy, int dir, int color) {
        float w = 5.5F * dir, h = 3.6F;
        Render2D.line(ctx, x, cy - h, x, cy + h, 1.5F, color, color);
        Render2D.line(ctx, x, cy - h, x + w, cy, 1.5F, color, color);
        Render2D.line(ctx, x, cy + h, x + w, cy, 1.5F, color, color);
        Render2D.line(ctx, x + w * 0.25F, cy - h * 0.5F, x + w * 0.25F, cy + h * 0.5F, 2.4F, color, color);
    }
}
