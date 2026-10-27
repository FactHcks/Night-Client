package me.night.ui.glass;

import me.night.Night;
import me.night.module.modules.render.HUD;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.WeakHashMap;

/** Glass look for vanilla buttons (pause menu, server list, options...). */
public final class GlassWidgets {
    private static final Map<ClickableWidget, Anim> HOVER = new WeakHashMap<>();

    private GlassWidgets() {
    }

    public static boolean enabled() {
        HUD hud = Night.INSTANCE == null ? null : Night.INSTANCE.getModuleManager().getModule(HUD.class);
        return hud != null && hud.glassScreens.getValue();
    }

    public static void drawButton(DrawContext ctx, ClickableWidget w) {
        boolean hovered = w.active && (w.isHovered() || w.isFocused());
        float hv = HOVER.computeIfAbsent(w, k -> new Anim(0)).update(hovered ? 1 : 0, 16);
        float x = w.getX(), y = w.getY(), bw = w.getWidth(), bh = w.getHeight();
        float r = Math.min(6, bh / 2.0F);
        int accent = Theme.accent(0);

        Render2D.shadow(ctx, x, y + 1, bw, bh, r, 6, 0x40000000);
        Render2D.rect(ctx, x, y, bw, bh, r, !w.active ? 0x58101016 : ColorUtil.lerp(0x8C121219, 0xB8222230, hv));
        if (hv > 0.01F) {
            Render2D.rectH(ctx, x, y, bw, bh, r, ColorUtil.withAlpha(accent, Math.round(0x38 * hv)),
                    ColorUtil.withAlpha(Theme.accentAlt(0), Math.round(0x14 * hv)));
        }
        Render2D.rectV(ctx, x, y, bw, bh * 0.6F, r, 0x10FFFFFF, 0x00FFFFFF);
        Render2D.outline(ctx, x, y, bw, bh, r, 0.6F, ColorUtil.lerp(0x24FFFFFF, ColorUtil.withAlpha(accent, 0xC8), hv));

        Text msg = w.getMessage();
        UiFont f = UiFont.MEDIUM.width(msg) <= bw - 10 ? UiFont.MEDIUM : UiFont.SMALL;
        float tw = f.width(msg);
        int color = w.active ? ColorUtil.lerp(0xFFE2E2EA, 0xFFFFFFFF, hv) : 0xFF6E6E7C;
        f.drawText(ctx, msg, x + (bw - tw) / 2, f.centerY(y, bh), color, false);
    }
}
