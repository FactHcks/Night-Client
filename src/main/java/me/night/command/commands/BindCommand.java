package me.night.command.commands;

import me.night.Night;
import me.night.command.Command;
import me.night.module.Module;
import me.night.util.KeyUtil;

public class BindCommand extends Command {
    public BindCommand() {
        super("bind", "Binds a module to a key", ".bind <module> <key/none>", "b");
    }

    @Override
    public void execute(String[] args) {
        if (args == null || args.length == 0) {
            sendMessage("§7Usage: §f" + getSyntax());
            return;
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("list")) {
                sendMessage("§7-- Module Keybinds --");
                int count = 0;
                for (Module mod : Night.INSTANCE.getModuleManager().getModules()) {
                    if (mod.getKey() > 0) {
                        sendMessage("§c" + mod.getName() + " §7-> §f" + KeyUtil.getKeyName(mod.getKey()));
                        count++;
                    }
                }
                if (count == 0) {
                    sendMessage("No active keybinds set.");
                }
                return;
            }

            Module mod = Night.INSTANCE.getModuleManager().getModule(args[0]);
            if (mod != null) {
                sendMessage("§c" + mod.getName() + " §fis currently bound to §7" + KeyUtil.getKeyName(mod.getKey()));
            } else {
                sendMessage("§cModule \"" + args[0] + "\" not found! Type .bind list for all binds.");
            }
            return;
        }

        String modName = args[0];
        String keyName = args[1];

        Module mod = Night.INSTANCE.getModuleManager().getModule(modName);
        if (mod == null) {
            sendMessage("§cModule \"" + modName + "\" not found!");
            return;
        }

        if (keyName.equalsIgnoreCase("none") || keyName.equalsIgnoreCase("0") || keyName.equalsIgnoreCase("clear")) {
            mod.setKey(0);
            sendMessage("Unbound §c" + mod.getName() + "§f.");
            return;
        }

        int key = KeyUtil.getKeyFromName(keyName);
        if (key == 0) {
            sendMessage("§cUnknown key: \"" + keyName + "\"! Examples: R, F, RShift, Space, LAlt.");
            return;
        }

        mod.setKey(key);
        sendMessage("Bound §c" + mod.getName() + " §fto §7" + KeyUtil.getKeyName(key) + "§f.");
    }
}
