package me.night.module.modules.render;

import me.night.Night;
import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.ui.hud.HudPos;
import me.night.ui.notification.Notifications;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.NightFont;
import me.night.util.NightRenderUtil;
import me.night.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HUD extends Module {
    /** Two-colour gradients for each theme ("Rainbow" and "Custom" are computed). */
    private static final java.util.LinkedHashMap<String, int[]> THEMES = new java.util.LinkedHashMap<>();

    static {
        THEMES.put("Night", new int[]{0xFF9D4EDD, 0xFF00C8FF});
        THEMES.put("Aqua", new int[]{0xFF3D8BFF, 0xFF4DF0E0});
        THEMES.put("Sunset", new int[]{0xFFFF4D8D, 0xFFFFA14D});
        THEMES.put("Fade", new int[]{0xFF9D4EDD, 0xFFDC82FF});
        THEMES.put("Rainbow", new int[]{0xFFFF5A8A, 0xFF5AC8FA});
        THEMES.put("White", new int[]{0xFFFFFFFF, 0xFF9A9AB0});
        THEMES.put("Ocean", new int[]{0xFF1565FF, 0xFF00D9C0});
        THEMES.put("Mint", new int[]{0xFF2BD99F, 0xFFB4F06E});
        THEMES.put("Ember", new int[]{0xFFFF3D3D, 0xFFFFB020});
        THEMES.put("Blush", new int[]{0xFFFF8FB1, 0xFFB28DFF});
        THEMES.put("Lavender", new int[]{0xFF9F7BFF, 0xFFF0B8FF});
        THEMES.put("Cherry", new int[]{0xFFFF2E63, 0xFFFF9EB5});
        THEMES.put("Gold", new int[]{0xFFFFA62B, 0xFFFFE58A});
        THEMES.put("Ice", new int[]{0xFF7FD8FF, 0xFFF2FBFF});
        THEMES.put("Matrix", new int[]{0xFF00FF85, 0xFF0B8F4E});
        THEMES.put("Neon", new int[]{0xFFFF00E5, 0xFF00F0FF});
        THEMES.put("Custom", new int[]{0xFF9D4EDD, 0xFFDC82FF});
    }

    public final ModeProperty theme = new ModeProperty("Theme", "", "Night", new java.util.ArrayList<>(THEMES.keySet()), () -> true);
    public final ModeProperty watermarkStyle = new ModeProperty("Watermark Style", "Modern", "Modern", "Card", "Classic");
    public final ModeProperty arrayListStyle = new ModeProperty("ArrayList Style", "Modern", "Modern", "Pills", "Classic");
    public final BooleanProperty watermark = new BooleanProperty("Watermark", true);
    public final BooleanProperty arrayList = new BooleanProperty("ArrayList", true);
    public final BooleanProperty coords = new BooleanProperty("Coords & BPS", true);
    public final BooleanProperty notifications = new BooleanProperty("Notifications", "Toast when a module is toggled", true);
    public final BooleanProperty glow = new BooleanProperty("Glow Shaders", "Soft ambient neon bloom", true);
    public final BooleanProperty rightBar = new BooleanProperty("Side Bar", "Accent bar on each ArrayList entry", true);
    public final BooleanProperty blur = new BooleanProperty("Blur", "Frosted glass behind HUD panels", true);
    public final ModeProperty scoreboardStyle = new ModeProperty("Scoreboard", "Modern", "Modern", "Vanilla");
    public final BooleanProperty scoreNumbers = new BooleanProperty("Score Numbers", "Show the red numbers on the scoreboard", false);
    public final BooleanProperty effects = new BooleanProperty("Effects", "Potion effects with timers", true);
    public final BooleanProperty glassScreens = new BooleanProperty("Glass Menus", "Glass buttons and chat in vanilla screens", true);
    public final BooleanProperty armor = new BooleanProperty("Armor", "Armour and held item with durability", true);
    public final NumberProperty speed = new NumberProperty("Speed", 15.0, 1.0, 30.0, 1.0);

    // movable positions (edited in the HUD editor, saved with configs)
    public final HudPos watermarkPos = new HudPos("Watermark", 0, 0);
    public final HudPos arrayListPos = new HudPos("ArrayList", 1, 0);
    public final HudPos infoPos = new HudPos("Info", 0, 1);
    public final HudPos notificationPos = new HudPos("Notifications", 1, 1);
    public final HudPos scoreboardPos = new HudPos("Scoreboard", 1, 0.5);
    public final HudPos effectsPos = new HudPos("Effects", 1, 0.80);
    public final HudPos armorPos = new HudPos("Armor", 0.5, 0.80);

    private final Map<Module, Anim> slideAnims = new HashMap<>();
    private final Map<Module, Anim> yAnims = new HashMap<>();

    public HUD() {
        super("HUD", "Watermark, module list, info and notifications", Category.RENDER);
        addProperties(theme, watermarkStyle, arrayListStyle, watermark, arrayList, coords, notifications, glow, blur, rightBar, speed,
                watermarkPos.px, watermarkPos.py, arrayListPos.px, arrayListPos.py,
                infoPos.px, infoPos.py, notificationPos.px, notificationPos.py,
                scoreboardStyle, scoreNumbers, effects, armor, glassScreens,
                scoreboardPos.px, scoreboardPos.py, effectsPos.px, effectsPos.py, armorPos.px, armorPos.py);
        setHidden(true);
        setEnabled(true);
    }

    public static boolean shouldHide(Module module) {
        return module instanceof HUD ||
               module instanceof ClickGui ||
               module instanceof TabGUI ||
               module instanceof TargetHUD ||
               module.isHidden();
    }

    public int getCurrentAccentColor() {
        return getCurrentAccentColor(0);
    }

    public int getCurrentAccentColor(int index) {
        String name = theme.getValue();
        float spd = speed.getFloat() / 15.0F;
        if (name.equalsIgnoreCase("Rainbow")) {
            float hue = (((System.currentTimeMillis() / 20.0F * spd) + index * 4.0F) % 360.0F) / 360.0F;
            return Color.HSBtoRGB(hue, 0.55F, 1.0F);
        }
        double time = (System.currentTimeMillis() / 1000.0) * spd + (index * 0.12);
        float wave = (float) ((Math.sin(time) + 1.0) / 2.0);
        int[] stops = getThemeStops(name);
        return ColorUtil.lerp(stops[0], stops[1], wave);
    }

    /** The two end colours of the current theme (used for backgrounds like the menu aurora). */
    public int[] getThemeStops() {
        return getThemeStops(theme.getValue());
    }

    /** End colours of any theme by name (used for theme previews). */
    public int[] getThemeStops(String themeName) {
        if (themeName.equalsIgnoreCase("Custom")) {
            int base = 0xFF9D4EDD;
            Module gui = Night.INSTANCE.getModuleManager().getModule(ClickGui.class);
            if (gui instanceof ClickGui) base = ((ClickGui) gui).accent.getRGB() | 0xFF000000;
            return new int[]{base, ColorUtil.brighter(base, 0.45F)};
        }
        for (java.util.Map.Entry<String, int[]> e : THEMES.entrySet()) {
            if (e.getKey().equalsIgnoreCase(themeName)) return e.getValue();
        }
        return THEMES.get("Night");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        DrawContext context = event.getContext();

        if (watermark.getValue()) {
            if (watermarkStyle.is("Modern")) {
                drawModernWatermark(context);
            } else if (watermarkStyle.is("Card")) {
                drawCardWatermark(context);
            } else {
                drawClassicWatermark(context);
            }
        }

        if (arrayList.getValue()) {
            if (arrayListStyle.is("Modern")) {
                drawConnectedArrayList(context);
            } else if (arrayListStyle.is("Pills")) {
                drawModernArrayList(context);
            } else {
                drawClassicArrayList(context);
            }
        }

        if (coords.getValue()) {
            drawInfo(context);
        }

        if (effects.getValue()) drawEffects(context);
        if (armor.getValue()) drawArmor(context);

        if (notifications.getValue()) {
            Notifications.render(context, notificationPos, blur.getValue());
        }
    }

    // ------------------------------------------------------------------ watermark

    private int getPing() {
        if (mc.getNetworkHandler() != null && mc.player != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) return entry.getLatency();
        }
        return 0;
    }

    private void drawModernWatermark(DrawContext context) {
        String logo = "Night";
        int logoW = NightFont.width(NightFont.Type.LARGE, logo);
        String version = "v" + Night.CLIENT_VERSION;
        watermarkPos.place(logoW + 3 + NightFont.width(NightFont.Type.SMALL, version), 16);
        float x = watermarkPos.x;
        float y = watermarkPos.y;

        if (glow.getValue()) {
            NightRenderUtil.shadow(context, x + 2, y + 3, logoW - 4, 10, 5, 14,
                    ColorUtil.withAlpha(Theme.accent(0), 0x38));
        }
        NightFont.drawGradient(context, NightFont.Type.LARGE, logo, x, NightFont.centerY(NightFont.Type.LARGE, y, 16),
                i -> Theme.accent(i * 6), false);

        // small version tag, top-right of the logo
        NightFont.draw(context, NightFont.Type.SMALL, version, x + logoW + 3, y - 1, 0xC8FFFFFF, false);
    }

    private void drawCardWatermark(DrawContext context) {
        String user = mc.player.getName().getString();
        String[] parts = {mc.getCurrentFps() + " fps", getPing() + " ms", user};

        float h = 18;
        int logoW = NightFont.width(NightFont.Type.BOLD, "Night");
        float w = 10 + logoW;
        for (String p : parts) w += 11 + NightFont.width(NightFont.Type.REGULAR, p);
        w += 8;
        watermarkPos.place(w, h);
        float x = watermarkPos.x;
        float y = watermarkPos.y;

        int accent = Theme.accent(0);
        if (glow.getValue()) {
            NightRenderUtil.shadow(context, x, y, w, h, 5, 10, ColorUtil.withAlpha(accent, 0x30));
        }
        NightRenderUtil.shadow(context, x, y, w, h, 5, 6, 0x40000000);
        NightRenderUtil.roundedRect(context, x, y, w, h, 5, 0xD80E0E13);
        NightRenderUtil.roundedOutline(context, x, y, w, h, 5, 0.5F, Theme.STROKE);
        NightRenderUtil.roundedGradient(context, x + 5, y, w - 10, 1, 0.5F, Theme.accent(0), Theme.accentAlt(0));

        float cx = x + 8;
        float ty = NightFont.centerY(NightFont.Type.BOLD, y, h);
        NightFont.drawGradient(context, NightFont.Type.BOLD, "Night", cx, ty, i -> Theme.accent(i * 6), false);
        cx += logoW + 5;
        for (String p : parts) {
            NightRenderUtil.circle(context, cx + 1, y + h / 2, 1.0F, Theme.TEXT_MUTED);
            cx += 6;
            NightFont.draw(context, NightFont.Type.REGULAR, p, cx, NightFont.centerY(NightFont.Type.REGULAR, y, h), Theme.TEXT, false);
            cx += NightFont.width(NightFont.Type.REGULAR, p) + 5;
        }
    }

    private void drawClassicWatermark(DrawContext context) {
        int colorXD = getCurrentAccentColor(0);
        String prefix = "§lN";
        String rest = "§light §7[§fv1.0.0§7] §7[§f" + mc.getCurrentFps() + " FPS§7] §7[§f" + getPing() + "ms§7]";
        int prefixW = mc.textRenderer.getWidth(prefix);
        watermarkPos.place(prefixW + mc.textRenderer.getWidth(rest), 9);
        int x = Math.round(watermarkPos.x);
        int y = Math.round(watermarkPos.y);
        context.drawTextWithShadow(mc.textRenderer, prefix, x, y, colorXD);
        context.drawTextWithShadow(mc.textRenderer, rest, x + prefixW, y, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ array list

    private String suffixOf(Module m) {
        return m.getSuffix();
    }

    private float entryTextWidth(Module m) {
        float w = UiFont.MEDIUM.width(m.getName());
        String s = suffixOf(m);
        if (s != null) w += 4 + UiFont.REGULAR.width(s);
        return w;
    }

    private void drawModernArrayList(DrawContext context) {
        final float lineH = 14;
        final float gap = 1.5F;
        final float radius = 4;
        boolean bar = rightBar.getValue();

        List<Module> shown = new ArrayList<>();
        for (Module m : Night.INSTANCE.getModuleManager().getModules()) {
            if (shouldHide(m)) continue;
            Anim a = slideAnims.computeIfAbsent(m, k -> new Anim(m.isEnabled() ? 1.0F : 0.0F));
            a.update(m.isEnabled() ? 1.0F : 0.0F, 13.0F);
            if (a.get() > 0.005F) shown.add(m);
        }
        boolean right = arrayListPos.onRightSide();
        boolean bottom = arrayListPos.onBottomSide();
        // longest entry nearest the screen edge
        shown.sort(Comparator.comparingDouble((Module m) -> bottom ? entryTextWidth(m) : -entryTextWidth(m)));

        float padL = bar && !right ? 9 : 6;
        float padR = bar && right ? 9 : 6;
        float maxW = 0;
        float totalH = 0;
        for (Module m : shown) {
            maxW = Math.max(maxW, entryTextWidth(m) + padL + padR);
            totalH += (lineH + gap) * Anim.easeOutCubic(slideAnims.get(m).get());
        }
        totalH = Math.max(0, totalH - gap);
        arrayListPos.place(Math.max(40, maxW), Math.max(lineH, totalH));
        float baseX = arrayListPos.x;
        float baseY = arrayListPos.y + (bottom ? arrayListPos.h - totalH : 0);
        float boxW = arrayListPos.w;

        int n = shown.size();
        float[] xs = new float[n], ys = new float[n], ws = new float[n], fs = new float[n];
        float cursor = 0;
        for (int i = 0; i < n; i++) {
            Module m = shown.get(i);
            float f = Anim.easeOutCubic(slideAnims.get(m).get());
            final float target = cursor;
            float w = entryTextWidth(m) + padL + padR;
            float slide = (1.0F - f) * (w + 16);
            xs[i] = right ? baseX + boxW - w + slide : baseX - slide;
            ys[i] = baseY + yAnims.computeIfAbsent(m, k -> new Anim(target)).update(target, 16.0F);
            ws[i] = w;
            fs[i] = f;
            cursor += (lineH + gap) * f;
        }

        // shadows and glow first, so they sit behind every pill
        for (int k = 0; k < n; k++) {
            Render2D.setAlpha(fs[k]);
            Render2D.shadow(context, xs[k], ys[k] + 1, ws[k], lineH, radius, 6, 0x45000000);
            if (glow.getValue()) {
                Render2D.shadow(context, xs[k], ys[k], ws[k], lineH, radius, 8, ColorUtil.withAlpha(Theme.accent(k * 12), 0x30));
            }
        }

        for (int k = 0; k < n; k++) {
            Module m = shown.get(k);
            int col = Theme.accent(k * 12);
            Render2D.setAlpha(fs[k]);
            Render2D.glass(context, xs[k], ys[k], ws[k], lineH, radius, 0x8C0C0C12, blur.getValue());
            Render2D.rectV(context, xs[k], ys[k], ws[k], lineH, radius, 0x10FFFFFF, 0x00FFFFFF);
            if (bar) {
                float bx = right ? xs[k] + ws[k] - 4.5F : xs[k] + 3;
                Render2D.shadow(context, bx, ys[k] + 3.5F, 1.5F, lineH - 7, 0.75F, 3, ColorUtil.withAlpha(col, 0x90));
                Render2D.rect(context, bx, ys[k] + 3.5F, 1.5F, lineH - 7, 0.75F, col);
            }
            float tx = xs[k] + padL;
            UiFont.MEDIUM.draw(context, m.getName(), tx, UiFont.MEDIUM.centerY(ys[k], lineH), col, false);
            String suffix = suffixOf(m);
            if (suffix != null) {
                float nameW = UiFont.MEDIUM.width(m.getName());
                UiFont.REGULAR.draw(context, suffix, tx + nameW + 4, UiFont.REGULAR.centerY(ys[k], lineH), 0xFF9A9AAA, false);
            }
        }
        Render2D.resetAlpha();
    }

    /** One connected block: rows sit flush like steps, with a single gradient bar along the screen edge. */
    private void drawConnectedArrayList(DrawContext context) {
        final float lineH = 13;
        final float barW = 2;
        boolean bar = rightBar.getValue();

        List<Module> shown = new ArrayList<>();
        for (Module m : Night.INSTANCE.getModuleManager().getModules()) {
            if (shouldHide(m)) continue;
            Anim a = slideAnims.computeIfAbsent(m, k -> new Anim(m.isEnabled() ? 1.0F : 0.0F));
            a.update(m.isEnabled() ? 1.0F : 0.0F, 13.0F);
            if (a.get() > 0.005F) shown.add(m);
        }
        boolean right = arrayListPos.onRightSide();
        boolean bottom = arrayListPos.onBottomSide();
        shown.sort(Comparator.comparingDouble((Module m) -> bottom ? entryTextWidth(m) : -entryTextWidth(m)));

        float edge = bar ? barW : 0;
        float padIn = 5, padOut = 4;
        float maxW = 0, totalH = 0;
        for (Module m : shown) {
            maxW = Math.max(maxW, entryTextWidth(m) + padIn + padOut + edge);
            totalH += lineH * Anim.easeOutCubic(slideAnims.get(m).get());
        }
        arrayListPos.place(Math.max(40, maxW), Math.max(lineH, totalH));
        float baseX = arrayListPos.x;
        float baseY = arrayListPos.y + (bottom ? arrayListPos.h - totalH : 0);
        float boxW = arrayListPos.w;

        int n = shown.size();
        if (n == 0) return;
        float[] xs = new float[n], ys = new float[n], ws = new float[n], fs = new float[n];
        float cursor = 0;
        for (int i = 0; i < n; i++) {
            Module m = shown.get(i);
            float f = Anim.easeOutCubic(slideAnims.get(m).get());
            final float target = cursor;
            float w = entryTextWidth(m) + padIn + padOut + edge;
            float slide = (1.0F - f) * (w + 8);
            xs[i] = right ? baseX + boxW - w + slide : baseX - slide;
            ys[i] = baseY + yAnims.computeIfAbsent(m, k -> new Anim(target)).update(target, 16.0F);
            ws[i] = w;
            fs[i] = f;
            cursor += lineH * f;
        }

        // rows: square, flush against each other so the list reads as one block
        for (int k = 0; k < n; k++) {
            Module m = shown.get(k);
            int col = Theme.accent(k * 12);
            float rowX = right ? xs[k] : xs[k] + edge;
            float rowW = ws[k] - edge;
            Render2D.setAlpha(fs[k]);
            Render2D.glass(context, rowX, ys[k], rowW, lineH, 0, 0x990C0C12, blur.getValue());
            float tx = right ? rowX + padOut : rowX + padIn;
            if (right) tx = rowX + rowW - padIn - entryTextWidth(m);
            UiFont.MEDIUM.draw(context, m.getName(), tx, UiFont.MEDIUM.centerY(ys[k], lineH), col, false);
            String suffix = suffixOf(m);
            if (suffix != null) {
                float nameW = UiFont.MEDIUM.width(m.getName());
                UiFont.REGULAR.draw(context, suffix, tx + nameW + 4, UiFont.REGULAR.centerY(ys[k], lineH), 0xFF9A9AAA, false);
            }
        }

        // the side bar runs down the screen edge, fading between each row's colour
        if (bar) {
            float bx = right ? baseX + boxW - barW : baseX;
            for (int k = 0; k < n; k++) {
                float bh = lineH * fs[k];
                if (bh < 0.5F) continue;
                int top = Theme.accent(k * 12), bottomCol = Theme.accent((k + 1) * 12);
                Render2D.setAlpha(1);
                if (glow.getValue()) Render2D.shadow(context, bx, ys[k], barW, bh, 0, 5, ColorUtil.withAlpha(top, 0x70));
                Render2D.rectV(context, bx, ys[k], barW, bh, 0, top, bottomCol);
            }
        }
        Render2D.resetAlpha();
    }

    private void drawClassicArrayList(DrawContext context) {
        List<Module> active = new ArrayList<>();
        for (Module m : Night.INSTANCE.getModuleManager().getEnabledModules()) {
            if (!shouldHide(m)) active.add(m);
        }
        active.sort(Comparator.comparingInt((Module m) ->
                -mc.textRenderer.getWidth(m.getName() + (m.getSuffix() != null ? " " + m.getSuffix() : ""))));

        int maxW = 0;
        for (Module m : active) {
            maxW = Math.max(maxW, mc.textRenderer.getWidth(m.getName() + (m.getSuffix() != null ? " " + m.getSuffix() : "")));
        }
        arrayListPos.place(maxW + 3, Math.max(10, active.size() * 10));
        boolean right = arrayListPos.onRightSide();
        int y = Math.round(arrayListPos.y);

        for (int i = 0; i < active.size(); i++) {
            Module m = active.get(i);
            String suffix = (m.getSuffix() != null) ? " §7" + m.getSuffix() : "";
            String fullText = m.getName() + suffix;
            int width = mc.textRenderer.getWidth(fullText);
            int edge = Math.round(arrayListPos.x + (right ? arrayListPos.w : 0));
            int x = right ? edge - width - 3 : edge + 3;
            int col = getCurrentAccentColor(i * 12);
            context.drawTextWithShadow(mc.textRenderer, fullText, x, y, col);
            if (rightBar.getValue()) {
                int bx = right ? edge - 1 : edge;
                context.fill(bx, y, bx + 1, y + 9, col);
            }
            y += 10;
        }
    }

    // ------------------------------------------------------------------ scoreboard

    public boolean replacesScoreboard() {
        return isEnabled() && scoreboardStyle.is("Modern");
    }

    public boolean replacesEffects() {
        return isEnabled() && effects.getValue();
    }

    /** Glass sidebar scoreboard (called instead of the vanilla one). */
    public void drawScoreboard(DrawContext ctx, net.minecraft.scoreboard.ScoreboardObjective objective) {
        net.minecraft.scoreboard.Scoreboard sb = objective.getScoreboard();
        net.minecraft.scoreboard.number.NumberFormat nf = objective.getNumberFormatOr(net.minecraft.scoreboard.number.StyledNumberFormat.RED);
        List<net.minecraft.scoreboard.ScoreboardEntry> entries = sb.getScoreboardEntries(objective).stream()
                .filter(e -> !e.hidden())
                .sorted(Comparator.comparingInt(net.minecraft.scoreboard.ScoreboardEntry::value).reversed()
                        .thenComparing(net.minecraft.scoreboard.ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER))
                .limit(15).toList();

        UiFont f = UiFont.REGULAR;
        UiFont tf = UiFont.BOLD;
        float lineH = 11;
        net.minecraft.text.Text title = objective.getDisplayName();
        List<net.minecraft.text.Text> names = new ArrayList<>();
        List<net.minecraft.text.Text> scores = new ArrayList<>();
        float w = tf.width(title);
        for (var e : entries) {
            net.minecraft.text.Text name = net.minecraft.scoreboard.Team.decorateName(sb.getScoreHolderTeam(e.owner()), e.name());
            net.minecraft.text.Text score = e.formatted(nf);
            names.add(name);
            scores.add(score);
            w = Math.max(w, f.width(name) + (scoreNumbers.getValue() ? 10 + f.width(score) : 0));
        }
        w += 16;
        float h = 24 + entries.size() * lineH + 4;
        scoreboardPos.place(w, h);
        float x = scoreboardPos.x, y = scoreboardPos.y;

        Render2D.shadow(ctx, x, y + 2, w, h, 7, 10, 0x50000000);
        Render2D.glass(ctx, x, y, w, h, 7, 0x8C0C0C12, blur.getValue());
        Render2D.outline(ctx, x, y, w, h, 7, 0.5F, 0x22FFFFFF);
        tf.drawText(ctx, title, x + (w - tf.width(title)) / 2, y + 7, 0xFFFFFFFF, false);
        Render2D.rectH(ctx, x + 8, y + 19, w - 16, 1, 0.5F, Theme.accent(0), Theme.accentAlt(0));
        float ly = y + 24;
        for (int i = 0; i < names.size(); i++) {
            f.drawText(ctx, names.get(i), x + 8, f.centerY(ly, lineH), 0xFFE6E6EE, false);
            if (scoreNumbers.getValue()) {
                net.minecraft.text.Text sc = scores.get(i);
                f.drawText(ctx, sc, x + w - 8 - f.width(sc), f.centerY(ly, lineH), 0xFFFF5A6E, false);
            }
            ly += lineH;
        }
    }

    // ------------------------------------------------------------------ effects and armour

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private void drawEffects(DrawContext ctx) {
        var list = new ArrayList<>(mc.player.getStatusEffects());
        boolean preview = list.isEmpty() && me.night.ui.hud.HudEditorScreen.isOpen();
        if (list.isEmpty() && !preview) return;

        UiFont nf = UiFont.MEDIUM;
        UiFont sf = UiFont.SMALL;
        float rowH = 18;
        List<String> names = new ArrayList<>();
        List<String> times = new ArrayList<>();
        if (preview) {
            names.add("Speed II");
            times.add("1:30");
            names.add("Strength");
            times.add("0:45");
        } else {
            list.sort(Comparator.comparingInt(net.minecraft.entity.effect.StatusEffectInstance::getDuration).reversed());
            for (var inst : list) {
                String name = inst.getEffectType().value().getName().getString();
                if (inst.getAmplifier() > 0) name += " " + roman(inst.getAmplifier() + 1);
                names.add(name);
                if (inst.isInfinite()) {
                    times.add("--:--");
                } else {
                    int sec = inst.getDuration() / 20;
                    times.add(String.format("%d:%02d", sec / 60, sec % 60));
                }
            }
        }
        float w = 0;
        for (int i = 0; i < names.size(); i++) w = Math.max(w, nf.width(names.get(i)) + 8 + sf.width(times.get(i)));
        w += 34;
        float h = names.size() * rowH + 6;
        effectsPos.place(w, h);
        float x = effectsPos.x, y = effectsPos.y;

        Render2D.shadow(ctx, x, y + 2, w, h, 7, 10, 0x50000000);
        Render2D.glass(ctx, x, y, w, h, 7, 0x8C0C0C12, blur.getValue());
        Render2D.outline(ctx, x, y, w, h, 7, 0.5F, 0x22FFFFFF);
        float ry = y + 3;
        for (int i = 0; i < names.size(); i++) {
            int color = preview ? Theme.accent(i * 20) : 0xFF000000 | list.get(i).getEffectType().value().getColor();
            if (!preview) {
                ctx.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                        net.minecraft.client.gui.hud.InGameHud.getEffectTexture(list.get(i).getEffectType()),
                        Math.round(x + 7), Math.round(ry + 2), 14, 14);
            } else {
                Render2D.circle(ctx, x + 14, ry + rowH / 2, 5, color);
            }
            nf.draw(ctx, names.get(i), x + 26, nf.centerY(ry, rowH), ColorUtil.lerp(color, 0xFFFFFFFF, 0.55F), false);
            sf.draw(ctx, times.get(i), x + w - 8 - sf.width(times.get(i)), sf.centerY(ry, rowH), 0xFF9A9AAA, false);
            ry += rowH;
        }
    }

    private void drawArmor(DrawContext ctx) {
        List<net.minecraft.item.ItemStack> stacks = new ArrayList<>();
        for (var slot : new net.minecraft.entity.EquipmentSlot[]{net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
            var s = mc.player.getEquippedStack(slot);
            if (!s.isEmpty()) stacks.add(s);
        }
        if (!mc.player.getMainHandStack().isEmpty()) stacks.add(mc.player.getMainHandStack());
        boolean preview = stacks.isEmpty() && me.night.ui.hud.HudEditorScreen.isOpen();
        if (stacks.isEmpty() && !preview) return;

        int count = preview ? 5 : stacks.size();
        float cell = 20;
        float w = count * cell + 6;
        float h = 24;
        armorPos.place(w, h);
        float x = armorPos.x, y = armorPos.y;
        Render2D.shadow(ctx, x, y + 2, w, h, 7, 10, 0x50000000);
        Render2D.glass(ctx, x, y, w, h, 7, 0x8C0C0C12, blur.getValue());
        Render2D.outline(ctx, x, y, w, h, 7, 0.5F, 0x22FFFFFF);
        for (int i = 0; i < count; i++) {
            float cx = x + 3 + i * cell;
            if (preview) {
                Render2D.rect(ctx, cx + 3, y + 4, 14, 14, 3, 0x30FFFFFF);
                continue;
            }
            var stack = stacks.get(i);
            ctx.drawItem(stack, Math.round(cx + 2), Math.round(y + 2));
            if (stack.isDamageable()) {
                float left = 1 - stack.getDamage() / (float) Math.max(1, stack.getMaxDamage());
                Render2D.rect(ctx, cx + 3, y + h - 4, 14, 1.5F, 0.75F, 0x40000000);
                Render2D.rect(ctx, cx + 3, y + h - 4, 14 * left, 1.5F, 0.75F, ColorUtil.lerp(0xFFFF4D5E, 0xFF4ADE80, left));
            } else if (stack.getCount() > 1) {
                String c = String.valueOf(stack.getCount());
                UiFont.SMALL.draw(ctx, c, cx + 18 - UiFont.SMALL.width(c), y + h - 3 - UiFont.SMALL.capHeight(), 0xFFFFFFFF, true);
            }
        }
    }

    // ------------------------------------------------------------------ info

    private void drawInfo(DrawContext context) {
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        float h = 16;

        double dx = mc.player.getX() - mc.player.lastRenderX;
        double dz = mc.player.getZ() - mc.player.lastRenderZ;
        String[][] items = {
                {"XYZ", String.format("%d, %d, %d", (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ())},
                {"BPS", String.format("%.2f", Math.hypot(dx, dz) * 20.0)},
                {"FPS", String.valueOf(mc.getCurrentFps())}
        };

        float w = 6;
        for (String[] it : items) {
            w += NightFont.width(NightFont.Type.SMALL, it[0]) + 3 + NightFont.width(NightFont.Type.REGULAR, it[1]) + 9;
        }
        w -= 3;
        infoPos.place(w, h);
        float x = infoPos.x;
        float y = infoPos.y - (chatOpen && infoPos.onBottomSide() ? 14 : 0);

        NightRenderUtil.shadow(context, x, y, w, h, 5, 6, 0x40000000);
        Render2D.glass(context, x, y, w, h, 5, 0x900C0C12, blur.getValue());
        NightRenderUtil.roundedOutline(context, x, y, w, h, 5, 0.5F, Theme.STROKE);

        float cx = x + 6;
        for (int i = 0; i < items.length; i++) {
            String label = items[i][0];
            String value = items[i][1];
            NightFont.draw(context, NightFont.Type.SMALL, label, cx, NightFont.centerY(NightFont.Type.SMALL, y, h), Theme.accent(i * 8), false);
            cx += NightFont.width(NightFont.Type.SMALL, label) + 3;
            NightFont.draw(context, NightFont.Type.REGULAR, value, cx, NightFont.centerY(NightFont.Type.REGULAR, y, h), Theme.TEXT, false);
            cx += NightFont.width(NightFont.Type.REGULAR, value) + 9;
        }
    }
}
