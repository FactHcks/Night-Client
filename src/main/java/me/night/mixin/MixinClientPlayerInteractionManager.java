package me.night.mixin;

import me.night.module.modules.combat.Criticals;
import me.night.module.modules.render.HitEffects;
import me.night.module.modules.render.SessionStats;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void night$onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        Criticals.onAttack(target);
        HitEffects.onAttack(target);
        SessionStats.onAttack(target);
    }
}
