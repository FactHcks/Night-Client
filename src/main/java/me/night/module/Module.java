package me.night.module;

import me.night.Night;
import me.night.property.Property;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private int key;
    private boolean enabled;
    private boolean hidden;
    public float animFactor = 0.0F;

    private final List<Property<?>> properties = new ArrayList<>();

    public Module(String name, String description, Category category) {
        this(name, description, category, 0);
    }

    public Module(String name, String description, Category category, int key) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.key = key;
        this.enabled = false;
        this.hidden = false;
    }

    public void addProperties(Property<?>... props) {
        this.properties.addAll(Arrays.asList(props));
    }

    public List<Property<?>> getProperties() {
        return this.properties;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public Category getCategory() {
        return this.category;
    }

    public int getKey() {
        return this.key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (this.enabled) {
                Night.INSTANCE.getEventManager().register(this);
                this.onEnable();
            } else {
                Night.INSTANCE.getEventManager().unregister(this);
                this.onDisable();
            }
            try {
                me.night.util.ExhiSoundManager.playToggle(this.enabled);
                if (!this.hidden) {
                    me.night.ui.notification.Notifications.push(this.name,
                            this.enabled ? "Module enabled" : "Module disabled",
                            this.enabled ? me.night.ui.notification.Notifications.Kind.ENABLED
                                         : me.night.ui.notification.Notifications.Kind.DISABLED);
                }
            } catch (Exception ignored) {}
        }
    }

    public void toggle() {
        setEnabled(!this.enabled);
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String getSuffix() {
        return null;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }
}
