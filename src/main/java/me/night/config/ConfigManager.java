package me.night.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.night.Night;
import me.night.module.Module;
import me.night.module.modules.render.ClickGui;
import me.night.property.Property;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ColorProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.client.MinecraftClient;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Config saved automatically when menus close and when the game quits. */
    public static final String AUTO = "default";
    /** Name of the config that was loaded or saved last. */
    public static String current = AUTO;
    /** Share codes start with this, so random clipboard text isn't mistaken for a config. */
    private static final String CODE_PREFIX = "NIGHT1:";

    /** Saves the current settings to the auto-save config. */
    public static void autoSave() {
        saveConfig(AUTO);
    }

    public static long lastModified(String name) {
        File file = file(name);
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

    private static File file(String name) {
        return new File(getConfigsFolder(), name.toLowerCase() + ".json");
    }

    // ------------------------------------------------------------------ to / from JSON

    /**
     * Current settings as JSON. For sharing, keybinds and hidden settings (HUD layout, window
     * position) are left out, since those are personal.
     */
    public static JsonObject snapshot(boolean forSharing) {
        JsonObject root = new JsonObject();
        JsonObject modulesObj = new JsonObject();
        for (Module mod : Night.INSTANCE.getModuleManager().getModules()) {
            JsonObject modJson = new JsonObject();
            modJson.addProperty("enabled", mod.isEnabled());
            if (!forSharing) modJson.addProperty("key", mod.getKey());

            JsonObject propsJson = new JsonObject();
            for (Property<?> prop : mod.getProperties()) {
                if (forSharing && !prop.isVisible()) continue;
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
        return root;
    }

    /** Applies settings from JSON. Anything missing from it is left unchanged. */
    public static boolean apply(JsonObject root) {
        if (!root.has("modules")) return false;
        JsonObject modulesObj = root.getAsJsonObject("modules");
        for (Module mod : Night.INSTANCE.getModuleManager().getModules()) {
            if (!modulesObj.has(mod.getName())) continue;
            JsonObject modJson = modulesObj.getAsJsonObject(mod.getName());

            if (modJson.has("key")) {
                mod.setKey(modJson.get("key").getAsInt());
            }
            // never open the ClickGUI just because a config says it was open
            if (modJson.has("enabled") && !(mod instanceof ClickGui)) {
                boolean enabled = modJson.get("enabled").getAsBoolean();
                if (mod.isEnabled() != enabled) mod.setEnabled(enabled);
            }
            if (modJson.has("properties")) {
                JsonObject propsJson = modJson.getAsJsonObject("properties");
                for (Property<?> prop : mod.getProperties()) {
                    if (!propsJson.has(prop.getName())) continue;
                    try {
                        if (prop instanceof BooleanProperty) {
                            ((BooleanProperty) prop).setValue(propsJson.get(prop.getName()).getAsBoolean());
                        } else if (prop instanceof NumberProperty) {
                            ((NumberProperty) prop).setValue(propsJson.get(prop.getName()).getAsDouble());
                        } else if (prop instanceof ModeProperty mp) {
                            String value = propsJson.get(prop.getName()).getAsString();
                            if (mp.getModes().contains(value)) mp.setValue(value);
                        } else if (prop instanceof ColorProperty) {
                            ((ColorProperty) prop).setValue(new Color(propsJson.get(prop.getName()).getAsInt(), true));
                        }
                    } catch (Exception ignored) {
                        // a bad value in one setting shouldn't stop the rest from loading
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ files

    public static boolean saveConfig(String name) {
        try (FileWriter writer = new FileWriter(file(name))) {
            GSON.toJson(snapshot(false), writer);
            if (!name.equalsIgnoreCase(AUTO)) current = name.toLowerCase();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static JsonObject read(String name) throws Exception {
        try (FileReader reader = new FileReader(file(name))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    public static boolean loadConfig(String name) {
        try {
            if (!file(name).exists()) return false;
            boolean ok = apply(read(name));
            if (ok && !name.equalsIgnoreCase(AUTO)) current = name.toLowerCase();
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteConfig(String name) {
        File file = file(name);
        return file.exists() && file.delete();
    }

    public static List<String> getConfigs() {
        List<String> list = new ArrayList<>();
        File[] files = getConfigsFolder().listFiles((dir, n) -> n.endsWith(".json"));
        if (files != null) {
            for (File f : files) list.add(f.getName().replace(".json", ""));
        }
        list.sort((a, b) -> Long.compare(lastModified(b), lastModified(a)));
        return list;
    }

    // ------------------------------------------------------------------ share codes

    private static String encode(JsonObject json) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(new Gson().toJson(json).getBytes(StandardCharsets.UTF_8));
        }
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    /** Share code for the current settings. */
    public static String exportCode() {
        try {
            return encode(snapshot(true));
        } catch (Exception e) {
            return null;
        }
    }

    /** Share code for a saved config (keybinds and HUD layout removed). */
    public static String exportCode(String name) {
        try {
            JsonObject json = read(name);
            JsonObject modules = json.getAsJsonObject("modules");
            for (Module mod : Night.INSTANCE.getModuleManager().getModules()) {
                if (!modules.has(mod.getName())) continue;
                JsonObject m = modules.getAsJsonObject(mod.getName());
                m.remove("key");
                if (m.has("properties")) {
                    JsonObject props = m.getAsJsonObject("properties");
                    for (Property<?> p : mod.getProperties()) if (!p.isVisible()) props.remove(p.getName());
                }
            }
            return encode(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reads a share code, applies it and saves it as a new config.
     * Returns the new config's name, or null if the code isn't valid.
     */
    public static String importCode(String code) {
        try {
            code = code.trim();
            if (!code.startsWith(CODE_PREFIX)) return null;
            byte[] data = Base64.getUrlDecoder().decode(code.substring(CODE_PREFIX.length()));
            String text;
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
                text = new String(gz.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (!apply(json)) return null;
            String name = "imported";
            for (int i = 2; file(name).exists(); i++) name = "imported-" + i;
            saveConfig(name);
            return name;
        } catch (Exception e) {
            return null;
        }
    }
}
