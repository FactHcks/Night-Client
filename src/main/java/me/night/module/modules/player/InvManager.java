package me.night.module.modules.player;

import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.NumberProperty;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class InvManager extends Module {
    public final BooleanProperty autoArmor = new BooleanProperty("Auto Armor", "Automatically equips best armor", true);
    public final BooleanProperty cleanTrash = new BooleanProperty("Clean Trash", "Drops useless duplicate items", true);
    public final BooleanProperty sortSword = new BooleanProperty("Sort Sword", "Puts best sword into hotbar slot 1", true);
    public final BooleanProperty invOnly = new BooleanProperty("Inventory Only", "Only manage when inventory screen is open", false);
    public final NumberProperty delay = new NumberProperty("Delay (ms)", 75.0, 20.0, 400.0, 15.0);

    private long lastActionTime = 0L;

    public InvManager() {
        super("InvManager", "Automates inventory sorting, auto-armor, and cleanup", Category.PLAYER);
        addProperties(autoArmor, cleanTrash, sortSword, invOnly, delay);
    }

    @Override
    public String getSuffix() {
        if (autoArmor.getValue() && cleanTrash.getValue()) return "Full";
        if (autoArmor.getValue()) return "Armor";
        return "Clean";
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null || mc.interactionManager == null) return;

        // Container screen check
        if (invOnly.getValue()) {
            if (!(mc.currentScreen instanceof InventoryScreen)) return;
        } else {
            // If in other containers (chest, crafting table, anvil, furnace), do not clean
            if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        }

        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return;
        PlayerScreenHandler handler = mc.player.playerScreenHandler;

        long now = System.currentTimeMillis();
        if (now - lastActionTime < delay.getValue()) return;

        // 1. Auto Armor Execution
        if (autoArmor.getValue()) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (EquipmentSlot eqSlot : slots) {
                int equippedScore = getArmorScore(mc.player.getEquippedStack(eqSlot));
                int bestInvSlot = -1;
                int bestInvScore = equippedScore;

                for (int i = 9; i <= 44; i++) {
                    if (handler.getSlot(i).hasStack()) {
                        ItemStack stack = handler.getSlot(i).getStack();
                        EquippableComponent eq = stack.get(DataComponentTypes.EQUIPPABLE);
                        if (eq != null && eq.slot() == eqSlot) {
                            int score = getArmorScore(stack);
                            if (score > bestInvScore) {
                                bestInvScore = score;
                                bestInvSlot = i;
                            }
                        }
                    }
                }

                if (bestInvSlot != -1) {
                    // If armor slot is currently empty, shift click to equip
                    if (mc.player.getEquippedStack(eqSlot).isEmpty()) {
                        mc.interactionManager.clickSlot(handler.syncId, bestInvSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    } else {
                        // Unequip current piece first
                        int armorSlotId = getArmorSlotId(eqSlot);
                        mc.interactionManager.clickSlot(handler.syncId, armorSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                    }
                    lastActionTime = now;
                    return;
                }
            }
        }

        // 2. Sort Sword into Hotbar Slot 1 (Slot ID 36)
        if (sortSword.getValue()) {
            int bestSwordSlot = -1;
            int bestSwordDamage = -1;

            for (int i = 9; i <= 44; i++) {
                if (handler.getSlot(i).hasStack()) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    int damage = getSwordScore(stack);
                    if (damage > bestSwordDamage) {
                        bestSwordDamage = damage;
                        bestSwordSlot = i;
                    }
                }
            }

            if (bestSwordSlot != -1 && bestSwordSlot != 36) {
                // Button 0 corresponds to hotbar index 0 (slot 36)
                mc.interactionManager.clickSlot(handler.syncId, bestSwordSlot, 0, SlotActionType.SWAP, mc.player);
                lastActionTime = now;
                return;
            }
        }

        // 3. Clean Trash Items
        if (cleanTrash.getValue()) {
            int bestSwordScore = -1;
            for (int i = 9; i <= 44; i++) {
                if (handler.getSlot(i).hasStack()) {
                    int s = getSwordScore(handler.getSlot(i).getStack());
                    if (s > bestSwordScore) bestSwordScore = s;
                }
            }

            for (int i = 9; i <= 44; i++) {
                if (handler.getSlot(i).hasStack()) {
                    ItemStack stack = handler.getSlot(i).getStack();

                    // Check for general junk
                    if (isJunkItem(stack)) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                        lastActionTime = now;
                        return;
                    }

                    // Check for duplicate worse sword
                    int swordScore = getSwordScore(stack);
                    if (swordScore > 0 && swordScore < bestSwordScore) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                        lastActionTime = now;
                        return;
                    }
                }
            }
        }
    }

    private int getArmorSlotId(EquipmentSlot slot) {
        switch (slot) {
            case HEAD: return 5;
            case CHEST: return 6;
            case LEGS: return 7;
            case FEET: return 8;
            default: return -1;
        }
    }

    private int getArmorScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        EquippableComponent eq = stack.get(DataComponentTypes.EQUIPPABLE);
        if (eq == null || !eq.slot().isArmorSlot()) return -1;

        String key = stack.getItem().getTranslationKey().toLowerCase();
        int score = 0;
        if (key.contains("netherite")) score = 600;
        else if (key.contains("diamond")) score = 500;
        else if (key.contains("iron")) score = 400;
        else if (key.contains("chainmail")) score = 300;
        else if (key.contains("golden") || key.contains("gold")) score = 200;
        else if (key.contains("leather")) score = 100;
        else if (key.contains("elytra")) score = 350;
        else score = 50;

        ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants != null) {
            score += enchants.getSize() * 15;
        }

        return score;
    }

    private int getSwordScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        String key = stack.getItem().getTranslationKey().toLowerCase();
        if (!key.contains("sword")) return -1;

        int score = 0;
        if (key.contains("netherite")) score = 60;
        else if (key.contains("diamond")) score = 50;
        else if (key.contains("iron")) score = 40;
        else if (key.contains("stone")) score = 30;
        else if (key.contains("golden") || key.contains("gold")) score = 25;
        else if (key.contains("wooden") || key.contains("wood")) score = 20;

        ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants != null) {
            score += enchants.getSize() * 5;
        }

        return score;
    }

    private boolean isJunkItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String key = stack.getItem().getTranslationKey().toLowerCase();
        return key.contains("poisonous_potato") || key.contains("rotten_flesh") ||
               key.contains("dead_bush") || key.contains("spider_eye") ||
               key.contains("dirt") || key.contains("gravel");
    }
}
