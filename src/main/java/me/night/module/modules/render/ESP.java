package me.night.module.modules.render;

import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.module.modules.combat.AntiBot;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ColorProperty;
import me.night.property.properties.ModeProperty;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

public class ESP extends Module {

    private final ModeProperty mode = new ModeProperty("Mode", "2D Box", "2D Box", "Corner", "Glow");
    private final BooleanProperty glowBoxes = new BooleanProperty("Glow Outline", "Add the glow to box modes too", false);
    private final BooleanProperty themeColor = new BooleanProperty("Theme Color", "Use the HUD theme colours", true);
    private final BooleanProperty healthBar = new BooleanProperty("Health Bar", "Renders health bar on left", true);
    private final BooleanProperty names = new BooleanProperty("Names", "Renders player nametag", true);
    private final BooleanProperty invisibles = new BooleanProperty("Invisibles", "Renders on invisible players", true);
    private final ColorProperty color = new ColorProperty("Color", "Box outline color", new Color(0xFF, 0x2A, 0x2A));
    private final BooleanProperty rainbow = new BooleanProperty("Rainbow", "Rainbow cycle color", false);

    public ESP() {
        super("ESP", "Box, corner or glow outlines on players", Category.RENDER);
        addProperties(mode, glowBoxes, themeColor, healthBar, names, invisibles, color, rainbow);
        INSTANCE = this;
    }

    private static ESP INSTANCE;
    /** Dev screenshot tool only: also glow the local player. */
    public static boolean devIncludeSelf = false;

    /** True if this entity should get the glowing outline. */
    public static boolean shouldGlow(net.minecraft.entity.Entity entity) {
        ESP esp = INSTANCE;
        if (esp == null || !esp.isEnabled() || !(entity instanceof PlayerEntity player)) return false;
        if (!esp.mode.is("Glow") && !esp.glowBoxes.getValue()) return false;
        if ((player == mc.player && !devIncludeSelf) || !player.isAlive() || AntiBot.isBot(player)) return false;
        return esp.invisibles.getValue() || !player.isInvisible();
    }

    public static int glowColor(net.minecraft.entity.Entity entity) {
        if (me.night.module.modules.combat.Teams.isTeammate(entity)) return 0xFF4ADE80;
        ESP esp = INSTANCE;
        if (esp.rainbow.getValue()) return esp.getRainbow(entity.getId() * 40, 1.0F) | 0xFF000000;
        if (esp.themeColor.getValue()) return me.night.util.Theme.accent(entity.getId() * 9) | 0xFF000000;
        return esp.color.getRGB() | 0xFF000000;
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        if (!camera.isReady()) {
            return;
        }

        DrawContext context = event.getContext();
        Vec3d camPos = camera.getCameraPos();

        double yawRad = Math.toRadians(camera.getYaw());
        double pitchRad = Math.toRadians(camera.getPitch());
        double fX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fY = -Math.sin(pitchRad);
        double fZ = Math.cos(yawRad) * Math.cos(pitchRad);
        Vec3d forward = new Vec3d(fX, fY, fZ);

        int windowW = mc.getWindow().getScaledWidth();
        int windowH = mc.getWindow().getScaledHeight();
        float tickDelta = event.getTickCounter().getTickProgress(true);

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) {
                continue;
            }
            if (AntiBot.isBot(player)) {
                continue;
            }
            if (!invisibles.getValue() && player.isInvisible()) {
                continue;
            }

            Vec3d pos = player.getLerpedPos(tickDelta);
            float pWidth = player.getWidth();
            float pHeight = player.getHeight();

            Vec3d center = pos.add(0, pHeight / 2.0, 0);
            if (center.subtract(camPos).dotProduct(forward) <= 0.3) {
                continue;
            }

            double minBoxX = pos.x - pWidth / 2.0;
            double maxBoxX = pos.x + pWidth / 2.0;
            double minBoxY = pos.y;
            double maxBoxY = pos.y + pHeight + (player.isSneaking() ? 0.0 : 0.05);
            double minBoxZ = pos.z - pWidth / 2.0;
            double maxBoxZ = pos.z + pWidth / 2.0;

            Vec3d[] corners = new Vec3d[]{
                    new Vec3d(minBoxX, minBoxY, minBoxZ),
                    new Vec3d(maxBoxX, minBoxY, minBoxZ),
                    new Vec3d(minBoxX, minBoxY, maxBoxZ),
                    new Vec3d(maxBoxX, minBoxY, maxBoxZ),
                    new Vec3d(minBoxX, maxBoxY, minBoxZ),
                    new Vec3d(maxBoxX, maxBoxY, minBoxZ),
                    new Vec3d(minBoxX, maxBoxY, maxBoxZ),
                    new Vec3d(maxBoxX, maxBoxY, maxBoxZ)
            };

            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;

            boolean allInFront = true;
            for (Vec3d c : corners) {
                if (c.subtract(camPos).dotProduct(forward) <= 0.1) {
                    allInFront = false;
                    break;
                }
                Vec3d ndc = mc.gameRenderer.project(c);
                float sx = (float) ((ndc.x + 1.0) / 2.0 * windowW);
                float sy = (float) ((1.0 - ndc.y) / 2.0 * windowH);
                minX = Math.min(minX, sx);
                maxX = Math.max(maxX, sx);
                minY = Math.min(minY, sy);
                maxY = Math.max(maxY, sy);
            }

            if (!allInFront) {
                continue;
            }

