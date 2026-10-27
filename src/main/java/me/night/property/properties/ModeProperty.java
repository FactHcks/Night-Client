package me.night.property.properties;

import me.night.property.Property;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class ModeProperty extends Property<String> {
    private final List<String> modes;

    public ModeProperty(String name, String defaultMode, String... modes) {
        this(name, "", defaultMode, Arrays.asList(modes), () -> true);
    }

    public ModeProperty(String name, String defaultMode, Supplier<Boolean> visibility, String... modes) {
        this(name, "", defaultMode, Arrays.asList(modes), visibility);
    }

    public ModeProperty(String name, String description, String defaultMode, List<String> modes, Supplier<Boolean> visibility) {
        super(name, description, defaultMode, visibility);
        this.modes = modes;
    }

    public List<String> getModes() {
        return this.modes;
    }

    public boolean is(String mode) {
        return getValue().equalsIgnoreCase(mode);
    }

    public void cycle() {
        cycle(true);
    }

    public void cycle(boolean forward) {
        int index = modes.indexOf(getValue());
        if (index == -1) index = 0;
        if (forward) {
            index = (index + 1) % modes.size();
        } else {
            index = (index - 1 + modes.size()) % modes.size();
        }
        setValue(modes.get(index));
    }
}
