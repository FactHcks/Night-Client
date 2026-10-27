package me.night.module.modules.render;

import me.night.Night;
import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.ui.hud.HudPos;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import me.night.waypoint.WaypointManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Kills, deaths, streaks and playtime for the current server, with FPS and ping graphs. */
public class SessionStats extends Module {
    private static SessionStats INSTANCE;

    public final HudPos pos = new HudPos("Session", 0, 0.25);
    private final BooleanProperty mobs = new BooleanProperty("Count Mobs", "Count mob kills too, not just players", false);
    private final BooleanProperty fpsGraph = new BooleanProperty("FPS Graph", true);
    private final BooleanProperty pingGraph = new BooleanProperty("Ping Graph", true);

    private static final float W = 132;
    private static final int SAMPLES = 60;
    private static final long SAMPLE_MS = 250;
    /** A target that dies within this time after your last hit counts as your kill. */
    private static final long KILL_WINDOW_MS = 3000;

    // session data (also read by DiscordRPC)
    private static String sessionKey = "";
    private static long sessionStart = System.currentTimeMillis();
    private static int kills, deaths, streak, bestStreak;

    private final Map<LivingEntity, Long> attacked = new HashMap<>();
    private boolean wasDead;
    private final float[] fps = new float[SAMPLES];
    private final float[] ping = new float[SAMPLES];
    private int sampleCount;
    private long lastSample;

    public SessionStats() {
        super("SessionStats", "Kills, deaths, K/D, playtime and FPS / ping graphs", Category.RENDER);
        addProperties(mobs, fpsGraph, pingGraph, pos.px, pos.py);
        INSTANCE = this;
    }

    // ------------------------------------------------------------------ public data

    public static boolean active() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    public static int kills() {
        return kills;
    }

    public static int deaths() {
        return deaths;
    }

    /** When the current session started (epoch millis). */
    public static long sessionStart() {
        return sessionStart;
    }

    public static void reset() {
        sessionStart = System.currentTimeMillis();
        kills = deaths = streak = bestStreak = 0;
        if (INSTANCE != null) INSTANCE.attacked.clear();
    }

    /** Called when the player attacks an entity. */
    public static void onAttack(Entity target) {
        SessionStats s = INSTANCE;
        if (s == null || !s.isEnabled() || !(target instanceof LivingEntity living)) return;
        s.attacked.put(living, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ tracking

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.POST || mc.player == null || mc.world == null) return;

        // a new server or world starts a new session
        String key = WaypointManager.worldKey();
        if (!key.equals(sessionKey)) {
            sessionKey = key;
            reset();
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<LivingEntity, Long>> it = attacked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> e = it.next();
            LivingEntity ent = e.getKey();
            boolean recent = now - e.getValue() <= KILL_WINDOW_MS;
            if (ent.isDead() || ent.getHealth() <= 0) {
                if (recent && (mobs.getValue() || ent instanceof PlayerEntity)) {
                    kills++;
                    streak++;
                    bestStreak = Math.max(bestStreak, streak);
                }
                it.remove();
            } else if (!recent || ent.isRemoved()) {
                it.remove();
            }
        }

        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0;
        if (dead && !wasDead) {
            deaths++;
            streak = 0;
        }
        wasDead = dead;
    }

    private void sample() {
        long now = System.currentTimeMillis();
        if (now - lastSample < SAMPLE_MS) return;
        lastSample = now;
        System.arraycopy(fps, 1, fps, 0, SAMPLES - 1);
        System.arraycopy(ping, 1, ping, 0, SAMPLES - 1);
        fps[SAMPLES - 1] = mc.getCurrentFps();
        ping[SAMPLES - 1] = currentPing();
        sampleCount = Math.min(SAMPLES, sampleCount + 1);
    }

