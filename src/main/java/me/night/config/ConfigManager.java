package me.night.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.night.Night;
import me.night.module.Module;
import me.night.property.Property;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ColorProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.client.MinecraftClient;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Config saved automatically when menus close and when the game quits. */
    public static final String AUTO = "default";
    /** Name of the config that was loaded or saved last. */
    public static String current = AUTO;

    /** Saves the current settings to the auto-save config. */
    public static void autoSave() {
        saveConfig(AUTO);
    }

    public static long lastModified(String name) {
        File file = new File(getConfigsFolder(), name.toLowerCase() + ".json");
        return file.exists() ? file.lastModified() : 0L;
    }

    public static void openFolder() {
        net.minecraft.util.Util.getOperatingSystem().open(getConfigsFolder());
    }

    public static File getConfigsFolder() {
        File folder = new File(MinecraftClient.getInstance().runDirectory, "night/configs");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static boolean saveConfig(String name) {
        try {
            File file = new File(getConfigsFolder(), name.toLowerCase() + ".json");
            JsonObject root = new JsonObject();
            JsonObject modulesObj = new JsonObject();

            for (Module mod : Night.INSTANCE.getModuleManager().getModules()) {
                JsonObject modJson = new JsonObject();
                modJson.addProperty("enabled", mod.isEnabled());
                modJson.addProperty("key", mod.getKey()); // Binds saved!

                JsonObject propsJson = new JsonObject();
                for (Property<?> prop : mod.getProperties()) {
                    if (prop instanceof BooleanProperty) {
                        propsJson.addProperty(prop.getName(), ((BooleanProperty) prop).getValue());
                    } else if (prop instanceof NumberProperty) {
                        propsJson.addProperty(prop.getName(), ((NumberProperty) prop).getValue());
                    } else if (prop instanceof ModeProperty) {
                        propsJson.addProperty(prop.getName(), ((ModeProperty) prop).getValue());
                    } else if (prop instanceof ColorProperty) {
                        propsJson.addProperty(prop.getName(), ((ColorProperty) prop).getRGB());
                    }
                }
                modJson.add("properties", propsJson);
                modulesObj.add(mod.getName(), modJson);
            }

            root.add("modules", modulesObj);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
            }
            if (!name.equalsIgnoreCase(AUTO)) current = name.toLowerCase();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean loadConfig(String name) {
        try {
            File file = new File(getConfigsFolder(), name.toLowerCase() + ".json");
            if (!file.exists()) {
                return false;
            }

            JsonObject root;
            try (FileReader reader = new FileReader(file)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }

            if (!root.has("modules")) return false;
            if (!name.equalsIgnoreCase(AUTO)) current = name.toLowerCase();
            JsonObject modulesObj = root.getAsJsonObject("modules");

            for (Module mod : Night.INSTANCE.getModuleManager().getModules()) {
                if (modulesObj.has(mod.getName())) {
                    JsonObject modJson = modulesObj.getAsJsonObject(mod.getName());

                    // Restore Keybind
                    if (modJson.has("key")) {
                        mod.setKey(modJson.get("key").getAsInt());
                    }

                    // Restore Enabled State
                    if (modJson.has("enabled") && !(mod instanceof me.night.module.modules.render.ClickGui)) {
                        boolean enabled = modJson.get("enabled").getAsBoolean();
                        if (mod.isEnabled() != enabled) {
                            mod.setEnabled(enabled);
                        }
                    }

                    // Restore Properties
                    if (modJson.has("properties")) {
                        JsonObject propsJson = modJson.getAsJsonObject("properties");
                        for (Property<?> prop : mod.getProperties()) {
                            if (propsJson.has(prop.getName())) {
                                if (prop instanceof BooleanProperty) {
                                    ((BooleanProperty) prop).setValue(propsJson.get(prop.getName()).getAsBoolean());
                                } else if (prop instanceof NumberProperty) {
                                    ((NumberProperty) prop).setValue(propsJson.get(prop.getName()).getAsDouble());
                                } else if (prop instanceof ModeProperty) {
                                    ((ModeProperty) prop).setValue(propsJson.get(prop.getName()).getAsString());
                                } else if (prop instanceof ColorProperty) {
                                    ((ColorProperty) prop).setValue(new Color(propsJson.get(prop.getName()).getAsInt(), true));
                                }
                            }
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteConfig(String name) {
        File file = new File(getConfigsFolder(), name.toLowerCase() + ".json");
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static List<String> getConfigs() {
        List<String> list = new ArrayList<>();
        File folder = getConfigsFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                list.add(f.getName().replace(".json", ""));
            }
        }
        list.sort((a, b) -> Long.compare(lastModified(b), lastModified(a)));
        return list;
    }
}
