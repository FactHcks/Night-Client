package me.night.mixin;

import io.netty.channel.ChannelHandlerContext;
import me.night.Night;
import me.night.event.events.PacketEvent;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class MixinClientConnection {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (me.night.util.PacketUtil.isSilent()) {
            return;
        }
        if (Night.INSTANCE != null && Night.INSTANCE.getEventManager() != null) {
            PacketEvent event = new PacketEvent(packet, PacketEvent.Direction.SEND);
            Night.INSTANCE.getEventManager().post(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onReceivePacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (Night.INSTANCE != null && Night.INSTANCE.getEventManager() != null) {
            PacketEvent event = new PacketEvent(packet, PacketEvent.Direction.RECEIVE);
            Night.INSTANCE.getEventManager().post(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }
}
