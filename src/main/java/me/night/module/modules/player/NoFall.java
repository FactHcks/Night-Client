package me.night.module.modules.player;

import me.night.event.EventTarget;
import me.night.event.events.PacketEvent;
import me.night.event.events.TickEvent;
import me.night.mixin.IPlayerMoveC2SPacket;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.util.PacketUtil;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class NoFall extends Module {
    public final ModeProperty mode = new ModeProperty("Mode", "Packet",
            "Packet", "Spoof", "NoGround", "Blink", "Hypixel", "Vulcan", "Catch");
    public final NumberProperty distance = new NumberProperty("Distance", "Fall distance to trigger", 3.0, 1.0, 10.0, 0.5);

    private final List<Packet<?>> blinkedPackets = new ArrayList<>();
    private boolean blinking = false;

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.PLAYER);
        addProperties(mode, distance);
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @Override
    protected void onDisable() {
        flushBlink();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        double distThresh = distance.getValue();

        switch (mode.getValue()) {
            case "Packet" -> {
                if (mc.player.fallDistance > distThresh && mc.getNetworkHandler() != null) {
                    if (!mc.player.isTouchingWater() && !mc.player.isClimbing()) {
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
                        mc.player.fallDistance = 0.0F;
                    }
                }
            }
            case "Hypixel" -> {
                if (mc.player.fallDistance > distThresh && !isOverVoid()) {
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));
                    mc.player.fallDistance = 0.0F;
                }
            }
            case "Vulcan" -> {
                if (mc.player.fallDistance > 3.2F) {
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX(), mc.player.getY() - (mc.player.fallDistance % 3.0), mc.player.getZ(), true, mc.player.horizontalCollision));
                    mc.player.fallDistance = 0.0F;
                }
            }
            case "Catch" -> {
                if (mc.player.fallDistance > 2.5F && mc.player.getVelocity().y < -0.5) {
                    if (isSolidBelow(1.5)) {
                        mc.player.setVelocity(mc.player.getVelocity().x, -0.05, mc.player.getVelocity().z);
                        PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(
                                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));
                        mc.player.fallDistance = 0.0F;
                    }
                }
            }
            case "Blink" -> {
                if (mc.player.isOnGround() && blinking) {
                    landBlink();
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || !event.isSend()) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket movePacket) {
            switch (mode.getValue()) {
                case "Spoof" -> {
                    if (!mc.player.isOnGround() && mc.player.fallDistance > 1.5F) {
                        if (movePacket instanceof IPlayerMoveC2SPacket accessor) {
                            accessor.setOnGround(true);
                            mc.player.fallDistance = 0.0F;
                        }
                    }
                }
                case "NoGround" -> {
                    if (movePacket instanceof IPlayerMoveC2SPacket accessor) {
                        accessor.setOnGround(false);
                    }
                }
                case "Blink" -> {
                    boolean allowed = !mc.player.isClimbing() && !mc.player.isTouchingWater() && !mc.player.getAbilities().allowFlying;
                    if (!allowed || isOverVoid()) {
                        flushBlink();
                        return;
                    }
                    if (mc.player.isOnGround()) {
                        landBlink();
                    } else if (mc.player.fallDistance > 1.0F && mc.player.getVelocity().y < -0.08) {
                        blinking = true;
                        blinkedPackets.add(event.getPacket());
                        event.cancel();
                    }
                }
                case "Hypixel" -> {
                    if (mc.player.fallDistance > distance.getValue() && !mc.player.isOnGround() && !isOverVoid()) {
                        if (movePacket instanceof IPlayerMoveC2SPacket accessor) {
                            accessor.setOnGround(true);
                        }
                    }
                }
            }
        }
    }

    private void flushBlink() {
        if (!blinkedPackets.isEmpty()) {
            for (Packet<?> p : blinkedPackets) {
                PacketUtil.sendPacketNoEvent(p);
            }
            blinkedPackets.clear();
        }
        blinking = false;
    }

    private void landBlink() {
        if (!blinkedPackets.isEmpty()) {
            for (Packet<?> p : blinkedPackets) {
                if (p instanceof IPlayerMoveC2SPacket accessor) {
                    accessor.setOnGround(true);
                }
                PacketUtil.sendPacketNoEvent(p);
            }
            blinkedPackets.clear();
        }
        blinking = false;
        if (mc.player != null) {
            mc.player.fallDistance = 0.0F;
        }
    }

    private boolean isSolidBelow(double maxDist) {
        if (mc.world == null || mc.player == null) return false;
        BlockPos pos = mc.player.getBlockPos();
        for (int y = 0; y <= (int) Math.ceil(maxDist) + 2; y++) {
            BlockPos checkPos = pos.down(y);
            BlockState state = mc.world.getBlockState(checkPos);
            if (!state.isAir() && !state.getCollisionShape(mc.world, checkPos).isEmpty()) {
                double dist = mc.player.getY() - (checkPos.getY() + 1.0);
                if (dist >= 0 && dist <= maxDist) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOverVoid() {
        if (mc.world == null || mc.player == null) return false;
        BlockPos pos = mc.player.getBlockPos();
        int minY = mc.world.getBottomY();
        for (int y = pos.getY(); y >= minY; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState state = mc.world.getBlockState(checkPos);
            if (!state.isAir() && !state.getCollisionShape(mc.world, checkPos).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
