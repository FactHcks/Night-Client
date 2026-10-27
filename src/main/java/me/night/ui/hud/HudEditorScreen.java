package me.night.ui.hud;

import me.night.Night;
import me.night.config.ConfigManager;
import me.night.module.Module;
import me.night.module.modules.render.ClickGui;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.ExhiSoundManager;
import me.night.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Drag HUD elements around the screen. Positions snap to the centre lines and edges. */
public class HudEditorScreen extends Screen {
    private static final float SNAP = 5;

    private final boolean returnToClickGui;
    private final Anim fade = new Anim(0);
    private final Map<HudPos, Anim> hover = new HashMap<>();

    private HudPos dragging;
    private float grabX, grabY;
    private boolean snapX, snapY;

    // reset-all button bounds (from the last frame)
    private float resetX, resetY, resetW, resetH;

    public HudEditorScreen(boolean returnToClickGui) {
        super(Text.literal("HUD Editor"));
        this.returnToClickGui = returnToClickGui;
    }

    public static boolean isOpen() {
        return MinecraftClient.getInstance().currentScreen instanceof HudEditorScreen;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // keep the HUD visible: just a light tint, no blur
        ctx.fill(0, 0, this.width, this.height, ColorUtil.withAlpha(0x000000, Math.round(0x38 * this.fade.get())));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float f = this.fade.update(1, 10);
        Render2D.setAlpha(f);
        int accent = Theme.accent(0);
        float cx = this.width / 2.0F;
        float cy = this.height / 2.0F;

        // centre guides (bright while snapped)
        int guide = 0x1CFFFFFF;
        Render2D.rect(ctx, cx - 0.25F, 0, 0.5F, this.height, 0, this.dragging != null && this.snapX ? ColorUtil.withAlpha(accent, 0xD0) : guide);
        Render2D.rect(ctx, 0, cy - 0.25F, this.width, 0.5F, 0, this.dragging != null && this.snapY ? ColorUtil.withAlpha(accent, 0xD0) : guide);

        List<HudPos> all = HudPos.all();
        HudPos hovered = this.dragging != null ? this.dragging : elementAt(mouseX, mouseY);
        int showing = 0;
        for (HudPos p : all) {
            if (!p.isShowing()) continue;
            showing++;
            float hv = this.hover.computeIfAbsent(p, k -> new Anim()).update(p == hovered ? 1 : 0, 16);
            boolean active = p == this.dragging;
            float x = p.x - 3, y = p.y - 3, w = p.w + 6, h = p.h + 6;
            Render2D.rect(ctx, x, y, w, h, 5, ColorUtil.withAlpha(accent, Math.round(0x10 + 0x18 * hv)));
            Render2D.outline(ctx, x, y, w, h, 5, active ? 1.0F : 0.75F, ColorUtil.withAlpha(accent, Math.round(0x70 + 0x8F * hv)));

            // name tag (above the box, or below it when at the top of the screen)
            UiFont tf = UiFont.SMALL;
            String label = p.name;
            float tw = tf.width(label) + 10;
            float th = 12;
            float tx = Math.max(2, Math.min(this.width - tw - 2, x));
            float ty = y - th - 3 < 2 ? y + h + 3 : y - th - 3;
            Render2D.setAlpha(f * (0.55F + 0.45F * hv));
            Render2D.rect(ctx, tx, ty, tw, th, 6, ColorUtil.withAlpha(accent, 0xE0));
            tf.draw(ctx, label, tx + 5, tf.centerY(ty, th), 0xFFFFFFFF, false);
            Render2D.setAlpha(f);
        }

        // help bar
        UiFont font = UiFont.SMALL;
        String help = showing == 0 ? "Join a world to edit the HUD" : "Drag to move  ·  Right-click to reset  ·  Esc when done";
        float hw = font.width(help) + 24;
        String reset = "Reset all";
        this.resetW = UiFont.MEDIUM.width(reset) + 18;
        this.resetH = 20;
        float total = hw + 6 + this.resetW;
        float bx = cx - total / 2;
        float by = this.height - 34;
        Render2D.shadow(ctx, bx, by, hw, 20, 10, 12, 0x60000000);
        Render2D.rect(ctx, bx, by, hw, 20, 10, 0xE00E0E14);
        Render2D.outline(ctx, bx, by, hw, 20, 10, 0.5F, 0x22FFFFFF);
        font.draw(ctx, help, bx + 12, font.centerY(by, 20), 0xFFD8D8E2, false);

        this.resetX = bx + hw + 6;
        this.resetY = by;
        boolean resetHover = inside(mouseX, mouseY, this.resetX, this.resetY, this.resetW, this.resetH);
        Render2D.shadow(ctx, this.resetX, by, this.resetW, 20, 10, 12, 0x60000000);
        Render2D.rect(ctx, this.resetX, by, this.resetW, 20, 10, resetHover ? 0xF02A1418 : 0xE00E0E14);
        Render2D.outline(ctx, this.resetX, by, this.resetW, 20, 10, 0.5F, resetHover ? 0x90FF5A6E : 0x22FFFFFF);
        UiFont.MEDIUM.draw(ctx, reset, this.resetX + 9, UiFont.MEDIUM.centerY(by, 20), resetHover ? 0xFFFF8A98 : 0xFFE6E6EE, false);

        Render2D.resetAlpha();
    }

