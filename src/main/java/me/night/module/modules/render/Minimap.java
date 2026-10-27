package me.night.module.modules.render;

import me.night.Night;
import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.ui.hud.HudPos;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import me.night.waypoint.Waypoint;
import me.night.waypoint.WaypointManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.joml.Matrix3x2fStack;

/** Top-down map of the terrain around you, drawn like a vanilla map. */
public class Minimap extends Module {
    private static final Identifier TEXTURE_ID = Identifier.of(Night.MOD_ID, "minimap");
    private static final int UNLOADED = 0xFF0E0E12;

    public final HudPos pos = new HudPos("Minimap", 0, 0.42);
    private final NumberProperty size = new NumberProperty("Size", "Size of the map on screen", 90, 60, 160, 2);
    private final NumberProperty range = new NumberProperty("Range", "Blocks shown in each direction", 40, 16, 64, 4);
    private final ModeProperty shape = new ModeProperty("Shape", "Circle", "Circle", "Square");
    private final BooleanProperty rotate = new BooleanProperty("Rotate", "Turn the map with your view (circle only)", true);
    private final BooleanProperty players = new BooleanProperty("Players", true);
    private final BooleanProperty waypoints = new BooleanProperty("Waypoints", true);

    private NativeImageBackedTexture texture;
    private int textureRange = -1;
    private int centerX, centerZ;     // block the texture is centred on
    private int ticksSinceBuild = 100;

