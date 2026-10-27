package me.night.util;

import java.lang.reflect.Method;

/**
 * Asks ViaFabricPlus (if installed) which protocol the current server uses.
 * Uses reflection so the client works with or without ViaFabricPlus.
 */
public final class ServerVersion {
    private static final int PROTOCOL_1_8 = 47;
    private static long checkedAt;
    private static int cached = -1;

    private ServerVersion() {
    }

    /** True when connected through ViaFabricPlus to a 1.8.x (or older) server. */
    public static boolean isLegacy() {
        int p = protocol();
        return p > 0 && p <= PROTOCOL_1_8;
    }

    /** Protocol number of the target version, or -1 if ViaFabricPlus isn't there. */
    public static int protocol() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 1000) return cached;
        checkedAt = now;
        cached = read();
        return cached;
    }

    private static int read() {
        Object version = null;
        try { // ViaFabricPlus 4.x
            Class<?> api = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
            Object impl = api.getMethod("getImpl").invoke(null);
            version = impl.getClass().getMethod("getTargetVersion").invoke(impl);
        } catch (Throwable ignored) {
        }
        if (version == null) {
            try { // ViaFabricPlus 3.x
                Class<?> pt = Class.forName("de.florianmichael.viafabricplus.protocoltranslator.ProtocolTranslator");
                version = pt.getMethod("getTargetVersion").invoke(null);
            } catch (Throwable ignored) {
            }
        }
        if (version == null) return -1;
        try {
            Method getVersion = version.getClass().getMethod("getVersion");
            return (int) getVersion.invoke(version);
        } catch (Throwable e) {
            return -1;
        }
    }
}
