package me.night.ui.notification;

import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.ui.hud.HudEditorScreen;
import me.night.ui.hud.HudPos;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small toasts that slide in when modules are toggled. */
public final class Notifications {
    public enum Kind { ENABLED, DISABLED, INFO }

    private static final long DURATION = 1800L;
    private static final int MAX = 4;
    private static final float TOAST_H = 22;
    private static final float GAP = 4;
    /** Size of the area the toasts live in (what the HUD editor moves). */
    private static final float AREA_W = 170;
    private static final float AREA_H = MAX * (TOAST_H + GAP) - GAP;

    private static final List<Entry> entries = new ArrayList<>();

    private Notifications() {
    }

    public static void push(String title, String text, Kind kind) {
        if (MinecraftClient.getInstance().player == null) return;
        synchronized (entries) {
            // replace an existing toast for the same title so spam-toggling doesn't stack
            entries.removeIf(e -> e.title.equals(title) && e.slide.get() > 0.5F && e.age() < DURATION);
            entries.add(0, new Entry(title, text, kind));
            while (entries.size() > MAX) entries.remove(entries.size() - 1);
        }
    }

    public static void render(DrawContext ctx, HudPos pos, boolean blur) {
        MinecraftClient mc = MinecraftClient.getInstance();
        pos.place(AREA_W, AREA_H);
        boolean right = pos.onRightSide();
        boolean bottom = pos.onBottomSide();
        float chatLift = bottom && mc.currentScreen instanceof ChatScreen ? 14 : 0;

        synchronized (entries) {
            if (entries.isEmpty() && HudEditorScreen.isOpen()) {
                // preview so the element can be seen and moved
                drawToast(ctx, blur, "Killaura", "Enabled", Kind.ENABLED, pos, right, bottom ? pos.y + AREA_H - TOAST_H : pos.y, 1, 0.6F);
                drawToast(ctx, blur, "Speed", "Disabled", Kind.DISABLED, pos, right,
                        bottom ? pos.y + AREA_H - TOAST_H * 2 - GAP : pos.y + TOAST_H + GAP, 1, 0.3F);
                return;
            }

            float cursor = 0;
            Iterator<Entry> it = entries.iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                long age = e.age();
                float s = e.slide.update(age < DURATION ? 1.0F : 0.0F, 12.0F);
                if (age >= DURATION && s <= 0.01F) {
                    it.remove();
                    continue;
                }
                float target = bottom ? pos.y + AREA_H - TOAST_H - cursor - chatLift : pos.y + cursor;
                if (e.y == null) e.y = new Anim(target);
                float y = e.y.update(target, 16.0F);
                String state = e.kind == Kind.ENABLED ? "Enabled" : e.kind == Kind.DISABLED ? "Disabled" : e.text;
                drawToast(ctx, blur, e.title, state, e.kind, pos, right, y, s, 1.0F - Math.min(1.0F, age / (float) DURATION));
                cursor += (TOAST_H + GAP) * Anim.easeOutCubic(s);
            }
        }
    }

    private static void drawToast(DrawContext ctx, boolean blur, String title, String state, Kind kind, HudPos pos, boolean right,
                                  float y, float show, float timeLeft) {
        UiFont nameFont = UiFont.MEDIUM;
        UiFont stateFont = UiFont.SMALL;
        float nameW = nameFont.width(title);
        float w = Math.min(AREA_W, 26 + nameW + 6 + stateFont.width(state) + 10);
        float h = TOAST_H;
        float ease = Anim.easeOutCubic(show);
        float x = right ? pos.x + AREA_W - w + (1 - ease) * (w + 16) : pos.x - (1 - ease) * (w + 16);

        Render2D.setAlpha(Math.min(1.0F, show * 1.5F));
        int accent = kind == Kind.DISABLED ? 0xFFFF5A6E : Theme.accent(0);
        float r = h / 2;
        Render2D.shadow(ctx, x, y + 2, w, h, r, 10, 0x60000000);
        Render2D.glass(ctx, x, y, w, h, r, 0xA80E0E14, blur);
        Render2D.outline(ctx, x, y, w, h, r, 0.5F, 0x1AFFFFFF);

        float dx = x + 13, dy = y + h / 2;
        Render2D.shadow(ctx, dx - 3, dy - 3, 6, 6, 3, 5, ColorUtil.withAlpha(accent, 0x90));
        Render2D.circle(ctx, dx, dy, 3, accent);

        float tx = x + 24;
        nameFont.draw(ctx, title, tx, nameFont.centerY(y, h), 0xFFF2F2F7, false);
        stateFont.draw(ctx, state, tx + nameW + 6, stateFont.centerY(y, h),
                kind == Kind.DISABLED ? 0xFFFF8A98 : ColorUtil.brighter(accent, 0.35F), false);

        if (timeLeft > 0) {
            Render2D.rect(ctx, x + r, y + h - 1.6F, (w - r * 2) * timeLeft, 1.0F, 0.5F, ColorUtil.withAlpha(accent, 0xB0));
        }
        Render2D.resetAlpha();
    }

    private static final class Entry {
        final String title;
        final String text;
        final Kind kind;
        final long start = System.currentTimeMillis();
        final Anim slide = new Anim(0.0F);
        Anim y;

        Entry(String title, String text, Kind kind) {
            this.title = title;
            this.text = text;
            this.kind = kind;
        }

        long age() {
            return System.currentTimeMillis() - this.start;
        }
    }
}
