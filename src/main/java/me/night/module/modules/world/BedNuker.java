package me.night.module.modules.world;

import me.night.event.EventTarget;
import me.night.event.events.Render2DEvent;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.property.properties.NumberProperty;
import me.night.util.NightFont;
import me.night.util.NightRenderUtil;
import me.night.util.RotationManager;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class BedNuker extends Module {
    public final NumberProperty range = new NumberProperty("Range", 4.5, 2.0, 6.0, 0.1);
    public final ModeProperty mode = new ModeProperty("Mode", "Legit", "Legit", "Instant");
    public final BooleanProperty surroundings = new BooleanProperty("Surroundings", "Breaks defense blocks covering bed", true);
    public final BooleanProperty autoTool = new BooleanProperty("Auto Tool", "Selects best mining tool", true);
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", "Silently rotates to block face", true);
    public final ModeProperty moveFix = new ModeProperty("Move Fix", "Strict", () -> silentAim.getValue(), "Off", "Strict", "Silent");
    public final BooleanProperty progressHud = new BooleanProperty("Progress HUD", "Shows Rise 6 style progress card", true);
    public final BooleanProperty esp = new BooleanProperty("ESP", "Highlights target bed", true);

    private BlockPos currentTarget = null;
    private BlockPos bedPos = null;
    private Direction currentFacing = Direction.UP;
    private float breakProgress = 0.0F;
    private boolean isBreaking = false;
    private int savedSlot = -1;

    public BedNuker() {
        super("BedNuker", "Automatically breaks nearby beds and defense blocks", Category.WORLD);
        addProperties(range, mode, surroundings, autoTool, silentAim, moveFix, progressHud, esp);
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @Override
    protected void onDisable() {
        resetBreaking();
    }

    private void resetBreaking() {
        if (isBreaking && currentTarget != null && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
        if (savedSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(savedSlot);
            savedSlot = -1;
        }
        currentTarget = null;
        bedPos = null;
        breakProgress = 0.0F;
        isBreaking = false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;

        BlockPos bestBed = findNearbyBed();
        if (bestBed == null) {
            resetBreaking();
            return;
        }

        bedPos = bestBed;
        BlockPos targetPos = bestBed;

        // If covered and surroundings is enabled, target obstruction block
        if (surroundings.getValue()) {
            BlockPos obstruction = findObstruction(bestBed);
            if (obstruction != null) {
                targetPos = obstruction;
            }
        }

        currentTarget = targetPos;
        currentFacing = getOptimalDirection(targetPos);

        // Silent Aim rotations
        if (silentAim.getValue()) {
            Vec3d center = targetPos.toCenterPos();
            float[] rots = RotationManager.calculateRotations(
                    mc.player.getX(),
                    mc.player.getEyeY(),
                    mc.player.getZ(),
                    center.x,
                    center.y,
                    center.z
            );
            RotationManager.setRotations(rots[0], rots[1], moveFix.getValue());
        }

        // Auto Tool selection
        if (autoTool.getValue()) {
            BlockState state = mc.world.getBlockState(targetPos);
            int bestToolSlot = findBestTool(state);
            if (bestToolSlot != -1 && bestToolSlot != mc.player.getInventory().getSelectedSlot()) {
                if (savedSlot == -1) {
                    savedSlot = mc.player.getInventory().getSelectedSlot();
                }
                mc.player.getInventory().setSelectedSlot(bestToolSlot);
            }
        }

        // Break logic
        if (mode.is("Instant")) {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, targetPos, currentFacing));
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, targetPos, currentFacing));
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            breakProgress = 1.0F;
        } else {
            // Legit mode
            BlockState state = mc.world.getBlockState(targetPos);
            float delta = state.calcBlockBreakingDelta(mc.player, mc.world, targetPos);

            if (!isBreaking) {
                if (mc.interactionManager != null) {
                    mc.interactionManager.attackBlock(targetPos, currentFacing);
                }
                isBreaking = true;
                breakProgress = 0.0F;
            } else {
                if (mc.interactionManager != null) {
                    mc.interactionManager.updateBlockBreakingProgress(targetPos, currentFacing);
                }
                breakProgress = Math.min(1.0F, breakProgress + delta);
                mc.player.swingHand(Hand.MAIN_HAND);

                if (breakProgress >= 1.0F || state.isAir()) {
                    resetBreaking();
                }
            }
        }
    }

    private BlockPos findNearbyBed() {
        double r = range.getValue();
        int reach = (int) Math.ceil(r);
        BlockPos playerPos = mc.player.getBlockPos();

        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    double dist = mc.player.squaredDistanceTo(pos.toCenterPos());
                    if (dist > r * r) continue;

                    BlockState state = mc.world.getBlockState(pos);
                    if (state.getBlock() instanceof BedBlock) {
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }

    private BlockPos findObstruction(BlockPos bed) {
        // Check directly above the bed
        BlockPos up = bed.up();
        if (!mc.world.getBlockState(up).isAir()) {
            return up;
        }

        // Check horizontal faces towards player
        Direction[] dirs = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction d : dirs) {
            BlockPos side = bed.offset(d);
            if (!mc.world.getBlockState(side).isAir() && !(mc.world.getBlockState(side).getBlock() instanceof BedBlock)) {
                if (mc.player.squaredDistanceTo(side.toCenterPos()) < mc.player.squaredDistanceTo(bed.toCenterPos())) {
                    return side;
                }
            }
        }
        return null;
    }

    private Direction getOptimalDirection(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = pos.toCenterPos();
        Vec3d diff = eye.subtract(center);

        double absX = Math.abs(diff.x);
        double absY = Math.abs(diff.y);
        double absZ = Math.abs(diff.z);

        if (absY >= absX && absY >= absZ) {
            return diff.y > 0 ? Direction.UP : Direction.DOWN;
        } else if (absX >= absZ) {
            return diff.x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private int findBestTool(BlockState state) {
        PlayerInventory inv = mc.player.getInventory();
        int bestSlot = -1;
        float bestSpeed = 1.0F;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                float speed = stack.getMiningSpeedMultiplier(state);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null || bedPos == null) return;

        DrawContext context = event.getContext();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        // 1. Rise 6 Style BedNuker Progress HUD Card
        if (progressHud.getValue() && currentTarget != null) {
            int cardW = 160;
            int cardH = 36;
            int cardX = (screenW - cardW) / 2;
            int cardY = screenH - 72;

            double dist = Math.sqrt(mc.player.squaredDistanceTo(currentTarget.toCenterPos()));
            int pct = (int) (breakProgress * 100);

            // Modern glowing card
            NightRenderUtil.drawModernCard(context, cardX, cardY, cardW, cardH, 5, 0xEE0B0C15, 0x30FFFFFF, 0x459D4EDD);

            // Top ambient gradient line
            NightRenderUtil.drawHorizontalGradient(context, cardX + 2, cardY, cardW - 4, 1, 0xFF9D4EDD, 0xFF00E5FF);

            // Title and distance
            NightFont.drawBold(context, "BED NUKER", cardX + 8, cardY + 6, 0xFF9D4EDD);
            String distStr = String.format("%.1fm  %d%%", dist, pct);
            int distW = NightFont.getWidth(distStr);
            NightFont.draw(context, distStr, cardX + cardW - distW - 8, cardY + 6, 0xFFD0D0E0);

            // Continuous Progress Bar
            int barX = cardX + 8;
            int barY = cardY + 20;
            int barW = cardW - 16;
            int barH = 7;

            // Track
            NightRenderUtil.drawRoundedRect(context, barX, barY, barW, barH, 3, 0xFF141522);
            // Progress Fill
            int fillW = Math.max(3, (int) (barW * Math.min(1.0F, Math.max(0.0F, breakProgress))));
            NightRenderUtil.drawHorizontalGradient(context, barX, barY, fillW, barH, 0xFF9D4EDD, 0xFF00E5FF);
        }

        // 2. 2D ESP Box around target bed
        if (esp.getValue() && bedPos != null) {
            Camera camera = mc.gameRenderer.getCamera();
            if (!camera.isReady()) return;

            Vec3d camPos = camera.getCameraPos();
            double yawRad = Math.toRadians(camera.getYaw());
            double pitchRad = Math.toRadians(camera.getPitch());
            Vec3d forward = new Vec3d(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));

            Vec3d center = bedPos.toCenterPos();
            if (center.subtract(camPos).dotProduct(forward) > 0.2) {
                Vec3d ndc = mc.gameRenderer.project(center);
                int sx = (int) ((ndc.x + 1.0) / 2.0 * screenW);
                int sy = (int) ((1.0 - ndc.y) / 2.0 * screenH);

                if (sx >= 0 && sx <= screenW && sy >= 0 && sy <= screenH) {
                    int tagW = NightFont.getBoldWidth("BED") + 10;
                    int tagH = 14;
                    NightRenderUtil.drawModernCard(context, sx - tagW / 2, sy - tagH / 2, tagW, tagH, 3, 0xD0121320, 0x409D4EDD, 0x309D4EDD);
                    NightFont.drawBold(context, "BED", sx - tagW / 2 + 5, sy - tagH / 2 + 3, 0xFF9D4EDD);
                }
            }
        }
    }
}
