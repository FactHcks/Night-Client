package me.night.module.modules.movement;

import me.night.Night;
import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.module.modules.combat.Killaura;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Circles your Killaura target.
 * <p>
 * Instead of overwriting velocity, this only changes the direction your normal movement is
 * applied in (see MixinEntityMovement), so friction, sprinting, jumping and collisions all
 * behave exactly like vanilla.
 */
public class TargetStrafe extends Module {
    /** Movement direction (yaw, degrees) while strafing, or null when not strafing. */
    public static Float strafeYaw = null;
    public static boolean isStrafing = false;
    public static LivingEntity currentStrafeTarget = null;

    public final NumberProperty radius = new NumberProperty("Radius", "Distance to keep from the target", 2.5, 0.5, 5.0, 0.1);
    public final ModeProperty activation = new ModeProperty("Activate", "Moving", "Moving", "Jump Key", "Speed");
    public final BooleanProperty keyDirection = new BooleanProperty("A/D Direction", "Press A or D to pick which way to circle", true);
    public final BooleanProperty antiStuck = new BooleanProperty("Anti-Stuck", "Turn around when running into a wall", true);
    public final BooleanProperty avoidEdges = new BooleanProperty("Avoid Edges", "Turn around instead of walking off ledges or into lava", true);

    private int direction = 1;
    private int stuckTicks = 0;

    public TargetStrafe() {
        super("TargetStrafe", "Circles around your Killaura target", Category.MOVEMENT);
        addProperties(radius, activation, keyDirection, antiStuck, avoidEdges);
    }

    @Override
    public String getSuffix() {
        return String.format("%.1f", radius.getValue());
    }

    @Override
    protected void onDisable() {
        stop();
    }

    private void stop() {
        strafeYaw = null;
        isStrafing = false;
        currentStrafeTarget = null;
    }

    private boolean shouldActivate() {
        boolean moving = mc.player.input.getMovementInput().lengthSquared() > 0.0001;
        return switch (activation.getValue()) {
            case "Jump Key" -> mc.options.jumpKey.isPressed();
            case "Speed" -> moving && Night.INSTANCE.getModuleManager().getModule(Speed.class).isEnabled();
            default -> moving;
        };
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE) return;
        if (mc.player == null || mc.world == null) {
            stop();
            return;
        }

        LivingEntity target = Killaura.currentTarget;
        if (target == null || !target.isAlive() || mc.player.distanceTo(target) > radius.getValue() + 6 || !shouldActivate()) {
            stop();
            return;
        }

        // pick the direction with A / D
        if (keyDirection.getValue()) {
            boolean left = mc.options.leftKey.isPressed();
            boolean right = mc.options.rightKey.isPressed();
            if (left && !right) direction = 1;
            if (right && !left) direction = -1;
        }

        // turn around after bumping into a wall for a couple of ticks
        if (antiStuck.getValue() && mc.player.horizontalCollision) {
            if (++stuckTicks >= 2) {
                direction = -direction;
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }

        double[] dest = nextPoint(target, direction);
        if (avoidEdges.getValue() && !safe(dest[0], dest[1])) {
            direction = -direction;
            dest = nextPoint(target, direction);
            if (!safe(dest[0], dest[1])) {
                stop(); // both ways are unsafe: leave movement alone
                return;
            }
        }

        double dx = dest[0] - mc.player.getX();
        double dz = dest[1] - mc.player.getZ();
        strafeYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        isStrafing = true;
        currentStrafeTarget = target;
    }

    /** A point a little further along the circle around the target. */
    private double[] nextPoint(LivingEntity target, int dir) {
        double tx = target.getX(), tz = target.getZ();
        double angle = Math.atan2(mc.player.getZ() - tz, mc.player.getX() - tx);
        double r = radius.getValue();
        // step along the circle by roughly the distance we move per tick
        double speed = Math.max(0.2, mc.player.getVelocity().horizontalLength());
        double step = Math.max(0.08, Math.min(0.9, (speed * 1.6) / r)) * dir;
        return new double[]{tx + Math.cos(angle + step) * r, tz + Math.sin(angle + step) * r};
    }

    /** True if standing at (x, z) won't drop us more than 3 blocks or into lava/fire. */
    private boolean safe(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int feet = (int) Math.floor(mc.player.getY());
        for (int y = feet - 1; y >= feet - 3; y--) {
            BlockState state = mc.world.getBlockState(new BlockPos(bx, y, bz));
            if (state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA)
                    || state.isOf(net.minecraft.block.Blocks.FIRE) || state.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)) {
                return false;
            }
            if (!state.getCollisionShape(mc.world, new BlockPos(bx, y, bz)).isEmpty()) return true;
        }
        return !mc.player.isOnGround(); // already in the air: don't block the move
    }
}
