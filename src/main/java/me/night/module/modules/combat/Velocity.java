package me.night.module.modules.combat;

import me.night.event.EventTarget;
import me.night.event.events.PacketEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

public class Velocity extends Module {

    private final NumberProperty horizontal = new NumberProperty("Horizontal %", "Horizontal knockback percentage", 0.0, 0.0, 100.0, 5.0);
    private final NumberProperty vertical = new NumberProperty("Vertical %", "Vertical knockback percentage", 0.0, 0.0, 100.0, 5.0);
    private final BooleanProperty explosions = new BooleanProperty("Explosions", "Apply knockback reduction to explosion packets", true);

    public Velocity() {
        super("Velocity", "Reduces or eliminates incoming knockback", Category.COMBAT);
        addProperties(horizontal, vertical, explosions);
    }

    @Override
    public String getSuffix() {
        return String.format("%d%% %d%%", horizontal.getInt(), vertical.getInt());
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isReceive() || mc.player == null) {
            return;
        }

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocityPacket) {
            if (velocityPacket.getEntityId() == mc.player.getId()) {
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
            }
        } else if (explosions.getValue() && event.getPacket() instanceof ExplosionS2CPacket explosion) {
            if (explosion.playerKnockback().isPresent()) {
                double hRatio = horizontal.getValue() / 100.0;
                double vRatio = vertical.getValue() / 100.0;

                if (hRatio == 0.0 && vRatio == 0.0) {
                    event.cancel();
                } else {
                    event.cancel();
                    Vec3d kb = explosion.playerKnockback().get();
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.addVelocity(kb.x * hRatio, kb.y * vRatio, kb.z * hRatio);
                        }
                    });
                }
            }
        }
    }
}