    public Minimap() {
        super("Minimap", "Top-down map of the area around you", Category.RENDER);
        addProperties(size, range, shape, rotate, players, waypoints, pos.px, pos.py);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.POST) return;
        ticksSinceBuild++;
    }

    // ------------------------------------------------------------------ map texture

    private void rebuild() {
        int r = range.getInt();
        int side = r * 2;
        if (texture == null || textureRange != r) {
            if (texture != null) mc.getTextureManager().destroyTexture(TEXTURE_ID);
            texture = new NativeImageBackedTexture(() -> "night-minimap", new NativeImage(side, side, false));
            mc.getTextureManager().registerTexture(TEXTURE_ID, texture);
            textureRange = r;
        }
        centerX = mc.player.getBlockX();
        centerZ = mc.player.getBlockZ();
        NativeImage img = texture.getImage();
        boolean ceiling = mc.world.getDimension().hasCeiling();

        // height of the row just north of the map, for shading the first row
        int[] prevHeights = new int[side];
        for (int i = 0; i < side; i++) prevHeights[i] = surfaceY(centerX - r + i, centerZ - r - 1, ceiling);

        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int zi = 0; zi < side; zi++) {
            int z = centerZ - r + zi;
            for (int xi = 0; xi < side; xi++) {
                int x = centerX - r + xi;
                if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                    img.setColorArgb(xi, zi, UNLOADED);
                    prevHeights[xi] = Integer.MIN_VALUE;
                    continue;
                }
                int y = surfaceY(x, z, ceiling);
                BlockState state = mc.world.getBlockState(p.set(x, y, z));
                MapColor color = state.getMapColor(mc.world, p);
                // see-through blocks (glass...): look one block lower
                if (color == MapColor.CLEAR && y > mc.world.getBottomY()) {
                    state = mc.world.getBlockState(p.set(x, y - 1, z));
                    color = state.getMapColor(mc.world, p);
                }
                MapColor.Brightness b;
                int north = prevHeights[xi];
                if (north == Integer.MIN_VALUE || y == north) b = MapColor.Brightness.NORMAL;
                else b = y > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                if (color == MapColor.WATER_BLUE) b = MapColor.Brightness.NORMAL;
                img.setColorArgb(xi, zi, color == MapColor.CLEAR ? UNLOADED : color.getRenderColor(b));
                prevHeights[xi] = y;
            }
        }
        texture.upload();
        ticksSinceBuild = 0;
    }

    /** The highest visible block at x, z (scans below the roof in the Nether). */
    private int surfaceY(int x, int z, boolean ceiling) {
        if (!ceiling) return mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        BlockPos.Mutable p = new BlockPos.Mutable(x, mc.player.getBlockY() + 2, z);
        boolean sawAir = false;
        for (int i = 0; i < 32; i++) {
            boolean air = mc.world.getBlockState(p).isAir();
            if (sawAir && !air) return p.getY();
            if (air) sawAir = true;
            p.move(0, -1, 0);
        }
        return mc.player.getBlockY() - 1;
    }

    // ------------------------------------------------------------------ drawing

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        boolean moved = Math.abs(mc.player.getBlockX() - centerX) > 3 || Math.abs(mc.player.getBlockZ() - centerZ) > 3;
        if (texture == null || textureRange != range.getInt() || ticksSinceBuild >= 10 || (moved && ticksSinceBuild >= 2)) rebuild();

        DrawContext ctx = event.getContext();
        float s = size.getFloat();
        pos.place(s, s);
        float x = pos.x, y = pos.y;
        float cx = x + s / 2, cy = y + s / 2;
        boolean circle = shape.is("Circle");
        boolean rot = circle && rotate.getValue();
        float radius = circle ? s / 2 : 8;
        float scale = s / (range.getInt() * 2F);
        float tickDelta = event.getTickCounter().getTickProgress(true);
        double px = mc.player.getLerpedPos(tickDelta).x, pz = mc.player.getLerpedPos(tickDelta).z;
        float yaw = mc.player.getYaw(tickDelta);
        float angle = rot ? (float) Math.toRadians(-(yaw + 180)) : 0;
        int accent = Theme.accent(0);

        Module hudMod = Night.INSTANCE.getModuleManager().getModule(HUD.class);
        boolean blur = hudMod instanceof HUD hud && hud.blur.getValue();
        Render2D.shadow(ctx, x - 3, y - 1, s + 6, s + 6, radius + 3, 12, 0x60000000);
        Render2D.glass(ctx, x - 3, y - 3, s + 6, s + 6, radius + 3, 0x900C0C12, blur);

        // terrain, shifted so the player stays in the centre between rebuilds
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate(angle);
        float ox = (float) (centerX + 0.5 - px) * scale, oz = (float) (centerZ + 0.5 - pz) * scale;
        m.translate(ox, oz);
        Render2D.image(ctx, TEXTURE_ID, -s / 2, -s / 2, s, s, rot ? s / 2 : radius, 0, 0, 1, 1, 0xFFFFFFFF, true);
        m.popMatrix();

        Render2D.outline(ctx, x - 3, y - 3, s + 6, s + 6, radius + 3, 0.8F, ColorUtil.withAlpha(accent, 0x90));

        // waypoints and players as dots
        if (waypoints.getValue()) {
            for (Waypoint wp : WaypointManager.visible()) {
                float[] d = toMap(wp.x() + 0.5 - px, wp.z() + 0.5 - pz, scale, angle, s, circle);
                Render2D.shadow(ctx, cx + d[0] - 2.5F, cy + d[1] - 2.5F, 5, 5, 2.5F, 3, ColorUtil.withAlpha(wp.color(), 0x90));
                Render2D.circle(ctx, cx + d[0], cy + d[1], 2.5F, wp.color());
            }
        }
        if (players.getValue()) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                float[] d = toMap(p.getX() - px, p.getZ() - pz, scale, angle, s, circle);
                Render2D.circle(ctx, cx + d[0], cy + d[1], 2.6F, 0xFF101015);
                Render2D.circle(ctx, cx + d[0], cy + d[1], 1.9F, 0xFFFFFFFF);
            }
        }

        // you: an arrow pointing where you look
        float a = rot ? 0 : (float) Math.toRadians(yaw + 180);
        float fx = (float) Math.sin(a), fy = -(float) Math.cos(a);
        float sx = -fy, sy = fx;
        float tipX = cx + fx * 5, tipY = cy + fy * 5;
        Render2D.line(ctx, cx - fx * 3 + sx * 3.5F, cy - fy * 3 + sy * 3.5F, tipX, tipY, 1.8F, 0xFFFFFFFF, 0xFFFFFFFF);
        Render2D.line(ctx, cx - fx * 3 - sx * 3.5F, cy - fy * 3 - sy * 3.5F, tipX, tipY, 1.8F, 0xFFFFFFFF, 0xFFFFFFFF);
        Render2D.line(ctx, cx - fx * 1.5F, cy - fy * 1.5F, tipX, tipY, 1.8F, 0xFFFFFFFF, 0xFFFFFFFF);

        // north marker on the rim
        float[] n = rotatePoint(0, -(s / 2 - 7), angle);
        UiFont f = UiFont.SMALL;
        Render2D.circle(ctx, cx + n[0], cy + n[1], 5, 0xC00C0C12);
        f.drawCentered(ctx, "N", cx + n[0], f.centerY(cy + n[1] - 5, 10), accent, false);
    }

    private static float[] rotatePoint(float x, float y, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        return new float[]{x * c - y * s, x * s + y * c};
    }

    /** World offset -> map offset, kept inside the map edge. */
    private static float[] toMap(double dx, double dz, float scale, float angle, float size, boolean circle) {
        float[] p = rotatePoint((float) dx * scale, (float) dz * scale, angle);
        float limit = size / 2 - 4;
        if (circle) {
            float len = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1]);
            if (len > limit) {
                p[0] *= limit / len;
                p[1] *= limit / len;
            }
        } else {
            p[0] = Math.max(-limit, Math.min(limit, p[0]));
            p[1] = Math.max(-limit, Math.min(limit, p[1]));
        }
        return p;
    }
}
