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
        return rotations.getValue();
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            startY = (int) Math.floor(mc.player.getY());
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;

        int targetY = keepY.getValue() ? startY - 1 : (int) Math.floor(mc.player.getY() - 1.0);
        BlockPos below = new BlockPos((int) Math.floor(mc.player.getX()), targetY, (int) Math.floor(mc.player.getZ()));

        if (mc.world.getBlockState(below).isAir()) {
            for (Direction dir : Direction.values()) {
                BlockPos adj = below.offset(dir);
                if (!mc.world.getBlockState(adj).isAir()) {
                    Direction side = dir.getOpposite();

                    if (silentAim.getValue()) {
                        float[] rots = RotationManager.calculateRotations(
                                mc.player.getX(),
                                mc.player.getEyeY(),
                                mc.player.getZ(),
                                adj.getX() + 0.5 + side.getOffsetX() * 0.5,
                                adj.getY() + 0.5 + side.getOffsetY() * 0.5,
                                adj.getZ() + 0.5 + side.getOffsetZ() * 0.5
                        );
                        RotationManager.setRotations(rots[0], rots[1]);
                    }

                    int slot = findBlockSlot();
                    if (slot != -1) {
                        int prevSlot = mc.player.getInventory().getSelectedSlot();
                        mc.player.getInventory().setSelectedSlot(slot);

                        Vec3d hitVec = new Vec3d(adj.getX() + 0.5 + side.getOffsetX() * 0.5,
                                adj.getY() + 0.5 + side.getOffsetY() * 0.5,
                                adj.getZ() + 0.5 + side.getOffsetZ() * 0.5);

                        BlockHitResult hitResult = new BlockHitResult(hitVec, side, adj, false);
                        if (mc.interactionManager != null) {
                            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                            mc.player.swingHand(Hand.MAIN_HAND);
                        }

                        mc.player.getInventory().setSelectedSlot(prevSlot);
                    }

                    if (tower.getValue() && mc.options.jumpKey.isPressed()) {
                        mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
                    }
                    break;
                }
            }
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
