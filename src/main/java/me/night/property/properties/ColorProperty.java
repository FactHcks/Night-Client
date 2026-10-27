package me.night.property.properties;

import me.night.property.Property;
import java.awt.Color;
import java.util.function.Supplier;

public class ColorProperty extends Property<Color> {
    public ColorProperty(String name, Color defaultColor) {
        super(name, "", defaultColor);
    }

    public ColorProperty(String name, String description, Color defaultColor) {
        super(name, description, defaultColor);
    }

    public ColorProperty(String name, String description, Color defaultColor, Supplier<Boolean> visibility) {
        super(name, description, defaultColor, visibility);
    }

    public int getRGB() {
        return getValue().getRGB();
    }
}
