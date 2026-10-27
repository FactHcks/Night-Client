package me.night.module.modules.combat;

import me.night.event.EventTarget;
import me.night.event.events.PacketEvent;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Velocity extends Module {

    private record DelayedPacket(Packet<ClientPlayPacketListener> packet, long applyTime) {}

    public final ModeProperty mode = new ModeProperty("Mode", "Standard", "Standard", "Jump Reset", "Delay");
    public final NumberProperty horizontal = new NumberProperty("Horizontal %", "Horizontal knockback percentage",
            0.0, 0.0, 100.0, 5.0, () -> mode.is("Standard") || mode.is("Delay"));
    public final NumberProperty vertical = new NumberProperty("Vertical %", "Vertical knockback percentage",
            0.0, 0.0, 100.0, 5.0, () -> mode.is("Standard") || mode.is("Delay"));
    public final NumberProperty delay = new NumberProperty("Delay (ms)", "Delay before velocity packet is applied",
            200, 50, 1000, 25, () -> mode.is("Delay"));
    public final BooleanProperty flushOnHit = new BooleanProperty("Flush On Hit", "Flush delayed packets upon attacking",
            false, () -> mode.is("Delay"));
    public final NumberProperty jumpResetChance = new NumberProperty("Chance", "Chance to trigger jump reset",
            100, 0, 100, 5, () -> mode.is("Jump Reset"));
    public final BooleanProperty jumpResetOnlyGround = new BooleanProperty("Only Ground", "Only jump reset when on ground",
            true, () -> mode.is("Jump Reset"));
    public final BooleanProperty jumpResetOnlyMoving = new BooleanProperty("Only Moving", "Only jump reset when moving forward",
            false, () -> mode.is("Jump Reset"));
    public final BooleanProperty explosions = new BooleanProperty("Explosions", "Apply knockback reduction to explosion packets", true);

    private final Queue<DelayedPacket> delayedPackets = new ConcurrentLinkedQueue<>();
    private boolean shouldJumpReset = false;

    public Velocity() {
        super("Velocity", "Reduces or eliminates incoming knockback", Category.COMBAT);
        addProperties(mode, horizontal, vertical, delay, flushOnHit, jumpResetChance, jumpResetOnlyGround, jumpResetOnlyMoving, explosions);
    }

    @Override
    public String getSuffix() {
        if (mode.is("Standard")) {
            return String.format("%d%% %d%%", horizontal.getInt(), vertical.getInt());
        } else if (mode.is("Delay")) {
            return delay.getInt() + "ms";
        } else {
            return "Jump Reset";
        }
    }

    @Override
    protected void onDisable() {
        flushAll();
        shouldJumpReset = false;
    }

    private void flushAll() {
        while (!delayedPackets.isEmpty()) {
            DelayedPacket dp = delayedPackets.poll();
            if (dp != null) {
                applyDelayedPacket(dp.packet());
            }
        }
    }

    private void applyDelayedPacket(Packet<ClientPlayPacketListener> packet) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;

        double hRatio = horizontal.getValue() / 100.0;
        double vRatio = vertical.getValue() / 100.0;

        if (packet instanceof EntityVelocityUpdateS2CPacket velPacket) {
            EntityVelocityUpdateS2CPacket packetToApply;
            if (hRatio == 1.0 && vRatio == 1.0) {
                packetToApply = velPacket;
            } else {
                Vec3d v = velPacket.getVelocity();
                packetToApply = new EntityVelocityUpdateS2CPacket(
                        velPacket.getEntityId(),
                        new Vec3d(v.x * hRatio, v.y * vRatio, v.z * hRatio)
                );
            }
            packetToApply.apply(mc.getNetworkHandler());
        } else if (packet instanceof ExplosionS2CPacket explosion) {
            explosion.apply(mc.getNetworkHandler());
            if (hRatio != 1.0 || vRatio != 1.0) {
                if (explosion.playerKnockback().isPresent()) {
                    Vec3d kb = explosion.playerKnockback().get();
                    mc.player.addVelocity(-kb.x * (1.0 - hRatio), -kb.y * (1.0 - vRatio), -kb.z * (1.0 - hRatio));
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null) return;

        // Process delayed velocity packets
        if (mode.is("Delay") && !delayedPackets.isEmpty()) {
            long now = System.currentTimeMillis();
            while (!delayedPackets.isEmpty()) {
                DelayedPacket head = delayedPackets.peek();
                if (head != null && now >= head.applyTime()) {
                    delayedPackets.poll();
                    applyDelayedPacket(head.packet());
                } else {
                    break;
                }
            }
        }

        // Process Jump Reset
        if (mode.is("Jump Reset") && shouldJumpReset) {
            boolean groundOk = !jumpResetOnlyGround.getValue() || mc.player.isOnGround();
            boolean moveOk = !jumpResetOnlyMoving.getValue() || mc.player.input.hasForwardMovement();
            if (groundOk && moveOk) {
                mc.player.jump();
                shouldJumpReset = false;
            } else if (mc.player.isOnGround()) {
                shouldJumpReset = false;
            }
        }
    }

    @EventTarget
    @SuppressWarnings("unchecked")
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        if (event.isSend() && mode.is("Delay") && flushOnHit.getValue()) {
            if (event.getPacket() instanceof PlayerInteractEntityC2SPacket) {
                flushAll();
            }
            return;
        }

        if (!event.isReceive()) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocityPacket) {
            if (velocityPacket.getEntityId() == mc.player.getId()) {
                if (mode.is("Standard")) {
                    double hRatio = horizontal.getValue() / 100.0;
                    double vRatio = vertical.getValue() / 100.0;

                    if (hRatio == 0.0 && vRatio == 0.0) {
                        event.cancel();
                    } else {
                        event.cancel();
                        Vec3d packetVel = velocityPacket.getVelocity();
                        mc.execute(() -> {
                            if (mc.player != null) {
                                Vec3d current = mc.player.getVelocity();
                                double nx = (hRatio == 0.0) ? current.x : packetVel.x * hRatio;
                                double ny = (vRatio == 0.0) ? current.y : packetVel.y * vRatio;
                                double nz = (hRatio == 0.0) ? current.z : packetVel.z * hRatio;
                                mc.player.setVelocity(nx, ny, nz);
                            }
                        });
                    }
                } else if (mode.is("Delay")) {
                    // Intercept and delay the velocity packet itself
                    event.cancel();
                    long applyAt = System.currentTimeMillis() + delay.getInt();
                    delayedPackets.add(new DelayedPacket((Packet<ClientPlayPacketListener>) event.getPacket(), applyAt));
                } else if (mode.is("Jump Reset")) {
                    // Do not cancel packet - let vanilla apply velocity, then counter with jump
                    if (Math.random() * 100.0 <= jumpResetChance.getValue()) {
                        mc.execute(() -> {
                            if (mc.player != null) {
                                boolean groundOk = !jumpResetOnlyGround.getValue() || mc.player.isOnGround();
                                boolean moveOk = !jumpResetOnlyMoving.getValue() || mc.player.input.hasForwardMovement();
                                if (groundOk && moveOk) {
                                    mc.player.jump();
                                } else {
                                    shouldJumpReset = true;
                                }
                            }
                        });
                    }
                }
            }
        } else if (explosions.getValue() && event.getPacket() instanceof ExplosionS2CPacket explosion) {
            if (explosion.playerKnockback().isPresent()) {
                if (mode.is("Delay")) {
                    event.cancel();
                    long applyAt = System.currentTimeMillis() + delay.getInt();
                    delayedPackets.add(new DelayedPacket((Packet<ClientPlayPacketListener>) event.getPacket(), applyAt));
                } else if (mode.is("Standard")) {
                    double hRatio = horizontal.getValue() / 100.0;
                    double vRatio = vertical.getValue() / 100.0;
                    Vec3d kb = explosion.playerKnockback().get();
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.addVelocity(-kb.x * (1 - hRatio), -kb.y * (1 - vRatio), -kb.z * (1 - hRatio));
                        }
                    });
                }
            }
        }
    }
}
