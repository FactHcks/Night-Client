package me.night.module.modules.combat;

import me.night.Night;
import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class AntiBot extends Module {

    private final ModeProperty mode = new ModeProperty("Mode", "Hypixel", "Hypixel", "Advanced");
    private final BooleanProperty watchdog = new BooleanProperty("Watchdog", "Detects Hypixel Watchdog orbit bots", true);
    private final BooleanProperty tabList = new BooleanProperty("TabList", "Detects entities missing from player tab list", true);
    private final BooleanProperty groundCheck = new BooleanProperty("Ground Check", "Detects entities that never touch ground", true);
    private final BooleanProperty dead = new BooleanProperty("Dead", "Filters out dead and sleeping ghost bodies", true);
    private final BooleanProperty remove = new BooleanProperty("Remove", "Removes detected bots completely from world", true);

    private static final Set<Integer> botIds = new HashSet<>();
    private static final Set<UUID> botUuids = new HashSet<>();
    private static final Map<Integer, Integer> ticksOnGroundMap = new HashMap<>();

    public AntiBot() {
        super("AntiBot", "Prevents targeting Watchdog and server anticheat bots", Category.COMBAT);
        addProperties(mode, watchdog, tabList, groundCheck, dead, remove);
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @Override
    protected void onDisable() {
        clear();
    }

    public static void clear() {
        botIds.clear();
        botUuids.clear();
        ticksOnGroundMap.clear();
    }

    public static boolean isBot(Entity entity) {
        if (entity == null || !(entity instanceof PlayerEntity player)) {
            return false;
        }

        if (Night.INSTANCE == null || Night.INSTANCE.getModuleManager() == null) {
            return false;
        }

        AntiBot antiBot = Night.INSTANCE.getModuleManager().getModule(AntiBot.class);
        if (antiBot == null || !antiBot.isEnabled()) {
            return false;
        }

        return antiBot.isEntityBot(player);
    }

    public boolean isEntityBot(PlayerEntity player) {
        if (player == null || player == mc.player) {
            return false;
        }

        int id = player.getId();
        UUID uuid = player.getUuid();

        if (botIds.contains(id) || botUuids.contains(uuid)) {
            return true;
        }

        // Dead / sleeping ghost body check
        if (dead.getValue() && (!player.isAlive() || player.getHealth() <= 0.0F || player.isSleeping())) {
            markBot(player);
            return true;
        }

        // NPC naming format checks
        String name = player.getName().getString();
        if (name.contains("[NPC]") || name.contains("[CIT]") || name.startsWith("CIT-") || name.trim().isEmpty()) {
            markBot(player);
            return true;
        }

        // TabList check (multiplayer)
        if (tabList.getValue() && mc.getNetworkHandler() != null && !mc.isInSingleplayer() && player.age > 8) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry == null) {
                markBot(player);
                return true;
            }
        }

        // Ground check (TOG)
        if (groundCheck.getValue() && player.age > 20 && mc.player != null) {
            int tog = ticksOnGroundMap.getOrDefault(id, 0);
            if (tog < -15 && mc.player.distanceTo(player) < 8.0) {
                markBot(player);
                return true;
            }
        }

        return false;
    }

    private void markBot(PlayerEntity player) {
        botIds.add(player.getId());
        botUuids.add(player.getUuid());
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null || mc.world == null) {
            return;
        }

        // Clean up entities that left the world
        botIds.removeIf(id -> mc.world.getEntityById(id) == null);
        ticksOnGroundMap.keySet().removeIf(id -> mc.world.getEntityById(id) == null);

        List<Integer> toRemove = new ArrayList<>();

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                continue;
            }

            int id = player.getId();
            UUID uuid = player.getUuid();

            // 1. Ticks on ground tracking
            boolean onGround = player.isOnGround() || isTouchingGround(player);
            int currentTog = ticksOnGroundMap.getOrDefault(id, 0);
            if (onGround) {
                ticksOnGroundMap.put(id, Math.min(20, currentTog + 2));
            } else {
                ticksOnGroundMap.put(id, Math.max(-50, currentTog - 1));
            }

            // 2. Watchdog Orbiting Detection (Hypixel Watchdog bot behavior)
            if (watchdog.getValue()) {
                double dist = mc.player.distanceTo(player);
                double deltaY = player.getY() - mc.player.getY();

                // Watchdog aura check bots orbit between 1.2m and 4.5m, hovering at head/eye level
                if (dist >= 1.2 && dist <= 4.5 && deltaY >= -0.5 && deltaY <= 3.5 && !onGround) {
                    double speed = Math.hypot(player.getX() - player.lastRenderX, player.getZ() - player.lastRenderZ);
                    // Rapid circular orbit without landing
                    if (player.age > 5 && (speed > 0.12 || ticksOnGroundMap.getOrDefault(id, 0) < -5)) {
                        PlayerListEntry entry = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerListEntry(uuid) : null;
                        if (entry == null || player.getName().getString().contains("[NPC]")) {
                            markBot(player);
                        }
                    }
                }
            }

            // 3. TabList Check
            if (tabList.getValue() && mc.getNetworkHandler() != null && !mc.isInSingleplayer() && player.age > 8) {
                if (mc.getNetworkHandler().getPlayerListEntry(uuid) == null) {
                    markBot(player);
                }
            }

            // 4. Dead check
            if (dead.getValue() && (!player.isAlive() || player.getHealth() <= 0.0F || player.isSleeping())) {
                markBot(player);
            }

            // 5. Queued removal
            if (remove.getValue() && (botIds.contains(id) || botUuids.contains(uuid))) {
                toRemove.add(id);
            }
        }

        // Safely remove flagged bots from client world so they disappear
        for (int remId : toRemove) {
            mc.world.removeEntity(remId, Entity.RemovalReason.DISCARDED);
        }
    }

    private boolean isTouchingGround(PlayerEntity player) {
        if (mc.world == null) return false;
        BlockPos pos = player.getBlockPos();
        return !mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos.down()).isAir();
    }
}