            if (maxX < -50 || minX > windowW + 50 || maxY < -50 || minY > windowH + 50) {
                continue;
            }
            if (maxX - minX < 2 || maxY - minY < 2) {
                continue;
            }
            if (maxX - minX > windowW * 2 || maxY - minY > windowH * 2) {
                continue;
            }

            int x1 = (int) minX;
            int y1 = (int) minY;
            int x2 = (int) maxX;
            int y2 = (int) maxY;

            int boxColor = rainbow.getValue() ? getRainbow(0, 1.0f) : (color.getRGB() | 0xFF000000);
            if (me.night.module.modules.combat.Teams.isTeammate(player)) {
                boxColor = 0xFF4ADE80;
            } else if (themeColor.getValue() && !rainbow.getValue()) {
                boxColor = me.night.util.Theme.accent(player.getId() * 9) | 0xFF000000;
            }
            if (mode.is("Corner")) {
                renderCornerBox(context, x1, y1, x2, y2, boxColor);
            } else if (mode.is("2D Box")) {
                renderFullBox(context, x1, y1, x2, y2, boxColor);
            }

            if (healthBar.getValue()) {
                renderHealthBar(context, x1, y1, x2, y2, player);
            }

            if (names.getValue()) {
                renderName(context, x1, y1, x2, y2, player);
            }
        }
    }

    private void renderFullBox(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        // Authentic Exhibition Skeet 2D Box:
        // Outer black border (1px)
        drawBoxOutline(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x90000000);
        // Main colored outline (1px)
        drawBoxOutline(context, x1, y1, x2, y2, color);
        // Inner black border (1px)
        drawBoxOutline(context, x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0x90000000);
    }

    private void renderCornerBox(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int w = x2 - x1;
        int h = y2 - y1;
        int cW = Math.max(3, w / 4);
        int cH = Math.max(3, h / 4);

        // Outer black corners
        drawCornerLines(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, cW + 1, cH + 1, 0x90000000);
        // Inner black corners
        drawCornerLines(context, x1 + 1, y1 + 1, x2 - 1, y2 - 1, cW - 1, cH - 1, 0x90000000);
        // Main colored corners
        drawCornerLines(context, x1, y1, x2, y2, cW, cH, color);
    }

    private void drawCornerLines(DrawContext context, int x1, int y1, int x2, int y2, int cW, int cH, int color) {
        if (x2 <= x1 || y2 <= y1 || cW <= 0 || cH <= 0) return;
        // Top-Left
        context.fill(x1, y1, x1 + cW, y1 + 1, color);
        context.fill(x1, y1 + 1, x1 + 1, y1 + cH, color);
        // Top-Right
        context.fill(x2 - cW, y1, x2, y1 + 1, color);
        context.fill(x2 - 1, y1 + 1, x2, y1 + cH, color);
        // Bottom-Left
        context.fill(x1, y2 - 1, x1 + cW, y2, color);
        context.fill(x1, y2 - cH, x1 + 1, y2 - 1, color);
        // Bottom-Right
        context.fill(x2 - cW, y2 - 1, x2, y2, color);
        context.fill(x2 - 1, y2 - cH, x2, y2 - 1, color);
    }

    private void drawBoxOutline(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    private void renderHealthBar(DrawContext context, int x1, int y1, int x2, int y2, PlayerEntity player) {
        int barRight = x1 - 2;
        int barLeft = x1 - 5;

        // Background outline and fill
        drawBoxOutline(context, barLeft - 1, y1 - 1, barRight + 1, y2 + 1, 0x90000000);
        context.fill(barLeft, y1, barRight, y2, 0x50000000);

        float hp = Math.max(0.0f, Math.min(1.0f, player.getHealth() / player.getMaxHealth()));
        int barHeight = y2 - y1;
        int hpFillHeight = (int) (barHeight * hp);
        int hpFillY = y2 - hpFillHeight;

        int hpColor = getHealthColor(hp);
        context.fill(barLeft, hpFillY, barRight, y2, hpColor);

        // If low health (<= 50%), display health percentage on left
        if (hp <= 0.5f) {
            String hpPercent = ((int) (hp * 100)) + "%";
            int textW = mc.textRenderer.getWidth(hpPercent);
            context.drawTextWithShadow(mc.textRenderer, hpPercent, barLeft - textW - 2, hpFillY - 3, 0xFFE0E0E0);
        }
    }

    private void renderName(DrawContext context, int x1, int y1, int x2, int y2, PlayerEntity player) {
        String name = player.getName().getString();
        int textW = mc.textRenderer.getWidth(name);
        int textX = (x1 + x2) / 2 - textW / 2;
        int textY = y1 - 10;

        context.drawTextWithShadow(mc.textRenderer, name, textX, textY, 0xFFFFFFFF);
    }

    private int getHealthColor(float hp) {
        hp = Math.max(0.0f, Math.min(1.0f, hp));
        int r, g;
        if (hp > 0.5f) {
            float t = (hp - 0.5f) * 2.0f;
            r = (int) (255 * (1.0f - t));
            g = 255;
        } else {
            float t = hp * 2.0f;
            r = 255;
            g = (int) (255 * t);
        }
        return 0xFF000000 | (r << 16) | (g << 8);
    }

    private int getRainbow(int delay, float saturation) {
        double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 20.0);
        rainbowState %= 360;
        return Color.HSBtoRGB((float) (rainbowState / 360.0), saturation, 1.0f);
    }
}
