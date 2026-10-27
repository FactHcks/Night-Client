package me.night.alt;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import me.night.Night;
import me.night.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.util.Uuids;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Offline (cracked) alt accounts. Saved in night/alts.json. */
public final class AltManager {
    private static final List<String> ALTS = new ArrayList<>();
    private static Session original;
    private static boolean loaded;

    private AltManager() {
    }

    private static File file() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "night");
        dir.mkdirs();
        return new File(dir, "alts.json");
    }

    public static List<String> alts() {
        if (!loaded) load();
        return ALTS;
    }

    private static void load() {
        loaded = true;
        File f = file();
        if (!f.exists()) return;
        try (FileReader r = new FileReader(f)) {
            for (JsonElement e : JsonParser.parseReader(r).getAsJsonArray()) ALTS.add(e.getAsString());
        } catch (Exception e) {
            Night.LOGGER.warn("Could not read alts", e);
        }
    }

    private static void save() {
        JsonArray arr = new JsonArray();
        ALTS.forEach(arr::add);
        try (FileWriter w = new FileWriter(file())) {
            new Gson().toJson(arr, w);
        } catch (Exception e) {
            Night.LOGGER.warn("Could not save alts", e);
        }
    }

    public static boolean validName(String name) {
        return name.matches("[A-Za-z0-9_]{3,16}");
    }

    public static void add(String name) {
        if (!validName(name)) return;
        alts().removeIf(n -> n.equalsIgnoreCase(name));
        alts().add(0, name);
        save();
    }

    public static void remove(String name) {
        alts().removeIf(n -> n.equalsIgnoreCase(name));
        save();
    }

    /** Switches to an offline account with this name. */
    public static void login(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (original == null) original = mc.getSession();
        Session session = new Session(name, Uuids.getOfflinePlayerUuid(name), "0", Optional.empty(), Optional.empty());
        apply(session);
    }

    /** Goes back to the account the game was started with. */
    public static void restore() {
        if (original != null) {
            apply(original);
            original = null;
        }
    }

    public static boolean isSwitched() {
        return original != null;
    }

    private static void apply(Session session) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ((MinecraftClientAccessor) mc).night$setSession(session);
        // chat signing keys belong to the original account; offline alts can't use them
        ((MinecraftClientAccessor) mc).night$setProfileKeys(ProfileKeys.MISSING);
    }

    public static String randomName() {
        String[] a = {"Swift", "Dark", "Night", "Silent", "Lucky", "Frost", "Neon", "Pixel", "Shadow", "Echo", "Nova", "Blaze"};
        String[] b = {"Fox", "Wolf", "Hawk", "Byte", "Rider", "Ghost", "Storm", "Viper", "Raven", "Comet", "Tiger", "Spark"};
        Random r = new Random();
        return a[r.nextInt(a.length)] + b[r.nextInt(b.length)] + (10 + r.nextInt(990));
    }
}
