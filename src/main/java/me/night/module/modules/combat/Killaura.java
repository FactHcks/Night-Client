package me.night.module.modules.combat;

import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.module.modules.render.Animations;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.util.RotationManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

public class Killaura extends Module {
    private static Killaura INSTANCE;
    public static LivingEntity currentTarget = null;

    public final NumberProperty range = new NumberProperty("Range", "Attack range in blocks", 3.8, 3.0, 6.0, 0.1);
    public final ModeProperty mode = new ModeProperty("Mode", "Single", "Single", "Switch");
    public final ModeProperty attackMode = new ModeProperty("Attack Mode", "Auto", "Auto", "Cooldown", "CPS");
    public final NumberProperty cps = new NumberProperty("CPS", "Attacks per second for 1.8-style combat",
            10, 1, 20, 1, () -> !attackMode.is("Cooldown"));
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", "Silently rotate packet angles", true);
    public final ModeProperty moveFix = new ModeProperty("Move Fix", "Strict", () -> silentAim.getValue(), "Off", "Strict", "Silent");
    public final ModeProperty autoBlock = new ModeProperty("AutoBlock", "Legit", "Off", "Legit", "Packet", "Fake");
    public final NumberProperty switchDelay = new NumberProperty("Switch Delay", "Milliseconds before moving to the next target",
            300, 50, 1000, 10, () -> mode.is("Switch"));
    public final BooleanProperty playersOnly = new BooleanProperty("Players Only", true);
    public final BooleanProperty pauseInMenus = new BooleanProperty("Pause In Menus", "Don't attack while a menu is open or while eating/using an item", true);

    private int switchIndex = 0;
    private long lastSwitch = 0;
    private long nextAttack = 0;
    private boolean isBlocking = false;

    public Killaura() {
        super("Killaura", "Automatically attacks nearby entities", Category.COMBAT);
        addProperties(range, mode, switchDelay, attackMode, cps, silentAim, moveFix, autoBlock, playersOnly, pauseInMenus);
        INSTANCE = this;
    }

    public static boolean isAutoBlocking() {
        return currentTarget != null && INSTANCE != null && INSTANCE.isEnabled() && !INSTANCE.autoBlock.is("Off");
    }

    private boolean isHoldingBlockable() {
        if (mc.player == null) return false;
        return Animations.isSword(mc.player.getMainHandStack()) || mc.player.getOffHandStack().getItem() instanceof ShieldItem;
    }

    private Hand getBlockHand() {
        if (mc.player != null && mc.player.getOffHandStack().getItem() instanceof ShieldItem) {
            return Hand.OFF_HAND;
        }
        return Hand.MAIN_HAND;
    }

    /**
     * 1.9+ servers have an attack cooldown, so attacking when it's full does the most damage.
     * 1.8 servers (through ViaFabricPlus) have no cooldown, so attacks are timed by CPS instead.
     */
    private boolean useCps() {
        return attackMode.is("CPS") || (attackMode.is("Auto") && me.night.util.ServerVersion.isLegacy());
    }

    private boolean readyToAttack() {
        if (!useCps()) return mc.player.getAttackCooldownProgress(0.5F) >= 1.0F;
        long now = System.currentTimeMillis();
        if (now < nextAttack) return false;
        nextAttack = now + Math.round(1000.0 / cps.getValue());
        return true;
    }

    @Override
    public String getSuffix() {
        return useCps() ? mode.getValue() + " " + cps.getInt() + " CPS" : mode.getValue();
    }

    @Override
    protected void onDisable() {
        stopBlock();
        currentTarget = null;
    }

    private void startBlock() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!isHoldingBlockable()) return;

        if (autoBlock.is("Legit")) {
            Hand hand = getBlockHand();
            mc.interactionManager.interactItem(mc.player, hand);
            isBlocking = true;
        } else if (autoBlock.is("Packet")) {
            Hand hand = getBlockHand();
            mc.interactionManager.interactItem(mc.player, hand);
            isBlocking = true;
        } else if (autoBlock.is("Fake")) {
            isBlocking = true;
        }
    }

    private void stopBlock() {
        if (!isBlocking) return;
        if (mc.player != null && mc.interactionManager != null) {
            if (autoBlock.is("Legit")) {
                mc.interactionManager.stopUsingItem(mc.player);
            } else if (autoBlock.is("Packet")) {
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
                }
            }
        }
        isBlocking = false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;

        double r = this.range.getValue();
        List<LivingEntity> targets = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(e -> e instanceof LivingEntity && e != mc.player && e.isAlive())
                .map(e -> (LivingEntity) e)
                .filter(e -> !playersOnly.getValue() || e instanceof PlayerEntity)
                .filter(e -> !AntiBot.isBot(e))
                .filter(e -> !Teams.isTeammate(e))
                .filter(e -> mc.player.distanceTo(e) <= r)
                .sorted(Comparator.comparingDouble(mc.player::distanceTo))
                .toList();

        if (!targets.isEmpty()) {
            LivingEntity target;
            if (mode.is("Switch") && targets.size() > 1) {
                long now = System.currentTimeMillis();
                if (now - lastSwitch >= switchDelay.getValue()) {
                    switchIndex++;
                    lastSwitch = now;
                }
                target = targets.get(Math.floorMod(switchIndex, targets.size()));
            } else {
                target = targets.get(0);
            }
            currentTarget = target;

            // Don't count autoblock sword holding as pausing item use
            boolean isEatingOrDrinking = mc.player.isUsingItem()
                    && !Animations.isSword(mc.player.getActiveItem())
                    && !(mc.player.getActiveItem().getItem() instanceof ShieldItem);

            boolean paused = pauseInMenus.getValue()
                    && (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> || isEatingOrDrinking);

            // Silent Aim Rotations with MoveFix
            if (this.silentAim.getValue()) {
                float[] rots = RotationManager.calculateRotations(
                        mc.player.getX(),
                        mc.player.getEyeY(),
                        mc.player.getZ(),
                        target.getX(),
                        target.getEyeY() - 0.2,
                        target.getZ()
                );
                RotationManager.setRotations(rots[0], rots[1], moveFix.getValue());
            }

            // Attack with cooldown / CPS check and AutoBlock handling
            if (!paused && readyToAttack()) {
                if (mc.interactionManager != null) {
                    // For Legit & Packet autoblock: unblock briefly so attack lands cleanly
                    if (isBlocking && (autoBlock.is("Legit") || autoBlock.is("Packet"))) {
                        stopBlock();
                    }

                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);

                    // Re-block immediately after hit
                    if (!autoBlock.is("Off")) {
                        startBlock();
                    }
                }
            } else if (!isBlocking && !autoBlock.is("Off") && !paused) {
                // Ensure blocking is held between hits
                startBlock();
            }
        } else {
            if (isBlocking) {
                stopBlock();
            }
            currentTarget = null;
        }
    }
}