    private HudPos elementAt(double mx, double my) {
        List<HudPos> all = HudPos.all();
        HudPos best = null;
        float bestArea = Float.MAX_VALUE;
        for (HudPos p : all) {
            if (!p.isShowing()) continue;
            if (inside(mx, my, p.x - 3, p.y - 3, p.w + 6, p.h + 6)) {
                float area = p.w * p.h; // prefer the smallest element under the mouse
                if (area < bestArea) {
                    bestArea = area;
                    best = p;
                }
            }
        }
        return best;
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() == 0 && inside(mx, my, this.resetX, this.resetY, this.resetW, this.resetH)) {
            for (HudPos p : HudPos.all()) p.reset();
            ExhiSoundManager.playClick(0.8F);
            return true;
        }
        HudPos p = elementAt(mx, my);
        if (p == null) return super.mouseClicked(click, doubled);
        if (click.button() == 1) {
            p.reset();
            ExhiSoundManager.playClick(0.8F);
            return true;
        }
        if (click.button() == 0) {
            this.dragging = p;
            this.grabX = (float) mx - p.x;
            this.grabY = (float) my - p.y;
            ExhiSoundManager.playClick(1.1F);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (this.dragging == null) return super.mouseDragged(click, deltaX, deltaY);
        HudPos p = this.dragging;
        float x = (float) click.x() - this.grabX;
        float y = (float) click.y() - this.grabY;

        // snap to the centre lines
        this.snapX = Math.abs(x + p.w / 2 - this.width / 2.0F) < SNAP;
        this.snapY = Math.abs(y + p.h / 2 - this.height / 2.0F) < SNAP;
        if (this.snapX) x = this.width / 2.0F - p.w / 2;
        if (this.snapY) y = this.height / 2.0F - p.h / 2;
        // snap to the screen edges
        if (x < HudPos.MARGIN + SNAP) x = HudPos.MARGIN;
        if (x + p.w > this.width - HudPos.MARGIN - SNAP) x = this.width - HudPos.MARGIN - p.w;
        if (y < HudPos.MARGIN + SNAP) y = HudPos.MARGIN;
        if (y + p.h > this.height - HudPos.MARGIN - SNAP) y = this.height - HudPos.MARGIN - p.h;

        p.moveTo(x, y);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        this.dragging = null;
        this.snapX = this.snapY = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        ConfigManager.autoSave();
        if (this.client == null) return;
        this.client.setScreen(null);
        if (this.returnToClickGui) {
            Module gui = Night.INSTANCE.getModuleManager().getModule(ClickGui.class);
            if (gui != null && !gui.isEnabled()) gui.setEnabled(true);
        }
    }
}
