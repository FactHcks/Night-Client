package me.night.command;

import me.night.command.commands.BindCommand;
import me.night.command.commands.ConfigCommand;

import java.util.*;

public class CommandManager {
    private final Map<String, Command> commandMap = new LinkedHashMap<>();
    private final List<Command> commands = new ArrayList<>();
    private String prefix = ".";

    public CommandManager() {
        register(new BindCommand());
        register(new ConfigCommand());
        register(new me.night.command.commands.WaypointCommand());
    }

    public void register(Command command) {
        commands.add(command);
        commandMap.put(command.getName().toLowerCase(Locale.ROOT), command);
        for (String alias : command.getAliases()) {
            commandMap.put(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    public boolean execute(String rawMessage) {
        if (!rawMessage.startsWith(prefix)) {
            return false;
        }

        String content = rawMessage.substring(prefix.length()).trim();
        if (content.isEmpty()) {
            return false;
        }

        String[] parts = content.split(" +");
        String name = parts[0].toLowerCase(Locale.ROOT);
        String[] args = (parts.length > 1) ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        Command cmd = commandMap.get(name);
        if (cmd != null) {
            cmd.execute(args);
        } else {
            Command.sendMessage("§cUnknown command: \"" + name + "\". Type .bind or .config");
        }

        return true;
    }

    public List<Command> getCommands() {
        return this.commands;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
