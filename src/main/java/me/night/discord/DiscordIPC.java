package me.night.discord;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal client for the local Discord RPC connection (a unix socket on Linux/macOS, a named pipe
 * on Windows). Every message is a frame: opcode and length (little-endian ints), then JSON.
 * Not thread-safe; use it from a single thread.
 */
final class DiscordIPC implements Closeable {
    static final int OP_HANDSHAKE = 0;
    static final int OP_FRAME = 1;
    static final int OP_CLOSE = 2;

    private final Closeable handle;
    private final InputStream in;
    private final OutputStream out;

    private DiscordIPC(Closeable handle, InputStream in, OutputStream out) {
        this.handle = handle;
        this.in = in;
        this.out = out;
    }

    /** Connects to the first running Discord client found, or returns null. */
    static DiscordIPC open() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        for (int i = 0; i < 10; i++) {
            try {
                if (windows) {
                    RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    return new DiscordIPC(pipe, Channels.newInputStream(pipe.getChannel()), Channels.newOutputStream(pipe.getChannel()));
                }
                for (Path dir : socketDirs()) {
                    Path sock = dir.resolve("discord-ipc-" + i);
                    if (!Files.exists(sock)) continue;
                    SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                    try {
                        ch.connect(UnixDomainSocketAddress.of(sock));
                    } catch (IOException e) {
                        ch.close();
                        continue;
                    }
                    return new DiscordIPC(ch, Channels.newInputStream(ch), Channels.newOutputStream(ch));
                }
            } catch (IOException ignored) {
                // not this one, try the next
            }
        }
        return null;
    }

    /** Where Discord (normal, Flatpak, Snap, Vesktop) puts its socket. */
    private static List<Path> socketDirs() {
        Set<String> bases = new LinkedHashSet<>();
        for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String v = System.getenv(env);
            if (v != null && !v.isBlank()) bases.add(v);
        }
        bases.add("/tmp");
        List<Path> dirs = new ArrayList<>();
        for (String base : bases) {
            Path b = Path.of(base);
            dirs.add(b);
            dirs.add(b.resolve("app/com.discordapp.Discord"));
            dirs.add(b.resolve("app/com.discordapp.DiscordCanary"));
            dirs.add(b.resolve("app/dev.vencord.Vesktop"));
            dirs.add(b.resolve(".flatpak/dev.vencord.Vesktop/xdg-run"));
            dirs.add(b.resolve("snap.discord"));
        }
        return dirs;
    }

    void send(int op, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op).putInt(body.length).put(body);
        out.write(buf.array());
        out.flush();
    }

    /** Reads one frame and returns {opcode, json}. */
    String[] read() throws IOException {
        DataInputStream data = new DataInputStream(in);
        byte[] head = new byte[8];
        data.readFully(head);
        ByteBuffer hb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int op = hb.getInt();
        int len = hb.getInt();
        if (len < 0 || len > 1 << 20) throw new IOException("Bad Discord frame length " + len);
        byte[] body = new byte[len];
        data.readFully(body);
        return new String[]{String.valueOf(op), new String(body, StandardCharsets.UTF_8)};
    }

    @Override
    public void close() {
        try {
            handle.close();
        } catch (IOException ignored) {
        }
    }
}
