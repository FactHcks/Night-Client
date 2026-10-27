package me.night.command.commands;

import me.night.command.Command;
import me.night.config.ConfigManager;

import java.util.List;

public class ConfigCommand extends Command {
    public ConfigCommand() {
        super("config", "Saves and loads configuration files including module binds", ".config <save/load/list/delete> [name]", "cfg");
    }

    @Override
    public void execute(String[] args) {
        if (args == null || args.length == 0) {
            sendMessage("§7Usage: §f" + getSyntax());
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "save":
            case "s":
            case "create":
                if (args.length < 2) {
                    sendMessage("§cUsage: .config save <name>");
                    return;
                }
                String saveName = args[1];
                if (ConfigManager.saveConfig(saveName)) {
                    sendMessage("Successfully saved config §c\"" + saveName + "\" §f(including binds)!");
                } else {
                    sendMessage("§cFailed to save config \"" + saveName + "\"!");
                }
                break;

            case "load":
            case "l":
                if (args.length < 2) {
                    sendMessage("§cUsage: .config load <name>");
                    return;
                }
                String loadName = args[1];
                if (ConfigManager.loadConfig(loadName)) {
                    sendMessage("Successfully loaded config §c\"" + loadName + "\" §f(restored binds & settings)!");
                } else {
                    sendMessage("§cConfig \"" + loadName + "\" does not exist! Type .config list to view all.");
                }
                break;

            case "delete":
            case "del":
            case "d":
                if (args.length < 2) {
                    sendMessage("§cUsage: .config delete <name>");
                    return;
                }
                String delName = args[1];
                if (ConfigManager.deleteConfig(delName)) {
                    sendMessage("Deleted config §c\"" + delName + "\"§f.");
                } else {
                    sendMessage("§cConfig \"" + delName + "\" not found!");
                }
                break;

            case "list":
                List<String> configs = ConfigManager.getConfigs();
                if (configs.isEmpty()) {
                    sendMessage("No saved configs found in night/configs/.");
                } else {
                    sendMessage("§7-- Available Configs (" + configs.size() + ") --");
                    for (String cfg : configs) {
                        sendMessage("§8- §c" + cfg);
                    }
                }
                break;

            default:
                sendMessage("§cUnknown subcommand: \"" + sub + "\"! Valid options: save, load, list, delete.");
                break;
        }
    }
}
