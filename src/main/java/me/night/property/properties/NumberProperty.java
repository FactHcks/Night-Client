package me.night.property.properties;

import me.night.property.Property;
import java.util.function.Supplier;

public class NumberProperty extends Property<Double> {
    private final double min;
    private final double max;
    private final double increment;

    public NumberProperty(String name, double defaultValue, double min, double max, double increment) {
        this(name, "", defaultValue, min, max, increment, () -> true);
    }

    public NumberProperty(String name, String description, double defaultValue, double min, double max, double increment) {
        this(name, description, defaultValue, min, max, increment, () -> true);
    }

    public NumberProperty(String name, String description, double defaultValue, double min, double max, double increment, Supplier<Boolean> visibility) {
        super(name, description, defaultValue, visibility);
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    @Override
    public void setValue(Double val) {
        double clamped = Math.max(min, Math.min(max, val));
        double stepped = Math.round(clamped / increment) * increment;
        super.setValue(stepped);
    }

    public int getInt() {
        return getValue().intValue();
    }

    public float getFloat() {
        return getValue().floatValue();
    }

    public double getMin() {
        return this.min;
    }

    public double getMax() {
        return this.max;
    }

    public double getIncrement() {
        return this.increment;
    }
}
