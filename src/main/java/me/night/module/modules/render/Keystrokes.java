package me.night.module.modules.render;

import me.night.Night;
import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.ui.hud.HudPos;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** WASD, space and mouse buttons with clicks per second. */
public class Keystrokes extends Module {
    public final HudPos pos = new HudPos("Keystrokes", 0, 0.62);
    private final BooleanProperty cps = new BooleanProperty("CPS", "Clicks per second under the mouse buttons", true);
    private final BooleanProperty space = new BooleanProperty("Space Bar", true);

    private final Map<KeyBinding, Anim> press = new HashMap<>();
    private final Deque<Long> leftClicks = new ArrayDeque<>();
    private final Deque<Long> rightClicks = new ArrayDeque<>();
    private boolean wasLeft, wasRight;

    private static final float KEY = 22;
    private static final float GAP = 3;

    public Keystrokes() {
        super("Keystrokes", "Shows pressed keys and CPS", Category.RENDER);
        addProperties(cps, space, pos.px, pos.py);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        DrawContext ctx = event.getContext();
        long now = System.currentTimeMillis();

        // count clicks by watching the buttons go down
        boolean left = mc.options.attackKey.isPressed();
        boolean right = mc.options.useKey.isPressed();
        if (left && !wasLeft) leftClicks.add(now);
        if (right && !wasRight) rightClicks.add(now);
        wasLeft = left;
        wasRight = right;
        while (!leftClicks.isEmpty() && now - leftClicks.peekFirst() > 1000) leftClicks.pollFirst();
        while (!rightClicks.isEmpty() && now - rightClicks.peekFirst() > 1000) rightClicks.pollFirst();

        float w = KEY * 3 + GAP * 2;
        float mouseH = cps.getValue() ? KEY + 4 : KEY;
        float h = KEY * 2 + GAP + GAP + mouseH + (space.getValue() ? GAP + 11 : 0);
        pos.place(w, h);
        float x = pos.x, y = pos.y;

        Module hudMod = Night.INSTANCE.getModuleManager().getModule(HUD.class);
        boolean blur = hudMod instanceof HUD hud && hud.blur.getValue();

        key(ctx, mc.options.forwardKey, x + KEY + GAP, y, KEY, KEY, null, blur);
        float row2 = y + KEY + GAP;
        key(ctx, mc.options.leftKey, x, row2, KEY, KEY, null, blur);
        key(ctx, mc.options.backKey, x + KEY + GAP, row2, KEY, KEY, null, blur);
        key(ctx, mc.options.rightKey, x + (KEY + GAP) * 2, row2, KEY, KEY, null, blur);
        float row3 = row2 + KEY + GAP;
        float mw = (w - GAP) / 2;
        key(ctx, mc.options.attackKey, x, row3, mw, mouseH, cps.getValue() ? leftClicks.size() + " CPS" : null, blur);
        key(ctx, mc.options.useKey, x + mw + GAP, row3, mw, mouseH, cps.getValue() ? rightClicks.size() + " CPS" : null, blur);
        if (space.getValue()) {
            key(ctx, mc.options.jumpKey, x, row3 + mouseH + GAP, w, 11, "", blur);
        }
    }

    private void key(DrawContext ctx, KeyBinding binding, float x, float y, float w, float h, String sub, boolean blur) {
        float p = press.computeIfAbsent(binding, k -> new Anim()).update(binding.isPressed() ? 1 : 0, 20);
        int accent = Theme.accent(Math.round(x + y));
        Render2D.shadow(ctx, x, y + 1, w, h, 5, 6, 0x40000000);
        Render2D.glass(ctx, x, y, w, h, 5, 0x800C0C12, blur);
        if (p > 0.01F) {
            Render2D.shadow(ctx, x, y, w, h, 5, 6, ColorUtil.withAlpha(accent, Math.round(0x70 * p)));
            Render2D.rect(ctx, x, y, w, h, 5, ColorUtil.withAlpha(accent, Math.round(0xB0 * p)));
        }
        Render2D.outline(ctx, x, y, w, h, 5, 0.5F, ColorUtil.lerp(0x22FFFFFF, 0x60FFFFFF, p));

        if (sub != null && sub.isEmpty()) {
            // space bar: a short line instead of a label
            Render2D.rect(ctx, x + w / 2 - 10, y + h / 2 - 0.5F, 20, 1, 0.5F, ColorUtil.lerp(0xB0FFFFFF, 0xFFFFFFFF, p));
            return;
        }
        String label = labelOf(binding);
        UiFont f = UiFont.MEDIUM;
        int textCol = ColorUtil.lerp(0xFFE0E0EA, 0xFFFFFFFF, p);
        if (sub == null) {
            f.drawCentered(ctx, label, x + w / 2, f.centerY(y, h), textCol, false);
        } else {
            f.drawCentered(ctx, label, x + w / 2, y + 5, textCol, false);
            UiFont.SMALL.drawCentered(ctx, sub, x + w / 2, y + h - 4 - UiFont.SMALL.capHeight(), ColorUtil.lerp(0xFF9A9AAA, 0xFFFFFFFF, p), false);
        }
    }

    private String labelOf(KeyBinding binding) {
        if (binding == mc.options.attackKey) return "LMB";
        if (binding == mc.options.useKey) return "RMB";
        String s = binding.getBoundKeyLocalizedText().getString();
        return s.length() > 3 ? s.substring(0, 3) : s.toUpperCase();
    }
}
