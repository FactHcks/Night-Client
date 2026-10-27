package me.night.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.night.Night;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Waypoints per server / singleplayer world, saved in night/waypoints.json. */
public final class WaypointManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int[] COLORS = {0xFF60A5FA, 0xFF4ADE80, 0xFFFACC15, 0xFFF472B6, 0xFFA78BFA, 0xFF2DD4BF, 0xFFFB923C};
    public static final int DEATH_COLOR = 0xFFFF4D5E;

    private static Map<String, List<Waypoint>> all;

    private WaypointManager() {
    }

    private static File file() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "night");
        dir.mkdirs();
        return new File(dir, "waypoints.json");
    }

    private static Map<String, List<Waypoint>> data() {
        if (all == null) {
            all = new HashMap<>();
            File f = file();
            if (f.exists()) {
                try (FileReader r = new FileReader(f)) {
                    Type type = new TypeToken<Map<String, List<Waypoint>>>() { }.getType();
                    Map<String, List<Waypoint>> loaded = GSON.fromJson(r, type);
                    if (loaded != null) loaded.forEach((k, v) -> all.put(k, new ArrayList<>(v)));
                } catch (Exception e) {
                    Night.LOGGER.warn("Could not read waypoints", e);
                }
            }
        }
        return all;
    }

    private static void save() {
        try (FileWriter w = new FileWriter(file())) {
            GSON.toJson(data(), w);
        } catch (Exception e) {
            Night.LOGGER.warn("Could not save waypoints", e);
        }
    }

    /** Identifies the current world: server address or singleplayer save name. */
    public static String worldKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isInSingleplayer() && mc.getServer() != null) return "sp:" + mc.getServer().getSaveProperties().getLevelName();
        if (mc.getCurrentServerEntry() != null) return "mp:" + mc.getCurrentServerEntry().address.toLowerCase();
        return "unknown";
    }

    public static String dimension() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    public static List<Waypoint> inWorld() {
        return data().computeIfAbsent(worldKey(), k -> new ArrayList<>());
    }

    /** Waypoints in the current world and dimension. */
    public static List<Waypoint> visible() {
        String dim = dimension();
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : inWorld()) if (w.dimension().equals(dim)) out.add(w);
        return out;
    }

    public static Waypoint add(String name, int x, int y, int z) {
        List<Waypoint> list = inWorld();
        list.removeIf(w -> w.name().equalsIgnoreCase(name) && w.dimension().equals(dimension()));
        int color = name.equalsIgnoreCase("Death") ? DEATH_COLOR : COLORS[list.size() % COLORS.length];
        Waypoint wp = new Waypoint(name, x, y, z, dimension(), color);
        list.add(wp);
        save();
        return wp;
    }

    public static boolean remove(String name) {
        boolean removed = inWorld().removeIf(w -> w.name().equalsIgnoreCase(name));
        if (removed) save();
        return removed;
    }

    public static int clear() {
        List<Waypoint> list = inWorld();
        int n = list.size();
        list.clear();
        save();
        return n;
    }
}
