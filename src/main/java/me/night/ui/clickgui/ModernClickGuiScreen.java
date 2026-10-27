package me.night.ui.clickgui;

import me.night.Night;
import me.night.config.ConfigManager;
import me.night.module.Category;
import me.night.module.Module;
import me.night.module.modules.render.ClickGui;
import me.night.module.modules.render.HUD;
import me.night.property.Property;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ColorProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.render.Render2D;
import me.night.render.UiFont;
import me.night.ui.hud.HudEditorScreen;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.ExhiSoundManager;
import me.night.util.NightFont;
import me.night.util.NightFont.Type;
import me.night.util.NightRenderUtil;
import me.night.util.Theme;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rise 6 style ClickGUI. Sidebar with module categories and client pages (themes, configs,
 * HUD editor). Module settings expand inline under each card (right click).
 * The window can be dragged by its top bar and resized with the GUI Scale setting / Ctrl + scroll.
 */
public class ModernClickGuiScreen extends Screen {

    // layout (unscaled)
    private static final float W = 520;
    private static final float H = 340;
    private static final float RADIUS = 10;
    private static final float SIDEBAR = 130;
    private static final float CARD_HEAD = 34;
    private static final float CARD_GAP = 6;
    private static final float PAD = 12;
    private static final float TOP_BAR = 44;

    private enum Page { MODULES, THEMES, CONFIGS }

    private record NavItem(String label, Category category, Page page, boolean hudEditor) {
    }

    // state kept between openings
    private static Category selectedCategory = Category.COMBAT;
    private static Page page = Page.MODULES;
    private static final Set<Module> expanded = new HashSet<>();

    private float gx;
    private float gy;
    private float scale = 1.0F;
    private float appliedUserScale = -1;

    private String search = "";
    private boolean searchFocused = false;
    private Module binding = null;
    /** Set when a key was used for binding, so its typed character doesn't reach the search box. */
    private boolean swallowChar = false;

    private String configName = "";
    private boolean configFocused = false;
    private String pendingDelete = null;
    private long pendingDeleteTime = 0;

    private NumberProperty dragNumber = null;
    private ColorProperty dragColor = null;
    private float dragX;
    private float dragW;

    private boolean draggingWindow = false;
    private double windowGrabX, windowGrabY;
    private double windowStartX, windowStartY;

    private final Anim openAnim = new Anim(0.0F);
    private boolean closing = false;

    private final Anim navHighlight = new Anim(-1.0F);
    private final Map<String, Anim> navHover = new HashMap<>();
    private final Map<Object, Anim> anims = new HashMap<>();
    private final Anim scrollAnim = new Anim(0.0F);
    private float scrollTarget = 0.0F;
    private final Anim searchFocusAnim = new Anim(0.0F);
    private final Anim switchAnim = new Anim(1.0F);
    private int switchDir = 1;

    /** Clickable regions collected while rendering, used by the mouse handlers. */
    private final List<Hit> hits = new ArrayList<>();

    private float listX, listY, listW, listH;

    // tooltips: what the mouse is over this frame, and since when
    private int mouseLX, mouseLY;
    private Object hoverKey, tipKey;
    private String hoverText;
    private long tipSince;
    private final Anim tipAnim = new Anim(0);

    private interface Action {
        void run(double mouseX);
    }

    private record Hit(float x, float y, float w, float h, boolean inList, Action left, Action right, Action middle) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    /** Opens the settings of a module (used by the dev screenshot tool). */
    public static void devExpand(Module module) {
        if (module != null) {
            expanded.add(module);
            selectedCategory = module.getCategory();
            page = Page.MODULES;
        }
    }

    /** Opens a client page by name: "themes" or "configs" (used by the dev screenshot tool). */
    public static void devPage(String name) {
        page = name.equals("themes") ? Page.THEMES : name.equals("configs") ? Page.CONFIGS : Page.MODULES;
    }

    /** Test hooks for the dev screenshot tool. */
    public void devStartBinding(Module module) {
        startBinding(module);
    }

    public String devSearchText() {
        return this.search;
    }

    public ModernClickGuiScreen() {
        super(Text.literal("Night Client"));
    }

    private static ClickGui settings() {
        return Night.INSTANCE.getModuleManager().getModule(ClickGui.class);
    }

