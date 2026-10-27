package me.night.module.modules.render;

import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.module.modules.combat.Killaura;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.util.ExhiRenderUtil;
import me.night.util.NightFont;
import me.night.util.NightRenderUtil;
import me.night.ui.hud.HudEditorScreen;
import me.night.ui.hud.HudPos;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.joml.Matrix3x2fStack;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.awt.Color;
import java.util.Comparator;
import java.util.stream.StreamSupport;

public class TargetHUD extends Module {
    public final ModeProperty style = new ModeProperty("Style", "Modern", "Modern", "Classic");
    public final BooleanProperty glow = new BooleanProperty("Glow Shaders", "Render ambient neon bloom", true);
    public final BooleanProperty blur = new BooleanProperty("Blur", "Frosted glass background", true);
    public final HudPos pos = new HudPos("TargetHUD", 0.5, 0.66);

    public TargetHUD() {
        super("TargetHUD", "Shows info about your Killaura target", Category.RENDER);
        addProperties(style, glow, blur, pos.px, pos.py);
        setEnabled(true);
    }

    /** Only shows Killaura's current target. */
    private LivingEntity findTarget() {
        LivingEntity t = Killaura.currentTarget;
        return t != null && t.isAlive() ? t : null;
    }

    // animation state for the modern style
    private final Anim showAnim = new Anim(0.0F);
    private final Anim healthAnim = new Anim(1.0F);
    private final Anim trailAnim = new Anim(1.0F);
    private LivingEntity shownTarget = null;

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        LivingEntity target = findTarget();
        if (target == null && HudEditorScreen.isOpen()) target = mc.player; // preview while editing
        DrawContext context = event.getContext();
        int x = 0;
        int y = 0;

