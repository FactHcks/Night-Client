package me.night.module.modules.player;

import me.night.event.EventTarget;
import me.night.event.events.PacketEvent;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PingSpoof extends Module {
    public final NumberProperty delay = new NumberProperty("Delay (ms)", "Ping delay in milliseconds", 250.0, 50.0, 5000.0, 25.0);
    public final BooleanProperty keepAlive = new BooleanProperty("KeepAlive", "Delay KeepAlive packets", true);
    public final BooleanProperty ping = new BooleanProperty("Pong", "Delay CommonPong packets", true);

    private static class DelayedPacket {
        final Packet<?> packet;
        final long sendTime;

        DelayedPacket(Packet<?> packet, long sendTime) {
            this.packet = packet;
            this.sendTime = sendTime;
        }
    }

    private final Queue<DelayedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private boolean flushing = false;

    public PingSpoof() {
        super("PingSpoof", "Artificially delays ping / keepalive packets", Category.PLAYER);
        addProperties(delay, keepAlive, ping);
    }

    @Override
    public String getSuffix() {
        return delay.getInt() + "ms";
    }

    @Override
    protected void onDisable() {
        flushAll();
    }

    private void flushAll() {
        if (mc.getNetworkHandler() == null) {
            packetQueue.clear();
            return;
        }
        flushing = true;
        while (!packetQueue.isEmpty()) {
            DelayedPacket dp = packetQueue.poll();
            if (dp != null && mc.getNetworkHandler().getConnection() != null) {
                mc.getNetworkHandler().getConnection().send(dp.packet, null);
            }
        }
        flushing = false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (flushing || !event.isSend() || mc.player == null) return;

        Packet<?> packet = event.getPacket();
        boolean isKeepAlive = (packet instanceof KeepAliveC2SPacket) && keepAlive.getValue();
        boolean isPong = (packet instanceof CommonPongC2SPacket) && ping.getValue();

        if (isKeepAlive || isPong) {
            event.cancel();
            long sendTime = System.currentTimeMillis() + delay.getInt();
            packetQueue.add(new DelayedPacket(packet, sendTime));
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            packetQueue.clear();
            return;
        }

        if (event.getPhase() != TickEvent.Phase.PRE || packetQueue.isEmpty() || mc.getNetworkHandler() == null) return;

        long now = System.currentTimeMillis();
        flushing = true;
        while (!packetQueue.isEmpty()) {
            DelayedPacket head = packetQueue.peek();
            if (head != null && now >= head.sendTime) {
                packetQueue.poll();
                if (mc.getNetworkHandler().getConnection() != null) {
                    mc.getNetworkHandler().getConnection().send(head.packet, null);
                }
            } else {
                break;
            }
        }
        flushing = false;
    }
}