    private static HUD hud() {
        return Night.INSTANCE.getModuleManager().getModule(HUD.class);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    /** Works out the window position and scale for this frame. */
    private void layout() {
        ClickGui s = settings();
        if (this.dragNumber != s.scale || this.appliedUserScale < 0) {
            // the scale slider only applies when released, so the window doesn't resize under the mouse
            this.appliedUserScale = s.scale.getFloat();
        }
        float maxFit = Math.min((this.width - 16) / W, (this.height - 16) / H);
        this.scale = Math.min(this.appliedUserScale, maxFit);

        float maxOffX = Math.max(0, (this.width - W * this.scale) / 2 - 4);
        float maxOffY = Math.max(0, (this.height - H * this.scale) / 2 - 4);
        float ox = (float) Math.max(-maxOffX, Math.min(maxOffX, s.windowX.getValue()));
        float oy = (float) Math.max(-maxOffY, Math.min(maxOffY, s.windowY.getValue()));
        this.gx = this.width / 2.0F + ox - W / 2;
        this.gy = this.height / 2.0F + oy - H / 2;

        this.listX = gx + SIDEBAR + 14;
        this.listY = gy + 48;
        this.listW = W - SIDEBAR - 28;
        this.listH = H - 48 - 12;
    }

    /** Screen mouse position -> window (unscaled) coordinates. */
    private double localX(double mx) {
        float cx = gx + W / 2;
        return cx + (mx - cx) / this.scale;
    }

    private double localY(double my) {
        float cy = gy + H / 2;
        return cy + (my - cy) / this.scale;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.applyBlur(ctx);
        float a = Anim.easeOutCubic(this.openAnim.get());
        ctx.fill(0, 0, this.width, this.height, ColorUtil.withAlpha(0x05050A, Math.round(0x80 * a)));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float open = this.openAnim.update(this.closing ? 0.0F : 1.0F, this.closing ? 18.0F : 12.0F);
        if (this.closing && open < 0.02F) {
            finishClose();
            return;
        }
        layout();

        float ease = Anim.easeOutCubic(open);
        float s = (0.94F + 0.06F * ease) * this.scale;
        mouseX = (int) Math.round(localX(mouseX));
        mouseY = (int) Math.round(localY(mouseY));
        this.mouseLX = mouseX;
        this.mouseLY = mouseY;
        this.hoverKey = null;
        this.hoverText = null;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(gx + W / 2, gy + H / 2);
        m.scale(s, s);
        m.translate(-(gx + W / 2), -(gy + H / 2));
        Render2D.setAlpha(ease);

        this.hits.clear();

        // window
        Render2D.shadow(ctx, gx, gy + 4, W, H, RADIUS, 26, 0x90000000);
        Render2D.shadow(ctx, gx, gy, W, H, RADIUS, 18, ColorUtil.withAlpha(Theme.accent(0), 0x22));
        Render2D.rect(ctx, gx, gy, W, H, RADIUS, Theme.BG);
        Render2D.rect(ctx, gx, gy, SIDEBAR + RADIUS * 2, H, RADIUS, Theme.SIDEBAR);
        Render2D.rect(ctx, gx + SIDEBAR, gy, RADIUS * 2, H, 0, Theme.BG);
        Render2D.rect(ctx, gx + SIDEBAR, gy + 10, 0.5F, H - 20, 0, Theme.STROKE);
        Render2D.outline(ctx, gx, gy, W, H, RADIUS, 0.5F, 0x1CFFFFFF);

        renderSidebar(ctx, mouseX, mouseY);
        renderHeader(ctx, mouseX, mouseY);

        // page content slides in when the page changes
        float sw = Anim.easeOutCubic(this.switchAnim.update(1.0F, 9.0F));
        m.pushMatrix();
        m.translate(0, (1 - sw) * 14 * this.switchDir);
        Render2D.setAlpha(ease * sw);
        switch (page) {
            case MODULES -> renderModules(ctx, mouseX, mouseY);
            case THEMES -> renderScrollable(ctx, mouseX, mouseY, this::renderThemes);
            case CONFIGS -> renderScrollable(ctx, mouseX, mouseY, this::renderConfigs);
        }
        m.popMatrix();

        Render2D.setAlpha(ease);
        renderTooltip(ctx);
        Render2D.resetAlpha();
        m.popMatrix();
    }

    private void renderTooltip(DrawContext ctx) {
        long now = System.currentTimeMillis();
        if (this.hoverKey != this.tipKey) {
            this.tipKey = this.hoverKey;
            this.tipSince = now;
        }
        boolean show = this.hoverKey != null && this.hoverText != null && !this.hoverText.isEmpty() && now - this.tipSince > 400;
        float t = this.tipAnim.update(show ? 1 : 0, show ? 14 : 30);
        if (t < 0.02F || this.hoverText == null) return;

        UiFont f = UiFont.SMALL;
        float maxW = 190;
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : this.hoverText.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (f.width(test) > maxW && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        float w = 0;
        for (String l : lines) w = Math.max(w, f.width(l));
        w += 14;
        float lh = f.capHeight() + 5;
        float h = lines.size() * lh + 9;
        float x = Math.min(this.mouseLX + 10, gx + W - w - 6);
        float y = Math.min(this.mouseLY + 12, gy + H - h - 6);

        float prev = Render2D.alpha();
        Render2D.setAlpha(prev * t);
        Render2D.shadow(ctx, x, y + 2, w, h, 6, 10, 0x70000000);
        Render2D.rect(ctx, x, y, w, h, 6, 0xF2181820);
        Render2D.outline(ctx, x, y, w, h, 6, 0.5F, ColorUtil.withAlpha(Theme.accent(0), 0x70));
        float ty = y + 6;
        for (String l : lines) {
            f.draw(ctx, l, x + 7, ty, 0xFFE6E6EE, false);
            ty += lh;
        }
        Render2D.setAlpha(prev);
    }

    private boolean mouseInList() {
        return inside(this.mouseLX, this.mouseLY, listX, listY, listW, listH);
    }

    private List<NavItem> navItems() {
        List<NavItem> items = new ArrayList<>();
        for (Category c : Category.values()) items.add(new NavItem(c.getName(), c, Page.MODULES, false));
        items.add(new NavItem("Themes", null, Page.THEMES, false));
        items.add(new NavItem("Configs", null, Page.CONFIGS, false));
        items.add(new NavItem("HUD Editor", null, null, true));
        return items;
    }

    private float navY(int index) {
        int cats = Category.values().length;
        float startY = gy + 62;
        return index < cats ? startY + index * 24 : startY + cats * 24 + 22 + (index - cats) * 24;
    }

    private boolean isSelected(NavItem item) {
        if (item.hudEditor) return false;
        if (item.page != page) return false;
        return item.page != Page.MODULES || (!searchActive() && item.category == selectedCategory);
    }

    private void renderSidebar(DrawContext ctx, int mouseX, int mouseY) {
        // logo
        String logo = "Night";
        float lx = gx + 18;
        float ly = gy + 16;
        int logoW = NightFont.width(Type.LARGE, logo);
        Render2D.shadow(ctx, lx + 2, ly + 4, logoW - 4, 9, 4, 12, ColorUtil.withAlpha(Theme.accent(0), 0x40));
        NightFont.drawGradient(ctx, Type.LARGE, logo, lx, NightFont.centerY(Type.LARGE, ly, 16), i -> Theme.accent(i * 6), false);
        NightFont.draw(ctx, Type.SMALL, "v" + Night.CLIENT_VERSION, lx + logoW + 3, ly - 1, Theme.TEXT_MUTED, false);

        int cats = Category.values().length;
        UiFont.SMALL.draw(ctx, "CATEGORIES", gx + 20, gy + 50, Theme.TEXT_MUTED, false);
        UiFont.SMALL.draw(ctx, "CLIENT", gx + 20, navY(cats) - 12, Theme.TEXT_MUTED, false);

        List<NavItem> items = navItems();
        float itemH = 22;
        int accent = Theme.accent(0);

        // sliding highlight
        int sel = -1;
        for (int i = 0; i < items.size(); i++) if (isSelected(items.get(i))) sel = i;
        if (sel >= 0) {
            float target = navY(sel);
            if (this.navHighlight.get() < 0) this.navHighlight.set(target);
            float hy = this.navHighlight.update(target, 16.0F);
            Render2D.rectH(ctx, gx + 10, hy, SIDEBAR - 20, itemH, 6,
                    ColorUtil.withAlpha(accent, 0x38), ColorUtil.withAlpha(Theme.accentAlt(0), 0x10));
            Render2D.rect(ctx, gx + 10, hy + 6, 2, itemH - 12, 1, accent);
        }

        for (int i = 0; i < items.size(); i++) {
            NavItem item = items.get(i);
            float bx = gx + 10;
            float by = navY(i);
            float bw = SIDEBAR - 20;
            boolean selected = i == sel;
            boolean hovered = inside(mouseX, mouseY, bx, by, bw, itemH);

            float hv = this.navHover.computeIfAbsent(item.label, k -> new Anim()).update(hovered && !selected ? 1.0F : 0.0F, 14.0F);
            if (hv > 0.01F) Render2D.rect(ctx, bx, by, bw, itemH, 6, ColorUtil.withAlpha(0xFFFFFF, Math.round(10 * hv)));

            int textCol = selected ? Theme.TEXT : ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv * 0.6F);
            int dotCol = selected ? accent : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv);
            Render2D.circle(ctx, bx + 13, by + itemH / 2, 2.25F, dotCol);
            UiFont f = selected ? UiFont.BOLD : UiFont.REGULAR;
            f.draw(ctx, item.label, bx + 22 + hv * 2, f.centerY(by, itemH), textCol, false);
            if (item.hudEditor) {
                // small arrow: this opens another screen
                float ax = bx + bw - 12 + hv * 2, ay = by + itemH / 2;
                Render2D.rect(ctx, ax - 3, ay - 0.5F, 6, 1, 0.5F, dotCol);
                Render2D.circle(ctx, ax + 3, ay, 1.4F, dotCol);
            }

            this.hits.add(new Hit(bx, by, bw, itemH, false, mx -> openNav(item), null, null));
        }

        // profile
        float py = gy + H - 38;
        Render2D.rect(ctx, gx + 14, py - 8, SIDEBAR - 28, 0.5F, 0, Theme.STROKE);
        if (this.client != null && this.client.player != null) {
            Render2D.rect(ctx, gx + 13, py - 1, 22, 22, 5, Theme.CARD);
            Render2D.head(ctx, this.client.player.getSkin(), gx + 15, py + 1, 18, 4, 0xFFFFFFFF);
            String name = UiFont.BOLD.trim(this.client.player.getName().getString(), SIDEBAR - 56);
            NightFont.draw(ctx, Type.BOLD, name, gx + 41, py + 1, Theme.TEXT, false);
        } else {
            String guest = this.client != null ? this.client.getSession().getUsername() : "Guest";
            Render2D.rectH(ctx, gx + 15, py + 1, 18, 18, 4, Theme.accent(0), Theme.accentAlt(0));
            UiFont.BOLD.drawCentered(ctx, guest.substring(0, 1).toUpperCase(), gx + 24, UiFont.BOLD.centerY(py + 1, 18), 0xFFFFFFFF, false);
            NightFont.draw(ctx, Type.BOLD, UiFont.BOLD.trim(guest, SIDEBAR - 56), gx + 41, py + 1, Theme.TEXT, false);
        }
        Render2D.circle(ctx, gx + 43, py + 15.5F, 1.75F, 0xFF4ADE80);
        NightFont.draw(ctx, Type.SMALL, "Online", gx + 48, py + 12, Theme.TEXT_DIM, false);
    }

