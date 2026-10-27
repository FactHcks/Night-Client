package me.night.module.modules.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.night.Night;
import me.night.discord.DiscordPresence;
import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.module.modules.render.SessionStats;
import me.night.property.properties.BooleanProperty;
import me.night.ui.notification.Notifications;
import me.night.waypoint.WaypointManager;

/** Shows "Playing Night Client" with the server and session stats on your Discord profile. */
public class DiscordRPC extends Module {
    /**
     * The Discord application this status belongs to. Its name is what Discord shows as the game
     * ("Playing Night Client"). Create one at https://discord.com/developers/applications, and upload
     * the logo under Rich Presence → Art Assets with the key "logo".
     * Can be overridden with -Dnight.discordAppId=... for testing.
     */
    public static final String APP_ID = System.getProperty("night.discordAppId", "1564697266693087322");

    private static final String GITHUB = "https://github.com/FactHcks/Night-Client";

    private final BooleanProperty showServer = new BooleanProperty("Show Server", "Show the server address (off: just \"Multiplayer\")", true);
    private final BooleanProperty showStats = new BooleanProperty("Show Stats", "Show kills and deaths from SessionStats", true);
    private final BooleanProperty button = new BooleanProperty("GitHub Button", "Add a button linking to the client's GitHub", true);

    private final long launchTime = System.currentTimeMillis() / 1000;
    private String placeKey = "";
    private long placeSince = launchTime;
    private int tick;

    public DiscordRPC() {
        super("DiscordRPC", "Shows what you're playing on your Discord profile", Category.PLAYER);
        addProperties(showServer, showStats, button);
    }

    @Override
    protected void onEnable() {
        if (APP_ID.isBlank()) {
            Notifications.push("DiscordRPC", "No Discord application ID set", Notifications.Kind.INFO);
            Night.LOGGER.warn("DiscordRPC: set DiscordRPC.APP_ID (or -Dnight.discordAppId) to your Discord application ID");
            return;
        }
        update();
        DiscordPresence.start(APP_ID);
    }

    @Override
    protected void onDisable() {
        DiscordPresence.stop();
    }

    @Override
    public String getSuffix() {
        return DiscordPresence.connected() ? "Connected" : null;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.POST || ++tick % 20 != 0) return;
        update();
    }

    /** Builds the activity from the game state; the presence thread sends it when it changes. */
    private void update() {
        String details;
        String key;
        if (mc.world == null) {
            details = "In the menus";
            key = "menu";
        } else if (mc.isInSingleplayer()) {
            details = "Playing singleplayer";
            key = "sp";
        } else {
            String address = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "";
            details = showServer.getValue() && !address.isBlank() ? "Playing on " + address : "Playing multiplayer";
            key = WaypointManager.worldKey();
        }
        // the elapsed timer restarts when you join a different server or go back to the menu
        if (!key.equals(placeKey)) {
            placeKey = key;
            placeSince = System.currentTimeMillis() / 1000;
        }

        JsonObject act = new JsonObject();
        act.addProperty("details", details);
        if (mc.world != null && showStats.getValue() && SessionStats.active()) {
            act.addProperty("state", SessionStats.kills() + " kills · " + SessionStats.deaths() + " deaths");
        }
        JsonObject ts = new JsonObject();
        ts.addProperty("start", placeSince);
        act.add("timestamps", ts);

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "logo");
        assets.addProperty("large_text", Night.CLIENT_NAME + " v" + Night.CLIENT_VERSION);
        act.add("assets", assets);

        if (button.getValue()) {
            JsonObject b = new JsonObject();
            b.addProperty("label", "Get " + Night.CLIENT_NAME);
            b.addProperty("url", GITHUB);
            JsonArray buttons = new JsonArray();
            buttons.add(b);
            act.add("buttons", buttons);
        }
        DiscordPresence.set(act);
    }
}
