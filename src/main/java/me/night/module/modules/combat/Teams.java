package me.night.module.modules.combat;

import me.night.Night;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Objects;

public class Teams extends Module {
    public final BooleanProperty scoreboard = new BooleanProperty("Scoreboard", "Checks scoreboard team match", true);
    public final BooleanProperty color = new BooleanProperty("Color", "Checks display name / prefix color match", true);
    public final BooleanProperty armorColor = new BooleanProperty("Armor Color", "Checks dyed leather armor color match", true);

    public Teams() {
        super("Teams", "Prevents targeting and attacking teammates", Category.COMBAT);
        addProperties(scoreboard, color, armorColor);
    }

    public static boolean isTeammate(Entity entity) {
        if (entity == null || !(entity instanceof PlayerEntity player)) {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || player == mc.player) {
            return true;
        }

        if (Night.INSTANCE == null || Night.INSTANCE.getModuleManager() == null) {
            return false;
        }

        Teams teams = Night.INSTANCE.getModuleManager().getModule(Teams.class);
        if (teams == null || !teams.isEnabled()) {
            return false;
        }

        return teams.isEntityTeammate(player);
    }

    public boolean isEntityTeammate(PlayerEntity player) {
        if (player == null || mc.player == null) {
            return false;
        }
        if (player == mc.player) {
            return true;
        }

        // 1. Scoreboard team check
        if (scoreboard.getValue()) {
            // Built-in vanilla isTeammate
            if (mc.player.isTeammate(player)) {
                return true;
            }

            Team selfTeam = mc.player.getScoreboardTeam();
            Team targetTeam = player.getScoreboardTeam();
            if (selfTeam != null && targetTeam != null) {
                if (Objects.equals(selfTeam.getName(), targetTeam.getName())) {
                    return true;
                }
            }

            // Tab list team check
            if (mc.getNetworkHandler() != null) {
                PlayerListEntry selfEntry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                PlayerListEntry targetEntry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
                if (selfEntry != null && targetEntry != null) {
                    Team entrySelfTeam = selfEntry.getScoreboardTeam();
                    Team entryTargetTeam = targetEntry.getScoreboardTeam();
                    if (entrySelfTeam != null && entryTargetTeam != null) {
                        if (Objects.equals(entrySelfTeam.getName(), entryTargetTeam.getName())) {
                            return true;
                        }
                    }
                }
            }
        }

        // 2. Color prefix / name style check (Bedwars / Skywars)
        if (color.getValue()) {
            Team selfTeam = mc.player.getScoreboardTeam();
            Team targetTeam = player.getScoreboardTeam();
            if (selfTeam != null && targetTeam != null) {
                Formatting selfFmt = selfTeam.getColor();
                Formatting targetFmt = targetTeam.getColor();
                if (selfFmt != Formatting.RESET && selfFmt == targetFmt) {
                    return true;
                }
            }

            // Check display name style color
            if (mc.player.getDisplayName() != null && player.getDisplayName() != null) {
                TextColor c1 = mc.player.getDisplayName().getStyle().getColor();
                TextColor c2 = player.getDisplayName().getStyle().getColor();
                if (c1 != null && c2 != null && c1.equals(c2)) {
                    return true;
                }
            }
        }

        // 3. Dyed leather armor color check (BedWars / mini-games)
        if (armorColor.getValue()) {
            // Check chestplate
            ItemStack selfChest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            ItemStack targetChest = player.getEquippedStack(EquipmentSlot.CHEST);
            if (!selfChest.isEmpty() && !targetChest.isEmpty()) {
                int c1 = DyedColorComponent.getColor(selfChest, -1);
                int c2 = DyedColorComponent.getColor(targetChest, -1);
                if (c1 != -1 && c1 == c2) {
                    return true;
                }
            }

            // Check helmet
            ItemStack selfHead = mc.player.getEquippedStack(EquipmentSlot.HEAD);
            ItemStack targetHead = player.getEquippedStack(EquipmentSlot.HEAD);
            if (!selfHead.isEmpty() && !targetHead.isEmpty()) {
                int c1 = DyedColorComponent.getColor(selfHead, -1);
                int c2 = DyedColorComponent.getColor(targetHead, -1);
                if (c1 != -1 && c1 == c2) {
                    return true;
                }
            }
        }

        return false;
    }
}
