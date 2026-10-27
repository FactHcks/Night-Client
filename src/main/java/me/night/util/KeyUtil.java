package me.night.util;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class KeyUtil {
    private static final Map<String, Integer> NAME_TO_KEY = new HashMap<>();
    private static final Map<Integer, String> KEY_TO_NAME = new HashMap<>();

    static {
        registerKey("NONE", 0);
        registerKey("RSHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT);
        registerKey("LSHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);
        registerKey("RCTRL", GLFW.GLFW_KEY_RIGHT_CONTROL);
        registerKey("LCTRL", GLFW.GLFW_KEY_LEFT_CONTROL);
        registerKey("RALT", GLFW.GLFW_KEY_RIGHT_ALT);
        registerKey("LALT", GLFW.GLFW_KEY_LEFT_ALT);
        registerKey("SPACE", GLFW.GLFW_KEY_SPACE);
        registerKey("TAB", GLFW.GLFW_KEY_TAB);
        registerKey("ENTER", GLFW.GLFW_KEY_ENTER);
        registerKey("ESCAPE", GLFW.GLFW_KEY_ESCAPE);
        registerKey("ESC", GLFW.GLFW_KEY_ESCAPE);
        registerKey("BACKSPACE", GLFW.GLFW_KEY_BACKSPACE);
        registerKey("DELETE", GLFW.GLFW_KEY_DELETE);
        registerKey("DEL", GLFW.GLFW_KEY_DELETE);
        registerKey("INSERT", GLFW.GLFW_KEY_INSERT);
        registerKey("INS", GLFW.GLFW_KEY_INSERT);
        registerKey("UP", GLFW.GLFW_KEY_UP);
        registerKey("DOWN", GLFW.GLFW_KEY_DOWN);
        registerKey("LEFT", GLFW.GLFW_KEY_LEFT);
        registerKey("RIGHT", GLFW.GLFW_KEY_RIGHT);
        registerKey("CAPSLOCK", GLFW.GLFW_KEY_CAPS_LOCK);
        registerKey("CAPS", GLFW.GLFW_KEY_CAPS_LOCK);

        for (int i = 1; i <= 12; i++) {
            registerKey("F" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        }

        for (char c = 'A'; c <= 'Z'; c++) {
            registerKey(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'A'));
        }

        for (char c = '0'; c <= '9'; c++) {
            registerKey(String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        }
    }

    private static void registerKey(String name, int code) {
        NAME_TO_KEY.put(name.toUpperCase(Locale.ROOT), code);
        if (!KEY_TO_NAME.containsKey(code)) {
            KEY_TO_NAME.put(code, name);
        }
    }

    public static int getKeyFromName(String name) {
        if (name == null || name.trim().isEmpty()) return 0;
        String clean = name.trim().toUpperCase(Locale.ROOT);
        if (clean.equals("NONE") || clean.equals("0") || clean.equals("CLEAR")) return 0;

        Integer code = NAME_TO_KEY.get(clean);
        if (code != null) return code;

        if (clean.length() == 1) {
            char c = clean.charAt(0);
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }

        return 0;
    }

    public static String getKeyName(int keyCode) {
        if (keyCode <= 0) return "None";
        String name = KEY_TO_NAME.get(keyCode);
        if (name != null) return name;

        String glfwName = GLFW.glfwGetKeyName(keyCode, 0);
        if (glfwName != null && !glfwName.isEmpty()) {
            return glfwName.toUpperCase(Locale.ROOT);
        }

        return "KEY" + keyCode;
    }
}
