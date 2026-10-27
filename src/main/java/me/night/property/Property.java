package me.night.property;

import java.util.function.Supplier;

public abstract class Property<T> {
    private final String name;
    private final String description;
    private T value;
    private final T defaultValue;
    private Supplier<Boolean> visibility;

    public Property(String name, String description, T defaultValue) {
        this(name, description, defaultValue, () -> true);
    }

    public Property(String name, String description, T defaultValue, Supplier<Boolean> visibility) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.visibility = visibility;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public T getValue() {
        return this.value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public T getDefaultValue() {
        return this.defaultValue;
    }

    public void reset() {
        this.value = this.defaultValue;
    }

    public boolean isVisible() {
        return this.visibility == null || this.visibility.get();
    }

    public void setVisibility(Supplier<Boolean> visibility) {
        this.visibility = visibility;
    }
}
