package me.night.module.modules.render;

import me.night.Night;
import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.render.Projection;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.util.ColorUtil;
import me.night.waypoint.Waypoint;
import me.night.waypoint.WaypointManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Shows saved waypoints in the world. Add them with .waypoint add <name>. */
public class Waypoints extends Module {
    private final BooleanProperty deathPoints = new BooleanProperty("Death Waypoint", "Mark where you last died", true);
    private final BooleanProperty arrows = new BooleanProperty("Off-screen Arrows", "Arrows at the screen edge for waypoints out of view", true);
    private final BooleanProperty distance = new BooleanProperty("Distance", true);

    private boolean wasDead = false;

    public Waypoints() {
        super("Waypoints", "Markers for saved spots (.waypoint add <name>)", Category.RENDER);
        addProperties(deathPoints, arrows, distance);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.POST || mc.player == null) return;
        boolean dead = mc.player.isDead();
        if (dead && !wasDead && deathPoints.getValue()) {
            WaypointManager.add("Death", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        }
        wasDead = dead;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        DrawContext ctx = event.getContext();
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        Module hudMod = Night.INSTANCE.getModuleManager().getModule(HUD.class);
        boolean blur = hudMod instanceof HUD hud && hud.blur.getValue();
        Vec3d eye = mc.player.getEyePos();

        for (Waypoint wp : WaypointManager.visible()) {
            Vec3d at = new Vec3d(wp.x() + 0.5, wp.y() + 1.0, wp.z() + 0.5);
            double dist = eye.distanceTo(at);
            float[] sp = Projection.toScreen(at);
            boolean onScreen = sp != null && sp[0] > 10 && sp[0] < sw - 10 && sp[1] > 10 && sp[1] < sh - 10;

            if (onScreen) {
                drawMarker(ctx, wp, sp[0], sp[1], dist, blur);
            } else if (arrows.getValue()) {
                drawEdgeArrow(ctx, wp, at, sw, sh);
            }
        }
    }

    private void drawMarker(DrawContext ctx, Waypoint wp, float x, float y, double dist, boolean blur) {
        int c = wp.color();
        // diamond made of two crossing lines + glow
        Render2D.shadow(ctx, x - 4, y - 4, 8, 8, 4, 6, ColorUtil.withAlpha(c, 0x80));
        Render2D.line(ctx, x, y - 4, x, y + 4, 4.5F, c, c);
        Render2D.circle(ctx, x, y, 1.6F, 0xFFFFFFFF);

        UiFont nf = UiFont.MEDIUM;
        UiFont sf = UiFont.SMALL;
        String d = distance.getValue() ? (dist >= 1000 ? String.format("%.1fk", dist / 1000) : Math.round(dist) + "m") : "";
        float w = nf.width(wp.name()) + 12 + (d.isEmpty() ? 0 : sf.width(d) + 6);
        float h = 14;
        float bx = x - w / 2, by = y - 9 - h;
        Render2D.shadow(ctx, bx, by + 1, w, h, 7, 8, 0x50000000);
        Render2D.glass(ctx, bx, by, w, h, 7, 0x800C0C12, blur);
        Render2D.outline(ctx, bx, by, w, h, 7, 0.6F, ColorUtil.withAlpha(c, 0x90));
        float tx = nf.draw(ctx, wp.name(), bx + 6, nf.centerY(by, h), 0xFFFFFFFF, false);
        if (!d.isEmpty()) sf.draw(ctx, d, tx + 6, sf.centerY(by, h), 0xFFB0B0BE, false);
    }

    private void drawEdgeArrow(DrawContext ctx, Waypoint wp, Vec3d at, float sw, float sh) {
        // angle of the waypoint relative to where we're looking
        double dx = at.x - mc.player.getX(), dz = at.z - mc.player.getZ();
        float worldYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float rel = (float) Math.toRadians(MathHelper.wrapDegrees(worldYaw - mc.player.getYaw()));
        float cx = sw / 2, cy = sh / 2;
        float rx = sw / 2 - 26, ry = sh / 2 - 26;
        float x = cx + (float) Math.sin(rel) * rx;
        float y = cy - (float) Math.cos(rel) * ry;
        int c = wp.color();
        float ax = (float) Math.sin(rel), ay = -(float) Math.cos(rel);
        float px = -ay, py = ax;
        // arrow head pointing outwards
        float tipX = x + ax * 6, tipY = y + ay * 6;
        Render2D.lineGlow(ctx, x - px * 4, y - py * 4, tipX, tipY, 2, 4, ColorUtil.withAlpha(c, 0x70));
        Render2D.line(ctx, x - px * 4, y - py * 4, tipX, tipY, 2, c, c);
        Render2D.line(ctx, x + px * 4, y + py * 4, tipX, tipY, 2, c, c);
        UiFont.SMALL.drawCentered(ctx, wp.name(), x - ax * 8, y - ay * 8 - UiFont.SMALL.capHeight() / 2, 0xE0FFFFFF, true);
    }
}
