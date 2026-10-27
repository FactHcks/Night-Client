package me.night.module.modules.render;

import me.night.module.Category;
import me.night.module.Module;
import me.night.module.modules.combat.Killaura;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

/** 1.7 / 1.8 style first-person animations (purely visual). */
public class Animations extends Module {
    private static Animations INSTANCE;

    public final ModeProperty style = new ModeProperty("Block Style", "1.7", "1.7", "1.8");
    public final BooleanProperty oldPosition = new BooleanProperty("1.8 Item Position", "Hold swords and tools like 1.8", true);
    public final BooleanProperty blockOnAura = new BooleanProperty("Block On Aura", "Show the blocking pose while Killaura has a target", true);
    public final BooleanProperty noEquipDip = new BooleanProperty("No Cooldown Dip", "Item doesn't lower after attacking (1.8 style)", true);
    public final NumberProperty swingSpeed = new NumberProperty("Swing Speed", "1 is normal; higher swings faster", 1.0, 0.4, 2.0, 0.05);
    public final NumberProperty scale = new NumberProperty("Item Scale", 1.0, 0.5, 1.5, 0.05);
    public final NumberProperty offsetX = new NumberProperty("Offset X", 0, -1, 1, 0.02);
    public final NumberProperty offsetY = new NumberProperty("Offset Y", 0, -1, 1, 0.02);
    public final NumberProperty offsetZ = new NumberProperty("Offset Z", 0, -1, 1, 0.02);

    public Animations() {
        super("Animations", "1.7 / 1.8 sword blocking and swing animations", Category.RENDER);
        addProperties(style, oldPosition, blockOnAura, noEquipDip, swingSpeed, scale, offsetX, offsetY, offsetZ);
        INSTANCE = this;
    }

    /** The module if it's enabled, else null. */
    public static Animations active() {
        return INSTANCE != null && INSTANCE.isEnabled() ? INSTANCE : null;
    }

    public static boolean isSword(ItemStack stack) {
        return stack.isIn(ItemTags.SWORDS);
    }

    /** Items held like a sword in 1.8. */
    public static boolean isHandheld(ItemStack stack) {
        return isSword(stack) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.PICKAXES)
                || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES);
    }

    public boolean shouldBlock(ItemStack stack) {
        if (!isSword(stack)) return false;
        if (mc.options.useKey.isPressed()) return true;
        if (Killaura.isAutoBlocking()) return true;
        return blockOnAura.getValue() && Killaura.currentTarget != null;
    }
}
