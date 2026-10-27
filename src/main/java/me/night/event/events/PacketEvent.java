package me.night.event.events;

import me.night.event.Event;
import net.minecraft.network.packet.Packet;

public class PacketEvent extends Event {
    public enum Direction {
        SEND, RECEIVE
    }

    private final Packet<?> packet;
    private final Direction direction;

    public PacketEvent(Packet<?> packet, Direction direction) {
        this.packet = packet;
        this.direction = direction;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public boolean isSend() {
        return this.direction == Direction.SEND;
    }

    public boolean isReceive() {
        return this.direction == Direction.RECEIVE;
    }
}
