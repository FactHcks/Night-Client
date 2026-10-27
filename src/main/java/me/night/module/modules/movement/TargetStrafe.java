package me.night.module.modules.movement;

import me.night.Night;
import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.module.modules.combat.AntiBot;
import me.night.module.modules.combat.Killaura;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Comparator;
import java.util.stream.StreamSupport;

public class TargetStrafe extends Module {
    public static boolean isStrafing = false;
    public static LivingEntity currentStrafeTarget = null;

    public final NumberProperty radius = new NumberProperty("Radius", 2.2, 0.5, 6.0, 0.1);
    public final NumberProperty range = new NumberProperty("Target Range", 6.0, 3.0, 12.0, 0.5);
    public final BooleanProperty antiStuck = new BooleanProperty("Anti-Stuck", "Reverse direction when colliding with wall", true);
    public final BooleanProperty onlySpeed = new BooleanProperty("Only Speed", "Only strafe while Speed module is enabled", false);

    private int direction = 1;
    private int stuckTicks = 0;

    public TargetStrafe() {
        super("TargetStrafe", "Orbits in a circle around your combat target", Category.MOVEMENT);
        addProperties(radius, range, antiStuck, onlySpeed);
    }

    @Override
    public String getSuffix() {
        return String.format("%.1f", radius.getValue());
    }

    @Override
    protected void onDisable() {
        isStrafing = false;
        currentStrafeTarget = null;
    }

    private LivingEntity findTarget() {
        if (Killaura.currentTarget != null && Killaura.currentTarget.isAlive() && !AntiBot.isBot(Killaura.currentTarget)) {
            return Killaura.currentTarget;
        }
        if (mc.world != null && mc.player != null) {
            return StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                    .filter(e -> e instanceof PlayerEntity && e != mc.player && e.isAlive())
                    .map(e -> (LivingEntity) e)
                    .filter(e -> !AntiBot.isBot(e))
                    .filter(e -> mc.player.distanceTo(e) <= range.getValue())
                    .min(Comparator.comparingDouble(mc.player::distanceTo))
                    .orElse(null);
        }
        return null;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null || mc.world == null) {
            isStrafing = false;
            return;
        }

        Module speedMod = Night.INSTANCE.getModuleManager().getModule(Speed.class);
        boolean speedActive = speedMod != null && speedMod.isEnabled();

        if (onlySpeed.getValue() && !speedActive) {
            isStrafing = false;
            return;
        }

        LivingEntity target = findTarget();
        currentStrafeTarget = target;

        if (target == null || !target.isAlive() || mc.player.distanceTo(target) > range.getValue()) {
            isStrafing = false;
            return;
        }

        boolean hasInput = mc.player.input.getMovementInput().y != 0 || mc.player.input.getMovementInput().x != 0;
        if (!hasInput && !speedActive) {
            isStrafing = false;
            return;
        }

        isStrafing = true;

        // Anti-stuck direction flipping
        if (antiStuck.getValue()) {
            if (mc.player.horizontalCollision) {
                stuckTicks++;
                if (stuckTicks >= 2) {
                    direction = -direction;
                    stuckTicks = 0;
                }
            } else {
                stuckTicks = 0;
            }
        }

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double tx = target.getX();
        double tz = target.getZ();

        double currentAngle = Math.atan2(pz - tz, px - tx);
        double rad = radius.getValue();

        // Calculate next orbit angle
        double nextAngle = currentAngle + (direction * 0.28);
        double destX = tx + rad * Math.cos(nextAngle);
        double destZ = tz + rad * Math.sin(nextAngle);

        double moveAngle = Math.atan2(destZ - pz, destX - px);

        double moveSpeed = 0.2873;
        if (speedActive && speedMod instanceof Speed) {
            Speed spd = (Speed) speedMod;
            moveSpeed = spd.speed.getValue() * 0.36;
            if (mc.player.isOnGround()) {
                mc.player.jump();
            }
        }

        double motionX = Math.cos(moveAngle) * moveSpeed;
        double motionZ = Math.sin(moveAngle) * moveSpeed;

        mc.player.setVelocity(motionX, mc.player.getVelocity().y, motionZ);
    }
}
