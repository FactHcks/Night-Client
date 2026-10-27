package me.night.module.modules.player;

import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class ChestStealer extends Module {
    public final NumberProperty delay = new NumberProperty("Delay (ms)", 50.0, 0.0, 300.0, 10.0);
    public final BooleanProperty autoClose = new BooleanProperty("Auto Close", true);
    public final BooleanProperty ignoreTrash = new BooleanProperty("Ignore Trash", true);

    private long lastStealTime = 0L;

    public ChestStealer() {
        super("ChestStealer", "Automatically loots chests into your inventory", Category.PLAYER);
        addProperties(delay, autoClose, ignoreTrash);
    }

    @Override
    public String getSuffix() {
        return delay.getInt() + "ms";
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null) return;

        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
            return;
        }

        GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;
        int chestSlots = handler.getRows() * 9;

        long now = System.currentTimeMillis();
        if (now - lastStealTime < delay.getValue()) return;

        boolean hasItemsLeft = false;

        for (int i = 0; i < chestSlots; i++) {
            if (handler.getSlot(i).hasStack()) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (!isTrash(stack)) {
                    hasItemsLeft = true;
                    if (mc.interactionManager != null) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                        lastStealTime = now;
                        return;
                    }
                }
            }
        }

        // If no desirable items remain in the chest, auto-close
        if (!hasItemsLeft && autoClose.getValue()) {
            mc.player.closeHandledScreen();
            mc.setScreen(null);
        }
    }

    private boolean isTrash(ItemStack stack) {
        if (!ignoreTrash.getValue() || stack.isEmpty()) return false;
        String name = stack.getItem().getTranslationKey().toLowerCase();
        return name.contains("dirt") || name.contains("gravel") || name.contains("seed") ||
               name.contains("rotten_flesh") || name.contains("poisonous_potato") ||
               name.contains("dead_bush") || name.contains("string") || name.contains("spider_eye");
    }
}
