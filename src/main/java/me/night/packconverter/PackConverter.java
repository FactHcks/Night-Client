package me.night.packconverter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * High-performance 1.8.9 to 1.21.11 Resource Pack Converter.
 * Automatically translates file paths, renames legacy textures,
 * slices legacy 1.8.9 GUI icons and widgets into modern 1.21.x sprites,
 * and updates pack.mcmeta to modern format with compatibility flags.
 */
public class PackConverter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public interface ProgressListener {
        void onProgress(int current, int total, String status);
        void onComplete(File convertedFile);
        void onError(Exception error);
    }

    private static final Map<String, String> ITEM_RENAMES = new HashMap<>();
    private static final Map<String, String> BLOCK_RENAMES = new HashMap<>();

    static {
        // --- Item Renames (1.8.9 -> 1.13+) ---
        ITEM_RENAMES.put("wood_sword", "wooden_sword");
        ITEM_RENAMES.put("wood_pickaxe", "wooden_pickaxe");
        ITEM_RENAMES.put("wood_axe", "wooden_axe");
        ITEM_RENAMES.put("wood_shovel", "wooden_shovel");
        ITEM_RENAMES.put("wood_hoe", "wooden_hoe");
        ITEM_RENAMES.put("gold_sword", "golden_sword");
        ITEM_RENAMES.put("gold_pickaxe", "golden_pickaxe");
        ITEM_RENAMES.put("gold_axe", "golden_axe");
        ITEM_RENAMES.put("gold_shovel", "golden_shovel");
        ITEM_RENAMES.put("gold_hoe", "golden_hoe");
        ITEM_RENAMES.put("gold_helmet", "golden_helmet");
        ITEM_RENAMES.put("gold_chestplate", "golden_chestplate");
        ITEM_RENAMES.put("gold_leggings", "golden_leggings");
        ITEM_RENAMES.put("gold_boots", "golden_boots");

        ITEM_RENAMES.put("apple_golden", "golden_apple");
        ITEM_RENAMES.put("carrot_golden", "golden_carrot");
        ITEM_RENAMES.put("melon_speckled", "glistering_melon_slice");
        ITEM_RENAMES.put("melon", "melon_slice");
        ITEM_RENAMES.put("porkchop_raw", "porkchop");
        ITEM_RENAMES.put("porkchop_cooked", "cooked_porkchop");
        ITEM_RENAMES.put("beef_raw", "beef");
        ITEM_RENAMES.put("beef_cooked", "cooked_beef");
        ITEM_RENAMES.put("chicken_raw", "chicken");
        ITEM_RENAMES.put("chicken_cooked", "cooked_chicken");
        ITEM_RENAMES.put("mutton_raw", "mutton");
        ITEM_RENAMES.put("mutton_cooked", "cooked_mutton");
        ITEM_RENAMES.put("rabbit_raw", "rabbit");
        ITEM_RENAMES.put("rabbit_cooked", "cooked_rabbit");
        ITEM_RENAMES.put("fish_cod_raw", "cod");
        ITEM_RENAMES.put("fish_cod_cooked", "cooked_cod");
        ITEM_RENAMES.put("fish_salmon_raw", "salmon");
        ITEM_RENAMES.put("fish_salmon_cooked", "cooked_salmon");
        ITEM_RENAMES.put("fish_clownfish_raw", "tropical_fish");
        ITEM_RENAMES.put("fish_pufferfish_raw", "pufferfish");

        ITEM_RENAMES.put("bow_standby", "bow");
        ITEM_RENAMES.put("fishing_rod_uncast", "fishing_rod");
        ITEM_RENAMES.put("potion_bottle_drinkable", "potion");
        ITEM_RENAMES.put("potion_bottle_splash", "splash_potion");
        ITEM_RENAMES.put("potion_bottle_empty", "glass_bottle");
        ITEM_RENAMES.put("bucket_empty", "bucket");
        ITEM_RENAMES.put("bucket_water", "water_bucket");
        ITEM_RENAMES.put("bucket_lava", "lava_bucket");
        ITEM_RENAMES.put("bucket_milk", "milk_bucket");

        ITEM_RENAMES.put("fireworks", "firework_rocket");
        ITEM_RENAMES.put("fireworks_charge", "firework_star");
        ITEM_RENAMES.put("fireworks_charge_overlay", "firework_star_overlay");
        ITEM_RENAMES.put("slimeball", "slime_ball");
        ITEM_RENAMES.put("redstone_dust", "redstone");
        ITEM_RENAMES.put("totem", "totem_of_undying");
        ITEM_RENAMES.put("reeds", "sugar_cane");
        ITEM_RENAMES.put("netherbrick", "nether_brick");
        ITEM_RENAMES.put("book_normal", "book");
        ITEM_RENAMES.put("book_enchanted", "enchanted_book");
        ITEM_RENAMES.put("book_writable", "writable_book");
        ITEM_RENAMES.put("book_written", "written_book");
        ITEM_RENAMES.put("spider_eye_fermented", "fermented_spider_eye");

        ITEM_RENAMES.put("dye_powder_black", "black_dye");
        ITEM_RENAMES.put("dye_powder_red", "red_dye");
        ITEM_RENAMES.put("dye_powder_green", "green_dye");
        ITEM_RENAMES.put("dye_powder_brown", "cocoa_beans");
        ITEM_RENAMES.put("dye_powder_blue", "lapis_lazuli");
        ITEM_RENAMES.put("dye_powder_purple", "purple_dye");
        ITEM_RENAMES.put("dye_powder_cyan", "cyan_dye");
        ITEM_RENAMES.put("dye_powder_silver", "light_gray_dye");
        ITEM_RENAMES.put("dye_powder_gray", "gray_dye");
        ITEM_RENAMES.put("dye_powder_pink", "pink_dye");
        ITEM_RENAMES.put("dye_powder_lime", "lime_dye");
        ITEM_RENAMES.put("dye_powder_yellow", "yellow_dye");
        ITEM_RENAMES.put("dye_powder_light_blue", "light_blue_dye");
        ITEM_RENAMES.put("dye_powder_magenta", "magenta_dye");
        ITEM_RENAMES.put("dye_powder_orange", "orange_dye");
        ITEM_RENAMES.put("dye_powder_white", "bone_meal");

        // --- Block Renames (1.8.9 -> 1.13+) ---
        BLOCK_RENAMES.put("wool_colored_white", "white_wool");
        BLOCK_RENAMES.put("wool_colored_orange", "orange_wool");
        BLOCK_RENAMES.put("wool_colored_magenta", "magenta_wool");
        BLOCK_RENAMES.put("wool_colored_light_blue", "light_blue_wool");
        BLOCK_RENAMES.put("wool_colored_yellow", "yellow_wool");
        BLOCK_RENAMES.put("wool_colored_lime", "lime_wool");
        BLOCK_RENAMES.put("wool_colored_pink", "pink_wool");
        BLOCK_RENAMES.put("wool_colored_gray", "gray_wool");
        BLOCK_RENAMES.put("wool_colored_silver", "light_gray_wool");
        BLOCK_RENAMES.put("wool_colored_cyan", "cyan_wool");
        BLOCK_RENAMES.put("wool_colored_purple", "purple_wool");
        BLOCK_RENAMES.put("wool_colored_blue", "blue_wool");
        BLOCK_RENAMES.put("wool_colored_brown", "brown_wool");
        BLOCK_RENAMES.put("wool_colored_green", "green_wool");
        BLOCK_RENAMES.put("wool_colored_red", "red_wool");
        BLOCK_RENAMES.put("wool_colored_black", "black_wool");

        BLOCK_RENAMES.put("hardened_clay", "terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_white", "white_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_orange", "orange_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_magenta", "magenta_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_light_blue", "light_blue_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_yellow", "yellow_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_lime", "lime_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_pink", "pink_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_gray", "gray_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_silver", "light_gray_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_cyan", "cyan_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_purple", "purple_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_blue", "blue_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_brown", "brown_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_green", "green_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_red", "red_terracotta");
        BLOCK_RENAMES.put("hardened_clay_stained_black", "black_terracotta");

        BLOCK_RENAMES.put("glass_white", "white_stained_glass");
        BLOCK_RENAMES.put("glass_orange", "orange_stained_glass");
        BLOCK_RENAMES.put("glass_magenta", "magenta_stained_glass");
        BLOCK_RENAMES.put("glass_light_blue", "light_blue_stained_glass");
        BLOCK_RENAMES.put("glass_yellow", "yellow_stained_glass");
        BLOCK_RENAMES.put("glass_lime", "lime_stained_glass");
        BLOCK_RENAMES.put("glass_pink", "pink_stained_glass");
        BLOCK_RENAMES.put("glass_gray", "gray_stained_glass");
        BLOCK_RENAMES.put("glass_silver", "light_gray_stained_glass");
        BLOCK_RENAMES.put("glass_cyan", "cyan_stained_glass");
        BLOCK_RENAMES.put("glass_purple", "purple_stained_glass");
        BLOCK_RENAMES.put("glass_blue", "blue_stained_glass");
        BLOCK_RENAMES.put("glass_brown", "brown_stained_glass");
        BLOCK_RENAMES.put("glass_green", "green_stained_glass");
        BLOCK_RENAMES.put("glass_red", "red_stained_glass");
        BLOCK_RENAMES.put("glass_black", "black_stained_glass");

        BLOCK_RENAMES.put("log_oak", "oak_log");
        BLOCK_RENAMES.put("log_oak_top", "oak_log_top");
        BLOCK_RENAMES.put("log_spruce", "spruce_log");
        BLOCK_RENAMES.put("log_spruce_top", "spruce_log_top");
        BLOCK_RENAMES.put("log_birch", "birch_log");
        BLOCK_RENAMES.put("log_birch_top", "birch_log_top");
        BLOCK_RENAMES.put("log_jungle", "jungle_log");
        BLOCK_RENAMES.put("log_jungle_top", "jungle_log_top");
        BLOCK_RENAMES.put("log_acacia", "acacia_log");
        BLOCK_RENAMES.put("log_acacia_top", "acacia_log_top");
        BLOCK_RENAMES.put("log_big_oak", "dark_oak_log");
        BLOCK_RENAMES.put("log_big_oak_top", "dark_oak_log_top");

        BLOCK_RENAMES.put("planks_oak", "oak_planks");
        BLOCK_RENAMES.put("planks_spruce", "spruce_planks");
        BLOCK_RENAMES.put("planks_birch", "birch_planks");
        BLOCK_RENAMES.put("planks_jungle", "jungle_planks");
        BLOCK_RENAMES.put("planks_acacia", "acacia_planks");
        BLOCK_RENAMES.put("planks_big_oak", "dark_oak_planks");

        BLOCK_RENAMES.put("leaves_oak", "oak_leaves");
        BLOCK_RENAMES.put("leaves_spruce", "spruce_leaves");
        BLOCK_RENAMES.put("leaves_birch", "birch_leaves");
        BLOCK_RENAMES.put("leaves_jungle", "jungle_leaves");
        BLOCK_RENAMES.put("leaves_acacia", "acacia_leaves");
        BLOCK_RENAMES.put("leaves_big_oak", "dark_oak_leaves");

        BLOCK_RENAMES.put("web", "cobweb");
        BLOCK_RENAMES.put("torch_on", "torch");
        BLOCK_RENAMES.put("fire_layer_0", "fire_0");
        BLOCK_RENAMES.put("fire_layer_1", "fire_1");
        BLOCK_RENAMES.put("stone_slab_top", "smooth_stone_slab_side");
        BLOCK_RENAMES.put("stone_slab_side", "smooth_stone");
        BLOCK_RENAMES.put("redstone_dust_cross", "redstone_dust_dot");
        BLOCK_RENAMES.put("redstone_dust_line", "redstone_dust_line0");
        BLOCK_RENAMES.put("bed_feet_top", "red_bed_feet_top");
        BLOCK_RENAMES.put("bed_head_top", "red_bed_head_top");
    }

    /**
     * Converts a 1.8.9 resource pack (zip or folder) to 1.21.11 format asynchronously.
     */
    public static CompletableFuture<File> convertAsync(File sourcePack, File outputDir, ProgressListener listener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return convert(sourcePack, outputDir, listener);
            } catch (Exception e) {
                if (listener != null) listener.onError(e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Synchronous conversion routine.
     */
    public static File convert(File sourcePack, File outputDir, ProgressListener listener) throws IOException {
        if (!sourcePack.exists()) {
            throw new FileNotFoundException("Source pack does not exist: " + sourcePack.getAbsolutePath());
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String baseName = sourcePack.getName().replaceAll("\\.zip$", "");
        File outputFile = new File(outputDir, baseName + "-1.21.zip");

        // Read all entries in source pack
        Map<String, byte[]> entries = new LinkedHashMap<>();
        if (sourcePack.isFile() && sourcePack.getName().endsWith(".zip")) {
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(sourcePack)))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    entries.put(entry.getName().replace('\\', '/'), baos.toByteArray());
                }
            }
        } else if (sourcePack.isDirectory()) {
            readDirectory(sourcePack, "", entries);
        }

        int totalEntries = entries.size();
        if (listener != null) listener.onProgress(0, totalEntries, "Analyzing pack...");

        Map<String, byte[]> convertedEntries = new LinkedHashMap<>();

        byte[] iconsBytes = null;
        byte[] widgetsBytes = null;

        int processed = 0;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String path = entry.getKey();
            byte[] data = entry.getValue();
            processed++;

            if (listener != null && processed % 20 == 0) {
                listener.onProgress(processed, totalEntries, "Converting: " + path);
            }

            // pack.mcmeta update
            if (path.equalsIgnoreCase("pack.mcmeta")) {
                convertedEntries.put("pack.mcmeta", convertMcmeta(data));
                continue;
            }

            // Save icons.png / widgets.png for slicing into 1.20+/1.21+ HUD sprites
            if (path.equalsIgnoreCase("assets/minecraft/textures/gui/icons.png")) {
                iconsBytes = data;
            } else if (path.equalsIgnoreCase("assets/minecraft/textures/gui/widgets.png")) {
                widgetsBytes = data;
            }

            // Remap texture paths and filenames
            String remappedPath = remapPath(path);
            convertedEntries.put(remappedPath, data);
        }

        // Slice 1.8.9 GUI icons & widgets into 1.21.x sprites
        if (listener != null) listener.onProgress(processed, totalEntries, "Slicing GUI sprites for 1.21 HUD...");
        sliceGuiSprites(iconsBytes, widgetsBytes, convertedEntries);

        // Write converted zip file
        if (listener != null) listener.onProgress(totalEntries, totalEntries, "Packaging converted zip...");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)))) {
            for (Map.Entry<String, byte[]> entry : convertedEntries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }

        // Notify Minecraft resource pack manager to reload available packs
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getResourcePackManager() != null) {
                mc.getResourcePackManager().scanPacks();
            }
        } catch (Throwable ignored) {
        }

        if (listener != null) {
            listener.onComplete(outputFile);
        }

        return outputFile;
    }

    private static void readDirectory(File dir, String currentPath, Map<String, byte[]> entries) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String path = currentPath.isEmpty() ? file.getName() : currentPath + "/" + file.getName();
            if (file.isDirectory()) {
                readDirectory(file, path, entries);
            } else {
                entries.put(path, Files.readAllBytes(file.toPath()));
            }
        }
    }

    private static byte[] convertMcmeta(byte[] raw) {
        try {
            String jsonStr = new String(raw, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
            JsonObject pack = root.has("pack") ? root.getAsJsonObject("pack") : new JsonObject();

            int targetFormat = 46;
            try {
                targetFormat = SharedConstants.RESOURCE_PACK_VERSION;
            } catch (Throwable ignored) {
            }

            pack.addProperty("pack_format", targetFormat);

            // Add supported_formats to prevent Minecraft "Incompatible pack" warnings
            JsonObject supported = new JsonObject();
            supported.addProperty("min_inclusive", 1);
            supported.addProperty("max_inclusive", 99);
            pack.add("supported_formats", supported);

            String desc = pack.has("description") ? pack.get("description").getAsString() : "Converted Resource Pack";
            pack.addProperty("description", desc + " §8[§b1.21§8]");

            root.add("pack", pack);
            return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Fallback default mcmeta
            String fallback = "{\"pack\":{\"pack_format\":46,\"supported_formats\":{\"min_inclusive\":1,\"max_inclusive\":99},\"description\":\"Converted 1.8.9 Pack (Night)\"}}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String remapPath(String path) {
        String lower = path.toLowerCase();

        // 1. assets/minecraft/textures/blocks/ -> assets/minecraft/textures/block/
        if (lower.startsWith("assets/minecraft/textures/blocks/")) {
            String file = path.substring("assets/minecraft/textures/blocks/".length());
            String remappedName = remapFileName(file, BLOCK_RENAMES);
            return "assets/minecraft/textures/block/" + remappedName;
        }

        // 2. assets/minecraft/textures/items/ -> assets/minecraft/textures/item/
        if (lower.startsWith("assets/minecraft/textures/items/")) {
            String file = path.substring("assets/minecraft/textures/items/".length());
            String remappedName = remapFileName(file, ITEM_RENAMES);
            return "assets/minecraft/textures/item/" + remappedName;
        }

        return path;
    }

    private static String remapFileName(String fileName, Map<String, String> renames) {
        boolean isMcmeta = fileName.endsWith(".mcmeta");
        String cleanName = isMcmeta ? fileName.substring(0, fileName.length() - 7) : fileName;

        String baseName = cleanName;
        String ext = "";
        int dot = cleanName.lastIndexOf('.');
        if (dot != -1) {
            baseName = cleanName.substring(0, dot);
            ext = cleanName.substring(dot);
        }

        String renamed = renames.get(baseName.toLowerCase());
        if (renamed != null) {
            return renamed + ext + (isMcmeta ? ".mcmeta" : "");
        }

        return fileName;
    }

    /**
     * Slices 1.8.9 monolithic icons.png and widgets.png into individual 1.20.2+ / 1.21.x sprites.
     * Preserves scaling (supports 16x, 32x, 64x, 128x, 256x, 512x).
     */
    private static void sliceGuiSprites(byte[] iconsBytes, byte[] widgetsBytes, Map<String, byte[]> output) {
        if (iconsBytes != null) {
            try {
                BufferedImage icons = ImageIO.read(new ByteArrayInputStream(iconsBytes));
                if (icons != null) {
                    double s = icons.getWidth() / 256.0;

                    // Crosshair
                    cropAndPut(icons, (int) (0 * s), (int) (0 * s), (int) (15 * s), (int) (15 * s),
                            "assets/minecraft/textures/gui/sprites/hud/crosshair.png", output);

                    // Hearts
                    cropAndPut(icons, (int) (16 * s), 0, (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/container.png", output);
                    cropAndPut(icons, (int) (25 * s), 0, (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/container_blinking.png", output);
                    cropAndPut(icons, (int) (52 * s), 0, (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/full.png", output);
                    cropAndPut(icons, (int) (61 * s), 0, (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/half.png", output);
                    cropAndPut(icons, (int) (52 * s), (int) (45 * s), (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full.png", output);
                    cropAndPut(icons, (int) (61 * s), (int) (45 * s), (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png", output);
                    cropAndPut(icons, (int) (88 * s), 0, (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full.png", output);
                    cropAndPut(icons, (int) (97 * s), 0, (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half.png", output);
                    cropAndPut(icons, (int) (124 * s), 0, (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/withered_full.png", output);
                    cropAndPut(icons, (int) (133 * s), 0, (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/withered_half.png", output);
                    cropAndPut(icons, (int) (160 * s), 0, (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png", output);
                    cropAndPut(icons, (int) (169 * s), 0, (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png", output);

                    // Armor
                    cropAndPut(icons, (int) (16 * s), (int) (9 * s), (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/armor_empty.png", output);
                    cropAndPut(icons, (int) (25 * s), (int) (9 * s), (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/armor_half.png", output);
                    cropAndPut(icons, (int) (34 * s), (int) (9 * s), (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/armor_full.png", output);

                    // Food
                    cropAndPut(icons, (int) (16 * s), (int) (27 * s), (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/food_empty.png", output);
                    cropAndPut(icons, (int) (61 * s), (int) (27 * s), (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/food_half.png", output);
                    cropAndPut(icons, (int) (52 * s), (int) (27 * s), (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/food_full.png", output);

                    // Air
                    cropAndPut(icons, (int) (16 * s), (int) (18 * s), (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/air.png", output);
                    cropAndPut(icons, (int) (25 * s), (int) (18 * s), (int) (9 * s), (int) (9 * s),
                            "assets/minecraft/textures/gui/sprites/hud/air_bursting.png", output);

                    // Experience bars
                    cropAndPut(icons, 0, (int) (64 * s), (int) (182 * s), (int) (5 * s),
                            "assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png", output);
                    cropAndPut(icons, 0, (int) (69 * s), (int) (182 * s), (int) (5 * s),
                            "assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png", output);
                }
            } catch (Exception ignored) {
            }
        }

        if (widgetsBytes != null) {
            try {
                BufferedImage widgets = ImageIO.read(new ByteArrayInputStream(widgetsBytes));
                if (widgets != null) {
                    double s = widgets.getWidth() / 256.0;

                    // Hotbar & selector
                    cropAndPut(widgets, 0, 0, (int) (182 * s), (int) (22 * s),
                            "assets/minecraft/textures/gui/sprites/hud/hotbar.png", output);
                    cropAndPut(widgets, 0, (int) (22 * s), (int) (24 * s), (int) (24 * s),
                            "assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png", output);

                    // Buttons
                    cropAndPut(widgets, 0, (int) (66 * s), (int) (200 * s), (int) (20 * s),
                            "assets/minecraft/textures/gui/sprites/widget/button.png", output);
                    cropAndPut(widgets, 0, (int) (86 * s), (int) (200 * s), (int) (20 * s),
                            "assets/minecraft/textures/gui/sprites/widget/button_highlighted.png", output);
                    cropAndPut(widgets, 0, (int) (46 * s), (int) (200 * s), (int) (20 * s),
                            "assets/minecraft/textures/gui/sprites/widget/button_disabled.png", output);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void cropAndPut(BufferedImage src, int x, int y, int w, int h, String destPath, Map<String, byte[]> output) {
        if (x + w > src.getWidth() || y + h > src.getHeight() || w <= 0 || h <= 0) return;
        try {
            BufferedImage sub = src.getSubimage(x, y, w, h);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(sub, "PNG", baos);
            output.put(destPath, baos.toByteArray());
        } catch (Exception ignored) {
        }
    }
}
