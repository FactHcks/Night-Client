package me.night.command.commands;

import me.night.command.Command;
import me.night.packconverter.PackConverter;
import me.night.packconverter.PackConverterScreen;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.file.Path;

public class PackConverterCommand extends Command {
    public PackConverterCommand() {
        super("packconverter", "Opens 1.8.9 to 1.21 pack converter or converts a pack", ".packconverter [pack name]", "convertpack", "pconv");
    }

    @Override
    public void execute(String[] args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        if (args == null || args.length == 0) {
            mc.execute(() -> mc.setScreen(new PackConverterScreen(mc.currentScreen)));
            sendMessage("§aOpened 1.8.9 Pack Converter.");
            return;
        }

        Path packDir = mc.getResourcePackDir();
        if (packDir == null) {
            sendMessage("§cUnable to find resource packs directory!");
            return;
        }

        String search = String.join(" ", args).trim();
        File target = new File(packDir.toFile(), search);
        if (!target.exists() && !search.endsWith(".zip")) {
            target = new File(packDir.toFile(), search + ".zip");
        }

        if (!target.exists()) {
            sendMessage("§cPack not found: §f" + search);
            return;
        }

        File finalTarget = target;
        sendMessage("§7Starting conversion of §f" + finalTarget.getName() + "§7...");

        PackConverter.convertAsync(finalTarget, packDir.toFile(), new PackConverter.ProgressListener() {
            @Override
            public void onProgress(int current, int total, String status) {
            }

            @Override
            public void onComplete(File convertedFile) {
                sendMessage("§aSuccessfully converted: §f" + convertedFile.getName());
            }

            @Override
            public void onError(Exception error) {
                sendMessage("§cConversion failed: §f" + error.getMessage());
            }
        });
    }
}
