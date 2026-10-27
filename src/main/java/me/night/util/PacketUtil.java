package me.night.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

public final class PacketUtil {
    private static final ThreadLocal<Boolean> SILENT = ThreadLocal.withInitial(() -> false);

    private PacketUtil() {}

    public static boolean isSilent() {
        return SILENT.get();
    }

    public static void sendPacket(Packet<?> packet) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null && packet != null) {
            mc.getNetworkHandler().sendPacket(packet);
        }
    }

    public static void sendPacketNoEvent(Packet<?> packet) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null && packet != null) {
            SILENT.set(true);
            try {
                mc.getNetworkHandler().sendPacket(packet);
            } finally {
                SILENT.set(false);
            }
        }
    }
}
