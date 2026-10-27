package me.night.module.modules.render;

import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.NumberProperty;
import me.night.render.Projection;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Frosted glass nametags with health, distance and gear. Replaces vanilla player nametags. */
public class Nametags extends Module {
    private static Nametags INSTANCE;
    /** Dev screenshot tool only: also tag the local player. */
    public static boolean devIncludeSelf = false;

    private final NumberProperty scale = new NumberProperty("Scale", 1.0, 0.5, 2.0, 0.05);
    private final BooleanProperty health = new BooleanProperty("Health", true);
    private final BooleanProperty distance = new BooleanProperty("Distance", true);
    private final BooleanProperty items = new BooleanProperty("Items", "Held item and armour above the tag", true);
    private final BooleanProperty blur = new BooleanProperty("Blur", "Frosted glass background", true);

    public Nametags() {
        super("Nametags", "Glass nametags with health and gear", Category.RENDER);
        addProperties(scale, health, distance, items, blur);
        INSTANCE = this;
    }

    /** True if the vanilla nametag of this entity should be hidden. */
    public static boolean replaces(Entity entity) {
        return INSTANCE != null && INSTANCE.isEnabled() && entity instanceof PlayerEntity && (entity != mc.player || devIncludeSelf);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;
        DrawContext ctx = event.getContext();
        float tickDelta = event.getTickCounter().getTickProgress(true);

        List<PlayerEntity> players = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if ((p != mc.player || devIncludeSelf) && p.isAlive() && !p.isInvisible()) players.add(p);
        }
        // far ones first, so close tags are drawn on top
        players.sort(Comparator.comparingDouble(p -> -mc.player.squaredDistanceTo(p)));

        for (PlayerEntity p : players) {
            Vec3d pos = p.getLerpedPos(tickDelta).add(0, p.getHeight() + (p.isSneaking() ? 0.25 : 0.45), 0);
            float[] sp = Projection.toScreen(pos);
            if (sp == null) continue;
            double dist = mc.player.distanceTo(p);
            float s = scale.getFloat() * (float) Math.max(0.6, Math.min(1.2, 1.35 - dist / 45.0));

            Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(sp[0], sp[1]);
            m.scale(s, s);
            drawTag(ctx, p, dist);
            m.popMatrix();
        }
    }

    private void drawTag(DrawContext ctx, PlayerEntity p, double dist) {
        UiFont nameFont = UiFont.MEDIUM;
        UiFont small = UiFont.SMALL;
        float hp = p.getHealth() + p.getAbsorptionAmount();
        float ratio = Math.min(1, Math.max(0, p.getHealth() / Math.max(1, p.getMaxHealth())));
        String hpText = String.format("%.1f", hp);
        String distText = Math.round(dist) + "m";

        float nameW = nameFont.width(p.getDisplayName());
        float w = 12 + nameW;
        if (health.getValue()) w += 6 + small.width(hpText);
        if (distance.getValue()) w += 6 + small.width(distText);
        float h = 16;
        float x = -w / 2;
        float y = -h;

        Render2D.shadow(ctx, x, y + 1, w, h, 6, 8, 0x50000000);
        Render2D.glass(ctx, x, y, w, h, 6, blur.getValue() ? 0x700C0C12 : 0xB00C0C12, blur.getValue());
        boolean isTeam = me.night.module.modules.combat.Teams.isTeammate(p);
        Render2D.outline(ctx, x, y, w, h, 6, 0.5F, isTeam ? 0x884ADE80 : 0x22FFFFFF);
        if (health.getValue()) {
            int hpCol = ColorUtil.lerp(0xFFFF4D5E, 0xFF4ADE80, ratio);
            Render2D.rect(ctx, x + 4, y + h - 2, (w - 8) * ratio, 1, 0.5F, hpCol);
        }

        float tx = x + 6;
        tx = nameFont.drawText(ctx, p.getDisplayName(), tx, nameFont.centerY(y, h - 1), 0xFFFFFFFF, false);
        if (health.getValue()) {
            int hpCol = ColorUtil.lerp(0xFFFF6B78, 0xFF6BEA97, ratio);
            tx = small.draw(ctx, hpText, tx + 6, small.centerY(y, h - 1), hpCol, false);
        }
        if (distance.getValue()) {
            small.draw(ctx, distText, tx + 6, small.centerY(y, h - 1), 0xFF9A9AAA, false);
        }

        if (items.getValue()) drawItems(ctx, p, y - 2);
    }

    private void drawItems(DrawContext ctx, PlayerEntity p, float bottom) {
        List<ItemStack> stacks = new ArrayList<>();
        if (!p.getMainHandStack().isEmpty()) stacks.add(p.getMainHandStack());
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = p.getEquippedStack(slot);
            if (!s.isEmpty()) stacks.add(s);
        }
        if (!p.getOffHandStack().isEmpty()) stacks.add(p.getOffHandStack());
        if (stacks.isEmpty()) return;

        float size = 12;
        float total = stacks.size() * size;
        float x = -total / 2;
        float y = bottom - size;
        Render2D.glass(ctx, x - 3, y - 2, total + 6, size + 4, 5, 0x600C0C12, blur.getValue());
        Matrix3x2fStack m = ctx.getMatrices();
        for (ItemStack stack : stacks) {
            m.pushMatrix();
            m.translate(x, y);
            m.scale(size / 16F, size / 16F);
            ctx.drawItem(stack, 0, 0);
            m.popMatrix();
            if (stack.isDamageable() && stack.getDamage() > 0) {
                float left = 1 - stack.getDamage() / (float) stack.getMaxDamage();
                Render2D.rect(ctx, x + 1, y + size - 1, (size - 2) * left, 1, 0.5F, ColorUtil.lerp(0xFFFF4D5E, 0xFF4ADE80, left));
            }
            x += size;
        }
    }
}