    private void renderHeader(DrawContext ctx, int mouseX, int mouseY) {
        float hx = gx + SIDEBAR + 16;
        float hy = gy + 14;

        String title;
        String sub;
        switch (page) {
            case THEMES -> {
                title = "Themes";
                sub = hud().theme.getModes().size() + " themes";
            }
            case CONFIGS -> {
                title = "Configs";
                int n = ConfigManager.getConfigs().size();
                sub = n + (n == 1 ? " config" : " configs");
            }
            default -> {
                title = searchActive() ? "Search" : selectedCategory.getName();
                int count = visibleModules().size();
                sub = count + (count == 1 ? " module" : " modules");
            }
        }
        NightFont.draw(ctx, Type.TITLE, title, hx, NightFont.centerY(Type.TITLE, hy, 22) - 1, Theme.TEXT, false);
        int titleW = NightFont.width(Type.TITLE, title);
        NightFont.draw(ctx, Type.SMALL, sub, hx + titleW + 7, NightFont.centerY(Type.SMALL, hy, 22) + 1, Theme.TEXT_MUTED, false);

        if (page == Page.MODULES) {
            renderSearchBox(ctx, mouseX, mouseY, hy);
        } else if (page == Page.CONFIGS) {
            String label = "Open folder";
            float bw = UiFont.MEDIUM.width(label) + 22;
            float bx = gx + W - 16 - bw;
            button(ctx, mouseX, mouseY, label, bx, hy, bw, 22, false, false, false, mx -> ConfigManager.openFolder());
        }
    }

    private void renderSearchBox(DrawContext ctx, int mouseX, int mouseY, float hy) {
        float sw = 140;
        float sh = 22;
        float sx = gx + W - 16 - sw;
        float focus = this.searchFocusAnim.update(this.searchFocused ? 1.0F : 0.0F, 14.0F);
        boolean hovered = inside(mouseX, mouseY, sx, hy, sw, sh);
        Render2D.rect(ctx, sx, hy, sw, sh, 6, hovered || this.searchFocused ? Theme.CARD_HOVER : Theme.CARD);
        Render2D.outline(ctx, sx, hy, sw, sh, 6, 0.5F, ColorUtil.lerp(Theme.STROKE, ColorUtil.withAlpha(Theme.accent(0), 0xC0), focus));

        float mx = sx + 12;
        float my = hy + sh / 2 - 1;
        int iconCol = ColorUtil.lerp(Theme.TEXT_MUTED, Theme.accent(0), focus);
        Render2D.outline(ctx, mx - 3.5F, my - 3.5F, 7, 7, 3.5F, 1.0F, iconCol);
        Render2D.rect(ctx, mx + 2.5F, my + 2.5F, 1.5F, 1.5F, 0.5F, iconCol);
        Render2D.rect(ctx, mx + 3.5F, my + 3.5F, 1.5F, 1.5F, 0.5F, iconCol);

        float tx = sx + 22;
        float ty = NightFont.centerY(Type.REGULAR, hy, sh);
        if (this.search.isEmpty()) {
            NightFont.draw(ctx, Type.REGULAR, "Search modules...", tx, ty, Theme.TEXT_MUTED, false);
        } else {
            String shown = this.search;
            while (NightFont.width(Type.REGULAR, shown) > sw - 30 && shown.length() > 1) shown = shown.substring(1);
            NightFont.draw(ctx, Type.REGULAR, shown, tx, ty, Theme.TEXT, false);
            tx += NightFont.widthF(Type.REGULAR, shown);
        }
        if (this.searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            Render2D.rect(ctx, tx + 1, hy + 6, 0.75F, sh - 12, 0, Theme.TEXT);
        }

        this.hits.add(new Hit(sx, hy, sw, sh, false, x -> this.searchFocused = true, x -> {
            this.search = "";
            this.searchFocused = true;
        }, null));
    }

    // ------------------------------------------------------------------ shared widgets

    private Anim anim(Object key, float initial) {
        return this.anims.computeIfAbsent(key, k -> new Anim(initial));
    }

