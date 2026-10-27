package me.night.util;

import me.night.Night;
import me.night.module.Module;
import me.night.module.modules.render.HUD;

/** Single source for the client's colour palette (driven by the HUD theme setting). */
public final class Theme {
    // Surfaces
    public static final int BG = 0xFF111116;
    public static final int SIDEBAR = 0xFF0C0C10;
    public static final int CARD = 0xFF18181F;
    public static final int CARD_HOVER = 0xFF1E1E26;
    public static final int CARD_INNER = 0xFF141419;
    public static final int STROKE = 0x14FFFFFF;
    public static final int HUD_BG = 0xC00C0C11;

    // Text
    public static final int TEXT = 0xFFEDEDF3;
    public static final int TEXT_DIM = 0xFF9A9AAB;
    public static final int TEXT_MUTED = 0xFF5F5F70;

    private Theme() {
    }

    private static HUD hud() {
        if (Night.INSTANCE == null || Night.INSTANCE.getModuleManager() == null) return null;
        Module m = Night.INSTANCE.getModuleManager().getModule(HUD.class);
        return m instanceof HUD ? (HUD) m : null;
    }

    /** Animated accent colour; different indexes are offset along the gradient. */
    public static int accent(int index) {
        HUD hud = hud();
        return hud != null ? hud.getCurrentAccentColor(index) : NightRenderUtil.getNightColor(index, 1.0F);
    }

    /** First and last colour of the current theme. */
    public static int[] stops() {
        HUD hud = hud();
        return hud != null ? hud.getThemeStops() : new int[]{0xFF9D4EDD, 0xFF00C8FF};
    }

    /** The colour at the "other end" of the current gradient, for two-tone fills. */
    public static int accentAlt(int index) {
        return accent(index + 20);
    }
}
