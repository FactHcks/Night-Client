package me.night.module.modules.render;

import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.module.modules.combat.Killaura;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.NumberProperty;
import me.night.render.Projection;
import me.night.render.Render2D;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/** Glowing ring that bobs up and down your Killaura target. */
public class TargetIndicator extends Module {
    private static final int POINTS = 48;

    private final NumberProperty speed = new NumberProperty("Speed", "How fast the ring moves", 1.0, 0.3, 3.0, 0.1);
    private final NumberProperty thickness = new NumberProperty("Thickness", 1.6, 0.6, 4.0, 0.1);
    private final BooleanProperty glow = new BooleanProperty("Glow", "Soft light around the ring", true);
    private final BooleanProperty trail = new BooleanProperty("Trail", "Fading copies behind the ring", true);

    private final Anim show = new Anim(0);
    private LivingEntity last;

    public TargetIndicator() {
        super("TargetIndicator", "Animated ring around your Killaura target", Category.RENDER);
        addProperties(speed, thickness, glow, trail);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        LivingEntity target = Killaura.currentTarget != null && Killaura.currentTarget.isAlive() ? Killaura.currentTarget : null;
        if (target != null) last = target;
        float s = show.update(target != null ? 1 : 0, target != null ? 10 : 6);
        if (last == null || s < 0.01F) return;

        DrawContext ctx = event.getContext();
        float tickDelta = event.getTickCounter().getTickProgress(true);
        Vec3d pos = last.getLerpedPos(tickDelta);
        double height = last.getHeight();
        double radius = last.getWidth() * 0.75 + 0.15;
        double time = System.currentTimeMillis() / 1000.0 * speed.getValue();

        // eased bounce between feet and head
        double wave = (Math.sin(time * 2.2) + 1) / 2;
        double eased = wave * wave * (3 - 2 * wave);
        double slope = Math.cos(time * 2.2); // direction of travel, for the trail
        double y = pos.y + 0.05 + (height - 0.1) * eased;
        double centerDepth = Projection.depth(pos.add(0, y - pos.y, 0));

        Render2D.setAlpha(s);
        if (trail.getValue()) {
            for (int k = 3; k >= 1; k--) {
                double ty = y - Math.signum(slope) * k * 0.07 * Math.min(1, Math.abs(slope) * 2);
                drawRing(ctx, pos, ty, radius, centerDepth, 0.22F / k, thickness.getFloat() * 0.8F, false);
            }
        }
        drawRing(ctx, pos, y, radius, centerDepth, 1.0F, thickness.getFloat(), glow.getValue());
        Render2D.resetAlpha();
    }

    private void drawRing(DrawContext ctx, Vec3d pos, double y, double radius, double centerDepth, float alpha, float thick, boolean withGlow) {
        float[][] pts = new float[POINTS + 1][];
        float[] far = new float[POINTS + 1];
        for (int i = 0; i <= POINTS; i++) {
            double a = Math.PI * 2 * i / POINTS;
            Vec3d p = new Vec3d(pos.x + Math.cos(a) * radius, y, pos.z + Math.sin(a) * radius);
            pts[i] = Projection.toScreen(p);
            // the back half of the ring is dimmer so it reads as 3D
            far[i] = Projection.depth(p) > centerDepth ? 0.4F : 1.0F;
        }
        if (withGlow) {
            for (int i = 0; i < POINTS; i++) {
                if (pts[i] == null || pts[i + 1] == null) continue;
                int c = Theme.accent(i * 6);
                Render2D.lineGlow(ctx, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], thick, 6,
                        ColorUtil.withAlpha(c, Math.round(0x60 * alpha * far[i])));
            }
        }
        for (int i = 0; i < POINTS; i++) {
            if (pts[i] == null || pts[i + 1] == null) continue;
            int c1 = ColorUtil.withAlpha(Theme.accent(i * 6), Math.round(255 * alpha * far[i]));
            int c2 = ColorUtil.withAlpha(Theme.accent((i + 1) * 6), Math.round(255 * alpha * far[i + 1]));
            Render2D.line(ctx, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], thick, c1, c2);
        }
    }
}
