package me.night.music;

import me.night.Night;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * What's playing on the computer, read with playerctl (Linux / MPRIS: Spotify, browsers, VLC...).
 * Polled on a background thread so the game never waits on it.
 */
public final class MediaSession {
    public record Track(boolean playing, String title, String artist, long positionUs, long lengthUs,
                        String artUrl, long fetchedAt) {
        /** Current position, moving forward between polls while playing. */
        public long positionNowUs() {
            long p = positionUs + (playing ? (System.currentTimeMillis() - fetchedAt) * 1000L : 0);
            return lengthUs > 0 ? Math.min(p, lengthUs) : p;
        }
    }

    private static final String FORMAT = "{{status}}\t{{artist}}\t{{title}}\t{{position}}\t{{mpris:length}}\t{{mpris:artUrl}}";

    private static volatile Track track;
    private static volatile boolean available = true;
    private static volatile boolean running;
    private static Thread thread;

    private MediaSession() {
    }

    public static Track current() {
        return track;
    }

    /** False when playerctl isn't installed (Windows, macOS, or Linux without it). */
    public static boolean available() {
        return available;
    }

    public static synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(() -> {
            while (running) {
                poll();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    // woken early to refresh after a button press
                }
            }
        }, "Night media poller");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        track = null;
    }

    private static void poll() {
        try {
            Process p = new ProcessBuilder("playerctl", "metadata", "--format", FORMAT).redirectErrorStream(true).start();
            String line;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                line = r.readLine();
            }
            if (!p.waitFor(2, TimeUnit.SECONDS) || p.exitValue() != 0 || line == null) {
                track = null; // no player running
                return;
            }
            String[] f = line.split("\t", -1);
            if (f.length < 6 || f[2].isBlank()) {
                track = null;
                return;
            }
            track = new Track("Playing".equals(f[0]), f[2], f[1], parseLong(f[3]), parseLong(f[4]), f[5], System.currentTimeMillis());
        } catch (java.io.IOException e) {
            available = false; // playerctl not installed
            running = false;
        } catch (Exception e) {
            Night.LOGGER.debug("Media poll failed", e);
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Sends play-pause, next or previous to the media player. */
    public static void command(String action) {
        if (!available) return;
        new Thread(() -> {
            try {
                new ProcessBuilder("playerctl", action).start().waitFor(2, TimeUnit.SECONDS);
                Thread.sleep(150);
            } catch (Exception ignored) {
            }
            Thread t = thread;
            if (t != null) t.interrupt(); // refresh right away
        }, "Night media command").start();
    }
}
