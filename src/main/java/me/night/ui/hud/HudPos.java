package me.night.ui.hud;

import me.night.property.properties.NumberProperty;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Screen position of a movable HUD element.
 * <p>
 * The position is stored as two fractions (0..1) of the free space on screen, so 0 hugs the
 * left/top edge, 1 hugs the right/bottom edge and 0.5 is centred, whatever the element's size
 * or the GUI scale. The values live in hidden module settings, so configs save them.
 */
public final class HudPos {
    public static final float MARGIN = 5;
    private static final List<HudPos> ALL = new ArrayList<>();

    public final String name;
    public final NumberProperty px;
    public final NumberProperty py;
    private final double defaultX;
    private final double defaultY;

    // where the element was last drawn (screen GUI units)
    public float x, y, w, h;
    private long lastDrawn;

    public HudPos(String name, double defaultX, double defaultY) {
        this.name = name;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.px = new NumberProperty(name + " X", "", defaultX, 0, 1, 0.0001, () -> false);
        this.py = new NumberProperty(name + " Y", "", defaultY, 0, 1, 0.0001, () -> false);
        ALL.add(this);
    }

    public static List<HudPos> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static float screenW() {
        return MinecraftClient.getInstance().getWindow().getScaledWidth();
    }

    private static float screenH() {
        return MinecraftClient.getInstance().getWindow().getScaledHeight();
    }

    /** Works out the top-left corner for an element of this size and records its bounds. */
    public void place(float width, float height) {
        this.w = width;
        this.h = height;
        this.x = MARGIN + (float) this.px.getValue().doubleValue() * Math.max(0, screenW() - width - MARGIN * 2);
        this.y = MARGIN + (float) this.py.getValue().doubleValue() * Math.max(0, screenH() - height - MARGIN * 2);
        this.lastDrawn = System.currentTimeMillis();
    }

    /** Moves the element so its top-left corner is at (x, y). */
    public void moveTo(float x, float y) {
        float freeW = Math.max(1, screenW() - this.w - MARGIN * 2);
        float freeH = Math.max(1, screenH() - this.h - MARGIN * 2);
        this.px.setValue((double) Math.max(0, Math.min(1, (x - MARGIN) / freeW)));
        this.py.setValue((double) Math.max(0, Math.min(1, (y - MARGIN) / freeH)));
    }

    public void reset() {
        this.px.setValue(this.defaultX);
        this.py.setValue(this.defaultY);
    }

    public boolean onRightSide() {
        return this.px.getValue() >= 0.5;
    }

    public boolean onBottomSide() {
        return this.py.getValue() >= 0.5;
    }

    /** True if the element was drawn in the last moment (it is enabled and visible). */
    public boolean isShowing() {
        return System.currentTimeMillis() - this.lastDrawn < 250;
    }
}
