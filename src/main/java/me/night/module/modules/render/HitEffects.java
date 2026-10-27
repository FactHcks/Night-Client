package me.night.module.modules.render;

import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.render.Projection;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Hit marker on the crosshair and floating damage numbers. */
public class HitEffects extends Module {
    private static HitEffects INSTANCE;

    private final BooleanProperty marker = new BooleanProperty("Hit Marker", "Flash around the crosshair when you hit", true);
    private final BooleanProperty numbers = new BooleanProperty("Damage Numbers", "Floating numbers showing damage dealt", true);

    private long markerTime = 0;
    private final Map<LivingEntity, float[]> watched = new HashMap<>(); // {health, time}
    private final List<Popup> popups = new ArrayList<>();

    private record Popup(Vec3d pos, float damage, long start, float drift) {
    }

    public HitEffects() {
        super("HitEffects", "Hit marker and floating damage numbers", Category.RENDER);
        addProperties(marker, numbers);
        INSTANCE = this;
    }

    /** Called when the player attacks an entity. */
    public static void onAttack(Entity target) {
        HitEffects fx = INSTANCE;
        if (fx == null || !fx.isEnabled()) return;
        fx.markerTime = System.currentTimeMillis();
        if (target instanceof LivingEntity living) {
            fx.watched.put(living, new float[]{living.getHealth(), System.currentTimeMillis()});
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.POST) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<LivingEntity, float[]>> it = watched.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, float[]> e = it.next();
            LivingEntity ent = e.getKey();
            float[] data = e.getValue();
            float hp = ent.isAlive() ? ent.getHealth() : 0;
            if (hp < data[0] - 0.01F) {
                Vec3d at = ent.getEntityPos().add(0, ent.getHeight() * 0.75, 0);
                popups.add(new Popup(at, data[0] - hp, now, (float) (Math.random() - 0.5)));
                data[0] = hp;
            }
            if (now - (long) data[1] > 1500 || !ent.isAlive()) it.remove();
        }
        popups.removeIf(p -> now - p.start > 1100);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        DrawContext ctx = event.getContext();
        long now = System.currentTimeMillis();
        int accent = Theme.accent(0);

        if (marker.getValue()) {
            float t = (now - markerTime) / 260F;
            if (t < 1) {
                float cx = mc.getWindow().getScaledWidth() / 2.0F;
                float cy = mc.getWindow().getScaledHeight() / 2.0F;
                float e = Anim.easeOutCubic(t);
                float r1 = 3.5F + 3 * e, r2 = 8 + 3 * e;
                int col = ColorUtil.withAlpha(0xFFFFFF, Math.round(255 * (1 - t)));
                int glow = ColorUtil.withAlpha(accent, Math.round(180 * (1 - t)));
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 4 + i * Math.PI / 2;
                    float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                    Render2D.lineGlow(ctx, cx + dx * r1, cy + dy * r1, cx + dx * r2, cy + dy * r2, 1.4F, 4, glow);
                    Render2D.line(ctx, cx + dx * r1, cy + dy * r1, cx + dx * r2, cy + dy * r2, 1.4F, col, col);
                }
            }
        }

        if (numbers.getValue()) {
            UiFont font = UiFont.BOLD;
            for (Popup p : popups) {
                float age = (now - p.start) / 1100F;
                Vec3d pos = p.pos.add(p.drift * 0.4 * age, age * 0.9, 0);
                float[] sp = Projection.toScreen(pos);
                if (sp == null) continue;
                float pop = age < 0.15F ? Anim.easeOutBack(age / 0.15F) : 1;
                float alpha = age > 0.65F ? 1 - (age - 0.65F) / 0.35F : 1;
                String text = String.format(p.damage >= 10 ? "%.0f" : "%.1f", p.damage);
                var m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(sp[0], sp[1]);
                m.scale(0.8F + 0.4F * pop, 0.8F + 0.4F * pop);
                Render2D.setAlpha(alpha);
                float w = font.width(text);
                int color = p.damage >= 4 ? 0xFFFF5A6E : ColorUtil.brighter(accent, 0.3F);
                Render2D.shadow(ctx, -w / 2 - 2, -font.capHeight() / 2 - 1, w + 4, font.capHeight() + 2, 3, 6,
                        ColorUtil.withAlpha(color, 0x50));
                font.draw(ctx, text, -w / 2, -font.capHeight() / 2, color, true);
                Render2D.resetAlpha();
                m.popMatrix();
            }
        }
    }
}
