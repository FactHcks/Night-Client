package me.night.module.modules.world;

import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.util.RotationManager;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scaffold extends Module {
    public final ModeProperty mode = new ModeProperty("Mode", "Normal", "Normal", "KeepY", "Telly", "Telly KeepY");
    public final ModeProperty towerMode = new ModeProperty("Tower Mode", "Normal", "Normal", "Telly", "Off");
    public final ModeProperty moveFix = new ModeProperty("Move Fix", "Silent", "Off", "Silent", "Strict");
    public final ModeProperty sprint = new ModeProperty("Sprint", "Vanilla", "None", "Vanilla");
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", "Silently look down/back at block face", true);
    public final ModeProperty rotations = new ModeProperty("Rotations", "Normal",
            "Normal", "Strict", "Hypixel", "Backwards", "Pitch Only", "Godbridge", "Breezily", "Snap");
    public final NumberProperty rotSpeed = new NumberProperty("Rotation Speed", "Speed of silent rotation turns", 180.0, 10.0, 180.0, 5.0);
    public final BooleanProperty raycast = new BooleanProperty("Raycast", "Only place if rotation raycast intersects target block face", true);
    public final BooleanProperty keepY = new BooleanProperty("Keep Y", "Keep Y level", false);
    public final BooleanProperty tower = new BooleanProperty("Tower", "Tower upwards when jumping", true);

    private int keepYLevel = 0;
    private int keepYState = 0;
    private int placementDelayTicks = 0;
    private boolean tellyRotationActive = false;

    public Scaffold() {
        super("Scaffold", "Places blocks underneath player", Category.WORLD);
        addProperties(mode, towerMode, moveFix, sprint, silentAim, rotations, rotSpeed, raycast, keepY, tower);
    }

    public boolean isKeepY() {
        return mode.is("KeepY") || mode.is("Telly KeepY") || keepY.getValue();
    }

    public boolean isTelly() {
        return mode.is("Telly") || mode.is("Telly KeepY");
    }

    public boolean isTower() {
        return !towerMode.is("Off") && tower.getValue();
    }

    public boolean isTellyTower() {
        return towerMode.is("Telly") && tower.getValue();
    }

    @Override
    public String getSuffix() {
        if (mc.player == null) return mode.getValue();
        int blocks = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) blocks += stack.getCount();
        }
        return mode.getValue() + " (" + blocks + ")";
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            keepYLevel = (int) Math.floor(mc.player.getY());
            keepYState = 0;
            placementDelayTicks = 0;
            tellyRotationActive = false;
        }
    }

    @Override
    protected void onDisable() {
        RotationManager.reset();
        keepYLevel = 0;
        keepYState = 0;
        placementDelayTicks = 0;
        tellyRotationActive = false;
    }

    public boolean isCeilingAbove() {
        if (mc.player == null || mc.world == null) return false;
        return !mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(0.0, 1.0, 0.0));
    }

    /** Matches NewScaff isTellyTakeoff logic */
    public boolean isTellyTakeoff() {
        if (mc.player == null || !mc.player.isOnGround() || !RotationManager.isForwardPressed()) {
            return false;
        }
        if (isCeilingAbove()) {
            return false;
        }
        if (isTelly() && keepYState > 0) {
            return true;
        }
        return isTellyTower() && mc.options.jumpKey.isPressed();
    }

    /** Called from MixinClientPlayerEntity to trigger natural vanilla jump on KeepY takeoff without motion hacks */
    public boolean shouldKeepYJump() {
        if (!isEnabled() || mc.player == null) return false;
        if (!mc.player.isOnGround() || !RotationManager.isForwardPressed()) return false;
        if (mc.options.jumpKey.isPressed()) return false; // user is manually towering
        if (isCeilingAbove()) return false;
        return isKeepY() && keepYState > 0;
    }

    /** A solid block to place against, and which of its faces to click. */
    public record Support(BlockPos block, Direction face) {
    }

    /** Finds a solid neighbour of pos to build onto (never the block above). */
    public Support supportFor(BlockPos pos) {
        for (Direction dir : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos adj = pos.offset(dir);
            if (!mc.world.getBlockState(adj).isReplaceable()) return new Support(adj, dir.getOpposite());
        }
        return null;
    }

    /** Finds placement target and connecting face matching NewScaff algorithm */
    public Support findPlacementTarget(int targetY) {
        BlockPos desiredPlacementBlock = new BlockPos(
                (int) Math.floor(mc.player.getX()),
                targetY,
                (int) Math.floor(mc.player.getZ())
        );

        if (!mc.world.getBlockState(desiredPlacementBlock).isReplaceable()) {
            return null;
        }

        Support direct = supportFor(desiredPlacementBlock);
        if (direct != null) {
            return direct;
        }

        List<BlockPos> candidates = new ArrayList<>();
        double reach = 4.5;
        double reachSq = reach * reach;
        Vec3d eye = mc.player.getEyePos();

        for (int ox = -3; ox <= 3; ox++) {
            for (int oy = -3; oy <= 0; oy++) {
                for (int oz = -3; oz <= 3; oz++) {
                    BlockPos candidate = desiredPlacementBlock.add(ox, oy, oz);
                    if (mc.world.getBlockState(candidate).isReplaceable()) continue;

                    if (eye.squaredDistanceTo(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5) > reachSq) {
                        continue;
                    }

                    if (isKeepY() && keepYState > 0 && candidate.getY() >= keepYLevel) {
                        continue;
                    }

                    for (Direction dir : Direction.values()) {
                        if (dir != Direction.DOWN && mc.world.getBlockState(candidate.offset(dir)).isReplaceable()) {
                            candidates.add(candidate);
                            break;
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator.comparingDouble(c -> c.getSquaredDistance(desiredPlacementBlock)));
        BlockPos bestCandidate = candidates.get(0);

        Direction bestFace = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            BlockPos neighbor = bestCandidate.offset(dir);
            if (neighbor.getY() > desiredPlacementBlock.getY()) continue;
            if (!mc.world.getBlockState(neighbor).isReplaceable()) continue;

            double d = neighbor.getSquaredDistance(desiredPlacementBlock);
            if (bestFace == null || d < bestDistSq || (d == bestDistSq && dir == Direction.UP)) {
                bestDistSq = d;
                bestFace = dir;
            }
        }

        return bestFace == null ? null : new Support(bestCandidate, bestFace);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;

        int slot = findBlockSlot();
        if (slot == -1) {
            RotationManager.reset();
            return;
        }

        boolean jumpPressed = mc.options.jumpKey.isPressed();

        // KeepY ground state update (matching NewScaff.java:724-744)
        if (mc.player.isOnGround()) {
            if (keepYState > 0) keepYState--;
            if (keepYState < 0) keepYState++;

            if (keepYState == 0 && isKeepY() && !jumpPressed) {
                keepYState = 1;
            }

            keepYLevel = (int) Math.floor(mc.player.getY());
            tellyRotationActive = false;
        }

        if (placementDelayTicks > 0) {
            placementDelayTicks--;
        }

        // Sprint policy: only disable sprint if configured as "None" and NOT during takeoff
        if (sprint.is("None") && !isTellyTakeoff()) {
            mc.player.setSprinting(false);
        }

        // Calculate target placement Y
        int playerBlockY = (int) Math.floor(mc.player.getY());
        int targetY;
        if (isKeepY() && (!isTower() || !jumpPressed)) {
            targetY = Math.min(playerBlockY, keepYLevel) - 1;
        } else {
            targetY = playerBlockY - 1;
        }

        Support support = findPlacementTarget(targetY);

        // Calculate placement rotations
        float placeYaw = mc.player.getYaw() + 180.0F;
        float placePitch = 82.0F;
        if (support != null) {
            BlockPos adj = support.block();
            Direction side = support.face();
            Vec3d hitVec = new Vec3d(adj.getX() + 0.5 + side.getOffsetX() * 0.5,
                    adj.getY() + 0.5 + side.getOffsetY() * 0.5,
                    adj.getZ() + 0.5 + side.getOffsetZ() * 0.5);
            float[] r = RotationManager.calculateRotations(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                    hitVec.x, hitVec.y, hitVec.z);
            placeYaw = r[0];
            placePitch = r[1];
        }

        float targetYaw;
        float targetPitch;

        // NewScaff Telly Takeoff & Tower logic:
        if (isTellyTakeoff()) {
            targetYaw = mc.player.getYaw();
            targetPitch = mc.player.getPitch();
            placementDelayTicks = 3;
            tellyRotationActive = true;
        } else if (isTellyTower() && jumpPressed) {
            // Telly tower rotates forwards without modifying motion
            targetYaw = mc.player.getYaw();
            targetPitch = (placementDelayTicks > 0) ? mc.player.getPitch() : 82.0F;
        } else if (tellyRotationActive && placementDelayTicks > 0) {
            targetYaw = mc.player.getYaw();
            targetPitch = mc.player.getPitch();
        } else {
            switch (rotations.getValue()) {
                case "Strict" -> {
                    targetYaw = mc.player.getYaw() + 180.0F;
                    targetPitch = 82.0F;
                }
                case "Hypixel" -> {
                    targetYaw = mc.player.getYaw() + 180.0F;
                    targetPitch = 80.0F;
                }
                case "Backwards" -> {
                    targetYaw = mc.player.getYaw() + 180.0F;
                    targetPitch = 82.0F;
                }
                case "Pitch Only" -> {
                    targetYaw = mc.player.getYaw();
                    targetPitch = 82.0F;
                }
                case "Godbridge" -> {
                    targetYaw = mc.player.getYaw() + 45.0F;
                    targetPitch = 75.5F;
                }
                case "Breezily" -> {
                    float sway = (float) Math.sin(System.currentTimeMillis() / 120.0) * 15.0F;
                    targetYaw = mc.player.getYaw() + 180.0F + sway;
                    targetPitch = 80.0F;
                }
                case "Snap" -> {
                    if (support != null) {
                        targetYaw = placeYaw;
                        targetPitch = placePitch;
                    } else {
                        targetYaw = mc.player.getYaw();
                        targetPitch = mc.player.getPitch();
                    }
                }
                default -> { // "Normal"
                    targetYaw = placeYaw;
                    targetPitch = placePitch;
                }
            }
            if (tellyRotationActive && mc.player.getVelocity().y <= 0.0) {
                tellyRotationActive = false;
            }
        }

        // Apply continuous silent rotations
        if (silentAim.getValue()) {
            RotationManager.setRotations(targetYaw, targetPitch, moveFix.getValue(), rotSpeed.getFloat(), true);
        }

        // Delay checks: do not place during telly takeoff or active placement delay
        if (placementDelayTicks > 0 || isTellyTakeoff()) {
            return;
        }

        if (support == null) {
            return;
        }

        BlockPos adj = support.block();
        Direction side = support.face();
        Vec3d hitVec = new Vec3d(adj.getX() + 0.5 + side.getOffsetX() * 0.5,
                adj.getY() + 0.5 + side.getOffsetY() * 0.5,
                adj.getZ() + 0.5 + side.getOffsetZ() * 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, side, adj, false);

        // Raycast verification
        if (raycast.getValue()) {
            Vec3d eye = mc.player.getEyePos();
            Vec3d dir = Vec3d.fromPolar(RotationManager.getPitch(), RotationManager.getYaw());
            Vec3d reach = eye.add(dir.multiply(4.5));
            BlockHitResult rayHit = mc.world.raycast(new RaycastContext(
                    eye, reach, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player
            ));
            if (rayHit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            hitResult = rayHit;
        }

        // Place block
        if (mc.interactionManager != null) {
            int prevSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.getInventory().setSelectedSlot(prevSlot);
        }
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }
}
