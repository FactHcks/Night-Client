package me.night.module.modules.player;

import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.NumberProperty;

public class FastPlace extends Module {
    public final NumberProperty delay = new NumberProperty("Delay", 0.0, 0.0, 4.0, 1.0);

    public FastPlace() {
        super("FastPlace", "Removes block place delay", Category.PLAYER);
        addProperties(delay);
    }

    @Override
    public String getSuffix() {
        return delay.getInt() + "";
    }
}