        if (style.is("Modern")) {
            if (target != null && target != shownTarget) {
                if (shownTarget == null || showAnim.get() < 0.05F) {
                    float ratio = healthRatio(target);
                    healthAnim.set(ratio);
                    trailAnim.set(ratio);
                }
                shownTarget = target;
            }
            float show = showAnim.update(target != null ? 1.0F : 0.0F, target != null ? 10.0F : 14.0F);
            if (shownTarget == null || show < 0.01F) {
                if (target == null) shownTarget = null;
                return;
            }
            drawModernTargetHUD(context, x, y, shownTarget, show);
        } else if (target != null) {
            String n = target.getName().getString();
            pos.place(Math.max(145, 46 + mc.textRenderer.getWidth(n) + 16), 44);
            drawClassicTargetHUD(context, Math.round(pos.x), Math.round(pos.y), target);
        }
    }

    private static float healthRatio(LivingEntity e) {
        return Math.min(1.0F, Math.max(0.0F, e.getHealth() / Math.max(1.0F, e.getMaxHealth())));
    }

    private void drawModernTargetHUD(DrawContext context, int unusedX, int unusedY, LivingEntity target, float show) {
        String name = target.getName().getString();
        int nameW = NightFont.width(NightFont.Type.BOLD, name);
        float w = Math.max(150, 52 + nameW + 30);
        float h = 46;
        pos.place(w, h);
        int x = Math.round(pos.x);
        int y = Math.round(pos.y);
        int accent = Theme.accent(0);

        // pop in / out around the card centre
        float s = 0.85F + 0.15F * Anim.easeOutBack(show);
        Matrix3x2fStack m = context.getMatrices();
        m.pushMatrix();
        m.translate(x + w / 2, y + h / 2);
        m.scale(s, s);
        m.translate(-(x + w / 2), -(y + h / 2));
        NightRenderUtil.setGlobalAlpha(Math.min(1.0F, show * 1.3F));

        // clear frosted glass: blurred world, light tint, soft top highlight
        NightRenderUtil.shadow(context, x, y + 2, w, h, 9, 14, 0x55000000);
        if (glow.getValue()) {
            NightRenderUtil.shadow(context, x, y, w, h, 9, 12, ColorUtil.withAlpha(accent, 0x30));
        }
        me.night.render.Render2D.glass(context, x, y, w, h, 9, blur.getValue() ? 0x4A0C0C12 : 0xE00F0F14, blur.getValue());
        me.night.render.Render2D.rectV(context, x, y, w, h * 0.6F, 9, 0x16FFFFFF, 0x00FFFFFF);
        NightRenderUtil.roundedOutline(context, x, y, w, h, 9, 0.5F, 0x26FFFFFF);

        // head / portrait
        float hx = x + 7;
        float hy = y + 7;
        float hs = 32;
        float hurt = target.hurtTime > 0 ? target.hurtTime / 10.0F : 0.0F;
        NightRenderUtil.roundedRect(context, hx - 1, hy - 1, hs + 2, hs + 2, 7, 0x40000000);
        if (target instanceof AbstractClientPlayerEntity player) {
            int tint = ColorUtil.lerp(0xFFFFFFFF, 0xFFFF6060, hurt);
            float shrink = hurt * 1.5F;
            me.night.render.Render2D.head(context, player.getSkin(), hx + shrink, hy + shrink, hs - shrink * 2, 6, tint);
        } else {
            ItemStack hand = target.getMainHandStack();
            if (!hand.isEmpty()) {
                context.drawItem(hand, Math.round(hx + 8), Math.round(hy + 8));
            } else {
                String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
                NightFont.draw(context, NightFont.Type.TITLE, initial,
                        hx + (hs - NightFont.width(NightFont.Type.TITLE, initial)) / 2,
                        NightFont.centerY(NightFont.Type.TITLE, hy, hs), accent, false);
            }
        }

        // name + win/lose badge
        float tx = x + 46;
        NightFont.draw(context, NightFont.Type.BOLD, name, tx, y + 8, Theme.TEXT, false);
        float myHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float theirHp = target.getHealth() + target.getAbsorptionAmount();
        String badge = myHp > theirHp ? "W" : (myHp < theirHp ? "L" : "D");
        int badgeCol = myHp > theirHp ? 0xFF4ADE80 : (myHp < theirHp ? 0xFFFF5A6E : 0xFFFACC15);
        float bw = 14;
        float bx = x + w - 8 - bw;
        NightRenderUtil.roundedRect(context, bx, y + 7, bw, 11, 3.5F, ColorUtil.withAlpha(badgeCol, 0x30));
        NightFont.draw(context, NightFont.Type.SMALL, badge,
                bx + (bw - NightFont.width(NightFont.Type.SMALL, badge)) / 2, NightFont.centerY(NightFont.Type.SMALL, y + 7, 11), badgeCol, false);

        // health text + distance
        String hpText = String.format("%.1f HP", target.getHealth());
        NightFont.draw(context, NightFont.Type.SMALL, hpText, tx, y + 19, Theme.TEXT_DIM, false);
        String dist = String.format("%.1fm", mc.player.distanceTo(target));
        NightFont.draw(context, NightFont.Type.SMALL, dist, tx + NightFont.width(NightFont.Type.SMALL, hpText) + 6, y + 19, Theme.TEXT_MUTED, false);

        // health bar: fast fill + slow white trail showing recent damage
        float ratio = healthRatio(target);
        float hp = healthAnim.update(ratio, 12.0F);
        float trail = trailAnim.update(ratio, ratio < trailAnim.get() ? 3.0F : 20.0F);
        float barX = tx;
        float barY = y + 33;
        float barW = w - 46 - 8;
        float barH = 5;
        NightRenderUtil.roundedRect(context, barX, barY, barW, barH, 2.5F, 0xFF22222C);
        if (trail > hp) {
            NightRenderUtil.roundedRect(context, barX, barY, Math.max(barH, barW * trail), barH, 2.5F, 0x60FFFFFF);
        }
        if (glow.getValue()) {
            NightRenderUtil.shadow(context, barX, barY, Math.max(barH, barW * hp), barH, 2.5F, 5, ColorUtil.withAlpha(accent, 0x45));
        }
        NightRenderUtil.roundedGradient(context, barX, barY, Math.max(barH, barW * hp), barH, 2.5F, accent, Theme.accentAlt(0));

        float abs = target.getAbsorptionAmount();
        if (abs > 0.0F) {
            float absW = barW * Math.min(1.0F, abs / Math.max(1.0F, target.getMaxHealth()));
            NightRenderUtil.roundedGradient(context, barX, barY + barH - 1.5F, Math.max(2, absW), 1.5F, 0.75F, 0xFFFFD54A, 0xFFFFA62B);
        }

        NightRenderUtil.resetGlobalAlpha();
        m.popMatrix();
    }

    private void drawClassicTargetHUD(DrawContext context, int x, int y, LivingEntity target) {
        String rawName = target.getName().getString();
        int nameW = mc.textRenderer.getWidth(rawName);
        int boxW = Math.max(145, 46 + nameW + 16);
        int boxH = 44;

        ExhiRenderUtil.drawSkeetBox(context, x, y, boxW, boxH);
        ExhiRenderUtil.drawSkeetRainbowBar(context, x + 2, y + 2, boxW - 4, 1);

        context.fill(x + 4, y + 5, x + 38, y + 39, 0xFF111111);
        ExhiRenderUtil.drawOutline(context, x + 4, y + 5, 34, 34, 0xFF303030);

        ItemStack mainHand = target.getMainHandStack();
        if (!mainHand.isEmpty()) {
            context.drawItem(mainHand, x + 13, y + 14);
        } else {
            context.drawTextWithShadow(mc.textRenderer, "HP", x + 16, y + 18, 0xFF888888);
        }

        context.drawTextWithShadow(mc.textRenderer, rawName, x + 42, y + 5, 0xFFFFFFFF);

        int barX = x + 42;
        int barY = y + 16;
        int barW = boxW - 48;
        int barH = 5;

        float health = target.getHealth();
        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        float hpRatio = Math.min(1.0F, Math.max(0.0F, health / maxHealth));

        context.fill(barX, barY, barX + barW, barY + barH, 0xFF0A0A0A);
        context.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF1E1E1E);

        float hue = hpRatio * 0.33F;
        int hpColor = Color.HSBtoRGB(hue, 0.85F, 1.0F);
        int fillW = (int) ((barW - 2) * hpRatio);
        context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, hpColor);

        for (int i = 1; i < 10; i++) {
            int sx = barX + (barW / 10) * i;
            context.fill(sx, barY, sx + 1, barY + barH, 0xFF0A0A0A);
        }

        float myTotalHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float targetTotalHp = target.getHealth() + target.getAbsorptionAmount();
        String winText = myTotalHp > targetTotalHp ? "§aW" : (myTotalHp < targetTotalHp ? "§cL" : "§eD");
        String subInfo = String.format("HP: %.1f | Dist: %.1fm | %s", health, mc.player.distanceTo(target), winText);
        context.drawTextWithShadow(mc.textRenderer, subInfo, x + 42, y + 24, 0xFFCCCCCC);
    }
}
