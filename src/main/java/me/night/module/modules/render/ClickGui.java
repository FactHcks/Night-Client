package me.night.module.modules.render;

import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ColorProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.ui.clickgui.ModernClickGuiScreen;
import me.night.ui.clickgui.SkeetClickGuiScreen;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class ClickGui extends Module {
    public final ModeProperty style = new ModeProperty("Style", "Rise 6", "Rise 6", "Skeet");
    public final ColorProperty accent = new ColorProperty("Accent", "Accent color", new Color(157, 78, 221));
    public final BooleanProperty glow = new BooleanProperty("Glow", "Ambient drop glow", true);
    public final NumberProperty scale = new NumberProperty("GUI Scale", "Size of the menu (Ctrl + scroll also works)", 1.0, 0.6, 1.5, 0.05);
    // window position, remembered between openings (hidden, saved with configs)
    public final NumberProperty windowX = new NumberProperty("Window X", "", 0, -4000, 4000, 1, () -> false);
    public final NumberProperty windowY = new NumberProperty("Window Y", "", 0, -4000, 4000, 1, () -> false);

    public ClickGui() {
        super("ClickGUI", "Rise style or Skeet style module menu", Category.RENDER, GLFW.GLFW_KEY_RIGHT_SHIFT);
        addProperties(style, accent, glow, scale, windowX, windowY);
        setHidden(true);
    }

    @Override
    protected void onEnable() {
        if (mc.currentScreen == null) {
            if (style.is("Rise 6")) {
                mc.setScreen(new ModernClickGuiScreen());
            } else {
                mc.setScreen(new SkeetClickGuiScreen());
            }
        }
    }

    @Override
    protected void onDisable() {
        if (mc.currentScreen instanceof ModernClickGuiScreen gui) {
            // plays the close animation; does nothing if the screen is already closing
            if (!gui.isClosing()) gui.close();
        } else if (mc.currentScreen instanceof SkeetClickGuiScreen) {
            mc.setScreen(null);
        }
    }
}
