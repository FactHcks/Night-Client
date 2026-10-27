package me.night.music;

import me.night.Night;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * What's playing on the computer, read on a background thread so the game never waits on it.
 * <ul>
 *   <li>Linux: playerctl (MPRIS: Spotify, browsers, VLC...)</li>
 *   <li>Windows: the system media controls, through a small PowerShell script</li>
 * </ul>
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
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static java.nio.file.Path windowsScript;
    private static Process windowsProcess;

    private MediaSession() {
    }

    public static Track current() {
        return track;
    }

    /** False when no media source works here (no playerctl on Linux, no PowerShell, or macOS). */
    public static boolean available() {
        return available;
    }

    public static synchronized void start() {
        if (running) return;
        running = true;
        if (WINDOWS) {
            startWindows();
            return;
        }
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
        if (windowsProcess != null) windowsProcess.destroy();
        windowsProcess = null;
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
            parseLine(line);
        } catch (java.io.IOException e) {
            available = false; // playerctl not installed
            running = false;
        } catch (Exception e) {
            Night.LOGGER.debug("Media poll failed", e);
        }
    }

    /** status, artist, title, positionUs, lengthUs, artUrl separated by tabs. */
    private static void parseLine(String line) {
        String[] f = line.split("\t", -1);
        if (f.length < 6 || f[2].isBlank()) {
            track = null;
            return;
        }
        track = new Track("Playing".equals(f[0]), f[2], f[1], parseLong(f[3]), parseLong(f[4]), f[5], System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ Windows

    private static java.nio.file.Path script() throws java.io.IOException {
        if (windowsScript == null) {
            java.nio.file.Path p = java.nio.file.Files.createTempFile("night_media", ".ps1");
            try (var in = MediaSession.class.getResourceAsStream("/assets/night/media/windows_media.ps1")) {
                java.nio.file.Files.write(p, in.readAllBytes());
            }
            p.toFile().deleteOnExit();
            windowsScript = p;
        }
        return windowsScript;
    }

    private static ProcessBuilder powershell(String... extra) throws java.io.IOException {
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script().toString()));
        cmd.addAll(java.util.List.of(extra));
        return new ProcessBuilder(cmd);
    }

    /** One long-running PowerShell process prints the current track every second. */
    private static void startWindows() {
        thread = new Thread(() -> {
            while (running) {
                try {
                    Process p = powershell().redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    windowsProcess = p;
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (running && (line = r.readLine()) != null) {
                            if (line.equals("none")) track = null;
                            else parseLine(line);
                        }
                    }
                } catch (java.io.IOException e) {
                    available = false; // no PowerShell
                    running = false;
                    return;
                }
                track = null;
                // the script stopped (or was killed): wait a bit, then restart it
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
            }
        }, "Night media (Windows)");
        thread.setDaemon(true);
        thread.start();
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
                ProcessBuilder pb = WINDOWS ? powershell("-Action", action) : new ProcessBuilder("playerctl", action);
                pb.start().waitFor(5, TimeUnit.SECONDS);
                Thread.sleep(150);
            } catch (Exception ignored) {
            }
            Thread t = thread;
            if (t != null && !WINDOWS) t.interrupt(); // refresh right away (Windows refreshes every second anyway)
        }, "Night media command").start();
    }
}
