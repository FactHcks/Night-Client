package me.night.module.modules.combat;

import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.util.PacketUtil;
import me.night.util.TimerUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class Criticals extends Module {
    private static Criticals INSTANCE;

    public final ModeProperty mode = new ModeProperty("Mode", "Packet",
            "Packet", "Mini Jump", "Jump", "Hypixel", "Vulcan", "Delay");
    public final NumberProperty delay = new NumberProperty("Delay", "Delay between critical hits in ms", 400.0, 0.0, 1500.0, 50.0);
    public final BooleanProperty visual = new BooleanProperty("Visual", "Display critical particles", true);
    public final BooleanProperty onlyOnGround = new BooleanProperty("Only On Ground", "Only crit when on ground", true);

    private final TimerUtil timer = new TimerUtil();
    private int vulcanHits = 0;

    public Criticals() {
        super("Criticals", "Forces critical hits on attacks", Category.COMBAT);
        addProperties(mode, delay, visual, onlyOnGround);
        INSTANCE = this;
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @Override
    protected void onDisable() {
        vulcanHits = 0;
        timer.reset();
    }

    public static void onAttack(Entity target) {
        Criticals crits = INSTANCE;
        if (crits == null || !crits.isEnabled() || mc.player == null || mc.world == null) return;
        if (!(target instanceof LivingEntity)) return;

        // Cannot critical while in liquid, climbing, or in vehicle
        if (mc.player.isTouchingWater() || mc.player.isClimbing() || mc.player.hasVehicle()) return;

        // Ground check
        if (crits.onlyOnGround.getValue() && !mc.player.isOnGround()) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        boolean hCol = mc.player.horizontalCollision;

        switch (crits.mode.getValue()) {
            case "Packet" -> {
                if (crits.timer.hasTimeElapsed(crits.delay.getValue().longValue())) {
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, hCol));
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, hCol));
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0125, z, false, hCol));
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, hCol));

                    crits.timer.reset();
                    if (crits.visual.getValue()) {
                        mc.player.addCritParticles(target);
                    }
                }
            }
            case "Mini Jump" -> {
                if (crits.timer.hasTimeElapsed(crits.delay.getValue().longValue())) {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0.18, mc.player.getVelocity().z);
                    crits.timer.reset();
                    if (crits.visual.getValue()) {
                        mc.player.addCritParticles(target);
                    }
                }
            }
            case "Jump" -> {
                if (mc.player.isOnGround() && crits.timer.hasTimeElapsed(crits.delay.getValue().longValue())) {
                    mc.player.jump();
                    crits.timer.reset();
                }
            }
            case "Hypixel" -> {
                long minDelay = Math.max(400L, crits.delay.getValue().longValue());
                if (crits.timer.hasTimeElapsed(minDelay)) {
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0525000000122, z, false, hCol));
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0015000000122, z, false, hCol));
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0140000000122, z, false, hCol));
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0015000000122, z, false, hCol));

                    crits.timer.reset();
                    if (crits.visual.getValue()) {
                        mc.player.addCritParticles(target);
                    }
                }
            }
            case "Vulcan" -> {
                crits.vulcanHits++;
                if (crits.vulcanHits % 3 == 0) {
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.16477328182606651, z, false, hCol));
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.08307781780646721, z, false, hCol));
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.00301626150914247, z, false, hCol));

                    if (crits.visual.getValue()) {
                        mc.player.addCritParticles(target);
                    }
                }
            }
            case "Delay" -> {
                if (crits.timer.hasTimeElapsed(crits.delay.getValue().longValue())) {
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, hCol));
                    PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, hCol));

                    crits.timer.reset();
                    if (crits.visual.getValue()) {
                        mc.player.addCritParticles(target);
                    }
                }
            }
        }
    }
}
