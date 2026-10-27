package me.night.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public abstract class Command {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final String CHAT_PREFIX = "§d§lNight §8» §f";

    private final String name;
    private final String description;
    private final String syntax;
    private final String[] aliases;

    public Command(String name, String description, String syntax, String... aliases) {
        this.name = name;
        this.description = description;
        this.syntax = syntax;
        this.aliases = aliases;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public String getSyntax() {
        return this.syntax;
    }

    public String[] getAliases() {
        return this.aliases;
    }

    public static void sendMessage(String message) {
        if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal(CHAT_PREFIX + message));
        }
    }

    public abstract void execute(String[] args);
}
