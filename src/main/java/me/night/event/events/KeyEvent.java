package me.night.event.events;

import me.night.event.Event;

public class KeyEvent extends Event {
    private final int key;
    private final int scancode;
    private final int action;
    private final int modifiers;

    public KeyEvent(int key, int scancode, int action, int modifiers) {
        this.key = key;
        this.scancode = scancode;
        this.action = action;
        this.modifiers = modifiers;
    }

    public int getKey() {
        return this.key;
    }

    public int getScancode() {
        return this.scancode;
    }

    public int getAction() {
        return this.action;
    }

    public int getModifiers() {
        return this.modifiers;
    }

    public boolean isPress() {
        return this.action == 1;
    }

    public boolean isRelease() {
        return this.action == 0;
    }
}
