package me.night.module.modules.movement;

import me.night.event.EventTarget;
import me.night.event.events.TickEvent;
import me.night.module.Category;
import me.night.module.Module;
import me.night.property.properties.BooleanProperty;
import me.night.util.RotationManager;

public class Sprint extends Module {
    public final BooleanProperty omni = new BooleanProperty("Omni", "Sprint in all directions", false);

    public Sprint() {
        super("Sprint", "Automatically toggles sprint", Category.MOVEMENT);
        addProperties(omni);
        setEnabled(true);
    }

    @Override
    public String getSuffix() {
        return omni.getValue() ? "Omni" : "Vanilla";
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getPhase() == TickEvent.Phase.PRE && mc.player != null) {
            boolean moveFixMoving = RotationManager.hasRotations() && RotationManager.isMoveFixActive() && RotationManager.isForwardPressed();
            if (omni.getValue()) {
                if (mc.player.input.getMovementInput().x != 0 || mc.player.input.getMovementInput().y != 0 || moveFixMoving) {
                    mc.player.setSprinting(true);
                }
            } else {
                if ((mc.player.input.hasForwardMovement() || moveFixMoving) && !mc.player.isSneaking()) {
                    mc.player.setSprinting(true);
                }
            }
        }
    }
}
