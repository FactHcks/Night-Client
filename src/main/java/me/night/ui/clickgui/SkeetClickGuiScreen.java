package me.night.ui.clickgui;

import me.night.Night;
import me.night.module.Category;
import me.night.module.Module;
import me.night.module.modules.render.ClickGui;
import me.night.property.Property;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ColorProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.util.ExhiRenderUtil;
import me.night.util.ExhiSoundManager;
import me.night.util.NightRenderUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class SkeetClickGuiScreen extends Screen {
    private int guiX = 100;
    private int guiY = 80;
    private final int guiWidth = 440;
    private final int guiHeight = 310;

    private boolean isDragging = false;
    private double dragX = 0;
    private double dragY = 0;

    private Category selectedCategory = Category.COMBAT;
    private Module selectedModule = null;
    private Module listeningKeybindModule = null;

    private NumberProperty activeSlider = null;
    private int activeSliderX = 0;
    private int activeSliderW = 0;

    private int moduleScroll = 0;
    private int settingsScroll = 0;
    private long openTime = 0L;

    public SkeetClickGuiScreen() {
        super(Text.literal("Night Client"));
    }

    @Override
    protected void init() {
        super.init();
        this.openTime = System.currentTimeMillis();
        if (this.client != null) {
            int sw = this.client.getWindow().getScaledWidth();
            int sh = this.client.getWindow().getScaledHeight();
            this.guiX = Math.max(10, (sw - this.guiWidth) / 2);
            this.guiY = Math.max(10, (sh - this.guiHeight) / 2);
        }
        if (this.selectedModule == null) {
            List<Module> mods = Night.INSTANCE.getModuleManager().getModulesByCategory(this.selectedCategory);
            if (!mods.isEmpty()) {
                this.selectedModule = mods.get(0);
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Safe background dimming (Screen already manages background blur)
        context.fill(0, 0, this.width, this.height, 0x65000000);

        // Ambient outer drop-glow bloom around the GUI
        NightRenderUtil.drawGlow(context, this.guiX, this.guiY, this.guiWidth, this.guiHeight, 8, 0x459D4EDD);

        // 1. Skeet Main Frame
        ExhiRenderUtil.drawSkeetBox(context, this.guiX, this.guiY, this.guiWidth, this.guiHeight);

        // 2. Title bar
        context.drawTextWithShadow(this.textRenderer, "§lNIGHT §fCLIENT", this.guiX + 8, this.guiY + 6, 0xFF9D4EDD);
        context.drawTextWithShadow(this.textRenderer, "§7[v1.0.0]", this.guiX + 88, this.guiY + 6, 0xFFAAAAAA);

        // 3. Top Neon Gradient Stripe (Electric Violet to Cyan)
        NightRenderUtil.drawHorizontalGradient(context, this.guiX + 3, this.guiY + 18, this.guiWidth - 6, 2, 0xFF9D4EDD, 0xFF00E5FF);

        // 4. Category Tabs
        Category[] cats = Category.values();
        int tabW = (this.guiWidth - 10) / cats.length;
        int tabY = this.guiY + 23;
        int tabH = 18;

        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            int tx = this.guiX + 5 + i * tabW;
            boolean isSel = (cat == this.selectedCategory);
            boolean hovered = mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            int bgCol = isSel ? 0xFF222222 : (hovered ? 0xFF191919 : 0xFF121212);
            int textCol = isSel ? 0xFFFFFFFF : (hovered ? 0xFFD0D0D0 : 0xFF707070);

            context.fill(tx, tabY, tx + tabW - 2, tabY + tabH, bgCol);
            ExhiRenderUtil.drawOutline(context, tx, tabY, tabW - 2, tabH, isSel ? 0xFF3C3C3C : 0xFF1C1C1C);

            if (isSel) {
                // Modern accent top line
                context.fill(tx, tabY, tx + tabW - 2, tabY + 2, 0xFF9D4EDD);
            }

            int tw = this.textRenderer.getWidth(cat.getName());
            context.drawTextWithShadow(this.textRenderer, cat.getName(), tx + (tabW - 2 - tw) / 2, tabY + 5, textCol);
        }

        // 5. Dual-Column GroupBoxes
        int boxY = tabY + 23;
        int boxH = this.guiHeight - (boxY - this.guiY) - 6;
        int leftW = (int) ((this.guiWidth - 16) * 0.46F);
        int rightW = this.guiWidth - 16 - leftW;

        int leftX = this.guiX + 5;
        int rightX = leftX + leftW + 6;

        // Left Box: Category Modules
        ExhiRenderUtil.drawGroupBox(context, leftX, boxY, leftW, boxH, this.selectedCategory.getName() + " Modules");
        renderModulesList(context, leftX, boxY, leftW, boxH, mouseX, mouseY);

        // Right Box: Module Properties
        String rightTitle = (this.selectedModule != null) ? this.selectedModule.getName() + " Properties" : "Properties";
        ExhiRenderUtil.drawGroupBox(context, rightX, boxY, rightW, boxH, rightTitle);
        renderSettingsList(context, rightX, boxY, rightW, boxH, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderModulesList(DrawContext context, int boxX, int boxY, int boxW, int boxH, int mouseX, int mouseY) {
        int clipX1 = boxX + 2;
        int clipY1 = boxY + 6;
        int clipX2 = boxX + boxW - 2;
        int clipY2 = boxY + boxH - 3;

        context.enableScissor(clipX1, clipY1, clipX2, clipY2);

        List<Module> mods = Night.INSTANCE.getModuleManager().getModulesByCategory(this.selectedCategory);
        int currentY = boxY + 10 + this.moduleScroll;

        for (Module mod : mods) {
            // Cull items completely off-screen
            if (currentY + 16 >= clipY1 && currentY <= clipY2) {
                boolean isSelected = (mod == this.selectedModule);
                boolean isHovered = mouseX >= boxX + 4 && mouseX <= boxX + boxW - 4 && mouseY >= currentY && mouseY <= currentY + 16;

                // Row background highlight
                if (isSelected) {
                    context.fill(boxX + 4, currentY, boxX + boxW - 4, currentY + 16, 0xFF1E1E1E);
                    ExhiRenderUtil.drawOutline(context, boxX + 4, currentY, boxW - 8, 16, 0xFF353535);
                } else if (isHovered) {
                    context.fill(boxX + 4, currentY, boxX + boxW - 4, currentY + 16, 0xFF181818);
                }

                // Checkbox
                int cbX = boxX + 7;
                int cbY = currentY + 3;
                context.fill(cbX, cbY, cbX + 10, cbY + 10, 0xFF0A0A0A);
                ExhiRenderUtil.drawOutline(context, cbX, cbY, 10, 10, 0xFF282828);
                if (mod.isEnabled()) {
                    context.fill(cbX + 2, cbY + 2, cbX + 8, cbY + 8, 0xFF9D4EDD);
                }

                // Module name
                int nameCol = mod.isEnabled() ? 0xFFFFFFFF : 0xFF858585;
                context.drawTextWithShadow(this.textRenderer, mod.getName(), cbX + 14, currentY + 4, nameCol);

                // Keybind badge on the right
                String keyStr = (this.listeningKeybindModule == mod) ? "[...]" : "[" + getKeyName(mod.getKey()) + "]";
                int keyW = this.textRenderer.getWidth(keyStr);
                int keyX = boxX + boxW - keyW - 8;
                int keyCol = (this.listeningKeybindModule == mod) ? 0xFFFF3333 : 0xFF555555;
                context.drawTextWithShadow(this.textRenderer, keyStr, keyX, currentY + 4, keyCol);
            }
            currentY += 18;
        }

        context.disableScissor();
    }

    private void renderSettingsList(DrawContext context, int boxX, int boxY, int boxW, int boxH, int mouseX, int mouseY) {
        if (this.selectedModule == null) {
            context.drawTextWithShadow(this.textRenderer, "§7Select a module to view properties", boxX + 12, boxY + 20, 0xFF777777);
            return;
        }

        int clipX1 = boxX + 2;
        int clipY1 = boxY + 6;
        int clipX2 = boxX + boxW - 2;
        int clipY2 = boxY + boxH - 3;

        context.enableScissor(clipX1, clipY1, clipX2, clipY2);

        int currentY = boxY + 10 + this.settingsScroll;
        int contentX = boxX + 8;
        int contentW = boxW - 16;

        // 1. Module Keybind Setting Row
        if (currentY + 14 >= clipY1 && currentY <= clipY2) {
            String bindText = "Keybind: §f" + ((this.listeningKeybindModule == this.selectedModule) ? "Press any key..." : getKeyName(this.selectedModule.getKey()));
            context.drawTextWithShadow(this.textRenderer, bindText, contentX, currentY + 2, 0xFFCCCCCC);
        }
        currentY += 16;

        // 2. Properties
        for (Property<?> prop : this.selectedModule.getProperties()) {
            if (!prop.isVisible()) continue;

            if (prop instanceof BooleanProperty) {
                BooleanProperty bp = (BooleanProperty) prop;
                if (currentY + 14 >= clipY1 && currentY <= clipY2) {
                    int cbX = contentX;
                    int cbY = currentY + 2;
                    context.fill(cbX, cbY, cbX + 10, cbY + 10, 0xFF0A0A0A);
                    ExhiRenderUtil.drawOutline(context, cbX, cbY, 10, 10, 0xFF282828);
                    if (bp.getValue()) {
                        context.fill(cbX + 2, cbY + 2, cbX + 8, cbY + 8, 0xFF9D4EDD);
                    }
                    context.drawTextWithShadow(this.textRenderer, bp.getName(), cbX + 14, currentY + 3, 0xFFD0D0D0);
                }
                currentY += 16;
            } else if (prop instanceof NumberProperty) {
                NumberProperty np = (NumberProperty) prop;
                if (currentY + 20 >= clipY1 && currentY <= clipY2) {
                    double v = np.getValue();
                    String valStr = (v == (long) v) ? String.format("%d", (long) v) : String.format("%.2f", v);
                    String label = np.getName() + ": §d" + valStr;
                    context.drawTextWithShadow(this.textRenderer, label, contentX, currentY, 0xFFCCCCCC);

                    int trackX = contentX;
                    int trackY = currentY + 10;
                    int trackW = contentW;
                    int trackH = 5;

                    double ratio = (np.getValue() - np.getMin()) / (np.getMax() - np.getMin());
                    ratio = Math.max(0.0, Math.min(1.0, ratio));

                    context.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF0A0A0A);
                    ExhiRenderUtil.drawOutline(context, trackX, trackY, trackW, trackH, 0xFF252525);
                    context.fill(trackX + 1, trackY + 1, (int) (trackX + 1 + (trackW - 2) * ratio), trackY + trackH - 1, 0xFF9D4EDD);
                }
                currentY += 20;
            } else if (prop instanceof ModeProperty) {
                ModeProperty mp = (ModeProperty) prop;
                if (currentY + 17 >= clipY1 && currentY <= clipY2) {
                    context.drawTextWithShadow(this.textRenderer, mp.getName() + ":", contentX, currentY + 2, 0xFFCCCCCC);

                    String modeStr = "§f[ §d" + mp.getValue() + " §f]";
                    int mw = this.textRenderer.getWidth(modeStr);
                    int mx = contentX + contentW - mw;

                    context.fill(mx - 2, currentY, mx + mw + 2, currentY + 13, 0xFF101010);
                    ExhiRenderUtil.drawOutline(context, mx - 2, currentY, mw + 4, 13, 0xFF262626);
                    context.drawTextWithShadow(this.textRenderer, modeStr, mx, currentY + 3, 0xFFFFFFFF);
                }
                currentY += 17;
            } else if (prop instanceof ColorProperty) {
                ColorProperty cp = (ColorProperty) prop;
                if (currentY + 16 >= clipY1 && currentY <= clipY2) {
                    context.drawTextWithShadow(this.textRenderer, cp.getName(), contentX, currentY + 2, 0xFFCCCCCC);

                    int swatchX = contentX + contentW - 22;
                    context.fill(swatchX, currentY + 1, swatchX + 20, currentY + 11, cp.getRGB() | 0xFF000000);
                    ExhiRenderUtil.drawOutline(context, swatchX, currentY + 1, 20, 10, 0xFF353535);
                }
                currentY += 16;
            }
        }

        context.disableScissor();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int button = click.button();

        // 1. Dragging top title bar
        if (button == 0 && mouseX >= this.guiX && mouseX <= this.guiX + this.guiWidth && mouseY >= this.guiY && mouseY <= this.guiY + 20) {
            this.isDragging = true;
            this.dragX = mouseX - this.guiX;
            this.dragY = mouseY - this.guiY;
            return true;
        }

        // 2. Category Tab selection
        Category[] cats = Category.values();
        int tabW = (this.guiWidth - 10) / cats.length;
        int tabY = this.guiY + 23;
        int tabH = 18;
        if (mouseY >= tabY && mouseY <= tabY + tabH) {
            for (int i = 0; i < cats.length; i++) {
                int tx = this.guiX + 5 + i * tabW;
                if (mouseX >= tx && mouseX <= tx + tabW) {
                    this.selectedCategory = cats[i];
                    List<Module> mods = Night.INSTANCE.getModuleManager().getModulesByCategory(this.selectedCategory);
                    this.selectedModule = mods.isEmpty() ? null : mods.get(0);
                    this.moduleScroll = 0;
                    this.settingsScroll = 0;
                    ExhiSoundManager.playClick(1.2F);
                    return true;
                }
            }
        }

        // 3. Module List interactions
        int boxY = tabY + 23;
        int boxH = this.guiHeight - (boxY - this.guiY) - 6;
        int leftW = (int) ((this.guiWidth - 16) * 0.46F);
        int rightW = this.guiWidth - 16 - leftW;
        int leftX = this.guiX + 5;

        if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= boxY + 6 && mouseY <= boxY + boxH) {
            List<Module> mods = Night.INSTANCE.getModuleManager().getModulesByCategory(this.selectedCategory);
            int currentY = boxY + 10 + this.moduleScroll;
            for (Module mod : mods) {
                if (mouseY >= currentY && mouseY <= currentY + 16) {
                    int cbX = boxXPosition(leftX);
                    int keyW = this.textRenderer.getWidth("[" + getKeyName(mod.getKey()) + "]");
                    int keyX = leftX + leftW - keyW - 8;

                    if (button == 0) {
                        // Checkbox click
                        if (mouseX >= cbX && mouseX <= cbX + 14) {
                            mod.toggle();
                        } else if (mouseX >= keyX && mouseX <= keyX + keyW + 4) {
                            // Keybind badge click
                            this.listeningKeybindModule = (this.listeningKeybindModule == mod) ? null : mod;
                            ExhiSoundManager.playClick(0.9F);
                        } else {
                            // Row click selects module
                            this.selectedModule = mod;
                            this.settingsScroll = 0;
                            ExhiSoundManager.playClick(1.0F);
                        }
                    } else if (button == 1) {
                        // Right-click toggles keybind listener
                        this.listeningKeybindModule = (this.listeningKeybindModule == mod) ? null : mod;
                        ExhiSoundManager.playClick(0.9F);
                    }
                    return true;
                }
                currentY += 18;
            }
        }

        // 4. Settings Panel interactions
        int rightX = leftX + leftW + 6;
        if (this.selectedModule != null && mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= boxY + 6 && mouseY <= boxY + boxH) {
            int currentY = boxY + 10 + this.settingsScroll;
            int contentX = rightX + 8;
            int contentW = rightW - 16;

            // Keybind row click
            if (mouseY >= currentY && mouseY <= currentY + 14) {
                this.listeningKeybindModule = (this.listeningKeybindModule == this.selectedModule) ? null : this.selectedModule;
                ExhiSoundManager.playClick(0.9F);
                return true;
            }
            currentY += 16;

            for (Property<?> prop : this.selectedModule.getProperties()) {
                if (!prop.isVisible()) continue;

                if (prop instanceof BooleanProperty) {
                    if (mouseY >= currentY && mouseY <= currentY + 14) {
                        ((BooleanProperty) prop).toggle();
                        ExhiSoundManager.playClick(1.1F);
                        return true;
                    }
                    currentY += 16;
                } else if (prop instanceof NumberProperty) {
                    NumberProperty np = (NumberProperty) prop;
                    int trackX = contentX;
                    int trackY = currentY + 10;
                    int trackW = contentW;
                    int trackH = 6;
                    if (mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= trackY - 2 && mouseY <= trackY + trackH + 3) {
                        this.activeSlider = np;
                        this.activeSliderX = trackX;
                        this.activeSliderW = trackW;
                        updateSliderValue(mouseX);
                        ExhiSoundManager.playClick(1.0F);
                        return true;
                    }
                    currentY += 20;
                } else if (prop instanceof ModeProperty) {
                    if (mouseY >= currentY && mouseY <= currentY + 15) {
                        ((ModeProperty) prop).cycle(button == 0);
                        ExhiSoundManager.playClick(1.0F);
                        return true;
                    }
                    currentY += 17;
                } else if (prop instanceof ColorProperty) {
                    currentY += 16;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private int boxXPosition(int leftX) {
        return leftX + 7;
    }

    private void updateSliderValue(double mouseX) {
        if (this.activeSlider != null && this.activeSliderW > 0) {
            double ratio = (mouseX - this.activeSliderX) / (double) this.activeSliderW;
            ratio = Math.max(0.0, Math.min(1.0, ratio));
            double val = this.activeSlider.getMin() + ratio * (this.activeSlider.getMax() - this.activeSlider.getMin());
            this.activeSlider.setValue(val);
        }
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (this.isDragging) {
            this.guiX = (int) (click.x() - this.dragX);
            this.guiY = (int) (click.y() - this.dragY);
            return true;
        }
        if (this.activeSlider != null) {
            updateSliderValue(click.x());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        this.isDragging = false;
        this.activeSlider = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int tabY = this.guiY + 23;
        int boxY = tabY + 23;
        int boxH = this.guiHeight - (boxY - this.guiY) - 6;
        int leftW = (int) ((this.guiWidth - 16) * 0.46F);
        int rightW = this.guiWidth - 16 - leftW;
        int leftX = this.guiX + 5;
        int rightX = leftX + leftW + 6;

        if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= boxY && mouseY <= boxY + boxH) {
            this.moduleScroll += (int) (verticalAmount * 18);
            if (this.moduleScroll > 0) this.moduleScroll = 0;
            return true;
        } else if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= boxY && mouseY <= boxY + boxH) {
            this.settingsScroll += (int) (verticalAmount * 18);
            if (this.settingsScroll > 0) this.settingsScroll = 0;
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int key = keyInput.key();
        if (this.listeningKeybindModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE) {
                this.listeningKeybindModule.setKey(0);
            } else {
                this.listeningKeybindModule.setKey(key);
            }
            this.listeningKeybindModule = null;
            ExhiSoundManager.playClick(1.2F);
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }

        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT && System.currentTimeMillis() - this.openTime > 300) {
            this.close();
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public void close() {
        super.close();
        Module clickGui = Night.INSTANCE.getModuleManager().getModule(ClickGui.class);
        if (clickGui != null && clickGui.isEnabled()) {
            clickGui.setEnabled(false);
        }
    }

    public static String getKeyName(int key) {
        if (key <= 0) return "None";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty()) {
            return name.toUpperCase();
        }
        switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RShift";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LShift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCtrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "RAlt";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LAlt";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            default: return "Key " + key;
        }
    }
}
