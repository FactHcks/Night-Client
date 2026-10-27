package me.night.property.properties;

import me.night.property.Property;
import java.util.function.Supplier;

public class BooleanProperty extends Property<Boolean> {
    public BooleanProperty(String name, boolean defaultValue) {
        super(name, "", defaultValue);
    }

    public BooleanProperty(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public BooleanProperty(String name, String description, boolean defaultValue, Supplier<Boolean> visibility) {
        super(name, description, defaultValue, visibility);
    }

    public void toggle() {
        setValue(!getValue());
    }
}
