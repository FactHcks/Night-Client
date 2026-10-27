package me.night.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.night.Night;

import java.util.UUID;

/**
 * Background thread that keeps the Discord status in sync with the latest activity JSON.
 * Updates are sent at most every few seconds (Discord rate-limits them), and the connection
 * is retried quietly if Discord is closed or restarts.
 */
public final class DiscordPresence {
    private static final long MIN_UPDATE_MS = 5000;
    private static final long RETRY_MS = 15000;

    private static volatile JsonObject wanted; // null = clear the status
    private static volatile boolean connected;
    private static Worker worker;

    private DiscordPresence() {
    }

    public static boolean connected() {
        return connected;
    }

    /** Sets the activity to show (null clears it). Cheap; the thread sends it when allowed. */
    public static void set(JsonObject activity) {
        wanted = activity;
    }

    public static synchronized void start(String applicationId) {
        if (worker != null && worker.appId.equals(applicationId)) return;
        stop();
        worker = new Worker(applicationId);
        worker.start();
    }

    public static synchronized void stop() {
        if (worker != null) worker.interrupt();
        worker = null;
    }

    /** One connection; a new Worker replaces it when the module is toggled again. */
    private static final class Worker extends Thread {
        private final String appId;
        private DiscordIPC ipc;

        Worker(String appId) {
            super("Night Discord RPC");
            this.appId = appId;
            setDaemon(true);
        }

        @Override
        public void run() {
            String lastSent = null;
            long lastSendAt = 0;
            long nextConnectAt = 0;
            try {
                while (!isInterrupted()) {
                    long now = System.currentTimeMillis();
                    if (ipc == null && now >= nextConnectAt) {
                        if (connect()) {
                            lastSent = null; // re-send after reconnecting
                        } else {
                            nextConnectAt = now + RETRY_MS;
                        }
                    }
                    if (ipc != null) {
                        JsonObject act = wanted;
                        String json = act == null ? "null" : act.toString();
                        if (!json.equals(lastSent) && now - lastSendAt >= MIN_UPDATE_MS) {
                            try {
                                setActivity(act);
                                lastSent = json;
                                lastSendAt = now;
                            } catch (Exception e) {
                                Night.LOGGER.debug("Discord RPC update failed, reconnecting", e);
                                disconnect();
                                nextConnectAt = now + RETRY_MS;
                            }
                        }
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ignored) {
                // stopping
            }
            // clear the status on the way out
            if (ipc != null) {
                try {
                    setActivity(null);
                } catch (Exception ignored) {
                }
            }
            disconnect();
        }

        private boolean connect() {
            DiscordIPC c = DiscordIPC.open();
            if (c == null) return false; // Discord isn't running
            try {
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", appId);
                c.send(DiscordIPC.OP_HANDSHAKE, hello.toString());
                String[] reply = c.read();
                if (Integer.parseInt(reply[0]) != DiscordIPC.OP_FRAME || !reply[1].contains("\"READY\"")) {
                    Night.LOGGER.warn("Discord refused the connection: {}", reply[1]);
                    c.close();
                    return false;
                }
                ipc = c;
                connected = true;
                Night.LOGGER.info("Connected to Discord");
                return true;
            } catch (Exception e) {
                c.close();
                return false;
            }
        }

        private void setActivity(JsonObject activity) throws Exception {
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", activity);
            JsonObject msg = new JsonObject();
            msg.addProperty("cmd", "SET_ACTIVITY");
            msg.add("args", args);
            msg.addProperty("nonce", UUID.randomUUID().toString());
            ipc.send(DiscordIPC.OP_FRAME, msg.toString());
            String[] reply = ipc.read();
            if (Integer.parseInt(reply[0]) == DiscordIPC.OP_CLOSE) throw new IllegalStateException("Discord closed the connection");
            JsonObject r = JsonParser.parseString(reply[1]).getAsJsonObject();
            if (r.has("evt") && "ERROR".equals(r.get("evt").getAsString())) {
                Night.LOGGER.warn("Discord rejected the status: {}", r.get("data"));
            }
        }

        private void disconnect() {
            if (ipc != null) ipc.close();
            ipc = null;
            connected = false;
        }
    }
}