    /** Rounded button that registers a click target. */
    private void button(DrawContext ctx, int mouseX, int mouseY, String label, float x, float y, float w, float h,
                        boolean primary, boolean danger, boolean inList, Action onClick) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h) && (!inList || inside(mouseX, mouseY, listX, listY, listW, listH));
        float hv = anim("btn:" + label + "@" + Math.round(x) + "," + Math.round(y - (inList ? this.scrollAnim.get() : 0)), 0)
                .update(hovered ? 1 : 0, 16);
        int accent = Theme.accent(0);
        if (primary) {
            Render2D.shadow(ctx, x, y, w, h, 6, 8, ColorUtil.withAlpha(accent, Math.round(0x30 + 0x40 * hv)));
            Render2D.rectH(ctx, x, y, w, h, 6, ColorUtil.brighter(accent, 0.12F * hv), ColorUtil.brighter(Theme.accentAlt(0), 0.12F * hv));
        } else if (danger) {
            Render2D.rect(ctx, x, y, w, h, 6, ColorUtil.lerp(0x40FF5A6E, 0x80FF5A6E, hv));
            Render2D.outline(ctx, x, y, w, h, 6, 0.5F, 0x90FF5A6E);
        } else {
            Render2D.rect(ctx, x, y, w, h, 6, ColorUtil.lerp(Theme.CARD_INNER, Theme.CARD_HOVER, hv));
            Render2D.outline(ctx, x, y, w, h, 6, 0.5F, ColorUtil.lerp(Theme.STROKE, 0x30FFFFFF, hv));
        }
        UiFont f = h >= 20 ? UiFont.MEDIUM : UiFont.SMALL;
        int col = primary || danger ? 0xFFFFFFFF : ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv);
        f.drawCentered(ctx, label, x + w / 2, f.centerY(y, h), col, false);
        this.hits.add(new Hit(x, y, w, h, inList, onClick, null, null));
    }

    // ------------------------------------------------------------------ modules page

    private void renderModules(DrawContext ctx, int mouseX, int mouseY) {
        renderScrollable(ctx, mouseX, mouseY, (c, mx, my, x, y, w) -> {
            List<Module> mods = visibleModules();
            float start = y;
            boolean mouseInList = inside(mx, my, listX, listY, listW, listH);
            for (Module mod : mods) {
                y += renderCard(c, mod, x, y, w, mx, my, mouseInList) + CARD_GAP;
            }
            if (mods.isEmpty()) {
                String msg = "No modules found";
                NightFont.draw(c, Type.REGULAR, msg, x + (w - NightFont.width(Type.REGULAR, msg)) / 2, y + 40, Theme.TEXT_MUTED, false);
                return 60;
            }
            return y - start - CARD_GAP;
        });
    }

    private interface Content {
        /** Draws page content at (x, y) and returns its height. */
        float draw(DrawContext ctx, int mouseX, int mouseY, float x, float y, float w);
    }

    /** Draws content inside the scrolling, clipped list area with a scrollbar. */
    private void renderScrollable(DrawContext ctx, int mouseX, int mouseY, Content content) {
        float scroll = this.scrollAnim.update(this.scrollTarget, 14.0F);
        ctx.enableScissor(Math.round(listX - 1), Math.round(listY), Math.round(listX + listW + 1), Math.round(listY + listH));
        float height = content.draw(ctx, mouseX, mouseY, listX, listY + scroll, listW);
        ctx.disableScissor();

        float minScroll = Math.min(0, listH - height - 4);
        this.scrollTarget = Math.max(minScroll, Math.min(0, this.scrollTarget));
        if (height > listH) {
            float thumbH = Math.max(20, listH * listH / height);
            float thumbY = listY + (listH - thumbH) * (scroll / Math.min(-0.001F, minScroll));
            Render2D.rect(ctx, gx + W - 7, thumbY, 2, thumbH, 1, 0x30FFFFFF);
        }
    }

    /** Renders one module card and returns its height. */
    private float renderCard(DrawContext ctx, Module mod, float x, float y, float w, int mouseX, int mouseY, boolean mouseInList) {
        boolean open = expanded.contains(mod);
        float expand = Anim.easeInOutCubic(anim("expand:" + mod.getName(), open ? 1 : 0).update(open ? 1.0F : 0.0F, 12.0F));
        float settingsH = expand > 0.001F ? renderSettings(ctx, mod, x, y + CARD_HEAD, w, false) : 0;
        float h = CARD_HEAD + settingsH * expand;

        if (y > listY + listH || y + h < listY) return h; // off screen: layout only

        boolean hovered = mouseInList && inside(mouseX, mouseY, x, y, w, CARD_HEAD);
        float hv = anim("hover:" + mod.getName(), 0).update(hovered ? 1.0F : 0.0F, 14.0F);
        float on = anim("on:" + mod.getName(), mod.isEnabled() ? 1 : 0).update(mod.isEnabled() ? 1.0F : 0.0F, 14.0F);
        int accent = Theme.accent(0);

        Render2D.rect(ctx, x, y, w, h, 7, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, hv));
        Render2D.outline(ctx, x, y, w, h, 7, 0.5F, ColorUtil.lerp(Theme.STROKE, ColorUtil.withAlpha(accent, 0x50), on * 0.8F));
        if (on > 0.01F) {
            Render2D.rectH(ctx, x, y, w * 0.6F, CARD_HEAD, 7, ColorUtil.withAlpha(accent, Math.round(0x16 * on)), ColorUtil.withAlpha(accent, 0));
        }

        int nameCol = ColorUtil.lerp(Theme.TEXT, ColorUtil.brighter(accent, 0.25F), on);
        NightFont.draw(ctx, Type.BOLD, mod.getName(), x + PAD, y + 8, nameCol, false);
        String desc = mod.getDescription() == null || mod.getDescription().isEmpty() ? mod.getCategory().getName() : mod.getDescription();
        String shownDesc = UiFont.SMALL.trim(desc, w - PAD * 2 - 90);
        NightFont.draw(ctx, Type.SMALL, shownDesc, x + PAD, y + 19, Theme.TEXT_MUTED, false);
        if (hovered && !shownDesc.equals(desc)) {
            this.hoverKey = mod; // full description when it doesn't fit
            this.hoverText = desc;
        }

        float swW = 24, swH = 13;
        float swX = x + w - PAD - swW;
        float swY = y + (CARD_HEAD - swH) / 2;
        NightRenderUtil.toggleSwitch(ctx, swX, swY, swW, swH, on, accent);

        // plus that morphs into a minus when expanded
        float px = swX - 14;
        float pyc = y + CARD_HEAD / 2;
        int plusCol = ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, Math.max(hv, expand));
        Render2D.rect(ctx, px - 3.5F, pyc - 0.6F, 7, 1.2F, 0.6F, plusCol);
        float vh = 7 * (1.0F - expand);
        if (vh > 0.2F) Render2D.rect(ctx, px - 0.6F, pyc - vh / 2, 1.2F, vh, 0.6F, plusCol);

        if (mod.getKey() > 0 || this.binding == mod) {
            String key = this.binding == mod ? "..." : getKeyName(mod.getKey());
            float kw = NightFont.width(Type.SMALL, key) + 8;
            float kx = px - 10 - kw;
            float ky = y + (CARD_HEAD - 12) / 2;
            Render2D.rect(ctx, kx, ky, kw, 12, 3.5F, Theme.CARD_INNER);
            Render2D.outline(ctx, kx, ky, kw, 12, 3.5F, 0.5F, this.binding == mod ? accent : Theme.STROKE);
            NightFont.draw(ctx, Type.SMALL, key, kx + 4, NightFont.centerY(Type.SMALL, ky, 12), Theme.TEXT_DIM, false);
        }

        this.hits.add(new Hit(x, y, w, CARD_HEAD, true,
                mx -> {
                    mod.toggle();
                    ExhiSoundManager.playClick(mod.isEnabled() ? 1.1F : 0.9F);
                },
                mx -> {
                    if (!expanded.remove(mod)) expanded.add(mod);
                    ExhiSoundManager.playClick(1.0F);
                },
                mx -> startBinding(mod)));

        if (expand > 0.001F) {
            ctx.enableScissor(Math.round(x), Math.round(y + CARD_HEAD), Math.round(x + w), Math.round(y + h));
            float prevAlpha = Render2D.alpha();
            Render2D.setAlpha(prevAlpha * Math.min(1.0F, expand * 1.4F));
            renderSettings(ctx, mod, x, y + CARD_HEAD, w, true);
            Render2D.setAlpha(prevAlpha);
            ctx.disableScissor();
        }
        return h;
    }

    /** Lays out (and when draw is true, renders) a module's settings. Returns the height used. */
    private float renderSettings(DrawContext ctx, Module mod, float x, float y, float w, boolean draw) {
        float startY = y;
        float rx = x + PAD;
        float rw = w - PAD * 2;
        int accent = Theme.accent(0);

        if (draw) Render2D.rect(ctx, rx, y, rw, 0.5F, 0, Theme.STROKE);
        y += 6;

        float rowH = 18;
        if (draw) {
            label(ctx, "Keybind", rx, y, rowH);
            String key = this.binding == mod ? "Press a key..." : getKeyName(mod.getKey());
            float kw = NightFont.width(Type.SMALL, key) + 12;
            float kx = rx + rw - kw;
            float ky = y + (rowH - 13) / 2;
            Render2D.rect(ctx, kx, ky, kw, 13, 4, Theme.CARD_INNER);
            Render2D.outline(ctx, kx, ky, kw, 13, 4, 0.5F, this.binding == mod ? accent : Theme.STROKE);
            NightFont.draw(ctx, Type.SMALL, key, kx + 6, NightFont.centerY(Type.SMALL, ky, 13), this.binding == mod ? Theme.TEXT : Theme.TEXT_DIM, false);
            this.hits.add(new Hit(kx, ky, kw, 13, true, m -> startBinding(mod), m -> {
                mod.setKey(0);
                this.binding = null;
                ExhiSoundManager.playClick(0.8F);
            }, null));
        }
        y += rowH;

        for (Property<?> prop : mod.getProperties()) {
            if (!prop.isVisible()) continue;
            y += renderProperty(ctx, prop, rx, y, rw, draw);
        }
        return y + 4 - startY;
    }

    /** One setting row (with a tooltip on hover). Returns its height. */
    private float renderProperty(DrawContext ctx, Property<?> prop, float rx, float y, float rw, boolean draw) {
        float h = renderPropertyRow(ctx, prop, rx, y, rw, draw);
        if (draw && mouseInList() && inside(this.mouseLX, this.mouseLY, rx, y, rw, h)) {
            this.hoverKey = prop;
            this.hoverText = prop.getDescription();
        }
        return h;
    }

    private float renderPropertyRow(DrawContext ctx, Property<?> prop, float rx, float y, float rw, boolean draw) {
        int accent = Theme.accent(0);
        if (prop instanceof BooleanProperty bp) {
            float rowH = 18;
            if (draw) {
                label(ctx, bp.getName(), rx, y, rowH);
                float on = anim(bp, bp.getValue() ? 1 : 0).update(bp.getValue() ? 1.0F : 0.0F, 14.0F);
                NightRenderUtil.toggleSwitch(ctx, rx + rw - 20, y + (rowH - 11) / 2, 20, 11, on, accent);
                this.hits.add(new Hit(rx, y, rw, rowH, true, m -> {
                    bp.toggle();
                    ExhiSoundManager.playClick(1.1F);
                }, null, null));
            }
            return rowH;
        }
        if (prop instanceof NumberProperty np) {
            float rowH = 27;
            if (draw) {
                label(ctx, np.getName(), rx, y, 16);
                String val = formatNumber(np);
                NightFont.draw(ctx, Type.SMALL, val, rx + rw - NightFont.widthF(Type.SMALL, val), NightFont.centerY(Type.SMALL, y, 16), Theme.TEXT, false);
                float ty = y + 18;
                float ratio = (float) ((np.getValue() - np.getMin()) / (np.getMax() - np.getMin()));
                float shown = anim(np, ratio).update(ratio, 20.0F);
                Render2D.rect(ctx, rx, ty, rw, 3, 1.5F, 0xFF262631);
                Render2D.rectH(ctx, rx, ty, Math.max(3, rw * shown), 3, 1.5F, accent, Theme.accentAlt(0));
                float kx = rx + rw * shown;
                boolean active = this.dragNumber == np;
                Render2D.shadow(ctx, kx - 4, ty - 2.5F, 8, 8, 4, 4, ColorUtil.withAlpha(accent, active ? 0x70 : 0x30));
                Render2D.circle(ctx, kx, ty + 1.5F, active ? 4.5F : 4.0F, 0xFFFFFFFF);
                this.hits.add(new Hit(rx - 4, ty - 6, rw + 8, 15, true, m -> {
                    this.dragNumber = np;
                    this.dragX = rx;
                    this.dragW = rw;
                    updateDrag(m);
                }, null, null));
            }
            return rowH;
        }
        if (prop instanceof ModeProperty mp) {
            return layoutMode(ctx, mp, rx, y, rw, draw, accent);
        }
        if (prop instanceof ColorProperty cp) {
            float rowH = 27;
            if (draw) {
                label(ctx, cp.getName(), rx, y, 16);
                Render2D.rect(ctx, rx + rw - 16, y + 4, 16, 8, 3, cp.getRGB() | 0xFF000000);
                Render2D.outline(ctx, rx + rw - 16, y + 4, 16, 8, 3, 0.5F, 0x30FFFFFF);
                float ty = y + 17;
                NightRenderUtil.hueBar(ctx, rx, ty, rw, 4);
                float[] hsb = Color.RGBtoHSB(cp.getValue().getRed(), cp.getValue().getGreen(), cp.getValue().getBlue(), null);
                float kx = rx + rw * hsb[0];
                Render2D.circle(ctx, kx, ty + 2, 4.0F, 0xFFFFFFFF);
                Render2D.circle(ctx, kx, ty + 2, 2.5F, cp.getRGB() | 0xFF000000);
                this.hits.add(new Hit(rx - 4, ty - 5, rw + 8, 14, true, m -> {
                    this.dragColor = cp;
                    this.dragX = rx;
                    this.dragW = rw;
                    updateDrag(m);
                }, null, null));
            }
            return rowH;
        }
        return 0;
    }

    /** Mode setting: options shown as pills, inline when they fit, otherwise wrapped below the label. */
    private float layoutMode(DrawContext ctx, ModeProperty mp, float rx, float y, float rw, boolean draw, int accent) {
        float pillH = 13;
        float gap = 3;
        List<String> modes = mp.getModes();

        float total = 0;
        for (String s : modes) total += NightFont.widthF(Type.SMALL, s) + 12 + gap;
        total -= gap;
        float labelW = NightFont.widthF(Type.REGULAR, mp.getName()) + 12;
        boolean inline = total <= rw - labelW;

        float rowH = 18;
        if (draw) label(ctx, mp.getName(), rx, y, rowH);

        float px, py, height;
        if (inline) {
            px = rx + rw - total;
            py = y + (rowH - pillH) / 2;
            height = rowH;
        } else {
            px = rx;
            py = y + rowH;
            float cx = rx;
            int lines = 1;
            for (String s : modes) {
                float pw = NightFont.widthF(Type.SMALL, s) + 12;
                if (cx + pw > rx + rw) {
                    lines++;
                    cx = rx;
                }
                cx += pw + gap;
            }
            height = rowH + lines * (pillH + gap) + 3;
        }

        if (draw) {
            float cx = px, cy = py;
            for (String s : modes) {
                float pw = NightFont.widthF(Type.SMALL, s) + 12;
                if (!inline && cx + pw > rx + rw) {
                    cx = rx;
                    cy += pillH + gap;
                }
                boolean sel = mp.is(s);
                float sa = anim(mp.getName() + "#" + s + "@" + System.identityHashCode(mp), sel ? 1 : 0).update(sel ? 1.0F : 0.0F, 16.0F);
                Render2D.rect(ctx, cx, cy, pw, pillH, 4, Theme.CARD_INNER);
                if (sa > 0.01F) {
                    Render2D.rectH(ctx, cx, cy, pw, pillH, 4, ColorUtil.mulAlpha(accent, sa), ColorUtil.mulAlpha(Theme.accentAlt(0), sa));
                }
                Render2D.outline(ctx, cx, cy, pw, pillH, 4, 0.5F, Theme.STROKE);
                NightFont.draw(ctx, Type.SMALL, s, cx + 6, NightFont.centerY(Type.SMALL, cy, pillH), ColorUtil.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, sa), false);
                this.hits.add(new Hit(cx, cy, pw, pillH, true, m -> {
                    mp.setValue(s);
                    ExhiSoundManager.playClick(1.0F);
                }, null, null));
                cx += pw + gap;
            }
        }
        return height;
    }

    private void label(DrawContext ctx, String text, float x, float rowY, float rowH) {
        NightFont.draw(ctx, Type.REGULAR, text, x, NightFont.centerY(Type.REGULAR, rowY, rowH), Theme.TEXT_DIM, false);
    }

    // ------------------------------------------------------------------ themes page

    private float renderThemes(DrawContext ctx, int mouseX, int mouseY, float x, float y, float w) {
        HUD hud = hud();
        List<String> themes = hud.theme.getModes();
        float start = y;
        int cols = 3;
        float gap = 8;
        float cw = (w - gap * (cols - 1)) / cols;
        float ch = 64;
        float time = (System.nanoTime() / 1.0E9F) % 10000;
        boolean mouseInList = inside(mouseX, mouseY, listX, listY, listW, listH);

        for (int i = 0; i < themes.size(); i++) {
            String name = themes.get(i);
            float cx = x + (i % cols) * (cw + gap);
            float cy = y + (i / cols) * (ch + gap);
            boolean sel = hud.theme.is(name);
            boolean hovered = mouseInList && inside(mouseX, mouseY, cx, cy, cw, ch);
            float hv = anim("theme-h:" + name, 0).update(hovered ? 1 : 0, 14);
            float sv = anim("theme-s:" + name, sel ? 1 : 0).update(sel ? 1 : 0, 14);
            int[] stops = hud.getThemeStops(name);

            if (sv > 0.01F) Render2D.shadow(ctx, cx, cy, cw, ch, 8, 12, ColorUtil.withAlpha(stops[0], Math.round(0x60 * sv)));
            Render2D.rect(ctx, cx, cy, cw, ch, 8, Theme.CARD);
            // live preview of the theme
            float pw = cw - 8, ph = ch - 26;
            Render2D.aurora(ctx, cx + 4, cy + 4, pw, ph, 6, time + i * 7, stops[0], stops[1]);
            Render2D.rectH(ctx, cx + 10, cy + ph, pw - 12, 2, 1, stops[0], stops[1]);
            UiFont f = sel ? UiFont.BOLD : UiFont.MEDIUM;
            f.draw(ctx, name, cx + 8, f.centerY(cy + ph + 4, 22), sel ? Theme.TEXT : ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), false);

            if (sv > 0.01F) {
                float kx = cx + cw - 13, ky = cy + ph + 4 + 11;
                Render2D.circle(ctx, kx, ky, 5.5F * sv, stops[0] | 0xFF000000);
                Render2D.circle(ctx, kx, ky, 2.0F * sv, 0xFFFFFFFF);
            }
            Render2D.outline(ctx, cx, cy, cw, ch, 8, sel ? 1.0F : 0.5F,
                    ColorUtil.lerp(ColorUtil.lerp(Theme.STROKE, 0x30FFFFFF, hv), ColorUtil.withAlpha(stops[0], 0xE0), sv));

            this.hits.add(new Hit(cx, cy, cw, ch, true, m -> {
                hud.theme.setValue(name);
                ExhiSoundManager.playClick(1.1F);
            }, null, null));
        }
        y += ((themes.size() + cols - 1) / cols) * (ch + gap) + 4;

        // customise card
        ClickGui gui = settings();
        List<Property<?>> rows = new ArrayList<>();
        if (hud.theme.is("Custom")) rows.add(gui.accent);
        rows.add(hud.speed);
        rows.add(hud.glow);
        rows.add(gui.scale);

        float inner = 0;
        for (Property<?> p : rows) inner += renderProperty(ctx, p, 0, 0, w - PAD * 2, false);
        float cardH = 28 + inner + 6;
        Render2D.rect(ctx, x, y, w, cardH, 8, Theme.CARD);
        Render2D.outline(ctx, x, y, w, cardH, 8, 0.5F, Theme.STROKE);
        UiFont.BOLD.draw(ctx, "Customize", x + PAD, y + 10, Theme.TEXT, false);
        if (!hud.theme.is("Custom")) {
            UiFont.SMALL.draw(ctx, "Choose \"Custom\" to pick your own colour", x + PAD + UiFont.BOLD.width("Customize") + 8, y + 11, Theme.TEXT_MUTED, false);
        }
        float ry = y + 26;
        for (Property<?> p : rows) ry += renderProperty(ctx, p, x + PAD, ry, w - PAD * 2, true);
        y += cardH;
        return y - start;
    }

    // ------------------------------------------------------------------ configs page

    private float renderConfigs(DrawContext ctx, int mouseX, int mouseY, float x, float y, float w) {
        float start = y;
        int accent = Theme.accent(0);

        // new config row
        float fh = 24;
        float createW = 70;
        float fw = w - createW - 6;
        float focus = anim("cfg-focus", 0).update(this.configFocused ? 1 : 0, 14);
        Render2D.rect(ctx, x, y, fw, fh, 6, this.configFocused ? Theme.CARD_HOVER : Theme.CARD);
        Render2D.outline(ctx, x, y, fw, fh, 6, 0.5F, ColorUtil.lerp(Theme.STROKE, ColorUtil.withAlpha(accent, 0xC0), focus));
        float tx = x + 10;
        float ty = UiFont.REGULAR.centerY(y, fh);
        if (this.configName.isEmpty()) {
            UiFont.REGULAR.draw(ctx, "New config name...", tx, ty, Theme.TEXT_MUTED, false);
        } else {
            tx = UiFont.REGULAR.draw(ctx, this.configName, tx, ty, Theme.TEXT, false);
        }
        if (this.configFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            Render2D.rect(ctx, tx + 1, y + 6, 0.75F, fh - 12, 0, Theme.TEXT);
        }
        this.hits.add(new Hit(x, y, fw, fh, true, m -> this.configFocused = true, null, null));
        button(ctx, mouseX, mouseY, "Create", x + fw + 6, y, createW, fh, true, false, true, m -> createConfig());
        y += fh + 10;

        List<String> configs = ConfigManager.getConfigs();
        if (configs.isEmpty()) {
            UiFont.REGULAR.drawCentered(ctx, "No configs yet", x + w / 2, y + 20, Theme.TEXT_MUTED, false);
            return y + 40 - start;
        }

        long now = System.currentTimeMillis();
        if (this.pendingDelete != null && now - this.pendingDeleteTime > 3000) this.pendingDelete = null;

        for (String name : configs) {
            float rh = 38;
            boolean active = name.equalsIgnoreCase(ConfigManager.current);
            boolean auto = name.equalsIgnoreCase(ConfigManager.AUTO);
            Render2D.rect(ctx, x, y, w, rh, 7, Theme.CARD);
            Render2D.outline(ctx, x, y, w, rh, 7, 0.5F, active ? ColorUtil.withAlpha(accent, 0x70) : Theme.STROKE);

            float nx = x + PAD;
            float endX = UiFont.BOLD.draw(ctx, name, nx, y + 9, Theme.TEXT, false);
            if (active || auto) {
                String badge = active ? "Active" : "Auto-save";
                float bw = UiFont.SMALL.width(badge) + 10;
                Render2D.rect(ctx, endX + 6, y + 7, bw, 12, 6, active ? ColorUtil.withAlpha(accent, 0x40) : 0x20FFFFFF);
                UiFont.SMALL.draw(ctx, badge, endX + 11, UiFont.SMALL.centerY(y + 7, 12), active ? 0xFFFFFFFF : Theme.TEXT_DIM, false);
            }
            UiFont.SMALL.draw(ctx, "Edited " + timeAgo(ConfigManager.lastModified(name)), nx, y + 24, Theme.TEXT_MUTED, false);

            float bh = 18;
            float by = y + (rh - bh) / 2;
            boolean confirming = name.equals(this.pendingDelete);
            String delLabel = confirming ? "Sure?" : "Delete";
            float delW = UiFont.SMALL.width(delLabel) + 16;
            float saveW = UiFont.SMALL.width("Save") + 16;
            float loadW = UiFont.SMALL.width("Load") + 18;
            float bx = x + w - PAD - delW;
            button(ctx, mouseX, mouseY, delLabel, bx, by, delW, bh, false, confirming, true, m -> {
                if (name.equals(this.pendingDelete)) {
                    ConfigManager.deleteConfig(name);
                    this.pendingDelete = null;
                    ExhiSoundManager.playClick(0.7F);
                } else {
                    this.pendingDelete = name;
                    this.pendingDeleteTime = System.currentTimeMillis();
                    ExhiSoundManager.playClick(0.9F);
                }
            });
            bx -= saveW + 5;
            button(ctx, mouseX, mouseY, "Save", bx, by, saveW, bh, false, false, true, m -> {
                ConfigManager.saveConfig(name);
                ExhiSoundManager.playClick(1.1F);
            });
            bx -= loadW + 5;
            button(ctx, mouseX, mouseY, "Load", bx, by, loadW, bh, true, false, true, m -> {
                ConfigManager.loadConfig(name);
                ExhiSoundManager.playClick(1.2F);
            });
            y += rh + 6;
        }
        return y - 6 - start;
    }

    private void createConfig() {
        String name = this.configName.trim();
        if (name.isEmpty()) {
            this.configFocused = true;
            return;
        }
        ConfigManager.saveConfig(name);
        this.configName = "";
        this.configFocused = false;
        ExhiSoundManager.playClick(1.2F);
    }

    private static String timeAgo(long time) {
        if (time <= 0) return "never";
        long s = (System.currentTimeMillis() - time) / 1000;
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + "m ago";
        if (s < 86400) return (s / 3600) + "h ago";
        return (s / 86400) + "d ago";
    }

    // ------------------------------------------------------------------ data helpers

    private boolean searchActive() {
        return !this.search.isEmpty();
    }

    private List<Module> visibleModules() {
        List<Module> out = new ArrayList<>();
        String q = this.search.toLowerCase();
        for (Module m : Night.INSTANCE.getModuleManager().getModules()) {
            if (searchActive()) {
                if (m.getName().toLowerCase().contains(q)) out.add(m);
            } else if (m.getCategory() == selectedCategory) {
                out.add(m);
            }
        }
        out.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private int pageIndex() {
        List<NavItem> items = navItems();
        for (int i = 0; i < items.size(); i++) if (isSelected(items.get(i))) return i;
        return 0;
    }

    private void openNav(NavItem item) {
        if (item.hudEditor) {
            ExhiSoundManager.playClick(1.2F);
            ConfigManager.autoSave();
            if (this.client != null) this.client.setScreen(new HudEditorScreen(true));
            return;
        }
        int before = pageIndex();
        boolean changed = page != item.page || (item.page == Page.MODULES && (selectedCategory != item.category || searchActive()));
        if (!changed) return;
        page = item.page;
        if (item.category != null) selectedCategory = item.category;
        this.search = "";
        this.searchFocused = false;
        this.configFocused = false;
        this.scrollTarget = 0;
        this.scrollAnim.set(0);
        this.switchDir = pageIndex() >= before ? 1 : -1;
        this.switchAnim.set(0);
        ExhiSoundManager.playClick(1.2F);
    }

    private void startBinding(Module mod) {
        this.binding = mod;
        this.searchFocused = false;
        this.configFocused = false;
        ExhiSoundManager.playClick(0.9F);
    }

    private static String formatNumber(NumberProperty np) {
        double v = np.getValue();
        if (np.getIncrement() >= 1.0 && v == Math.rint(v)) return String.valueOf((long) v);
        int decimals = np.getIncrement() >= 0.1 ? 1 : 2;
        return String.format("%." + decimals + "f", v);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.closing || this.openAnim.get() < 0.6F) return true;
        double mx = localX(click.x());
        double my = localY(click.y());
        int button = click.button();

        if (this.binding != null) {
            this.binding = null; // clicking anywhere cancels binding
            return true;
        }

        boolean inList = inside(mx, my, listX, listY, listW, listH);
        for (int i = this.hits.size() - 1; i >= 0; i--) {
            Hit hit = this.hits.get(i);
            if (hit.inList && !inList) continue;
            if (!hit.contains(mx, my)) continue;
            Action action = button == 0 ? hit.left : button == 1 ? hit.right : button == 2 ? hit.middle : null;
            if (action == null) continue;
            // text boxes re-focus themselves in their own action
            this.searchFocused = false;
            this.configFocused = false;
            action.run(mx);
            return true;
        }
        this.searchFocused = false;
        this.configFocused = false;

        // drag the window by its top bar
        if (button == 0 && inside(mx, my, gx, gy, W, TOP_BAR)) {
            ClickGui s = settings();
            this.draggingWindow = true;
            this.windowGrabX = click.x();
            this.windowGrabY = click.y();
            this.windowStartX = s.windowX.getValue();
            this.windowStartY = s.windowY.getValue();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.dragNumber == settings().scale) this.appliedUserScale = -1; // apply the new scale now
        this.dragNumber = null;
        this.dragColor = null;
        if (this.draggingWindow) {
            // store the clamped position so the window can't be lost off screen
            ClickGui s = settings();
            s.windowX.setValue((double) (this.gx + W / 2 - this.width / 2.0F));
            s.windowY.setValue((double) (this.gy + H / 2 - this.height / 2.0F));
        }
        this.draggingWindow = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (this.draggingWindow) {
            ClickGui s = settings();
            s.windowX.setValue(this.windowStartX + (click.x() - this.windowGrabX));
            s.windowY.setValue(this.windowStartY + (click.y() - this.windowGrabY));
            return true;
        }
        if (this.dragNumber != null || this.dragColor != null) {
            updateDrag(localX(click.x()));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    private void updateDrag(double mouseX) {
        if (this.dragW <= 0) return;
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - this.dragX) / this.dragW));
        if (this.dragNumber != null) {
            NumberProperty np = this.dragNumber;
            np.setValue(np.getMin() + ratio * (np.getMax() - np.getMin()));
        } else if (this.dragColor != null) {
            Color c = this.dragColor.getValue();
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            float sat = hsb[1] < 0.1F ? 0.7F : hsb[1];
            float bri = hsb[2] < 0.2F ? 0.9F : hsb[2];
            this.dragColor.setValue(new Color(Color.HSBtoRGB((float) Math.min(0.999, ratio), sat, bri)));
        }
    }

    private boolean ctrlDown() {
        if (this.client == null) return false;
        long handle = this.client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ctrlDown()) {
            // Ctrl + scroll resizes the menu
            NumberProperty sp = settings().scale;
            sp.setValue(sp.getValue() + Math.signum(verticalAmount) * 0.05);
            this.appliedUserScale = -1;
            return true;
        }
        double lx = localX(mouseX);
        double ly = localY(mouseY);
        if (inside(lx, ly, gx + SIDEBAR, gy, W - SIDEBAR, H)) {
            this.scrollTarget += (float) (verticalAmount * 28);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        this.swallowChar = false;

        if (this.binding != null) {
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                this.binding.setKey(0);
            } else if (keyCode != GLFW.GLFW_KEY_ESCAPE) { // Esc cancels without changing the bind
                this.binding.setKey(keyCode);
            }
            this.binding = null;
            this.swallowChar = true; // the character from this key must not be typed into a text box
            ExhiSoundManager.playClick(1.2F);
            return true;
        }

        if (this.configFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.configName.isEmpty()) {
                this.configName = this.configName.substring(0, this.configName.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                createConfig();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.configFocused = false;
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.searchFocused || searchActive()) {
                this.search = "";
                this.searchFocused = false;
                return true;
            }
            this.close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.search.isEmpty() && page == Page.MODULES) {
            boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
            this.search = ctrl ? "" : this.search.substring(0, this.search.length() - 1);
            this.scrollTarget = 0;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT && !this.searchFocused && !this.configFocused) {
            this.close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (this.swallowChar) {
            this.swallowChar = false;
            return true;
        }
        if (this.binding != null) return true;
        if (!input.isValidChar()) return false;
        String s = input.asString();

        if (page == Page.CONFIGS) {
            // config names: letters, digits, dash and underscore
            if (s.matches("[A-Za-z0-9_-]") && this.configName.length() < 24) {
                this.configName += s;
                this.configFocused = true;
            }
            return true;
        }
        if (page != Page.MODULES) return false;

        if (s.isBlank() && this.search.isEmpty()) return false;
        if (this.search.length() < 32) {
            this.search += s;
            this.searchFocused = true;
            this.scrollTarget = 0;
        }
        return true;
    }

    // ------------------------------------------------------------------ closing

    @Override
    public void close() {
        // play the close animation first; finishClose() runs when it's done
        this.closing = true;
        this.binding = null;
    }

    private void finishClose() {
        if (this.client != null) this.client.setScreen(null);
        ConfigManager.autoSave();
    }

    public boolean isClosing() {
        return this.closing;
    }

    @Override
    public void removed() {
        this.closing = true;
        super.removed();
        Module gui = Night.INSTANCE.getModuleManager().getModule(ClickGui.class);
        if (gui != null && gui.isEnabled()) gui.setEnabled(false);
    }

    private String getKeyName(int key) {
        if (key <= 0) return "None";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase();
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            default -> key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25 ? "F" + (key - GLFW.GLFW_KEY_F1 + 1) : "K" + key;
        };
    }
}
