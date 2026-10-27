package me.night.command.commands;

import me.night.command.Command;
import me.night.waypoint.Waypoint;
import me.night.waypoint.WaypointManager;

import java.util.List;

public class WaypointCommand extends Command {
    public WaypointCommand() {
        super("waypoint", "Adds, removes and lists waypoints for this world", ".waypoint <add/remove/list/clear> [name] [x y z]", "wp");
    }

    @Override
    public void execute(String[] args) {
        if (args == null || args.length == 0) {
            sendMessage("§7Usage: §f" + getSyntax());
            return;
        }
        switch (args[0].toLowerCase()) {
            case "add", "set" -> {
                if (args.length < 2 || mc.player == null) {
                    sendMessage("§cUsage: .waypoint add <name> [x y z]");
                    return;
                }
                int x = mc.player.getBlockX(), y = mc.player.getBlockY(), z = mc.player.getBlockZ();
                if (args.length >= 5) {
                    try {
                        x = Integer.parseInt(args[2]);
                        y = Integer.parseInt(args[3]);
                        z = Integer.parseInt(args[4]);
                    } catch (NumberFormatException e) {
                        sendMessage("§cCoordinates must be whole numbers.");
                        return;
                    }
                }
                WaypointManager.add(args[1], x, y, z);
                sendMessage("Added waypoint §c" + args[1] + "§f at " + x + ", " + y + ", " + z + ".");
            }
            case "remove", "del", "delete" -> {
                if (args.length < 2) {
                    sendMessage("§cUsage: .waypoint remove <name>");
                    return;
                }
                sendMessage(WaypointManager.remove(args[1]) ? "Removed waypoint §c" + args[1] + "§f." : "§cNo waypoint called " + args[1] + ".");
            }
            case "list" -> {
                List<Waypoint> list = WaypointManager.inWorld();
                if (list.isEmpty()) {
                    sendMessage("No waypoints in this world yet.");
                    return;
                }
                sendMessage("§7-- Waypoints (" + list.size() + ") --");
                for (Waypoint w : list) {
                    String dim = w.dimension().replace("minecraft:", "");
                    sendMessage("§8- §c" + w.name() + " §7" + w.x() + ", " + w.y() + ", " + w.z() + " §8(" + dim + ")");
                }
            }
            case "clear" -> sendMessage("Removed " + WaypointManager.clear() + " waypoints.");
            default -> sendMessage("§cUnknown option. Use add, remove, list or clear.");
        }
    }
}
