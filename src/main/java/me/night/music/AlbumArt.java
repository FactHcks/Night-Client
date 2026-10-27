package me.night.music;

import me.night.Night;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Loads the album cover of the current track into a texture (download happens off the game thread). */
public final class AlbumArt {
    public static final Identifier ID = Identifier.of(Night.MOD_ID, "music_art");
    private static final int SIZE = 96;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static String loadedUrl = "";
    private static String requestedUrl = "";
    private static volatile int[] pendingPixels;
    private static volatile String pendingUrl;
    private static NativeImageBackedTexture texture;
    private static boolean ready;

    private AlbumArt() {
    }

    /** True when the cover for this url is loaded and can be drawn with {@link #ID}. */
    public static boolean ready(String url) {
        if (url == null || url.isEmpty()) return false;
        if (!url.equals(requestedUrl)) {
            requestedUrl = url;
            ready = false;
            new Thread(() -> download(url), "Night album art").start();
        }
        uploadPending();
        return ready && url.equals(loadedUrl);
    }

    private static void download(String url) {
        try {
            BufferedImage img;
            if (url.startsWith("file://")) {
                // the part after "?" only makes each track's url unique
                img = ImageIO.read(new File(URI.create(url.split("\\?")[0])));
            } else {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).build();
                try (InputStream in = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream()).body()) {
                    img = ImageIO.read(in);
                }
            }
            if (img == null) return;
            // centre-crop to a square and scale down
            int side = Math.min(img.getWidth(), img.getHeight());
            int ox = (img.getWidth() - side) / 2, oy = (img.getHeight() - side) / 2;
            int[] px = new int[SIZE * SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    px[y * SIZE + x] = 0xFF000000 | img.getRGB(ox + x * side / SIZE, oy + y * side / SIZE);
                }
            }
            pendingUrl = url;
            pendingPixels = px;
        } catch (Exception e) {
            Night.LOGGER.debug("Could not load album art {}", url, e);
        }
    }

    /** Copies a finished download into the texture (must run on the render thread). */
    private static void uploadPending() {
        int[] px = pendingPixels;
        if (px == null) return;
        pendingPixels = null;
        String url = pendingUrl;
        if (!url.equals(requestedUrl)) return; // a newer track was requested meanwhile
        if (texture == null) {
            texture = new NativeImageBackedTexture(() -> "night-album-art", new NativeImage(SIZE, SIZE, false));
            MinecraftClient.getInstance().getTextureManager().registerTexture(ID, texture);
        }
        NativeImage image = texture.getImage();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) image.setColorArgb(x, y, px[y * SIZE + x]);
        }
        texture.upload();
        loadedUrl = url;
        ready = true;
    }
}
