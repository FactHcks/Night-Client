package me.night.module.modules.combat;

import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.util.RotationManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

public class Killaura extends Module {
    public static LivingEntity currentTarget = null;

    public final NumberProperty range = new NumberProperty("Range", "Attack range in blocks", 3.8, 3.0, 6.0, 0.1);
    public final ModeProperty mode = new ModeProperty("Mode", "Single", "Single", "Switch");
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", "Silently rotate packet angles", true);
    public final BooleanProperty targetMace = new BooleanProperty("Mace Smash", "Smash attacks from air", true);
    public final BooleanProperty spearCharge = new BooleanProperty("Spear Charge", "Use spear charge attacks", true);
    public final BooleanProperty playersOnly = new BooleanProperty("Players Only", true);

    public Killaura() {
        super("Killaura", "Automatically attacks nearby entities", Category.COMBAT);
        addProperties(range, mode, silentAim, targetMace, spearCharge, playersOnly);
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @Override
    protected void onDisable() {
        currentTarget = null;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;

        double r = this.range.getValue();
        List<LivingEntity> targets = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(e -> e instanceof LivingEntity && e != mc.player && e.isAlive())
                .map(e -> (LivingEntity) e)
                .filter(e -> !playersOnly.getValue() || e instanceof PlayerEntity)
                .filter(e -> !AntiBot.isBot(e))
                .filter(e -> mc.player.distanceTo(e) <= r)
                .sorted(Comparator.comparingDouble(mc.player::distanceTo))
                .toList();

        if (!targets.isEmpty()) {
            LivingEntity target = targets.get(0);
            currentTarget = target;

            // Silent Aim Rotations
            if (this.silentAim.getValue()) {
                float[] rots = RotationManager.calculateRotations(
                        mc.player.getX(),
                        mc.player.getEyeY(),
                        mc.player.getZ(),
                        target.getX(),
                        target.getEyeY() - 0.2,
                        target.getZ()
                );
                RotationManager.setRotations(rots[0], rots[1]);
            }

            // Attack with cooldown check
            if (mc.player.getAttackCooldownProgress(0.5F) >= 1.0F) {
                if (mc.interactionManager != null) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
        } else {
            currentTarget = null;
        }
    }
}
