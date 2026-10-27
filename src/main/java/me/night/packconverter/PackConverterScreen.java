package me.night.packconverter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PackConverterScreen extends Screen {
    private static final long START = System.nanoTime();
    private static final float W = 460;
    private static final float ROW_H = 46;

    private final Screen parent;
    private final List<PackEntry> packs = new ArrayList<>();
    private final Anim fade = new Anim(0);
    private float scroll = 0;
    private final Anim scrollAnim = new Anim(0);
    private final Map<String, Anim> buttonHover = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();

    private boolean converting = false;
    private float convertProgress = 0F;
    private String statusText = "Ready to convert";
    private int statusColor = 0xFF9A9AAA;

    private float px, py, pw, ph, listY, listH;

    public record PackEntry(File file, String name, String description, int format, boolean isLegacy) {
    }

    private record Hit(float x, float y, float w, float h, boolean inList, Runnable action) {
    }

    public PackConverterScreen(Screen parent) {
        super(Text.literal("1.8.9 Pack Converter"));
        this.parent = parent;
        refreshPacks();
    }

    public void refreshPacks() {
        this.packs.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Path packPath = mc.getResourcePackDir();
        if (packPath == null || !Files.exists(packPath)) return;

        File dir = packPath.toFile();
        File[] files = dir.listFiles();
        if (files == null) return;

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isFile() && f.getName().endsWith(".zip")) {
                loadZipPack(f);
            } else if (f.isDirectory()) {
                loadDirPack(f);
            }
        }
    }

    private void loadZipPack(File zipFile) {
        try (ZipFile zf = new ZipFile(zipFile)) {
            ZipEntry mcmetaEntry = zf.getEntry("pack.mcmeta");
            String desc = "Resource Pack";
            int format = 1;
            if (mcmetaEntry != null) {
                try (InputStream is = zf.getInputStream(mcmetaEntry)) {
                    String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    if (root.has("pack")) {
                        JsonObject p = root.getAsJsonObject("pack");
                        if (p.has("pack_format")) format = p.get("pack_format").getAsInt();
                        if (p.has("description")) desc = p.get("description").getAsString();
                    }
                }
            }
            boolean isLegacy = format == 1 || format <= 3 || zipFile.getName().toLowerCase().contains("1.8");
            packs.add(new PackEntry(zipFile, zipFile.getName(), desc, format, isLegacy));
        } catch (Exception e) {
            packs.add(new PackEntry(zipFile, zipFile.getName(), "Unable to read metadata", 1, true));
        }
    }

    private void loadDirPack(File dir) {
        File mcmeta = new File(dir, "pack.mcmeta");
        String desc = "Folder Pack";
        int format = 1;
        if (mcmeta.exists()) {
            try {
                String json = Files.readString(mcmeta.toPath(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (root.has("pack")) {
                    JsonObject p = root.getAsJsonObject("pack");
                    if (p.has("pack_format")) format = p.get("pack_format").getAsInt();
                    if (p.has("description")) desc = p.get("description").getAsString();
                }
            } catch (Exception ignored) {
            }
        }
        boolean isLegacy = format == 1 || format <= 3 || dir.getName().toLowerCase().contains("1.8");
        packs.add(new PackEntry(dir, dir.getName(), desc, format, isLegacy));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int[] stops = Theme.stops();
        Render2D.aurora(ctx, 0, 0, this.width, this.height, (System.nanoTime() - START) / 1.0E9F, stops[0], stops[1]);
        Render2D.rect(ctx, 0, 0, this.width, this.height, 0, 0x55000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float f = Anim.easeOutCubic(this.fade.update(1, 9));
        this.hits.clear();

        this.pw = Math.min(W, this.width - 32);
        this.ph = Math.min(380, this.height - 36);
        this.px = (this.width - pw) / 2;
        this.py = (this.height - ph) / 2 + (1 - f) * 12;
        int accent = Theme.accent(0);
        Render2D.setAlpha(f);

        // Panel background
        Render2D.shadow(ctx, px, py + 4, pw, ph, 14, 28, 0x80000000);
        Render2D.rect(ctx, px, py, pw, ph, 14, 0xD80E0E14);
        Render2D.outline(ctx, px, py, pw, ph, 14, 0.6F, 0x22FFFFFF);

        // Header
        UiFont.TITLE.draw(ctx, "1.8.9 -> 1.21.11 Pack Converter", px + 18, py + 16, 0xFFFFFFFF, false);
        UiFont.SMALL.draw(ctx, "Converts textures, renames items/blocks, & slices GUI sprites", px + 18, py + 34, 0xFF9A9AAA, false);

        // Action buttons in top right
        float btnW = 90;
        float btnH = 22;
        float btnY = py + 16;
        float btnX1 = px + pw - 18 - btnW;
        float btnX2 = btnX1 - btnW - 8;

        drawMiniButton(ctx, "Open Folder", btnX1, btnY, btnW, btnH, mouseX, mouseY, () -> {
            Path p = MinecraftClient.getInstance().getResourcePackDir();
            if (p != null) Util.getOperatingSystem().open(p.toFile());
        });

        drawMiniButton(ctx, "Refresh", btnX2, btnY, btnW, btnH, mouseX, mouseY, this::refreshPacks);

        // Status / Progress bar
        float statusY = py + 54;
        Render2D.rect(ctx, px + 18, statusY, pw - 36, 26, 6, 0xFF14141B);
        Render2D.outline(ctx, px + 18, statusY, pw - 36, 26, 6, 0.5F, 0x1AFFFFFF);

        if (converting) {
            float barW = (pw - 40) * Math.min(1.0F, Math.max(0.02F, convertProgress));
            Render2D.rect(ctx, px + 20, statusY + 2, barW, 22, 4, ColorUtil.withAlpha(accent, 0x40));
        }

        UiFont.SMALL.draw(ctx, statusText, px + 26, statusY + 8, statusColor, false);

        // List layout
        this.listY = statusY + 34;
        this.listH = py + ph - 44 - listY;

        float totalH = packs.size() * ROW_H;
        float maxScroll = Math.max(0, totalH - listH);
        this.scroll = Math.max(0, Math.min(maxScroll, this.scroll));
        float sy = this.scrollAnim.update(this.scroll, 14);

        ctx.enableScissor((int) px, (int) listY, (int) (px + pw), (int) (listY + listH));

        float itemY = listY - sy;
        for (PackEntry pack : packs) {
            if (itemY + ROW_H >= listY && itemY <= listY + listH) {
                drawPackRow(ctx, pack, px + 18, itemY, pw - 36, ROW_H - 6, mouseX, mouseY);
            }
            itemY += ROW_H;
        }

        ctx.disableScissor();

        // Footer Done button
        float closeBtnW = 100;
        float closeBtnH = 24;
        float closeBtnX = px + (pw - closeBtnW) / 2;
        float closeBtnY = py + ph - 34;

        drawMiniButton(ctx, "Done", closeBtnX, closeBtnY, closeBtnW, closeBtnH, mouseX, mouseY, this::close);

        Render2D.setAlpha(1.0F);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawPackRow(DrawContext ctx, PackEntry pack, float x, float y, float w, float h, int mx, int my) {
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int bg = hovered ? 0xFF1C1C24 : 0xFF14141B;
        Render2D.rect(ctx, x, y, w, h, 6, bg);
        Render2D.outline(ctx, x, y, w, h, 6, 0.5F, hovered ? 0x33FFFFFF : 0x14FFFFFF);

        // Format badge
        String badge = pack.isLegacy() ? "1.8.9" : "v" + pack.format();
        int badgeColor = pack.isLegacy() ? 0xFFFFAA00 : 0xFF55FF55;
        float badgeW = UiFont.SMALL.width(badge) + 10;
        Render2D.rect(ctx, x + 8, y + 8, badgeW, 14, 3, ColorUtil.withAlpha(badgeColor, 0x25));
        UiFont.SMALL.draw(ctx, badge, x + 13, y + 10, badgeColor, false);

        // Pack name & description
        float textX = x + 8 + badgeW + 8;
        float maxTextW = w - (textX - x) - 80;
        String title = UiFont.BOLD.trim(pack.name(), maxTextW);
        UiFont.BOLD.draw(ctx, title, textX, y + 6, 0xFFFFFFFF, false);

        String cleanDesc = pack.description().replaceAll("§[0-9a-fk-or]", "").trim();
        String desc = UiFont.SMALL.trim(cleanDesc.isEmpty() ? "No description" : cleanDesc, maxTextW);
        UiFont.SMALL.draw(ctx, desc, textX, y + 22, 0xFF888899, false);

        // Convert Button
        float convW = 66;
        float convH = 22;
        float convX = x + w - convW - 8;
        float convY = y + (h - convH) / 2;

        boolean convHover = mx >= convX && mx <= convX + convW && my >= convY && my <= convY + convH && !converting;
        int accent = Theme.accent(0);
        int btnBg = convHover ? ColorUtil.brighter(accent, 0.15F) : accent;

        Render2D.rect(ctx, convX, convY, convW, convH, 4, btnBg);
        float tw = UiFont.BOLD.width("Convert");
        UiFont.BOLD.draw(ctx, "Convert", convX + (convW - tw) / 2, convY + 6, 0xFFFFFFFF, false);

        if (!converting) {
            hits.add(new Hit(convX, convY, convW, convH, true, () -> startConversion(pack)));
        }
    }

    private void drawMiniButton(DrawContext ctx, String text, float x, float y, float w, float h, int mx, int my, Runnable action) {
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
        Anim a = buttonHover.computeIfAbsent(text, k -> new Anim(0));
        float hv = a.update(hovered ? 1 : 0, 10);

        int bg = ColorUtil.lerp(0xFF1E1E28, 0xFF282836, hv);
        Render2D.rect(ctx, x, y, w, h, 5, bg);
        Render2D.outline(ctx, x, y, w, h, 5, 0.5F, ColorUtil.lerp(0x22FFFFFF, 0x44FFFFFF, hv));

        float tw = UiFont.SMALL.width(text);
        UiFont.SMALL.draw(ctx, text, x + (w - tw) / 2, UiFont.SMALL.centerY(y, h), 0xFFFFFFFF, false);

        hits.add(new Hit(x, y, w, h, false, action));
    }

    private void startConversion(PackEntry pack) {
        if (converting) return;
        converting = true;
        convertProgress = 0F;
        statusText = "Converting " + pack.name() + "...";
        statusColor = 0xFF55FFFF;

        File outputDir = MinecraftClient.getInstance().getResourcePackDir().toFile();

        PackConverter.convertAsync(pack.file(), outputDir, new PackConverter.ProgressListener() {
            @Override
            public void onProgress(int current, int total, String status) {
                convertProgress = total > 0 ? (float) current / total : 0.5F;
                statusText = String.format("[%d%%] %s", (int) (convertProgress * 100), status);
            }

            @Override
            public void onComplete(File convertedFile) {
                converting = false;
                convertProgress = 1.0F;
                statusText = "Successfully converted: " + convertedFile.getName();
                statusColor = 0xFF55FF55;
                refreshPacks();
            }

            @Override
            public void onError(Exception error) {
                converting = false;
                statusText = "Error: " + error.getMessage();
                statusColor = 0xFFFF5555;
            }
        });
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        double mx = click.x();
        double my = click.y();
        for (Hit h : hits) {
            if (h.inList && (my < listY || my > listY + listH)) continue;
            if (mx >= h.x && mx <= h.x + h.w && my >= h.y && my <= h.y + h.h) {
                h.action.run();
                return true;
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= px && mouseX <= px + pw && mouseY >= listY && mouseY <= listY + listH) {
            this.scroll -= (float) (verticalAmount * ROW_H);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (key.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