    private int currentPing() {
        if (mc.getNetworkHandler() == null || mc.player == null) return 0;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    private static String playtime() {
        long s = (System.currentTimeMillis() - sessionStart) / 1000;
        if (s >= 3600) return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ drawing

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        sample();
        DrawContext ctx = event.getContext();

        float graphH = 22;
        int graphs = (fpsGraph.getValue() ? 1 : 0) + (pingGraph.getValue() ? 1 : 0);
        float h = 22 + 2 * 24 + 4 + graphs * (graphH + 14) + 2;
        pos.place(W, h);
        float x = pos.x, y = pos.y;
        int accent = Theme.accent(0);
        Module hudMod = Night.INSTANCE.getModuleManager().getModule(HUD.class);
        boolean blur = hudMod instanceof HUD hud && hud.blur.getValue();

        Render2D.shadow(ctx, x, y + 2, W, h, 8, 12, 0x55000000);
        Render2D.glass(ctx, x, y, W, h, 8, 0x8C0C0C12, blur);
        Render2D.outline(ctx, x, y, W, h, 8, 0.5F, 0x22FFFFFF);

        // header: title and playtime
        UiFont bold = UiFont.BOLD;
        Render2D.circle(ctx, x + 10, y + 11, 2.2F, accent);
        bold.draw(ctx, "Session", x + 16, bold.centerY(y + 4, 14), 0xFFFFFFFF, false);
        String time = playtime();
        UiFont.SMALL.draw(ctx, time, x + W - 8 - UiFont.SMALL.width(time), UiFont.SMALL.centerY(y + 4, 14), 0xFF9A9AAA, false);
        Render2D.rectH(ctx, x + 8, y + 20, W - 16, 0.6F, 0.3F, ColorUtil.withAlpha(accent, 0x80), 0x00FFFFFF);

        // 2x2 stat tiles
        String kd = deaths == 0 ? String.valueOf(kills) : String.format("%.2f", kills / (float) deaths);
        float tw = (W - 16 - 4) / 2, th = 21;
        float ty = y + 24;
        tile(ctx, x + 8, ty, tw, th, "Kills", String.valueOf(kills), accent);
        tile(ctx, x + 12 + tw, ty, tw, th, "Deaths", String.valueOf(deaths), 0xFFFF5A6E);
        tile(ctx, x + 8, ty + th + 3, tw, th, "K/D", kd, Theme.accentAlt(0));
        tile(ctx, x + 12 + tw, ty + th + 3, tw, th, "Streak", streak + " / " + bestStreak, 0xFFFFC857);

        float gy = ty + th * 2 + 3 + 6;
        if (fpsGraph.getValue()) {
            graph(ctx, x + 8, gy, W - 16, graphH, "FPS", String.valueOf(mc.getCurrentFps()), fps, accent, Theme.accentAlt(0));
            gy += graphH + 14;
        }
        if (pingGraph.getValue()) {
            int p = currentPing();
            int col = p < 80 ? 0xFF6EE7A0 : p < 160 ? 0xFFFFC857 : 0xFFFF5A6E;
            graph(ctx, x + 8, gy, W - 16, graphH, "Ping", p + " ms", ping, col, col);
        }
    }

    private void tile(DrawContext ctx, float x, float y, float w, float h, String label, String value, int color) {
        Render2D.rect(ctx, x, y, w, h, 4, 0x18FFFFFF);
        Render2D.rect(ctx, x, y + 4, 1.5F, h - 8, 0.75F, color);
        UiFont.SMALL.draw(ctx, label, x + 6, y + 3, 0xFF8A8A9A, false);
        UiFont.MEDIUM.draw(ctx, value, x + 6, y + h - 3 - UiFont.MEDIUM.capHeight() - 1, 0xFFFFFFFF, false);
    }

    /** Label row, then a line graph of the samples (newest on the right) scaled to its own max. */
    private void graph(DrawContext ctx, float x, float y, float w, float h, String label, String value,
                       float[] data, int c1, int c2) {
        UiFont.SMALL.draw(ctx, label, x, y, 0xFF8A8A9A, false);
        UiFont.SMALL.draw(ctx, value, x + w - UiFont.SMALL.width(value), y, 0xFFFFFFFF, false);
        float gy = y + 11;
        Render2D.rect(ctx, x, gy, w, h, 4, 0x14FFFFFF);
        if (sampleCount < 2) return;

        float max = 1;
        for (int i = SAMPLES - sampleCount; i < SAMPLES; i++) max = Math.max(max, data[i]);
        max *= 1.15F;
        float step = w / (SAMPLES - 1);
        float prevX = 0, prevY = 0;
        for (int i = SAMPLES - sampleCount; i < SAMPLES; i++) {
            float px = x + i * step;
            float py = gy + h - 2 - (data[i] / max) * (h - 4);
            if (i > SAMPLES - sampleCount) {
                int a = ColorUtil.lerp(c1, c2, (i - 1) / (float) SAMPLES);
                int b = ColorUtil.lerp(c1, c2, i / (float) SAMPLES);
                Render2D.line(ctx, prevX, prevY, px, py, 1.2F, a, b);
            }
            prevX = px;
            prevY = py;
        }
        Render2D.circle(ctx, prevX, prevY, 1.8F, c2);
    }
}
