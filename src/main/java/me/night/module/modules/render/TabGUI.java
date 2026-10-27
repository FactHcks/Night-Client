package me.night.module.modules.render;

import me.night.Night;
import me.night.event.EventTarget;
import me.night.event.events.KeyEvent;
import me.night.event.events.Render2DEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.Property;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ColorProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.util.ExhiRenderUtil;
import me.night.util.ExhiSoundManager;
import me.night.util.NightRenderUtil;
import me.night.ui.hud.HudPos;
import me.night.util.Anim;
import me.night.util.ColorUtil;
import me.night.util.NightFont;
import me.night.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.List;

public class TabGUI extends Module {
    private final Category[] categories = Category.values();
    private int currentCategory = 0;
    private int currentModule = 0;
    private int currentSetting = 0;

    private boolean inModules = false;
    private boolean inSettings = false;
    private boolean editingSetting = false;

    private long lastNavTime = 0L;

    public TabGUI() {
        super("TabGUI", "Arrow-key module menu", Category.RENDER);
        addProperties(pos.px, pos.py);
        setHidden(true);
        setEnabled(true);
    }

    private int getAccentColor() {
        Module hudMod = Night.INSTANCE.getModuleManager().getModule(HUD.class);
        if (hudMod instanceof HUD) {
            return ((HUD) hudMod).getCurrentAccentColor();
        }
        return 0xFFFF2A2A;
    }

    private List<Module> getCurrentCategoryModules() {
        return Night.INSTANCE.getModuleManager().getModulesByCategory(categories[currentCategory]);
    }

    private Module getSelectedModule() {
        List<Module> mods = getCurrentCategoryModules();
        if (mods.isEmpty() || currentModule < 0 || currentModule >= mods.size()) return null;
        return mods.get(currentModule);
    }

