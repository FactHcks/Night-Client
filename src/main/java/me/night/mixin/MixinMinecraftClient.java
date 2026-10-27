package me.night.mixin;

import me.night.ui.menu.NightMainMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    /** Swap the vanilla title screen for the client's own main menu. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen night$replaceTitleScreen(Screen screen) {
        if (screen instanceof TitleScreen) {
            return new NightMainMenu();
        }
        return screen;
    }

    /** Glow ESP: give targets the vanilla glowing outline. */
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void night$glowEsp(net.minecraft.entity.Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (me.night.module.modules.render.ESP.shouldGlow(entity)) cir.setReturnValue(true);
    }
}
