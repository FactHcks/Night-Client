package me.night.module.modules.world;

import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.property.properties.ModeProperty;
import me.night.util.RotationManager;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Scaffold extends Module {
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", "Silently look down/back at block face", true);
    public final ModeProperty rotations = new ModeProperty("Rotations", "Normal", "Normal", "Strict", "Hypixel");
    public final BooleanProperty tower = new BooleanProperty("Tower", "Fast tower upwards", true);
    public final BooleanProperty keepY = new BooleanProperty("Keep Y", false);

    private int startY = -1;

    public Scaffold() {
        super("Scaffold", "Places blocks underneath player", Category.WORLD);
        addProperties(silentAim, rotations, tower, keepY);
    }

    @Override
    public String getSuffix() {
        if (mc.player == null) return rotations.getValue();
        int blocks = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BlockItem) blocks += stack.getCount();
        }
        return blocks + " blocks";
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            startY = (int) Math.floor(mc.player.getY());
        }
    }

    /** A solid block to place against, and which of its faces to click. */
    private record Support(BlockPos block, Direction face) {
    }

    /** Finds a solid neighbour of pos to build onto (never the block above). */
    private Support supportFor(BlockPos pos) {
        for (Direction dir : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos adj = pos.offset(dir);
            if (!mc.world.getBlockState(adj).isReplaceable()) return new Support(adj, dir.getOpposite());
        }
        return null;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;

        int targetY = keepY.getValue() ? startY - 1 : (int) Math.floor(mc.player.getY() - 1.0);
        BlockPos below = new BlockPos((int) Math.floor(mc.player.getX()), targetY, (int) Math.floor(mc.player.getZ()));
        // grass, flowers, water and snow layers can be built over
        if (!mc.world.getBlockState(below).isReplaceable()) return;

        BlockPos placeAt = below;
        Support support = supportFor(below);
        if (support == null) {
            // nothing touching: place a block next to it first (e.g. when moving diagonally)
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos side = below.offset(dir);
                if (!mc.world.getBlockState(side).isReplaceable()) continue;
                Support s2 = supportFor(side);
                if (s2 != null) {
                    placeAt = side;
                    support = s2;
                    break;
                }
            }
        }
        if (support == null) return;

        BlockPos adj = support.block();
        Direction side = support.face();
        Vec3d hitVec = new Vec3d(adj.getX() + 0.5 + side.getOffsetX() * 0.5,
                adj.getY() + 0.5 + side.getOffsetY() * 0.5,
                adj.getZ() + 0.5 + side.getOffsetZ() * 0.5);

        if (silentAim.getValue()) {
            float[] rots = RotationManager.calculateRotations(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                    hitVec.x, hitVec.y, hitVec.z);
            RotationManager.setRotations(rots[0], rots[1]);
        }

        int slot = findBlockSlot();
        if (slot != -1 && mc.interactionManager != null) {
            int prevSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, side, adj, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.getInventory().setSelectedSlot(prevSlot);
        }

        if (tower.getValue() && mc.options.jumpKey.isPressed() && placeAt.equals(below)) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
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