    private Property<?> getSelectedProperty() {
        Module mod = getSelectedModule();
        if (mod == null || visibleProps(mod).isEmpty() || currentSetting < 0 || currentSetting >= visibleProps(mod).size()) return null;
        return visibleProps(mod).get(currentSetting);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (!event.isPress() || mc.currentScreen != null) return;
        int key = event.getKey();

        if (key != GLFW.GLFW_KEY_UP && key != GLFW.GLFW_KEY_DOWN &&
            key != GLFW.GLFW_KEY_LEFT && key != GLFW.GLFW_KEY_RIGHT &&
            key != GLFW.GLFW_KEY_ENTER) {
            return;
        }

        lastNavTime = System.currentTimeMillis();

        if (!inModules) {
            // Category navigation
            if (key == GLFW.GLFW_KEY_DOWN) {
                currentCategory++;
                if (currentCategory >= categories.length) currentCategory = 0;
                ExhiSoundManager.playClick(1.0F);
            } else if (key == GLFW.GLFW_KEY_UP) {
                currentCategory--;
                if (currentCategory < 0) currentCategory = categories.length - 1;
                ExhiSoundManager.playClick(1.0F);
            } else if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_ENTER) {
                List<Module> mods = getCurrentCategoryModules();
                if (!mods.isEmpty()) {
                    inModules = true;
                    currentModule = 0;
                    ExhiSoundManager.playClick(1.1F);
                }
            }
        } else if (!inSettings) {
            // Module navigation
            List<Module> mods = getCurrentCategoryModules();
            if (key == GLFW.GLFW_KEY_LEFT) {
                inModules = false;
                ExhiSoundManager.playClick(0.9F);
            } else if (key == GLFW.GLFW_KEY_DOWN) {
                if (!mods.isEmpty()) {
                    currentModule++;
                    if (currentModule >= mods.size()) currentModule = 0;
                    ExhiSoundManager.playClick(1.0F);
                }
            } else if (key == GLFW.GLFW_KEY_UP) {
                if (!mods.isEmpty()) {
                    currentModule--;
                    if (currentModule < 0) currentModule = mods.size() - 1;
                    ExhiSoundManager.playClick(1.0F);
                }
            } else if (key == GLFW.GLFW_KEY_ENTER) {
                Module mod = getSelectedModule();
                if (mod != null) {
                    mod.toggle();
                }
            } else if (key == GLFW.GLFW_KEY_RIGHT) {
                Module mod = getSelectedModule();
                if (mod != null && !visibleProps(mod).isEmpty()) {
                    inSettings = true;
                    currentSetting = 0;
                    editingSetting = false;
                    ExhiSoundManager.playClick(1.1F);
                }
            }
        } else if (!editingSetting) {
            // Setting navigation
            Module mod = getSelectedModule();
            if (mod == null || visibleProps(mod).isEmpty()) {
                inSettings = false;
                return;
            }
            List<Property<?>> props = visibleProps(mod);

            if (key == GLFW.GLFW_KEY_LEFT) {
                inSettings = false;
                ExhiSoundManager.playClick(0.9F);
            } else if (key == GLFW.GLFW_KEY_DOWN) {
                currentSetting++;
                if (currentSetting >= props.size()) currentSetting = 0;
                ExhiSoundManager.playClick(1.0F);
            } else if (key == GLFW.GLFW_KEY_UP) {
                currentSetting--;
                if (currentSetting < 0) currentSetting = props.size() - 1;
                ExhiSoundManager.playClick(1.0F);
            } else if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_ENTER) {
                Property<?> prop = getSelectedProperty();
                if (prop instanceof BooleanProperty) {
                    BooleanProperty bp = (BooleanProperty) prop;
                    bp.setValue(!bp.getValue());
                    ExhiSoundManager.playClick(1.2F);
                } else if (prop instanceof ModeProperty || prop instanceof NumberProperty) {
                    editingSetting = true;
                    ExhiSoundManager.playClick(1.2F);
                }
            }
        } else {
            // Adjusting setting value
            Property<?> prop = getSelectedProperty();
            if (prop == null) {
                editingSetting = false;
                return;
            }

            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_ENTER) {
                editingSetting = false;
                ExhiSoundManager.playClick(0.9F);
            } else if (key == GLFW.GLFW_KEY_UP) {
                if (prop instanceof ModeProperty) {
                    ((ModeProperty) prop).cycle(false);
                } else if (prop instanceof NumberProperty) {
                    NumberProperty np = (NumberProperty) prop;
                    np.setValue(np.getValue() + np.getIncrement());
                } else if (prop instanceof BooleanProperty) {
                    BooleanProperty bp = (BooleanProperty) prop;
                    bp.setValue(!bp.getValue());
                }
                ExhiSoundManager.playClick(1.15F);
            } else if (key == GLFW.GLFW_KEY_DOWN) {
                if (prop instanceof ModeProperty) {
                    ((ModeProperty) prop).cycle(true);
                } else if (prop instanceof NumberProperty) {
                    NumberProperty np = (NumberProperty) prop;
                    np.setValue(np.getValue() - np.getIncrement());
                } else if (prop instanceof BooleanProperty) {
                    BooleanProperty bp = (BooleanProperty) prop;
                    bp.setValue(!bp.getValue());
                }
                ExhiSoundManager.playClick(1.15F);
            }
        }
    }

    public final HudPos pos = new HudPos("TabGUI", 0, 0.22);

    private final Anim catSel = new Anim(0.0F);
    private final Anim modSel = new Anim(0.0F);
    private final Anim setSel = new Anim(0.0F);
    private final Anim modOpen = new Anim(0.0F);
    private final Anim setOpen = new Anim(0.0F);

    private static final float ROW = 14;
    private static final float GAP = 4;

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        DrawContext context = event.getContext();

        int accent = getAccentColor();
        // categories
        float catW = 0;
        for (Category c : categories) catW = Math.max(catW, NightFont.width(NightFont.Type.REGULAR, c.getName()));
        catW += 22;
        float catH = categories.length * ROW + 4;
        pos.place(catW, catH);
        float startX = pos.x;
        float startY = pos.y;
        boolean openLeft = pos.onRightSide();
        drawPanel(context, startX, startY, catW, catH);
        float cs = catSel.update(currentCategory, 18.0F);
        drawHighlight(context, startX + 2, startY + 2 + cs * ROW, catW - 4, ROW, accent, !inModules);
        for (int i = 0; i < categories.length; i++) {
            boolean sel = i == currentCategory;
            float ry = startY + 2 + i * ROW;
            NightFont.draw(context, NightFont.Type.REGULAR, categories[i].getName(), startX + 8 + (sel ? 2 : 0),
                    NightFont.centerY(NightFont.Type.REGULAR, ry, ROW), sel ? Theme.TEXT : Theme.TEXT_DIM, false);
        }

        // modules
        float mo = modOpen.update(inModules ? 1.0F : 0.0F, 16.0F);
        if (mo < 0.01F) return;
        List<Module> mods = getCurrentCategoryModules();
        if (mods.isEmpty()) return;
        float modW = 0;
        for (Module m : mods) modW = Math.max(modW, NightFont.width(NightFont.Type.REGULAR, m.getName()));
        modW += 26;
        float modX = openLeft ? startX - GAP - modW : startX + catW + GAP;
        float modH = mods.size() * ROW + 4;

        NightRenderUtil.setGlobalAlpha(mo);
        float slide = (1.0F - Anim.easeOutCubic(mo)) * (openLeft ? 6 : -6);
        drawPanel(context, modX + slide, startY, modW, modH);
        float ms = modSel.update(currentModule, 18.0F);
        drawHighlight(context, modX + slide + 2, startY + 2 + ms * ROW, modW - 4, ROW, accent, !inSettings);
        for (int i = 0; i < mods.size(); i++) {
            Module mod = mods.get(i);
            float ry = startY + 2 + i * ROW;
            int col = mod.isEnabled() ? ColorUtil.brighter(accent, 0.3F) : (i == currentModule ? Theme.TEXT : Theme.TEXT_DIM);
            NightFont.draw(context, NightFont.Type.REGULAR, mod.getName(), modX + slide + 8 + (i == currentModule ? 2 : 0),
                    NightFont.centerY(NightFont.Type.REGULAR, ry, ROW), col, false);
            if (mod.isEnabled()) {
                NightRenderUtil.circle(context, modX + slide + modW - 8, ry + ROW / 2, 1.75F, accent);
            }
        }
        NightRenderUtil.resetGlobalAlpha();

        // settings
        float so = setOpen.update(inSettings ? 1.0F : 0.0F, 16.0F);
        Module mod = getSelectedModule();
        if (so < 0.01F || mod == null || visibleProps(mod).isEmpty()) return;
        List<Property<?>> props = visibleProps(mod);
        float setW = 0;
        for (Property<?> p : props) {
            setW = Math.max(setW, NightFont.width(NightFont.Type.REGULAR, p.getName())
                    + NightFont.width(NightFont.Type.REGULAR, getPropertyDisplay(p)) + 14);
        }
        setW += 18;
        float setX = openLeft ? modX - GAP - setW : modX + modW + GAP;
        float setH = props.size() * ROW + 4;

        NightRenderUtil.setGlobalAlpha(so);
        float sslide = (1.0F - Anim.easeOutCubic(so)) * (openLeft ? 6 : -6);
        drawPanel(context, setX + sslide, startY, setW, setH);
        float ss = setSel.update(currentSetting, 18.0F);
        drawHighlight(context, setX + sslide + 2, startY + 2 + ss * ROW, setW - 4, ROW, accent, true);
        for (int i = 0; i < props.size(); i++) {
            Property<?> prop = props.get(i);
            boolean sel = i == currentSetting;
            float ry = startY + 2 + i * ROW;
            float ty = NightFont.centerY(NightFont.Type.REGULAR, ry, ROW);
            NightFont.draw(context, NightFont.Type.REGULAR, prop.getName(), setX + sslide + 8, ty, sel ? Theme.TEXT : Theme.TEXT_DIM, false);
            String val = getPropertyDisplay(prop);
            int valCol = sel && editingSetting ? ColorUtil.brighter(accent, 0.3F) : Theme.TEXT;
            NightFont.draw(context, NightFont.Type.REGULAR, val,
                    setX + sslide + setW - 8 - NightFont.width(NightFont.Type.REGULAR, val), ty, valCol, false);
        }
        NightRenderUtil.resetGlobalAlpha();
    }

    private void drawPanel(DrawContext ctx, float x, float y, float w, float h) {
        NightRenderUtil.shadow(ctx, x, y, w, h, 6, 8, 0x50000000);
        Module hudMod = Night.INSTANCE.getModuleManager().getModule(HUD.class);
        boolean blur = hudMod instanceof HUD hud && hud.blur.getValue();
        me.night.render.Render2D.glass(ctx, x, y, w, h, 6, 0x980E0E13, blur);
        NightRenderUtil.roundedOutline(ctx, x, y, w, h, 6, 0.5F, Theme.STROKE);
    }

    private void drawHighlight(DrawContext ctx, float x, float y, float w, float h, int accent, boolean focused) {
        int a = focused ? 0x40 : 0x1C;
        NightRenderUtil.roundedGradient(ctx, x, y, w, h, 4, ColorUtil.withAlpha(accent, a), ColorUtil.withAlpha(accent, a / 3));
        NightRenderUtil.roundedRect(ctx, x, y + 3, 1.5F, h - 6, 0.75F, focused ? accent : ColorUtil.withAlpha(accent, 0x80));
    }

    private static List<Property<?>> visibleProps(Module mod) {
        List<Property<?>> out = new java.util.ArrayList<>();
        for (Property<?> p : mod.getProperties()) if (p.isVisible()) out.add(p);
        return out;
    }

    private String getPropertyDisplay(Property<?> p) {
        if (p instanceof ModeProperty) {
            return ((ModeProperty) p).getValue();
        } else if (p instanceof NumberProperty) {
            double v = ((NumberProperty) p).getValue();
            if (v == (long) v) return String.format("%d", (long) v);
            return String.format("%.1f", v);
        } else if (p instanceof BooleanProperty) {
            return ((BooleanProperty) p).getValue() ? "On" : "Off";
        } else if (p instanceof ColorProperty) {
            return String.format("#%06X", ((ColorProperty) p).getRGB() & 0xFFFFFF);
        }
        return p.getValue().toString();
    }
}
