package me.night.module.modules.player;

import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.ModeProperty;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class NoFall extends Module {
    public final ModeProperty mode = new ModeProperty("Mode", "Packet", "Packet", "Spoof");

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.PLAYER);
        addProperties(mode);
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player != null && mc.player.fallDistance > 2.5F) {
            if (mode.is("Packet") && mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
            }
        }
    }
}
